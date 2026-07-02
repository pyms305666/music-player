package app.musicplayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

final class NeteaseMusicProvider implements OnlineLyricsProvider {
    @Override
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient) {
        try {
            String searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&type=1&offset=0&limit=5&s="
                    + JsonSupport.encode(JsonSupport.queryText(track));
            String searchJson = get(httpClient, searchUrl, "https://music.163.com/");
            String songs = JsonSupport.arrayValue(searchJson, "songs");

            for (String song : JsonSupport.splitTopLevelObjects(songs)) {
                String id = JsonSupport.numberValue(song, "id");
                if (id == null || id.isBlank()) {
                    continue;
                }

                String lyricJson = get(httpClient,
                        "https://music.163.com/api/song/lyric?id=" + JsonSupport.encode(id) + "&lv=1&kv=1&tv=-1",
                        "https://music.163.com/");
                String lrcObject = JsonSupport.objectValue(lyricJson, "lrc");
                String lyric = JsonSupport.stringValue(lrcObject == null ? lyricJson : lrcObject, "lyric");
                if (lyric == null || lyric.isBlank()) {
                    continue;
                }

                String name = JsonSupport.stringValue(song, "name");
                String artist = firstArtist(song, "artists");
                String album = JsonSupport.objectValue(song, "album");
                String artworkUrl = JsonSupport.stringValue(album == null ? song : album, "picUrl");
                return Optional.of(new OnlineLyricsResult(
                        "网易云音乐：" + readableName(name, artist), lyric, artworkUrl, 40));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static String get(HttpClient httpClient, String url, String referer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0 SimpleMusicPlayer/1.0")
                .header("Referer", referer)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private static String firstArtist(String songJson, String arrayField) {
        String firstArtist = JsonSupport.firstObject(JsonSupport.arrayValue(songJson, arrayField));
        String name = JsonSupport.stringValue(firstArtist, "name");
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
