package app.musicplayer.online;

import static app.musicplayer.online.OnlineTestFixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DownloadController
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：单曲下载与界面状态协调。跟踪当前歌曲的下载事件、重试及取消，将已下载文件交给平台发布操作并回报完成。
 */
class DownloadControllerTest {
    @TempDir Path directory;

    /** 清理测试下载暂存文件，保留已发布音频。 */
    private void deleteStaging(Path path) {
        try {
            java.nio.file.Files.deleteIfExists(path);
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    /** 验证传输完成到界面发布之间关闭会清理暂存文件。 */
    @Test
    void closingAfterTransferBeforeUiPublicationDiscardsStagingFile() throws Exception {
        var ui = new LinkedBlockingQueue<Runnable>();
        var posted = new CountDownLatch(2);
        var discarded = new AtomicInteger();
        var publications = new AtomicInteger();
        Path staging = directory.resolve("unpublished.mp3");
        try (var service =
                        new OnlineMusicSearchService(
                                new MusicCrawler(List.of(), 1000),
                                System::nanoTime,
                                (track, target, token, progress) ->
                                        java.nio.file.Files.write(staging, new byte[32]));
                var controller =
                        new DownloadController<String>(
                                service,
                                command -> {
                                    ui.add(command);
                                    posted.countDown();
                                },
                                ignored -> {},
                                path -> {
                                    discarded.incrementAndGet();
                                    deleteStaging(path);
                                })) {
            assertTrue(
                    controller.start(
                            track("closed"),
                            directory,
                            path -> {
                                publications.incrementAndGet();
                                return CompletableFuture.completedFuture("saved");
                            },
                            ignored -> fail("Closed page received completion")));
            assertTrue(
                    posted.await(3, TimeUnit.SECONDS),
                    "Transfer did not reach the queued UI handoff");
            assertTrue(java.nio.file.Files.exists(staging));
            controller.close();
            Runnable next;
            while ((next = ui.poll()) != null) next.run();
            assertEquals(0, publications.get());
            assertEquals(1, discarded.get());
            assertFalse(java.nio.file.Files.exists(staging));
        }
    }

    /** 验证发布拒绝时归还文件清理责任并提供重试。 */
    @Test
    void rejectedPublicationReturnsOwnershipAndOffersRetry() throws Exception {
        var ui = new LinkedBlockingQueue<Runnable>();
        var discarded = new AtomicInteger();
        var notices = new java.util.ArrayList<DownloadController.Notice>();
        Path staging = directory.resolve("rejected.mp3");
        try (var service =
                        new OnlineMusicSearchService(
                                new MusicCrawler(List.of(), 1000),
                                System::nanoTime,
                                (track, target, token, progress) ->
                                        java.nio.file.Files.write(staging, new byte[32]));
                var controller =
                        new DownloadController<String>(
                                service,
                                ui::add,
                                notices::add,
                                path -> {
                                    discarded.incrementAndGet();
                                    deleteStaging(path);
                                })) {
            assertTrue(
                    controller.start(
                            track("rejected"),
                            directory,
                            path -> {
                                throw new java.util.concurrent.RejectedExecutionException(
                                        "Library executor is closed");
                            },
                            ignored -> fail("Rejected publication completed")));
            while (!controller.retry(track("rejected"))) {
                Runnable next = ui.poll(3, TimeUnit.SECONDS);
                assertNotNull(next);
                next.run();
            }
            assertEquals(1, discarded.get());
            assertFalse(java.nio.file.Files.exists(staging));
            assertEquals(
                    DownloadEvent.Stage.FAILED, notices.get(notices.size() - 1).event().stage());
            assertInstanceOf(
                    java.util.concurrent.RejectedExecutionException.class,
                    notices.get(notices.size() - 1).error());
        }
    }

    /** 验证重复启动只发布一次，已进入发布阶段不会被中断。 */
    @Test
    void duplicateStartsPublishOnlyOnceAndPublicationCannotBeInterrupted() throws Exception {
        var transferStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ui = new LinkedBlockingQueue<Runnable>();
        var transfers = new AtomicInteger();
        var publications = new AtomicInteger();
        var notices = new java.util.ArrayList<DownloadController.Notice>();
        var saved = new CompletableFuture<String>();
        var completed = new java.util.ArrayList<String>();
        try (var service =
                        new OnlineMusicSearchService(
                                new MusicCrawler(List.of(), 1000),
                                System::nanoTime,
                                (track, target, token, progress) -> {
                                    transfers.incrementAndGet();
                                    transferStarted.countDown();
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                    token.check();
                                    return target.resolve("song.mp3");
                                });
                var controller = new DownloadController<String>(service, ui::add, notices::add)) {
            assertTrue(
                    controller.start(
                            track("same"),
                            directory,
                            path -> {
                                publications.incrementAndGet();
                                return saved;
                            },
                            completed::add));
            assertTrue(transferStarted.await(3, TimeUnit.SECONDS));
            assertFalse(
                    controller.start(
                            track("same"),
                            directory.resolve("."),
                            path -> {
                                fail("Duplicate publisher");
                                return saved;
                            },
                            completed::add));
            release.countDown();
            while (controller.event(track("same")).orElseThrow().stage()
                    != DownloadEvent.Stage.PUBLISHING) {
                Runnable next = ui.poll(3, TimeUnit.SECONDS);
                assertNotNull(next);
                next.run();
            }
            assertEquals(
                    DownloadController.CancelResult.PUBLISHING, controller.cancel(track("same")));
            saved.complete("saved");
            while (completed.isEmpty()) {
                Runnable next = ui.poll(3, TimeUnit.SECONDS);
                assertNotNull(next);
                next.run();
            }
            assertEquals(List.of("saved"), completed);
            assertEquals(1, publications.get());
            assertEquals(1, transfers.get());
            assertTrue(controller.event(track("same")).isEmpty());
            assertEquals(
                    DownloadEvent.Stage.COMPLETE, notices.get(notices.size() - 1).event().stage());
        } finally {
            release.countDown();
        }
    }

    /** 验证传输失败可重试，另一首歌曲任务保持独立。 */
    @Test
    void failedTransferOffersRetryAndKeepsAnotherSongIndependent() throws Exception {
        var ui = new LinkedBlockingQueue<Runnable>();
        var completed = new java.util.ArrayList<String>();
        try (var service =
                        new OnlineMusicSearchService(
                                new MusicCrawler(List.of(), 1000),
                                System::nanoTime,
                                (track, target, token, progress) -> {
                                    if (track.primaryId().equals("failed"))
                                        throw new java.io.IOException("Fixture failure");
                                    return target.resolve("song.mp3");
                                });
                var controller = new DownloadController<String>(service, ui::add, ignored -> {})) {
            assertTrue(
                    controller.start(
                            track("failed"),
                            directory,
                            path -> {
                                fail("Failed transfer published");
                                return CompletableFuture.completedFuture("");
                            },
                            completed::add));
            assertTrue(
                    controller.start(
                            track("other"),
                            directory,
                            path -> CompletableFuture.completedFuture("other"),
                            completed::add));
            while (!controller.retry(track("failed")) || completed.isEmpty()) {
                Runnable next = ui.poll(3, TimeUnit.SECONDS);
                assertNotNull(next);
                next.run();
            }
            assertEquals(List.of("other"), completed);
            assertFalse(controller.retry(track("other")));
        }
    }
}
