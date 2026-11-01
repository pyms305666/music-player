package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 协调多个在线来源，并负责统一的下载、校验和跨来源回退。 各网站的搜索与地址解析位于独立的 OnlineSourceProvider 实现中。
 *
 * <p>稳定性策略：并行搜索 + 单来源超时；来源级熔断（连续失败暂停一段时间）； 解析结果按 source|id 缓存；下载时对受限歌曲自动换源到其他来源。
 */
public final class MusicCrawler implements AutoCloseable {
    private static final int SEARCH_TIMEOUT_SECONDS = 10;

    /** 连续失败达到 3 次后按此序列熔断，成功后复位。 */
    private static final long[] SUSPEND_DELAYS_MS = {60_000, 5 * 60_000, 15 * 60_000};

    private final CrawlerSession session = new CrawlerSession();
    private final List<OnlineSourceProvider> providers;
    private final Map<String, OnlineSourceProvider> providersByName;
    private final long searchBudgetNanos;
    private final boolean allowFallback;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public MusicCrawler() {
        this(true);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public MusicCrawler(boolean allowFallback) {
        this(allowFallback, 1);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    MusicCrawler(boolean allowFallback, int parallelSearches) {
        this.allowFallback = allowFallback;
        providers =
                List.of(
                        new KugouSourceProvider(session),
                        new KuwoSourceProvider(session),
                        new MiguSourceProvider(session),
                        new QqSourceProvider(session),
                        new NeteaseSourceProvider(session));
        providersByName = indexProviders(providers);
        searchBudgetNanos = TimeUnit.SECONDS.toNanos(SEARCH_TIMEOUT_SECONDS);
        searchExecutor = searchExecutor(providers.size(), parallelSearches);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    MusicCrawler(List<OnlineSourceProvider> providers, long budgetMillis) {
        this(providers, budgetMillis, 1);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    MusicCrawler(List<OnlineSourceProvider> providers, long budgetMillis, int parallelSearches) {
        this.allowFallback = true;
        this.providers = List.copyOf(providers);
        this.providersByName = indexProviders(providers);
        this.searchBudgetNanos = TimeUnit.MILLISECONDS.toNanos(budgetMillis);
        searchExecutor = searchExecutor(providers.size(), parallelSearches);
    }

    private final java.util.concurrent.ScheduledExecutorService searchExecutor;

    /** 创建来源请求线程池；并行搜索数量决定可同时运行的来源请求数。 */
    private static java.util.concurrent.ScheduledExecutorService searchExecutor(
            int sources, int parallelSearches) {
        if (parallelSearches < 1 || parallelSearches > 2)
            throw new IllegalArgumentException(
                    "Support one interactive search or two batch searches");
        return Executors.newScheduledThreadPool(
                Math.max(1, sources) * parallelSearches,
                runnable -> {
                    Thread thread = new Thread(runnable, "crawler-search");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private final ResolutionCache resolutionCache = new ResolutionCache(System::nanoTime);
    private final java.util.concurrent.atomic.AtomicBoolean resolutionMaintenanceStarted =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final Map<String, ProviderHealth> healthBySource = new ConcurrentHashMap<>();
    private final AudioDownloadTransport transport =
            new AudioDownloadTransport(session, this::refererFor);

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    public List<OnlineTrackInfo> search(String query) {
        return searchIncrementally(query, ignored -> {}).tracks();
    }

    /** 并行查询多个来源，按完成顺序发布累计结果，并在统一预算内结束。 */
    public OnlineSearchSnapshot searchIncrementally(
            String query, java.util.function.Consumer<OnlineSearchSnapshot> progress) {
        RequestCancellation token = session.currentCancellation();
        return searchIncrementally(
                query, progress, token == null ? new RequestCancellation() : token);
    }

    /** 并行查询多个来源，按完成顺序发布累计结果，并在统一预算内结束。 */
    OnlineSearchSnapshot searchIncrementally(
            String query,
            java.util.function.Consumer<OnlineSearchSnapshot> progress,
            RequestCancellation cancellation) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            var empty =
                    new OnlineSearchSnapshot(
                            normalizedQuery,
                            List.of(),
                            List.of(),
                            OnlineSearchSnapshot.State.EMPTY,
                            false);
            progress.accept(empty);
            return empty;
        }

        session.ensurePrimed();
        long deadline = System.nanoTime() + searchBudgetNanos;
        var completions =
                new java.util.concurrent.ExecutorCompletionService<SourceResult>(searchExecutor);
        Map<java.util.concurrent.Future<SourceResult>, String> futures = new HashMap<>();
        Map<String, OnlineSearchSnapshot.Outcome> outcomes = new java.util.LinkedHashMap<>();
        for (OnlineSourceProvider provider : providers) {
            String name = provider.sourceName();
            if (isSuspended(name)) {
                outcomes.put(name, OnlineSearchSnapshot.Outcome.SUSPENDED);
                continue;
            }
            outcomes.put(name, OnlineSearchSnapshot.Outcome.PENDING);
            var future =
                    completions.submit(
                            () -> {
                                session.setDeadline(deadline);
                                try (var scope = session.cancellationScope(cancellation)) {
                                    cancellation.check();
                                    List<OnlineTrackInfo> results =
                                            provider.search(normalizedQuery);
                                    cancellation.check();
                                    recordSuccess(name);
                                    return new SourceResult(
                                            name, results, OnlineSearchSnapshot.Outcome.COMPLETE);
                                } catch (RuntimeException error) {
                                    boolean expired =
                                            System.nanoTime() >= deadline
                                                    || Thread.currentThread().isInterrupted();
                                    if (!cancellation.isCancelled() && !expired)
                                        recordFailure(name);
                                    return new SourceResult(
                                            name,
                                            List.of(),
                                            cancellation.isCancelled()
                                                    ? OnlineSearchSnapshot.Outcome.CANCELLED
                                                    : expired
                                                            ? OnlineSearchSnapshot.Outcome.TIMED_OUT
                                                            : OnlineSearchSnapshot.Outcome.FAILED);
                                } finally {
                                    session.clearDeadline();
                                }
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
                        if (seen.add(downloadKey(track)))
                            results.add(
                                    track.withAvailability(
                                            OnlineTrackInfo.Availability.TENTATIVE, "可尝试下载"));
                    }
                } catch (ExecutionException | CancellationException failed) {
                    outcomes.put(futures.get(completed), OnlineSearchSnapshot.Outcome.FAILED);
                }
                progress.accept(searchSnapshot(normalizedQuery, results, outcomes, false, false));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (CancellationException cancelled) {
            /* The final snapshot records cancellation explicitly. */
        } finally {
            for (var future : futures.keySet()) if (!future.isDone()) future.cancel(true);
        }
        boolean cancelled = cancellation.isCancelled() || Thread.currentThread().isInterrupted();
        outcomes.replaceAll(
                (source, outcome) ->
                        outcome != OnlineSearchSnapshot.Outcome.PENDING
                                ? outcome
                                : cancelled
                                        ? OnlineSearchSnapshot.Outcome.CANCELLED
                                        : OnlineSearchSnapshot.Outcome.TIMED_OUT);
        var complete = searchSnapshot(normalizedQuery, results, outcomes, true, cancelled);
        progress.accept(complete);
        return complete;
    }

    /** 组合某个在线来源的搜索结果、错误与完成状态。 */
    private record SourceResult(
            String name, List<OnlineTrackInfo> tracks, OnlineSearchSnapshot.Outcome outcome) {}

    /** 结合累计歌曲及来源状态构造不可变搜索快照。 */
    private static OnlineSearchSnapshot searchSnapshot(
            String query,
            List<OnlineTrackInfo> tracks,
            Map<String, OnlineSearchSnapshot.Outcome> outcomes,
            boolean finished,
            boolean cancelled) {
        var sources =
                outcomes.entrySet().stream()
                        .map(
                                entry ->
                                        new OnlineSearchSnapshot.Source(
                                                entry.getKey(), entry.getValue()))
                        .toList();
        boolean failed =
                outcomes.values().stream()
                        .anyMatch(
                                outcome ->
                                        outcome == OnlineSearchSnapshot.Outcome.FAILED
                                                || outcome == OnlineSearchSnapshot.Outcome.TIMED_OUT
                                                || outcome
                                                        == OnlineSearchSnapshot.Outcome.SUSPENDED);
        var state =
                !finished
                        ? OnlineSearchSnapshot.State.SEARCHING
                        : cancelled
                                ? OnlineSearchSnapshot.State.CANCELLED
                                : failed
                                        ? tracks.isEmpty()
                                                ? OnlineSearchSnapshot.State.FAILED
                                                : OnlineSearchSnapshot.State.PARTIAL_FAILURE
                                        : tracks.isEmpty()
                                                ? OnlineSearchSnapshot.State.EMPTY
                                                : OnlineSearchSnapshot.State.COMPLETE;
        return new OnlineSearchSnapshot(query, tracks, sources, state, false);
    }

    /** 从歌曲所属来源解析下载地址，优先复用仍有效的解析缓存。 */
    public String resolveDownloadUrl(OnlineTrackInfo track) {
        OnlineSourceProvider provider = providerFor(track);
        return provider == null ? null : resolveCached(provider, track);
    }

    /** 启动或执行下载，接通传输进度、文件发布与完成处理。 */
    public Path download(OnlineTrackInfo track, Path targetDir)
            throws IOException, InterruptedException {
        return download(track, targetDir, new RequestCancellation(), ignored -> {});
    }

    /** 启动或执行下载，接通传输进度、文件发布与完成处理。 */
    Path download(
            OnlineTrackInfo track,
            Path targetDir,
            RequestCancellation cancellation,
            java.util.function.Consumer<DownloadEvent> progress)
            throws IOException, InterruptedException {
        try (var scope = session.cancellationScope(cancellation)) {
            cancellation.check();
            return downloadInternal(track, targetDir, progress);
        }
    }

    /** 在指定取消作用域内执行工作，完成后恢复原作用域。 */
    <T> T withinCancellation(
            RequestCancellation cancellation, java.util.function.Supplier<T> work) {
        try (var scope = session.cancellationScope(cancellation)) {
            cancellation.check();
            return work.get();
        }
    }

    /** 尝试原来源及匹配的备用来源，记录失败候选，最后报告有效文件或下载错误。 */
    private Path downloadInternal(
            OnlineTrackInfo track,
            Path targetDir,
            java.util.function.Consumer<DownloadEvent> progress)
            throws IOException, InterruptedException {
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
            System.out.println(
                    "[crawler] "
                            + track.source()
                            + " is "
                            + track.availabilityText()
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
                    System.out.println(
                            "[crawler] candidate failed: "
                                    + candidate.source()
                                    + " - "
                                    + candidate.title()
                                    + " : "
                                    + exception.getMessage());
                }
            }
            if (round == 0 && allowFallback) {
                candidates = fallbackCandidates(track, failedDownloadKeys);
                if (candidates.isEmpty()) {
                    break;
                }
            }
            if (!allowFallback) break;
        }
        throw lastError != null
                ? lastError
                : new IOException(track.source() + ": cannot download track");
    }

    /** 通过共享 HTTP 会话读取指定地址的响应文本。 */
    String fetch(String url, String referer) throws Exception {
        session.ensurePrimed();
        return session.fetch(url, referer);
    }

    /** 解析单个候选地址、传输到临时文件、校验格式，再原子发布最终文件。 */
    private Path tryDownloadCandidate(
            OnlineTrackInfo track,
            Path targetDir,
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
            boolean ready =
                    AudioDownloadTransport.curlAvailable()
                            && transport.downloadViaCurl(url, temporary, track.source(), progress)
                            && AudioDownloadTransport.validateFile(temporary);
            session.checkCancellation();
            if (!ready) transport.downloadViaJava(url, temporary, track.source(), progress);
            progress.accept(
                    new DownloadEvent(
                            DownloadEvent.Stage.VALIDATING,
                            Files.size(temporary),
                            java.util.OptionalLong.empty()));
            if (!AudioDownloadTransport.validateFile(temporary))
                throw new IOException(track.source() + ": unusable file");
            extension = DownloadFiles.detectedExtension(temporary, extension);
            if (Boolean.getBoolean("musicplayer.desktop")
                    && (extension.equals(".flac")
                            || extension.equals(".ogg")
                            || extension.equals(".aac"))) {
                throw new IOException("桌面播放器不支持此来源的音频编码，将尝试其他来源");
            }
            session.checkCancellation();
            return DownloadFiles.publish(
                    temporary, targetDir, sanitize(artist + " - " + track.title()), extension);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** 搜索可替代的版本，排除失败或不匹配歌曲，再按来源优先级排序。 */
    List<OnlineTrackInfo> fallbackCandidates(
            OnlineTrackInfo original, Set<String> failedDownloadKeys) {
        String query =
                ((original.artist() == null ? "" : original.artist() + " ")
                                + (original.title() == null ? "" : original.title()))
                        .trim();
        if (query.isBlank()) {
            return List.of();
        }
        try {
            List<OnlineTrackInfo> results = search(query);
            List<OnlineTrackInfo> filtered = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (OnlineTrackInfo candidate : results) {
                if (FallbackTrackMatcher.sameOnlineTrack(original, candidate)
                        || failedDownloadKeys.contains(downloadKey(candidate))
                        || !seen.add(downloadKey(candidate))
                        || !candidate.canAttemptDownload()
                        || !FallbackTrackMatcher.strongFallbackMatch(original, candidate)) {
                    continue;
                }
                filtered.add(candidate);
            }
            // 先比较全部匹配来源再限制数量，避免较快返回的来源占满候选而排除酷我和咪咕。
            filtered.sort(Comparator.comparingInt(this::sourcePriority));
            return filtered.stream().limit(6).toList();
        } catch (Exception exception) {
            System.out.println("[crawler] fallback search failed: " + exception.getMessage());
            return List.of();
        }
    }

    /** 按来源歌曲身份移除重复结果，保留首次出现的顺序。 */
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
            System.out.println(
                    "[crawler]["
                            + provider.sourceName()
                            + "] resolve err: "
                            + exception.getMessage());
            url = null;
        }
        resolutionCache.put(key, url);
        if (resolutionMaintenanceStarted.compareAndSet(false, true)) {
            // Reuse the owned source pool, without creating another maintenance thread.
            try {
                searchExecutor.scheduleWithFixedDelay(
                        resolutionCache::purge, 1, 1, TimeUnit.MINUTES);
            } catch (java.util.concurrent.RejectedExecutionException closing) {
                resolutionCache.close();
            }
        }
        return url;
    }

    /** 检查渠道是否仍处于连续失败后的暂停期。 */
    private boolean isSuspended(String source) {
        ProviderHealth health = healthBySource.get(source);
        return health != null && health.suspendedUntil > System.currentTimeMillis();
    }

    /** 记录渠道请求成功，重置连续失败次数。 */
    private synchronized void recordSuccess(String source) {
        ProviderHealth health = healthBySource.get(source);
        if (health != null) {
            health.consecutiveFailures = 0;
        }
    }

    /** 记录连续失败，达到阈值后按逐级延长的间隔暂停渠道。 */
    private synchronized void recordFailure(String source) {
        ProviderHealth health = healthBySource.computeIfAbsent(source, key -> new ProviderHealth());
        int failures = health.consecutiveFailures + 1;
        health.consecutiveFailures = failures;
        if (failures >= 3) {
            int level = Math.min(failures - 3, SUSPEND_DELAYS_MS.length - 1);
            health.suspendedUntil = System.currentTimeMillis() + SUSPEND_DELAYS_MS[level];
            System.out.println(
                    "[crawler]["
                            + source
                            + "] suspended for "
                            + (SUSPEND_DELAYS_MS[level] / 1000)
                            + "s after "
                            + failures
                            + " failures");
        }
    }

    /** 根据歌曲来源查找对应适配器，没有已注册来源时返回空值。 */
    private OnlineSourceProvider providerFor(OnlineTrackInfo track) {
        return track == null ? null : providersByName.get(track.source());
    }

    /** 换源优先酷我和咪咕，其余来源继续按原有配置顺序尝试。 */
    private int sourcePriority(OnlineTrackInfo track) {
        if (KuwoSourceProvider.SOURCE.equals(track.source())) return 0;
        if (MiguSourceProvider.SOURCE.equals(track.source())) return 1;
        for (int index = 0; index < providers.size(); index++) {
            if (providers.get(index).sourceName().equals(track.source())) {
                return index + 2;
            }
        }
        return providers.size() + 2;
    }

    /** 取得来源要求的 Referer 请求头，供 HTTP 请求使用。 */
    private String refererFor(String source) {
        OnlineSourceProvider provider = providersByName.get(source);
        return provider == null ? "https://music.163.com/" : provider.referer();
    }

    /** 按来源名建立适配器索引，后续解析可直接查找。 */
    private static Map<String, OnlineSourceProvider> indexProviders(
            List<OnlineSourceProvider> providers) {
        Map<String, OnlineSourceProvider> result = new HashMap<>();
        for (OnlineSourceProvider provider : providers) {
            result.put(provider.sourceName(), provider);
        }
        return Map.copyOf(result);
    }

    /** 生成当前数据的稳定键，供去重、索引或缓存使用。 */
    static String downloadKey(OnlineTrackInfo track) {
        return track.identity();
    }

    /** 替换文件名中的非法字符，生成适合保存到磁盘的名称。 */
    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", " ").trim();
    }

    /** 按地址和来源推测音频扩展名，实际文件头检测会进一步修正。 */
    static String guessExtension(OnlineTrackInfo track, String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".flac") || lower.contains("flac")) return ".flac";
        if (lower.contains(".m4a") || lower.contains(".mp4")) return ".m4a";
        if (lower.contains(".aac")) return ".aac";
        if (lower.contains(".wav")) return ".wav";
        return track.source() != null && track.source().contains("QQ") ? ".m4a" : ".mp3";
    }

    /** 停止自有来源线程池，关闭自有 HTTP 会话及地址缓存。 */
    @Override
    public void close() {
        searchExecutor.shutdownNow();
        session.close();
        resolutionCache.close();
    }

    /** 检查文件头是否属于受支持的音频格式，保留原公共入口。 */
    public static boolean isAudioContent(byte[] data, int length) {
        return AudioDownloadTransport.isAudioContent(data, length);
    }

    /** 记录渠道连续失败次数与暂停截止时间。 */
    private static final class ProviderHealth {
        private volatile int consecutiveFailures;
        private volatile long suspendedUntil;
    }
}
