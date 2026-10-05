package app.musicplayer.android;

import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.lang.ref.WeakReference;
import org.json.JSONObject;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** Shared safety boundary for opt-in, disposable performance fixtures. */
final class BenchmarkSupport {
    private BenchmarkSupport() { }
    static void settleHeap() {
        // A sampling thread must be stopped first. Check collection, rather than assume a delay did it.
        WeakReference<Object> sentinel = collectionSentinel();
        for (int attempt = 0; attempt < 5; attempt++) {
            System.gc(); System.runFinalization(); SystemClock.sleep(100);
            if (sentinel.get() == null) {
                System.gc(); SystemClock.sleep(100);
                return;
            }
        }
        throw new AssertionError("Benchmark heap did not collect its sentinel");
    }
    private static WeakReference<Object> collectionSentinel() { return new WeakReference<>(new Object()); }
    static JSONObject runtimeStats() {
        // Public ART counters help distinguish collection/accounting changes from retained objects.
        return new JSONObject(Debug.getRuntimeStats());
    }
    static JSONObject heapAccounting() throws org.json.JSONException {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return new JSONObject().put("totalBytes", total).put("freeBytes", free).put("usedBytes", total - free);
    }
    static int batches() {
        int count = Integer.parseInt(InstrumentationRegistry.getArguments().getString("benchmarkBatches", "3"));
        assertTrue("Use one to three batches", count >= 1 && count <= 3);
        return count;
    }
    static Context requireEmulator() {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Run explicitly on a disposable emulator", "true".equals(args.getString("performanceLibrary"))
                && (Build.MODEL.startsWith("sdk_gphone") || Build.MODEL.contains("Emulator")));
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
    static Context databaseContext(Context context, File file) {
        return new ContextWrapper(context) {
            @Override public File getDatabasePath(String name) { return file; }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(file, factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return SQLiteDatabase.openOrCreateDatabase(file.getAbsolutePath(), factory, handler);
            }
        };
    }
    static void deleteFixture(Context context, File root) throws Exception {
        var owned = root.toPath().toAbsolutePath().normalize();
        assertTrue(root.getName().startsWith("qa-"));
        assertTrue(owned.getParent().equals(context.getCacheDir().toPath().toAbsolutePath().normalize()));
        try (var files = Files.walk(owned)) {
            for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
                assertTrue(file.startsWith(owned));
                Files.deleteIfExists(file);
            }
        }
    }
}
