package app.musicplayer.playlist;

import static app.musicplayer.playlist.NamedPlaylist.State.*;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.online.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 歌单批量下载状态机。控制有界调度、暂停、继续、取消和失败重试，发布完成后保存条目并通知曲库。 Feed at most two songs to the bounded downloader,
 * with durable per-song results.
 */
public final class PlaylistDownloads implements AutoCloseable {
    /** 约定下载完成后的平台保存入口，将暂存音频变为可登记的本地歌曲。 */
    @FunctionalInterface
    public interface Publisher {
        /** 平台选择任务暂存位置；默认保留 Android 及现有调用的原策略。 */
        default Path stagingDirectory(String destination, Path fallback) {
            return fallback;
        }

        /** 将准备好的数据发布到目标位置，并把结果交给后续处理。 */
        String publish(Path downloaded, NamedPlaylist.Entry entry, String destination)
                throws Exception;
    }

    /** 约定按歌单歌曲搜索其他下载候选的入口。 */
    @FunctionalInterface
    public interface CandidateSearch {
        /** 查找歌单或为歌单歌曲产生可选择的下载候选。 */
        List<OnlineTrackInfo> find(NamedPlaylist.Entry entry, RequestCancellation cancellation);
    }

    /** 保存候选查询返回的歌曲和来源结果。 */
    private record CandidateResult(List<OnlineTrackInfo> tracks, String summary) {}

    /** 组合候选查询请求及其取消范围。 */
    @FunctionalInterface
    private interface CandidateLookup {
        /** 查找歌单或为歌单歌曲产生可选择的下载候选。 */
        CandidateResult find(NamedPlaylist.Entry entry, RequestCancellation cancellation);
    }

    /** 保存一次下载的输入、进度、状态及完成结果。 */
    private record Job(
            String playlist,
            NamedPlaylist.Entry entry,
            String destination,
            List<NamedPlaylist.Entry> aliases) {}

