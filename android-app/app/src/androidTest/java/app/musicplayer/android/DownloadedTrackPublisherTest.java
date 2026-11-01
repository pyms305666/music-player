package app.musicplayer.android;

import static org.junit.Assert.*;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import app.musicplayer.android.data.DownloadedTrackPublisher;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.model.OnlineTrackInfo;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/** 在独立模拟器验证单曲保存，只查询和清理本用例随机命名的媒体记录。 */
@RunWith(AndroidJUnit4.class)
public class DownloadedTrackPublisherTest {
    private Context context;
    private File root;
    private String name;
    private final List<Uri> published = new ArrayList<>();

    /** 创建专用临时目录及不会与用户歌曲同名的文件名。 */
    @Before
    public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = new File(context.getCacheDir(), "qa-publisher-" + UUID.randomUUID());
        assertTrue(root.mkdir());
        name = "qa-" + UUID.randomUUID() + ".wav";
    }

    /** 仅删除本用例保存的媒体 URI 和临时目录。 */
    @After
    public void cleanup() throws Exception {
        for (Uri uri : published) context.getContentResolver().delete(uri, null, null);
        if (root != null) BenchmarkSupport.deleteFixture(context, root);
    }

    /** 重复文件名生成独立可读音频，保存成功后移除各自的暂存源文件。 */
    @Test
    public void repeatedNamesPreserveEachPublishedPayload() throws Exception {
        DownloadedTrackPublisher publisher =
                new DownloadedTrackPublisher(context, new File(root, "private"));
        Set<String> locations = new HashSet<>();
        for (int index = 0; index < 3; index++) {
            File folder = new File(root, "transfer-" + index);
            assertTrue(folder.mkdir());
            File source = new File(folder, name);
            byte[] payload = new byte[32768];
            payload[0] = 'R';
            payload[1] = 'I';
            payload[2] = 'F';
            payload[3] = 'F';
            payload[40] = (byte) index;
            Files.write(source.toPath(), payload);
            TrackEntry entry = publisher.publish(source, online(), false);
            assertTrue(locations.add(entry.location()));
            assertFalse(source.exists());
            if (entry.storageType() == TrackEntry.StorageType.MEDIA_STORE) {
                Uri uri = Uri.parse(entry.location());
                published.add(uri);
                try (var input = context.getContentResolver().openInputStream(uri)) {
                    assertNotNull(input);
                    assertArrayEquals(payload, input.readAllBytes());
                }
            } else {
                assertArrayEquals(payload, Files.readAllBytes(new File(entry.location()).toPath()));
            }
        }
    }

    /** 创建媒体条目后源文件读取失败，必须撤销未完成的 MediaStore 记录。 */
    @Test
    public void missingSourceRollsBackPendingMediaStoreEntry() throws Exception {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 29);
        var publisher = new DownloadedTrackPublisher(context, new File(root, "private"));
        assertThrows(
                IOException.class, () -> publisher.publish(new File(root, name), online(), false));
        try (var rows =
                context.getContentResolver()
                        .query(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                new String[] {MediaStore.Audio.Media._ID},
                                MediaStore.Audio.Media.DISPLAY_NAME + "=?",
                                new String[] {name},
                                null)) {
            assertNotNull(rows);
            assertEquals(0, rows.getCount());
        }
    }

    /** 构造不依赖真实网站的在线元数据。 */
    private OnlineTrackInfo online() {
        return new OnlineTrackInfo("fixture", "测试歌曲", "测试歌手", "", "", name, "");
    }
}
