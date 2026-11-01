package app.musicplayer.lyrics;

import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;
import app.musicplayer.util.JsonSupport;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;

/** LRCLIB 歌词渠道。按名称、歌手及可选时长匹配同步或纯文本歌词，是在线歌词查询的后续来源。 */
public final class LrclibLyricsProvider implements OnlineLyricsProvider {
    private static final String SEARCH_URL = "https://lrclib.net/api/search";

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    @Override
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, LyricsHttp http) {
        try {
            StringBuilder query = new StringBuilder();
            appendQuery(query, "track_name", track.title());
            if (track.artist() != null
                    && !track.artist().isBlank()
                    && !"未知歌手".equals(track.artist())) {
                appendQuery(query, "artist_name", track.artist());
            }
            if (duration != null && !duration.isNegative() && !duration.isZero()) {
                appendQuery(query, "duration", String.valueOf(duration.toSeconds()));
            }

            String body = http.fetch(SEARCH_URL + "?" + query, SEARCH_URL);
            return JsonSupport.splitTopLevelObjects(body).stream()
                    .map(this::toCandidate)
                    .flatMap(Optional::stream)
                    .max(Comparator.comparing(OnlineLyricsResult::score));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** 向请求地址追加正确编码的查询参数。 */
    private static void appendQuery(StringBuilder query, String key, String value) {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(JsonSupport.encode(key));
        query.append('=');
        query.append(JsonSupport.encode(value));
    }

    /** 将歌词接口的单条数据转为可比较的歌词候选。 */
    private Optional<OnlineLyricsResult> toCandidate(String objectJson) {
        String syncedLyrics = JsonSupport.stringValue(objectJson, "syncedLyrics");
        String plainLyrics = JsonSupport.stringValue(objectJson, "plainLyrics");
        String trackName = JsonSupport.stringValue(objectJson, "trackName");
        String artistName = JsonSupport.stringValue(objectJson, "artistName");
        String source = "LRCLIB：" + readableName(trackName, artistName);

        if (syncedLyrics != null && !syncedLyrics.isBlank()) {
            return Optional.of(new OnlineLyricsResult(source, syncedLyrics, null, 20));
        }
        if (plainLyrics != null && !plainLyrics.isBlank()) {
            return Optional.of(new OnlineLyricsResult(source, plainLyrics, null, 10));
        }

        return Optional.empty();
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
