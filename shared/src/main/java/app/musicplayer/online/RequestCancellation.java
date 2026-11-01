package app.musicplayer.online;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * 请求取消令牌。标记取消状态，执行已注册的断开连接等回调，检查点可立即终止仍在运行的任务。 A task owns its connections and subprocesses;
 * cancelling it never closes the shared session.
 */
public final class RequestCancellation implements AutoCloseable {
    /** 持有一个取消监听注册，关闭时撤销该监听。 */
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        /** 标记请求取消并执行已注册清理，重复取消保持安全。 */
        @Override
        void close();
    }

    private final Set<Runnable> listeners = new HashSet<>();
    private boolean cancelled;

    /** 检查任务或取消令牌是否已经被取消。 */
    public synchronized boolean isCancelled() {
        return cancelled;
    }

    /** 创建并配置当前界面使用的按钮、输入或选择控件。 */
    public void check() {
        if (isCancelled() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Task cancelled");
    }

    /** 注册取消清理操作，返回可用于撤销注册的句柄。 */
    public Registration onCancel(Runnable listener) {
        synchronized (this) {
            if (!cancelled) {
                listeners.add(listener);
                return () -> {
                    synchronized (RequestCancellation.this) {
                        listeners.remove(listener);
                    }
                };
            }
        }
        listener.run();
        return () -> {};
    }

    /** 标记请求取消并执行已注册清理，重复取消保持安全。 */
    @Override
    public void close() {
        Set<Runnable> captured;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            captured = Set.copyOf(listeners);
            listeners.clear();
        }
        captured.forEach(
                listener -> {
                    try {
                        listener.run();
                    } catch (RuntimeException ignored) {
                    }
                });
    }
}
