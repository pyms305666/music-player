package app.musicplayer.android;

import static org.junit.Assert.*;

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

/**
 * AndroidTrackFiles 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：Android
 * 音频元数据与文件名规则。安全生成文件名，原子预留不冲突的目标文件，并在标签读取失败时保留文件名元数据。
 */
@RunWith(AndroidJUnit4.class)
public class AndroidTrackFilesTest {
    /** 验证并发文件名预留不覆盖已有歌曲或其他任务文件。 */
    @Test
    public void parallelReservationsNeverOverwriteExistingOrOtherTaskFiles() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "qa-file-reservation-" + UUID.randomUUID());
        assertTrue(root.mkdir());
        var workers = Executors.newFixedThreadPool(2);
        try {
            File original = new File(root, "same.mp3");
            Files.write(original.toPath(), new byte[] {9});
            List<Future<File>> requests = new ArrayList<>();
            for (int i = 0; i < 100; i++)
                requests.add(workers.submit(() -> AndroidTrackFiles.reserve(root, "same.mp3")));
            Set<File> unique = new HashSet<>();
            for (var request : requests) unique.add(request.get(10, TimeUnit.SECONDS));
            assertEquals(100, unique.size());
            assertFalse(unique.contains(original));
            assertArrayEquals(new byte[] {9}, Files.readAllBytes(original.toPath()));
            assertEquals("audio.mp3", AndroidTrackFiles.safeName(".."));
            assertFalse(AndroidTrackFiles.safeName("../a\nb.mp3").contains("/"));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
            BenchmarkSupport.deleteFixture(context, root);
        }
    }
}
