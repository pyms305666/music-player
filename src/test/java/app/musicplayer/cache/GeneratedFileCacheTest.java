package app.musicplayer.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedFileCacheTest {
    @TempDir Path root;
    private Path file(int id, int size, long used) throws Exception {
        Path path = root.resolve(String.format("%040x.mp3", id));
        Files.write(path, new byte[size]); Files.setLastModifiedTime(path, FileTime.fromMillis(used)); return path;
    }
    @Test void evictsOldestGeneratedFilesAndLeavesForeignFilesAndDirectories() throws Exception {
        // Start maintenance before fixture creation to avoid racing its initial pass.
        try (var cache = new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 10, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            Path old = file(1, 6, 1), recent = file(2, 6, 2);
            Path foreign = Files.write(root.resolve("personal.mp3"), new byte[100]);
            Path part = Files.write(root.resolve(".za-playback-temp.part"), new byte[100]);
            Path nested = Files.createDirectories(root.resolve("nested"));
            Files.write(nested.resolve(String.format("%040x.mp3", 3)), new byte[100]);
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertFalse(Files.exists(old)); assertTrue(Files.exists(recent));
            assertTrue(Files.exists(foreign)); assertTrue(Files.exists(part)); assertTrue(Files.exists(nested));
        }
    }
    @Test void allConsumersMustReleaseBeforeEvictionAndCloseIsIdempotent() throws Exception {
        try (var cache = new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 0, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            Path path = root.resolve(String.format("%040x.mp3", 1));
            var first = cache.acquire(path); var second = cache.acquire(path);
            Files.write(path, new byte[20]);
            cache.cleanAsync().get(3, TimeUnit.SECONDS); assertTrue(Files.exists(path));
            first.close(); first.close(); cache.cleanAsync().get(3, TimeUnit.SECONDS); assertTrue(Files.exists(path));
            second.close(); cache.cleanAsync().get(3, TimeUnit.SECONDS); assertFalse(Files.exists(path));
            assertThrows(IllegalArgumentException.class, () -> cache.acquire(root.resolve("user.mp3")));
            assertThrows(IllegalArgumentException.class, () -> cache.acquire(root.resolve("nested").resolve(String.format("%040x.mp3", 1))));
        }
    }
    @Test void recentReleaseTouchesBeforeRemovingProtection() throws Exception {
        try (var cache = new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 6, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            Path active = root.resolve(String.format("%040x.mp3", 1));
            var lease = cache.acquire(active);
            file(1, 6, 1); Path other = file(2, 6, 2);
            lease.close(); cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.exists(active)); assertFalse(Files.exists(other));
            cache.close(); lease.close();
            assertThrows(IllegalStateException.class, () -> cache.acquire(active));
        }
    }
    @Test void symbolicLinkDoesNotDeleteItsTarget() throws Exception {
        Path target = Files.write(root.resolve("personal.mp3"), new byte[100]);
        Path link = root.resolve(String.format("%040x.mp3", 1));
        try { Files.createSymbolicLink(link, target); }
        catch (java.io.IOException | UnsupportedOperationException denied) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Host cannot create symlinks");
        }
        try (var cache = new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 0, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.isSymbolicLink(link)); assertTrue(Files.exists(target));
        }
    }
    @Test void closeSettlesQueuedCleanupRequests() throws Exception {
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var cache = new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 10, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            var field = GeneratedFileCache.class.getDeclaredField("worker");
            field.setAccessible(true);
            var worker = (java.util.concurrent.ExecutorService)field.get(cache);
            worker.execute(() -> {
                blocked.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            });
            assertTrue(blocked.await(3, TimeUnit.SECONDS));
            var queued = cache.cleanAsync(); var another = cache.cleanAsync();
            queued.whenComplete((result, error) -> cache.close());
            cache.close(); assertTrue(another.isCancelled());
            assertTrue(queued.isDone()); assertTrue(queued.isCancelled());
            release.countDown();
        } finally { release.countDown(); }
    }
}
