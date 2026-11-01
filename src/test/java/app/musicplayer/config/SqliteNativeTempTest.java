package app.musicplayer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SqliteNativeTemp 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：为 SQLite JDBC 创建进程独立的原生库临时目录。在加载 JDBC
 * 驱动前设置路径，避免不同进程争用或安装目录权限问题。
 */
class SqliteNativeTempTest {
    @TempDir Path tempDir;

    /** 验证 SQLite 原生库临时目录位于应用缓存且按进程隔离。 */
    @Test
    void createsAProcessSpecificDirectoryInsideTheApplicationCache() {
        String previous = System.getProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY);
        try {
            System.clearProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY);

            Path configured = SqliteNativeTemp.configure(tempDir.resolve("cache"));

            assertTrue(Files.isDirectory(configured));
            assertTrue(configured.startsWith(tempDir.resolve("cache").resolve("sqlite-native")));
            assertTrue(configured.getFileName().toString().startsWith("process-"));
            assertEquals(
                    configured.toString(),
                    System.getProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY));
        } finally {
            restoreProperty(previous);
        }
    }

    /** 验证用户显式配置的数据目录保持有效。 */
    @Test
    void preservesAnExplicitlyConfiguredDirectory() {
        String previous = System.getProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY);
        Path explicit = tempDir.resolve("explicit-sqlite-temp").toAbsolutePath().normalize();
        try {
            System.setProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY, explicit.toString());

            assertEquals(explicit, SqliteNativeTemp.configure(tempDir.resolve("unused")));
        } finally {
            restoreProperty(previous);
        }
    }

    /** 恢复系统属性，供本文件的主流程使用。 */
    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY);
        } else {
            System.setProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY, previous);
        }
    }
}
