package app.musicplayer.playlist;

import app.musicplayer.util.LatestRequest;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Owns debounce, background filtering and delivery generations for either UI. */
public final class LocalSearch<T> implements AutoCloseable {
    public static final int DEBOUNCE_THRESHOLD = 1000;
    public static final int DEBOUNCE_MILLIS = 200;
    private final ScheduledThreadPoolExecutor worker;
    private final LatestRequest<List<T>> latest = new LatestRequest<>();
    private final Executor ui;
    private final Consumer<List<T>> display;
    private SearchSnapshot<T> snapshot = new SearchSnapshot<>(List.of());
    private ScheduledFuture<?> pending;
    private long generation;
    private boolean closed;

    public LocalSearch(Executor ui, Consumer<List<T>> display) {
        this.ui = ui;
        this.display = display;
        worker = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "local-search");
            thread.setDaemon(true);
            return thread;
        });
        worker.setRemoveOnCancelPolicy(true);
    }

    public synchronized void replace(SearchSnapshot<T> value, String query) {
        snapshot = value;
        search(query);
    }

    public synchronized void search(String query) {
        if (closed) return;
        long request = ++generation;
        if (pending != null) pending.cancel(false);
        latest.close();
        SearchSnapshot<T> captured = snapshot;
        if (query == null || query.isBlank()) {
            deliver(request, captured.values());
            return;
        }
        long delay = captured.values().size() >= DEBOUNCE_THRESHOLD ? DEBOUNCE_MILLIS : 0;
        pending = worker.schedule(() -> start(request, captured, query), delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void start(long request, SearchSnapshot<T> captured, String query) {
        if (closed || request != generation) return;
        latest.submit(worker, () -> captured.filter(query)).thenAccept(values -> deliver(request, values));
    }

    private void deliver(long request, List<T> values) {
        ui.execute(() -> {
            synchronized (LocalSearch.this) {
                if (closed || request != generation) return;
                display.accept(values);
            }
        });
    }

    @Override public synchronized void close() {
        closed = true;
        generation++;
        if (pending != null) pending.cancel(false);
        latest.close();
        worker.shutdownNow();
    }
}
