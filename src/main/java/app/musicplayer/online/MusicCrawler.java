package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 协调多个在线来源，并负责统一的下载、校验和跨来源回退。
 * 各网站的搜索与地址解析位于独立的 OnlineSourceProvider 实现中。
 */
public final class MusicCrawler {
    private final CrawlerSession session = new CrawlerSession();
    private final Qqmp3SourceProvider qqmp3Provider = new Qqmp3SourceProvider(session);
    private final List<OnlineSourceProvider> providers = List.of(
            qqmp3Provider,
            new NeteaseSourceProvider(session),
            new QqSourceProvider(session),
            new KugouSourceProvider(session)
    );
    private final Map<String, OnlineSourceProvider> providersByName = indexProviders(providers);
    private static Path curlPath;
    private static boolean curlChecked;

    public List<OnlineTrackInfo> search(String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        session.ensurePrimed();
        List<OnlineTrackInfo> allResults = new ArrayList<>();
        for (int index = 0; index < providers.size(); index++) {
            allResults.addAll(providers.get(index).search(normalizedQuery));
            if (index < providers.size() - 1) {
                session.snooze(index == 0 ? 300 : 500);
            }
        }

        List<OnlineTrackInfo> uniqueResults = deduplicate(allResults);
        List<OnlineTrackInfo> annotatedResults = uniqueResults.stream()
                .map(this::annotateAvailability)
                .sorted(Comparator
                        .comparingInt(MusicCrawler::availabilityPriority)
                        .thenComparingInt(this::sourcePriority))
                .toList();
        System.out.println("[crawler] total: " + annotatedResults.size());
        return annotatedResults;
    }

    public String resolveDownloadUrl(OnlineTrackInfo track) {
        OnlineSourceProvider provider = providerFor(track);
        return provider == null ? null : provider.resolve(track);
    }

    public Path download(OnlineTrackInfo track, Path targetDir)
            throws IOException, InterruptedException {
        if (track == null || track.source() == null) {
            throw new IOException("invalid track info");
        }
        if (!track.canAttemptDownload()) {
            throw new IOException(track.source() + ": " + track.availabilityText());
        }

        session.ensurePrimed();
        Files.createDirectories(targetDir);
        IOException lastError = null;
        List<OnlineTrackInfo> candidates = new ArrayList<>(List.of(track));
        Set<String> failedDownloadKeys = new HashSet<>();

        for (int round = 0; round < 2; round++) {
            for (OnlineTrackInfo candidate : candidates) {
                String key = downloadKey(candidate);
                if (failedDownloadKeys.contains(key)) {
                    continue;
                }
                try {
                    return tryDownloadCandidate(candidate, targetDir);
                } catch (IOException exception) {
                    failedDownloadKeys.add(key);
                    lastError = exception;
                    System.out.println("[crawler] candidate failed: " + candidate.source()
                            + " - " + candidate.title() + " : " + exception.getMessage());
                }
            }
            if (round == 0) {
                candidates = fallbackCandidates(track, failedDownloadKeys);
                if (candidates.isEmpty()) {
                    break;
                }
            }
        }
        throw lastError != null ? lastError : new IOException(track.source() + ": cannot download track");
    }

    String fetch(String url, String referer) throws Exception {
        session.ensurePrimed();
        return session.fetch(url, referer);
    }

    String fetchQqmp3SongData(String id) throws Exception {
        session.ensurePrimed();
        return qqmp3Provider.fetchSongData(id);
    }

    private Path tryDownloadCandidate(OnlineTrackInfo track, Path targetDir)
            throws IOException, InterruptedException {
        String url = resolveDownloadUrl(track);
        if (url == null || url.isBlank()) {
            throw new IOException(track.source() + ": cannot resolve URL");
        }

        String extension = guessExtension(track, url);
        String artist = track.artist() == null ? "Unknown" : track.artist();
        Path target = uniqueTarget(targetDir, sanitize(artist + " - " + track.title()), extension);
        System.out.println("[crawler] download: " + OnlineTextSupport.truncate(url, 120));

        if (curlAvailable()) {
            boolean downloaded = downloadViaCurl(url, target, track.source());
            if (downloaded && validateFile(target)) {
                return target;
            }
            safeDelete(target);
        }

        downloadViaJava(url, target, track.source());
        if (!validateFile(target)) {
            safeDelete(target);
            throw new IOException(track.source() + ": unusable file");
        }
        return target;
    }

