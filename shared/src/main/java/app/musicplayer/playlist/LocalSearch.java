package app.musicplayer.playlist;

import app.musicplayer.util.LatestRequest;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 共享异步本地搜索协调。捕获搜索快照，后台筛选，取消过期工作并将最新结果交给界面执行器。 Owns debounce, background filtering and delivery
 * generations for either UI.
 */
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

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public LocalSearch(Executor ui, Consumer<List<T>> display) {
        this.ui = ui;
        this.display = display;
        worker =
                new ScheduledThreadPoolExecutor(
                        1,
                        runnable -> {
                            Thread thread = new Thread(runnable, "local-search");
                            thread.setDaemon(true);
                            return thread;
                        });
        worker.setRemoveOnCancelPolicy(true);
    }

    /** 用新的不可变列表或控件替换旧数据，保留组件规定的选择规则。 */
    public synchronized void replace(SearchSnapshot<T> value, String query) {
        snapshot = value;
        search(query);
    }

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
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
        pending =
                worker.schedule(
                        () -> start(request, captured, query), delay, TimeUnit.MILLISECONDS);
    }

    /** 开始当前组件的工作，并接通所需状态与完成回调。 */
    private synchronized void start(long request, SearchSnapshot<T> captured, String query) {
        if (closed || request != generation) return;
        latest.submit(worker, () -> captured.filter(query))
                .thenAccept(values -> deliver(request, values));
    }

    /** 在指定界面执行器交付当前有效结果，丢弃过期轮次。 */
    private void deliver(long request, List<T> values) {
        ui.execute(
                () -> {
                    synchronized (LocalSearch.this) {
                        if (closed || request != generation) return;
                        display.accept(values);
                    }
                });
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public synchronized void close() {
        closed = true;
        generation++;
        if (pending != null) pending.cancel(false);
        latest.close();
        worker.shutdownNow();
    }
}
