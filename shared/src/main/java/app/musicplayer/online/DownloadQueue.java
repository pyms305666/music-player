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
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Two running transfers, eight waiting jobs; duplicates share work and have independent handles. */
final class DownloadQueue implements AutoCloseable {
    static final int PARALLEL_DOWNLOADS = 2;
    static final int WAITING_DOWNLOADS = 8;
    @FunctionalInterface interface Transfer {
        Path run(OnlineTrackInfo track, Path directory, RequestCancellation cancellation,
                 Consumer<DownloadEvent> progress) throws Exception;
    }
    private record Key(String track, Path directory) { }
    private final Transfer transfer;
    private final Map<Key, Job> jobs = new HashMap<>();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(PARALLEL_DOWNLOADS, PARALLEL_DOWNLOADS,
            0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(WAITING_DOWNLOADS), runnable -> {
        Thread thread = new Thread(runnable, "online-download"); thread.setDaemon(true); return thread;
    });
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "download-progress"); thread.setDaemon(true); return thread;
    });
    private boolean closed;
    private static final class Subscription {
        final CompletableFuture<Path> result = new CompletableFuture<>();
        final ThrottledDelivery<DownloadEvent> delivery;
        Subscription(ThrottledDelivery<DownloadEvent> delivery) { this.delivery = delivery; }
    }
    private static final class Job {
        final Key key;
        final OnlineTrackInfo track;
        final RequestCancellation cancellation = new RequestCancellation();
        final Set<Subscription> subscribers = new HashSet<>();
        Future<?> future;
        DownloadEvent event = DownloadEvent.of(DownloadEvent.Stage.QUEUED);
        boolean terminal;
        Job(Key key, OnlineTrackInfo track) { this.key = key; this.track = track; }
    }
    DownloadQueue(Transfer transfer) {
        this.transfer = transfer;
        scheduler.setRemoveOnCancelPolicy(true);
    }
    synchronized CancellableTask<Path> submit(OnlineTrackInfo track, Path directory, Executor ui,
                                              Consumer<DownloadEvent> progress) {
        if (closed) throw new IllegalStateException("Download queue is closed");
        Key key = new Key(MusicCrawler.downloadKey(track), directory.toAbsolutePath().normalize());
        Job job = jobs.get(key);
        boolean created = job == null;
        if (created) { job = new Job(key, track); jobs.put(key, job); }
        Subscription subscription = new Subscription(new ThrottledDelivery<>(scheduler, ui, progress));
        job.subscribers.add(subscription);
        subscription.delivery.offer(job.event);
        if (created) {
            Job captured = job;
            try { job.future = worker.submit(() -> run(captured)); }
            catch (RejectedExecutionException full) {
                jobs.remove(key, job); subscription.delivery.close(); job.subscribers.clear();
                throw new DownloadQueueFullException();
            }
        }
        Job captured = job;
        return new CancellableTask<>(subscription.result, () -> cancel(captured, subscription));
    }
    private void run(Job job) {
        Path path = null;
        Throwable error = null;
        try {
            job.cancellation.check();
            path = transfer.run(job.track, job.key.directory(), job.cancellation, event -> publish(job, event));
            job.cancellation.check();
        } catch (Throwable failed) { error = failed; }
        List<Subscription> subscribers;
        boolean cancelled;
        synchronized (this) {
            cancelled = job.cancellation.isCancelled();
            job.terminal = true;
            jobs.remove(job.key, job);
            subscribers = List.copyOf(job.subscribers);
            job.subscribers.clear();
        }
        if (cancelled && path != null) {
            try { Files.deleteIfExists(path); } catch (java.io.IOException cleanup) { error = cleanup; }
        }
        DownloadEvent.Stage stage = cancelled ? DownloadEvent.Stage.CANCELLED
                : error == null ? DownloadEvent.Stage.COMPLETE : DownloadEvent.Stage.FAILED;
        DownloadEvent terminal = new DownloadEvent(stage, job.event.transferredBytes(), job.event.totalBytes());
        for (Subscription subscriber : subscribers) {
            subscriber.delivery.finish(terminal);
            if (cancelled) subscriber.result.cancel(false);
            else if (error == null) subscriber.result.complete(path);
            else subscriber.result.completeExceptionally(error);
        }
    }
    private synchronized void publish(Job job, DownloadEvent event) {
        if (closed || job.terminal || job.cancellation.isCancelled()) return;
        job.event = event;
        job.subscribers.forEach(subscriber -> subscriber.delivery.offer(event));
    }
    private synchronized void cancel(Job job, Subscription subscriber) {
        if (job.terminal || !job.subscribers.remove(subscriber)) return;
        subscriber.delivery.finish(new DownloadEvent(DownloadEvent.Stage.CANCELLED,
                job.event.transferredBytes(), job.event.totalBytes()));
        subscriber.result.cancel(false);
        if (job.subscribers.isEmpty()) {
            jobs.remove(job.key, job);
            job.cancellation.close();
            if (job.future != null) { job.future.cancel(true); worker.remove((Runnable) job.future); }
        }
    }
    @Override public synchronized void close() {
        closed = true;
        for (Job job : List.copyOf(jobs.values())) {
            job.cancellation.close();
            if (job.future != null) job.future.cancel(true);
            job.subscribers.forEach(subscriber -> { subscriber.delivery.close(); subscriber.result.cancel(false); });
            job.subscribers.clear();
        }
        jobs.clear(); worker.shutdownNow(); scheduler.shutdownNow();
    }
}