    private List<OnlineTrackInfo> fallbackCandidates(
            OnlineTrackInfo original,
            Set<String> failedDownloadKeys
    ) {
        String query = ((original.artist() == null ? "" : original.artist() + " ")
                + (original.title() == null ? "" : original.title())).trim();
        if (query.isBlank()) {
            return List.of();
        }
        try {
            List<OnlineTrackInfo> results = search(query);
            List<OnlineTrackInfo> filtered = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (OnlineTrackInfo candidate : results) {
                if (sameOnlineTrack(original, candidate)
                        || failedDownloadKeys.contains(downloadKey(candidate))
                        || !seen.add(downloadKey(candidate))
                        || !candidate.canAttemptDownload()
                        || !strongFallbackMatch(original, candidate)) {
                    continue;
                }
                filtered.add(candidate);
                if (filtered.size() >= 6) {
                    break;
                }
            }
            filtered.sort(Comparator.comparingInt(this::sourcePriority));
            return filtered;
        } catch (Exception exception) {
            System.out.println("[crawler] fallback search failed: " + exception.getMessage());
            return List.of();
        }
    }

    static List<OnlineTrackInfo> deduplicate(List<OnlineTrackInfo> results) {
        List<OnlineTrackInfo> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (OnlineTrackInfo result : results) {
            if (seen.add(downloadKey(result))) {
                unique.add(result);
            }
        }
        return unique;
    }

    private OnlineTrackInfo annotateAvailability(OnlineTrackInfo track) {
        OnlineSourceProvider provider = providerFor(track);
        return provider == null
                ? track.withAvailability(false, "未知来源")
                : provider.annotateAvailability(track);
    }

    private OnlineSourceProvider providerFor(OnlineTrackInfo track) {
        return track == null ? null : providersByName.get(track.source());
    }

    private int sourcePriority(OnlineTrackInfo track) {
        if (track == null) {
            return providers.size();
        }
        for (int index = 0; index < providers.size(); index++) {
            if (providers.get(index).sourceName().equals(track.source())) {
                return index;
            }
        }
        return providers.size();
    }

    private String refererFor(String source) {
        OnlineSourceProvider provider = providersByName.get(source);
        return provider == null ? "https://music.163.com/" : provider.referer();
    }

