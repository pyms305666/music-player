package app.musicplayer.online;

import app.musicplayer.util.LatestRequest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owns search coordination and UI publication; provider requests use the crawler's separate pool. */
final class SearchCoordinator implements AutoCloseable {
    private final MusicCrawler crawler;
    private final SearchResultCache cache;
    private final LatestRequest<OnlineSearchSnapshot> latest = new LatestRequest<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "online-search-coordinator"); thread.setDaemon(true); return thread;
    });
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r, "online-search-delivery"); thread.setDaemon(true); return thread;
    });
    private Cycle current;
    private boolean closed;
    private static final class Cycle {
        final RequestCancellation cancellation = new RequestCancellation();
        final ThrottledDelivery<OnlineSearchSnapshot> delivery;
        OnlineSearchSnapshot snapshot;
        Cycle(ThrottledDelivery<OnlineSearchSnapshot> delivery, String query) {
            this.delivery = delivery;
            snapshot = new OnlineSearchSnapshot(query, List.of(), List.of(), OnlineSearchSnapshot.State.SEARCHING, false);
        }
    }
    SearchCoordinator(MusicCrawler crawler, LongSupplier clock) {
        this.crawler = crawler; cache = new SearchResultCache(clock);
        scheduler.setRemoveOnCancelPolicy(true);
    }
    synchronized CancellableTask<OnlineSearchSnapshot> search(String query, Executor ui,
                                                              Consumer<OnlineSearchSnapshot> progress) {
        if (closed) throw new IllegalStateException("Search coordinator is closed");
        if (current != null) { current.cancellation.close(); current.delivery.close(); }
        latest.close();
        Cycle cycle = new Cycle(new ThrottledDelivery<>(scheduler, ui, progress), query);
        current = cycle;
        OnlineSearchSnapshot cached = cache.get(query);
        CompletableFuture<OnlineSearchSnapshot> result;
        if (cached != null) {
            cycle.snapshot = cached;
            cycle.delivery.offer(cached);
            result = CompletableFuture.completedFuture(cached);
        } else {
            result = latest.submit(worker, () -> {
                OnlineSearchSnapshot complete = crawler.searchIncrementally(query, snapshot -> {
                    synchronized (SearchCoordinator.this) {
                        if (closed || current != cycle || cycle.cancellation.isCancelled()) return;
                        cycle.snapshot = snapshot;
                        cycle.delivery.offer(snapshot);
                    }
                }, cycle.cancellation);
                if (!cycle.cancellation.isCancelled()) cache.put(complete);
                return complete;
            });
        }
        return new CancellableTask<>(result, () -> cancel(cycle));
    }
    private synchronized void cancel(Cycle cycle) {
        if (closed || current != cycle || cycle.snapshot.finished()) return;
        cycle.cancellation.close();
        latest.close();
        OnlineSearchSnapshot old = cycle.snapshot;
        var sources = old.sources().stream().map(source -> source.outcome() != OnlineSearchSnapshot.Outcome.PENDING
                ? source : new OnlineSearchSnapshot.Source(source.name(), OnlineSearchSnapshot.Outcome.CANCELLED)).toList();
        cycle.snapshot = new OnlineSearchSnapshot(old.query(), old.tracks(), sources, OnlineSearchSnapshot.State.CANCELLED, false);
        cycle.delivery.offer(cycle.snapshot);
    }
    @Override public synchronized void close() {
        closed = true;
        if (current != null) { current.cancellation.close(); current.delivery.close(); }
        latest.close(); worker.shutdownNow(); scheduler.shutdownNow(); cache.close();
    }
}
