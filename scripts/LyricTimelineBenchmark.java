import app.musicplayer.model.LyricLine;
import java.lang.management.*;
import java.lang.invoke.MethodHandles;
import java.time.Duration;
import java.util.*;
import java.util.function.LongToIntFunction;

/** Same immutable timestamps and queries; baseline uses the previous forward scan. */
public final class LyricTimelineBenchmark {
    private static volatile int sink;
    private static volatile Object retained;
    public static void main(String[] args) throws Exception {
        boolean indexed = args[0].equals("timeline");
        for (int count : new int[]{100, 1_000, 10_000}) {
            var lines = new ArrayList<LyricLine>();
            for (int i = 0; i < count; i++) lines.add(new LyricLine(Duration.ofMillis(i * 400L), "歌词 " + i));
            for (int batch = 1; batch <= 3; batch++) measure(List.copyOf(lines), indexed, batch);
        }
    }
    private static void measure(List<LyricLine> lines, boolean indexed, int batch) throws Exception {
        LongToIntFunction locate;
        if (indexed) {
            Class<?> type = Class.forName("app.musicplayer.lyrics.LyricTimeline");
            Object timeline = type.getConstructor(List.class).newInstance(lines);
            var advance = MethodHandles.publicLookup().unreflect(type.getMethod("advance", long.class)).bindTo(timeline);
            locate = millis -> {
                try { return (int) advance.invokeExact(millis); }
                catch (Throwable failure) { throw new AssertionError(failure); }
            };
        } else locate = millis -> {
            int selected = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).time().toMillis() <= millis) selected = i; else break;
            }
            return selected;
        };
        retained = new Object[]{lines, locate};
        for (int warm = 0; warm < 3; warm++) run(lines.size(), locate);
        System.gc();
        var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP).toList();
        pools.forEach(MemoryPoolMXBean::resetPeakUsage);
        var allocation = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long allocated = allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
        long started = System.nanoTime();
        run(lines.size(), locate);
        double elapsedMs = (System.nanoTime() - started) / 1e6;
        allocated = allocation.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
        long peak = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        System.gc();
        long stable = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.out.printf(Locale.ROOT,
                "mode=%s lines=%d batch=%d lookupMs=%.6f allocatedBytes=%d peakHeapBytes=%d stableHeapBytes=%d%n",
                indexed ? "timeline" : "linear", lines.size(), batch, elapsedMs / (lines.size() + 2_000), allocated, peak, stable);
    }
    private static void run(int count, LongToIntFunction locate) {
        for (int i = 0; i < count; i++) sink = locate.applyAsInt(i * 400L);
        var random = new Random(7);
        for (int i = 0; i < 2_000; i++) sink = locate.applyAsInt(random.nextInt(count) * 400L);
    }
}
