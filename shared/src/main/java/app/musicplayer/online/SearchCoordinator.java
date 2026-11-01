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

/**
 * 增量搜索与界面发布协调。管理当前搜索、结果缓存和节流投递，取消旧轮次，来源网络请求仍由爬虫线程池执行。
 *
 * <p>Owns search coordination and UI publication; provider requests use the crawler's separate
 * pool.
 */
final class SearchCoordinator implements AutoCloseable {
    private final MusicCrawler crawler;
    private final SearchResultCache cache;
    private final LatestRequest<OnlineSearchSnapshot> latest = new LatestRequest<>();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread thread = new Thread(r, "online-search-coordinator");
                        thread.setDaemon(true);
                        return thread;
                    });
    private final ScheduledThreadPoolExecutor scheduler =
            new ScheduledThreadPoolExecutor(
                    1,
                    r -> {
                        Thread thread = new Thread(r, "online-search-delivery");
                        thread.setDaemon(true);
                        return thread;
                    });
    private Cycle current;
    private boolean closed;

    /** 保存单次搜索的取消、快照及投递状态。 */
    private static final class Cycle {
        final RequestCancellation cancellation = new RequestCancellation();
        final ThrottledDelivery<OnlineSearchSnapshot> delivery;
        OnlineSearchSnapshot snapshot;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        Cycle(ThrottledDelivery<OnlineSearchSnapshot> delivery, String query) {
            this.delivery = delivery;
            snapshot =
                    new OnlineSearchSnapshot(
                            query,
                            List.of(),
                            List.of(),
                            OnlineSearchSnapshot.State.SEARCHING,
                            false);
        }
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    SearchCoordinator(MusicCrawler crawler, LongSupplier clock) {
        this.crawler = crawler;
        cache = new SearchResultCache(clock);
        scheduler.setRemoveOnCancelPolicy(true);
    }

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    synchronized CancellableTask<OnlineSearchSnapshot> search(
            String query, Executor ui, Consumer<OnlineSearchSnapshot> progress) {
        if (closed) throw new IllegalStateException("Search coordinator is closed");
        if (current != null) {
            current.cancellation.close();
            current.delivery.close();
        }
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
            result =
                    latest.submit(
                            worker,
                            () -> {
                                OnlineSearchSnapshot complete =
                                        crawler.searchIncrementally(
                                                query,
                                                snapshot -> {
                                                    synchronized (SearchCoordinator.this) {
                                                        if (closed
                                                                || current != cycle
                                                                || cycle.cancellation.isCancelled())
                                                            return;
                                                        cycle.snapshot = snapshot;
                                                        cycle.delivery.offer(snapshot);
                                                    }
                                                },
                                                cycle.cancellation);
                                if (!cycle.cancellation.isCancelled()) cache.put(complete);
                                return complete;
                            });
        }
        return new CancellableTask<>(result, () -> cancel(cycle));
    }

    /** 取消指定任务或当前轮次，并通知相关工作停止。 */
    private synchronized void cancel(Cycle cycle) {
        if (closed || current != cycle || cycle.snapshot.finished()) return;
        cycle.cancellation.close();
        latest.close();
        OnlineSearchSnapshot old = cycle.snapshot;
        var sources =
                old.sources().stream()
                        .map(
                                source ->
                                        source.outcome() != OnlineSearchSnapshot.Outcome.PENDING
                                                ? source
                                                : new OnlineSearchSnapshot.Source(
                                                        source.name(),
                                                        OnlineSearchSnapshot.Outcome.CANCELLED))
                        .toList();
        cycle.snapshot =
                new OnlineSearchSnapshot(
                        old.query(),
                        old.tracks(),
                        sources,
                        OnlineSearchSnapshot.State.CANCELLED,
                        false);
        cycle.delivery.offer(cycle.snapshot);
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public synchronized void close() {
        closed = true;
        if (current != null) {
            current.cancellation.close();
            current.delivery.close();
        }
        latest.close();
        worker.shutdownNow();
        scheduler.shutdownNow();
        cache.close();
    }
}