    private boolean downloadViaCurl(String url, Path target, String source) {
        try {
            List<String> command = new ArrayList<>(List.of(
                    curlPath.toString(), "-L", "-f",
                    "-A", session.userAgent(),
                    "-H", "Accept: */*",
                    "-H", "Accept-Language: zh-CN,zh;q=0.9",
                    "-H", "Referer: " + refererFor(source),
                    "-H", "Connection: keep-alive",
                    "--connect-timeout", "12",
                    "--max-time", "60",
                    "--retry", "1",
                    "--retry-delay", "2",
                    "-o", target.toString()
            ));
            String cookies = session.cookieHeader(url);
            if (!cookies.isBlank()) {
                command.add("-b");
                command.add(cookies);
            }
            command.add(url);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(65, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception exception) {
            System.out.println("[crawler] curl err: " + exception.getMessage());
            return false;
        }
    }

    private void downloadViaJava(String url, Path target, String source)
            throws IOException, InterruptedException {
        try (CrawlerSession.DownloadResponse response = session.download(url, refererFor(source));
             InputStream input = response.body();
             OutputStream output = Files.newOutputStream(
                     target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }
            String contentType = response.firstHeader("Content-Type").toLowerCase(Locale.ROOT);
            if (contentType.contains("text/html")) {
                byte[] firstBytes = readPrefix(input, 512);
                String text = new String(firstBytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (text.contains("<!doctype") || text.contains("<html")) {
                    throw new IOException("server returned HTML");
                }
                output.write(firstBytes);
            }
            copy(input, output);
        }
    }

    private static Map<String, OnlineSourceProvider> indexProviders(List<OnlineSourceProvider> providers) {
        Map<String, OnlineSourceProvider> result = new HashMap<>();
        for (OnlineSourceProvider provider : providers) {
            result.put(provider.sourceName(), provider);
        }
        return Map.copyOf(result);
    }

    private static int availabilityPriority(OnlineTrackInfo track) {
        if (track.downloadable()) {
            return 0;
        }
        return "可尝试下载".equals(track.availabilityText()) ? 1 : 2;
    }

    private static String downloadKey(OnlineTrackInfo track) {
        return (track.source() == null ? "" : track.source())
                + "|" + (track.primaryId() == null ? "" : track.primaryId());
    }

    private static boolean sameOnlineTrack(OnlineTrackInfo first, OnlineTrackInfo second) {
        return Objects.equals(first.source(), second.source())
                && Objects.equals(first.primaryId(), second.primaryId());
    }

    private static boolean strongFallbackMatch(OnlineTrackInfo expected, OnlineTrackInfo candidate) {
        if (candidate == null || isBadFallbackText(candidate.title()) || isBadFallbackText(candidate.artist())) {
            return false;
        }
        String expectedTitle = normalizeTitle(expected.title());
        String candidateTitle = normalizeTitle(candidate.title());
        if (expectedTitle.isBlank() || candidateTitle.isBlank()) {
            return false;
        }

        boolean exactTitle = expectedTitle.equals(candidateTitle);
        boolean relatedTitle = expectedTitle.contains(candidateTitle) || candidateTitle.contains(expectedTitle);
        if (!exactTitle && !relatedTitle) {
            return false;
        }

        String expectedArtist = normalizeArtist(expected.artist());
        String candidateArtist = normalizeArtist(candidate.artist());
        boolean artistMatches = !expectedArtist.isBlank()
                && !candidateArtist.isBlank()
                && (expectedArtist.equals(candidateArtist)
                || expectedArtist.contains(candidateArtist)
                || candidateArtist.contains(expectedArtist));
        return exactTitle || artistMatches;
    }

    private static boolean isBadFallbackText(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String term : List.of("MusicPart", "儿歌", "童谣", "预告", "片段", "串烧", "铃声", "故事", "伴奏", "广播剧")) {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeArtist(String value) {
        String normalized = normalizeTitle(value);
        return normalized.equals(normalizeTitle("未知歌手")) ? "" : normalized;
    }

    private static String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int asciiParentheses = 0;
        int fullWidthParentheses = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if (character == '(') {
                asciiParentheses++;
                continue;
            }
            if (character == ')' && asciiParentheses > 0) {
                asciiParentheses--;
                continue;
            }
            if (character == '（') {
                fullWidthParentheses++;
                continue;
            }
            if (character == '）' && fullWidthParentheses > 0) {
                fullWidthParentheses--;
                continue;
            }
            if (asciiParentheses == 0
                    && fullWidthParentheses == 0
                    && (isChinese(character)
                    || character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9')) {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static boolean isChinese(char character) {
        return character >= '\u4e00' && character <= '\u9fff';
    }

    private static Path uniqueTarget(Path targetDir, String name, String extension) {
        Path target = targetDir.resolve(name + extension);
        int counter = 2;
        while (Files.exists(target)) {
            target = targetDir.resolve(name + " (" + counter++ + ")" + extension);
        }
        return target;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
    }

    private static void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static boolean validateFile(Path path) {
        try {
            if (Files.size(path) < 32_768) {
                return false;
            }
            byte[] header = new byte[256];
            try (InputStream input = Files.newInputStream(path)) {
                int length = input.read(header);
                return length > 0 && isAudioContent(header, length);
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String guessExtension(OnlineTrackInfo track, String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".flac")) return ".flac";
        if (lower.contains(".m4a") || lower.contains(".mp4")) return ".m4a";
        if (lower.contains(".aac")) return ".aac";
        if (lower.contains(".wav")) return ".wav";
        return track.source() != null && track.source().contains("QQ") ? ".m4a" : ".mp3";
    }

    public static boolean isAudioContent(byte[] data, int length) {
        if (data == null || length < 4) {
            return false;
        }
        if (data[0] == 'I' && data[1] == 'D' && data[2] == '3') return true;
        if ((data[0] & 0xff) == 0xff && (data[1] & 0xe0) == 0xe0) return true;
        if (length > 8 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') return true;
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') return true;
        if (data[0] == 'f' && data[1] == 'L' && data[2] == 'a' && data[3] == 'C') return true;
        return data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S';
    }

    private static synchronized boolean curlAvailable() {
        if (curlChecked) {
            return curlPath != null;
        }
        curlChecked = true;
        for (String location : new String[]{"C:\\Windows\\System32\\curl.exe", "curl.exe", "curl"}) {
            try {
                Process process = new ProcessBuilder(location, "--version").redirectErrorStream(true).start();
                if (process.waitFor(4, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    curlPath = Paths.get(location);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private static byte[] readPrefix(InputStream input, int maximumLength) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(maximumLength);
        byte[] buffer = new byte[Math.min(512, maximumLength)];
        while (output.size() < maximumLength) {
            int length = input.read(buffer, 0, Math.min(buffer.length, maximumLength - output.size()));
            if (length < 0) {
                break;
            }
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16_384];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
    }
}
