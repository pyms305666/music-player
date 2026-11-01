package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ThrottledDelivery 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：界面更新节流器。合并快速产生的进度快照，控制定时器与已排队 UI
 * 回调，最终结果和关闭有明确处理。 Runs timer and UI queues explicitly; no sleeps or wall-clock performance
 * assertions.
 */
class ThrottledDeliveryTest {
    /** 验证调度器停止后仍可完成或取消在途任务。 */
    @Test
    void stoppedSchedulerDoesNotInterruptTaskCompletionOrCancellation() {
        var shown = new ArrayList<String>();
        var scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.shutdownNow();
        try (var delivery = new ThrottledDelivery<String>(scheduler, Runnable::run, shown::add)) {
            assertDoesNotThrow(() -> delivery.finish("cancelled"));
            assertDoesNotThrow(() -> delivery.offer("late"));
            assertTrue(shown.isEmpty());
        }
    }

    /** 验证 UI 停止后丢弃待投递进度并停止重新调度。 */
    @Test
    void stoppedUiDiscardsPendingProgressAndPreventsRescheduling() {
        try (var scheduler = new Scheduler();
                var delivery =
                        new ThrottledDelivery<String>(
                                scheduler,
                                work -> {
                                    throw new java.util.concurrent.RejectedExecutionException(
                                            "UI closed");
                                },
                                value -> fail("Closed UI must not receive progress"))) {
            delivery.offer("pending");
            scheduler.tick();
            assertDoesNotThrow(() -> delivery.finish("terminal"));
            assertTrue(scheduler.timers.isEmpty());
        }
    }

    /** 模拟定时任务，测试可以主动触发而无需等待真实时间。 */
    private static final class Timer extends FutureTask<Void> implements ScheduledFuture<Void> {
        final long delay;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        Timer(Runnable work, long delay) {
            super(work, null);
            this.delay = delay;
        }

        /** 实现可控调度器中 ScheduledFuture 的延迟与顺序约定。 */
        public long getDelay(TimeUnit unit) {
            return unit.convert(delay, TimeUnit.NANOSECONDS);
        }

        /** 实现可控调度器中 ScheduledFuture 的延迟与顺序约定。 */
        public int compareTo(Delayed other) {
            return Long.compare(delay, other.getDelay(TimeUnit.NANOSECONDS));
        }
    }

    /** 模拟调度器，记录任务与延迟，供节流投递测试控制触发顺序。 */
    private static final class Scheduler extends ScheduledThreadPoolExecutor
            implements AutoCloseable {
        final ArrayDeque<Timer> timers = new ArrayDeque<>();

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        Scheduler() {
            super(1);
        }

        /** 在可控调度器中记录或执行定时任务，测试无需等待真实时钟。 */
        @Override
        public ScheduledFuture<?> schedule(Runnable work, long delay, TimeUnit unit) {
            Timer timer = new Timer(work, unit.toNanos(delay));
            timers.add(timer);
            return timer;
        }

        /** 在可控调度器中记录或执行定时任务，测试无需等待真实时钟。 */
        void tick() {
            timers.remove().run();
        }

        /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
        @Override
        public void close() {
            shutdownNow();
        }
    }

    /** 验证合并计时器及已排队 UI 更新，并从实际投递时间计算下次等待。 */
    @Test
    void coalescesTimerAndQueuedUiUpdatesAndSchedulesFromActualDeliveryTime() {
        var ui = new ArrayDeque<Runnable>();
        var shown = new ArrayList<String>();
        var clock = new AtomicLong();
        try (var scheduler = new Scheduler();
                var delivery =
                        new ThrottledDelivery<String>(scheduler, ui::add, shown::add, clock::get)) {
            delivery.offer("first");
            assertEquals(0, scheduler.timers.getFirst().delay);
            for (int i = 0; i < 1000; i++) delivery.offer("timer" + i);
            assertEquals(1, scheduler.timers.size());
            scheduler.tick();
            for (int i = 0; i < 1000; i++) delivery.offer("ui" + i);
            assertTrue(scheduler.timers.isEmpty());
            assertEquals(1, ui.size());
            assertTrue(shown.isEmpty());
            clock.set(TimeUnit.SECONDS.toNanos(1));
            ui.remove().run();
            assertEquals(java.util.List.of("ui999"), shown);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(30));
            delivery.offer("next");
            assertEquals(TimeUnit.MILLISECONDS.toNanos(70), scheduler.timers.getFirst().delay);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(70));
            scheduler.tick();
            ui.remove().run();
            assertEquals(java.util.List.of("ui999", "next"), shown);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100));
            delivery.offer("ready");
            assertEquals(0, scheduler.timers.getFirst().delay);
        }
    }

    /** 验证最终结果替换待显示进度并阻止后续更新。 */
    @Test
    void finishReplacesPendingValueAndPreventsAdditionalUpdates() {
        var ui = new ArrayDeque<Runnable>();
        var shown = new ArrayList<String>();
        try (var scheduler = new Scheduler();
                var delivery =
                        new ThrottledDelivery<String>(scheduler, ui::add, shown::add, () -> 0L)) {
            delivery.offer("intermediate");
            scheduler.tick();
            delivery.finish("terminal");
            delivery.offer("too late");
            delivery.finish("another terminal");
            assertEquals(1, ui.size());
            assertTrue(scheduler.timers.isEmpty());
            ui.remove().run();
            assertEquals(java.util.List.of("terminal"), shown);
            delivery.offer("closed");
            assertTrue(scheduler.timers.isEmpty());
        }
    }

    /** 验证关闭取消计时器，已经排队的旧 UI 回调也不执行。 */
    @Test
    void closeCancelsTimerAndSuppressesAlreadyQueuedUiCallback() {
        var ui = new ArrayDeque<Runnable>();
        var shown = new ArrayList<String>();
        try (var scheduler = new Scheduler()) {
            var beforeTimer =
                    new ThrottledDelivery<String>(scheduler, ui::add, shown::add, () -> 0L);
            beforeTimer.offer("cancel timer");
            Timer pending = scheduler.timers.getFirst();
            beforeTimer.close();
            assertTrue(pending.isCancelled());
            scheduler.tick();
            assertTrue(ui.isEmpty());
            var beforeUi = new ThrottledDelivery<String>(scheduler, ui::add, shown::add, () -> 0L);
            beforeUi.offer("cancel callback");
            scheduler.tick();
            assertEquals(1, ui.size());
            beforeUi.close();
            ui.remove().run();
            assertTrue(shown.isEmpty());
        }
    }
}
