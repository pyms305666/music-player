package app.musicplayer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * AppPaths
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：集中确定桌面应用的数据、曲库数据库和缓存路径。支持开发运行、安装后运行及旧版数据迁移，避免把用户数据放在程序安装目录。
 */
class AppPathsTest {
    @TempDir Path tempDir;

    /** 验证打包运行时识别程序目录。 */
    @Test
    void usesPackagedExecutableDirectoryWhenAvailable() {
        String previous = System.getProperty("jpackage.app-path");
        try {
            System.setProperty("jpackage.app-path", tempDir.resolve("播放器.exe").toString());
            AppPaths paths = AppPaths.resolve(AppPathsTest.class);

            assertEquals(tempDir.toAbsolutePath().normalize(), paths.baseDir());
            assertTrue(
                    !paths.dataDir()
                            .equals(tempDir.resolve("downloads").toAbsolutePath().normalize()));
        } finally {
            if (previous == null) {
                System.clearProperty("jpackage.app-path");
            } else {
                System.setProperty("jpackage.app-path", previous);
            }
        }
    }

    /** 验证开发散装类运行时使用工作目录。 */
    @Test
    void usesWorkingDirectoryForExplodedDevelopmentClasses() {
        String previous = System.getProperty("jpackage.app-path");
        try {
            System.clearProperty("jpackage.app-path");
            AppPaths paths = AppPaths.resolve(AppPathsTest.class);

            assertEquals(
                    Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
                    paths.baseDir());
        } finally {
            if (previous != null) {
                System.setProperty("jpackage.app-path", previous);
            }
        }
    }

    /** 验证应用数据目录与两种安装目录均分离。 */
    @Test
    void dataDirectoryNeverMatchesEitherInstallerDirectory() {
        String previous = System.getProperty("jpackage.app-path");
        String local = System.getenv("LOCALAPPDATA");
        if (local == null) return;
        try {
            for (String app : java.util.List.of("ZA音乐", "ZA-Music", "简约音乐播放器")) {
                Path install = Path.of(local, app).toAbsolutePath().normalize();
                System.setProperty("jpackage.app-path", install.resolve("ZA音乐.exe").toString());
                assertTrue(!AppPaths.resolve(AppPathsTest.class).dataDir().startsWith(install));
            }
        } finally {
            if (previous == null) System.clearProperty("jpackage.app-path");
            else System.setProperty("jpackage.app-path", previous);
        }
    }
}
