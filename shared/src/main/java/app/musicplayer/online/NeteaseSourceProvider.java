package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 网易云歌曲来源适配器。处理搜索响应、加密请求和音频地址解析，支持部分响应形式的后备解析。 */
final class NeteaseSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "网易云音乐";
    private static final String REFERER = "https://music.163.com/";
    // 与请求数量保持一致，包括结构化解析和旧响应的后备解析。
    private static final int SEARCH_LIMIT = 50;

    private final CrawlerSession session;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    NeteaseSourceProvider(CrawlerSession session) {
        this.session = session;
    }

    /** 返回来源的稳定名称，用于结果标记、去重与渠道选择。 */
    @Override
    public String sourceName() {
        return SOURCE;
    }

    /** 取得来源要求的 Referer 请求头，供 HTTP 请求使用。 */
    @Override
    public String referer() {
        return REFERER;
    }

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    @Override
    public List<OnlineTrackInfo> search(String query) {
        try {
            String csrf = session.cookieValue("music.163.com", "__csrf");
            String json =
                    session.fetch(
                            "https://music.163.com/api/search/get/web?csrf_token="
                                    + (csrf == null ? "" : csrf)
                                    + "&type=1&offset=0&limit="
                                    + SEARCH_LIMIT
                                    + "&s="
                                    + JsonSupport.encode(query),
                            REFERER);
            return parseSearchResponse(json);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("来源搜索失败", exception);
        }
    }

    /** 校验来源响应并逐条转换歌曲，区分合法空结果与解析失败。 */
    static List<OnlineTrackInfo> parseSearchResponse(String json) {
        SearchResponse.requireObject(json);
        SearchResponse.requireSuccess(json, "code", "200");
        List<OnlineTrackInfo> results = new ArrayList<>();
        String result = JsonSupport.objectValue(json, "result");
        String payload = result == null ? json : result;
        String songs = JsonSupport.arrayValue(payload, "songs");
        if (songs == null && "0".equals(JsonSupport.numberValue(payload, "songCount")))
            return List.of();
        if (songs == null) throw new IllegalStateException("Missing search results: songs");
        Set<String> seen = new HashSet<>();
        for (String song : JsonSupport.splitTopLevelObjects(songs)) {
            // 接口可能先返回嵌套的专辑及其歌手，再返回歌曲自身的 id/name。
            // 按对象层级取值，避免误把专辑或歌手的字段当成歌曲字段。
            JsonObject item = JsonParser.parseString(song).getAsJsonObject();
            String id = jsonString(item, "id");
            String title = jsonString(item, "name");
            if (id == null || "0".equals(id) || title == null || title.isBlank() || !seen.add(id)) {
                continue;
            }
            String artist = "未知歌手";
            JsonArray artists = item.get("artists") instanceof JsonArray array ? array : null;
            String parsedArtist =
                    artists != null
                                    && !artists.isEmpty()
                                    && artists.get(0) instanceof JsonObject firstArtist
                            ? jsonString(firstArtist, "name")
                            : null;
            if (parsedArtist != null && !parsedArtist.isBlank()) {
                artist = parsedArtist;
            }

            String album = "";
            String cover = null;
            if (item.get("album") instanceof JsonObject albumObject) {
                String parsedAlbum = jsonString(albumObject, "name");
                album = parsedAlbum == null ? "" : parsedAlbum;
                cover = jsonString(albumObject, "picUrl");
            }
            results.add(new OnlineTrackInfo(SOURCE, title, artist, album, cover, id, null));
            if (results.size() >= SEARCH_LIMIT) {
                break;
            }
        }
        if (results.isEmpty()) {
            addRegexFallback(json, results);
        }
        return results;
    }

    /** 读取当前 JSON 对象自己的简单字段；字段缺失或为 null 时返回空值。 */
    private static String jsonString(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /** 准备或解析可实际使用的地址、路径或运行配置。 */
    @Override
    public String resolve(OnlineTrackInfo track) {
        String csrf = session.cookieValue("music.163.com", "__csrf");
        csrf = csrf == null ? "" : csrf;
        String body =
                "{\"ids\":\"["
                        + track.primaryId()
                        + "]\",\"level\":\"standard\",\"encodeType\":\"mp3\",\"csrf_token\":\""
                        + csrf
                        + "\"}";
        try {
            String endpoint = "/api/song/enhance/player/url/v1";
            String[] eapi =
                    NeteaseCrypto.eapiEncrypt("nobody" + endpoint + "use" + body + "md5forencrypt");
            String url =
                    findUrl(
                            postEncrypted(
                                    "https://music.163.com/eapi/song/enhance/player/url/v1?csrf_token="
                                            + csrf,
                                    eapi));
            if (url != null) {
                return url;
            }

            String[] weapi = NeteaseCrypto.encrypt(body);
            url =
                    findUrl(
                            postEncrypted(
                                    "https://music.163.com/weapi/song/enhance/player/url/v1?csrf_token="
                                            + csrf,
                                    weapi));
            if (url != null) {
                return url;
            }
        } catch (Exception exception) {
            System.out.println("[crawler] netease resolve err: " + exception.getMessage());
        }
        return "https://music.163.com/song/media/outer/url?id=" + track.primaryId() + ".mp3";
    }

    /** 检查地址是否具备可尝试下载的形式，实际可用性仍需传输校验。 */
    @Override
    public boolean isTentativeUrl(String url) {
        return url != null && url.contains("/song/media/outer/url");
    }

    /** 按网易云协议封装加密参数，再通过共享会话发送请求。 */
    private String postEncrypted(String url, String[] encrypted) throws Exception {
        String form =
                "params="
                        + JsonSupport.encode(encrypted[0])
                        + "&encSecKey="
                        + JsonSupport.encode(encrypted[1]);
        String response = session.postForm(url, REFERER, form, Map.of());
        return response == null || response.isBlank() ? null : response;
    }

    /** 从来源响应中提取音频地址，缺失时返回空值。 */
    private String findUrl(String json) {
        if (json == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"url\"\\s*:\\s*\"(https?://[^\"]+)\"").matcher(json);
        return matcher.find() ? OnlineTextSupport.unescape(matcher.group(1)) : null;
    }

    /** 在常规结构未找到地址时，用已有响应格式的后备规则提取数据。 */
    private static void addRegexFallback(String json, List<OnlineTrackInfo> results) {
        if (json == null || json.isBlank()) {
            return;
        }
        Matcher matcher =
                Pattern.compile(
                                "\"id\"\\s*:\\s*(\\d+).*?\"name\"\\s*:\\s*\"([^\"]+)\"",
                                Pattern.DOTALL)
                        .matcher(json);
        Set<String> seen = new HashSet<>();
        while (matcher.find() && results.size() < SEARCH_LIMIT) {
            String id = matcher.group(1);
            String title = OnlineTextSupport.unescape(matcher.group(2));
            if (!"0".equals(id) && title != null && !title.isBlank() && seen.add(id)) {
                results.add(new OnlineTrackInfo(SOURCE, title, "未知歌手", "", null, id, null));
            }
        }
    }
}
