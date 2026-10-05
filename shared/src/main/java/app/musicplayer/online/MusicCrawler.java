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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 协调多个在线来源，并负责统一的下载、校验和跨来源回退。
 * 各网站的搜索与地址解析位于独立的 OnlineSourceProvider 实现中。
 *
 * 稳定性策略：并行搜索 + 单来源超时；来源级熔断（连续失败暂停一段时间）；
 * 解析结果按 source|id 缓存；下载时对受限歌曲自动换源到其他来源。
 */
public final class MusicCrawler implements AutoCloseable {
    private static final int SEARCH_TIMEOUT_SECONDS = 10;
    /** 连续失败达到 3 次后按此序列熔断，成功后复位。 */
    private static final long[] SUSPEND_DELAYS_MS = {60_000, 5 * 60_000, 15 * 60_000};

    private final CrawlerSession session = new CrawlerSession();
    private final List<OnlineSourceProvider> providers;
    private final Map<String, OnlineSourceProvider> providersByName;
    private final long searchBudgetNanos;

    public MusicCrawler() {
        providers = List.of(new KugouSourceProvider(session), new KuwoSourceProvider(session),
                new MiguSourceProvider(session), new QqSourceProvider(session), new NeteaseSourceProvider(session));
        providersByName = indexProviders(providers);
        searchBudgetNanos = TimeUnit.SECONDS.toNanos(SEARCH_TIMEOUT_SECONDS);
    }

    MusicCrawler(List<OnlineSourceProvider> providers, long budgetMillis) {
        this.providers = List.copyOf(providers);
        this.providersByName = indexProviders(providers);
        this.searchBudgetNanos = TimeUnit.MILLISECONDS.toNanos(budgetMillis);
    }

    private final java.util.concurrent.ScheduledExecutorService searchExecutor = Executors.newScheduledThreadPool(5, runnable -> {
        Thread thread = new Thread(runnable, "crawler-search");
        thread.setDaemon(true);
        return thread;
    });
    private final ResolutionCache resolutionCache = new ResolutionCache(System::nanoTime);
    private final java.util.concurrent.atomic.AtomicBoolean resolutionMaintenanceStarted = new java.util.concurrent.atomic.AtomicBoolean();
    private final Map<String, ProviderHealth> healthBySource = new ConcurrentHashMap<>();
    private static Path curlPath;
    private static boolean curlChecked;

    public List<OnlineTrackInfo> search(String query) {
        return searchIncrementally(query, ignored -> { }).tracks();
    }

    public OnlineSearchSnapshot searchIncrementally(String query,
            java.util.function.Consumer<OnlineSearchSnapshot> progress) {
        RequestCancellation token = session.currentCancellation();
        return searchIncrementally(query, progress, token == null ? new RequestCancellation() : token);
    }

