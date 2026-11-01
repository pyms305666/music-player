package app.musicplayer.data;

import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.LyricLine;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.Track;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 桌面 SQLite 存储实现。保存歌曲、歌词缓存和独立歌单，通过事务保持批量写入一致，并实现共享的 PlaylistStore 接口。 */
public final class MusicDatabase implements AutoCloseable, app.musicplayer.playlist.PlaylistStore {
    private static final Logger LOGGER = Logger.getLogger(MusicDatabase.class.getName());
    private final Connection connection;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public MusicDatabase(Path databasePath) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("pragma foreign_keys = on");
                statement.execute("pragma busy_timeout = 5000");
            }
            initialize();
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    /** 初始化应用目录或数据库结构，失败时交给启动流程处理。 */
    private void initialize() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists download_migrations(id text primary key,status text"
                            + " not null,payload text not null)");
            statement.executeUpdate(
                    "create unique index if not exists one_active_download_migration on"
                            + " download_migrations((1)) where status not in"
                            + " ('DONE','DONE_PRESERVED','CANCELLED')");
            statement.executeUpdate(
                    "create table if not exists named_playlists(id text primary key,metadata text"
                            + " not null)");
            statement.executeUpdate(
                    "create table if not exists named_playlist_items(playlist_id text not"
                        + " null,item_id text not null,position integer not null,payload text not"
                        + " null,primary key(playlist_id,item_id),foreign key(playlist_id)"
                        + " references named_playlists(id) on delete cascade)");
            // tracks 保存已经导入过的歌曲。下次启动时会读取这些路径，
            // 但只有文件仍然存在且格式受支持时才恢复到播放列表。
            statement.executeUpdate(
                    """
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
            statement.executeUpdate(
                    """
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
        try (Statement statement = connection.createStatement();
                ResultSet columns = statement.executeQuery("pragma table_info(lyrics)")) {
            while (columns.next())
                if ("artwork_url".equals(columns.getString("name"))) artworkColumn = true;
        }
        if (!artworkColumn)
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("alter table lyrics add column artwork_url text");
            }
    }

    /** 在事务内执行批量修改，失败时回滚，保持数据库可继续使用。 */
    private void transaction(SqlAction action) {
        try {
            connection.setAutoCommit(false);
            try {
                action.run();
                connection.commit();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("曲库写入失败", failure);
        }
    }

    /** 约定事务中执行的数据库操作，允许 SQL 异常交由事务回滚处理。 */
    @FunctionalInterface
    private interface SqlAction {
        /** 执行当前任务的实际工作；调度方决定所在线程。 */
        void run() throws SQLException;
    }

    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
    @Override
    public synchronized List<app.musicplayer.playlist.NamedPlaylist> loadPlaylists() {
        var result = new ArrayList<app.musicplayer.playlist.NamedPlaylist>();
        try (var query =
                        connection.prepareStatement(
                                "select id,metadata from named_playlists order by rowid");
                var rows = query.executeQuery();
                var items =
                        connection.prepareStatement(
                                "select payload from named_playlist_items where playlist_id=? order"
                                        + " by position")) {
            while (rows.next()) {
                var entries = new ArrayList<app.musicplayer.playlist.NamedPlaylist.Entry>();
                items.setString(1, rows.getString(1));
                try (var itemRows = items.executeQuery()) {
                    while (itemRows.next())
                        entries.add(
                                app.musicplayer.playlist.PlaylistCodec.entry(
                                        itemRows.getString(1)));
                }
                result.add(
                        app.musicplayer.playlist.PlaylistCodec.header(rows.getString(2), entries));
            }
        } catch (SQLException error) {
            throw new IllegalStateException("读取歌单失败", error);
        }
        return List.copyOf(result);
    }

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    @Override
    public synchronized void savePlaylist(app.musicplayer.playlist.NamedPlaylist playlist) {
        transaction(
                () -> {
                    try (var header =
                                    connection.prepareStatement(
                                            "insert into named_playlists(id,metadata) values(?,?)"
                                                    + " on conflict(id) do update set"
                                                    + " metadata=excluded.metadata");
                            var clear =
                                    connection.prepareStatement(
                                            "delete from named_playlist_items where playlist_id=?");
                            var item =
                                    connection.prepareStatement(
                                            "insert into"
                                                + " named_playlist_items(playlist_id,item_id,position,payload)"
                                                + " values(?,?,?,?)")) {
                        header.setString(1, playlist.id());
                        header.setString(
                                2, app.musicplayer.playlist.PlaylistCodec.header(playlist));
                        header.executeUpdate();
                        clear.setString(1, playlist.id());
                        clear.executeUpdate();
                        int position = 0;
                        for (var e : playlist.entries()) {
                            item.setString(1, playlist.id());
                            item.setString(2, e.id());
                            item.setInt(3, position++);
                            item.setString(4, app.musicplayer.playlist.PlaylistCodec.entry(e));
                            item.addBatch();
                        }
                        item.executeBatch();
                    }
                });
    }

    /** 保存歌单中指定条目的最新状态与配置。 */
    @Override
    public synchronized void updatePlaylistEntry(
            String playlistId, app.musicplayer.playlist.NamedPlaylist.Entry entry) {
        try (var item =
                connection.prepareStatement(
                        "update named_playlist_items set payload=? where playlist_id=? and"
                                + " item_id=?")) {
            item.setString(1, app.musicplayer.playlist.PlaylistCodec.entry(entry));
            item.setString(2, playlistId);
            item.setString(3, entry.id());
            if (item.executeUpdate() != 1) throw new IllegalStateException("歌单条目已移除");
        } catch (SQLException error) {
            throw new IllegalStateException("更新歌单失败", error);
        }
    }

    /** 删除歌单及其条目记录，保留关联音频和歌词。 */
    @Override
    public synchronized void deletePlaylist(String playlistId) {
        transaction(
                () -> {
                    try (var delete =
                            connection.prepareStatement("delete from named_playlists where id=?")) {
                        delete.setString(1, playlistId);
                        delete.executeUpdate();
                    }
                });
    }

    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
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

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    public synchronized void saveTracks(List<Track> tracks) {
        String now = Instant.now().toString();
        // 使用 upsert：第一次导入插入，重复导入则更新标题和歌手，不制造重复歌曲记录。
        String sql =
                """
                insert into tracks(path, title, artist, duration_seconds, imported_at, updated_at)
                values(?, ?, ?, null, ?, ?)
                on conflict(path) do update set
                    title = excluded.title,
                    artist = excluded.artist,
                    updated_at = excluded.updated_at
                """;

        transaction(
                () -> {
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

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    public synchronized void saveTrack(Track track, java.time.Duration duration) {
        String now = Instant.now().toString();
        Long seconds = duration == null ? null : duration.toSeconds();
        // 单首歌曲播放后可能读到了更准确的音频元数据和时长，这里把它补写回数据库。
        String sql =
                """
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

    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
    public synchronized Optional<Lyrics> loadLyrics(Track track) {
        return loadLyricsLookup(track).map(LyricsLookupResult::lyrics);
    }

    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
    public synchronized Optional<LyricsLookupResult> loadLyricsLookup(Track track) {
        String sql = "select source, raw_lyrics, artwork_url from lyrics where track_path = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    // 数据库保存的是原始歌词文本，读取时重新解析，保证时间轴高亮逻辑一致。
                    return Optional.of(
                            new LyricsLookupResult(
                                    LrcParser.parse(
                                            resultSet.getString("source"),
                                            resultSet.getString("raw_lyrics")),
                                    resultSet.getString("artwork_url")));
                }
            }
        } catch (SQLException exception) {
            logFailure("读取歌词缓存", exception);
            return Optional.empty();
        }

        return Optional.empty();
    }

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    public synchronized void saveLyrics(Track track, Lyrics lyrics) {
        saveLyrics(track, lyrics, null);
    }

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    public synchronized void saveLyrics(Track track, Lyrics lyrics, String artworkUrl) {
        // 只缓存真正找到的歌词。错误提示、空歌词或“继续搜索”这类占位内容不写入数据库。
        if (lyrics == null
                || lyrics.lines().isEmpty()
                || lyrics.source().startsWith("没有找到")
                || lyrics.source().startsWith("暂无")
                || lyrics.source().startsWith("歌词加载失败")) {
            return;
        }

        String rawLyrics = lyrics.rawText();
        if (rawLyrics == null || rawLyrics.isBlank()) {
            // 极少数情况下只有展示行没有原文，就退化为按行保存纯文本。
            rawLyrics = String.join("\n", lyrics.lines().stream().map(LyricLine::text).toList());
        }

        String sql =
                """
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

    /** 持久化的一次迁移；状态与 JSON 清单共同用于启动恢复。 */
    public record DownloadMigration(String id, String status, String payload) {}

    /** 读取待恢复任务；正常完成或用户明确保留旧副本的历史记录不阻塞启动。 */
    public synchronized Optional<DownloadMigration> pendingDownloadMigration() {
        try (var query =
                        connection.prepareStatement(
                                "select id,status,payload from download_migrations where status not"
                                        + " in ('DONE','DONE_PRESERVED','CANCELLED') order by rowid"
                                        + " limit 1");
                var rows = query.executeQuery()) {
            return rows.next()
                    ? Optional.of(
                            new DownloadMigration(
                                    rows.getString(1), rows.getString(2), rows.getString(3)))
                    : Optional.empty();
        } catch (SQLException failure) {
            throw new IllegalStateException("读取迁移任务失败", failure);
        }
    }

    /** 保存计划或逐文件进度；调用方负责仅更新自己获得的迁移任务。 */
    public synchronized void saveDownloadMigration(String id, String status, String payload) {
        try {
            writeMigration(id, status, payload);
        } catch (SQLException failure) {
            throw new IllegalStateException("保存迁移进度失败", failure);
        }
    }

    /** 不自行提交事务，供普通进度写入及迁移提交共同使用。 */
    private void writeMigration(String id, String status, String payload) throws SQLException {
        try (var statement =
                connection.prepareStatement(
                        "insert into download_migrations(id,status,payload) values(?,?,?) on"
                                + " conflict(id) do update set"
                                + " status=excluded.status,payload=excluded.payload")) {
            statement.setString(1, id);
            statement.setString(2, status);
            statement.setString(3, payload);
            statement.executeUpdate();
        }
    }

    /** 包含已提交 WAL 数据的一致性备份；必须在迁移修改任何路径之前调用。 */
    public synchronized void backup(Path target) {
        try (var statement = connection.createStatement()) {
            statement.execute(
                    "vacuum into '" + target.toAbsolutePath().toString().replace("'", "''") + "'");
        } catch (SQLException failure) {
            throw new IllegalStateException("备份曲库失败", failure);
        }
    }

    /** 一次提交歌曲、歌词、歌单及恢复标记；不能调用各自会提交的 saveTracks/savePlaylist。 */
    public synchronized void migrateDownloadPaths(
            String id, String payload, Map<Path, Path> paths, Path sourceRoot, Path targetRoot) {
        Map<Path, Path> normalized = new java.util.LinkedHashMap<>();
        paths.forEach(
                (oldPath, newPath) ->
                        normalized.put(
                                oldPath.toAbsolutePath().normalize(),
                                newPath.toAbsolutePath().normalize()));
        // 同一个源文件可能以短路径或链接路径保存在曲库中；保留它的真实路径供匹配。
        for (var entry : List.copyOf(normalized.entrySet())) {
            try {
                normalized.putIfAbsent(entry.getKey().toRealPath(), entry.getValue());
            } catch (java.io.IOException ignored) {
                // 源文件不可用时仍保留原始路径映射，由上层迁移校验处理。
            }
        }
        transaction(
                () -> {
                    Map<String, Path> records = new java.util.LinkedHashMap<>();
                    // 曲库中同一目标文件可能通过 Windows 短路径或目录链接登记。
                    // 复用已有记录的实际键，避免为同一音频插入第二条记录。
                    Map<Path, Path> existingTargets = new java.util.HashMap<>();
                    var destinations = new java.util.HashSet<>(normalized.values());
                    try (var query = connection.createStatement();
                            var rows = query.executeQuery("select path from tracks")) {
                        while (rows.next()) {
                            Path stored = localPath(rows.getString(1));
                            if (stored == null || normalized.containsKey(stored)) continue;
                            try {
                                Path actual = stored.toRealPath();
                                if (destinations.contains(actual)) {
                                    if (stored.equals(actual)) existingTargets.put(actual, stored);
                                    else existingTargets.putIfAbsent(actual, stored);
                                }
                            } catch (java.io.IOException ignored) {
                                // 已失效的外部歌曲不参与目标文件合并，保留原有记录。
                            }
                        }
                    }
                    normalized.replaceAll(
                            (source, target) -> existingTargets.getOrDefault(target, target));
                    try (var query = connection.createStatement();
                            var rows = query.executeQuery("select path from tracks")) {
                        while (rows.next()) {
                            String original = rows.getString(1);
                            Path replacement = mapped(original, normalized);
                            if (replacement != null && !localPath(original).equals(replacement))
                                records.put(original, replacement);
                        }
                    }
                    for (var entry : records.entrySet()) {
                        String oldPath = entry.getKey(), newPath = entry.getValue().toString();
                        if (oldPath.equals(newPath)) continue;
                        try (var copy =
                                connection.prepareStatement(
                                        """
                                        insert into tracks(path,title,artist,duration_seconds,imported_at,updated_at)
                                        select ?,title,artist,duration_seconds,imported_at,updated_at
                                        from tracks where path=?
                                        on conflict(path) do update set
                                            title=case when tracks.title='' then excluded.title else tracks.title end,
                                            artist=case when tracks.artist='' then excluded.artist else tracks.artist end,
                                            duration_seconds=coalesce(tracks.duration_seconds,excluded.duration_seconds)
                                        """)) {
                            copy.setString(1, newPath);
                            copy.setString(2, oldPath);
                            copy.executeUpdate();
                        }
                        try (var copy =
                                connection.prepareStatement(
                                        """
                                        insert into lyrics(track_path,source,raw_lyrics,updated_at,artwork_url)
                                        select ?,source,raw_lyrics,updated_at,artwork_url from lyrics where track_path=?
                                        on conflict(track_path) do update set
                                            artwork_url=coalesce(lyrics.artwork_url,excluded.artwork_url)
                                        """)) {
                            copy.setString(1, newPath);
                            copy.setString(2, oldPath);
                            copy.executeUpdate();
                        }
                    }
                    try (var query =
                                    connection.prepareStatement(
                                            "select playlist_id,item_id,payload from"
                                                    + " named_playlist_items");
                            var rows = query.executeQuery();
                            var update =
                                    connection.prepareStatement(
                                            "update named_playlist_items set payload=? where"
                                                    + " playlist_id=? and item_id=?")) {
                        while (rows.next()) {
                            JsonObject item =
                                    JsonParser.parseString(rows.getString(3)).getAsJsonObject();
                            if (item.has("location")) {
                                Path replacement =
                                        mapped(item.get("location").getAsString(), normalized);
                                if (replacement != null) {
                                    item.addProperty("location", replacement.toString());
                                    update.setString(1, item.toString());
                                    update.setString(2, rows.getString(1));
                                    update.setString(3, rows.getString(2));
                                    update.addBatch();
                                }
                            }
                        }
                        update.executeBatch();
                    }
                    try (var query =
                                    connection.prepareStatement(
                                            "select id,metadata from named_playlists");
                            var rows = query.executeQuery();
                            var update =
                                    connection.prepareStatement(
                                            "update named_playlists set metadata=? where id=?")) {
                        while (rows.next()) {
                            JsonObject header =
                                    JsonParser.parseString(rows.getString(2)).getAsJsonObject();
                            if (header.has("directory")) {
                                Path oldDirectory =
                                        localPath(header.get("directory").getAsString());
                                if (oldDirectory != null
                                        && migrationDirectory(oldDirectory, sourceRoot)) {
                                    header.addProperty(
                                            "directory",
                                            targetRoot
                                                    .resolve(
                                                            sourceRoot
                                                                    .toAbsolutePath()
                                                                    .normalize()
                                                                    .relativize(oldDirectory))
                                                    .toAbsolutePath()
                                                    .normalize()
                                                    .toString());
                                    update.setString(1, header.toString());
                                    update.setString(2, rows.getString(1));
                                    update.addBatch();
                                }
                            }
                        }
                        update.executeBatch();
                    }
                    try (var delete =
                            connection.prepareStatement("delete from tracks where path=?")) {
                        for (String oldPath : records.keySet()) {
                            delete.setString(1, oldPath);
                            delete.addBatch();
                        }
                        delete.executeBatch();
                    }
                    try (var statement = connection.createStatement();
                            var rows = statement.executeQuery("pragma foreign_key_check")) {
                        if (rows.next()) throw new SQLException("迁移后歌词关联无效");
                    }
                    writeMigration(id, "COMMITTED", payload);
                });
    }

    /** 只匹配明确的旧文件路径，目录外的导入歌曲保持原位置。 */
    private static Path mapped(String value, Map<Path, Path> paths) {
        Path path = localPath(value);
        if (path == null) return null;
        Path direct = paths.get(path);
        if (direct != null) return direct;
        try {
            return paths.get(path.toRealPath());
        } catch (java.io.IOException ignored) {
            return null;
        }
    }

    /** URI、空值和损坏路径不会被当作本地迁移对象。 */
    private static Path localPath(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (java.nio.file.InvalidPathException failure) {
            return null;
        }
    }

    /** 歌单目录经链接指向旧根之外时保持原配置，不能只按字符串前缀重定向。 */
    private static boolean migrationDirectory(Path directory, Path sourceRoot) throws SQLException {
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!directory.startsWith(root)) return false;
        try {
            Path expected = root.toRealPath().resolve(root.relativize(directory));
            return app.musicplayer.config.DownloadDirectorySettings.resolveExistingParent(directory)
                    .equals(expected);
        } catch (java.io.IOException failure) {
            throw new SQLException("检查歌单迁移目录失败：" + directory, failure);
        }
    }

    /** 迁移进程保留 SQLite 独占锁，阻止未支持实例锁的旧版本在复制期间写入旧路径。 */
    public synchronized void acquireMigrationExclusivity() {
        try (var statement = connection.createStatement()) {
            statement.execute("pragma locking_mode = exclusive");
            statement.execute("begin exclusive");
            statement.execute("commit");
        } catch (SQLException failure) {
            throw new IllegalStateException("曲库正在被其他实例使用，请关闭其他播放器后重试", failure);
        }
    }

    /** 迁移阶段结束后恢复普通连接模式；连接仍由创建方关闭。 */
    public synchronized void releaseMigrationExclusivity() {
        try (var statement = connection.createStatement()) {
            statement.execute("pragma locking_mode = normal");
            try (var rows = statement.executeQuery("select count(*) from download_migrations")) {
                rows.next();
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("释放曲库迁移锁失败", failure);
        }
    }

    /** 删除歌曲的曲库记录，并按本数据库约定清理关联缓存。 */
    public synchronized void removeTrack(Track track) {
        if (track == null) {
            return;
        }

        try (PreparedStatement statement =
                connection.prepareStatement("delete from tracks where path = ?")) {
            statement.setString(1, track.path().toAbsolutePath().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("移除歌曲失败", exception);
        }
    }

    /** 关闭 SQLite 连接；调用者应先完成同一 I/O 队列中的写入。 */
    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException exception) {
            logFailure("关闭数据库", exception);
        }
    }

    /** 记录数据库操作失败原因，供调用者诊断。 */
    private void logFailure(String operation, SQLException exception) {
        LOGGER.log(Level.WARNING, operation + "失败", exception);
    }
}
