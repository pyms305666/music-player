package app.musicplayer.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

/**
 * LatestRequest 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：通用最新请求协调器。新请求取消前一次工作，过期结果不得替换最新结果，关闭时终止当前任务。
 */
class LatestRequestTest {
    /** 验证旧工作被取消，而最新请求正常完成。 */
    @Test
    void cancelsObsoleteWorkerAndCompletesLatest() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try (var requests = new LatestRequest<String>()) {
            CountDownLatch started = new CountDownLatch(1), interrupted = new CountDownLatch(1);
            var first =
                    requests.submit(
                            executor,
                            () -> {
                                started.countDown();
                                try {
                                    new CountDownLatch(1).await();
                                } catch (InterruptedException cancelled) {
                                    interrupted.countDown();
                                    Thread.currentThread().interrupt();
                                }
                                return "old";
                            });
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals("new", requests.submit(executor, () -> "new").get(2, TimeUnit.SECONDS));
            assertTrue(first.isCancelled());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
