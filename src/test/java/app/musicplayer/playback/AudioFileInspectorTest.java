package app.musicplayer.playback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AudioFileInspector 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：根据文件头识别音频实际编码。区分 MP3、AAC、MP4
 * 等格式，辅助处理扩展名与内容不一致及 JavaFX 不支持的音频。
 */
class AudioFileInspectorTest {
    @TempDir Path tempDir;

    /** 验证 MP3、MP4 和原始 AAC 文件头识别。 */
    @Test
    void detectsMp3Mp4AndRawAacHeaders() throws Exception {
        AudioFileInspector inspector = new AudioFileInspector();
        Path mp3 =
                Files.write(
                        tempDir.resolve("audio.m4a"), new byte[] {(byte) 0xff, (byte) 0xfb, 0, 0});
        Path mp4 =
                Files.write(
                        tempDir.resolve("audio.mp4"), new byte[] {0, 0, 0, 0, 'f', 't', 'y', 'p'});
        Path aac =
                Files.write(
                        tempDir.resolve("audio.aac"), new byte[] {(byte) 0xff, (byte) 0xf1, 0, 0});

        assertEquals(AudioFormat.MP3, inspector.detect(mp3));
        assertEquals(AudioFormat.MP4, inspector.detect(mp4));
        assertEquals(AudioFormat.RAW_AAC, inspector.detect(aac));
    }

    /** 验证扩展名错误的 MP3 下载获得正确播放别名。 */
    @Test
    void createsMp3PlaybackAliasForMislabeledDownload() throws Exception {
        Path source =
                Files.write(
                        tempDir.resolve("download.m4a"),
                        new byte[] {(byte) 0xff, (byte) 0xfb, 0, 0});
        AudioFileInspector inspector = new AudioFileInspector();
        try (var resolver = new PlaybackFileResolver(tempDir.resolve("cache"), inspector);
                var resolution = resolver.resolve(source)) {
            assertTrue(resolution.correctedExtension());
            assertTrue(resolution.path().getFileName().toString().endsWith(".mp3"));
            assertTrue(Files.isRegularFile(resolution.path()));
            assertEquals(-1, Files.mismatch(source, resolution.path()));
            try (var again = resolver.resolve(source)) {
                assertEquals(resolution.path(), again.path());
            }
            try (var entries = Files.list(tempDir.resolve("cache"))) {
                assertEquals(1, entries.count());
            }
        }
    }
}
