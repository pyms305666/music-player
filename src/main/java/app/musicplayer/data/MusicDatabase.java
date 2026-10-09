package app.musicplayer.data;

import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.LyricLine;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
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

public final class MusicDatabase implements AutoCloseable, app.musicplayer.playlist.PlaylistStore {
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
            statement.executeUpdate("create table if not exists named_playlists(id text primary key,metadata text not null)");
            statement.executeUpdate("create table if not exists named_playlist_items(playlist_id text not null,item_id text not null,position integer not null,payload text not null,primary key(playlist_id,item_id),foreign key(playlist_id) references named_playlists(id) on delete cascade)");
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
        boolean artworkColumn = false;
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("pragma table_info(lyrics)")) {
            while (columns.next()) if ("artwork_url".equals(columns.getString("name"))) artworkColumn = true;
        }
        if (!artworkColumn) try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("alter table lyrics add column artwork_url text");
        }
    }

    private void transaction(SqlAction action) {
        try {
            connection.setAutoCommit(false);
            try { action.run(); connection.commit(); }
            catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally { connection.setAutoCommit(true); }
        } catch (SQLException failure) { throw new IllegalStateException("曲库写入失败", failure); }
    }

    @FunctionalInterface private interface SqlAction { void run() throws SQLException; }

    @Override public synchronized List<app.musicplayer.playlist.NamedPlaylist> loadPlaylists() {
        var result = new ArrayList<app.musicplayer.playlist.NamedPlaylist>();
        try (var query=connection.prepareStatement("select id,metadata from named_playlists order by rowid");var rows=query.executeQuery();
             var items=connection.prepareStatement("select payload from named_playlist_items where playlist_id=? order by position")) {
            while(rows.next()) {
                var entries=new ArrayList<app.musicplayer.playlist.NamedPlaylist.Entry>();items.setString(1,rows.getString(1));
                try(var itemRows=items.executeQuery()){while(itemRows.next())entries.add(app.musicplayer.playlist.PlaylistCodec.entry(itemRows.getString(1)));}
                result.add(app.musicplayer.playlist.PlaylistCodec.header(rows.getString(2),entries));
            }
        } catch(SQLException error){throw new IllegalStateException("读取歌单失败",error);}return List.copyOf(result);
    }
    @Override public synchronized void savePlaylist(app.musicplayer.playlist.NamedPlaylist playlist) {
        transaction(() -> {
            try(var header=connection.prepareStatement("insert into named_playlists(id,metadata) values(?,?) on conflict(id) do update set metadata=excluded.metadata");
                var clear=connection.prepareStatement("delete from named_playlist_items where playlist_id=?");
                var item=connection.prepareStatement("insert into named_playlist_items(playlist_id,item_id,position,payload) values(?,?,?,?)")) {
                header.setString(1,playlist.id());header.setString(2,app.musicplayer.playlist.PlaylistCodec.header(playlist));header.executeUpdate();
                clear.setString(1,playlist.id());clear.executeUpdate();int position=0;
                for(var e:playlist.entries()){item.setString(1,playlist.id());item.setString(2,e.id());item.setInt(3,position++);item.setString(4,app.musicplayer.playlist.PlaylistCodec.entry(e));item.addBatch();}
                item.executeBatch();
            }
        });
    }
    @Override public synchronized void updatePlaylistEntry(String playlistId,app.musicplayer.playlist.NamedPlaylist.Entry entry) {
        try(var item=connection.prepareStatement("update named_playlist_items set payload=? where playlist_id=? and item_id=?")){
            item.setString(1,app.musicplayer.playlist.PlaylistCodec.entry(entry));item.setString(2,playlistId);item.setString(3,entry.id());
            if(item.executeUpdate()!=1)throw new IllegalStateException("歌单条目已移除");
        }catch(SQLException error){throw new IllegalStateException("更新歌单失败",error);}
    }
    @Override public synchronized void deletePlaylist(String playlistId) {
        transaction(() -> {try(var delete=connection.prepareStatement("delete from named_playlists where id=?")){delete.setString(1,playlistId);delete.executeUpdate();}});
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
            throw new IllegalStateException("读取曲库失败", exception);
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

        transaction(() -> {
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
        }
        });
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
            throw new IllegalStateException("保存歌曲元数据失败", exception);
        }
    }

    public synchronized Optional<Lyrics> loadLyrics(Track track) {
        return loadLyricsLookup(track).map(LyricsLookupResult::lyrics);
    }

    public synchronized Optional<LyricsLookupResult> loadLyricsLookup(Track track) {
        String sql = "select source, raw_lyrics, artwork_url from lyrics where track_path = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    // 数据库保存的是原始歌词文本，读取时重新解析，保证时间轴高亮逻辑一致。
                    return Optional.of(new LyricsLookupResult(LrcParser.parse(resultSet.getString("source"), resultSet.getString("raw_lyrics")), resultSet.getString("artwork_url")));
                }
            }
        } catch (SQLException exception) {
            logFailure("读取歌词缓存", exception);
            return Optional.empty();
        }

        return Optional.empty();
    }

    public synchronized void saveLyrics(Track track, Lyrics lyrics) { saveLyrics(track, lyrics, null); }

    public synchronized void saveLyrics(Track track, Lyrics lyrics, String artworkUrl) {
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
                insert into lyrics(track_path, source, raw_lyrics, updated_at, artwork_url)
                values(?, ?, ?, ?, ?)
                on conflict(track_path) do update set
                    source = excluded.source,
                    raw_lyrics = excluded.raw_lyrics,
                    updated_at = excluded.updated_at,
                    artwork_url = coalesce(excluded.artwork_url, lyrics.artwork_url)
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            statement.setString(2, lyrics.source());
            statement.setString(3, rawLyrics);
            statement.setString(4, Instant.now().toString());
            statement.setString(5, artworkUrl);
            statement.executeUpdate();
        } catch (SQLException exception) {
            logFailure("保存歌词缓存", exception);
        }
    }

    public synchronized void removeTrack(Track track) {
        if (track == null) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement("delete from tracks where path = ?")) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            statement.executeUpdate();
        } catch (SQLException exception) { throw new IllegalStateException("移除歌曲失败", exception); }
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
