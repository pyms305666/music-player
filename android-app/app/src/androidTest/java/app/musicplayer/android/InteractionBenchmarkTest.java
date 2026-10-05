package app.musicplayer.android;

import android.content.Context;
import android.graphics.Color;
import android.os.Debug;
import android.os.SystemClock;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.data.*;
import app.musicplayer.model.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Isolates database writes and lyric text updates; timings exclude import copies and frame rendering. */
@RunWith(AndroidJUnit4.class)
public class InteractionBenchmarkTest {
    private static final int IMPORT_COUNT = 500;
    private static final int LYRIC_UPDATES = 500;
    private boolean optimized() { return "optimized".equals(InstrumentationRegistry.getArguments().getString("benchmarkMode")); }

    @Test public void measureImportWrites() throws Exception {
        Context context = BenchmarkSupport.requireEmulator();
        File root = new File(context.getCacheDir(), "qa-import-benchmark-" + UUID.randomUUID());
        assertTrue(root.mkdir());
        List<TrackEntry> entries = new ArrayList<>();
        for (int i = 0; i < IMPORT_COUNT; i++) {
            File file = new File(root, "track-" + i + ".mp3");
            assertTrue(file.createNewFile());
            entries.add(new TrackEntry(new Track(file.toPath()), i));
        }
        JSONArray results = new JSONArray();
        try {
            for (int batch = 1; batch <= BenchmarkSupport.batches(); batch++) {
                Context isolated = BenchmarkSupport.databaseContext(context, new File(root, "batch-" + batch + ".db"));
                try (var database = new AndroidMusicDatabase(isolated)) {
                    database.saveTrack(entries.get(0));
                    database.removeTrack(entries.get(0));
                    JSONObject measured = measure(() -> writeImports(database, entries));
                    assertEquals(IMPORT_COUNT, database.loadTracks().size());
                    results.put(measured.put("batch", batch).put("tracks", IMPORT_COUNT));
                }
            }
            report(context, "qa-import-write-results.json", results);
        } finally { BenchmarkSupport.deleteFixture(context, root); }
    }

    private void writeImports(AndroidMusicDatabase database, List<TrackEntry> entries) throws Exception {
        if (!optimized()) { for (TrackEntry entry : entries) database.saveTrack(entry); return; }
        OptimizedBenchmarkWork.imports(database, entries);
    }

