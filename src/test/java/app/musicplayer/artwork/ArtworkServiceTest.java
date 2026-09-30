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
