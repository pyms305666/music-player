package app.musicplayer.data;

import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicDatabaseTest {
    @TempDir
    Path tempDir;

    @Test void rollsBackWholeBatchAndRemainsUsableAfterFailure() throws Exception {
        Path path = tempDir.resolve("rollback.db");
        Track first = new Track(tempDir.resolve("first.mp3")), broken = new Track(tempDir.resolve("broken.mp3"));
        try (var database = new MusicDatabase(path)) {
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path); var statement = connection.createStatement()) {
                statement.execute("create trigger reject_broken before insert on tracks when new.path like '%broken.mp3' begin select raise(abort, 'fixture failure'); end");
            }
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> database.saveTracks(List.of(first, broken)));
            assertTrue(database.loadTracks().isEmpty());
            database.saveTracks(List.of(first));
            assertEquals(1, database.loadTracks().size());
        }
    }

    @Test void migratesOldLyricsTableAndRestoresArtworkOffline() throws Exception {
        Path path = tempDir.resolve("legacy.db");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path); var statement = connection.createStatement()) {
            statement.execute("create table lyrics(track_path text primary key, source text not null, raw_lyrics text not null, updated_at text not null)");
        }
        Track track = new Track(tempDir.resolve("cached.mp3"));
        try (var database = new MusicDatabase(path)) {
            database.saveTracks(List.of(track));
            var lyrics = LrcParser.parse("fixture", "[00:01]cached lyrics");
            database.saveLyrics(track, lyrics, "https://example.com/art.png");
            database.saveLyrics(track, lyrics);
        }
        try (var database = new MusicDatabase(path); var service = new app.musicplayer.lyrics.LyricsService(database, tempDir.resolve("lyrics"))) {
            var restored = service.findLyrics(track, null).get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("https://example.com/art.png", restored.artworkUrl());
            assertEquals("cached lyrics", restored.lyrics().lines().getFirst().text());
        }
    }

    @Test
    void persistsTracksLyricsAndRemoval() throws Exception {
        Path audio = Files.write(tempDir.resolve("歌手 - 歌曲.mp3"), new byte[]{1});
        Track track = new Track(audio);

        try (MusicDatabase database = new MusicDatabase(tempDir.resolve("music.db"))) {
            database.saveTracks(List.of(track));
            assertEquals(1, database.loadTracks().size());

            database.saveLyrics(track, LrcParser.parse("test", "[00:01.00]歌词"));
            assertTrue(database.loadLyrics(track).isPresent());

            database.removeTrack(track);
            assertTrue(database.loadTracks().isEmpty());
            assertTrue(database.loadLyrics(track).isEmpty());
        }
    }
}
