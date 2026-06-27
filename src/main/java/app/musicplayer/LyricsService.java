package app.musicplayer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LyricsService {
    private static final String LRCLIB_SEARCH = "https://lrclib.net/api/search";
    private final MusicDatabase database;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(8))
            .build();

    public LyricsService(MusicDatabase database) {
        this.database = database;
    }

    public CompletableFuture<Lyrics> findLyrics(Track track, java.time.Duration duration) {
        // 歌词加载优先级：
        // 1. 数据库缓存，最快，也能离线使用；
        // 2. 歌曲同目录同名 .lrc；
        // 3. LRCLIB 联网搜索。
        Optional<Lyrics> cachedLyrics = database.loadLyrics(track);
        if (cachedLyrics.isPresent()) {
            return CompletableFuture.completedFuture(cachedLyrics.get());
        }

        Optional<Lyrics> localLyrics = readLocalLyrics(track);
        if (localLyrics.isPresent()) {
            database.saveLyrics(track, localLyrics.get());
            return CompletableFuture.completedFuture(localLyrics.get());
        }

        return searchOnlineAsync(track, duration);
    }

    public CompletableFuture<Lyrics> searchOnlineAsync(Track track, java.time.Duration duration) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Lyrics> lyrics = searchOnline(track, duration);
            // 联网搜到后立即写入缓存，后续播放同一首歌不再依赖网络。
            lyrics.ifPresent(found -> database.saveLyrics(track, found));
            return lyrics.orElseGet(() -> Lyrics.empty("没有找到歌词，正在后台继续搜索"));
        });
    }

    private Optional<Lyrics> readLocalLyrics(Track track) {
        Path audioPath = track.path();
        String fileName = audioPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }

        Path lrcPath = audioPath.resolveSibling(fileName.substring(0, dot) + ".lrc");
        if (!Files.isRegularFile(lrcPath)) {
            return Optional.empty();
        }

        try {
            // 本地 .lrc 默认按 UTF-8 读取；如果用户的歌词文件是其他编码，可能需要另行转换。
            String content = Files.readString(lrcPath, StandardCharsets.UTF_8);
            return Optional.of(LrcParser.parse("本地歌词：" + lrcPath.getFileName(), content));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Lyrics> searchOnline(Track track, java.time.Duration duration) {
        try {
            // LRCLIB 支持通过歌曲名、歌手、时长搜索。时长可以提高匹配准确度，
            // 但部分文件拿不到时长，所以这里允许为空。
            StringBuilder query = new StringBuilder();
            appendQuery(query, "track_name", track.title());
            if (track.artist() != null && !track.artist().isBlank() && !"未知歌手".equals(track.artist())) {
                appendQuery(query, "artist_name", track.artist());
            }
            if (duration != null && !duration.isNegative() && !duration.isZero()) {
                appendQuery(query, "duration", String.valueOf(duration.toSeconds()));
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(LRCLIB_SEARCH + "?" + query))
                    .timeout(java.time.Duration.ofSeconds(12))
                    .header("User-Agent", "SimpleMusicPlayer/1.0 (JavaFX)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            return parseSearchResponse(response.body());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static void appendQuery(StringBuilder query, String key, String value) {
        if (query.length() > 0) {
            query.append('&');
        }
        query.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        query.append('=');
        query.append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
    }

    private Optional<Lyrics> parseSearchResponse(String json) {
        List<String> objects = splitJsonObjects(json);
        return objects.stream()
                .map(this::toLyricsCandidate)
                .flatMap(Optional::stream)
                // 优先选择带时间轴的 syncedLyrics，其次才是 plainLyrics。
                .max(Comparator.comparing(candidate -> candidate.score))
                .map(candidate -> LrcParser.parse(candidate.source, candidate.lyrics));
    }

    private Optional<Candidate> toLyricsCandidate(String objectJson) {
        String syncedLyrics = jsonStringValue(objectJson, "syncedLyrics");
        String plainLyrics = jsonStringValue(objectJson, "plainLyrics");
        String trackName = jsonStringValue(objectJson, "trackName");
        String artistName = jsonStringValue(objectJson, "artistName");

        if (syncedLyrics != null && !syncedLyrics.isBlank()) {
            return Optional.of(new Candidate("联网歌词：" + readableName(trackName, artistName), syncedLyrics, 2));
        }
        if (plainLyrics != null && !plainLyrics.isBlank()) {
            return Optional.of(new Candidate("联网歌词：" + readableName(trackName, artistName), plainLyrics, 1));
        }

        return Optional.empty();
    }

    private static String readableName(String trackName, String artistName) {
        if (trackName == null || trackName.isBlank()) {
            return "LRCLIB";
        }
        if (artistName == null || artistName.isBlank()) {
            return trackName;
        }
        return artistName + " - " + trackName;
    }

    private static List<String> splitJsonObjects(String json) {
        List<String> objects = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;

        // 项目不额外引入 JSON 库，避免为了一个接口响应增加较重依赖；
        // 这里按 JSON 字符串/转义规则切出数组里的对象，再读取需要的字段。
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    objectStart = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(json.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }

        return objects;
    }

    private static String jsonStringValue(String objectJson, String fieldName) {
        String needle = "\"" + fieldName + "\"";
        int fieldIndex = objectJson.indexOf(needle);
        if (fieldIndex < 0) {
            return null;
        }

        int colon = objectJson.indexOf(':', fieldIndex + needle.length());
        if (colon < 0) {
            return null;
        }

        int cursor = colon + 1;
        while (cursor < objectJson.length() && Character.isWhitespace(objectJson.charAt(cursor))) {
            cursor++;
        }
        if (objectJson.startsWith("null", cursor) || cursor >= objectJson.length() || objectJson.charAt(cursor) != '"') {
            return null;
        }

        StringBuilder value = new StringBuilder();
        boolean escaped = false;

        for (int i = cursor + 1; i < objectJson.length(); i++) {
            char c = objectJson.charAt(i);
            if (escaped) {
                value.append(unescape(c, objectJson, i));
                escaped = false;
                if (c == 'u') {
                    i += 4;
                }
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                return value.toString();
            }
            value.append(c);
        }

        return null;
    }

    private static String unescape(char c, String json, int index) {
        return switch (c) {
            case '"' -> "\"";
            case '\\' -> "\\";
            case '/' -> "/";
            case 'b' -> "\b";
            case 'f' -> "\f";
            case 'n' -> "\n";
            case 'r' -> "\r";
            case 't' -> "\t";
            case 'u' -> {
                if (index + 4 < json.length()) {
                    String hex = json.substring(index + 1, index + 5);
                    try {
                        yield String.valueOf((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException ignored) {
                        yield "";
                    }
                }
                yield "";
            }
            default -> String.valueOf(c);
        };
    }

    private record Candidate(String source, String lyrics, int score) {
    }
}
