package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static app.musicplayer.online.OnlineTestFixtures.track;
import static org.junit.jupiter.api.Assertions.*;

/** Real body reads occupy both queue workers; cancellation must release one without waiting for the server. */
class CrawlerTransportQueueTest {
    @TempDir Path directory;

    @Test void cancellationReleasesAWorkerKeepsOtherTransferAliveAndCleansItsPart() throws Exception {
        var started = new CountDownLatch(2); var releaseOther = new CountDownLatch(1);
        var releaseCancelled = new CountDownLatch(1); var cleanedCancelled = new CountDownLatch(1);
        var handlers = Executors.newCachedThreadPool(); var cancellationWorker = Executors.newSingleThreadExecutor();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(handlers);
        for (String name : java.util.List.of("cancelled", "other")) {
            server.createContext("/" + name, exchange -> {
                try {
                    exchange.sendResponseHeaders(200, 1024); exchange.getResponseBody().write(new byte[128]);
                    exchange.getResponseBody().flush(); started.countDown();
                    (name.equals("other") ? releaseOther : releaseCancelled).await(10, TimeUnit.SECONDS);
                    exchange.getResponseBody().write(new byte[896]);
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
        }
        server.createContext("/waiting", exchange -> {
            try { exchange.sendResponseHeaders(200, 32); exchange.getResponseBody().write(new byte[32]); }
            finally { exchange.close(); }
        });
        server.start(); String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try (var session = new CrawlerSession(); var queue = new DownloadQueue((track, target, token, progress) -> {
            Path part = target.resolve(track.primaryId() + ".part");
            try (var scope = session.cancellationScope(token)) {
                try (var response = session.download(base + "/" + track.primaryId(), base);
                     var output = Files.newOutputStream(part)) {
                    response.body().transferTo(output); token.check();
                }
                return Files.move(part, target.resolve(track.primaryId() + ".mp3"));
            } finally {
                Files.deleteIfExists(part);
                if (track.primaryId().equals("cancelled")) cleanedCancelled.countDown();
            }
        })) {
            var cancelled = queue.submit(track("cancelled"), directory, Runnable::run, ignored -> { });
            var other = queue.submit(track("other"), directory, Runnable::run, ignored -> { });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            var waiting = queue.submit(track("waiting"), directory, Runnable::run, ignored -> { });
            assertFalse(waiting.result().isDone()); assertFalse(other.result().isDone());
            cancellationWorker.submit(cancelled::cancel).get(3, TimeUnit.SECONDS);
            assertTrue(cancelled.result().isCancelled());
            assertEquals(32, Files.size(waiting.result().get(3, TimeUnit.SECONDS)));
            assertTrue(cleanedCancelled.await(3, TimeUnit.SECONDS));
            assertFalse(Files.exists(directory.resolve("cancelled.part"))); assertFalse(Files.exists(directory.resolve("cancelled.mp3")));
            assertFalse(other.result().isDone(), "Cancelling one request must not cancel the other body read");
            releaseOther.countDown(); assertEquals(1024, Files.size(other.result().get(3, TimeUnit.SECONDS)));
            try (var files = Files.list(directory)) { assertFalse(files.anyMatch(path -> path.toString().endsWith(".part"))); }
        } finally {
            releaseOther.countDown(); releaseCancelled.countDown(); server.stop(0);
            handlers.shutdownNow(); cancellationWorker.shutdownNow();
        }
    }
}
