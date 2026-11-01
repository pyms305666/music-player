package app.musicplayer.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.prefs.Preferences;

/** 下载设置回归：用户确认、持久化、目录权限边界及显式测试参数互相独立。 */
class DownloadDirectorySettingsTest {
    @TempDir Path root;

    /** 保存后重建设置对象仍能读取位置，未选择时只给出建议。 */
    @Test
    void persistsOnlyExplicitSelection() throws Exception {
        Preferences preferences =
                Preferences.userRoot().node("/app/musicplayer/storage-tests/" + UUID.randomUUID());
        try {
            Path legacy = Files.createDirectories(root.resolve("old"));
            var settings =
                    new DownloadDirectorySettings(preferences, legacy, root.resolve("application"));
            assertTrue(settings.configured().isEmpty());
            assertEquals(legacy, settings.suggested());
            Path chosen = root.resolve("新盘目录 with spaces");
            settings.save(chosen);
            assertEquals(
                    chosen.toRealPath(),
                    new DownloadDirectorySettings(preferences, legacy, null)
                            .configured()
                            .orElseThrow());
            assertThrows(
                    java.io.IOException.class,
                    () -> settings.validate(root.resolve("application/music")));
            assertThrows(java.io.IOException.class, () -> settings.validate(Path.of("relative")));
            Path file = Files.writeString(root.resolve("not-a-folder"), "x");
            assertThrows(java.io.IOException.class, () -> settings.validate(file));
            assertEquals(chosen.toRealPath(), settings.configured().orElseThrow());
        } finally {
            preferences.removeNode();
        }
    }

    /** 显式启动参数用于独立夹具，不偷偷修改用户偏好设置。 */
    @Test
    void overrideDoesNotPersistOrIncludeStaging() throws Exception {
        Preferences preferences =
                Preferences.userRoot().node("/app/musicplayer/storage-tests/" + UUID.randomUUID());
        String previous = System.getProperty("musicplayer.download-dir");
        try {
            System.setProperty("musicplayer.download-dir", root.resolve("qa").toString());
            var settings = new DownloadDirectorySettings(preferences, root, null);
            assertEquals(root.resolve("qa"), settings.configured().orElseThrow());
            assertNull(preferences.get("download.directory", null));
            assertTrue(
                    DownloadDirectorySettings.internal(
                            root.resolve(".za-music-staging/job/song.wav")));
            assertFalse(DownloadDirectorySettings.internal(root.resolve("music/song.wav")));
        } finally {
            if (previous == null) System.clearProperty("musicplayer.download-dir");
            else System.setProperty("musicplayer.download-dir", previous);
            preferences.removeNode();
        }
    }
}
