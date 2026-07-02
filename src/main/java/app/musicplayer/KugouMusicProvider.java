package app.musicplayer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

final class KugouMusicProvider implements OnlineLyricsProvider {
    @Override
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient) {
        try {
            String searchUrl = "https://songsearch.kugou.com/song_search_v2"
                    + "?page=1&pagesize=5&userid=-1&clientver=&platform=WebFilter&tag=em"
                    + "&filter=2&iscorrection=1&privilege_filter=0&keyword="
                    + JsonSupport.encode(JsonSupport.queryText(track));
            String searchJson = get(httpClient, searchUrl, "https://www.kugou.com/");
            String list = JsonSupport.arrayValue(searchJson, "lists");

            for (String song : JsonSupport.splitTopLevelObjects(list)) {
                String hash = value(song, "FileHash", "filehash", "Hash", "hash");
                if (hash == null || hash.isBlank()) {
                    continue;
                }

                String albumId = value(song, "AlbumID", "album_id", "AlbumId");
                String playJson = get(httpClient, playDataUrl(hash, albumId), "https://www.kugou.com/");
                String lyric = JsonSupport.stringValue(playJson, "lyrics");
                if (lyric == null || lyric.isBlank()) {
                    lyric = downloadLrc(httpClient, track, duration, hash);
                }
                if (lyric == null || lyric.isBlank()) {
                    continue;
                }

                String songName = JsonSupport.stripHtml(value(song, "SongName", "songname", "FileName", "filename"));
                String singer = JsonSupport.stripHtml(value(song, "SingerName", "singername"));
                String artworkUrl = value(playJson, "img", "album_img", "Image", "image");
                if ((artworkUrl == null || artworkUrl.isBlank())) {
                    artworkUrl = value(song, "Image", "image");
                }

                return Optional.of(new OnlineLyricsResult(
                        "酷狗音乐：" + readableName(songName, singer), lyric, artworkUrl, 30));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private static String playDataUrl(String hash, String albumId) {
        StringBuilder url = new StringBuilder("https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash=")
                .append(JsonSupport.encode(hash));
        if (albumId != null && !albumId.isBlank()) {
            url.append("&album_id=").append(JsonSupport.encode(albumId));
        }
        return url.toString();
    }

    private static String downloadLrc(HttpClient httpClient, Track track, Duration duration, String hash) throws Exception {
        long millis = duration == null || duration.isNegative() || duration.isZero() ? 0 : duration.toMillis();
        String searchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword="
                + JsonSupport.encode(JsonSupport.queryText(track)) + "&duration=" + millis + "&hash="
                + JsonSupport.encode(hash);
        String searchJson = get(httpClient, searchUrl, "https://www.kugou.com/");
        String first = JsonSupport.firstObject(JsonSupport.arrayValue(searchJson, "candidates"));
        String id = value(first, "id");
        String accessKey = value(first, "accesskey");
        if (id == null || accessKey == null) {
            return null;
        }

        String downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id="
                + JsonSupport.encode(id) + "&accesskey=" + JsonSupport.encode(accessKey);
        String downloadJson = get(httpClient, downloadUrl, "https://www.kugou.com/");
        return JsonSupport.decodeBase64Text(JsonSupport.stringValue(downloadJson, "content"));
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

    private static String value(String json, String... names) {
        if (json == null) {
            return null;
        }
        for (String name : names) {
            String text = JsonSupport.stringValue(json, name);
            if (text != null && !text.isBlank()) {
                return text;
            }
            text = JsonSupport.numberValue(json, name);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
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
