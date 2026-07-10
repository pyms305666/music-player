package app.musicplayer.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteNativeTempTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAProcessSpecificDirectoryInsideTheApplicationCache() {
        String previous = System.getProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY);
        try {
            System.clearProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY);

            Path configured = SqliteNativeTemp.configure(tempDir.resolve("cache"));

            assertTrue(Files.isDirectory(configured));
            assertTrue(configured.startsWith(tempDir.resolve("cache").resolve("sqlite-native")));
            assertTrue(configured.getFileName().toString().startsWith("process-"));
            assertEquals(configured.toString(), System.getProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY));
        } finally {
            restoreProperty(previous);
        }
    }

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

    private static void restoreProperty(String previous) {
        if (previous == null) {
            System.clearProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY);
        } else {
            System.setProperty(SqliteNativeTemp.SQLITE_TEMP_PROPERTY, previous);
        }
    }
}
