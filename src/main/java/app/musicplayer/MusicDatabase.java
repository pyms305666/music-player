// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.Path;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.sql.Connection;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.sql.DriverManager;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.sql.PreparedStatement;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.sql.ResultSet;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.sql.SQLException;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.sql.Statement;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.time.Instant;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.ArrayList;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.List;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Optional;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public final class MusicDatabase implements AutoCloseable {
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final Connection connection;

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public MusicDatabase(Path databasePath) throws SQLException {
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (Statement statement = connection.createStatement()) {
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
            statement.execute("pragma foreign_keys = on");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        initialize();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void initialize() throws SQLException {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (Statement statement = connection.createStatement()) {
            // tracks 保存已经导入过的歌曲。下次启动时会读取这些路径，
            // 但只有文件仍然存在且格式受支持时才恢复到播放列表。
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
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
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
            statement.executeUpdate("""
                    create table if not exists lyrics (
                        track_path text primary key,
                        source text not null,
                        raw_lyrics text not null,
                        updated_at text not null,
                        foreign key(track_path) references tracks(path) on delete cascade
                    )
                    """);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public synchronized List<Track> loadTracks() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<Track> tracks = new ArrayList<>();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String sql = "select path, title, artist from tracks order by imported_at, title";

        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (PreparedStatement statement = connection.prepareStatement(sql);
             // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
             ResultSet resultSet = statement.executeQuery()) {
            // 说明：while 循环：只要条件继续成立，就反复执行代码块。
            while (resultSet.next()) {
                // Track 构造器会先从文件名推断信息，然后这里再用数据库保存的标题/歌手覆盖。
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Track track = new Track(Path.of(resultSet.getString("path")));
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                track.updateMetadata(resultSet.getString("title"), resultSet.getString("artist"));
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                tracks.add(track);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (SQLException ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return List.of();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return tracks;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public synchronized void saveTracks(List<Track> tracks) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String now = Instant.now().toString();
        // 使用 upsert：第一次导入插入，重复导入则更新标题和歌手，不制造重复歌曲记录。
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String sql = """
                insert into tracks(path, title, artist, duration_seconds, imported_at, updated_at)
                values(?, ?, ?, null, ?, ?)
                on conflict(path) do update set
                    title = excluded.title,
                    artist = excluded.artist,
                    updated_at = excluded.updated_at
                """;

        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (Track track : tracks) {
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                statement.setString(1, track.path().toAbsolutePath().toString());
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                statement.setString(2, track.title());
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                statement.setString(3, track.artist());
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                statement.setString(4, now);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                statement.setString(5, now);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                statement.addBatch();
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
            statement.executeBatch();
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (SQLException ignored) {
            // A cache failure should not stop playback.
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public synchronized void saveTrack(Track track, java.time.Duration duration) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String now = Instant.now().toString();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Long seconds = duration == null ? null : duration.toSeconds();
        // 单首歌曲播放后可能读到了更准确的音频元数据和时长，这里把它补写回数据库。
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String sql = """
                insert into tracks(path, title, artist, duration_seconds, imported_at, updated_at)
                values(?, ?, ?, ?, ?, ?)
                on conflict(path) do update set
                    title = excluded.title,
                    artist = excluded.artist,
                    duration_seconds = excluded.duration_seconds,
                    updated_at = excluded.updated_at
                """;

        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            statement.setString(1, track.path().toAbsolutePath().toString());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            statement.setString(2, track.title());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            statement.setString(3, track.artist());
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (seconds == null) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                statement.setNull(4, java.sql.Types.INTEGER);
            // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
            } else {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                statement.setLong(4, seconds);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            statement.setString(5, now);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            statement.setString(6, now);
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
            statement.executeUpdate();
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (SQLException ignored) {
            // A cache failure should not stop playback.
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public synchronized Optional<Lyrics> loadLyrics(Track track) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String sql = "select source, raw_lyrics from lyrics where track_path = ?";

        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            statement.setString(1, track.path().toAbsolutePath().toString());
            // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
            try (ResultSet resultSet = statement.executeQuery()) {
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (resultSet.next()) {
                    // 数据库保存的是原始歌词文本，读取时重新解析，保证时间轴高亮逻辑一致。
                    // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                    return Optional.of(LrcParser.parse(resultSet.getString("source"), resultSet.getString("raw_lyrics")));
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (SQLException ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Optional.empty();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public synchronized void saveLyrics(Track track, Lyrics lyrics) {
        // 只缓存真正找到的歌词。错误提示、空歌词或“继续搜索”这类占位内容不写入数据库。
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyrics == null || lyrics.lines().isEmpty() || lyrics.source().startsWith("没有找到")
                // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
                || lyrics.source().startsWith("暂无") || lyrics.source().startsWith("歌词加载失败")) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String rawLyrics = lyrics.rawText();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (rawLyrics == null || rawLyrics.isBlank()) {
            // 极少数情况下只有展示行没有原文，就退化为按行保存纯文本。
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            rawLyrics = String.join("\n", lyrics.lines().stream().map(LyricLine::text).toList());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String sql = """
                insert into lyrics(track_path, source, raw_lyrics, updated_at)
                values(?, ?, ?, ?)
                on conflict(track_path) do update set
                    source = excluded.source,
                    raw_lyrics = excluded.raw_lyrics,
                    updated_at = excluded.updated_at
                """;

        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            statement.setString(1, track.path().toAbsolutePath().toString());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            statement.setString(2, lyrics.source());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            statement.setString(3, rawLyrics);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            statement.setString(4, Instant.now().toString());
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
            statement.executeUpdate();
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (SQLException ignored) {
            // A cache failure should not stop playback.
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public synchronized void removeTrack(Track track) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (track == null) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        String absolutePath = track.path().toAbsolutePath().toString();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (PreparedStatement deleteLyrics = connection.prepareStatement("delete from lyrics where track_path = ?");
             // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
             PreparedStatement deleteTrack = connection.prepareStatement("delete from tracks where path = ?")) {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            deleteLyrics.setString(1, absolutePath);
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
            deleteLyrics.executeUpdate();
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            deleteTrack.setString(1, absolutePath);
            // 说明：数据库操作相关代码，用来保存或读取歌曲和歌词缓存。
            deleteTrack.executeUpdate();
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (SQLException ignored) {
            // A cache failure should not stop playback.
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：这是注解，用来告诉 Java 或框架这个声明有特殊含义。
    @Override
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public synchronized void close() {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            connection.close();
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (SQLException ignored) {
            // Nothing useful to do on shutdown.
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
