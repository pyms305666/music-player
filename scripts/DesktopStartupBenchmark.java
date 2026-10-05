import app.musicplayer.MusicPlayerApp;
import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.Track;
import javafx.application.Platform;
import javafx.stage.Stage;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual JavaFX application initialization and library readiness, with an already initialized runtime. */
public final class DesktopStartupBenchmark {
    private static Object retained;
    private static Path ROOT;
    public static void main(String[] args) throws Exception {
        int count = Integer.parseInt(args[0]);
        ROOT = Files.createTempDirectory(Path.of(args[1]).toAbsolutePath().normalize(), "desktop-startup-");
        System.setProperty("musicplayer.data-dir", ROOT.toString());
        System.setProperty("musicplayer.preferences-node", "/app/musicplayer/qa/round3-startup-" + UUID.randomUUID());
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < count; i++) tracks.add(new Track(Files.createFile(ROOT.resolve("Artist " + i + " - 歌曲 " + i + ".mp3"))));
        try (var database = new MusicDatabase(ROOT.resolve("music-player.db"))) { database.saveTracks(tracks); }
        tracks.clear(); Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            for (int batch = 1; batch <= 3; batch++) { retained = null; System.gc(); Thread.sleep(100); measure(count, batch); }
        } finally {
            Platform.exit();
            java.util.prefs.Preferences.userRoot().node(System.getProperty("musicplayer.preferences-node")).removeNode();
        }
    }
    private static void measure(int count, int batch) throws Exception {
        var pools = ManagementFactory.getMemoryPoolMXBeans().stream().filter(pool -> pool.getType() == MemoryType.HEAP).toList();
        pools.forEach(MemoryPoolMXBean::resetPeakUsage); long started = System.nanoTime();
        Object[] running = fx(() -> {
            MusicPlayerApp app = new MusicPlayerApp();
            Class<?> parameters = Class.forName("com.sun.javafx.application.ParametersImpl");
            Object value = parameters.getConstructor(List.class).newInstance(List.of());
            parameters.getMethod("registerParameters", javafx.application.Application.class, javafx.application.Application.Parameters.class).invoke(null, app, value);
            Stage stage = new Stage(); stage.setOpacity(0); app.start(stage); return new Object[]{app, stage};
        });
        try {
            MusicPlayerApp app = (MusicPlayerApp)running[0]; Stage stage = (Stage)running[1];
            boolean ready = false;
            for (int attempt = 0; attempt < 1_500 && !ready; attempt++) {
                ready = fx(() -> ((List<?>)field(app, "tracks")).size() == count && ((List<?>)field(app, "filteredTracks")).size() == count);
                if (!ready) Thread.sleep(20);
            }
            if (!ready) throw new AssertionError("Application library did not become ready");
            fx(() -> { stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout(); return null; });
            double elapsed = (System.nanoTime() - started) / 1e6;
            long peak = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
            retained = app; System.gc(); Thread.sleep(100);
            long stable = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
            System.out.printf(Locale.ROOT, "tracks=%d batch=%d applicationLibraryReadyMs=%.3f peakHeapBytes=%d stableHeapBytes=%d%n", count, batch, elapsed, peak, stable);
        } finally {
            fx(() -> { ((MusicPlayerApp)running[0]).stop(); ((Stage)running[1]).close(); return null; });
            ((ExecutorService)field(running[0], "libraryExecutor")).awaitTermination(30, TimeUnit.SECONDS);
        }
    }
    private static Object field(Object app, String name) throws Exception {
        var field = MusicPlayerApp.class.getDeclaredField(name); field.setAccessible(true); return field.get(app);
    }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
    private static <T> T fx(Work<T> work) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(() -> { try { result.complete(work.run()); } catch (Throwable error) { result.completeExceptionally(error); } });
        return result.get(30, TimeUnit.SECONDS);
    }
}
