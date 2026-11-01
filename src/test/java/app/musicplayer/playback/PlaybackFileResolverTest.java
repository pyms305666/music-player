package app.musicplayer.playback;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.cache.GeneratedFileCache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/**
 * PlaybackFileResolver
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：桌面播放前的文件适配。为扩展名错误的音频建立合适的播放缓存文件，返回带释放操作的句柄，原始音频保持独立。
 */
class PlaybackFileResolverTest {
    @TempDir Path root;

    /** 验证两个播放句柄释放前保护缓存，原始音频始终保留。 */
    @Test
    void activeResolutionsProtectCacheUntilBothAreDisposedButNeverDeleteOriginal()
            throws Exception {
        Path source =
                Files.write(
                        root.resolve("original.m4a"), new byte[] {(byte) 0xff, (byte) 0xfb, 0, 0});
        Path directory = root.resolve("cache");
        var cache =
                new GeneratedFileCache(
                        directory, GeneratedFileCache.Kind.PLAYBACK, 0, System::currentTimeMillis);
        try (var resolver = new PlaybackFileResolver(directory, new AudioFileInspector(), cache)) {
            var first = resolver.resolve(source);
            var second = resolver.resolve(source);
            assertTrue(first.correctedExtension());
            assertEquals(first.path(), second.path());
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.exists(first.path()));
            first.close();
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.exists(second.path()));
            second.close();
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertFalse(Files.exists(second.path()));
            assertTrue(Files.exists(source));
        }
    }

    /** 验证复制失败时后备处理不改变原文件且无临时残留。 */
    @Test
    void failedCopyFallsBackWithoutChangingSourceOrLeavingPartFile() throws Exception {
        Path source =
                Files.write(
                        root.resolve("original.aac"), new byte[] {(byte) 0xff, (byte) 0xfb, 0, 0});
        Path cacheFile = Files.write(root.resolve("cache"), new byte[] {9});
        try (var resolver = new PlaybackFileResolver(cacheFile, new AudioFileInspector());
                var result = resolver.resolve(source)) {
            assertEquals(source, result.path());
            assertFalse(result.correctedExtension());
            assertArrayEquals(new byte[] {9}, Files.readAllBytes(cacheFile));
            assertEquals(4, Files.size(source));
        }
    }
}
