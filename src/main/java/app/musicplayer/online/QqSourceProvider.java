package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QqSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "QQ音乐";
    private static final String REFERER = "https://y.qq.com/";

    private final CrawlerSession session;

    QqSourceProvider(CrawlerSession session) {
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
                    "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg?format=json&key="
                            + JsonSupport.encode(query)
                            + "&g_tk=5381&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8"
                            + "&notice=0&platform=yqq.json&needNewCode=0",
                    REFERER);
            String songObject = JsonSupport.objectValue(json, "song");
            String items = JsonSupport.arrayValue(songObject, "itemlist");
            Set<String> seen = new HashSet<>();
            for (String item : JsonSupport.splitTopLevelObjects(items)) {
                String id = JsonSupport.stringValue(item, "mid");
                String title = JsonSupport.stringValue(item, "name");
                if (id == null || id.isBlank() || title == null || title.isBlank() || !seen.add(id)) {
                    continue;
                }
                String artist = JsonSupport.stringValue(item, "singer");
                if (artist == null || artist.isBlank()) {
                    artist = "未知歌手";
                }
                results.add(new OnlineTrackInfo(SOURCE, title, artist, "", null, id, ""));
                if (results.size() >= 10) {
                    break;
                }
            }
        } catch (Exception exception) {
            System.out.println("[crawler] qq search err: " + exception.getMessage());
        }
        return results;
    }

    @Override
    public String resolve(OnlineTrackInfo track) {
        try {
            String requestBody = "{\"req_0\":{\"module\":\"vkey.GetVkeyServer\",\"method\":\"CgiGetVkey\","
                    + "\"param\":{\"guid\":\"" + session.randomGuid() + "\",\"songmid\":[\""
                    + track.primaryId() + "\"],\"songtype\":[0],\"uin\":\"0\",\"loginflag\":0,\"platform\":\"20\"}}}";
            String json = session.fetch(
                    "https://u.y.qq.com/cgi-bin/musicu.fcg?format=json&data=" + JsonSupport.encode(requestBody),
                    REFERER);
            Matcher pathMatcher = Pattern.compile("\"purl\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (!pathMatcher.find() || pathMatcher.group(1).isBlank()) {
                return null;
            }
            String path = OnlineTextSupport.unescapeUnicode(pathMatcher.group(1));
            String server = "http://aqqmusic.tc.qq.com/";
            Matcher serverMatcher = Pattern.compile("\"sip\"\\s*:\\s*\\[\"([^\"]+)\"").matcher(json);
            if (serverMatcher.find()) {
                server = serverMatcher.group(1);
            }
            return server + path;
        } catch (Exception exception) {
            System.out.println("[crawler] qq vkey err: " + exception.getMessage());
            return null;
        }
    }

    @Override
    public OnlineTrackInfo annotateAvailability(OnlineTrackInfo track) {
        String url = resolve(track);
        return url == null || url.isBlank()
                ? track.withAvailability(false, "VIP/不可下载")
                : track.withAvailability(true, "可下载");
    }
}
