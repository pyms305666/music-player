package app.musicplayer.online;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Exercises Android's real HTTP transport using loopback sockets and no application data. */
@RunWith(AndroidJUnit4.class)
public class AndroidTransportTest {
    @Test public void cancellingStalledBodyFreesWorkerAndPreservesSessionCookies() throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        var canceller = Executors.newSingleThreadExecutor();
        var bodyRead = new CountDownLatch(1);
        var token = new RequestCancellation();
        try (var server = new LocalServer(); var session = new CrawlerSession()) {
            var request = worker.submit(() -> {
                try (var scope = session.cancellationScope(token);
                     var response = session.download(server.base() + "/slow", server.base())) {
                    assertEquals(200, response.statusCode());
                    assertEquals('x', response.body().read());
                    bodyRead.countDown();
                    while (response.body().read() >= 0) { }
                    return "unexpected completion";
                }
            });
            assertTrue("Response body was not read", bodyRead.await(3, TimeUnit.SECONDS));
            canceller.submit(token::close).get(3, TimeUnit.SECONDS);
            try {
                request.get(3, TimeUnit.SECONDS);
                fail("Cancelled body completed normally");
            } catch (ExecutionException stopped) {
                assertTrue("Cancellation must close the active body: " + stopped.getCause(),
                        stopped.getCause() instanceof IOException);
            }
            // The same single worker must be available while the server's slow handler is still blocked.
            assertEquals("ok", worker.submit(() -> session.fetch(server.base() + "/fast", server.base()))
                    .get(3, TimeUnit.SECONDS));
            assertNotNull("No Cookie request header", server.cookie.get());
            assertTrue(server.cookie.get().contains("fixture=owned"));
            assertNull("Loopback server failed", server.failure.get());
        } finally {
            token.close();
            worker.shutdownNow();
            canceller.shutdownNow();
            assertTrue("Request worker did not terminate", worker.awaitTermination(3, TimeUnit.SECONDS));
            assertTrue("Canceller did not terminate", canceller.awaitTermination(3, TimeUnit.SECONDS));
        }
    }

    private static final class LocalServer implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService handlers = Executors.newCachedThreadPool();
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final CountDownLatch release = new CountDownLatch(1);
        private final Thread acceptor;
        private volatile boolean closed;
        final AtomicReference<String> cookie = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        LocalServer() throws IOException {
            listener = new ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"));
            acceptor = new Thread(() -> {
                try {
                    while (!closed) {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        handlers.execute(() -> handle(socket));
                    }
                } catch (IOException | RuntimeException error) {
                    if (!closed) failure.compareAndSet(null, error);
                }
            }, "android-transport-loopback");
            acceptor.start();
        }

        String base() { return "http://127.0.0.1:" + listener.getLocalPort(); }

        private void handle(Socket socket) {
            try (Socket owned = socket) {
                owned.setSoTimeout(3000);
                var input = new BufferedReader(new InputStreamReader(owned.getInputStream(), StandardCharsets.US_ASCII));
                String request = input.readLine();
                if (request == null) throw new IOException("Missing request line");
                for (String header; (header = input.readLine()) != null && !header.isEmpty();) {
                    if (header.regionMatches(true, 0, "Cookie:", 0, 7)) cookie.set(header.substring(7).trim());
                }
                var output = owned.getOutputStream();
                if (request.startsWith("GET /slow ")) {
                    output.write(("HTTP/1.1 200 OK\r\nContent-Length: 4096\r\n"
                            + "Set-Cookie: fixture=owned; Path=/\r\nConnection: close\r\n\r\nx")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Slow handler was not released");
                } else if (request.startsWith("GET /fast ")) {
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
                            .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                } else throw new IOException("Unexpected request: " + request);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException error) {
                if (!closed) failure.compareAndSet(null, error);
            } finally { sockets.remove(socket); }
        }

        @Override public void close() throws Exception {
            closed = true;
            release.countDown();
            try { listener.close(); }
            finally {
                try { acceptor.join(3000); }
                finally {
                    for (Socket socket : sockets) {
                        try { socket.close(); } catch (IOException ignored) { }
                    }
                    handlers.shutdownNow();
                }
                assertFalse("Accept thread did not terminate", acceptor.isAlive());
                assertTrue("Server handlers did not terminate", handlers.awaitTermination(3, TimeUnit.SECONDS));
            }
        }
    }
}
