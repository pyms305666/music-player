package app.musicplayer.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

/**
 * GeneratedFileCache
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：应用生成文件的磁盘缓存管理器。保护活跃句柄，清理过期缓存，并检查路径边界及符号链接，避免删除原始用户文件。
 */
class GeneratedFileCacheTest {
    @TempDir Path root;

    /** 在隔离夹具中创建指定内容的测试文件。 */
    private Path file(int id, int size, long used) throws Exception {
        Path path = root.resolve(String.format("%040x.mp3", id));
        Files.write(path, new byte[size]);
        Files.setLastModifiedTime(path, FileTime.fromMillis(used));
        return path;
    }

    /** 验证淘汰最旧生成文件，保留外来文件及目录。 */
    @Test
    void evictsOldestGeneratedFilesAndLeavesForeignFilesAndDirectories() throws Exception {
        // Start maintenance before fixture creation to avoid racing its initial pass.
        try (var cache =
                new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 10, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            Path old = file(1, 6, 1), recent = file(2, 6, 2);
            Path foreign = Files.write(root.resolve("personal.mp3"), new byte[100]);
            Path part = Files.write(root.resolve(".za-playback-temp.part"), new byte[100]);
            Path nested = Files.createDirectories(root.resolve("nested"));
            Files.write(nested.resolve(String.format("%040x.mp3", 3)), new byte[100]);
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertFalse(Files.exists(old));
            assertTrue(Files.exists(recent));
            assertTrue(Files.exists(foreign));
            assertTrue(Files.exists(part));
            assertTrue(Files.exists(nested));
        }
    }

    /** 验证所有使用者释放后才允许淘汰，重复关闭保持安全。 */
    @Test
    void allConsumersMustReleaseBeforeEvictionAndCloseIsIdempotent() throws Exception {
        try (var cache =
                new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 0, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            Path path = root.resolve(String.format("%040x.mp3", 1));
            var first = cache.acquire(path);
            var second = cache.acquire(path);
            Files.write(path, new byte[20]);
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.exists(path));
            first.close();
            first.close();
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.exists(path));
            second.close();
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertFalse(Files.exists(path));
            assertThrows(
                    IllegalArgumentException.class, () -> cache.acquire(root.resolve("user.mp3")));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            cache.acquire(
                                    root.resolve("nested").resolve(String.format("%040x.mp3", 1))));
        }
    }

    /** 验证释放使用权前更新最近使用时间，避免马上被淘汰。 */
    @Test
    void recentReleaseTouchesBeforeRemovingProtection() throws Exception {
        try (var cache =
                new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 6, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            Path active = root.resolve(String.format("%040x.mp3", 1));
            var lease = cache.acquire(active);
            file(1, 6, 1);
            Path other = file(2, 6, 2);
            lease.close();
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.exists(active));
            assertFalse(Files.exists(other));
            cache.close();
            lease.close();
            assertThrows(IllegalStateException.class, () -> cache.acquire(active));
        }
    }

    /** 验证清理符号链接时不会删除其指向的原文件。 */
    @Test
    void symbolicLinkDoesNotDeleteItsTarget() throws Exception {
        Path target = Files.write(root.resolve("personal.mp3"), new byte[100]);
        Path link = root.resolve(String.format("%040x.mp3", 1));
        try {
            Files.createSymbolicLink(link, target);
        } catch (java.io.IOException | UnsupportedOperationException denied) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Host cannot create symlinks");
        }
        try (var cache =
                new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 0, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            assertTrue(Files.isSymbolicLink(link));
            assertTrue(Files.exists(target));
        }
    }

    /** 验证关闭后妥善结束已经排队的清理请求。 */
    @Test
    void closeSettlesQueuedCleanupRequests() throws Exception {
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var cache =
                new GeneratedFileCache(root, GeneratedFileCache.Kind.PLAYBACK, 10, () -> 100)) {
            cache.cleanAsync().get(3, TimeUnit.SECONDS);
            var field = GeneratedFileCache.class.getDeclaredField("worker");
            field.setAccessible(true);
            var worker = (java.util.concurrent.ExecutorService) field.get(cache);
            worker.execute(
                    () -> {
                        blocked.countDown();
                        try {
                            release.await(3, TimeUnit.SECONDS);
                        } catch (InterruptedException cancelled) {
                            Thread.currentThread().interrupt();
                        }
                    });
            assertTrue(blocked.await(3, TimeUnit.SECONDS));
            var queued = cache.cleanAsync();
            var another = cache.cleanAsync();
            queued.whenComplete((result, error) -> cache.close());
            cache.close();
            assertTrue(another.isCancelled());
            assertTrue(queued.isDone());
            assertTrue(queued.isCancelled());
            release.countDown();
        } finally {
            release.countDown();
        }
    }
}