    private final PlaylistStore store;
    private final OnlineMusicSearchService service;
    private final Publisher publisher;
    private final CandidateLookup search;
    private final Path scratch;
    private final Executor ui;
    private final ExecutorService workers =
            Executors.newFixedThreadPool(2, r -> new Thread(r, "playlist-download"));
    private final ArrayDeque<Job> pending = new ArrayDeque<>();
    private final Map<String, CancellableTask<Path>> transfers = new HashMap<>();
    private final Map<String, RequestCancellation> searches = new HashMap<>();
    private final Set<String> occupied = new HashSet<>(),
            paused = new HashSet<>(),
            cancelled = new HashSet<>();
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<String>> publishedListeners =
            new CopyOnWriteArrayList<>();
    private int running;
    private boolean closed;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public PlaylistDownloads(PlaylistStore store, Path scratch, Publisher publisher, Executor ui) {
        this(store, scratch, publisher, ui, OnlineMusicSearchService.forPlaylists());
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public PlaylistDownloads(
            PlaylistStore store,
            Path scratch,
            Publisher publisher,
            Executor ui,
            OnlineMusicSearchService service) {
        this(
                store,
                scratch,
                publisher,
                ui,
                service,
                (CandidateLookup)
                        (entry, cancellation) -> {
                            var result = service.playlistSearch(entry.track(), cancellation);
                            String summary =
                                    result.sources().stream()
                                            .map(
                                                    source -> {
                                                        long matches =
                                                                result.tracks().stream()
                                                                        .filter(
                                                                                t ->
                                                                                        t.source()
                                                                                                        .equals(
                                                                                                                source
                                                                                                                        .name())
                                                                                                && PlaylistSongMatcher
                                                                                                        .matches(
                                                                                                                entry
                                                                                                                        .track(),
                                                                                                                t))
                                                                        .count();
                                                        String state =
                                                                switch (source.outcome()) {
                                                                    case COMPLETE ->
                                                                            "同版候选 " + matches;
                                                                    case TIMED_OUT -> "搜索超时";
                                                                    case SUSPENDED -> "暂时暂停";
                                                                    case CANCELLED -> "已取消";
                                                                    default -> "搜索失败";
                                                                };
                                                        return source.name() + "：" + state;
                                                    })
                                            .collect(java.util.stream.Collectors.joining("；"));
                            return new CandidateResult(result.tracks(), summary);
                        });
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public PlaylistDownloads(
            PlaylistStore store,
            Path scratch,
            Publisher publisher,
            Executor ui,
            OnlineMusicSearchService service,
            CandidateSearch search) {
        this(
                store,
                scratch,
                publisher,
                ui,
                service,
                (CandidateLookup)
                        (entry, cancellation) ->
                                new CandidateResult(search.find(entry, cancellation), ""));
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private PlaylistDownloads(
            PlaylistStore store,
            Path scratch,
            Publisher publisher,
            Executor ui,
            OnlineMusicSearchService service,
            CandidateLookup search) {
        this.store = store;
        this.scratch = scratch;
        this.publisher = publisher;
        this.ui = ui;
        this.service = service;
        this.search = search;
    }

    /** 查找歌单或为歌单歌曲产生可选择的下载候选。 */
    public List<OnlineTrackInfo> candidates(NamedPlaylist.Entry entry) {
        try (var cancellation = new RequestCancellation()) {
            return search.find(entry, cancellation).tracks();
        }
    }

    /** 注册任务或发布完成的观察者，让页面和曲库响应状态变化。 */
    public void listen(Consumer<String> listener) {
        listeners.add(listener);
    }

    /** 取消观察者注册，避免页面关闭后继续收到更新。 */
    public void unlisten(Consumer<String> listener) {
        listeners.remove(listener);
    }

    /** 注册任务或发布完成的观察者，让页面和曲库响应状态变化。 */
    public void listenPublished(Consumer<String> listener) {
        publishedListeners.add(listener);
    }

    /** 取消观察者注册，避免页面关闭后继续收到更新。 */
    public void unlistenPublished(Consumer<String> listener) {
        publishedListeners.remove(listener);
    }

    /** 通知观察者当前数据或任务状态已经改变。 */
    private void changed(String playlist) {
        ui.execute(() -> listeners.forEach(l -> l.accept(playlist)));
    }

    /** 检查请求编号或任务状态是否仍属于当前有效工作。 */
    public synchronized boolean active(String playlist) {
        return occupied.stream().anyMatch(k -> k.startsWith(playlist + "/"));
    }

    /** 检查批量下载是否处于暂停状态。 */
    public synchronized boolean paused(String playlist) {
        return paused.contains(playlist);
    }

    /** 开始当前组件的工作，并接通所需状态与完成回调。 */
    public synchronized void start(NamedPlaylist playlist, Set<String> items) {
        if (closed) throw new IllegalStateException("下载管理已关闭");
        cancelled.remove(playlist.id());
        paused.remove(playlist.id());
        synchronized (store) {
            var latest = store.findPlaylist(playlist.id()).orElseThrow();
            Map<String, List<NamedPlaylist.Entry>> groups = new LinkedHashMap<>();
            Map<String, NamedPlaylist.Entry> waitingItems = new HashMap<>();
            for (var entry : latest.entries())
                if (items.contains(entry.id())
                        && !occupied.contains(key(playlist.id(), entry.id()))) {
                    var waiting = entry.prepareDownload().status(WAITING, "");
                    waitingItems.put(entry.id(), waiting);
                    // Preserve repeated playlist rows, while downloading a new recording once.
                    String group =
                            entry.state() == REDOWNLOAD
                                    ? entry.id()
                                    : entry.track().identity()
                                            + "|"
                                            + (!entry.userSelectedVersion()
                                                            || entry.downloadTrack() == null
                                                    ? ""
                                                    : entry.downloadTrack().identity());
                    groups.computeIfAbsent(group, k -> new ArrayList<>()).add(waiting);
                }
            if (!waitingItems.isEmpty()) {
                store.savePlaylist(
                        latest.withEntries(
                                latest.entries().stream()
                                        .map(e -> waitingItems.getOrDefault(e.id(), e))
                                        .toList()));
                waitingItems.keySet().forEach(id -> occupied.add(key(playlist.id(), id)));
            }
            for (var group : groups.values())
                pending.add(
                        new Job(
                                playlist.id(),
                                group.get(0),
                                latest.directory(),
                                List.copyOf(group)));
        }
        pump();
        changed(playlist.id());
    }

    /** 暂停后续任务调度，保留已经完成的条目和可继续状态。 */
    public synchronized void pause(String playlist) {
        paused.add(playlist);
        changed(playlist);
    }

    /** 恢复暂停的批量下载调度。 */
    public synchronized void resume(String playlist) {
        paused.remove(playlist);
        pump();
        changed(playlist);
    }

    /** 取消指定任务或当前轮次，并通知相关工作停止。 */
    public synchronized void cancel(String playlist) {
        cancelled.add(playlist);
        paused.remove(playlist);
        for (var it = pending.iterator(); it.hasNext(); ) {
            var job = it.next();
            if (job.playlist.equals(playlist)) {
                it.remove();
                for (var entry : job.aliases) {
                    occupied.remove(key(playlist, entry.id()));
                    store.updatePlaylistEntry(playlist, entry.status(CANCELLED, ""));
                }
            }
        }
        searches.forEach(
                (key, token) -> {
                    if (key.startsWith(playlist + "/")) token.close();
                });
        transfers.forEach(
                (key, task) -> {
                    if (key.startsWith(playlist + "/")) task.cancel();
                });
        changed(playlist);
    }

    /** 按并发上限继续调度尚未完成的歌单条目。 */
    private synchronized void pump() {
        while (!closed && running < 2) {
            Job next = null;
            for (var it = pending.iterator(); it.hasNext(); ) {
                var job = it.next();
                if (!paused.contains(job.playlist)) {
                    next = job;
                    it.remove();
                    break;
                }
            }
            if (next == null) return;
            running++;
            Job job = next;
            workers.execute(() -> run(job));
        }
    }

    /** 执行当前任务的实际工作；调度方决定所在线程。 */
    private void run(Job job) {
        String key = key(job.playlist, job.entry.id());
        Path file = null;
        Path staging = null;
        OnlineTrackInfo actual = job.entry.downloadTrack();
        RequestCancellation cancellation = new RequestCancellation();
        String searchSummary = "";
        Set<String> attempted = new LinkedHashSet<>();
        try {
            staging = publisher.stagingDirectory(job.destination, scratch.resolve(job.entry.id()));
            synchronized (this) {
                if (closed || cancelled.contains(job.playlist)) throw new CancellationException();
                searches.put(key, cancellation);
                for (var entry : job.aliases)
                    store.updatePlaylistEntry(
                            job.playlist,
                            entry.status(DOWNLOADING, actual == null ? "正在匹配下载渠道" : ""));
            }
            changed(job.playlist);
            var found =
                    actual == null
                            ? search.find(job.entry, cancellation)
                            : new CandidateResult(List.of(actual), "已固定手动选择的版本和渠道");
            searchSummary = found.summary();
            var candidates =
                    PlaylistSongMatcher.ranked(
                            actual == null ? job.entry.track() : actual, found.tracks(), true);
            if (candidates.isEmpty()) throw new java.io.IOException("未匹配到歌名、歌手及版本一致的歌曲，请选择下载版本");
            Exception lastError = null;
            for (var candidate : candidates) {
                cancellation.check();
                actual = candidate;
                attempted.add(candidate.source());
                for (var entry : job.aliases)
                    store.updatePlaylistEntry(
                            job.playlist, entry.downloadFrom(actual).status(DOWNLOADING, ""));
                changed(job.playlist);
                var task =
                        service.download(
                                candidate.withAvailability(
                                        OnlineTrackInfo.Availability.TENTATIVE, "待下载"),
                                staging,
                                Runnable::run,
                                event -> {});
                synchronized (this) {
                    transfers.put(key, task);
                }
                try (var registration = cancellation.onCancel(task::cancel)) {
                    file = task.result().get();
                    break;
                } catch (Exception error) {
                    cancellation.check();
                    lastError = error;
                } finally {
                    synchronized (this) {
                        transfers.remove(key);
                    }
                }
            }
            if (file == null)
                throw lastError == null ? new java.io.IOException("下载渠道暂不可用") : lastError;
            cancellation.check();
            synchronized (this) {
                if (closed || cancelled.contains(job.playlist)) throw new CancellationException();
            }
            // Publication owns the complete file. Navigation never starts playback here.
            String location =
                    publisher.publish(file, job.entry.downloadFrom(actual), job.destination);
            file = null;
            for (var entry : job.aliases)
                store.updatePlaylistEntry(job.playlist, entry.downloadFrom(actual).local(location));
            ui.execute(() -> publishedListeners.forEach(listener -> listener.accept(job.playlist)));
        } catch (Exception error) {
            Throwable cause = error;
            while (cause.getCause() != null) cause = cause.getCause();
            boolean stopped =
                    cause instanceof CancellationException || cause instanceof InterruptedException;
            for (var entry : job.aliases)
                try {
                    store.updatePlaylistEntry(
                            job.playlist,
                            entry.downloadFrom(actual)
                                    .status(
                                            stopped ? CANCELLED : FAILED,
                                            stopped
                                                    ? ""
                                                    : errorMessage(cause)
                                                            + (searchSummary.isBlank()
                                                                    ? ""
                                                                    : "；" + searchSummary)
                                                            + (attempted.isEmpty()
                                                                    ? ""
                                                                    : "；已尝试："
                                                                            + String.join(
                                                                                    "、",
                                                                                    attempted))));
                } catch (RuntimeException ignored) {
                }
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            if (file != null)
                try {
                    Files.deleteIfExists(file);
                } catch (Exception ignored) {
                }
            try {
                if (staging != null) Files.deleteIfExists(staging);
            } catch (Exception ignored) {
            }
            cancellation.close();
            synchronized (this) {
                transfers.remove(key);
                searches.remove(key);
                for (var entry : job.aliases) occupied.remove(key(job.playlist, entry.id()));
                running--;
                pump();
            }
            changed(job.playlist);
        }
    }

    /** 提取异常链最内层的原因或可显示信息。 */
    private static String errorMessage(Throwable error) {
        String message = Objects.toString(error.getMessage(), "下载失败");
        if (message.contains("cannot resolve URL") || message.contains("cannot download track"))
            return "下载渠道未提供可用地址，可重试或选择其他下载版本";
        if (message.contains("unusable file")) return "平台返回的内容不是可用音频";
        return message;
    }

    /** 生成当前数据的稳定键，供去重、索引或缓存使用。 */
    private static String key(String playlist, String item) {
        return playlist + "/" + item;
    }

    /** 停止接收新工作，等待或取消本组件持有的任务并释放资源。 */
    public CompletableFuture<Void> shutdown() {
        synchronized (this) {
            closed = true;
            searches.values().forEach(RequestCancellation::close);
            transfers.values().forEach(CancellableTask::cancel);
            pending.clear();
            workers.shutdownNow();
        }
        return CompletableFuture.runAsync(
                () -> {
                    service.close();
                    boolean interrupted = false;
                    while (!workers.isTerminated())
                        try {
                            workers.awaitTermination(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    if (interrupted) Thread.currentThread().interrupt();
                });
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        shutdown();
    }
}
