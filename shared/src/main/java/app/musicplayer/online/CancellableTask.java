package app.musicplayer.online;

import java.util.concurrent.CompletableFuture;

/** Explicit task handle. Closing a handle cancels only the work it represents. */
public final class CancellableTask<T> implements AutoCloseable {
    private final CompletableFuture<T> result;
    private final Runnable cancel;
    CancellableTask(CompletableFuture<T> result, Runnable cancel) {
        this.result = result;
        this.cancel = cancel;
    }
    public CompletableFuture<T> result() { return result; }
    public void cancel() { cancel.run(); }
    @Override public void close() { cancel(); }
}
