package app.musicplayer.lyrics;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.Track;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** 用可控 Future、界面队列和计时器验证歌词轮次，测试不等待真实重试间隔。 */
class DesktopLyricsControllerTest {
    /** 旧查询已经排入 UI 队列，仍不能覆盖切歌后取得的新结果。 */
    @Test
    void queuedOldResultAndClosedCallbacksAreDiscarded() {
        Track first = new Track(Path.of("first.mp3"));
        Track second = new Track(Path.of("second.mp3"));
        AtomicReference<Track> current = new AtomicReference<>(first);
        List<CompletableFuture<LyricsLookupResult>> requests = new ArrayList<>();
        ArrayDeque<Runnable> ui = new ArrayDeque<>();
        List<String> displayed = new ArrayList<>();
        RecordingScheduler scheduler = new RecordingScheduler();
        try (var controller =
                new DesktopLyricsController(
                        (track, duration, manual) -> {
                            var request = new CompletableFuture<LyricsLookupResult>();
                            requests.add(request);
                            return request;
                        },
                        ui::add,
                        current::get,
                        callbacks(displayed),
                        scheduler,
                        false)) {
            controller.load(first, null, false);
            requests.get(0).complete(result("old"));
            current.set(second);
            controller.load(second, null, false);
            requests.get(1).complete(result("new"));
            while (!ui.isEmpty()) ui.remove().run();
            assertEquals(List.of("new"), displayed);
            controller.load(second, null, true);
            requests.get(2).complete(result("after-close"));
            controller.close();
            while (!ui.isEmpty()) ui.remove().run();
            assertEquals(List.of("new"), displayed);
            assertFalse(scheduler.isShutdown(), "借用的调度器由调用者关闭");
        } finally {
            scheduler.shutdownNow();
        }
    }

    /** 未找到歌词时沿用 10、20、30、60 秒退避，切换请求后取消已安排的计时器。 */
    @Test
    void retryDelaysAndCancellationPreserveExistingPolicy() {
        Track track = new Track(Path.of("song.mp3"));
        RecordingScheduler scheduler = new RecordingScheduler();
        List<Boolean> manualFlags = new ArrayList<>();
        try (var controller =
                new DesktopLyricsController(
                        (song, duration, manual) -> {
                            manualFlags.add(manual);
                            return CompletableFuture.completedFuture(result("暂无歌词"));
                        },
                        Runnable::run,
                        () -> track,
                        callbacks(new ArrayList<>()),
                        scheduler,
                        false)) {
            controller.load(track, null, false);
            for (int index = 0; index < 4; index++) scheduler.tasks.get(index).action.run();
            assertEquals(List.of(10L, 20L, 30L, 60L, 60L), scheduler.delays);
            assertEquals(List.of(false, true, true, true, true), manualFlags);
            controller.invalidate();
            assertTrue(scheduler.tasks.get(4).cancelled);
            scheduler.tasks.get(4).action.run();
            assertEquals(5, manualFlags.size());
        } finally {
            scheduler.shutdownNow();
        }
    }

    /** 初次查询失败仅更新失败歌词，不把失败伪装成成功封面结果。 */
    @Test
    void errorsUseFailureCallbackAndOwnedSchedulerStopsOnClose() {
        Track track = new Track(Path.of("song.mp3"));
        RecordingScheduler scheduler = new RecordingScheduler();
        List<String> success = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        var callbacks =
                new DesktopLyricsController.Callbacks(
                        ignored -> {},
                        ignored -> {},
                        value -> success.add(value.lyrics().source()),
                        lyrics -> failures.add(lyrics.source()));
        var controller =
                new DesktopLyricsController(
                        (song, duration, manual) ->
                                CompletableFuture.failedFuture(
                                        new IllegalStateException("offline")),
                        Runnable::run,
                        () -> track,
                        callbacks,
                        scheduler,
                        true);
        controller.load(track, null, true);
        assertTrue(success.isEmpty());
        assertEquals(List.of("歌词加载失败，稍后继续搜索"), failures);
        controller.close();
        controller.close();
        assertTrue(scheduler.isShutdown());
        assertTrue(scheduler.tasks.get(0).cancelled);
    }

    /** 构造成功查词结果；来源文字也用作本测试的结果身份。 */
    private static LyricsLookupResult result(String source) {
        return LyricsLookupResult.lyricsOnly(Lyrics.empty(source));
    }

    /** 收集显示来源，忽略本用例不关心的控件外观。 */
    private static DesktopLyricsController.Callbacks callbacks(List<String> displayed) {
        return new DesktopLyricsController.Callbacks(
                ignored -> {},
                ignored -> {},
                value -> displayed.add(value.lyrics().source()),
                ignored -> {});
    }

    /** 记录调度动作，调用者手动推进；不会创建等待真实时钟的工作线程。 */
    private static final class RecordingScheduler extends ScheduledThreadPoolExecutor {
        final List<FakeTask> tasks = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();

        /** 只初始化执行器外壳；schedule 被本夹具覆盖。 */
        RecordingScheduler() {
            super(1);
        }

        /** 保存动作及以秒表示的延迟，返回可检查取消的句柄。 */
        @Override
        public ScheduledFuture<?> schedule(Runnable action, long delay, TimeUnit unit) {
            FakeTask task = new FakeTask(action);
            tasks.add(task);
            delays.add(unit.toSeconds(delay));
            return task;
        }
    }

    /** 最小可控定时句柄，测试只使用取消状态与所保存动作。 */
    private static final class FakeTask implements ScheduledFuture<Object> {
        final Runnable action;
        boolean cancelled;

        /** 保存待执行动作。 */
        FakeTask(Runnable action) {
            this.action = action;
        }

        /** 记录取消，并允许重复取消。 */
        @Override
        public boolean cancel(boolean interrupt) {
            cancelled = true;
            return true;
        }

        /** 返回取消标记。 */
        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        /** 本夹具把取消视为完成。 */
        @Override
        public boolean isDone() {
            return cancelled;
        }

        /** 不等待实际时间，调度顺序由测试列表决定。 */
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        /** 本夹具不依赖执行器的优先队列排序。 */
        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        /** 不提供真实结果等待。 */
        @Override
        public Object get() {
            throw new UnsupportedOperationException();
        }

        /** 不提供真实结果等待。 */
        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
