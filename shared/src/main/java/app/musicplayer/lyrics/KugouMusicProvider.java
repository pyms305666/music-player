package app.musicplayer.lyrics;

import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;
import app.musicplayer.util.JsonSupport;

import java.time.Duration;
import java.util.Optional;

/** 酷狗歌词渠道。搜索匹配歌曲，读取播放信息或 LRC 数据，提供原始歌词及封面地址。 */
public final class KugouMusicProvider implements OnlineLyricsProvider {
    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    @Override
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, LyricsHttp http) {
        try {
            String searchUrl =
                    "https://songsearch.kugou.com/song_search_v2"
                            + "?page=1&pagesize=5&userid=-1&clientver=&platform=WebFilter&tag=em"
                            + "&filter=2&iscorrection=1&privilege_filter=0&keyword="
                            + JsonSupport.encode(JsonSupport.queryText(track));
            String searchJson = http.fetch(searchUrl, "https://www.kugou.com/");
            String list = JsonSupport.arrayValue(searchJson, "lists");

            for (String song : JsonSupport.splitTopLevelObjects(list)) {
                String hash = value(song, "FileHash", "filehash", "Hash", "hash");
                if (hash == null || hash.isBlank()) {
                    continue;
                }

                String albumId = value(song, "AlbumID", "album_id", "AlbumId");
                String playJson = http.fetch(playDataUrl(hash, albumId), "https://www.kugou.com/");
                String lyric = JsonSupport.stringValue(playJson, "lyrics");
                if (lyric == null || lyric.isBlank()) {
                    lyric = downloadLrc(http, track, duration, hash);
                }
                if (lyric == null || lyric.isBlank()) {
                    continue;
                }

                String songName =
                        JsonSupport.stripHtml(
                                value(song, "SongName", "songname", "FileName", "filename"));
                String singer = JsonSupport.stripHtml(value(song, "SingerName", "singername"));
                String artworkUrl = value(playJson, "img", "album_img", "Image", "image");
                if ((artworkUrl == null || artworkUrl.isBlank())) {
                    artworkUrl = value(song, "Image", "image");
                }

                return Optional.of(
                        new OnlineLyricsResult(
                                "酷狗音乐：" + readableName(songName, singer), lyric, artworkUrl, 30));
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    /** 构造酷狗播放信息请求地址，供歌词和封面预览使用。 */
    private static String playDataUrl(String hash, String albumId) {
        StringBuilder url =
                new StringBuilder(
                                "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash=")
                        .append(JsonSupport.encode(hash));
        if (albumId != null && !albumId.isBlank()) {
            url.append("&album_id=").append(JsonSupport.encode(albumId));
        }
        return url.toString();
    }

    /** 读取酷狗的 LRC 歌词文本并处理响应编码。 */
    private static String downloadLrc(LyricsHttp http, Track track, Duration duration, String hash)
            throws Exception {
        long millis =
                duration == null || duration.isNegative() || duration.isZero()
                        ? 0
                        : duration.toMillis();
        String searchUrl =
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&keyword="
                        + JsonSupport.encode(JsonSupport.queryText(track))
                        + "&duration="
                        + millis
                        + "&hash="
                        + JsonSupport.encode(hash);
        String searchJson = http.fetch(searchUrl, "https://www.kugou.com/");
        String first = JsonSupport.firstObject(JsonSupport.arrayValue(searchJson, "candidates"));
        String id = value(first, "id");
        String accessKey = value(first, "accesskey");
        if (id == null || accessKey == null) {
            return null;
        }

        String downloadUrl =
                "https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8&id="
                        + JsonSupport.encode(id)
                        + "&accesskey="
                        + JsonSupport.encode(accessKey);
        String downloadJson = http.fetch(downloadUrl, "https://www.kugou.com/");
        return JsonSupport.decodeBase64Text(JsonSupport.stringValue(downloadJson, "content"));
    }

    /** 提取指定字段或当前控件的值，缺失值使用本组件约定的备用结果。 */
    private static String value(String json, String... names) {
        if (json == null) {
            return null;
        }
        for (String name : names) {
            String text = JsonSupport.stringValue(json, name);
            if (text != null && !text.isBlank()) {
                return text;
            }
            text = JsonSupport.numberValue(json, name);
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    /** 返回该歌词渠道用于界面显示的名称。 */
    private static String readableName(String trackName, String artistName) {
        if (trackName == null || trackName.isBlank()) {
            return "搜索结果";
        }
        if (artistName == null || artistName.isBlank()) {
            return trackName;
        }
        return artistName + " - " + trackName;
    }
}
