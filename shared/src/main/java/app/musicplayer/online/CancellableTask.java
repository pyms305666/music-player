package app.musicplayer.online;

import java.util.concurrent.CompletableFuture;

/**
 * 带取消操作的异步任务句柄。把 CompletableFuture 和取消入口组合，供界面和搜索协调器管理单次任务。 Explicit task handle. Closing a handle
 * cancels only the work it represents.
 */
public final class CancellableTask<T> implements AutoCloseable {
    private final CompletableFuture<T> result;
    private final Runnable cancel;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    CancellableTask(CompletableFuture<T> result, Runnable cancel) {
        this.result = result;
        this.cancel = cancel;
    }

    /** 返回此任务或句柄关联的结果，供调用者等待或使用。 */
    public CompletableFuture<T> result() {
        return result;
    }

    /** 取消指定任务或当前轮次，并通知相关工作停止。 */
    public void cancel() {
        cancel.run();
    }

    /** 通过取消入口结束此任务，具体网络清理由任务所属组件处理。 */
    @Override
    public void close() {
        cancel();
    }
}
