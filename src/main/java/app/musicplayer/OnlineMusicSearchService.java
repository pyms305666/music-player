// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.charset.StandardCharsets;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.Path;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.List;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Optional;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.CompletableFuture;

/**
 * Online music search service — delegates scraping to MusicCrawler,
 * delegates lyrics fetching to site-specific providers,
 * and reuses the crawler's shared HttpClient + CookieManager
 * so all requests carry the same session.
 */
// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public final class OnlineMusicSearchService {

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final MusicCrawler crawler = new MusicCrawler();

    // ---- search / download (delegated to crawler) ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public CompletableFuture<List<OnlineTrackInfo>> searchAsync(String query) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return CompletableFuture.supplyAsync(() -> crawler.search(query));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public CompletableFuture<String> resolveDownloadUrlAsync(OnlineTrackInfo info) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return CompletableFuture.supplyAsync(() -> crawler.resolveDownloadUrl(info));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public CompletableFuture<Path> downloadAsync(OnlineTrackInfo info, Path targetDir) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return CompletableFuture.supplyAsync(() -> {
            // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
            try {
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return crawler.download(info, targetDir);
            // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
            } catch (Exception e) {
                // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
                throw new RuntimeException(e);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ---- lyrics preview (reuses crawler's HTTP session) ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public CompletableFuture<LyricsLookupResult> loadPreviewAsync(OnlineTrackInfo trackInfo) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return CompletableFuture.supplyAsync(() ->
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            loadPreview(trackInfo).orElseGet(() ->
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                LyricsLookupResult.lyricsOnly(Lyrics.empty("在线结果暂无歌词"))));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<LyricsLookupResult> loadPreview(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null || info.source() == null) return Optional.empty();
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return switch (info.source()) {
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQMP3"      -> loadQqmp3Preview(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "网易云音乐" -> loadNeteasePreview(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "QQ音乐"     -> loadQqPreview(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "酷狗音乐"   -> loadKugouPreview(info);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            default          -> Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        };
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ---- QQMP3 lyrics ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<LyricsLookupResult> loadQqmp3Preview(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = crawler.fetchQqmp3SongData(info.primaryId());
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String data = JsonSupport.objectValue(json, "data");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String lyric = JsonSupport.stringValue(data == null ? json : data, "lrc");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String artwork = info.artworkUrl();
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (artwork == null || artwork.isBlank()) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                artwork = JsonSupport.stringValue(data == null ? json : data, "pic");
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(new LyricsLookupResult(
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    LrcParser.parse("在线预览：QQMP3：" + rd(info.title(), info.artist()), lyric),
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    artwork));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) { return Optional.empty(); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ---- NetEase lyrics ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<LyricsLookupResult> loadNeteasePreview(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch(
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                "https://music.163.com/api/song/lyric?id="
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + enc(info.primaryId()) + "&lv=1&kv=1&tv=-1",
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                "https://music.163.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String lrcObj = JsonSupport.objectValue(json, "lrc");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String lyric  = JsonSupport.stringValue(
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                lrcObj == null ? json : lrcObj, "lyric");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(new LyricsLookupResult(
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                LrcParser.parse("在线预览：网易云音乐：" + rd(info.title(), info.artist()), lyric),
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                info.artworkUrl()));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) { return Optional.empty(); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ---- QQ lyrics ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<LyricsLookupResult> loadQqPreview(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String json = fetch(
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "?format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&inCharset=utf8&outCharset=utf-8&notice=0"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&platform=yqq.json&needNewCode=0"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&songmid=" + enc(info.primaryId()),
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                "https://y.qq.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String lyric = JsonSupport.htmlDecode(
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                JsonSupport.stringValue(json, "lyric"));
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String decoded = JsonSupport.decodeBase64Text(lyric);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (decoded != null && !decoded.isBlank()) lyric = decoded;
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(new LyricsLookupResult(
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                LrcParser.parse("在线预览：QQ音乐：" + rd(info.title(), info.artist()), lyric),
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                info.artworkUrl()));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) { return Optional.empty(); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ---- Kugou lyrics ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<LyricsLookupResult> loadKugouPreview(OnlineTrackInfo info) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String playJson = fetch(
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                playDataUrl(info.primaryId(), info.secondaryId()),
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                "https://www.kugou.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String lyric = JsonSupport.stringValue(playJson, "lyrics");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (lyric == null || lyric.isBlank())
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                lyric = downloadKugouLrc(info);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String art = info.artworkUrl();
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (art == null || art.isBlank())
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                art = value(playJson, "img", "album_img", "Image", "image");
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(new LyricsLookupResult(
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                LrcParser.parse("在线预览：酷狗音乐：" + rd(info.title(), info.artist()), lyric),
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                art));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) { return Optional.empty(); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String downloadKugouLrc(OnlineTrackInfo info) throws Exception {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String searchJson = fetch(
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc"
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                + "&keyword=" + enc(queryText(info))
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                + "&duration=0&hash=" + enc(info.primaryId()),
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            "https://www.kugou.com/");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String first = JsonSupport.firstObject(
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            JsonSupport.arrayValue(searchJson, "candidates"));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String id = value(first, "id");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String ak = value(first, "accesskey");
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (id == null || ak == null) return null;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String dl = fetch(
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            "https://lyrics.kugou.com/download?ver=1&client=pc"
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                + "&fmt=lrc&charset=utf8&id=" + enc(id)
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                + "&accesskey=" + enc(ak),
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            "https://www.kugou.com/");
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return JsonSupport.decodeBase64Text(
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            JsonSupport.stringValue(dl, "content"));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ---- shared HTTP helper (reuses crawler's session) ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String fetch(String url, String referer) throws Exception {
        // Delegates to crawler's fetch which uses the shared HttpClient + CookieManager.
        // This ensures lyrics preview requests carry the same session cookies
        // as search/download requests, reducing anti-crawling blocks.
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return crawler.fetch(url, referer);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ---- tiny utilities ----

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String enc(String s) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return java.net.URLEncoder.encode(
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            s == null ? "" : s, StandardCharsets.UTF_8);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String rd(String title, String artist) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return (artist == null || artist.isBlank() ? "" : artist + " - ")
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            + (title == null ? "" : title);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String queryText(OnlineTrackInfo i) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String a = i.artist() == null ? "" : i.artist().trim();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String t = i.title()  == null ? "" : i.title().trim();
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return a.isBlank() ? t : a + " " + t;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String playDataUrl(String hash, String aid) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash="
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            + enc(hash)
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            + (aid != null && !aid.isBlank() ? "&album_id=" + enc(aid) : "");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String value(String json, String... names) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (json == null) return null;
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (String n : names) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String t = JsonSupport.stringValue(json, n);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (t != null && !t.isBlank()) return t;
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            t = JsonSupport.numberValue(json, n);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (t != null && !t.isBlank()) return t;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
