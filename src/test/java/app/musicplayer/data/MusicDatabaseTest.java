package app.musicplayer.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.Track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MusicDatabase 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：桌面 SQLite
 * 存储实现。保存歌曲、歌词缓存和独立歌单，通过事务保持批量写入一致，并实现共享的 PlaylistStore 接口。
 */
class MusicDatabaseTest {
    @TempDir Path tempDir;

    /** 验证整个写入批次回滚，数据库失败后仍可继续使用。 */
    @Test
    void rollsBackWholeBatchAndRemainsUsableAfterFailure() throws Exception {
        Path path = tempDir.resolve("rollback.db");
        Track first = new Track(tempDir.resolve("first.mp3")),
                broken = new Track(tempDir.resolve("broken.mp3"));
        try (var database = new MusicDatabase(path)) {
            try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path);
                    var statement = connection.createStatement()) {
                statement.execute(
                        "create trigger reject_broken before insert on tracks when new.path like"
                            + " '%broken.mp3' begin select raise(abort, 'fixture failure'); end");
            }
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class, () -> database.saveTracks(List.of(first, broken)));
            assertTrue(database.loadTracks().isEmpty());
            database.saveTracks(List.of(first));
            assertEquals(1, database.loadTracks().size());
        }
    }

    /** 验证旧歌词表迁移并可离线恢复封面。 */
    @Test
    void migratesOldLyricsTableAndRestoresArtworkOffline() throws Exception {
        Path path = tempDir.resolve("legacy.db");
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + path);
                var statement = connection.createStatement()) {
            statement.execute(
                    "create table lyrics(track_path text primary key, source text not null,"
                            + " raw_lyrics text not null, updated_at text not null)");
        }
        Track track = new Track(tempDir.resolve("cached.mp3"));
        try (var database = new MusicDatabase(path)) {
            database.saveTracks(List.of(track));
            var lyrics = LrcParser.parse("fixture", "[00:01]cached lyrics");
            database.saveLyrics(track, lyrics, "https://example.com/art.png");
            database.saveLyrics(track, lyrics);
        }
        try (var database = new MusicDatabase(path);
                var service =
                        new app.musicplayer.lyrics.LyricsService(
                                database, tempDir.resolve("lyrics"))) {
            var restored =
                    service.findLyrics(track, null).get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("https://example.com/art.png", restored.artworkUrl());
            assertEquals("cached lyrics", restored.lyrics().lines().getFirst().text());
        }
    }

    /** 验证歌曲和歌词持久化以及移除后的对应状态。 */
    @Test
    void persistsTracksLyricsAndRemoval() throws Exception {
        Path audio = Files.write(tempDir.resolve("歌手 - 歌曲.mp3"), new byte[] {1});
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
