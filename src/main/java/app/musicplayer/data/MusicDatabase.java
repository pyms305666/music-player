package app.musicplayer.data;

import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.LyricLine;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.Track;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MusicDatabase implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(MusicDatabase.class.getName());
    private final Connection connection;

    public MusicDatabase(Path databasePath) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("pragma foreign_keys = on");
        }
        initialize();
    }

    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // tracks 保存已经导入过的歌曲。下次启动时会读取这些路径，
            // 但只有文件仍然存在且格式受支持时才恢复到播放列表。
            statement.executeUpdate("""
                    create table if not exists tracks (
                        path text primary key,
                        title text not null,
                        artist text not null,
                        duration_seconds integer,
                        imported_at text not null,
                        updated_at text not null
                    )
                    """);
            // lyrics 保存已经成功获取到的歌词。这样同一首歌再次播放时，
            // 可以直接从本地数据库读取，减少重复联网请求。
            statement.executeUpdate("""
                    create table if not exists lyrics (
                        track_path text primary key,
                        source text not null,
                        raw_lyrics text not null,
                        updated_at text not null,
                        foreign key(track_path) references tracks(path) on delete cascade
                    )
                    """);
        }
    }

    public synchronized List<Track> loadTracks() {
        List<Track> tracks = new ArrayList<>();
        String sql = "select path, title, artist from tracks order by imported_at, title";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                // Track 构造器会先从文件名推断信息，然后这里再用数据库保存的标题/歌手覆盖。
                Track track = new Track(Path.of(resultSet.getString("path")));
                track.updateMetadata(resultSet.getString("title"), resultSet.getString("artist"));
                tracks.add(track);
            }
        } catch (SQLException exception) {
            logFailure("读取歌曲列表", exception);
            return List.of();
        }

        return tracks;
    }

    public synchronized void saveTracks(List<Track> tracks) {
        String now = Instant.now().toString();
        // 使用 upsert：第一次导入插入，重复导入则更新标题和歌手，不制造重复歌曲记录。
        String sql = """
                insert into tracks(path, title, artist, duration_seconds, imported_at, updated_at)
                values(?, ?, ?, null, ?, ?)
                on conflict(path) do update set
                    title = excluded.title,
                    artist = excluded.artist,
                    updated_at = excluded.updated_at
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Track track : tracks) {
                statement.setString(1, track.path().toAbsolutePath().toString());
                statement.setString(2, track.title());
                statement.setString(3, track.artist());
                statement.setString(4, now);
                statement.setString(5, now);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            logFailure("批量保存歌曲", exception);
        }
    }

    public synchronized void saveTrack(Track track, java.time.Duration duration) {
        String now = Instant.now().toString();
        Long seconds = duration == null ? null : duration.toSeconds();
        // 单首歌曲播放后可能读到了更准确的音频元数据和时长，这里把它补写回数据库。
        String sql = """
                insert into tracks(path, title, artist, duration_seconds, imported_at, updated_at)
                values(?, ?, ?, ?, ?, ?)
                on conflict(path) do update set
                    title = excluded.title,
                    artist = excluded.artist,
                    duration_seconds = excluded.duration_seconds,
                    updated_at = excluded.updated_at
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            statement.setString(2, track.title());
            statement.setString(3, track.artist());
            if (seconds == null) {
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setLong(4, seconds);
            }
            statement.setString(5, now);
            statement.setString(6, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logFailure("保存歌曲元数据", exception);
        }
    }

    public synchronized Optional<Lyrics> loadLyrics(Track track) {
        String sql = "select source, raw_lyrics from lyrics where track_path = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    // 数据库保存的是原始歌词文本，读取时重新解析，保证时间轴高亮逻辑一致。
                    return Optional.of(LrcParser.parse(resultSet.getString("source"), resultSet.getString("raw_lyrics")));
                }
            }
        } catch (SQLException exception) {
            logFailure("读取歌词缓存", exception);
            return Optional.empty();
        }

        return Optional.empty();
    }

    public synchronized void saveLyrics(Track track, Lyrics lyrics) {
        // 只缓存真正找到的歌词。错误提示、空歌词或“继续搜索”这类占位内容不写入数据库。
        if (lyrics == null || lyrics.lines().isEmpty() || lyrics.source().startsWith("没有找到")
                || lyrics.source().startsWith("暂无") || lyrics.source().startsWith("歌词加载失败")) {
            return;
        }

        String rawLyrics = lyrics.rawText();
        if (rawLyrics == null || rawLyrics.isBlank()) {
            // 极少数情况下只有展示行没有原文，就退化为按行保存纯文本。
            rawLyrics = String.join("\n", lyrics.lines().stream().map(LyricLine::text).toList());
        }

        String sql = """
                insert into lyrics(track_path, source, raw_lyrics, updated_at)
                values(?, ?, ?, ?)
                on conflict(track_path) do update set
                    source = excluded.source,
                    raw_lyrics = excluded.raw_lyrics,
                    updated_at = excluded.updated_at
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            statement.setString(2, lyrics.source());
            statement.setString(3, rawLyrics);
            statement.setString(4, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            logFailure("保存歌词缓存", exception);
        }
    }

    public synchronized void removeTrack(Track track) {
        if (track == null) {
            return;
        }

        String absolutePath = track.path().toAbsolutePath().toString();
        try (PreparedStatement deleteLyrics = connection.prepareStatement("delete from lyrics where track_path = ?");
             PreparedStatement deleteTrack = connection.prepareStatement("delete from tracks where path = ?")) {
            deleteLyrics.setString(1, absolutePath);
            deleteLyrics.executeUpdate();
            deleteTrack.setString(1, absolutePath);
            deleteTrack.executeUpdate();
        } catch (SQLException exception) {
            logFailure("移除歌曲缓存", exception);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            logFailure("关闭数据库", exception);
        }
    }

    private void logFailure(String operation, SQLException exception) {
        LOGGER.log(Level.WARNING, operation + "失败", exception);
    }
}
