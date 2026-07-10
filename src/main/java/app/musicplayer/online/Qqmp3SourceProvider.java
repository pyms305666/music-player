package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Qqmp3SourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "QQMP3";
    private static final String REFERER = "https://www.qqmp3.vip/";

    private final CrawlerSession session;

    Qqmp3SourceProvider(CrawlerSession session) {
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
                    "https://www.qqmp3.vip/api/songs.php?type=search&keyword=" + JsonSupport.encode(query),
                    REFERER,
                    Map.of("Accept", "application/json, text/plain, */*", "X-Requested-With", "XMLHttpRequest"));
            String data = JsonSupport.arrayValue(json, "data");
            Set<String> seen = new HashSet<>();
            for (String item : JsonSupport.splitTopLevelObjects(data)) {
                String id = OnlineTextSupport.value(item, "rid");
                String title = JsonSupport.stringValue(item, "name");
                if (id == null || id.isBlank() || title == null || title.isBlank() || !seen.add(id)) {
                    continue;
                }
                String artist = JsonSupport.stringValue(item, "artist");
                if (artist == null || artist.isBlank()) {
                    artist = "未知歌手";
                }
                String cover = JsonSupport.stringValue(item, "pic");
                results.add(new OnlineTrackInfo(SOURCE, title, artist, "", cover, id, ""));
                if (results.size() >= 12) {
                    break;
                }
            }
        } catch (Exception exception) {
            System.out.println("[crawler] qqmp3 search err: " + exception.getMessage());
        }
        return results;
    }

    @Override
    public String resolve(OnlineTrackInfo track) {
        try {
            String json = fetchSongData(track.primaryId());
            String data = JsonSupport.objectValue(json, "data");
            String url = OnlineTextSupport.value(data, "url", "play_url", "playUrl");
            if (url == null || url.isBlank()) {
                url = OnlineTextSupport.value(json, "url", "play_url", "playUrl");
            }
            return url;
        } catch (Exception exception) {
            System.out.println("[crawler] qqmp3 resolve err: " + exception.getMessage());
            return null;
        }
    }

    String fetchSongData(String id) throws Exception {
        return session.fetch(
                "https://www.qqmp3.vip/api/kw.php?rid=" + JsonSupport.encode(id)
                        + "&type=json&level=exhigh&lrc=true",
                REFERER,
                Map.of("Accept", "application/json, text/plain, */*", "X-Requested-With", "XMLHttpRequest"));
    }
}
