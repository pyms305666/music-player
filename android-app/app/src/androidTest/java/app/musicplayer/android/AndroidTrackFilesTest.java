package app.musicplayer.android;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.data.AndroidTrackFiles;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AndroidTrackFilesTest {
    @Test public void parallelReservationsNeverOverwriteExistingOrOtherTaskFiles() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "qa-file-reservation-" + UUID.randomUUID()); assertTrue(root.mkdir());
        var workers = Executors.newFixedThreadPool(2);
        try {
            File original = new File(root, "same.mp3"); Files.write(original.toPath(), new byte[]{9});
            List<Future<File>> requests = new ArrayList<>();
            for (int i = 0; i < 100; i++) requests.add(workers.submit(() -> AndroidTrackFiles.reserve(root, "same.mp3")));
            Set<File> unique = new HashSet<>(); for (var request : requests) unique.add(request.get(10, TimeUnit.SECONDS));
            assertEquals(100, unique.size()); assertFalse(unique.contains(original));
            assertArrayEquals(new byte[]{9}, Files.readAllBytes(original.toPath()));
            assertEquals("audio.mp3", AndroidTrackFiles.safeName(".."));
            assertFalse(AndroidTrackFiles.safeName("../a\nb.mp3").contains("/"));
        } finally { workers.shutdownNow(); assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS)); BenchmarkSupport.deleteFixture(context, root); }
    }
}
