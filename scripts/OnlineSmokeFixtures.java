package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic UI fixtures, compiled only by the smoke harness. */
public final class OnlineSmokeFixtures implements AutoCloseable {
    public final CountDownLatch releaseSource = new CountDownLatch(1);
    public final CountDownLatch transferStarted = new CountDownLatch(1);
    public final CountDownLatch releaseTransfer = new CountDownLatch(1);
    public final AtomicInteger transfers = new AtomicInteger();
    public final AtomicInteger sourceCalls = new AtomicInteger();
    public final OnlineMusicSearchService service;
    public OnlineSmokeFixtures() {
        var fast = provider("fast", false);
        var slow = provider("slow", true);
        service = new OnlineMusicSearchService(new MusicCrawler(List.of(slow, fast), 5000), System::nanoTime,
                (track, directory, cancellation, progress) -> {
                    transfers.incrementAndGet(); Files.createDirectories(directory);
                    Path temporary = Files.createTempFile(directory, "ui-fixture-", ".part");
                    try {
                        Files.write(temporary, new byte[2048]);
                        progress.accept(new DownloadEvent(DownloadEvent.Stage.TRANSFERRING, 2048, OptionalLong.empty()));
                        transferStarted.countDown();
                        try (var detach = cancellation.onCancel(releaseTransfer::countDown)) {
                            if (!releaseTransfer.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture was not released");
                            cancellation.check();
                            Path target = directory.resolve(track.primaryId() + ".wav");
                            return Files.move(temporary, target);
                        }
                    } finally { Files.deleteIfExists(temporary); }
                });
    }
    private OnlineSourceProvider provider(String name, boolean slow) {
        return new OnlineSourceProvider() {
            public String sourceName() { return name; }
            public String referer() { return "http://localhost/"; }
            public List<OnlineTrackInfo> search(String query) {
                sourceCalls.incrementAndGet();
                if (slow) try {
                    if (!releaseSource.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Source was not released");
                } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
                return slow ? List.of(track(name)) : List.of(track(name), track(name + "2"));
            }
            public String resolve(OnlineTrackInfo track) { return null; }
        };
    }
    private static OnlineTrackInfo track(String name) {
        return new OnlineTrackInfo(name, "song " + name, "artist", "", null, name, null);
    }
    @Override public void close() { releaseSource.countDown(); releaseTransfer.countDown(); service.close(); }
}
