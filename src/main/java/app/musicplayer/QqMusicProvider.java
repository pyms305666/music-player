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
import java.util.Optional;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
final class QqMusicProvider implements OnlineLyricsProvider {
    // 说明：这是注解，用来告诉 Java 或框架这个声明有特殊含义。
    @Override
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "?format=json&n=5&p=1&cr=1&g_tk=5381&t=0&loginUin=0&hostUin=0"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0&w="
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    + JsonSupport.encode(JsonSupport.queryText(track));
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String searchJson = get(httpClient, searchUrl);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String songData = JsonSupport.objectValue(searchJson, "song");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String list = JsonSupport.arrayValue(songData == null ? searchJson : songData, "list");

            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (String song : JsonSupport.splitTopLevelObjects(list)) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String songMid = JsonSupport.stringValue(song, "songmid");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (songMid == null || songMid.isBlank()) {
                    // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                    continue;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }

                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                        + "?format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0"
                        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                        + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
                        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                        + "&songmid=" + JsonSupport.encode(songMid);
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String lyricJson = get(httpClient, lyricUrl);
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String lyric = JsonSupport.htmlDecode(JsonSupport.stringValue(lyricJson, "lyric"));
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String decoded = JsonSupport.decodeBase64Text(lyric);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (decoded != null && !decoded.isBlank()) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    lyric = decoded;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (lyric == null || lyric.isBlank()) {
                    // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                    continue;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }

                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String songName = JsonSupport.stringValue(song, "songname");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String singer = firstSinger(song);
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String albumMid = JsonSupport.stringValue(song, "albummid");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String artworkUrl = albumMid == null || albumMid.isBlank()
                        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                        ? null
                        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                        : "https://y.gtimg.cn/music/photo_new/T002R800x800M000" + albumMid
                        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                        + ".jpg?max_age=2592000";
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return Optional.of(new OnlineLyricsResult(
                        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                        "QQ音乐：" + readableName(songName, singer), lyric, artworkUrl, 35));
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Optional.empty();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Optional.empty();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String get(HttpClient httpClient, String url) throws Exception {
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .timeout(Duration.ofSeconds(12))
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .header("User-Agent", "Mozilla/5.0 SimpleMusicPlayer/1.0")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .header("Referer", "https://y.qq.com/")
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
            // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
            throw new IllegalStateException("HTTP " + response.statusCode());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return response.body();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String firstSinger(String songJson) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String first = JsonSupport.firstObject(JsonSupport.arrayValue(songJson, "singer"));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String name = JsonSupport.stringValue(first, "name");
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return name == null || name.isBlank() ? null : name;
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
