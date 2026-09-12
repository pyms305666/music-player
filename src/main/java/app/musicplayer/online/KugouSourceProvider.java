package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
            results.addAll(parseSearchResponse(json));
            if (results.isEmpty()) {
                results.addAll(parseSearchResponseByRegex(json));
            }
        } catch (Exception exception) {
            System.out.println("[crawler] kugou search err: " + exception.getMessage());
        }
        return results;
    }

    /** 结构化解析，抽离成包内可见便于离线测试。 */
    static List<OnlineTrackInfo> parseSearchResponse(String json) {
        List<OnlineTrackInfo> results = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return results;
        }
        String data = JsonSupport.objectValue(json, "data");
        String lists = JsonSupport.arrayValue(data == null ? json : data, "lists");
        Set<String> seen = new HashSet<>();
        for (String item : JsonSupport.splitTopLevelObjects(lists)) {
            String hash = OnlineTextSupport.value(item, "FileHash", "Hash");
            if (hash == null || hash.isBlank() || !seen.add(hash)) {
                continue;
            }
            String title = OnlineTextSupport.stripHtml(OnlineTextSupport.unescape(
                    OnlineTextSupport.value(item, "SongName", "FileName")));
            if (title == null || title.isBlank()) {
                continue;
            }
            String artist = OnlineTextSupport.value(item, "SingerName");
            if (artist == null || artist.isBlank()) {
                artist = "未知歌手";
            }
            String cover = OnlineTextSupport.value(item, "Image");
            String albumId = OnlineTextSupport.value(item, "AlbumID", "AlbumId");
            results.add(new OnlineTrackInfo(SOURCE, title,
                    OnlineTextSupport.stripHtml(OnlineTextSupport.unescape(artist)),
                    "", cover, hash, albumId));
            if (results.size() >= 10) {
                break;
            }
        }
        return results;
    }

    /** 旧的正则解析保留为结构化解析的兜底。 */
    static List<OnlineTrackInfo> parseSearchResponseByRegex(String json) {
        List<OnlineTrackInfo> results = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return results;
        }
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
}
