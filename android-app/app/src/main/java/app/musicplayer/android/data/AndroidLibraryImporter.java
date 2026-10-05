package app.musicplayer.android.data;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

/** Owns URI deduplication, copied files and transactional batches; no UI or executor ownership. */
public final class AndroidLibraryImporter implements AutoCloseable {
    public static final long PROGRESS_INTERVAL_NANOS = 200_000_000L;
    public enum FailureReason { READ_COPY, DATABASE, CANCELLED, CLEANUP }
    public record Failure(FailureReason reason, String detail) { }
    public record Progress(int total, int processed, int success, int duplicates, List<Failure> failures, boolean finished) {
        public Progress { failures = List.copyOf(failures); }
    }
    @FunctionalInterface public interface Copier { TrackEntry copy(Uri uri, BooleanSupplier cancelled) throws Exception; }
    private final AndroidMusicDatabase database;
    private final Copier copier;
    private final Executor worker, delivery;
    private final LongSupplier clock;
    private final Set<String> pending = new HashSet<>();
    private volatile boolean closed;

    public AndroidLibraryImporter(Context context, File directory, AndroidMusicDatabase database, Executor worker, Executor delivery) {
        this(database, (uri, cancelled) -> copy(context, directory, uri, cancelled), worker, delivery, System::nanoTime);
    }
    public AndroidLibraryImporter(AndroidMusicDatabase database, Copier copier, Executor worker, Executor delivery, LongSupplier clock) {
        this.database = database; this.copier = copier; this.worker = worker; this.delivery = delivery; this.clock = clock;
    }
    public synchronized void submit(List<Uri> uris, Consumer<Progress> listener) {
        if (closed) return;
        List<Uri> input = List.copyOf(uris);
        List<Uri> accepted = new ArrayList<>(); List<String> keys = new ArrayList<>();
        int duplicates = 0;
        for (Uri uri : input) {
            String key = uri.normalizeScheme().toString();
            if (pending.add(key)) { accepted.add(uri); keys.add(key); } else duplicates++;
        }
        int repeated = duplicates;
        try { worker.execute(() -> run(input.size(), accepted, keys, repeated, listener)); }
        catch (RuntimeException rejected) { pending.removeAll(keys); throw rejected; }
    }
    private final class Session {
        final int total; final Consumer<Progress> listener;
        int processed, success, duplicates;
        long lastProgress;
        final List<Failure> failures = new ArrayList<>();
        Session(int total, int duplicates, Consumer<Progress> listener) {
            this.total = total; this.processed = this.duplicates = duplicates; this.listener = listener;
            lastProgress = clock.getAsLong();
        }
        void publish(boolean finished) {
            long now = clock.getAsLong();
            if (!finished && now - lastProgress < PROGRESS_INTERVAL_NANOS) return;
            lastProgress = now;
            Progress progress = new Progress(total, processed, success, duplicates, failures, finished);
            delivery.execute(() -> { if (!closed) listener.accept(progress); });
        }
        void failure(FailureReason reason, Exception error) {
            failures.add(new Failure(reason, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }
        void discard(TrackEntry entry) {
            try { Files.deleteIfExists(entry.track().path()); }
            catch (IOException error) { failure(FailureReason.CLEANUP, error); }
        }
        void commit(List<AndroidMusicDatabase.ImportedTrack> batch) {
            if (batch.isEmpty()) return;
            processed += batch.size();
            if (closed) {
                for (var item : batch) { discard(item.entry()); failures.add(new Failure(FailureReason.CANCELLED, "导入已取消")); }
            } else {
                try {
                    var result = database.saveImportedTracks(batch);
                    success += result.added().size(); duplicates += result.duplicates().size();
                    result.duplicates().forEach(this::discard);
                } catch (RuntimeException error) {
                    for (var item : batch) { discard(item.entry()); failure(FailureReason.DATABASE, error); }
                }
            }
            batch.clear(); publish(false);
        }
    }
    private void run(int total, List<Uri> uris, List<String> keys, int duplicates, Consumer<Progress> listener) {
        Session session = new Session(total, duplicates, listener);
        List<AndroidMusicDatabase.ImportedTrack> batch = new ArrayList<>();
        try {
            for (int index = 0; index < uris.size(); index++) {
                if (closed) break;
                String key = keys.get(index);
                boolean imported;
                try { imported = database.hasImported(key); }
                catch (RuntimeException error) {
                    session.processed++; session.failure(FailureReason.DATABASE, error); session.publish(false); continue;
                }
                try {
                    if (imported) { session.duplicates++; session.processed++; }
                    else batch.add(new AndroidMusicDatabase.ImportedTrack(key, copier.copy(uris.get(index), () -> closed)));
                } catch (Exception error) { session.processed++; session.failure(FailureReason.READ_COPY, error); }
                if (batch.size() == AndroidMusicDatabase.IMPORT_BATCH_SIZE) session.commit(batch);
                session.publish(false);
            }
            session.commit(batch);
            session.publish(true);
        } finally {
            // Unexpected callback/executor failures must not leave copied, uncommitted files behind.
            batch.forEach(item -> session.discard(item.entry()));
            synchronized (this) { pending.removeAll(keys); }
        }
    }
    private static TrackEntry copy(Context context, File directory, Uri uri, BooleanSupplier cancelled) throws Exception {
        try { context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (RuntimeException ignored) { /* Some providers grant only transient read permission. */ }
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        File target = AndroidTrackFiles.reserve(directory, name);
        boolean complete = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(target)) {
                if (input == null) throw new IOException("无法读取文件");
                byte[] buffer = new byte[16_384]; int size;
                while ((size = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new IOException("导入已取消");
                    output.write(buffer, 0, size);
                }
            }
            TrackEntry entry = AndroidTrackFiles.entry(target, System.currentTimeMillis());
            complete = true; return entry;
        } finally { if (!complete) Files.deleteIfExists(target.toPath()); }
    }
    @Override public synchronized void close() { closed = true; }
}
