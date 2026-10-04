package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static app.musicplayer.online.OnlineTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class IncrementalSearchTest {
    @Test void publishesFastSourceBeforeSlowSourceAndNeverReordersArrivals() throws Exception {
        var releaseSlow = new CountDownLatch(1);
        var fastPublished = new CountDownLatch(1);
        var intermediate = new AtomicReference<OnlineSearchSnapshot>();
        var slow = provider("slow", () -> {
            try { assertTrue(releaseSlow.await(5, TimeUnit.SECONDS)); }
            catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            return List.of(track("slow"));
        });
        var fast = provider("fast", () -> List.of(track("fast"), track("fast")));
        var worker = Executors.newSingleThreadExecutor();
        try (var crawler = new MusicCrawler(List.of(slow, fast), 5000)) {
            var result = worker.submit(() -> crawler.searchIncrementally("song", snapshot -> {
                if (!snapshot.finished() && snapshot.tracks().size() == 1) {
                    intermediate.set(snapshot); fastPublished.countDown();
                }
            }));
            assertTrue(fastPublished.await(3, TimeUnit.SECONDS));
            assertFalse(result.isDone());
            assertEquals("fast", intermediate.get().tracks().get(0).source());
            releaseSlow.countDown();
            var complete = result.get(3, TimeUnit.SECONDS);
            assertEquals(List.of("fast", "slow"), complete.tracks().stream().map(t -> t.source()).toList());
            assertTrue(complete.cacheable());
            assertThrows(UnsupportedOperationException.class, () -> intermediate.get().tracks().clear());
        } finally { releaseSlow.countDown(); worker.shutdownNow(); }
    }
    @Test void distinguishesEmptyPartialFailureAndAllFailed() {
        var empty = provider("empty", List::of);
        var failed = provider("failed", () -> { throw new IllegalStateException("offline"); });
        var good = provider("good", () -> List.of(track("good")));
        try (var crawler = new MusicCrawler(List.of(empty), 1000)) {
            assertEquals(OnlineSearchSnapshot.State.EMPTY, crawler.searchIncrementally("song", ignored -> { }).state());
        }
        try (var crawler = new MusicCrawler(List.of(failed, good), 1000)) {
            var result = crawler.searchIncrementally("song", ignored -> { });
            assertEquals(OnlineSearchSnapshot.State.PARTIAL_FAILURE, result.state());
            assertEquals(1, result.failedSources());
            assertFalse(result.cacheable());
        }
        try (var crawler = new MusicCrawler(List.of(failed), 1000)) {
            assertEquals(OnlineSearchSnapshot.State.FAILED, crawler.searchIncrementally("song", ignored -> { }).state());
        }
    }
    @Test void deadlineMarksPendingSourcesAsTimedOut() {
        var slow = provider("slow", () -> {
            try { Thread.sleep(10_000); } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            return List.of();
        });
        try (var crawler = new MusicCrawler(List.of(slow), 30)) {
            var result = crawler.searchIncrementally("song", ignored -> { });
            assertEquals(OnlineSearchSnapshot.State.FAILED, result.state());
            assertEquals(OnlineSearchSnapshot.Outcome.TIMED_OUT, result.sources().get(0).outcome());
            assertFalse(result.cacheable());
        }
    }
}
