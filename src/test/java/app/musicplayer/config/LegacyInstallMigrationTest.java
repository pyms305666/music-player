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
    @Test
    void snapshotsCommittedWalAndPreservesOriginalDatabase() throws Exception {
        Path source = root.resolve("ZA音乐/downloads");
        Files.createDirectories(source);
        Path song = Files.write(source.resolve("wal.mp3"), new byte[]{1});
        Path destination = root.resolve("user-data");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + source.resolve("music-player.db"))) {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("pragma journal_mode=wal")) { assertTrue(result.next()); }
            try (MusicDatabase database = new MusicDatabase(source.resolve("music-player.db"))) {
                database.saveTracks(List.of(new Track(song)));
                LegacyInstallMigration.migrateIfNeeded(root.resolve("ZA-Music"), destination, destination.resolve("music-player.db"));
                try (MusicDatabase migrated = new MusicDatabase(destination.resolve("music-player.db"))) {
                    assertTrue(migrated.loadTracks().stream().anyMatch(t -> t.path().equals(destination.resolve("wal.mp3"))));
                }
                assertTrue(database.loadTracks().stream().anyMatch(t -> t.path().equals(song)));
            }
        }
        try (var backups = Files.list(destination.resolve("backups"))) { assertTrue(backups.findAny().isPresent()); }
    }

    @Test
    void conflictingDestinationIsPreservedAndMigrationCanRetry() throws Exception {
        Path source = root.resolve("ZA音乐/downloads");
        Path destination = root.resolve("user-data");
        Files.createDirectories(source);
        Files.createDirectories(destination);
        Path song = Files.write(source.resolve("song.mp3"), new byte[]{1});
        Files.write(destination.resolve("song.mp3"), new byte[]{2});
        try (MusicDatabase db = new MusicDatabase(source.resolve("music-player.db"))) { db.saveTracks(List.of(new Track(song))); }
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
            LegacyInstallMigration.migrateIfNeeded(root.resolve("ZA-Music"), destination, destination.resolve("music-player.db")));
        assertArrayEquals(new byte[]{2}, Files.readAllBytes(destination.resolve("song.mp3")));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(destination.resolve("music-player.db")));
        Files.delete(destination.resolve("song.mp3"));
        LegacyInstallMigration.migrateIfNeeded(root.resolve("ZA-Music"), destination, destination.resolve("music-player.db"));
        assertTrue(Files.exists(destination.resolve("music-player.db")));
    }

}
