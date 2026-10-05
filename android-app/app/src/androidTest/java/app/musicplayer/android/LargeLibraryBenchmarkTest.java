package app.musicplayer.android;

import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.data.AndroidMusicDatabase;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.model.Track;
import app.musicplayer.playlist.SearchSnapshot;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

/** Opt-in measurement on disposable emulators; never opens the application's database. */
@RunWith(AndroidJUnit4.class)
public class LargeLibraryBenchmarkTest {
    private static volatile Object retained;

    @Test public void measureLibraryRestoreAndSearch() throws Exception {
        var arguments = InstrumentationRegistry.getArguments();
        Context context = BenchmarkSupport.requireEmulator();
        File root = new File(context.getCacheDir(), "qa-large-library-" + UUID.randomUUID());
        assertTrue(root.mkdir());
        JSONArray results = new JSONArray();
        try {
            for (int count : new int[]{100, 1_000, 10_000}) {
                File directory = new File(root, Integer.toString(count));
                assertTrue(directory.mkdir());
                Context isolated = BenchmarkSupport.databaseContext(context, new File(directory, "fixture.db"));
                try (var database = new AndroidMusicDatabase(isolated)) {
                    var sql = database.getWritableDatabase();
                    sql.beginTransaction();
                    try {
                        for (int i = 0; i < count; i++) {
                            File file = new File(directory, "Artist " + i + " - 歌曲 " + i + ".mp3");
                            assertTrue(file.createNewFile());
                            database.saveTrack(new TrackEntry(new Track(file.toPath()), i));
                        }
                        sql.setTransactionSuccessful();
                    } finally { sql.endTransaction(); }
                }
                // Warm identical restore/search branches in each independently started comparison process.
                warmup(isolated);
                for (int batch = 1; batch <= BenchmarkSupport.batches(); batch++) {
                    retained = null;
                    BenchmarkSupport.settleHeap();
                    results.put(measure(isolated, count, batch));
                }
            }
            JSONObject report = new JSONObject().put("label", arguments.getString("benchmarkLabel", "unspecified"))
                    .put("api", Build.VERSION.SDK_INT).put("model", Build.MODEL).put("results", results);
            Files.write(new File(context.getCacheDir(), "qa-large-library-results.json").toPath(),
                    report.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            retained = null;
            BenchmarkSupport.deleteFixture(context, root);
        }
    }

    private static SearchSnapshot<TrackEntry> restore(Context isolated) {
        try (var database = new AndroidMusicDatabase(isolated)) {
            var tracks = new ArrayList<>(database.loadTracks());
            tracks.sort(Comparator.comparing(entry -> entry.track().title().toLowerCase(Locale.ROOT)));
            return new SearchSnapshot<>(tracks.stream().map(entry -> new SearchSnapshot.Entry<>(entry,
                    entry.track().title(), entry.track().artist(), entry.fileName())).toList());
        }
    }

    private static void warmup(Context isolated) {
        // Returning removes the warmup frame and its snapshot roots even under ART's interpreter.
        for (int iteration = 0; iteration < 3; iteration++) {
            var snapshot = restore(isolated);
            for (int i = 0; i < 200; i++) query(snapshot);
        }
    }

    private static JSONObject measure(Context isolated, int count, int batch) throws Exception {
        JSONObject beforeStats = BenchmarkSupport.runtimeStats();
        AtomicLong peak = new AtomicLong(heap());
        var sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> peak.accumulateAndGet(heap(), Math::max), 0, 10, TimeUnit.MILLISECONDS);
        try {
            long started = System.nanoTime();
            var snapshot = restore(isolated);
            double startupMs = (System.nanoTime() - started) / 1e6;
            assertEquals(count, snapshot.filter("").size());
            for (int i = 0; i < 100; i++) query(snapshot);
            started = System.nanoTime();
            for (int i = 0; i < 100; i++) query(snapshot);
            double queryMs = (System.nanoTime() - started) / 1e6 / 500;
            peak.accumulateAndGet(heap(), Math::max);
            retained = snapshot;
            sampler.shutdownNow(); assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS));
            JSONObject afterWorkStats = BenchmarkSupport.runtimeStats();
            BenchmarkSupport.settleHeap();
            Debug.MemoryInfo memory = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memory);
            return new JSONObject().put("tracks", count).put("batch", batch).put("startupMs", startupMs)
                    .put("queryMs", queryMs).put("observedPeakHeapBytes", peak.get())
                    .put("stableHeapBytes", heap()).put("stablePssKiB", memory.getTotalPss())
                    .put("gcBeforeWork", beforeStats).put("gcAfterWork", afterWorkStats)
                    .put("gcAfterSettle", BenchmarkSupport.runtimeStats()).put("heapAccounting", BenchmarkSupport.heapAccounting());
        } finally { sampler.shutdownNow(); assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    private static long heap() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    private static void query(SearchSnapshot<TrackEntry> snapshot) {
        for (String query : List.of("歌曲", "ARTIST 9", ".mp3", "找不到", "歌曲 99")) retained = snapshot.filter(query);
    }
}
