package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Runs timer and UI queues explicitly; no sleeps or wall-clock performance assertions. */
class ThrottledDeliveryTest {
    @Test void stoppedSchedulerDoesNotInterruptTaskCompletionOrCancellation() {
        var shown = new ArrayList<String>();
        var scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.shutdownNow();
        try (var delivery = new ThrottledDelivery<String>(scheduler, Runnable::run, shown::add)) {
            assertDoesNotThrow(() -> delivery.finish("cancelled"));
            assertDoesNotThrow(() -> delivery.offer("late"));
            assertTrue(shown.isEmpty());
        }
    }

    @Test void stoppedUiDiscardsPendingProgressAndPreventsRescheduling() {
        try (var scheduler = new Scheduler();
             var delivery = new ThrottledDelivery<String>(scheduler, work -> {
                 throw new java.util.concurrent.RejectedExecutionException("UI closed");
             }, value -> fail("Closed UI must not receive progress"))) {
            delivery.offer("pending"); scheduler.tick();
            assertDoesNotThrow(() -> delivery.finish("terminal"));
            assertTrue(scheduler.timers.isEmpty());
        }
    }
    private static final class Timer extends FutureTask<Void> implements ScheduledFuture<Void> {
        final long delay;
        Timer(Runnable work, long delay) { super(work, null); this.delay = delay; }
        public long getDelay(TimeUnit unit) { return unit.convert(delay, TimeUnit.NANOSECONDS); }
        public int compareTo(Delayed other) { return Long.compare(delay, other.getDelay(TimeUnit.NANOSECONDS)); }
    }
    private static final class Scheduler extends ScheduledThreadPoolExecutor implements AutoCloseable {
        final ArrayDeque<Timer> timers = new ArrayDeque<>();
        Scheduler() { super(1); }
        @Override public ScheduledFuture<?> schedule(Runnable work, long delay, TimeUnit unit) {
            Timer timer = new Timer(work, unit.toNanos(delay)); timers.add(timer); return timer;
        }
        void tick() { timers.remove().run(); }
        @Override public void close() { shutdownNow(); }
    }

    @Test void coalescesTimerAndQueuedUiUpdatesAndSchedulesFromActualDeliveryTime() {
        var ui = new ArrayDeque<Runnable>(); var shown = new ArrayList<String>(); var clock = new AtomicLong();
        try (var scheduler = new Scheduler();
             var delivery = new ThrottledDelivery<String>(scheduler, ui::add, shown::add, clock::get)) {
            delivery.offer("first");
            assertEquals(0, scheduler.timers.getFirst().delay);
            for (int i = 0; i < 1000; i++) delivery.offer("timer" + i);
            assertEquals(1, scheduler.timers.size());
            scheduler.tick();
            for (int i = 0; i < 1000; i++) delivery.offer("ui" + i);
            assertTrue(scheduler.timers.isEmpty()); assertEquals(1, ui.size()); assertTrue(shown.isEmpty());
            clock.set(TimeUnit.SECONDS.toNanos(1));
            ui.remove().run(); assertEquals(java.util.List.of("ui999"), shown);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(30));
            delivery.offer("next");
            assertEquals(TimeUnit.MILLISECONDS.toNanos(70), scheduler.timers.getFirst().delay);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(70)); scheduler.tick(); ui.remove().run();
            assertEquals(java.util.List.of("ui999", "next"), shown);
            clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(100)); delivery.offer("ready");
            assertEquals(0, scheduler.timers.getFirst().delay);
        }
    }

    @Test void finishReplacesPendingValueAndPreventsAdditionalUpdates() {
        var ui = new ArrayDeque<Runnable>(); var shown = new ArrayList<String>();
        try (var scheduler = new Scheduler();
             var delivery = new ThrottledDelivery<String>(scheduler, ui::add, shown::add, () -> 0L)) {
            delivery.offer("intermediate"); scheduler.tick();
            delivery.finish("terminal"); delivery.offer("too late"); delivery.finish("another terminal");
            assertEquals(1, ui.size()); assertTrue(scheduler.timers.isEmpty());
            ui.remove().run(); assertEquals(java.util.List.of("terminal"), shown);
            delivery.offer("closed"); assertTrue(scheduler.timers.isEmpty());
        }
    }

    @Test void closeCancelsTimerAndSuppressesAlreadyQueuedUiCallback() {
        var ui = new ArrayDeque<Runnable>(); var shown = new ArrayList<String>();
        try (var scheduler = new Scheduler()) {
            var beforeTimer = new ThrottledDelivery<String>(scheduler, ui::add, shown::add, () -> 0L);
            beforeTimer.offer("cancel timer"); Timer pending = scheduler.timers.getFirst(); beforeTimer.close();
            assertTrue(pending.isCancelled()); scheduler.tick(); assertTrue(ui.isEmpty());
            var beforeUi = new ThrottledDelivery<String>(scheduler, ui::add, shown::add, () -> 0L);
            beforeUi.offer("cancel callback"); scheduler.tick(); assertEquals(1, ui.size());
            beforeUi.close(); ui.remove().run(); assertTrue(shown.isEmpty());
        }
    }
}
