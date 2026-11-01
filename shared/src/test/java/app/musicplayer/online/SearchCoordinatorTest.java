package app.musicplayer.online;

import static app.musicplayer.online.OnlineTestFixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SearchCoordinator
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：增量搜索与界面发布协调。管理当前搜索、结果缓存和节流投递，取消旧轮次，来源网络请求仍由爬虫线程池执行。
 */
class SearchCoordinatorTest {
    /** 验证空查询直接发布结束状态且不调用来源。 */
    @Test
    void emptyQueryPublishesTerminalStateWithoutCallingSources() throws Exception {
        var states = new LinkedBlockingQueue<OnlineSearchSnapshot>();
        try (var crawler =
                        new MusicCrawler(
                                List.of(
                                        provider(
                                                "source",
                                                () -> {
                                                    fail("Empty query reached provider");
                                                    return List.of();
                                                })),
                                1000);
                var searches = new SearchCoordinator(crawler, System::nanoTime)) {
            assertEquals(
                    OnlineSearchSnapshot.State.EMPTY,
                    searches.search("  ", Runnable::run, states::add)
                            .result()
                            .get(3, TimeUnit.SECONDS)
                            .state());
            var shown = states.poll(3, TimeUnit.SECONDS);
            assertNotNull(shown);
            assertEquals(OnlineSearchSnapshot.State.EMPTY, shown.state());
            assertFalse(shown.cacheable());
        }
    }

    /** 验证查询缓存命中后不再次调用来源。 */
    @Test
    void cachedQueryDoesNotCallSourcesAgain() throws Exception {
        var calls = new AtomicInteger();
        try (var crawler =
                        new MusicCrawler(
                                List.of(
                                        provider(
                                                "source",
                                                () -> {
                                                    calls.incrementAndGet();
                                                    return List.of(track("source"));
                                                })),
                                1000);
                var searches = new SearchCoordinator(crawler, () -> 0L)) {
            assertFalse(
                    searches.search(" song ", Runnable::run, ignored -> {})
                            .result()
                            .get(3, TimeUnit.SECONDS)
                            .cached());
            assertTrue(
                    searches.search("SONG", Runnable::run, ignored -> {})
                            .result()
                            .get(3, TimeUnit.SECONDS)
                            .cached());
            assertEquals(1, calls.get());
        }
    }

    /** 验证旧查询已排队 UI 更新不会进入下一个查询。 */
    @Test
    void obsoleteQueuedUiUpdatesNeverReachTheNextQuery() throws Exception {
        var ui = new LinkedBlockingQueue<Runnable>();
        var shown = new LinkedBlockingQueue<String>();
        try (var crawler =
                        new MusicCrawler(
                                List.of(provider("source", () -> List.of(track("source")))), 1000);
                var searches = new SearchCoordinator(crawler, System::nanoTime)) {
            searches.search("old", ui::add, value -> shown.add(value.query()))
                    .result()
                    .get(3, TimeUnit.SECONDS);
            Runnable old = ui.poll(3, TimeUnit.SECONDS);
            assertNotNull(old);
            searches.search("new", ui::add, value -> shown.add(value.query()))
                    .result()
                    .get(3, TimeUnit.SECONDS);
            old.run();
            assertTrue(shown.isEmpty());
            Runnable next = ui.poll(3, TimeUnit.SECONDS);
            assertNotNull(next);
            next.run();
            assertEquals("new", shown.poll());
        }
    }

    /** 验证用户取消发布取消状态，并且不缓存该查询。 */
    @Test
    void userCancellationPublishesCancelledStateAndDoesNotCacheIt() throws Exception {
        var started = new CountDownLatch(1);
        var cancelled = new CountDownLatch(1);
        var states = new LinkedBlockingQueue<OnlineSearchSnapshot>();
        var source =
                provider(
                        "source",
                        () -> {
                            started.countDown();
                            try {
                                Thread.sleep(10_000);
                            } catch (InterruptedException stopped) {
                                cancelled.countDown();
                                Thread.currentThread().interrupt();
                            }
                            return List.of();
                        });
        try (var crawler = new MusicCrawler(List.of(source), 5000);
                var searches = new SearchCoordinator(crawler, System::nanoTime)) {
            var task = searches.search("song", Runnable::run, states::add);
            assertTrue(started.await(3, TimeUnit.SECONDS));
            task.cancel();
            assertTrue(task.result().isCancelled());
            assertTrue(cancelled.await(3, TimeUnit.SECONDS));
            OnlineSearchSnapshot snapshot;
            do {
                snapshot = states.poll(3, TimeUnit.SECONDS);
                assertNotNull(snapshot);
            } while (!snapshot.finished());
            assertEquals(OnlineSearchSnapshot.State.CANCELLED, snapshot.state());
            assertFalse(snapshot.cacheable());
        }
    }
}
