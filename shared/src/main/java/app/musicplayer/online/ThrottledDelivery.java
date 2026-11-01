package app.musicplayer.online;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 界面更新节流器。合并快速产生的进度快照，控制定时器与已排队 UI 回调，最终结果和关闭有明确处理。 Keeps only the latest snapshot while an update
 * is waiting for the UI.
 */
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

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    ThrottledDelivery(ScheduledExecutorService scheduler, Executor ui, Consumer<T> consumer) {
        this(scheduler, ui, consumer, System::nanoTime);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    ThrottledDelivery(
            ScheduledExecutorService scheduler,
            Executor ui,
            Consumer<T> consumer,
            java.util.function.LongSupplier clock) {
        this.scheduler = scheduler;
        this.ui = ui;
        this.consumer = consumer;
        this.clock = clock;
    }

    /** 提供最新待投递快照，合并尚未显示的旧进度。 */
    synchronized void offer(T value) {
        if (closed || finishing) return;
        pending = value;
        if (scheduled != null) return;
        long delay =
                !delivered
                        ? 0
                        : Math.max(
                                0,
                                TimeUnit.MILLISECONDS.toNanos(INTERVAL_MILLIS)
                                        - (clock.getAsLong() - lastDelivered));
        try {
            scheduled = scheduler.schedule(this::dispatch, delay, TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException stopped) {
            close();
        }
    }

    /** 提供最终结果并结束后续进度调度。 */
    synchronized void finish(T value) {
        if (closed || finishing) return;
        offer(value);
        finishing = true;
    }

    /** 安排一次界面投递，避免重复排队相同更新。 */
    private void dispatch() {
        try {
            ui.execute(this::deliver);
        } catch (RejectedExecutionException stopped) {
            close();
        }
    }

    /** 在指定界面执行器交付当前有效结果，丢弃过期轮次。 */
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

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public synchronized void close() {
        closed = true;
        pending = null;
        if (scheduled != null) scheduled.cancel(false);
        scheduled = null;
    }
}
