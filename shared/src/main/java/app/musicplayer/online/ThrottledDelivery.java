package app.musicplayer.online;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Keeps only the latest snapshot while an update is waiting for the UI. */
final class ThrottledDelivery<T> implements AutoCloseable {
    static final long INTERVAL_MILLIS = 100;
    private final ScheduledExecutorService scheduler;
    private final Executor ui;
    private final Consumer<T> consumer;
    private final java.util.function.LongSupplier clock;
    private T pending;
    private ScheduledFuture<?> scheduled;
    private long lastDelivered;
    private boolean closed;
    private boolean finishing;
    private boolean delivered;

    ThrottledDelivery(ScheduledExecutorService scheduler, Executor ui, Consumer<T> consumer) {
        this(scheduler, ui, consumer, System::nanoTime);
    }
    ThrottledDelivery(ScheduledExecutorService scheduler, Executor ui, Consumer<T> consumer,
                      java.util.function.LongSupplier clock) {
        this.scheduler = scheduler;
        this.ui = ui;
        this.consumer = consumer;
        this.clock = clock;
    }
    synchronized void offer(T value) {
        if (closed || finishing) return;
        pending = value;
        if (scheduled != null) return;
        long delay = !delivered ? 0 : Math.max(0, TimeUnit.MILLISECONDS.toNanos(INTERVAL_MILLIS)
                - (clock.getAsLong() - lastDelivered));
        try { scheduled = scheduler.schedule(this::dispatch, delay, TimeUnit.NANOSECONDS); }
        catch (RejectedExecutionException stopped) { close(); }
    }
    synchronized void finish(T value) {
        if (closed || finishing) return;
        offer(value);
        finishing = true;
    }
    private void dispatch() {
        try { ui.execute(this::deliver); }
        catch (RejectedExecutionException stopped) { close(); }
    }
    private void deliver() {
        T value;
        synchronized (this) {
            scheduled = null;
            if (closed || pending == null) return;
            value = pending;
            pending = null;
            lastDelivered = clock.getAsLong();
            delivered = true;
            if (finishing) closed = true;
        }
        consumer.accept(value);
    }
    @Override public synchronized void close() {
        closed = true;
        pending = null;
        if (scheduled != null) scheduled.cancel(false);
        scheduled = null;
    }
}
