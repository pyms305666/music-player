package app.musicplayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

final class QqMusicProvider implements OnlineLyricsProvider {
    @Override
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient) {
        try {
            String searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
                    + "?format=json&n=5&p=1&cr=1&g_tk=5381&t=0&loginUin=0&hostUin=0"
                    + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0&w="
                    + JsonSupport.encode(JsonSupport.queryText(track));
            String searchJson = get(httpClient, searchUrl);
            String songData = JsonSupport.objectValue(searchJson, "song");
            String list = JsonSupport.arrayValue(songData == null ? searchJson : songData, "list");

            for (String song : JsonSupport.splitTopLevelObjects(list)) {
                String songMid = JsonSupport.stringValue(song, "songmid");
                if (songMid == null || songMid.isBlank()) {
                    continue;
                }

                String lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                        + "?format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0"
                        + "&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
                        + "&songmid=" + JsonSupport.encode(songMid);
                String lyricJson = get(httpClient, lyricUrl);
                String lyric = JsonSupport.htmlDecode(JsonSupport.stringValue(lyricJson, "lyric"));
                String decoded = JsonSupport.decodeBase64Text(lyric);
                if (decoded != null && !decoded.isBlank()) {
                    lyric = decoded;
                }
                if (lyric == null || lyric.isBlank()) {
                    continue;
                }

                String songName = JsonSupport.stringValue(song, "songname");
                String singer = firstSinger(song);
                String albumMid = JsonSupport.stringValue(song, "albummid");
                String artworkUrl = albumMid == null || albumMid.isBlank()
                        ? null
                        : "https://y.gtimg.cn/music/photo_new/T002R800x800M000" + albumMid
                        + ".jpg?max_age=2592000";
                return Optional.of(new OnlineLyricsResult(
                        "QQ音乐：" + readableName(songName, singer), lyric, artworkUrl, 35));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static String get(HttpClient httpClient, String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0 SimpleMusicPlayer/1.0")
                .header("Referer", "https://y.qq.com/")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String firstSinger(String songJson) {
        String first = JsonSupport.firstObject(JsonSupport.arrayValue(songJson, "singer"));
        String name = JsonSupport.stringValue(first, "name");
        return name == null || name.isBlank() ? null : name;
    }

    private static String readableName(String trackName, String artistName) {
        if (trackName == null || trackName.isBlank()) {
            return "搜索结果";
        }
        if (artistName == null || artistName.isBlank()) {
            return trackName;
        }
        return artistName + " - " + trackName;
    }
}
