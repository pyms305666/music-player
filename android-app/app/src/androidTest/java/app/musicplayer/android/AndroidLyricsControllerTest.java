package app.musicplayer.android;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import app.musicplayer.android.data.AndroidMusicDatabase;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.Track;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 用独立数据库、旁车文件及可控请求检查歌词加载，完全不访问个人曲库。 */
@RunWith(AndroidJUnit4.class)
public class AndroidLyricsControllerTest {
    private Context context;
    private File root;
    private AndroidMusicDatabase database;

    /** 每项用例创建自己的数据库目录。 */
    @Before
    public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = new File(context.getCacheDir(), "qa-lyrics-controller-" + UUID.randomUUID());
        assertTrue(root.mkdir());
        database =
                new AndroidMusicDatabase(
                        BenchmarkSupport.databaseContext(context, new File(root, "test.db")));
    }

    /** 先关闭数据库，再清理本用例夹具。 */
    @After
    public void cleanup() throws Exception {
        if (database != null) database.close();
        if (root != null) BenchmarkSupport.deleteFixture(context, root);
    }

    /** 数据库优先于旁车文件，手动刷新仍会跳过二者并覆盖缓存。 */
    @Test
    public void cacheWinsAndManualRefreshReplacesIt() throws Exception {
        TrackEntry entry = entry("song.mp3");
        database.saveLyrics(entry, Lyrics.empty("cached"), null);
        Files.write(
                new File(root, "song.lrc").toPath(),
                "[00:01]local".getBytes(StandardCharsets.UTF_8));
        List<Lyrics> displayed = new ArrayList<>();
        List<CompletableFuture<LyricsLookupResult>> requests = new ArrayList<>();
        try (var controller = controller(Runnable::run, displayed, requests)) {
            controller.load(entry);
            assertEquals("cached", displayed.get(displayed.size() - 1).source());
            assertTrue(requests.isEmpty());
            controller.refresh(entry);
            requests.get(0).complete(LyricsLookupResult.lyricsOnly(Lyrics.empty("online")));
            assertEquals("online", displayed.get(displayed.size() - 1).source());
            assertEquals("online", database.loadLyrics(entry).source());
        }
    }

    /** UTF-8 和旧中文 GB18030 旁车均可读取，MediaStore 歌曲不猜测旁车路径。 */
    @Test
    public void sidecarEncodingAndMediaStoreRulesArePreserved() throws Exception {
        TrackEntry entry = entry("song.mp3");
        File file = new File(root, "song.lrc");
        Files.write(file.toPath(), "[00:01]中文歌词".getBytes(StandardCharsets.UTF_8));
        assertEquals(file, AndroidLyricsController.localLrcFile(entry));
        assertEquals("[00:01]中文歌词", AndroidLyricsController.readLyricsText(file));
        Files.write(file.toPath(), "[00:01]旧中文歌词".getBytes(Charset.forName("GB18030")));
        assertEquals("[00:01]旧中文歌词", AndroidLyricsController.readLyricsText(file));
        assertNull(
                AndroidLyricsController.localLrcFile(
                        TrackEntry.mediaStore(
                                entry.track(),
                                1,
                                "content://media/external/audio/media/1",
                                "song.mp3")));
        List<Lyrics> displayed = new ArrayList<>();
        List<CompletableFuture<LyricsLookupResult>> requests = new ArrayList<>();
        try (var controller = controller(Runnable::run, displayed, requests)) {
            controller.load(entry);
            assertEquals("旧中文歌词", displayed.get(displayed.size() - 1).lines().get(0).text());
            assertTrue(requests.isEmpty());
            assertNotNull(database.loadLyrics(entry));
        }
    }

    /** 旧结果已排队仍不能覆盖新请求，关闭也会使等待中的 UI 结果失效。 */
    @Test
    public void queuedOldResultsAndClosedViewAreIgnored() throws Exception {
        TrackEntry first = entry("first.mp3");
        TrackEntry second = entry("second.mp3");
        ArrayDeque<Runnable> ui = new ArrayDeque<>();
        List<Lyrics> displayed = new ArrayList<>();
        List<CompletableFuture<LyricsLookupResult>> requests = new ArrayList<>();
        try (var controller = controller(ui::add, displayed, requests)) {
            controller.refresh(first);
            requests.get(0).complete(LyricsLookupResult.lyricsOnly(Lyrics.empty("old")));
            controller.refresh(second);
            requests.get(1).complete(LyricsLookupResult.lyricsOnly(Lyrics.empty("new")));
            while (!ui.isEmpty()) ui.remove().run();
            assertEquals("new", displayed.get(displayed.size() - 1).source());
            assertFalse(displayed.stream().anyMatch(value -> value.source().equals("old")));
            controller.refresh(second);
            requests.get(2).complete(LyricsLookupResult.lyricsOnly(Lyrics.empty("after-close")));
            controller.close();
            while (!ui.isEmpty()) ui.remove().run();
            assertFalse(displayed.stream().anyMatch(value -> value.source().equals("after-close")));
        }
    }

    /** 创建 FILE 歌曲，仅用于本用例数据库和旁车规则。 */
    private TrackEntry entry(String name) throws Exception {
        File audio = new File(root, name);
        Files.write(audio.toPath(), new byte[] {1});
        return new TrackEntry(new Track(audio.toPath()), 1);
    }

    /** 使用即时 I/O 和可控在线 Future；只把 UI 回调交给指定队列。 */
    private AndroidLyricsController controller(
            java.util.concurrent.Executor ui,
            List<Lyrics> displayed,
            List<CompletableFuture<LyricsLookupResult>> requests) {
        return new AndroidLyricsController(
                database,
                (track, duration) -> {
                    var future = new CompletableFuture<LyricsLookupResult>();
                    requests.add(future);
                    return future;
                },
                Runnable::run,
                ui,
                () -> 1000,
                new AndroidLyricsController.Callbacks(
                        ignored -> {}, displayed::add, ignored -> {}, ignored -> {}));
    }
}
