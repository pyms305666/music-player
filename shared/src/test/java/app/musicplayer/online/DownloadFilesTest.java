package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * DownloadFiles 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：下载文件的格式识别与原子发布。根据文件头确定扩展名，预留不冲突的最终文件，避免覆盖已有歌曲。
 */
class DownloadFilesTest {
    @TempDir Path directory;

    /** 验证并发同名下载保留两个新文件及已有歌曲。 */
    @Test
    void concurrentSameNameDownloadsKeepBothPayloadsAndExistingSong() throws Exception {
        Files.writeString(directory.resolve("song.mp3"), "original");
        Path first = Files.writeString(directory.resolve("a.part"), "first");
        Path second = Files.writeString(directory.resolve("b.part"), "second");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results =
                    executor.invokeAll(
                            List.<Callable<Path>>of(
                                    () -> DownloadFiles.publish(first, directory, "song", ".mp3"),
                                    () ->
                                            DownloadFiles.publish(
                                                    second, directory, "song", ".mp3")));
            assertNotEquals(results.get(0).get(), results.get(1).get());
            assertEquals("first", Files.readString(results.get(0).get()));
            assertEquals("second", Files.readString(results.get(1).get()));
            assertEquals("original", Files.readString(directory.resolve("song.mp3")));
            assertFalse(Files.exists(first));
            assertFalse(Files.exists(second));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 验证按真实内容识别编码，不被错误扩展名误导。 */
    @Test
    void recognizesContentInsteadOfMisleadingDownloadSuffix() throws Exception {
        Path file = Files.write(directory.resolve("wrong.m4a"), new byte[] {'I', 'D', '3', 4});
        assertEquals(".mp3", DownloadFiles.detectedExtension(file, ".m4a"));
        Files.write(file, new byte[] {'f', 'L', 'a', 'C'});
        assertEquals(".flac", DownloadFiles.detectedExtension(file, ".mp3"));
    }
}