    @Test public void measureLyricTextUpdates() throws Exception {
        Context context = BenchmarkSupport.requireEmulator();
        List<LyricLine> lines = new ArrayList<>();
        String text = String.join("", Collections.nCopies(5, "中文歌词长行用于换行验证，保持两种实现的文字内容一致。"));
        for (int i = 0; i < 1_000; i++) lines.add(new LyricLine(Duration.ofSeconds(i), text + i));
        Lyrics lyrics = new Lyrics("fixture", List.copyOf(lines), true, "");
        JSONArray results = new JSONArray();
        for (int batch = 1; batch <= BenchmarkSupport.batches(); batch++) {
            AtomicReference<TextView> view = new AtomicReference<>();
            AtomicReference<ScrollView> scroll = new AtomicReference<>();
            main(() -> {
                var themed = new ContextThemeWrapper(context, R.style.Theme_SimpleMusicPlayer);
                View layout = LayoutInflater.from(themed).inflate(R.layout.activity_main, null);
                view.set(layout.findViewById(R.id.lyricsText));
                scroll.set(layout.findViewById(R.id.lyricsScroll));
                legacyText(view.get(), lyrics, -1);
            });
            AtomicReference<Object> presenterReference = new AtomicReference<>();
            if (optimized()) main(() -> presenterReference.set(OptimizedBenchmarkWork.presenter(view.get(), scroll.get())));
            Object presenter = presenterReference.get();
            main(() -> {
                if (presenter != null) OptimizedBenchmarkWork.show(presenter, lyrics);
                int width = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY);
                view.get().measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                view.get().layout(0, 0, 240, view.get().getMeasuredHeight());
            });
            AtomicReference<String> layoutBeforeSettle = new AtomicReference<>();
            try {
                JSONObject measured = measure(() -> main(() -> {
                    Object originalText = view.get().getText();
                    for (int i = 0; i < LYRIC_UPDATES; i++) {
                        if (presenter != null) OptimizedBenchmarkWork.update(presenter, i * 1_000L);
                        else legacyText(view.get(), lyrics, i);
                    }
                    if (presenter != null) assertSame(originalText, view.get().getText());
                }), () -> main(() -> {
                    layoutBeforeSettle.set(view.get().getLayout() == null ? "none"
                            : view.get().getLayout().getClass().getSimpleName());
                    // Settle final layout on both sides before comparing retained memory.
                    // Record StaticLayout/DynamicLayout separately rather than treating
                    // their different retained structures as an unlaid-out view.
                    int width = View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY);
                    view.get().measure(width, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    view.get().layout(0, 0, 240, view.get().getMeasuredHeight());
                    assertNotNull(view.get().getLayout());
                }));
                main(() -> {
                    try {
                        measured.put("finalLayout", view.get().getLayout().getClass().getSimpleName());
                        measured.put("layoutBeforeSettle", layoutBeforeSettle.get());
                        measured.put("layoutLines", view.get().getLayout().getLineCount());
                    } catch (JSONException failure) { throw new AssertionError(failure); }
                });
                results.put(measured.put("batch", batch).put("lines", lines.size()).put("updates", LYRIC_UPDATES));
            } finally {
                if (presenter != null) main(() -> {
                    try { ((AutoCloseable)presenter).close(); } catch (Exception failure) { throw new AssertionError(failure); }
                });
                main(() -> { view.get().setText(""); scroll.get().removeAllViews(); });
            }
        }
        report(context, "qa-lyric-text-results.json", results);
    }
    private static void legacyText(TextView view, Lyrics lyrics, int active) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        for (int i = 0; i < lyrics.lines().size(); i++) {
            if (i > 0) text.append('\n');
            int start = text.length();
            text.append(lyrics.lines().get(i).text());
            if (i == active) {
                text.setSpan(new ForegroundColorSpan(Color.rgb(240, 90, 60)), start, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        view.setText(text);
    }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private static JSONObject measure(Work work) throws Exception {
        return measure(work, () -> { });
    }
    private static JSONObject measure(Work work, Work settleVisualState) throws Exception {
        BenchmarkSupport.settleHeap();
        JSONObject beforeStats = BenchmarkSupport.runtimeStats();
        long beforeHeap = heap();
        AtomicLong peak = new AtomicLong(beforeHeap);
        var sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> peak.accumulateAndGet(heap(), Math::max), 0, 10, TimeUnit.MILLISECONDS);
        try {
            long started = System.nanoTime(); work.run();
            double elapsed = (System.nanoTime() - started) / 1e6;
            // Layout time is excluded from text-update timing, but its retained
            // memory belongs to both versions' stable/peak samples.
            settleVisualState.run();
            peak.accumulateAndGet(heap(), Math::max);
            sampler.shutdownNow(); assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS));
            JSONObject afterWorkStats = BenchmarkSupport.runtimeStats();
            BenchmarkSupport.settleHeap();
            JSONObject settledStats = BenchmarkSupport.runtimeStats();
            JSONObject settledHeap = BenchmarkSupport.heapAccounting();
            JSONObject dumpStats = null, dumpHeap = null;
            if ("true".equals(InstrumentationRegistry.getArguments().getString("dumpPerformanceHeap"))) {
                Context context = BenchmarkSupport.requireEmulator();
                Debug.dumpHprofData(new File(context.getCacheDir(), "qa-performance.hprof").getAbsolutePath());
                dumpStats = BenchmarkSupport.runtimeStats();
                dumpHeap = BenchmarkSupport.heapAccounting();
            }
            Debug.MemoryInfo memory = new Debug.MemoryInfo(); Debug.getMemoryInfo(memory);
            return new JSONObject().put("elapsedMs", elapsed).put("heapBeforeWorkBytes", beforeHeap).put("observedPeakHeapBytes", peak.get())
                    .put("stableHeapBytes", heap()).put("stablePssKiB", memory.getTotalPss())
                    .put("gcBeforeWork", beforeStats).put("gcAfterWork", afterWorkStats)
                    .put("gcAfterSettle", settledStats).put("heapAccounting", settledHeap)
                    .put("diagnosticHeapDump", dumpStats != null).put("gcAfterDump", dumpStats).put("heapAfterDump", dumpHeap);
        } finally { sampler.shutdownNow(); assertTrue(sampler.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    private void report(Context context, String name, JSONArray results) throws Exception {
        JSONObject report = new JSONObject().put("label", InstrumentationRegistry.getArguments().getString("benchmarkLabel", "unspecified"))
                .put("mode", optimized() ? "optimized" : "legacy").put("results", results);
        Files.write(new File(context.getCacheDir(), name).toPath(), report.toString(2).getBytes(StandardCharsets.UTF_8));
    }
    private static long heap() { return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(); }
    private static void main(Runnable work) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try { work.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
