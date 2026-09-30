package app.musicplayer.util;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class LatestRequestTest {
    @Test void cancelsObsoleteWorkerAndCompletesLatest() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (var requests = new LatestRequest<String>()) {
            CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
            var first = requests.submit(executor, () -> {
                started.countDown();
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException cancelled) { interrupted.countDown(); Thread.currentThread().interrupt(); }
                return "old";
            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals("new", requests.submit(executor, () -> "new").get(2, TimeUnit.SECONDS));
            assertTrue(first.isCancelled());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
}
