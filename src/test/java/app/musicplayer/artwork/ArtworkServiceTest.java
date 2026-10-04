package app.musicplayer.artwork;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkServiceTest {
    @TempDir Path cache;

    @Test void downloadsWithoutExecutorDeadlockAndReusesCompleteCache() throws Exception {
        HttpServer server = server(new byte[] {1, 2, 3});
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/cover.png";
        try (var service = new ArtworkService(cache)) {
            Path file = service.cache(url).get(5, TimeUnit.SECONDS);
            assertNotNull(file);
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
            server.stop(0);
            assertEquals(file, service.cache(url).get(2, TimeUnit.SECONDS));
            try (var entries = Files.list(cache)) { assertEquals(1, entries.count()); }
        } finally { server.stop(0); }
    }

    @Test void rejectsOversizedImageWithoutPublishingPartialCache() throws Exception {
        HttpServer server = server(new byte[12 * 1024 * 1024 + 1]);
        try (var service = new ArtworkService(cache)) {
            assertNull(service.cache("http://127.0.0.1:" + server.getAddress().getPort() + "/cover.png").get(5, TimeUnit.SECONDS));
            try (var entries = Files.list(cache)) { assertEquals(0, entries.count()); }
        } finally { server.stop(0); }
    }

    @Test void mergesInFlightConsumersAndCancelsOnlyAbandonedWork() throws Exception {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet(); started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, 3);
                exchange.getResponseBody().write(new byte[]{1, 2, 3});
            } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try (var service = new ArtworkService(cache)) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/cover.png";
            var first = service.acquire(url);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var second = service.acquire(url);
            first.close();
            assertTrue(first.result().isCancelled());
            release.countDown();
            assertNotNull(second.result().get(5, TimeUnit.SECONDS));
            assertEquals(1, hits.get());
        } finally { release.countDown(); server.stop(0); }
    }

    @Test void cancellingAllConsumersDoesNotPublishOrLeaveTemporaryFiles() throws Exception {
        var started = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            started.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try (var service = new ArtworkService(cache)) {
            var request = service.acquire("http://127.0.0.1:" + server.getAddress().getPort() + "/cover.png");
            assertTrue(started.await(2, TimeUnit.SECONDS));
            request.close();
            assertTrue(request.result().isCancelled());
        } finally { release.countDown(); server.stop(0); }
        try (var entries = Files.list(cache)) { assertEquals(0, entries.count()); }
    }

    @Test void reacquiringCancelledUrlDoesNotLoseOrCancelReplacementConsumers() throws Exception {
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        var firstStarted = new java.util.concurrent.CountDownLatch(1);
        var nextStarted = new java.util.concurrent.CountDownLatch(1);
        var firstRelease = new java.util.concurrent.CountDownLatch(1);
        var nextRelease = new java.util.concurrent.CountDownLatch(1);
        var handlers = java.util.concurrent.Executors.newCachedThreadPool();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(handlers);
        server.createContext("/", exchange -> {
            boolean abandoned = hits.incrementAndGet() == 1;
            (abandoned ? firstStarted : nextStarted).countDown();
            try {
                (abandoned ? firstRelease : nextRelease).await(5, TimeUnit.SECONDS);
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, 3);
                exchange.getResponseBody().write(abandoned ? new byte[]{9, 9, 9} : new byte[]{1, 2, 3});
            } catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try (var service = new ArtworkService(cache)) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/cover.png";
            var abandoned = service.acquire(url);
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            abandoned.close();
            var replacement = service.acquire(url);
            assertTrue(nextStarted.await(2, TimeUnit.SECONDS));
            firstRelease.countDown();
            var coalesced = service.acquire(url);
            nextRelease.countDown();
            Path published = replacement.result().get(5, TimeUnit.SECONDS);
            assertNotNull(published);
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(published));
            assertEquals(published, coalesced.result().get(5, TimeUnit.SECONDS));
            assertEquals(2, hits.get());
            assertTrue(abandoned.result().isCancelled());
            try (var entries = Files.list(cache)) { assertEquals(1, entries.count()); }
        } finally {
            firstRelease.countDown(); nextRelease.countDown();
            server.stop(0); handlers.shutdownNow();
        }
    }

    private HttpServer server(byte[] bytes) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally { exchange.close(); }
        });
        server.start();
        return server;
    }
}
