package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.Track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/** 验证后台队列退出顺序和恢复过滤，不依赖 JavaFX 页面。 */
class DesktopLibraryControllerTest {
    @TempDir Path directory;

    /** finish 不能越过已经排队的写入，停止后应拒绝新工作。 */
    @Test
    void cleanupRunsAfterQueuedWritesAndRejectsNewWork() throws Exception {
        var controller = new DesktopLibraryController();
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cleaned = new CountDownLatch(1);
        try {
            controller
                    .executor()
                    .execute(
                            () -> {
                                try {
                                    assertTrue(release.await(5, TimeUnit.SECONDS));
                                } catch (InterruptedException error) {
                                    throw new AssertionError(error);
                                }
                                events.add("write-one");
                            });
            controller.executor().execute(() -> events.add("write-two"));
            controller.finish(
                    () -> {
                        events.add("close-database");
                        cleaned.countDown();
                    });
            assertThrows(
                    RejectedExecutionException.class,
                    () -> controller.executor().execute(() -> {}));
            release.countDown();
            assertTrue(cleaned.await(5, TimeUnit.SECONDS));
            assertEquals(List.of("write-one", "write-two", "close-database"), events);
        } finally {
            release.countDown();
            controller.close();
            assertTrue(controller.executor().awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    /** 恢复只包含仍存在的支持格式；保存、恢复和数据库关闭共用串行队列。 */
    @Test
    void restoreFiltersMissingAndUnsupportedFiles() throws Exception {
        var controller = new DesktopLibraryController();
        MusicDatabase database = new MusicDatabase(directory.resolve("library.db"));
        Path audio = Files.write(directory.resolve("song.mp3"), new byte[] {1});
        Path other = Files.write(directory.resolve("note.txt"), new byte[] {2});
        try {
            controller
                    .save(
                            database,
                            List.of(
                                    new Track(audio),
                                    new Track(other),
                                    new Track(directory.resolve("missing.mp3"))))
                    .get(5, TimeUnit.SECONDS);
            List<Track> restored =
                    controller
                            .restore(database, new TrackLibraryService())
                            .get(5, TimeUnit.SECONDS);
            assertEquals(
                    List.of(audio.toAbsolutePath()), restored.stream().map(Track::path).toList());
        } finally {
            controller.finish(database::close);
            assertTrue(controller.executor().awaitTermination(5, TimeUnit.SECONDS));
        }
    }
}
