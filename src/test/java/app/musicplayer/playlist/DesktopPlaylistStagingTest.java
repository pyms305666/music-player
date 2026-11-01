package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.config.DownloadDirectorySettings;
import app.musicplayer.data.MusicDatabase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/** 验证歌单音频暂存位于目标目录，且未发布音频不会进入扫描和重复检测。 */
class DesktopPlaylistStagingTest {
    @TempDir Path directory;

    /** 两个任务暂存互相隔离；递归查重及曲库导入都忽略内部暂存目录。 */
    @Test
    void stagesOnDestinationAndExcludesUnpublishedTracks() throws Exception {
        Path songs = Files.createDirectories(directory.resolve("目标盘音乐"));
        try (var database = new MusicDatabase(directory.resolve("library.db"))) {
            var files = new DesktopPlaylistFiles(database);
            Path first = files.stagingDirectory(songs.toString(), directory.resolve("legacy"));
            Path second = files.stagingDirectory(songs.toString(), directory.resolve("legacy"));
            assertTrue(first.startsWith(songs));
            assertNotEquals(first, second);
            Files.createDirectories(first);
            Files.write(first.resolve("pending.wav"), new byte[64]);
            Path published = Files.write(songs.resolve("published.wav"), new byte[64]);
            database.saveTracks(
                    java.util.List.of(
                            new app.musicplayer.model.Track(first.resolve("pending.wav")),
                            new app.musicplayer.model.Track(published)));
            assertFalse(files.readable(first.resolve("pending.wav").toString()));
            assertEquals(1, files.scan(songs.toString(), true).size());
            var library = new TrackLibraryService();
            assertEquals(published, library.scanFolder(songs).getFirst().path());
            assertTrue(
                    library.fromFiles(java.util.List.of(first.resolve("pending.wav"))).isEmpty());
            assertTrue(DownloadDirectorySettings.internal(first));
        }
    }
}
