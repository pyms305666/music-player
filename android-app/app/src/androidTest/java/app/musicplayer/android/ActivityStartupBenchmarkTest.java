package app.musicplayer.android;

import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import app.musicplayer.android.data.*;
import app.musicplayer.android.ui.TrackAdapter;
import app.musicplayer.model.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Opt-in on the disposable emulator: actual Activity start through displayed library readiness. */
@RunWith(AndroidJUnit4.class)
public class ActivityStartupBenchmarkTest {
    @Test public void measureActivityAndLibraryReadiness() throws Exception {
        Context context = BenchmarkSupport.requireEmulator();
        org.junit.Assume.assumeTrue("Startup backup requires disposable API 35+", android.os.Build.VERSION.SDK_INT >= 35);
        File root = new File(context.getCacheDir(), "qa-startup-" + UUID.randomUUID()); assertTrue(root.mkdir());
        File backup = new File(root, "original.db"); JSONArray results = new JSONArray();
        boolean saved = false;
        try {
            // Back up every SQLite row, including entries filtered out by loadTracks().
            try (var database = new AndroidMusicDatabase(context)) {
                database.getWritableDatabase().execSQL("VACUUM INTO ?", new Object[]{backup.getAbsolutePath()}); saved = true;
            }
            for (int count : new int[]{100, 1_000, 10_000}) {
                seed(context, root, count);
                measure(count, 0); // Symmetric untimed Activity/class initialization before retained samples.
                for (int batch = 1; batch <= BenchmarkSupport.batches(); batch++) results.put(measure(count, batch));
            }
            Files.write(new File(context.getCacheDir(), "qa-activity-startup-results.json").toPath(), results.toString(2).getBytes(StandardCharsets.UTF_8));
        } finally {
            if (saved) restore(context, backup);
            BenchmarkSupport.deleteFixture(context, root);
        }
    }
    private static void seed(Context context, File root, int count) throws Exception {
        try (var database = new AndroidMusicDatabase(context)) {
            var db = database.getWritableDatabase(); db.beginTransaction();
            try {
                clear(db);
                for (int i = 0; i < count; i++) {
                    File file = new File(root, "歌曲 " + i + ".mp3");
                    if (!file.exists()) assertTrue(file.createNewFile());
                    TrackEntry entry = new TrackEntry(new Track(file.toPath()), i);
                    database.saveTrack(entry);
                    database.saveLyrics(entry, new Lyrics("fixture", List.of(new LyricLine(java.time.Duration.ZERO, "歌词")), true, "[00:00]歌词"), null);
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
    }
    private static JSONObject measure(int count, int batch) throws Exception {
        BenchmarkSupport.settleHeap(); JSONObject beforeStats = BenchmarkSupport.runtimeStats();
        AtomicLong peak = new AtomicLong(heap());
        var sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> peak.accumulateAndGet(heap(), Math::max), 0, 10, TimeUnit.MILLISECONDS);
        AtomicReference<ExecutorService> libraryWorker = new AtomicReference<>();
        long started = System.nanoTime();
        try (var scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicBoolean ready = new AtomicBoolean();
            for (int attempt = 0; attempt < 600 && !ready.get(); attempt++) {
                scenario.onActivity(activity -> {
                    TrackAdapter adapter = (TrackAdapter)field(activity, "trackAdapter");
                    ready.set(adapter.getItemCount() == count);
                    libraryWorker.set((ExecutorService)field(activity, "libraryExecutor"));
                });
                if (!ready.get()) SystemClock.sleep(20);
            }
            assertTrue("Actual Activity library did not become ready", ready.get());
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            double elapsed = (System.nanoTime() - started) / 1e6;
            peak.accumulateAndGet(heap(), Math::max); sampler.shutdownNow(); assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS));
            JSONObject afterWorkStats = BenchmarkSupport.runtimeStats();
            BenchmarkSupport.settleHeap(); Debug.MemoryInfo memory = new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
            return new JSONObject().put("tracks", count).put("batch", batch).put("activityLibraryReadyMs", elapsed)
                    .put("observedPeakHeapBytes", peak.get()).put("stableHeapBytes", heap()).put("stablePssKiB", memory.getTotalPss())
                    .put("gcBeforeWork", beforeStats).put("gcAfterWork", afterWorkStats)
                    .put("gcAfterSettle", BenchmarkSupport.runtimeStats()).put("heapAccounting", BenchmarkSupport.heapAccounting());
        } finally {
            sampler.shutdownNow(); assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS));
            if (libraryWorker.get() != null) assertTrue(libraryWorker.get().awaitTermination(30, TimeUnit.SECONDS));
        }
    }
    private static Object field(MainActivity activity, String name) {
        try { var field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void clear(android.database.sqlite.SQLiteDatabase db) {
        db.delete("lyrics", null, null); db.delete("tracks", null, null);
        if (hasImports(db)) db.delete("imports", null, null);
    }
    private static boolean hasImports(android.database.sqlite.SQLiteDatabase db) {
        try (var cursor = db.rawQuery("select 1 from sqlite_master where name='imports'", null)) { return cursor.moveToFirst(); }
    }
    private static void restore(Context context, File backup) {
        try (var database = new AndroidMusicDatabase(context)) {
            var db = database.getWritableDatabase(); db.execSQL("attach database ? as qa_backup", new Object[]{backup.getAbsolutePath()});
            try {
                db.beginTransaction();
                try {
                    clear(db);
                    for (String table : hasImports(db) ? List.of("tracks", "lyrics", "imports") : List.of("tracks", "lyrics"))
                        db.execSQL("insert into " + table + " select * from qa_backup." + table);
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            } finally { db.execSQL("detach database qa_backup"); }
        }
    }
    private static long heap() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
}
