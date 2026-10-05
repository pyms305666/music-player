package app.musicplayer.android;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.data.*;
import app.musicplayer.model.Track;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AndroidLibraryImporterTest {
    private Context context; private File root; private AndroidMusicDatabase database;
    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        root = new File(context.getCacheDir(), "qa-import-tests-" + UUID.randomUUID()); assertTrue(root.mkdir());
        database = new AndroidMusicDatabase(BenchmarkSupport.databaseContext(context, new File(root, "fixture.db")));
    }
    @After public void cleanup() throws Exception { database.close(); BenchmarkSupport.deleteFixture(context, root); }
    private TrackEntry copy(Uri uri) throws Exception {
        File file = AndroidTrackFiles.reserve(root, uri.getLastPathSegment() + ".mp3");
        Files.write(file.toPath(), new byte[]{1, 2, 3}); return new TrackEntry(new Track(file.toPath()), 1);
    }
    private List<Uri> uris(int count) {
        List<Uri> result = new ArrayList<>(); for (int i = 0; i < count; i++) result.add(Uri.parse("content://qa-import/" + i)); return result;
    }
    @Test public void batchesAndCompletedDuplicatesPreserveOriginalSongs() {
        AtomicLong clock = new AtomicLong(); AtomicInteger copies = new AtomicInteger();
        List<AndroidLibraryImporter.Progress> progress = new ArrayList<>();
        try (var importer = new AndroidLibraryImporter(database, (uri, cancelled) -> {
            copies.incrementAndGet(); clock.addAndGet(10_000_000); return copy(uri);
        }, Runnable::run, Runnable::run, clock::get)) {
            List<Uri> input = uris(120); input.add(input.get(0));
            importer.submit(input, progress::add);
            var finalResult = progress.get(progress.size() - 1);
            assertEquals(120, finalResult.success()); assertEquals(1, finalResult.duplicates());
            assertEquals(121, finalResult.processed()); assertTrue(finalResult.finished());
            assertTrue(progress.size() <= 7); assertEquals(120, database.loadTracks().size());
            progress.clear(); importer.submit(input, progress::add);
            assertEquals(121, progress.get(0).duplicates()); assertEquals(120, copies.get());
            assertEquals(120, database.loadTracks().size());
        }
    }
    @Test public void queuedRepeatedSubmissionIsMergedAndCloseDoesNotStopUnrelatedTasks() {
        ArrayDeque<Runnable> queue = new ArrayDeque<>(); List<AndroidLibraryImporter.Progress> results = new ArrayList<>();
        AtomicInteger copies = new AtomicInteger();
        var importer = new AndroidLibraryImporter(database, (uri, cancelled) -> { copies.incrementAndGet(); return copy(uri); },
                queue::add, Runnable::run, System::nanoTime);
        importer.submit(uris(2), results::add); importer.submit(uris(2), results::add);
        queue.remove().run(); queue.remove().run(); assertEquals(2, copies.get()); assertEquals(2, results.get(1).duplicates());
        importer.submit(List.of(Uri.parse("content://qa-import/new")), results::add);
        importer.close(); AtomicBoolean unrelated = new AtomicBoolean(); queue.add(() -> unrelated.set(true));
        queue.remove().run(); queue.remove().run(); assertTrue(unrelated.get()); assertEquals(2, copies.get());
    }
    @Test public void readFailureReportsReasonAndCommitsOtherFiles() {
        List<AndroidLibraryImporter.Progress> results = new ArrayList<>();
        try (var importer = new AndroidLibraryImporter(database, (uri, cancelled) -> {
            if (uri.getLastPathSegment().equals("1")) throw new IOException("permission denied"); return copy(uri);
        }, Runnable::run, Runnable::run, System::nanoTime)) {
            importer.submit(uris(3), results::add);
            assertEquals(2, results.get(0).success()); assertEquals(1, results.get(0).failures().size());
            assertEquals(AndroidLibraryImporter.FailureReason.READ_COPY, results.get(0).failures().get(0).reason());
            assertEquals(2, database.loadTracks().size());
        }
    }
    @Test public void databaseFailureRollsBackWholeBatchAndDeletesOnlyItsCopies() throws Exception {
        TrackEntry existing = copy(Uri.parse("content://qa-import/existing")); database.saveTrack(existing);
        database.getWritableDatabase().execSQL("create trigger reject_import before insert on tracks when new.file_name='2.mp3' begin select raise(abort,'fixture failure'); end");
        List<AndroidLibraryImporter.Progress> results = new ArrayList<>();
        try (var importer = new AndroidLibraryImporter(database, (uri, cancelled) -> copy(uri), Runnable::run, Runnable::run, System::nanoTime)) {
            importer.submit(uris(3), results::add);
            assertEquals(0, results.get(0).success()); assertEquals(3, results.get(0).failures().size());
            assertEquals(1, database.loadTracks().size()); assertTrue(existing.track().path().toFile().isFile());
            assertFalse(database.hasImported(uris(3).get(0).toString()));
            assertFalse(new File(root, "0.mp3").exists()); assertFalse(new File(root, "1.mp3").exists()); assertFalse(new File(root, "2.mp3").exists());
        }
    }
    @Test public void closeDuringCopyCleansUncommittedFile() {
        AtomicReference<AndroidLibraryImporter> reference = new AtomicReference<>(); AtomicReference<TrackEntry> copied = new AtomicReference<>();
        var importer = new AndroidLibraryImporter(database, (uri, cancelled) -> {
            TrackEntry entry = copy(uri); copied.set(entry); reference.get().close(); return entry;
        }, Runnable::run, Runnable::run, System::nanoTime); reference.set(importer);
        importer.submit(uris(3), ignored -> fail("Closed importer delivered UI callback"));
        assertFalse(copied.get().track().path().toFile().exists()); assertTrue(database.loadTracks().isEmpty());
    }
    @Test public void actualProviderPartialReadCleansReservedFileAndReportsFailures() {
        // Instrumentation runs in the target process; some phones leave the
        // separately installed test APK stopped. Activate only our synthetic
        // provider, as the document picker would activate a real URI source.
        try (var descriptor = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("content query --uri content://app.musicplayer.android.test.import-fixture/ok");
             var input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             var reader = new BufferedReader(new InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8))) {
            String row = reader.readLine();
            assertTrue("Synthetic provider did not start: " + row, row != null && row.contains("_display_name=ok.mp3"));
        } catch (IOException failure) { throw new AssertionError(failure); }
        List<AndroidLibraryImporter.Progress> results = new ArrayList<>();
        File copies = new File(root, "copies");
        try (var importer = new AndroidLibraryImporter(context, copies, database, Runnable::run, Runnable::run)) {
            importer.submit(List.of(Uri.parse("content://app.musicplayer.android.test.import-fixture/ok"),
                    Uri.parse("content://app.musicplayer.android.test.import-fixture/partial"),
                    Uri.parse("content://app.musicplayer.android.test.import-fixture/denied")), results::add);
            var result = results.get(results.size() - 1);
            assertEquals(result.failures().toString(), 1, result.success()); assertEquals(2, result.failures().size());
            assertTrue(new File(copies, "ok.mp3").isFile()); assertFalse(new File(copies, "partial.mp3").exists());
            assertFalse(new File(copies, "denied.mp3").exists()); assertEquals(1, database.loadTracks().size());
        }
    }
    @Test public void databaseQueryFailureHasDatabaseReason() {
        database.getWritableDatabase().execSQL("drop table imports");
        List<AndroidLibraryImporter.Progress> results = new ArrayList<>();
        try (var importer = new AndroidLibraryImporter(database, (uri, cancelled) -> { fail("Should not copy after query failure"); return null; },
                Runnable::run, Runnable::run, System::nanoTime)) {
            importer.submit(uris(1), results::add);
            assertEquals(AndroidLibraryImporter.FailureReason.DATABASE, results.get(0).failures().get(0).reason());
        }
    }
    @Test public void deliveryRejectionCleansStagingAndReleasesPendingUri() {
        AtomicBoolean reject = new AtomicBoolean(true); AtomicLong clock = new AtomicLong();
        List<AndroidLibraryImporter.Progress> results = new ArrayList<>();
        try (var importer = new AndroidLibraryImporter(database, (uri, cancelled) -> {
            clock.addAndGet(201_000_000); return copy(uri);
        }, Runnable::run, task -> {
            if (reject.get()) throw new java.util.concurrent.RejectedExecutionException("fixture delivery closed"); task.run();
        }, clock::get)) {
            try { importer.submit(uris(1), results::add); fail("Expected delivery rejection"); }
            catch (java.util.concurrent.RejectedExecutionException expected) { }
            assertFalse(new File(root, "0.mp3").exists()); assertTrue(database.loadTracks().isEmpty());
            reject.set(false); importer.submit(uris(1), results::add);
            assertEquals(1, results.get(results.size() - 1).success());
        }
    }
    @Test public void schemaTwoUpgradePreservesTracksAndLyrics() throws Exception {
        database.close(); File legacy = new File(root, "legacy.db"); File song = new File(root, "legacy.mp3"); assertTrue(song.createNewFile());
        try (var db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(legacy, null)) {
            db.execSQL("create table tracks(path text primary key,title text not null,artist text not null,created_at integer not null,storage_type text not null,file_name text)");
            db.execSQL("create table lyrics(path text primary key,source text not null,raw_text text not null,artwork_url text)");
            db.execSQL("insert into tracks values(?, '中文歌曲', '歌手', 1, 'FILE', 'legacy.mp3')", new Object[]{song.getAbsolutePath()});
            db.execSQL("insert into lyrics values(?, 'fixture', '[00:00]歌词', null)", new Object[]{song.getAbsolutePath()}); db.setVersion(2);
        }
        database = new AndroidMusicDatabase(BenchmarkSupport.databaseContext(context, legacy));
        assertEquals("中文歌曲", database.loadTracks().get(0).track().title());
        assertEquals("[00:00]歌词", database.loadLyrics(database.loadTracks().get(0)).rawText());
        assertFalse(database.hasImported("content://qa-import/legacy")); assertEquals(3, database.getReadableDatabase().getVersion());
    }
}
