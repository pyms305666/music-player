package app.musicplayer.lyrics;

import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;
import app.musicplayer.util.JsonSupport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;

final class LrclibLyricsProvider implements OnlineLyricsProvider {
    private static final String SEARCH_URL = "https://lrclib.net/api/search";

    @Override
    public Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient) {
        try {
            StringBuilder query = new StringBuilder();
            appendQuery(query, "track_name", track.title());
            if (track.artist() != null && !track.artist().isBlank() && !"未知歌手".equals(track.artist())) {
                appendQuery(query, "artist_name", track.artist());
            }
            if (duration != null && !duration.isNegative() && !duration.isZero()) {
                appendQuery(query, "duration", String.valueOf(duration.toSeconds()));
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH_URL + "?" + query))
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "SimpleMusicPlayer/1.0 (JavaFX)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            return JsonSupport.splitTopLevelObjects(response.body()).stream()
                    .map(this::toCandidate)
                    .flatMap(Optional::stream)
                    .max(Comparator.comparing(OnlineLyricsResult::score));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static void appendQuery(StringBuilder query, String key, String value) {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(JsonSupport.encode(key));
        query.append('=');
        query.append(JsonSupport.encode(value));
    }

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
