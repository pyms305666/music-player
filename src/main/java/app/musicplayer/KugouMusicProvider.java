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
final class KugouMusicProvider implements OnlineLyricsProvider {
    // 说明：这是注解，用来告诉 Java 或框架这个声明有特殊含义。
    @Override
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String searchUrl = "https://songsearch.kugou.com/song_search_v2"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "?page=1&pagesize=5&userid=-1&clientver=&platform=WebFilter&tag=em"
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    + "&filter=2&iscorrection=1&privilege_filter=0&keyword="
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    + JsonSupport.encode(JsonSupport.queryText(track));
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String searchJson = get(httpClient, searchUrl, "https://www.kugou.com/");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String list = JsonSupport.arrayValue(searchJson, "lists");

            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (String song : JsonSupport.splitTopLevelObjects(list)) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String hash = value(song, "FileHash", "filehash", "Hash", "hash");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (hash == null || hash.isBlank()) {
                    // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                    continue;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }

                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String albumId = value(song, "AlbumID", "album_id", "AlbumId");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String playJson = get(httpClient, playDataUrl(hash, albumId), "https://www.kugou.com/");
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String lyric = JsonSupport.stringValue(playJson, "lyrics");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (lyric == null || lyric.isBlank()) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    lyric = downloadLrc(httpClient, track, duration, hash);
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (lyric == null || lyric.isBlank()) {
                    // 说明：跳过本轮循环剩余代码，直接进入下一轮循环。
                    continue;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }

                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String songName = JsonSupport.stripHtml(value(song, "SongName", "songname", "FileName", "filename"));
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String singer = JsonSupport.stripHtml(value(song, "SingerName", "singername"));
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String artworkUrl = value(playJson, "img", "album_img", "Image", "image");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if ((artworkUrl == null || artworkUrl.isBlank())) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    artworkUrl = value(song, "Image", "image");
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }

                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return Optional.of(new OnlineLyricsResult(
                        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                        "酷狗音乐：" + readableName(songName, singer), lyric, artworkUrl, 30));
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
    private static String playDataUrl(String hash, String albumId) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StringBuilder url = new StringBuilder("https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash=")
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                .append(JsonSupport.encode(hash));
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (albumId != null && !albumId.isBlank()) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            url.append("&album_id=").append(JsonSupport.encode(albumId));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return url.toString();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String downloadLrc(HttpClient httpClient, Track track, Duration duration, String hash) throws Exception {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        long millis = duration == null || duration.isNegative() || duration.isZero() ? 0 : duration.toMillis();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String searchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword="
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                + JsonSupport.encode(JsonSupport.queryText(track)) + "&duration=" + millis + "&hash="
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                + JsonSupport.encode(hash);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String searchJson = get(httpClient, searchUrl, "https://www.kugou.com/");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String first = JsonSupport.firstObject(JsonSupport.arrayValue(searchJson, "candidates"));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String id = value(first, "id");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String accessKey = value(first, "accesskey");
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (id == null || accessKey == null) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id="
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                + JsonSupport.encode(id) + "&accesskey=" + JsonSupport.encode(accessKey);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String downloadJson = get(httpClient, downloadUrl, "https://www.kugou.com/");
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return JsonSupport.decodeBase64Text(JsonSupport.stringValue(downloadJson, "content"));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String get(HttpClient httpClient, String url, String referer) throws Exception {
        // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .timeout(Duration.ofSeconds(12))
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .header("User-Agent", "Mozilla/5.0 SimpleMusicPlayer/1.0")
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                .header("Referer", referer)
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
    private static String value(String json, String... names) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (json == null) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return null;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (String name : names) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String text = JsonSupport.stringValue(json, name);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (text != null && !text.isBlank()) {
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return text;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            text = JsonSupport.numberValue(json, name);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (text != null && !text.isBlank()) {
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return text;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return null;
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
