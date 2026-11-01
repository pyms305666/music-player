package app.musicplayer.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/** 实例锁测试，确认迁移独占和退出释放，不触碰用户数据目录。 */
class StorageInstanceLockTest {
    @TempDir Path directory;

    /** 正常实例存在时拒绝迁移，关闭之后可以独占。 */
    @Test
    void migrationWaitsForRunningInstanceAndCanAcquireAfterClose() throws Exception {
        try (var running = StorageInstanceLock.acquire(directory, true)) {
            assertThrows(
                    java.io.IOException.class, () -> StorageInstanceLock.acquire(directory, false));
        }
        try (var migration = StorageInstanceLock.acquire(directory, false)) {
            assertThrows(
                    java.io.IOException.class, () -> StorageInstanceLock.acquire(directory, true));
        }
        try (var next = StorageInstanceLock.acquire(directory, true)) {
            assertNotNull(next);
        }
    }
}
