package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 酷我音乐：search.kuwo.cn/r.s 搜索（免登录），
 * mobi.kuwo.cn 车载播放器接口解析直链，VIP 歌曲同样返回完整音频。
 */
final class KuwoSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "酷我音乐";
    private static final String REFERER = "https://kuwo.cn/";
    private static final String CAR_PLAYER_SOURCE = "kwplayercar_ar_6.0.0.9_B_jiakong_vh.apk";
    /** 依次尝试的码率：320k MP3 为主，其次无损，128k 兜底。 */
    private static final String[] BITRATES = {"320kmp3", "2000kflac", "128kmp3"};

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("'(NAME|SONGNAME|ARTIST|ALBUM|ALBUMID|MUSICRID)':'([^']*)'");

    private final CrawlerSession session;

    KuwoSourceProvider(CrawlerSession session) {
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
            String body = session.fetch(
                    "http://search.kuwo.cn/r.s?all=" + JsonSupport.encode(query)
                            + "&ft=music&itemset=web_2013&client=kt&pcmp4=1&geo=c&vipver=1"
                            + "&pn=0&rn=10&rformat=json&encoding=utf8",
                    REFERER);
            results.addAll(parseSearchResponse(body));
        } catch (Exception exception) {
            System.out.println("[crawler] kuwo search err: " + exception.getMessage());
        }
        return results;
    }

    /** r.s 返回带嵌套花括号的单引号伪 JSON，需按深度扫描条目；抽离成包内可见便于离线测试。 */
    static List<OnlineTrackInfo> parseSearchResponse(String body) {
        List<OnlineTrackInfo> results = new ArrayList<>();
        if (body == null || body.isBlank()) {
            return results;
        }
        int arrayStart = body.indexOf("'abslist'");
        if (arrayStart < 0) {
            return results;
        }
        int cursor = body.indexOf('[', arrayStart);
        if (cursor < 0) {
            return results;
        }
        cursor++;
        Set<String> seen = new HashSet<>();
        while (cursor < body.length() && results.size() < 10) {
            char c = body.charAt(cursor);
            if (c == ']') {
                break;
            }
            if (c != '{') {
                cursor++;
                continue;
            }
            int objectEnd = pseudoJsonObjectEnd(body, cursor);
            if (objectEnd < 0) {
                break;
            }
            OnlineTrackInfo track = parseItem(body.substring(cursor, objectEnd + 1));
            if (track != null && seen.add(track.primaryId())) {
                results.add(track);
            }
            cursor = objectEnd + 1;
        }
        return results;
    }

    private static OnlineTrackInfo parseItem(String item) {
        String musicRid = field(item, "MUSICRID");
        String title = field(item, "NAME");
        if (title == null || title.isBlank()) {
            title = field(item, "SONGNAME");
        }
        if (musicRid == null || musicRid.isBlank() || title == null || title.isBlank()) {
            return null;
        }
        String rid = musicRid.startsWith("MUSIC_") ? musicRid.substring("MUSIC_".length()) : musicRid;
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
                OnlineTextSupport.stripHtml(title),
                OnlineTextSupport.stripHtml(artist),
                album == null ? "" : OnlineTextSupport.stripHtml(album),
                null,
                rid,
                albumId);
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

    @Override
    public String resolve(OnlineTrackInfo track) {
        for (String bitrate : BITRATES) {
            try {
                String json = session.fetch(
                        "https://mobi.kuwo.cn/mobi.s?f=web&source=" + CAR_PLAYER_SOURCE
                                + "&from=PC&type=convert_url_with_sign&br=" + bitrate
                                + "&rid=" + JsonSupport.encode(track.primaryId())
                                + "&user=" + randomUserId(),
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

    private String randomUserId() {
        return "C_APK_guanwang_" + System.currentTimeMillis() + session.randomGuid();
    }

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
