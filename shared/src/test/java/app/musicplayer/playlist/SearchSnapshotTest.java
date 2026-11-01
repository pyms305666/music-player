package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.model.Track;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * SearchSnapshot
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：本地搜索的不可变元数据快照。捕获名称、歌手和文件名的规范化文本，避免筛选过程中读取正在改变的模型。
 */
class SearchSnapshotTest {
    /** 验证中文、大小写、文件名及空查询使用捕获的元数据。 */
    @Test
    void chineseCaseFileAndEmptyQueriesUseCapturedMetadata() {
        Track track = new Track(Path.of("Artist - 夜曲.MP3"));
        SearchSnapshot<Track> snapshot = SearchSnapshot.ofTracks(List.of(track));
        for (String query : List.of(" 夜曲 ", "ARTIST", "mp3", ""))
            assertEquals(List.of(track), snapshot.filter(query));
        assertTrue(snapshot.filter("无结果").isEmpty());
        track.updateMetadata("新名字", "新歌手");
        assertTrue(snapshot.filter("新名字").isEmpty());
        assertEquals(List.of(track), SearchSnapshot.ofTracks(List.of(track)).filter("新名字"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().clear());
    }

    /** 验证已排队旧结果不覆盖新结果或已关闭页面。 */
    @Test
    void queuedOldResultCannotOverwriteNewResultOrClosedView() throws Exception {
        var delivery = new LinkedBlockingQueue<Runnable>();
        var shown = new LinkedBlockingQueue<List<String>>();
        try (var search = new LocalSearch<String>(delivery::add, shown::add)) {
            search.replace(
                    new SearchSnapshot<>(List.of(new SearchSnapshot.Entry<>("one", "one", "", ""))),
                    "one");
            Runnable old = delivery.poll(2, TimeUnit.SECONDS);
            assertNotNull(old);
            search.search("absent");
            Runnable next = delivery.poll(2, TimeUnit.SECONDS);
            assertNotNull(next);
            next.run();
            old.run();
            assertEquals(List.of(), shown.remove());
            assertTrue(shown.isEmpty());
            search.search("");
            Runnable clearing = delivery.poll(2, TimeUnit.SECONDS);
            assertNotNull(clearing);
            search.close();
            clearing.run();
            assertTrue(shown.isEmpty());
        }
    }

    /** 验证大曲库合并连续输入，而清空查询立即更新。 */
    @Test
    void largeLibraryMergesTypingAndClearingDoesNotWaitForDebounce() throws Exception {
        var delivery = new LinkedBlockingQueue<Runnable>();
        var shown = new LinkedBlockingQueue<List<Integer>>();
        List<SearchSnapshot.Entry<Integer>> entries =
                java.util.stream.IntStream.range(0, 1000)
                        .mapToObj(i -> new SearchSnapshot.Entry<>(i, "song" + i, "", ""))
                        .toList();
        try (var search = new LocalSearch<Integer>(delivery::add, shown::add)) {
            search.replace(new SearchSnapshot<>(entries), "s");
            search.search("so");
            search.search("song999");
            Runnable latest = delivery.poll(2, TimeUnit.SECONDS);
            assertNotNull(latest);
            latest.run();
            assertEquals(List.of(999), shown.remove());
            assertTrue(delivery.isEmpty());
            search.search("pending");
            search.search("");
            Runnable clear = delivery.poll();
            assertNotNull(clear);
            clear.run();
            assertEquals(1000, shown.remove().size());
        }
    }
}
