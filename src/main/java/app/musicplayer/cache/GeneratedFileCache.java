package app.musicplayer.cache;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Bounded cache for direct, application-generated files. Leases protect active consumers. */
public final class GeneratedFileCache implements AutoCloseable {
    public enum Kind {
        ARTWORK(256L * 1024 * 1024, "[0-9a-f]{40}\\.(png|jpg|jpeg|webp|gif|bmp|img)"),
        PLAYBACK(512L * 1024 * 1024, "[0-9a-f]{40}\\.mp3");
        final long budget;
        final Pattern names;
        Kind(long budget, String names) { this.budget = budget; this.names = Pattern.compile(names); }
    }
    private record Candidate(Path path, BasicFileAttributes attributes) { }
    private final Path root;
    private final Kind kind;
    private final long budget;
    private final LongSupplier clock;
    private final Map<Path, Integer> pins = new HashMap<>();
    private final Set<CompletableFuture<Void>> cleanRequests = new HashSet<>();
    private final ScheduledExecutorService worker;
    private boolean closed;

    public GeneratedFileCache(Path root, Kind kind) { this(root, kind, kind.budget, System::currentTimeMillis); }
    public GeneratedFileCache(Path root, Kind kind, long budget, LongSupplier clock) {
        if (budget < 0) throw new IllegalArgumentException("Negative cache budget");
        this.root = root.toAbsolutePath().normalize(); this.kind = kind; this.budget = budget; this.clock = clock;
        worker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "generated-cache-" + kind.name().toLowerCase(Locale.ROOT));
            thread.setDaemon(true); return thread;
        });
        worker.scheduleWithFixedDelay(this::cleanSafely, 0, 1, TimeUnit.MINUTES);
    }
    public synchronized Lease acquire(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (closed) throw new IllegalStateException("Cache closed");
        if (!owns(normalized)) throw new IllegalArgumentException("Not a generated cache file");
        pins.merge(normalized, 1, Integer::sum);
        return new Lease(normalized);
    }
    public final class Lease implements AutoCloseable {
        private final Path path;
        private boolean released;
        private Lease(Path path) { this.path = path; }
        @Override public void close() {
            synchronized (GeneratedFileCache.this) {
                if (released) return;
                released = true;
                if (closed) return;
                // Keep the pin until its access timestamp is updated on the maintenance worker.
                worker.execute(() -> {
                    synchronized (GeneratedFileCache.this) {
                        touch(path);
                        pins.computeIfPresent(path, (key, count) -> count == 1 ? null : count - 1);
                    }
                    cleanSafely();
                });
            }
        }
    }
    public synchronized CompletableFuture<Void> cleanAsync() {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Cache closed"));
        CompletableFuture<Void> result = new CompletableFuture<>();
        cleanRequests.add(result);
        worker.execute(() -> {
            try { cleanSafely(); result.complete(null); }
            finally { synchronized (this) { cleanRequests.remove(result); } }
        });
        return result;
    }
    private boolean owns(Path path) {
        return root.equals(path.getParent()) && kind.names.matcher(path.getFileName().toString()).matches();
    }
    private boolean safeRoot() { return Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS); }
    private void touch(Path path) {
        try {
            if (safeRoot() && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                Files.setLastModifiedTime(path, FileTime.fromMillis(clock.getAsLong()));
        } catch (IOException ignored) { /* A missing cache file can be recreated on demand. */ }
    }
    private void cleanSafely() {
        try { clean(); } catch (IOException | RuntimeException ignored) { /* Retry on the next maintenance pass. */ }
    }
    private void clean() throws IOException {
        if (!safeRoot()) return;
        List<Candidate> files = new ArrayList<>(); long total = 0;
        try (var stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (!owns(path)) continue;
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isRegularFile()) { files.add(new Candidate(path, attrs)); total += attrs.size(); }
            }
        }
        files.sort(Comparator.comparing(file -> file.attributes().lastModifiedTime()));
        for (Candidate file : files) {
            if (total <= budget) break;
            synchronized (this) {
                if (closed || !safeRoot()) return;
                if (pins.containsKey(file.path())) continue;
                BasicFileAttributes now = Files.readAttributes(file.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!now.isRegularFile() || now.size() != file.attributes().size()
                        || !now.lastModifiedTime().equals(file.attributes().lastModifiedTime())
                        || !Objects.equals(now.fileKey(), file.attributes().fileKey())) continue;
                if (Files.deleteIfExists(file.path())) total -= now.size();
            }
        }
    }
    @Override public void close() {
        List<CompletableFuture<Void>> pending;
        synchronized (this) {
            if (closed) return;
            closed = true; pins.clear();
            pending = List.copyOf(cleanRequests); cleanRequests.clear(); worker.shutdownNow();
        }
        pending.forEach(result -> result.cancel(false));
    }
}
