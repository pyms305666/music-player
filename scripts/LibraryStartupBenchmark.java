import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.Track;
import app.musicplayer.playlist.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;

/** Measures database restore + sorting + index construction in a disposable directory. */
public final class LibraryStartupBenchmark {
    private static volatile Object retained;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Path.of(args[0]).toAbsolutePath(), "library-startup-");
        System.out.println("disposableData=" + root);
        for (int count : new int[]{100, 1_000, 10_000}) {
            Path directory = Files.createDirectory(root.resolve(Integer.toString(count)));
            List<Track> fixtures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Path path = Files.createFile(directory.resolve("Artist " + i + " - 歌曲 " + i + ".mp3"));
                fixtures.add(new Track(path));
            }
            Path databasePath = directory.resolve("library.db");
            try (var database = new MusicDatabase(databasePath)) { database.saveTracks(fixtures); }
            fixtures.clear();
            // File system and JDBC are warm, consistently for both source versions.
            restore(databasePath);
            for (int batch = 1; batch <= 3; batch++) {
                retained = null;
                System.gc();
                measure(count, batch, databasePath);
            }
        }
        // Retained temporary data allows inspecting the measurement, never touches app data.
    }
    private record State(SearchSnapshot<Track> snapshot, TrackLibraryService library) { }
    private static void measure(int count, int batch, Path databasePath) throws Exception {
        var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP).toList();
        pools.forEach(MemoryPoolMXBean::resetPeakUsage);
        long started = System.nanoTime();
        var state = restore(databasePath);
        double startupMs = (System.nanoTime() - started) / 1e6;
        for (int warm = 0; warm < 100; warm++) query(state.snapshot());
        started = System.nanoTime();
        for (int iteration = 0; iteration < 100; iteration++) query(state.snapshot());
        double queryMs = (System.nanoTime() - started) / 1e6 / 500;
        long peak = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        retained = state;
        System.gc();
        long stable = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.out.printf(Locale.ROOT,
                "tracks=%d batch=%d startupMs=%.3f queryMs=%.4f peakHeapBytes=%d stableHeapBytes=%d%n",
                count, batch, startupMs, queryMs, peak, stable);
    }
    private static State restore(Path file) throws Exception {
        List<Track> tracks;
        var library = new TrackLibraryService();
        try (var database = new MusicDatabase(file)) {
            tracks = new ArrayList<>(database.loadTracks().stream().filter(t -> Files.isRegularFile(t.path()))
                    .filter(t -> library.isSupportedAudio(t.path())).toList());
        }
        library.primeCreationTimes(tracks);
        tracks.sort(library.comparator(PlaylistSort.TITLE, SortDirection.ASCENDING));
        return new State(SearchSnapshot.ofTracks(tracks), library);
    }
    private static void query(SearchSnapshot<Track> snapshot) {
        for (String text : List.of("歌曲", "ARTIST 9", ".mp3", "找不到", "歌曲 99"))
            retained = snapshot.filter(text);
    }
}
