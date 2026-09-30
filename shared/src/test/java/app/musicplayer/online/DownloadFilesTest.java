package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DownloadFilesTest {
    @TempDir Path directory;

    @Test void concurrentSameNameDownloadsKeepBothPayloadsAndExistingSong() throws Exception {
        Files.writeString(directory.resolve("song.mp3"), "original");
        Path first = Files.writeString(directory.resolve("a.part"), "first");
        Path second = Files.writeString(directory.resolve("b.part"), "second");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.<Callable<Path>>of(
                    () -> DownloadFiles.publish(first, directory, "song", ".mp3"),
                    () -> DownloadFiles.publish(second, directory, "song", ".mp3")));
            assertNotEquals(results.get(0).get(), results.get(1).get());
            assertEquals("first", Files.readString(results.get(0).get()));
            assertEquals("second", Files.readString(results.get(1).get()));
            assertEquals("original", Files.readString(directory.resolve("song.mp3")));
            assertFalse(Files.exists(first));
            assertFalse(Files.exists(second));
        } finally { executor.shutdownNow(); }
    }

    @Test void recognizesContentInsteadOfMisleadingDownloadSuffix() throws Exception {
        Path file = Files.write(directory.resolve("wrong.m4a"), new byte[]{'I','D','3',4});
        assertEquals(".mp3", DownloadFiles.detectedExtension(file, ".m4a"));
        Files.write(file, new byte[]{'f','L','a','C'});
        assertEquals(".flac", DownloadFiles.detectedExtension(file, ".mp3"));
    }
}
