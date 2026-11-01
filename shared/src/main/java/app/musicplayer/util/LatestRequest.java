package app.musicplayer.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * 通用最新请求协调器。新请求取消前一次工作，过期结果不得替换最新结果，关闭时终止当前任务。 Cancels obsolete work as well as its result, leaving
 * download tasks independent.
 */
public final class LatestRequest<T> implements AutoCloseable {
    private Future<?> task;
    private CompletableFuture<T> result;

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    public synchronized CompletableFuture<T> submit(ExecutorService executor, Supplier<T> work) {
        close();
        CompletableFuture<T> next = new CompletableFuture<>();
        result = next;
        task =
                executor.submit(
                        () -> {
                            try {
                                next.complete(work.get());
                            } catch (Throwable error) {
                                next.completeExceptionally(error);
                            }
                        });
        return next;
    }

    /** 取消当前工作并使旧结果失效；传入的执行器仍归调用者所有。 */
    @Override
    public synchronized void close() {
        Future<?> previousTask = task;
        CompletableFuture<T> previousResult = result;
        task = null;
        result = null;
        // Mark the result obsolete before interrupting a worker that may finish immediately.
        if (previousResult != null) previousResult.cancel(false);
        if (previousTask != null) previousTask.cancel(true);
    }
}
