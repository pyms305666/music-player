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
