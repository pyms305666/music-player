package app.musicplayer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPathsTest {
    @TempDir
    Path tempDir;

    @Test
    void usesPackagedExecutableDirectoryWhenAvailable() {
        String previous = System.getProperty("jpackage.app-path");
        try {
            System.setProperty("jpackage.app-path", tempDir.resolve("播放器.exe").toString());
            AppPaths paths = AppPaths.resolve(AppPathsTest.class);

            assertEquals(tempDir.toAbsolutePath().normalize(), paths.baseDir());
            assertTrue(!paths.dataDir().equals(tempDir.resolve("downloads").toAbsolutePath().normalize()));
        } finally {
            if (previous == null) {
                System.clearProperty("jpackage.app-path");
            } else {
                System.setProperty("jpackage.app-path", previous);
            }
        }
    }

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
    @Test void dataDirectoryNeverMatchesEitherInstallerDirectory() {
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
