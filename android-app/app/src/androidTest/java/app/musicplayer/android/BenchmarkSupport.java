package app.musicplayer.android;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Debug;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.Comparator;

/**
 * Android 测试共用辅助。生成和清理音频或曲库夹具、测量资源和时间，为设备基准及回归提供一致环境。 Shared safety boundary for opt-in, disposable
 * performance fixtures.
 */
final class BenchmarkSupport {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private BenchmarkSupport() {}

    /** 设置堆内存，供本文件的主流程使用。 */
    static void settleHeap() {
        // A sampling thread must be stopped first. Check collection, rather than assume a delay did
        // it.
        WeakReference<Object> sentinel = collectionSentinel();
        for (int attempt = 0; attempt < 5; attempt++) {
            System.gc();
            System.runFinalization();
            SystemClock.sleep(100);
            if (sentinel.get() == null) {
                System.gc();
                SystemClock.sleep(100);
                return;
            }
        }
        throw new AssertionError("Benchmark heap did not collect its sentinel");
    }

    /** 识别集合歌单所需的特殊标记。 */
    private static WeakReference<Object> collectionSentinel() {
        return new WeakReference<>(new Object());
    }

    /** 收集运行时间、内存及运行时统计。 */
    static JSONObject runtimeStats() {
        // Public ART counters help distinguish collection/accounting changes from retained objects.
        return new JSONObject(Debug.getRuntimeStats());
    }

    /** 读取运行时堆内存占用，用于比较测量前后变化。 */
    static JSONObject heapAccounting() throws org.json.JSONException {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return new JSONObject()
                .put("totalBytes", total)
                .put("freeBytes", free)
                .put("usedBytes", total - free);
    }

    /** 生成批次数量或执行分批测量。 */
    static int batches() {
        int count =
                Integer.parseInt(
                        InstrumentationRegistry.getArguments().getString("benchmarkBatches", "3"));
        assertTrue("Use one to three batches", count >= 1 && count <= 3);
        return count;
    }

    /** 确认运行设备是测试要求的模拟器，避免把基准操作用于个人设备。 */
    static Context requireEmulator() {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue(
                "Run explicitly on a disposable emulator",
                "true".equals(args.getString("performanceLibrary"))
                        && (Build.MODEL.startsWith("sdk_gphone")
                                || Build.MODEL.contains("Emulator")));
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    /** 为测试提供隔离数据库位置的 Context。 */
    static Context databaseContext(Context context, File file) {
        return new ContextWrapper(context) {
            /** 取得路径，供本文件的主流程使用。 */
            @Override
            public File getDatabasePath(String name) {
                return file;
            }

            /** 在测试约定的位置打开或创建数据库。 */
            @Override
            public SQLiteDatabase openOrCreateDatabase(
                    String name, int mode, SQLiteDatabase.CursorFactory factory) {
                return SQLiteDatabase.openOrCreateDatabase(file, factory);
            }

            /** 在测试约定的位置打开或创建数据库。 */
            @Override
            public SQLiteDatabase openOrCreateDatabase(
                    String name,
                    int mode,
                    SQLiteDatabase.CursorFactory factory,
                    DatabaseErrorHandler handler) {
                return SQLiteDatabase.openOrCreateDatabase(
                        file.getAbsolutePath(), factory, handler);
            }
        };
    }

    /** 删除测试夹具，供本文件的主流程使用。 */
    static void deleteFixture(Context context, File root) throws Exception {
        var owned = root.toPath().toAbsolutePath().normalize();
        assertTrue(root.getName().startsWith("qa-"));
        assertTrue(
                owned.getParent()
                        .equals(context.getCacheDir().toPath().toAbsolutePath().normalize()));
        try (var files = Files.walk(owned)) {
            for (var file : files.sorted(Comparator.reverseOrder()).toList()) {
                assertTrue(file.startsWith(owned));
                Files.deleteIfExists(file);
            }
        }
    }
}
