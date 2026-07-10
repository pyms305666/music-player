package app.musicplayer.playback;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 根据文件头而不是扩展名判断常见音频格式。 */
public final class AudioFileInspector {
    private static final int HEADER_SIZE = 512;

    public AudioFormat detect(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return AudioFormat.UNKNOWN;
        }
        try (InputStream input = Files.newInputStream(path)) {
            byte[] header = input.readNBytes(HEADER_SIZE);
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
        } catch (IOException ignored) {
            return AudioFormat.UNKNOWN;
        }
        return AudioFormat.UNKNOWN;
    }

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

    public boolean isUnsupportedMediaError(Throwable error) {
        if (error == null) {
            return false;
        }
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("corrupted")
                || message.contains("unsupported")
                || message.contains("error_media_corrupted");
    }

    private int skipId3Header(byte[] header) {
        if (header.length < 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') {
            return 0;
        }
        int size = (header[6] & 0x7f) << 21
                | (header[7] & 0x7f) << 14
                | (header[8] & 0x7f) << 7
                | (header[9] & 0x7f);
        return Math.min(header.length - 1, 10 + size);
    }

    private boolean isMp3FrameHeader(byte[] header, int offset) {
        if (offset + 1 >= header.length) {
            return false;
        }
        int first = header[offset] & 0xff;
        int second = header[offset + 1] & 0xff;
        return first == 0xff && (second & 0xe0) == 0xe0 && (second & 0x06) != 0;
    }

    private boolean isAdtsHeader(byte[] header, int offset) {
        if (offset + 1 >= header.length) {
            return false;
        }
        int first = header[offset] & 0xff;
        int second = header[offset + 1] & 0xff;
        return first == 0xff && (second & 0xf6) == 0xf0;
    }

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
