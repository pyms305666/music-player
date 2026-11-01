package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 酷我音乐：search.kuwo.cn/r.s 搜索（免登录）， mobi.kuwo.cn 车载播放器接口解析直链，VIP 歌曲同样返回完整音频。 */
final class KuwoSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "酷我音乐";
    private static final String REFERER = "https://kuwo.cn/";
    // 酷我支持一次返回 50 条；解析上限也要同步，否则结果仍会被截断。
    private static final int SEARCH_LIMIT = 50;
    private static final String CAR_PLAYER_SOURCE = "kwplayercar_ar_6.0.0.9_B_jiakong_vh.apk";

    /** 依次尝试的码率：320k MP3 为主，其次无损，128k 兜底。 */
    private static final String[] BITRATES = {"320kmp3", "2000kflac", "128kmp3"};

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("'(NAME|SONGNAME|SUBTITLE|ARTIST|ALBUM|ALBUMID|MUSICRID)':'([^']*)'");
    private static final Pattern TEXT_ESCAPE = Pattern.compile("\\\\+u([0-9a-fA-F]{4})");

    private final CrawlerSession session;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    KuwoSourceProvider(CrawlerSession session) {
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
        List<OnlineTrackInfo> results = new ArrayList<>();
        try {
            String body =
                    session.fetch(
                            "http://search.kuwo.cn/r.s?all="
                                    + JsonSupport.encode(query)
                                    + "&ft=music&itemset=web_2013&client=kt&pcmp4=1&geo=c&vipver=1"
                                    + "&pn=0&rn="
                                    + SEARCH_LIMIT
                                    + "&rformat=json&encoding=utf8",
                            REFERER);
            results.addAll(parseSearchResponse(body));
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("来源搜索失败", exception);
        }
        return results;
    }

    /** r.s 返回带嵌套花括号的单引号伪 JSON，需按深度扫描条目；抽离成包内可见便于离线测试。 */
    static List<OnlineTrackInfo> parseSearchResponse(String body) {
        SearchResponse.requireObject(body, '\'');
        List<OnlineTrackInfo> results = new ArrayList<>();
        int arrayStart = body.indexOf("'abslist'");
        if (arrayStart < 0) {
            throw new IllegalStateException("Missing search results: abslist");
        }
        int colon = body.indexOf(':', arrayStart + "'abslist'".length());
        int cursor = colon + 1;
        while (cursor < body.length() && Character.isWhitespace(body.charAt(cursor))) cursor++;
        if (colon < 0 || cursor >= body.length() || body.charAt(cursor) != '[') {
            throw new IllegalStateException("Missing search array: abslist");
        }
        cursor++;
        boolean closed = false;
        Set<String> seen = new HashSet<>();
        while (cursor < body.length() && results.size() < SEARCH_LIMIT) {
            char c = body.charAt(cursor);
            if (c == ']') {
                closed = true;
                break;
            }
            if (c != '{') {
                cursor++;
                continue;
            }
            int objectEnd = pseudoJsonObjectEnd(body, cursor);
            if (objectEnd < 0) {
                throw new IllegalStateException("Incomplete search item");
            }
            OnlineTrackInfo track = parseItem(body.substring(cursor, objectEnd + 1));
            if (track != null && seen.add(track.primaryId())) {
                results.add(track);
            }
            cursor = objectEnd + 1;
        }
        if (!closed && results.size() < SEARCH_LIMIT)
            throw new IllegalStateException("Incomplete search array");
        return results;
    }

    /** 把来源响应中的单首歌曲转为统一在线歌曲模型。 */
    private static OnlineTrackInfo parseItem(String item) {
        String musicRid = field(item, "MUSICRID");
        // NAME can omit Remix / 3D / DJ annotations; SONGNAME preserves the recording's version.
        String title = field(item, "SONGNAME");
        if (title == null || title.isBlank()) {
            title = field(item, "NAME");
        }
        if (musicRid == null || musicRid.isBlank() || title == null || title.isBlank()) {
            return null;
        }
        String rid =
                musicRid.startsWith("MUSIC_") ? musicRid.substring("MUSIC_".length()) : musicRid;
        if (rid.isBlank()) {
            return null;
        }
        String artist = field(item, "ARTIST");
        if (artist == null || artist.isBlank()) {
            artist = "未知歌手";
        }
        String album = field(item, "ALBUM");
        String albumId = field(item, "ALBUMID");
        return new OnlineTrackInfo(
                SOURCE,
                searchTitle(title, field(item, "SUBTITLE")),
                searchText(artist),
                album == null ? "" : searchText(album),
                null,
                rid,
                albumId);
    }

    /** 提取搜索响应中的歌名，保留影响版本匹配的必要信息。 */
    private static String searchTitle(String title, String subtitle) {
        String decoded = searchText(title);
        String credit = subtitle == null ? "" : searchText(subtitle);
        // A separately supplied soundtrack credit is promotional text, rather than a version.
        if (credit.matches("《[^《》]+》(电视剧|电影)(插曲|主题曲|片头曲|片尾曲)") && decoded.endsWith("-" + credit)) {
            return decoded.substring(0, decoded.length() - credit.length() - 1).trim();
        }
        return decoded;
    }

    /**
     * 提取来源的完整歌名，保留版本及独立的影视原声署名。 Search metadata sometimes contains several layers of escaped
     * Unicode.
     */
    private static String searchText(String text) {
        Matcher escapes = TEXT_ESCAPE.matcher(text);
        StringBuffer decoded = new StringBuffer();
        while (escapes.find()) {
            escapes.appendReplacement(
                    decoded,
                    Matcher.quoteReplacement(
                            String.valueOf((char) Integer.parseInt(escapes.group(1), 16))));
        }
        escapes.appendTail(decoded);
        return OnlineTextSupport.stripHtml(OnlineTextSupport.unescape(decoded.toString()));
    }

    /** 在单引号伪 JSON 中寻找深度闭合的 } 的下标。 */
    private static int pseudoJsonObjectEnd(String body, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < body.length(); index++) {
            char c = body.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '\'') {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** 准备或解析可实际使用的地址、路径或运行配置。 */
    @Override
    public String resolve(OnlineTrackInfo track) {
        for (String bitrate : BITRATES) {
            try {
                String json =
                        session.fetch(
                                "https://mobi.kuwo.cn/mobi.s?f=web&source="
                                        + CAR_PLAYER_SOURCE
                                        + "&from=PC&type=convert_url_with_sign&br="
                                        + bitrate
                                        + "&rid="
                                        + JsonSupport.encode(track.primaryId())
                                        + "&user="
                                        + randomUserId(),
                                REFERER);
                String url = parsePlayUrl(json);
                if (url != null && !url.isBlank()) {
                    return url;
                }
            } catch (Exception exception) {
                System.out.println("[crawler] kuwo resolve err: " + exception.getMessage());
            }
        }
        return null;
    }

    /** 车载接口返回标准 JSON，抽离成包内可见便于离线测试。 */
    static String parsePlayUrl(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!matcher.find()) {
            return null;
        }
        String url = OnlineTextSupport.unescape(matcher.group(1));
        return url == null || url.isBlank() ? null : url;
    }

    /** 生成对应来源协议需要的随机标识或随机参数。 */
    private String randomUserId() {
        return "C_APK_guanwang_" + System.currentTimeMillis() + session.randomGuid();
    }

    /** 提取指定字段或当前控件的值，缺失值使用本组件约定的备用结果。 */
    private static String field(String item, String name) {
        Matcher matcher = FIELD_PATTERN.matcher(item);
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return null;
    }
}
