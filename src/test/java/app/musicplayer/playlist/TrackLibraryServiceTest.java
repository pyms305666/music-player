package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.musicplayer.model.Track;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * TrackLibraryService
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：桌面曲库的文件规则。扫描支持的音频、按路径去重、缓存创建时间并提供搜索及排序，不依赖 JavaFX 控件。
 */
class TrackLibraryServiceTest {
    @TempDir Path tempDir;

    /** 验证扫描仅保留支持的音频，并按路径移除重复项。 */
    @Test
    void scansSupportedFilesAndRemovesDuplicates() throws Exception {
        Path first = Files.write(tempDir.resolve("歌手 - 歌曲.mp3"), new byte[] {1});
        Files.write(tempDir.resolve("忽略.txt"), new byte[] {1});
        TrackLibraryService service = new TrackLibraryService();

        List<Track> scanned = service.scanFolder(tempDir);
        var result = service.mergeUnique(List.of(new Track(first)), scanned);

        assertEquals(1, scanned.size());
        assertTrue(result.addedTracks().isEmpty());
        assertEquals(1, result.duplicateCount());
    }

    /** 验证曲库搜索与排序不依赖 JavaFX 界面。 */
    @Test
    void searchesAndSortsWithoutDependingOnJavaFx() throws Exception {
        Track second = new Track(Files.write(tempDir.resolve("B歌手 - A歌曲.mp3"), new byte[] {1}));
        Track first = new Track(Files.write(tempDir.resolve("A歌手 - B歌曲.mp3"), new byte[] {1}));
        TrackLibraryService service = new TrackLibraryService();
        List<Track> tracks = new ArrayList<>(List.of(second, first));

        tracks.sort(service.comparator(PlaylistSort.ARTIST, SortDirection.ASCENDING));

        assertEquals("A歌手", tracks.get(0).artist());
        assertTrue(service.matches(first, "B歌曲"));
        assertFalse(service.matches(first, "不存在"));
    }
}
