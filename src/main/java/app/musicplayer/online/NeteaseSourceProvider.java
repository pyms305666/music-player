package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class NeteaseSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "网易云音乐";
    private static final String REFERER = "https://music.163.com/";

    private final CrawlerSession session;

    NeteaseSourceProvider(CrawlerSession session) {
        this.session = session;
    }

    @Override
    public String sourceName() {
        return SOURCE;
    }

    @Override
    public String referer() {
        return REFERER;
    }

    @Override
    public List<OnlineTrackInfo> search(String query) {
        List<OnlineTrackInfo> results = new ArrayList<>();
        try {
            String csrf = session.cookieValue("music.163.com", "__csrf");
            String json = session.fetch(
                    "https://music.163.com/api/search/get/web?csrf_token="
                            + (csrf == null ? "" : csrf)
                            + "&type=1&offset=0&limit=8&s=" + JsonSupport.encode(query),
                    REFERER);
            String songs = JsonSupport.arrayValue(json, "songs");
            Set<String> seen = new HashSet<>();
            for (String song : JsonSupport.splitTopLevelObjects(songs)) {
                String id = JsonSupport.numberValue(song, "id");
                String title = JsonSupport.stringValue(song, "name");
                if (id == null || "0".equals(id) || title == null || title.isBlank() || !seen.add(id)) {
                    continue;
                }
                String artist = "未知歌手";
                String firstArtist = JsonSupport.firstObject(JsonSupport.arrayValue(song, "artists"));
                String parsedArtist = JsonSupport.stringValue(firstArtist, "name");
                if (parsedArtist != null && !parsedArtist.isBlank()) {
                    artist = parsedArtist;
                }

                String album = "";
                String cover = null;
                String albumObject = JsonSupport.objectValue(song, "album");
                if (albumObject != null) {
                    String parsedAlbum = JsonSupport.stringValue(albumObject, "name");
                    album = parsedAlbum == null ? "" : parsedAlbum;
                    cover = JsonSupport.stringValue(albumObject, "picUrl");
                }
                results.add(new OnlineTrackInfo(SOURCE, title, artist, album, cover, id, null));
                if (results.size() >= 8) {
                    break;
                }
            }
            if (results.isEmpty()) {
                addRegexFallback(json, results);
            }
        } catch (Exception exception) {
            System.out.println("[crawler] netease search err: " + exception.getMessage());
        }
        return results;
    }

    @Override
    public String resolve(OnlineTrackInfo track) {
        String csrf = session.cookieValue("music.163.com", "__csrf");
        csrf = csrf == null ? "" : csrf;
        String body = "{\"ids\":\"[" + track.primaryId()
                + "]\",\"level\":\"standard\",\"encodeType\":\"mp3\",\"csrf_token\":\""
                + csrf + "\"}";
        try {
            String endpoint = "/api/song/enhance/player/url/v1";
            String[] eapi = NeteaseCrypto.eapiEncrypt(
                    "nobody" + endpoint + "use" + body + "md5forencrypt");
            String url = findUrl(postEncrypted(
                    "https://music.163.com/eapi/song/enhance/player/url/v1?csrf_token=" + csrf,
                    eapi));
            if (url != null) {
                return url;
            }

            String[] weapi = NeteaseCrypto.encrypt(body);
            url = findUrl(postEncrypted(
                    "https://music.163.com/weapi/song/enhance/player/url/v1?csrf_token=" + csrf,
                    weapi));
            if (url != null) {
                return url;
            }
        } catch (Exception exception) {
            System.out.println("[crawler] netease resolve err: " + exception.getMessage());
        }
        return "https://music.163.com/song/media/outer/url?id=" + track.primaryId() + ".mp3";
    }

    @Override
    public OnlineTrackInfo annotateAvailability(OnlineTrackInfo track) {
        String url = resolve(track);
        if (url == null || url.isBlank()) {
            return track.withAvailability(false, "不可下载");
        }
        return url.contains("/song/media/outer/url")
                ? track.withAvailability(false, "可尝试下载")
                : track.withAvailability(true, "可下载");
    }

    private String postEncrypted(String url, String[] encrypted) throws Exception {
        String form = "params=" + JsonSupport.encode(encrypted[0])
                + "&encSecKey=" + JsonSupport.encode(encrypted[1]);
        String response = session.postForm(url, REFERER, form, Map.of());
        return response == null || response.isBlank() ? null : response;
    }

    private String findUrl(String json) {
        if (json == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"").matcher(json);
        return matcher.find() ? OnlineTextSupport.unescape(matcher.group(1)) : null;
    }

    private void addRegexFallback(String json, List<OnlineTrackInfo> results) {
        if (json == null || json.isBlank()) {
            return;
        }
        Matcher matcher = Pattern.compile(
                "\"id\"\\s*:\\s*(\\d+).*?\"name\"\\s*:\\s*\"([^\"]+)\"",
                Pattern.DOTALL).matcher(json);
        Set<String> seen = new HashSet<>();
        while (matcher.find() && results.size() < 8) {
            String id = matcher.group(1);
            String title = OnlineTextSupport.unescape(matcher.group(2));
            if (!"0".equals(id) && title != null && !title.isBlank() && seen.add(id)) {
                results.add(new OnlineTrackInfo(SOURCE, title, "未知歌手", "", null, id, null));
            }
        }
    }
}
