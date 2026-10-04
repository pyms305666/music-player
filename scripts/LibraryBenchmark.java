import app.musicplayer.model.Track;
import app.musicplayer.playlist.TrackLibraryService;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.*;

/** Disposable synthetic library: never opens the application's database. */
public final class LibraryBenchmark {
    private static volatile Object sink;
    public static void main(String[] args) throws Exception {
        boolean indexed = args.length > 0 && args[0].equals("indexed");
        var allocation = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        for (int count : new int[]{100, 1000, 10000}) {
            var tracks = new ArrayList<Track>();
            for (int i = 0; i < count; i++) {
                var track = new Track(Path.of("benchmark", "Artist " + i + " - 歌曲 " + i + ".mp3"));
                tracks.add(track);
            }
            var service = new TrackLibraryService();
            var constructor = indexed ? Class.forName("app.musicplayer.playlist.SearchSnapshot").getMethod("ofTracks", List.class) : null;
            long started = System.nanoTime();
            Object snapshot = indexed ? constructor.invoke(null, tracks) : null;
            double startup = (System.nanoTime() - started) / 1e6;
            var filter = indexed ? snapshot.getClass().getMethod("filter", String.class) : null;
            Runnable work = () -> {
                try {
                    for (String query : List.of("歌曲", "ARTIST 9", ".mp3", "找不到", "歌曲 99")) {
                        sink = indexed ? filter.invoke(snapshot, query)
                                : tracks.stream().filter(track -> service.matches(track, query)).toList();
                    }
                } catch (Exception failure) { throw new RuntimeException(failure); }
            };
            for (int warm = 0; warm < 100; warm++) work.run();
            for (int batch = 1; batch <= 3; batch++) {
                System.gc();
                var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                        .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP).toList();
                pools.forEach(pool -> pool.resetPeakUsage());
                long before = allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
                started = System.nanoTime();
                for (int iteration = 0; iteration < 100; iteration++) work.run();
                double ms = (System.nanoTime() - started) / 1e6;
                long bytes = allocation.getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
                long peak = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
                System.gc();
                long retained = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                System.out.printf(Locale.ROOT,
                    "mode=%s tracks=%d batch=%d queryMs=%.4f allocatedBytes=%d peakHeapBytes=%d stableHeapBytes=%d snapshotMs=%.4f%n",
                    indexed ? "indexed" : "legacy", count, batch, ms / 500, bytes, peak, retained, startup);
            }
        }
    }
}
