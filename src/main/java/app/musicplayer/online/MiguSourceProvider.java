package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 咪咕音乐：pd.musicapp.migu.cn 搜索（免登录），
 * app.c.nf.migu.cn listen-url 解析直链，免费曲库大、音质好。
 */
final class MiguSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "咪咕音乐";
    private static final String REFERER = "http://music.migu.cn/";
    /** 咪咕接口按 UA 区分客户端：移动端 UA 返回普通 MP3，桌面 UA 可能返回加密流变体。 */
    private static final String MOBILE_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 9_1 like Mac OS X) AppleWebKit/601.1.46"
                    + " (KHTML, like Gecko) Version/9.0 Mobile/13B143 Safari/601.1";
    private static final Map<String, String> SEARCH_HEADERS = Map.of("User-Agent", MOBILE_UA);
    private static final Map<String, String> LISTEN_HEADERS =
            Map.of("User-Agent", MOBILE_UA, "channel", "0146832", "version", "7.41.13");
    private static final String LISTEN_URL = "https://app.c.nf.migu.cn/MIGUM2.0/v2.1/content/listen-url";
    private static final String SEARCH_SWITCH =
            "{\"song\":1,\"album\":0,\"singer\":0,\"tagSong\":0,\"mvSong\":0,\"songlist\":0,\"bestShow\":1}";
    /** 咪咕 128k CDN 路径改写为 320k 的标记段。 */
    private static final String MP3_128_SEGMENT = "MP3_128_16_Stero";
    private static final String MP3_320_SEGMENT = "MP3_320_16_Stero";
    /** 接口偶发返回的 FTP 形式地址统一改写到 HTTPS CDN。 */
    private static final String FTP_PREFIX = "ftp://218.200.160.122:21/";
    private static final String HTTPS_PREFIX = "https://freetyst.nf.migu.cn/";
    private static final int PROBE_BYTES = 64;

    private final CrawlerSession session;

    MiguSourceProvider(CrawlerSession session) {
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
            String json = session.fetch(
                    "http://pd.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do?ua=Android_migu"
                            + "&version=5.0.1&text=" + JsonSupport.encode(query)
                            + "&pageNo=1&pageSize=10&searchSwitch=" + JsonSupport.encode(SEARCH_SWITCH),
                    REFERER,
                    SEARCH_HEADERS);
            results.addAll(parseSearchResponse(json));
        } catch (Exception exception) {
            System.out.println("[crawler] migu search err: " + exception.getMessage());
        }
        return results;
    }

    /** 抽离成包内可见便于离线测试。 */
    static List<OnlineTrackInfo> parseSearchResponse(String json) {
        List<OnlineTrackInfo> results = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return results;
        }
        String payload = JsonSupport.objectValue(json, "songResultData");
        String list = JsonSupport.arrayValue(payload == null ? json : payload, "result");
        Set<String> seen = new HashSet<>();
        for (String item : JsonSupport.splitTopLevelObjects(list)) {
            String contentId = JsonSupport.stringValue(item, "contentId");
            String title = JsonSupport.stringValue(item, "name");
            if (contentId == null || contentId.isBlank() || title == null || title.isBlank()
                    || !seen.add(contentId)) {
                continue;
            }
            String artist = JsonSupport.stringValue(
                    JsonSupport.firstObject(JsonSupport.arrayValue(item, "singers")), "name");
            if (artist == null || artist.isBlank()) {
                artist = "未知歌手";
            }
            String album = JsonSupport.stringValue(
                    JsonSupport.firstObject(JsonSupport.arrayValue(item, "albums")), "name");
            String copyrightId = JsonSupport.stringValue(item, "copyrightId");
            results.add(new OnlineTrackInfo(
                    SOURCE,
                    title,
                    artist,
                    album == null ? "" : album,
                    coverOf(item),
                    contentId,
                    copyrightId));
        }
        return results;
    }

    private static String coverOf(String item) {
        String images = JsonSupport.arrayValue(item, "imgItems");
        String image = JsonSupport.stringValue(
                JsonSupport.firstObject(images), "img");
        return image == null || image.isBlank() ? null : image;
    }

    @Override
    public String resolve(OnlineTrackInfo track) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String json = session.fetch(
                        LISTEN_URL
                                + "?netType=01&resourceType=2&toneFlag=PQ"
                                + "&contentId=" + JsonSupport.encode(track.primaryId())
                                + (track.secondaryId() == null || track.secondaryId().isBlank()
                                ? "" : "&copyrightId=" + JsonSupport.encode(track.secondaryId())),
                        REFERER,
                        LISTEN_HEADERS);
                String url = parsePlayUrl(json);
                if (url == null || url.isBlank()) {
                    return null;
                }
                url = normalizeCdnUrl(url);
                if (isEncryptedStream(url)) {
                    // 接口偶发返回加密流变体，重试一次；仍失败则交由换源回退处理。
                    continue;
                }
                return upgradeToHighQuality(url);
            } catch (Exception exception) {
                System.out.println("[crawler] migu resolve err: " + exception.getMessage());
                return null;
            }
        }
        return null;
    }

    /** listen-url 返回标准 JSON，抽离成包内可见便于离线测试。 */
    static String parsePlayUrl(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String data = JsonSupport.objectValue(json, "data");
        String url = JsonSupport.stringValue(data == null ? json : data, "url");
        return url == null || url.isBlank() ? null : OnlineTextSupport.unescape(url);
    }

    /** FTP 形式地址统一改写到 HTTPS CDN，并把路径里的中文百分号编码。 */
    static String normalizeCdnUrl(String url) {
        if (url == null) {
            return null;
        }
        if (url.startsWith(FTP_PREFIX)) {
            url = HTTPS_PREFIX + url.substring(FTP_PREFIX.length());
        }
        return encodeNonAscii(url);
    }

    private static boolean isEncryptedStream(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("_3d") || lower.contains("z3d") || lower.contains("wav_32bit");
    }

    static String encodeNonAscii(String url) {
        boolean needsEncoding = false;
        for (int index = 0; index < url.length(); index++) {
            if (url.charAt(index) > 127) {
                needsEncoding = true;
                break;
            }
        }
        if (!needsEncoding) {
            return url;
        }
        StringBuilder result = new StringBuilder(url.length() + 32);
        for (int index = 0; index < url.length(); index++) {
            char c = url.charAt(index);
            if (c <= 127) {
                result.append(c);
            } else {
                for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
                    result.append('%').append(String.format("%02X", b & 0xff));
                }
            }
        }
        return result.toString();
    }

    /** 默认返回 128k，尝试把 CDN 路径改写成 320k，探活失败则保持原地址。 */
    private String upgradeToHighQuality(String url) {
        String candidate = highQualityCandidate(url);
        if (candidate == null || !isPlayableAudio(candidate)) {
            return url;
        }
        return candidate;
    }

    /** 纯改写逻辑，抽离成包内可见便于离线测试。 */
    static String highQualityCandidate(String url) {
        if (url == null || !url.contains(MP3_128_SEGMENT)) {
            return null;
        }
        return url.replace(MP3_128_SEGMENT, MP3_320_SEGMENT);
    }

    private boolean isPlayableAudio(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(8_000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", session.userAgent());
            connection.setRequestProperty("Range", "bytes=0-" + (PROBE_BYTES - 1));
            int statusCode = connection.getResponseCode();
            InputStream input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) {
                connection.disconnect();
                return false;
            }
            byte[] header = new byte[PROBE_BYTES];
            int length = 0;
            try {
                while (length < header.length) {
                    int read = input.read(header, length, header.length - length);
                    if (read < 0) {
                        break;
                    }
                    length += read;
                }
            } finally {
                input.close();
                connection.disconnect();
            }
            return statusCode >= 200 && statusCode < 400 && MusicCrawler.isAudioContent(header, length);
        } catch (Exception exception) {
            System.out.println("[crawler] migu probe err: " + exception.getMessage());
            return false;
        }
    }
}