    OnlineSearchSnapshot searchIncrementally(String query,
            java.util.function.Consumer<OnlineSearchSnapshot> progress, RequestCancellation cancellation) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            var empty = new OnlineSearchSnapshot(normalizedQuery, List.of(), List.of(), OnlineSearchSnapshot.State.EMPTY, false);
            progress.accept(empty);
            return empty;
        }

        session.ensurePrimed();
        long deadline = System.nanoTime() + searchBudgetNanos;
        var completions = new java.util.concurrent.ExecutorCompletionService<SourceResult>(searchExecutor);
        Map<java.util.concurrent.Future<SourceResult>, String> futures = new HashMap<>();
        Map<String, OnlineSearchSnapshot.Outcome> outcomes = new java.util.LinkedHashMap<>();
        for (OnlineSourceProvider provider : providers) {
            String name = provider.sourceName();
            if (isSuspended(name)) { outcomes.put(name, OnlineSearchSnapshot.Outcome.SUSPENDED); continue; }
            outcomes.put(name, OnlineSearchSnapshot.Outcome.PENDING);
            var future = completions.submit(() -> {
                session.setDeadline(deadline);
                try (var scope = session.cancellationScope(cancellation)) {
                    cancellation.check();
                    List<OnlineTrackInfo> results = provider.search(normalizedQuery);
                    cancellation.check();
                    recordSuccess(name);
                    return new SourceResult(name, results, OnlineSearchSnapshot.Outcome.COMPLETE);
                } catch (RuntimeException error) {
                    boolean expired = System.nanoTime() >= deadline || Thread.currentThread().isInterrupted();
                    if (!cancellation.isCancelled() && !expired) recordFailure(name);
                    return new SourceResult(name, List.of(), cancellation.isCancelled()
                            ? OnlineSearchSnapshot.Outcome.CANCELLED : expired
                            ? OnlineSearchSnapshot.Outcome.TIMED_OUT : OnlineSearchSnapshot.Outcome.FAILED);
                } finally { session.clearDeadline(); }
            });
            futures.put(future, name);
        }
        List<OnlineTrackInfo> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            progress.accept(searchSnapshot(normalizedQuery, results, outcomes, false, false));
            for (int received = 0; received < futures.size(); received++) {
                cancellation.check();
                long left = Math.max(0, deadline - System.nanoTime());
                var completed = completions.poll(left, TimeUnit.NANOSECONDS);
                if (completed == null) break;
                try {
                    SourceResult batch = completed.get();
                    outcomes.put(batch.name(), batch.outcome());
                    for (OnlineTrackInfo track : batch.tracks()) {
                        if (seen.add(downloadKey(track))) results.add(track.withAvailability(
                                OnlineTrackInfo.Availability.TENTATIVE, "可尝试下载"));
                    }
                } catch (ExecutionException | CancellationException failed) {
                    outcomes.put(futures.get(completed), OnlineSearchSnapshot.Outcome.FAILED);
                }
                progress.accept(searchSnapshot(normalizedQuery, results, outcomes, false, false));
            }
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        catch (CancellationException cancelled) { /* The final snapshot records cancellation explicitly. */ }
        finally { for (var future : futures.keySet()) if (!future.isDone()) future.cancel(true); }
        boolean cancelled = cancellation.isCancelled() || Thread.currentThread().isInterrupted();
        outcomes.replaceAll((source, outcome) -> outcome != OnlineSearchSnapshot.Outcome.PENDING ? outcome
                : cancelled ? OnlineSearchSnapshot.Outcome.CANCELLED : OnlineSearchSnapshot.Outcome.TIMED_OUT);
        var complete = searchSnapshot(normalizedQuery, results, outcomes, true, cancelled);
        progress.accept(complete);
        return complete;
    }

    private record SourceResult(String name, List<OnlineTrackInfo> tracks, OnlineSearchSnapshot.Outcome outcome) { }

    private static OnlineSearchSnapshot searchSnapshot(String query, List<OnlineTrackInfo> tracks,
            Map<String, OnlineSearchSnapshot.Outcome> outcomes, boolean finished, boolean cancelled) {
        var sources = outcomes.entrySet().stream().map(entry ->
                new OnlineSearchSnapshot.Source(entry.getKey(), entry.getValue())).toList();
        boolean failed = outcomes.values().stream().anyMatch(outcome -> outcome == OnlineSearchSnapshot.Outcome.FAILED
                || outcome == OnlineSearchSnapshot.Outcome.TIMED_OUT || outcome == OnlineSearchSnapshot.Outcome.SUSPENDED);
        var state = !finished ? OnlineSearchSnapshot.State.SEARCHING
                : cancelled ? OnlineSearchSnapshot.State.CANCELLED
                : failed ? tracks.isEmpty() ? OnlineSearchSnapshot.State.FAILED : OnlineSearchSnapshot.State.PARTIAL_FAILURE
                : tracks.isEmpty() ? OnlineSearchSnapshot.State.EMPTY : OnlineSearchSnapshot.State.COMPLETE;
        return new OnlineSearchSnapshot(query, tracks, sources, state, false);
    }

    public String resolveDownloadUrl(OnlineTrackInfo track) {
        OnlineSourceProvider provider = providerFor(track);
        return provider == null ? null : resolveCached(provider, track);
    }

    public Path download(OnlineTrackInfo track, Path targetDir)
            throws IOException, InterruptedException {
        return download(track, targetDir, new RequestCancellation(), ignored -> { });
    }

    Path download(OnlineTrackInfo track, Path targetDir, RequestCancellation cancellation,
                  java.util.function.Consumer<DownloadEvent> progress) throws IOException, InterruptedException {
        try (var scope = session.cancellationScope(cancellation)) {
            cancellation.check();
            return downloadInternal(track, targetDir, progress);
        }
    }

    <T> T withinCancellation(RequestCancellation cancellation, java.util.function.Supplier<T> work) {
        try (var scope = session.cancellationScope(cancellation)) { cancellation.check(); return work.get(); }
    }

    private Path downloadInternal(OnlineTrackInfo track, Path targetDir,
            java.util.function.Consumer<DownloadEvent> progress) throws IOException, InterruptedException {
        if (track == null || track.source() == null) {
            throw new IOException("invalid track info");
        }

        session.ensurePrimed();
        Files.createDirectories(targetDir);
        IOException lastError = null;
        List<OnlineTrackInfo> candidates = new ArrayList<>();
        if (track.canAttemptDownload()) {
            candidates.add(track);
        } else {
            System.out.println("[crawler] " + track.source() + " is " + track.availabilityText()
                    + ", auto-switching sources");
        }
        Set<String> failedDownloadKeys = new HashSet<>();

        for (int round = 0; round < 2; round++) {
            for (OnlineTrackInfo candidate : candidates) {
                session.checkCancellation();
                String key = downloadKey(candidate);
                if (failedDownloadKeys.contains(key)) {
                    continue;
                }
                try {
                    return tryDownloadCandidate(candidate, targetDir, progress);
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

    private Path tryDownloadCandidate(OnlineTrackInfo track, Path targetDir,
            java.util.function.Consumer<DownloadEvent> progress)
            throws IOException, InterruptedException {
        progress.accept(DownloadEvent.of(DownloadEvent.Stage.RESOLVING));
        session.checkCancellation();
        String url = resolveDownloadUrl(track);
        if (url == null || url.isBlank()) {
            throw new IOException(track.source() + ": cannot resolve URL");
        }

        String extension = guessExtension(track, url);
        String artist = track.artist() == null ? "Unknown" : track.artist();
        Path temporary = Files.createTempFile(targetDir, ".za-download-", ".part");
        try {
            boolean ready = curlAvailable() && downloadViaCurl(url, temporary, track.source(), progress)
                    && validateFile(temporary);
            session.checkCancellation();
            if (!ready) downloadViaJava(url, temporary, track.source(), progress);
            progress.accept(new DownloadEvent(DownloadEvent.Stage.VALIDATING, Files.size(temporary), java.util.OptionalLong.empty()));
            if (!validateFile(temporary)) throw new IOException(track.source() + ": unusable file");
            extension = DownloadFiles.detectedExtension(temporary, extension);
            if (Boolean.getBoolean("musicplayer.desktop")
                    && (extension.equals(".flac") || extension.equals(".ogg") || extension.equals(".aac"))) {
                throw new IOException("桌面播放器不支持此来源的音频编码，将尝试其他来源");
            }
            session.checkCancellation();
            return DownloadFiles.publish(temporary, targetDir, sanitize(artist + " - " + track.title()), extension);
        } finally {
            Files.deleteIfExists(temporary);
        }
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

    /** 解析结果按 source|id 缓存：直链带签名有时效，避免搜索阶段重复请求触发风控。 */
    private String resolveCached(OnlineSourceProvider provider, OnlineTrackInfo track) {
        String key = downloadKey(track);
        ResolutionCache.Result cached = resolutionCache.get(key);
        if (cached != null) {
            return cached.url();
        }
        String url;
        try {
            url = provider.resolve(track);
            session.checkCancellation();
            recordSuccess(provider.sourceName());
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception exception) {
            session.checkCancellation();
            recordFailure(provider.sourceName());
            System.out.println("[crawler][" + provider.sourceName() + "] resolve err: "
                    + exception.getMessage());
            url = null;
        }
        resolutionCache.put(key, url);
        if (resolutionMaintenanceStarted.compareAndSet(false, true)) {
            // Reuse the owned source pool, without creating another maintenance thread.
            try { searchExecutor.scheduleWithFixedDelay(resolutionCache::purge, 1, 1, TimeUnit.MINUTES); }
            catch (java.util.concurrent.RejectedExecutionException closing) { resolutionCache.close(); }
        }
        return url;
    }

    private boolean isSuspended(String source) {
        ProviderHealth health = healthBySource.get(source);
        return health != null && health.suspendedUntil > System.currentTimeMillis();
    }

    private synchronized void recordSuccess(String source) {
        ProviderHealth health = healthBySource.get(source);
        if (health != null) {
            health.consecutiveFailures = 0;
        }
    }

    private synchronized void recordFailure(String source) {
        ProviderHealth health = healthBySource.computeIfAbsent(source, key -> new ProviderHealth());
        int failures = health.consecutiveFailures + 1;
        health.consecutiveFailures = failures;
        if (failures >= 3) {
            int level = Math.min(failures - 3, SUSPEND_DELAYS_MS.length - 1);
            health.suspendedUntil = System.currentTimeMillis() + SUSPEND_DELAYS_MS[level];
            System.out.println("[crawler][" + source + "] suspended for "
                    + (SUSPEND_DELAYS_MS[level] / 1000) + "s after " + failures + " failures");
        }
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

    private boolean downloadViaCurl(String url, Path target, String source,
            java.util.function.Consumer<DownloadEvent> progress) throws InterruptedException {
        try {
            List<String> command = new ArrayList<>(List.of(
                    curlPath.toString(), "-L", "-f", "--silent", "--show-error",
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
            try (var detach = session.onCancellation(process::destroyForcibly)) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(65);
                while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    session.checkCancellation();
                    progress.accept(new DownloadEvent(DownloadEvent.Stage.TRANSFERRING, Files.size(target), java.util.OptionalLong.empty()));
                    if (System.nanoTime() >= deadline) return false;
                }
                progress.accept(new DownloadEvent(DownloadEvent.Stage.TRANSFERRING, Files.size(target), java.util.OptionalLong.empty()));
                return process.exitValue() == 0;
            } finally {
                if (process.isAlive()) process.destroyForcibly();
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
            throw cancelled;
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (Exception exception) {
            System.out.println("[crawler] curl err: " + exception.getMessage());
            return false;
        }
    }

    private void downloadViaJava(String url, Path target, String source,
            java.util.function.Consumer<DownloadEvent> progress)
            throws IOException, InterruptedException {
        try (CrawlerSession.DownloadResponse response = session.download(url, refererFor(source));
             InputStream input = response.body();
             OutputStream output = Files.newOutputStream(
                     target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            if (response.statusCode() < 200 || response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode());
            }
            String contentType = response.firstHeader("Content-Type").toLowerCase(Locale.ROOT);
            java.util.OptionalLong total = contentLength(response.firstHeader("Content-Length"));
            long prefixLength = 0;
            progress.accept(new DownloadEvent(DownloadEvent.Stage.TRANSFERRING, 0, total));
            if (contentType.contains("text/html")) {
                byte[] firstBytes = readPrefix(input, 512);
                String text = new String(firstBytes, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if (text.contains("<!doctype") || text.contains("<html")) {
                    throw new IOException("server returned HTML");
                }
                output.write(firstBytes);
                prefixLength = firstBytes.length;
            }
            copy(input, output, progress, total, prefixLength);
            output.flush();
            String expected = response.firstHeader("Content-Length");
            if (!expected.isBlank()) {
                try {
                    if (Files.size(target) != Long.parseLong(expected)) throw new IOException("下载不完整");
                } catch (NumberFormatException invalid) { throw new IOException("无效文件长度", invalid); }
            }
        }
    }

    private static java.util.OptionalLong contentLength(String value) throws IOException {
        if (value.isBlank()) return java.util.OptionalLong.empty();
        try {
            long length = Long.parseLong(value);
            return length > 0 ? java.util.OptionalLong.of(length) : java.util.OptionalLong.empty();
        } catch (NumberFormatException invalid) { throw new IOException("无效文件长度", invalid); }
    }

    private static Map<String, OnlineSourceProvider> indexProviders(List<OnlineSourceProvider> providers) {
        Map<String, OnlineSourceProvider> result = new HashMap<>();
        for (OnlineSourceProvider provider : providers) {
            result.put(provider.sourceName(), provider);
        }
        return Map.copyOf(result);
    }

    static String downloadKey(OnlineTrackInfo track) {
        return track.identity();
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

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
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

    static String guessExtension(OnlineTrackInfo track, String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".flac") || lower.contains("flac")) return ".flac";
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

    private static void copy(InputStream input, OutputStream output,
            java.util.function.Consumer<DownloadEvent> progress, java.util.OptionalLong total, long transferred) throws IOException {
        byte[] buffer = new byte[16_384];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("下载已取消");
            output.write(buffer, 0, length);
            transferred += length;
            progress.accept(new DownloadEvent(DownloadEvent.Stage.TRANSFERRING, transferred, total));
        }
    }

    @Override public void close() {
        searchExecutor.shutdownNow();
        session.close();
        resolutionCache.close();
    }


    private static final class ProviderHealth {
        private volatile int consecutiveFailures;
        private volatile long suspendedUntil;
    }
}
