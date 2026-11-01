package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 共享有界下载工作队列。限制同时运行和等待的任务数量，区分传输与发布，取消时释放资源并清理临时文件。
 *
 * <p>Two running transfers, eight waiting jobs; duplicates share work and have independent handles.
 */
final class DownloadQueue implements AutoCloseable {
    static final int PARALLEL_DOWNLOADS = 2;
    static final int WAITING_DOWNLOADS = 8;

    /** 约定下载队列调用的实际传输过程，接收取消范围及进度回调。 */
    @FunctionalInterface
    interface Transfer {
        /** 执行当前任务的实际工作；调度方决定所在线程。 */
        Path run(
                OnlineTrackInfo track,
                Path directory,
                RequestCancellation cancellation,
                Consumer<DownloadEvent> progress)
                throws Exception;
    }

    /** 捕获图片缓存身份和解码尺寸。 */
    private record Key(String track, Path directory) {}

    private final Transfer transfer;
    private final Map<Key, Job> jobs = new HashMap<>();
    private final ThreadPoolExecutor worker =
            new ThreadPoolExecutor(
                    PARALLEL_DOWNLOADS,
                    PARALLEL_DOWNLOADS,
                    0,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(WAITING_DOWNLOADS),
                    runnable -> {
                        Thread thread = new Thread(runnable, "online-download");
                        thread.setDaemon(true);
                        return thread;
                    });
    private final ScheduledThreadPoolExecutor scheduler =
            new ScheduledThreadPoolExecutor(
                    1,
                    runnable -> {
                        Thread thread = new Thread(runnable, "download-progress");
                        thread.setDaemon(true);
                        return thread;
                    });
    private boolean closed;

    /** 保存任务通知订阅，关闭时移除监听，避免界面销毁后仍被引用。 */
    private static final class Subscription {
        final CompletableFuture<Path> result = new CompletableFuture<>();
        final ThrottledDelivery<DownloadEvent> delivery;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        Subscription(ThrottledDelivery<DownloadEvent> delivery) {
            this.delivery = delivery;
        }
    }

    /** 保存一次下载的输入、进度、状态及完成结果。 */
    private static final class Job {
        final Key key;
        final OnlineTrackInfo track;
        final RequestCancellation cancellation = new RequestCancellation();
        final Set<Subscription> subscribers = new HashSet<>();
        Future<?> future;
        DownloadEvent event = DownloadEvent.of(DownloadEvent.Stage.QUEUED);
        boolean terminal;
        boolean cancelled;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        Job(Key key, OnlineTrackInfo track) {
            this.key = key;
            this.track = track;
        }
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    DownloadQueue(Transfer transfer) {
        this.transfer = transfer;
        scheduler.setRemoveOnCancelPolicy(true);
    }

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    synchronized CancellableTask<Path> submit(
            OnlineTrackInfo track, Path directory, Executor ui, Consumer<DownloadEvent> progress) {
        if (closed) throw new IllegalStateException("Download queue is closed");
        Key key = new Key(MusicCrawler.downloadKey(track), directory.toAbsolutePath().normalize());
        Job job = jobs.get(key);
        boolean created = job == null;
        if (created) {
            job = new Job(key, track);
            jobs.put(key, job);
        }
        Subscription subscription =
                new Subscription(new ThrottledDelivery<>(scheduler, ui, progress));
        job.subscribers.add(subscription);
        subscription.delivery.offer(job.event);
        if (created) {
            Job captured = job;
            try {
                job.future = worker.submit(() -> run(captured));
            } catch (RejectedExecutionException full) {
                jobs.remove(key, job);
                subscription.delivery.close();
                job.subscribers.clear();
                throw new DownloadQueueFullException();
            }
        }
        Job captured = job;
        return new CancellableTask<>(subscription.result, () -> cancel(captured, subscription));
    }

    /** 执行当前任务的实际工作；调度方决定所在线程。 */
    private void run(Job job) {
        Path path = null;
        Throwable error = null;
        try {
            job.cancellation.check();
            path =
                    transfer.run(
                            job.track,
                            job.key.directory(),
                            job.cancellation,
                            event -> publish(job, event));
            job.cancellation.check();
        } catch (Throwable failed) {
            error = failed;
        }
        List<Subscription> subscribers;
        boolean cancelled;
        synchronized (this) {
            cancelled = job.cancelled || job.cancellation.isCancelled();
            job.terminal = true;
            jobs.remove(job.key, job);
            subscribers = List.copyOf(job.subscribers);
            job.subscribers.clear();
        }
        if (cancelled && path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (java.io.IOException cleanup) {
                error = cleanup;
            }
        }
        DownloadEvent.Stage stage =
                cancelled
                        ? DownloadEvent.Stage.CANCELLED
                        : error == null ? DownloadEvent.Stage.COMPLETE : DownloadEvent.Stage.FAILED;
        DownloadEvent terminal =
                new DownloadEvent(stage, job.event.transferredBytes(), job.event.totalBytes());
        for (Subscription subscriber : subscribers) {
            subscriber.delivery.finish(terminal);
            if (cancelled) subscriber.result.cancel(false);
            else if (error == null) subscriber.result.complete(path);
            else subscriber.result.completeExceptionally(error);
        }
    }

    /** 将准备好的数据发布到目标位置，并把结果交给后续处理。 */
    private synchronized void publish(Job job, DownloadEvent event) {
        if (closed || job.terminal || job.cancellation.isCancelled()) return;
        job.event = event;
        job.subscribers.forEach(subscriber -> subscriber.delivery.offer(event));
    }

    /** 取消指定任务或当前轮次，并通知相关工作停止。 */
    private void cancel(Job job, Subscription subscriber) {
        boolean last;
        DownloadEvent event;
        synchronized (this) {
            if (job.terminal || !job.subscribers.remove(subscriber)) return;
            event =
                    new DownloadEvent(
                            DownloadEvent.Stage.CANCELLED,
                            job.event.transferredBytes(),
                            job.event.totalBytes());
            last = job.subscribers.isEmpty();
            if (last) {
                job.cancelled = true;
                job.terminal = true;
                jobs.remove(job.key, job);
            }
        }
        subscriber.delivery.finish(event);
        subscriber.result.cancel(false);
        if (last) {
            job.cancellation.close();
            if (job.future != null) {
                job.future.cancel(true);
                worker.remove((Runnable) job.future);
            }
        }
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        List<Job> captured;
        List<Subscription> subscribers;
        synchronized (this) {
            closed = true;
            captured = List.copyOf(jobs.values());
            subscribers = captured.stream().flatMap(job -> job.subscribers.stream()).toList();
            captured.forEach(
                    job -> {
                        job.cancelled = true;
                        job.terminal = true;
                        job.subscribers.clear();
                    });
            jobs.clear();
        }
        for (Job job : captured) {
            job.cancellation.close();
            if (job.future != null) job.future.cancel(true);
        }
        subscribers.forEach(
                subscriber -> {
                    subscriber.delivery.close();
                    subscriber.result.cancel(false);
                });
        worker.shutdownNow();
        scheduler.shutdownNow();
    }
}
