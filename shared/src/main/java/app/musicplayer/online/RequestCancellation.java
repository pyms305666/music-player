package app.musicplayer.online;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

/** A task owns its connections and subprocesses; cancelling it never closes the shared session. */
public final class RequestCancellation implements AutoCloseable {
    @FunctionalInterface public interface Registration extends AutoCloseable {
        @Override void close();
    }
    private final Set<Runnable> listeners = new HashSet<>();
    private boolean cancelled;

    public synchronized boolean isCancelled() { return cancelled; }
    public void check() {
        if (isCancelled() || Thread.currentThread().isInterrupted()) throw new CancellationException("Task cancelled");
    }
    public Registration onCancel(Runnable listener) {
        synchronized (this) {
            if (!cancelled) {
                listeners.add(listener);
                return () -> { synchronized (RequestCancellation.this) { listeners.remove(listener); } };
            }
        }
        listener.run();
        return () -> { };
    }
    @Override public void close() {
        Set<Runnable> captured;
        synchronized (this) {
            if (cancelled) return;
            cancelled = true;
            captured = Set.copyOf(listeners);
            listeners.clear();
        }
        captured.forEach(listener -> {
            try { listener.run(); } catch (RuntimeException ignored) { }
        });
    }
}
