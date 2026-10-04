package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** UI-thread owner of transfers and subsequent library publication for either client. */
public final class DownloadController<T> implements AutoCloseable {
    public enum CancelResult { NONE, CANCELLED, PUBLISHING }
    public record Notice(OnlineTrackInfo track, DownloadEvent event, Throwable error) { }
    private record Key(String track, Path directory) { }
    private static final class Active {
        final OnlineTrackInfo track;
        DownloadEvent event = DownloadEvent.of(DownloadEvent.Stage.QUEUED);
        CancellableTask<Path> task;
        Active(OnlineTrackInfo track) { this.track = track; }
    }
    private final OnlineMusicSearchService service;
    private final Executor ui;
    private final Consumer<Notice> listener;
    private final Consumer<Path> discardUnpublished;
    private final Map<Key, Active> active = new HashMap<>();
    private volatile boolean closed;
    private String failedIdentity;

    public DownloadController(OnlineMusicSearchService service, Executor ui, Consumer<Notice> listener) {
        this(service, ui, listener, ignored -> { });
    }
    /** A staging destination must supply cleanup until its publisher accepts ownership. */
    public DownloadController(OnlineMusicSearchService service, Executor ui, Consumer<Notice> listener,
                              Consumer<Path> discardUnpublished) {
        this.service = service; this.ui = ui; this.listener = listener; this.discardUnpublished = discardUnpublished;
    }
    public boolean start(OnlineTrackInfo track, Path directory,
            Function<Path, CompletableFuture<T>> publish, Consumer<T> completed) {
        if (closed) return false;
        Key key = new Key(track.identity(), directory.toAbsolutePath().normalize());
        if (active.containsKey(key)) return false;
        Active job = new Active(track);
        active.put(key, job);
        try {
            job.task = service.download(track, directory, ui, event -> {
                if (closed || active.get(key) != job || job.event.stage() == DownloadEvent.Stage.PUBLISHING) return;
                job.event = event;
                listener.accept(new Notice(track, event, null));
            });
        } catch (DownloadQueueFullException full) {
            active.remove(key);
            failedIdentity = track.identity();
            listener.accept(new Notice(track, DownloadEvent.of(DownloadEvent.Stage.FAILED), full));
            return false;
        } catch (RuntimeException error) {
            active.remove(key);
            throw error;
        }
        listener.accept(new Notice(track, job.event, null));
        job.task.result().thenComposeAsync(path -> {
            // UI close and publication scheduling are serialized before the library executor shuts down.
            if (closed) return discard(path, new CancellationException("Page closed"));
            try {
                job.event = new DownloadEvent(DownloadEvent.Stage.PUBLISHING, job.event.transferredBytes(), job.event.totalBytes());
                listener.accept(new Notice(track, job.event, null));
                return java.util.Objects.requireNonNull(publish.apply(path), "Publisher must return a future");
            } catch (RuntimeException rejected) { return discard(path, rejected); }
        }, ui).whenComplete((value, error) -> ui.execute(() -> {
            if (closed || !active.remove(key, job)) return;
            Throwable cause = root(error);
            var stage = cause instanceof CancellationException ? DownloadEvent.Stage.CANCELLED
                    : cause == null ? DownloadEvent.Stage.COMPLETE : DownloadEvent.Stage.FAILED;
            failedIdentity = stage == DownloadEvent.Stage.FAILED ? track.identity()
                    : track.identity().equals(failedIdentity) ? null : failedIdentity;
            listener.accept(new Notice(track, new DownloadEvent(stage, job.event.transferredBytes(), job.event.totalBytes()), cause));
            if (cause == null) completed.accept(value);
        }));
        return true;
    }
    private CompletableFuture<T> discard(Path path, RuntimeException cause) {
        try { discardUnpublished.accept(path); } catch (RuntimeException cleanup) { cause.addSuppressed(cleanup); }
        CompletableFuture<T> failure = new CompletableFuture<>();
        failure.completeExceptionally(cause);
        return failure;
    }
    private Active selected(OnlineTrackInfo track) {
        if (track == null) return null;
        return active.values().stream().filter(job -> job.track.identity().equals(track.identity())).findFirst().orElse(null);
    }
    public Optional<DownloadEvent> event(OnlineTrackInfo track) {
        Active job = selected(track); return job == null ? Optional.empty() : Optional.of(job.event);
    }
    public boolean retry(OnlineTrackInfo track) { return track != null && track.identity().equals(failedIdentity); }
    public CancelResult cancel(OnlineTrackInfo track) {
        Active job = selected(track);
        if (job == null) return CancelResult.NONE;
        if (job.event.stage() == DownloadEvent.Stage.PUBLISHING || job.task.result().isDone()) return CancelResult.PUBLISHING;
        job.task.cancel(); return CancelResult.CANCELLED;
    }
    private static Throwable root(Throwable error) {
        while (error != null && error.getCause() != null) error = error.getCause();
        return error;
    }
    @Override public void close() {
        closed = true;
        List.copyOf(active.values()).forEach(job -> job.task.cancel());
        active.clear();
    }
}
