package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KugouSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "酷狗音乐";
    private static final String REFERER = "https://www.kugou.com/";

    private final CrawlerSession session;

    KugouSourceProvider(CrawlerSession session) {
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
                    "https://songsearch.kugou.com/song_search_v2?page=1&pagesize=10&userid=-1"
                            + "&clientver=&platform=WebFilter&tag=em&filter=2&iscorrection=1"
                            + "&privilege_filter=0&keyword=" + JsonSupport.encode(query),
                    REFERER);
            Matcher hashes = Pattern.compile("\"(?:FileHash|Hash)\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            Matcher titles = Pattern.compile("\"(?:SongName|FileName)\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            Matcher artists = Pattern.compile("\"SingerName\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            Matcher images = Pattern.compile("\"Image\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            Matcher albums = Pattern.compile("\"(?:AlbumID|AlbumId)\"\\s*:\\s*(\\d+)").matcher(json);
            for (int index = 0; index < 10 && hashes.find(); index++) {
                String title = titles.find()
                        ? OnlineTextSupport.stripHtml(OnlineTextSupport.unescape(titles.group(1)))
                        : "?";
                String artist = artists.find()
                        ? OnlineTextSupport.stripHtml(OnlineTextSupport.unescape(artists.group(1)))
                        : "未知歌手";
                String cover = images.find() ? images.group(1) : null;
                String albumId = albums.find() ? albums.group(1) : "";
                results.add(new OnlineTrackInfo(
                        SOURCE, title, artist, "", cover, hashes.group(1), albumId));
            }
        } catch (Exception exception) {
            System.out.println("[crawler] kugou search err: " + exception.getMessage());
        }
        return results;
    }

    @Override
    public String resolve(OnlineTrackInfo track) {
        try {
            String url = "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash="
                    + JsonSupport.encode(track.primaryId());
            if (track.secondaryId() != null && !track.secondaryId().isBlank()) {
                url += "&album_id=" + JsonSupport.encode(track.secondaryId());
            }
            url += "&mid=" + session.randomHex(32);
            String json = session.fetch(url, REFERER);
            for (String field : new String[]{"play_url", "url", "play_backup_url"}) {
                Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
                if (matcher.find() && !matcher.group(1).isBlank()) {
                    return OnlineTextSupport.unescape(matcher.group(1));
                }
            }
        } catch (Exception exception) {
            System.out.println("[crawler] kugou resolve err: " + exception.getMessage());
        }
        return null;
    }

    @Override
    public OnlineTrackInfo annotateAvailability(OnlineTrackInfo track) {
        String url = resolve(track);
        return url == null || url.isBlank()
                ? track.withAvailability(false, "VIP/不可下载")
                : track.withAvailability(true, "可下载");
    }
}
