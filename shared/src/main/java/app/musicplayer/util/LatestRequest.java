package app.musicplayer.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/** Cancels obsolete work as well as its result, leaving download tasks independent. */
public final class LatestRequest<T> implements AutoCloseable {
    private Future<?> task;
    private CompletableFuture<T> result;

    public synchronized CompletableFuture<T> submit(ExecutorService executor, Supplier<T> work) {
        close();
        CompletableFuture<T> next = new CompletableFuture<>();
        result = next;
        task = executor.submit(() -> {
            try { next.complete(work.get()); }
            catch (Throwable error) { next.completeExceptionally(error); }
        });
        return next;
    }

    @Override public synchronized void close() {
        Future<?> previousTask = task;
        CompletableFuture<T> previousResult = result;
        task = null;
        result = null;
        // Mark the result obsolete before interrupting a worker that may finish immediately.
        if (previousResult != null) previousResult.cancel(false);
        if (previousTask != null) previousTask.cancel(true);
    }
}
