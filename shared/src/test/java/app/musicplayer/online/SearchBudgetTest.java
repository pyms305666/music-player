package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SearchBudgetTest {
    @Test void returnsFastResultsWithinOneBudgetWithoutResolvingAddresses() throws Exception {
        var interrupted = new CountDownLatch(1);
        var resolves = new AtomicInteger();
        var fast = provider("fast", () -> List.of(track("fast")), resolves);
        var slow = provider("slow", () -> {
            try { Thread.sleep(10_000); }
            catch (InterruptedException cancelled) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return List.of();
        }, resolves);
        try (var crawler = new MusicCrawler(List.of(slow, fast), 250)) {
            long start = System.nanoTime();
            var results = crawler.search("song");
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500);
            assertEquals(1, results.size());
            assertEquals("fast", results.get(0).source());
            assertTrue(results.get(0).canAttemptDownload());
            assertEquals(0, resolves.get());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        }
    }

    @Test void suspendsSourceAfterThreeFailuresAndRetainsHealthySource() {
        var calls = new AtomicInteger();
        var broken = provider("broken", () -> { calls.incrementAndGet(); throw new IllegalStateException("offline"); }, new AtomicInteger());
        var good = provider("good", () -> List.of(track("good")), new AtomicInteger());
        try (var crawler = new MusicCrawler(List.of(broken, good), 2000)) {
            for (int i = 0; i < 4; i++) assertEquals(1, crawler.search("song").size());
            assertEquals(3, calls.get());
        }
    }

    private static OnlineTrackInfo track(String source) { return new OnlineTrackInfo(source, "song", "artist", "", null, "id", null); }
    private static OnlineSourceProvider provider(String source, java.util.function.Supplier<List<OnlineTrackInfo>> search, AtomicInteger resolves) {
        return new OnlineSourceProvider() {
            public String sourceName() { return source; }
            public String referer() { return "http://localhost/"; }
            public List<OnlineTrackInfo> search(String query) { return search.get(); }
            public String resolve(OnlineTrackInfo track) { resolves.incrementAndGet(); return null; }
        };
    }
}
