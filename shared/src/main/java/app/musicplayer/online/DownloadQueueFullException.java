package app.musicplayer.online;

import java.util.concurrent.RejectedExecutionException;

/** 下载队列容量已满的异常。让调用者显示可理解的任务拥塞提示，而不是无限堆积等待任务。 */
public final class DownloadQueueFullException extends RejectedExecutionException {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public DownloadQueueFullException() {
        super("Download queue is full");
    }
}
