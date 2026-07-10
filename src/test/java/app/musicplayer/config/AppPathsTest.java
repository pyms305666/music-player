package app.musicplayer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
            assertEquals(tempDir.resolve("downloads").toAbsolutePath().normalize(), paths.dataDir());
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
}
