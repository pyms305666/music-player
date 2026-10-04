package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static app.musicplayer.online.OnlineTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class DownloadQueueTest {
    @TempDir Path directory;
    @Test void enforcesTwoRunningEightWaitingAndFreesCancelledWaitingSlot() throws Exception {
        var running = new AtomicInteger(); var peak = new AtomicInteger();
        var started = new CountDownLatch(2); var release = new CountDownLatch(1);
        var handles = new ArrayList<CancellableTask<Path>>();
        try (var queue = new DownloadQueue((track, target, cancellation, progress) -> {
            int count = running.incrementAndGet(); peak.accumulateAndGet(count, Math::max); started.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS)); cancellation.check();
                Path path = target.resolve(track.primaryId() + ".mp3"); Files.write(path, new byte[32]); return path;
            } finally { running.decrementAndGet(); }
        })) {
            for (int i = 0; i < 2; i++) handles.add(queue.submit(track("t" + i), directory, Runnable::run, ignored -> { }));
            assertTrue(started.await(3, TimeUnit.SECONDS));
            for (int i = 2; i < 10; i++) handles.add(queue.submit(track("t" + i), directory, Runnable::run, ignored -> { }));
            assertThrows(DownloadQueueFullException.class, () -> queue.submit(track("full"), directory, Runnable::run, ignored -> { }));
            handles.get(2).cancel(); assertTrue(handles.get(2).result().isCancelled());
            handles.add(queue.submit(track("replacement"), directory, Runnable::run, ignored -> { }));
            release.countDown();
            for (var handle : handles) if (!handle.result().isCancelled()) assertNotNull(handle.result().get(3, TimeUnit.SECONDS));
            assertEquals(2, peak.get());
        } finally { release.countDown(); }
    }
    @Test void mergesDuplicatesAndCancellationDoesNotInterruptAnotherConsumerOrSong() throws Exception {
        var started = new CountDownLatch(2); var release = new CountDownLatch(1); var calls = new AtomicInteger();
        try (var queue = new DownloadQueue((track, target, cancellation, progress) -> {
            calls.incrementAndGet(); started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS)); cancellation.check();
            return target.resolve(track.primaryId());
        })) {
            var first = queue.submit(track("same"), directory, Runnable::run, ignored -> { });
            var duplicate = queue.submit(track("same"), directory.resolve("."), Runnable::run, ignored -> { });
            var other = queue.submit(track("other"), directory, Runnable::run, ignored -> { });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            first.cancel(); assertTrue(first.result().isCancelled()); assertFalse(duplicate.result().isDone());
            release.countDown();
            assertEquals(directory.resolve("same"), duplicate.result().get(3, TimeUnit.SECONDS));
            assertEquals(directory.resolve("other"), other.result().get(3, TimeUnit.SECONDS));
            assertEquals(2, calls.get());
        } finally { release.countDown(); }
    }
    @Test void cancelledPublicationIsRemovedAndOtherDownloadFinishes() throws Exception {
        var wrote = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new CountDownLatch(1);
        try (var queue = new DownloadQueue((track, target, cancellation, progress) -> {
            Path path = target.resolve(track.primaryId()); Files.write(path, new byte[32]);
            if (track.primaryId().equals("cancelled")) {
                wrote.countDown();
                // Simulates a transfer returning after cancellation, despite its worker interrupt.
                while (release.getCount() > 0) try { release.await(); } catch (InterruptedException ignored) { }
                finished.countDown();
            }
            return path;
        })) {
            var cancelled = queue.submit(track("cancelled"), directory, Runnable::run, ignored -> { });
            assertTrue(wrote.await(3, TimeUnit.SECONDS)); cancelled.cancel();
            var other = queue.submit(track("other"), directory, Runnable::run, ignored -> { });
            assertTrue(Files.exists(other.result().get(3, TimeUnit.SECONDS)));
            release.countDown(); assertTrue(finished.await(3, TimeUnit.SECONDS));
        } finally { release.countDown(); }
        // Queue close interrupts owned work; await its file cleanup rather than using a timing metric.
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(3), () -> {
            while (Files.exists(directory.resolve("cancelled"))) Thread.yield();
        });
        assertTrue(Files.exists(directory.resolve("other")));
    }
    @Test void differentDestinationDirectoriesDoNotShareAJob() throws Exception {
        var calls = new AtomicInteger();
        try (var queue = new DownloadQueue((track, target, cancellation, progress) -> { calls.incrementAndGet(); return target.resolve("song"); })) {
            var first = queue.submit(track("same"), directory, Runnable::run, ignored -> { });
            var second = queue.submit(track("same"), directory.resolve("other"), Runnable::run, ignored -> { });
            assertNotEquals(first.result().get(3, TimeUnit.SECONDS), second.result().get(3, TimeUnit.SECONDS)); assertEquals(2, calls.get());
        }
    }
}
