package app.musicplayer.config;

import app.musicplayer.data.MusicDatabase;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyInstallMigrationTest {
    @TempDir
    Path root;

    @Test
    void copiesOldLibraryAndRemapsOnlyFilesInsideTheFormerInstallation() throws Exception {
        Path oldData = root.resolve("简约音乐播放器/downloads");
        Path oldSong = oldData.resolve("旧下载.wav");
        Path externalSong = root.resolve("其他位置.wav");
        Files.createDirectories(oldData);
        Files.write(oldSong, new byte[]{1, 2, 3});
        Files.write(externalSong, new byte[]{4});

        Track downloaded = new Track(oldSong);
        Track external = new Track(externalSong);
        try (MusicDatabase database = new MusicDatabase(oldData.resolve("music-player.db"))) {
            database.saveTracks(List.of(downloaded, external));
            database.saveLyrics(downloaded, LrcParser.parse("测试", "[00:00.00]歌词"));
        }

        Path newData = root.resolve("ZA音乐/downloads");
        Path newDatabase = newData.resolve("music-player.db");
        Files.createDirectories(newData.getParent());
        Files.write(newData.getParent().resolve("ZA音乐.exe"), new byte[]{1});
        LegacyInstallMigration.migrateIfNeeded(newData.getParent(), newData, newDatabase);
        LegacyInstallMigration.migrateIfNeeded(newData.getParent(), newData, newDatabase);

        Path newSong = newData.resolve("旧下载.wav");
        assertTrue(Files.exists(oldSong));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(newSong));
        try (MusicDatabase database = new MusicDatabase(newDatabase)) {
            List<Track> tracks = database.loadTracks();
            assertTrue(tracks.stream().anyMatch(track -> track.path().equals(newSong)));
            assertTrue(tracks.stream().anyMatch(track -> track.path().equals(externalSong)));
            Track migrated = tracks.stream().filter(track -> track.path().equals(newSong)).findFirst().orElseThrow();
            assertTrue(database.loadLyrics(migrated).isPresent());
        }
    }

    @Test
    void doesNotOverwriteAnExistingNewLibrary() throws Exception {
        Path oldData = root.resolve("简约音乐播放器/downloads");
        Path newData = root.resolve("ZA音乐/downloads");
        Files.createDirectories(oldData);
        Files.createDirectories(newData);
        Files.write(oldData.resolve("music-player.db"), new byte[]{1});
        Files.write(newData.resolve("music-player.db"), new byte[]{2});
        Files.write(newData.getParent().resolve("ZA音乐.exe"), new byte[]{1});

        LegacyInstallMigration.migrateIfNeeded(newData.getParent(), newData, newData.resolve("music-player.db"));

        assertArrayEquals(new byte[]{2}, Files.readAllBytes(newData.resolve("music-player.db")));
    }
}
