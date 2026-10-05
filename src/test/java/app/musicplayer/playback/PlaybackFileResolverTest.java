package app.musicplayer.playback;

import app.musicplayer.cache.GeneratedFileCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackFileResolverTest {
    @TempDir Path root;
    @Test void activeResolutionsProtectCacheUntilBothAreDisposedButNeverDeleteOriginal() throws Exception {
        Path source = Files.write(root.resolve("original.m4a"), new byte[]{(byte)0xff, (byte)0xfb, 0, 0});
        Path directory = root.resolve("cache");
        var cache = new GeneratedFileCache(directory, GeneratedFileCache.Kind.PLAYBACK, 0, System::currentTimeMillis);
        try (var resolver = new PlaybackFileResolver(directory, new AudioFileInspector(), cache)) {
            var first = resolver.resolve(source); var second = resolver.resolve(source);
            assertTrue(first.correctedExtension()); assertEquals(first.path(), second.path());
            cache.cleanAsync().get(3, TimeUnit.SECONDS); assertTrue(Files.exists(first.path()));
            first.close(); cache.cleanAsync().get(3, TimeUnit.SECONDS); assertTrue(Files.exists(second.path()));
            second.close(); cache.cleanAsync().get(3, TimeUnit.SECONDS); assertFalse(Files.exists(second.path()));
            assertTrue(Files.exists(source));
        }
    }
    @Test void failedCopyFallsBackWithoutChangingSourceOrLeavingPartFile() throws Exception {
        Path source = Files.write(root.resolve("original.aac"), new byte[]{(byte)0xff, (byte)0xfb, 0, 0});
        Path cacheFile = Files.write(root.resolve("cache"), new byte[]{9});
        try (var resolver = new PlaybackFileResolver(cacheFile, new AudioFileInspector()); var result = resolver.resolve(source)) {
            assertEquals(source, result.path()); assertFalse(result.correctedExtension());
            assertArrayEquals(new byte[]{9}, Files.readAllBytes(cacheFile));
            assertEquals(4, Files.size(source));
        }
    }
}
