// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.io.IOException;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpClient;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.charset.StandardCharsets;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.Files;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.Path;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.time.Duration;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.List;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Optional;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.CompletableFuture;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public final class LyricsService {
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final MusicDatabase database;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final Path lyricsCacheDir;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final HttpClient httpClient = HttpClient.newBuilder()
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .connectTimeout(Duration.ofSeconds(8))
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            .build();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final List<OnlineLyricsProvider> onlineProviders = List.of(
            // 优先查能同时给出歌词和封面图的中文音乐站，再回退到原来的 LRCLIB。
            // 说明：创建一个新对象实例，让程序可以使用这个对象提供的功能。
            new NeteaseMusicProvider(),
            // 说明：创建一个新对象实例，让程序可以使用这个对象提供的功能。
            new QqMusicProvider(),
            // 说明：创建一个新对象实例，让程序可以使用这个对象提供的功能。
            new KugouMusicProvider(),
            // 说明：创建一个新对象实例，让程序可以使用这个对象提供的功能。
            new LrclibLyricsProvider()
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    );

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public LyricsService(MusicDatabase database, Path lyricsCacheDir) {
        // 说明：访问当前对象自己的字段或方法，避免和局部变量混淆。
        this.database = database;
        // 说明：访问当前对象自己的字段或方法，避免和局部变量混淆。
        this.lyricsCacheDir = lyricsCacheDir;
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.createDirectories(lyricsCacheDir);
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException ignored) {
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public CompletableFuture<LyricsLookupResult> findLyrics(Track track, Duration duration) {
        // 歌词加载优先级：
        // 1. 数据库缓存，最快，也能离线使用；
        // 2. 歌曲同目录同名 .lrc；
        // 3. 网易云音乐 / QQ 音乐 / 酷狗音乐 / LRCLIB 联网搜索。
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Optional<Lyrics> cachedLyrics = database.loadLyrics(track);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (cachedLyrics.isPresent()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return CompletableFuture.completedFuture(LyricsLookupResult.lyricsOnly(cachedLyrics.get()));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Optional<Lyrics> fileCacheLyrics = readCachedLyrics(track);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (fileCacheLyrics.isPresent()) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            database.saveLyrics(track, fileCacheLyrics.get());
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return CompletableFuture.completedFuture(LyricsLookupResult.lyricsOnly(fileCacheLyrics.get()));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Optional<Lyrics> localLyrics = readLocalLyrics(track);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (localLyrics.isPresent()) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            database.saveLyrics(track, localLyrics.get());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            saveCachedLyrics(track, localLyrics.get());
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return CompletableFuture.completedFuture(LyricsLookupResult.lyricsOnly(localLyrics.get()));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return searchOnlineAsync(track, duration);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public CompletableFuture<LyricsLookupResult> searchOnlineAsync(Track track, Duration duration) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return CompletableFuture.supplyAsync(() -> {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Optional<LyricsLookupResult> result = searchOnline(track, duration);
            // 联网搜到后立即写入缓存，后续播放同一首歌不再依赖网络。
            // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
            result.ifPresent(found -> {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                database.saveLyrics(track, found.lyrics());
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                saveCachedLyrics(track, found.lyrics());
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            });
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return result.orElseGet(() -> LyricsLookupResult.lyricsOnly(
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    Lyrics.empty("没有找到歌词，正在后台继续搜索")));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<Lyrics> readLocalLyrics(Track track) {
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path audioPath = track.path();
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        String fileName = audioPath.getFileName().toString();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int dot = fileName.lastIndexOf('.');
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (dot <= 0) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path lrcPath = audioPath.resolveSibling(fileName.substring(0, dot) + ".lrc");
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!Files.isRegularFile(lrcPath)) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 本地 .lrc 默认按 UTF-8 读取；如果用户的歌词文件是其他编码，可能需要另行转换。
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            String content = Files.readString(lrcPath, StandardCharsets.UTF_8);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(LrcParser.parse("本地歌词：" + lrcPath.getFileName(), content));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<LyricsLookupResult> searchOnline(Track track, Duration duration) {
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (OnlineLyricsProvider provider : onlineProviders) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Optional<OnlineLyricsResult> result = provider.search(track, duration, httpClient);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (result.isPresent() && result.get().hasLyrics()) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                OnlineLyricsResult found = result.get();
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                Lyrics lyrics = LrcParser.parse("联网歌词：" + found.source(), found.rawLyrics());
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return Optional.of(new LyricsLookupResult(lyrics, found.artworkUrl()));
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Optional.empty();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<Lyrics> readCachedLyrics(Track track) {
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path cachePath = lyricsCachePath(track);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!Files.isRegularFile(cachePath)) return Optional.empty();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            String content = Files.readString(cachePath, StandardCharsets.UTF_8);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(LrcParser.parse("缓存歌词：" + cachePath.getFileName(), content));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void saveCachedLyrics(Track track, Lyrics lyrics) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (track == null || lyrics == null || lyrics.rawText() == null || lyrics.rawText().isBlank()) return;
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.createDirectories(lyricsCacheDir);
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.writeString(lyricsCachePath(track), lyrics.rawText(), StandardCharsets.UTF_8);
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException ignored) {
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Path lyricsCachePath(Track track) {
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        String key = Integer.toHexString(track.path().toAbsolutePath().normalize().toString().hashCode());
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return lyricsCacheDir.resolve(key + ".lrc");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
