package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 酷狗歌曲来源适配器。搜索并解析歌曲信息，将酷狗身份转换为可尝试下载的歌曲，再解析实际音频地址。 */
final class KugouSourceProvider implements OnlineSourceProvider {
    static final String SOURCE = "酷狗音乐";
    private static final String REFERER = "https://www.kugou.com/";
    // 一次请求取更多歌曲，搜索歌手时不再只显示首页前十条。
    private static final int SEARCH_LIMIT = 50;

    private final CrawlerSession session;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    KugouSourceProvider(CrawlerSession session) {
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
            String json =
                    session.fetch(
                            "https://songsearch.kugou.com/song_search_v2?page=1&pagesize="
                                    + SEARCH_LIMIT
                                    + "&userid=-1"
                                    + "&clientver=&platform=WebFilter&tag=em&filter=2&iscorrection=1"
                                    + "&privilege_filter=0&keyword="
                                    + JsonSupport.encode(query),
                            REFERER);
            results.addAll(parseSearchResponse(json));
            if (results.isEmpty()) {
                results.addAll(parseSearchResponseByRegex(json));
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("来源搜索失败", exception);
        }
        return results;
    }

    /** 结构化解析，抽离成包内可见便于离线测试。 */
    static List<OnlineTrackInfo> parseSearchResponse(String json) {
        SearchResponse.requireObject(json);
        List<OnlineTrackInfo> results = new ArrayList<>();
        SearchResponse.requireSuccess(json, "status", "1");
        SearchResponse.requireSuccess(json, "error_code", "0");
        String data = JsonSupport.objectValue(json, "data");
        String lists = SearchResponse.array(data == null ? json : data, "lists");
        Set<String> seen = new HashSet<>();
        for (String item : JsonSupport.splitTopLevelObjects(lists)) {
            String hash = OnlineTextSupport.value(item, "FileHash", "Hash");
            if (hash == null || hash.isBlank() || !seen.add(hash)) {
                continue;
            }
            String title =
                    OnlineTextSupport.stripHtml(
                            OnlineTextSupport.unescape(
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
            results.add(
                    new OnlineTrackInfo(
                            SOURCE,
                            title,
                            OnlineTextSupport.stripHtml(OnlineTextSupport.unescape(artist)),
                            "",
                            cover,
                            hash,
                            albumId));
            if (results.size() >= SEARCH_LIMIT) {
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
        Matcher hashes =
                Pattern.compile("\"(?:FileHash|Hash)\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        Matcher titles =
                Pattern.compile("\"(?:SongName|FileName)\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        Matcher artists = Pattern.compile("\"SingerName\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        Matcher images = Pattern.compile("\"Image\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        Matcher albums = Pattern.compile("\"(?:AlbumID|AlbumId)\"\\s*:\\s*(\\d+)").matcher(json);
        for (int index = 0; index < SEARCH_LIMIT && hashes.find(); index++) {
            String title =
                    titles.find()
                            ? OnlineTextSupport.stripHtml(
                                    OnlineTextSupport.unescape(titles.group(1)))
                            : "?";
            String artist =
                    artists.find()
                            ? OnlineTextSupport.stripHtml(
                                    OnlineTextSupport.unescape(artists.group(1)))
                            : "未知歌手";
            String cover = images.find() ? images.group(1) : null;
            String albumId = albums.find() ? albums.group(1) : "";
            results.add(
                    new OnlineTrackInfo(
                            SOURCE, title, artist, "", cover, hashes.group(1), albumId));
        }
        return results;
    }

    /** 准备或解析可实际使用的地址、路径或运行配置。 */
    @Override
    public String resolve(OnlineTrackInfo track) {
        try {
            String url =
                    "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash="
                            + JsonSupport.encode(track.primaryId());
            if (track.secondaryId() != null && !track.secondaryId().isBlank()) {
                url += "&album_id=" + JsonSupport.encode(track.secondaryId());
            }
            url += "&mid=" + session.randomHex(32);
            String json = session.fetch(url, REFERER);
            for (String field : new String[] {"play_url", "url", "play_backup_url"}) {
                Matcher matcher =
                        Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
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
