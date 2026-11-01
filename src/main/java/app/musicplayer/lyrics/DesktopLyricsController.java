package app.musicplayer.lyrics;

import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.Track;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 桌面歌词请求与重试的生命周期。界面只提供歌曲、时长和显示回调。 请求编号用于丢弃旧结果；只关闭自己创建的重试线程，不关闭借用的歌词服务。 */
public final class DesktopLyricsController implements AutoCloseable {
    private static final int[] RETRY_DELAYS_SECONDS = {10, 20, 30, 60};

    /** manual 为 true 时跳过本地缓存，直接查询在线歌词。 */
    @FunctionalInterface
    public interface Lookup {
        /** 异步查询指定歌曲歌词；manual 为 true 时跳过本地缓存，返回歌词与封面结果。 */
        CompletableFuture<LyricsLookupResult> find(Track track, Duration duration, boolean manual);
    }

    /** load 在界面线程直接发送初始状态；异步结果由 ui 执行器投递到同一线程。 */
    public record Callbacks(
            Consumer<Boolean> loading,
            Consumer<String> source,
            Consumer<LyricsLookupResult> result,
            Consumer<Lyrics> failure) {}

    private final Lookup lookup;
    private final Executor ui;
    private final Supplier<Track> currentTrack;
    private final Callbacks callbacks;
    private final ScheduledExecutorService scheduler;
    private final boolean ownsScheduler;
    private volatile long generation;
    private volatile boolean closed;
    private ScheduledFuture<?> retryTask;
    private int retryAttempt;

    /** 创建专用重试线程；调用者负责在界面关闭时调用 close。 */
    public DesktopLyricsController(
            Lookup lookup, Executor ui, Supplier<Track> currentTrack, Callbacks callbacks) {
        this(
                lookup,
                ui,
                currentTrack,
                callbacks,
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "lyrics-retry");
                            thread.setDaemon(true);
                            return thread;
                        }),
                true);
    }

    /** 测试可借用可控调度器，避免等待真实的 10～60 秒重试间隔。 */
    DesktopLyricsController(
            Lookup lookup,
            Executor ui,
            Supplier<Track> currentTrack,
            Callbacks callbacks,
            ScheduledExecutorService scheduler,
            boolean ownsScheduler) {
        this.lookup = lookup;
        this.ui = ui;
        this.currentTrack = currentTrack;
        this.callbacks = callbacks;
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
    }

    /** 在界面线程发起加载；手动刷新与自动重试使用相同的过期结果检查。 */
    public void load(Track track, Duration duration, boolean manual) {
        if (closed || track == null) return;
        cancelRetry();
        long request = ++generation;
        callbacks.loading().accept(true);
        callbacks.source().accept(manual ? "歌词来源：正在重新搜索..." : "歌词来源：正在搜索...");
        lookup.find(track, duration, manual)
                .whenComplete(
                        (result, error) ->
                                ui.execute(
                                        () -> {
                                            if (!active(track, request)) return;
                                            callbacks.loading().accept(false);
                                            if (error != null) {
                                                callbacks
                                                        .failure()
                                                        .accept(Lyrics.empty("歌词加载失败，稍后继续搜索"));
                                                scheduleRetry(track, duration, request);
                                                return;
                                            }
                                            callbacks.result().accept(result);
                                            if (missing(result.lyrics()))
                                                scheduleRetry(track, duration, request);
                                        }));
    }

    /** 切歌、预览在线结果或销毁播放器时使旧查询失效。 */
    public void invalidate() {
        generation++;
        cancelRetry();
    }

    /** 只取消重试计时，不关闭借用的歌词服务。 */
    public void cancelRetry() {
        retryAttempt = 0;
        if (retryTask != null) {
            retryTask.cancel(true);
            retryTask = null;
        }
    }

    /** 检查请求编号或任务状态是否仍属于当前有效工作。 */
    private boolean active(Track track, long request) {
        return !closed && generation == request && currentTrack.get() == track;
    }

    /** 按逐级等待间隔安排下一次查词，并保留当前请求编号。 */
    private void scheduleRetry(Track track, Duration duration, long request) {
        if (!active(track, request)) return;
        int delay = RETRY_DELAYS_SECONDS[Math.min(retryAttempt, RETRY_DELAYS_SECONDS.length - 1)];
        retryAttempt++;
        callbacks.source().accept("歌词来源：未找到，" + delay + " 秒后继续搜索");
        retryTask =
                scheduler.schedule(
                        () -> ui.execute(() -> retry(track, duration, request)),
                        delay,
                        TimeUnit.SECONDS);
    }

    /** 发起本轮在线查词；无结果或失败时继续等待，成功后更新界面。 */
    private void retry(Track track, Duration duration, long request) {
        if (!active(track, request)) return;
        lookup.find(track, duration, true)
                .whenComplete(
                        (result, error) ->
                                ui.execute(
                                        () -> {
                                            if (!active(track, request)) return;
                                            if (error != null
                                                    || result == null
                                                    || missing(result.lyrics())) {
                                                scheduleRetry(track, duration, request);
                                                return;
                                            }
                                            callbacks.loading().accept(false);
                                            callbacks.result().accept(result);
                                        }));
    }

    /** 检查歌词结果是否仍表示未找到或等待继续搜索。 */
    private static boolean missing(Lyrics lyrics) {
        return lyrics == null
                || lyrics.source().startsWith("没有找到")
                || lyrics.source().startsWith("暂无")
                || lyrics.source().contains("继续搜索");
    }

    /** 使排队回调失效，取消重试，并停止自有调度器。 */
    @Override
    public void close() {
        closed = true;
        invalidate();
        if (ownsScheduler) scheduler.shutdownNow();
    }
}
