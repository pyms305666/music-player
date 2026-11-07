package app.musicplayer.playback;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 根据文件头而不是扩展名判断常见音频格式。 */
public final class AudioFileInspector {
    private static final int HEADER_SIZE = 512;

    /** MPEG1 Layer III 帧头码率表（kbps），索引由帧头第 3 字节高 4 位给出。 */
    private static final int[] MP3_BITRATES_MPEG1 = {
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320
    };

    /** MPEG2 / MPEG2.5 Layer III 帧头码率表（kbps）。 */
    private static final int[] MP3_BITRATES_MPEG2 = {
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160
    };

    /** 播放条音质徽章信息：展示文案与是否无损。 */
    public record QualityInfo(String label, boolean lossless) {}

    /** 按实际文件头识别音频编码，避免仅凭后缀选择播放格式。 */
    public AudioFormat detect(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return AudioFormat.UNKNOWN;
        }
        byte[] header = readHeader(path);
        if (header.length < 4) {
            return AudioFormat.UNKNOWN;
        }
        if (containsAscii(header, "ftyp", 0, 64)) {
            return AudioFormat.MP4;
        }
        int offset = skipId3Header(header);
        if (isMp3FrameHeader(header, offset)) {
            return AudioFormat.MP3;
        }
        if (isAdtsHeader(header, offset)) {
            return AudioFormat.RAW_AAC;
        }
        return AudioFormat.UNKNOWN;
    }

    /**
     * 按文件头识别适合播放条展示的高精度音质徽章（如 FLAC、320K）。
     * 识别不出时返回 null，由调用方按扩展名兜底展示。
     */
    public QualityInfo qualityLabel(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        byte[] header = readHeader(path);
        if (header.length < 4) {
            return null;
        }
        if (containsAscii(header, "fLaC", 0, 4)) {
            return new QualityInfo("FLAC", true);
        }
        if (containsAscii(header, "RIFF", 0, 4) && containsAscii(header, "WAVE", 8, 12)) {
            return new QualityInfo("WAV", true);
        }
        if (containsAscii(header, "MAC ", 0, 4)) {
            return new QualityInfo("APE", true);
        }
        if (containsAscii(header, "ftyp", 0, 64)) {
            return new QualityInfo("AAC", false);
        }
        int offset = skipId3Header(header);
        if (isAdtsHeader(header, offset)) {
            return new QualityInfo("AAC", false);
        }
        String mp3 = mp3BitrateLabel(header, offset);
        return mp3 == null ? null : new QualityInfo(mp3, false);
    }

    /** 读取文件头采样字节，读取失败时返回空数组。 */
    private byte[] readHeader(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return input.readNBytes(HEADER_SIZE);
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    /** 解析 MP3 帧头码率并返回如 "320K" 的标签；非 Layer III 或解析不出时返回 "MP3" 或 null。 */
    private String mp3BitrateLabel(byte[] header, int offset) {
        if (!isMp3FrameHeader(header, offset) || offset + 3 >= header.length) {
            return null;
        }
        int second = header[offset + 1] & 0xff;
        // Layer III 的 layer 位组合是 01；其他 Layer 不做码率解析，仅展示 MP3。
        if (((second >> 1) & 0x03) != 0x01) {
            return "MP3";
        }
        int versionBits = (second >> 3) & 0x03;
        int bitrateIndex = (header[offset + 2] >> 4) & 0x0f;
        int[] table = versionBits == 0x03 ? MP3_BITRATES_MPEG1 : MP3_BITRATES_MPEG2;
        int kbps = bitrateIndex < table.length ? table[bitrateIndex] : 0;
        return kbps > 0 ? kbps + "K" : "MP3";
    }

    /** 检查文件是否具有指定扩展名，不区分大小写。 */
    public boolean hasExtension(Path path, String... extensions) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (fileName.endsWith("." + extension.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 识别媒体异常是否可能由不支持的音频格式引起。 */
    public boolean isUnsupportedMediaError(Throwable error) {
        if (error == null) {
            return false;
        }
        String message =
                error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("corrupted")
                || message.contains("unsupported")
                || message.contains("error_media_corrupted");
    }

    /** 跳过 MP3 的 ID3 标签以定位实际音频帧。 */
    private int skipId3Header(byte[] header) {
        if (header.length < 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
            return 0;
        }
        int size =
                (header[6] & 0x7f) << 21
                        | (header[7] & 0x7f) << 14
                        | (header[8] & 0x7f) << 7
                        | (header[9] & 0x7f);
        return Math.min(header.length - 1, 10 + size);
    }

    /** 检查字节前缀是否符合对应音频帧头规则。 */
    private boolean isMp3FrameHeader(byte[] header, int offset) {
        if (offset + 1 >= header.length) {
            return false;
        }
        int first = header[offset] & 0xff;
        int second = header[offset + 1] & 0xff;
        return first == 0xff && (second & 0xe0) == 0xe0 && (second & 0x06) != 0;
    }

    /** 检查字节前缀是否符合对应音频帧头规则。 */
    private boolean isAdtsHeader(byte[] header, int offset) {
        if (offset + 1 >= header.length) {
            return false;
        }
        int first = header[offset] & 0xff;
        int second = header[offset + 1] & 0xff;
        return first == 0xff && (second & 0xf6) == 0xf0;
    }

    /** 检查文件头是否包含指定 ASCII 格式标记。 */
    private boolean containsAscii(byte[] header, String token, int start, int endExclusive) {
        int end = Math.min(header.length - token.length() + 1, endExclusive);
        for (int index = Math.max(0, start); index < end; index++) {
            boolean matched = true;
            for (int tokenIndex = 0; tokenIndex < token.length(); tokenIndex++) {
                if (header[index + tokenIndex] != (byte) token.charAt(tokenIndex)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }
}
