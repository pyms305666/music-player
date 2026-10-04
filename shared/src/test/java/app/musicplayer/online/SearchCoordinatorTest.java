package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static app.musicplayer.online.OnlineTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class SearchCoordinatorTest {
    @Test void emptyQueryPublishesTerminalStateWithoutCallingSources() throws Exception {
        var states = new LinkedBlockingQueue<OnlineSearchSnapshot>();
        try (var crawler = new MusicCrawler(List.of(provider("source", () -> { fail("Empty query reached provider"); return List.of(); })), 1000);
             var searches = new SearchCoordinator(crawler, System::nanoTime)) {
            assertEquals(OnlineSearchSnapshot.State.EMPTY, searches.search("  ", Runnable::run, states::add).result().get(3, TimeUnit.SECONDS).state());
            var shown = states.poll(3, TimeUnit.SECONDS);
            assertNotNull(shown); assertEquals(OnlineSearchSnapshot.State.EMPTY, shown.state()); assertFalse(shown.cacheable());
        }
    }
    @Test void cachedQueryDoesNotCallSourcesAgain() throws Exception {
        var calls = new AtomicInteger();
        try (var crawler = new MusicCrawler(List.of(provider("source", () -> { calls.incrementAndGet(); return List.of(track("source")); })), 1000);
             var searches = new SearchCoordinator(crawler, () -> 0L)) {
            assertFalse(searches.search(" song ", Runnable::run, ignored -> { }).result().get(3, TimeUnit.SECONDS).cached());
            assertTrue(searches.search("SONG", Runnable::run, ignored -> { }).result().get(3, TimeUnit.SECONDS).cached());
            assertEquals(1, calls.get());
        }
    }
    @Test void obsoleteQueuedUiUpdatesNeverReachTheNextQuery() throws Exception {
        var ui = new LinkedBlockingQueue<Runnable>(); var shown = new LinkedBlockingQueue<String>();
        try (var crawler = new MusicCrawler(List.of(provider("source", () -> List.of(track("source")))), 1000);
             var searches = new SearchCoordinator(crawler, System::nanoTime)) {
            searches.search("old", ui::add, value -> shown.add(value.query())).result().get(3, TimeUnit.SECONDS);
            Runnable old = ui.poll(3, TimeUnit.SECONDS); assertNotNull(old);
            searches.search("new", ui::add, value -> shown.add(value.query())).result().get(3, TimeUnit.SECONDS);
            old.run(); assertTrue(shown.isEmpty());
            Runnable next = ui.poll(3, TimeUnit.SECONDS); assertNotNull(next); next.run(); assertEquals("new", shown.poll());
        }
    }
    @Test void userCancellationPublishesCancelledStateAndDoesNotCacheIt() throws Exception {
        var started = new CountDownLatch(1); var cancelled = new CountDownLatch(1); var states = new LinkedBlockingQueue<OnlineSearchSnapshot>();
        var source = provider("source", () -> {
            started.countDown();
            try { Thread.sleep(10_000); } catch (InterruptedException stopped) { cancelled.countDown(); Thread.currentThread().interrupt(); }
            return List.of();
        });
        try (var crawler = new MusicCrawler(List.of(source), 5000); var searches = new SearchCoordinator(crawler, System::nanoTime)) {
            var task = searches.search("song", Runnable::run, states::add);
            assertTrue(started.await(3, TimeUnit.SECONDS)); task.cancel();
            assertTrue(task.result().isCancelled()); assertTrue(cancelled.await(3, TimeUnit.SECONDS));
            OnlineSearchSnapshot snapshot;
            do { snapshot = states.poll(3, TimeUnit.SECONDS); assertNotNull(snapshot); } while (!snapshot.finished());
            assertEquals(OnlineSearchSnapshot.State.CANCELLED, snapshot.state()); assertFalse(snapshot.cacheable());
        }
    }
}
