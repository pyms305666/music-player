package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrawlerSessionTest {
    @Test void cancellingOneScopedRequestDisconnectsItsBodyAndLeavesSessionUsable() throws Exception {
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var handlers = java.util.concurrent.Executors.newCachedThreadPool();
        var worker = java.util.concurrent.Executors.newFixedThreadPool(2);
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(handlers);
        server.createContext("/slow", exchange -> {
            try {
                exchange.sendResponseHeaders(200, 4096); exchange.getResponseBody().write(new byte[128]);
                exchange.getResponseBody().flush(); blocked.countDown(); release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.createContext("/fast", exchange -> {
            try { exchange.sendResponseHeaders(200, 2); exchange.getResponseBody().write(new byte[]{'o', 'k'}); }
            finally { exchange.close(); }
        });
        server.start();
        try (var session = new CrawlerSession()) {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var token = new RequestCancellation();
            var request = worker.submit(() -> {
                try (var scope = session.cancellationScope(token)) { return session.fetch(base + "/slow", base); }
            });
            assertTrue(blocked.await(3, java.util.concurrent.TimeUnit.SECONDS));
            worker.submit(token::close).get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> request.get(3, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals("ok", session.fetch(base + "/fast", base));
        } finally { release.countDown(); server.stop(0); handlers.shutdownNow(); worker.shutdownNow(); }
    }
    @Test void seedCookiesStayOnTheirOwnDomainAndPrimingNeedsNoNetwork() {
        try (var session = new CrawlerSession()) {
            session.ensurePrimed();
            assertTrue(session.cookieHeader("https://music.163.com/api/search").contains("__csrf"));
            assertFalse(session.cookieHeader("https://www.kugou.com/").contains("__csrf"));
            assertEquals("", session.cookieHeader("https://example.com/"));
        }
    }

    @Test void expiredDeadlinePreventsNetworkRequest() {
        try (var session = new CrawlerSession()) {
            session.setDeadline(System.nanoTime() - 1);
            assertThrows(InterruptedException.class, () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
        }
    }

    @Test void scopedDeadlineRestoresPreviousBudgetOnFailure() {
        try (var session = new CrawlerSession()) {
            session.setDeadline(System.nanoTime() - 1);
            assertThrows(IllegalStateException.class, () -> session.withinTimeout(1000, () -> { throw new IllegalStateException("fixture"); }));
            assertThrows(InterruptedException.class, () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
            session.clearDeadline();
            assertThrows(java.io.IOException.class, () -> session.fetch("http://127.0.0.1:1/", "http://localhost/"));
        }
    }
}
