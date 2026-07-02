// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.URI;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpClient;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpRequest;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpResponse;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.charset.StandardCharsets;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.time.Duration;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Comparator;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Optional;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
final class LrclibLyricsProvider implements OnlineLyricsProvider {
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String SEARCH_URL = "https://lrclib.net/api/search";

    // 说明：这是注解，用来告诉 Java 或框架这个声明有特殊含义。
    @Override
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            StringBuilder query = new StringBuilder();
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            appendQuery(query, "track_name", track.title());
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (track.artist() != null && !track.artist().isBlank() && !"未知歌手".equals(track.artist())) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                appendQuery(query, "artist_name", track.artist());
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (duration != null && !duration.isNegative() && !duration.isZero()) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                appendQuery(query, "duration", String.valueOf(duration.toSeconds()));
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }

            // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
            HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH_URL + "?" + query))
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    .timeout(Duration.ofSeconds(12))
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    .header("User-Agent", "SimpleMusicPlayer/1.0 (JavaFX)")
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    .GET()
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    .build();

            // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
            HttpResponse<String> response = httpClient.send(request,
                    // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return Optional.empty();
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }

            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return JsonSupport.splitTopLevelObjects(response.body()).stream()
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    .map(this::toCandidate)
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    .flatMap(Optional::stream)
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    .max(Comparator.comparing(OnlineLyricsResult::score));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static void appendQuery(StringBuilder query, String key, String value) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (query.length() > 0) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            query.append('&');
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        query.append(JsonSupport.encode(key));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        query.append('=');
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        query.append(JsonSupport.encode(value));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Optional<OnlineLyricsResult> toCandidate(String objectJson) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String syncedLyrics = JsonSupport.stringValue(objectJson, "syncedLyrics");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String plainLyrics = JsonSupport.stringValue(objectJson, "plainLyrics");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String trackName = JsonSupport.stringValue(objectJson, "trackName");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String artistName = JsonSupport.stringValue(objectJson, "artistName");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String source = "LRCLIB：" + readableName(trackName, artistName);

        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (syncedLyrics != null && !syncedLyrics.isBlank()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(new OnlineLyricsResult(source, syncedLyrics, null, 20));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (plainLyrics != null && !plainLyrics.isBlank()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.of(new OnlineLyricsResult(source, plainLyrics, null, 10));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Optional.empty();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String readableName(String trackName, String artistName) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (trackName == null || trackName.isBlank()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return "搜索结果";
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (artistName == null || artistName.isBlank()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return trackName;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return artistName + " - " + trackName;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
