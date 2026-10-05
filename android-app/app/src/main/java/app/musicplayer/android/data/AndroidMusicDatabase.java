package app.musicplayer.android.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import app.musicplayer.model.Lyrics;
import app.musicplayer.model.Track;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class AndroidMusicDatabase extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 3;
    public static final int IMPORT_BATCH_SIZE = 50;
    private static final String IMPORT_LOOKUP = "select (select imports.path from imports join tracks on tracks.path=imports.path where source_uri=? limit 1)";
    public record ImportedTrack(String sourceUri, TrackEntry entry) { }
    public record ImportCommit(List<TrackEntry> added, List<TrackEntry> duplicates) {
        public ImportCommit { added = List.copyOf(added); duplicates = List.copyOf(duplicates); }
    }

    private final Context context;

    public record CachedLyrics(String source, String rawText, String artworkUrl) {
    }

    public AndroidMusicDatabase(Context context) {
        super(context, "music-player.db", null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("create table tracks(path text primary key,title text not null,artist text not null,created_at integer not null,storage_type text not null default 'FILE',file_name text)");
        database.execSQL("create table lyrics(path text primary key,source text not null,raw_text text not null,artwork_url text)");
        createImports(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL("alter table tracks add column storage_type text not null default 'FILE'");
            database.execSQL("alter table tracks add column file_name text");
        }
        if (oldVersion < 3) createImports(database);
    }
    private static void createImports(SQLiteDatabase database) {
        database.execSQL("create table imports(source_uri text primary key,path text not null)");
        database.execSQL("create index imports_path on imports(path)");
    }
    public boolean hasImported(String sourceUri) { return hasImported(getReadableDatabase(), sourceUri); }
    private boolean hasImported(SQLiteDatabase database, String sourceUri) {
        // A scalar statement avoids creating a CursorWindow for every imported URI.
        try (var statement = database.compileStatement(IMPORT_LOOKUP)) {
            return hasImported(statement, sourceUri);
        }
    }
    private static boolean hasImported(android.database.sqlite.SQLiteStatement statement, String sourceUri) {
        statement.bindString(1, sourceUri);
        String path = statement.simpleQueryForString();
        return path != null && new java.io.File(path).isFile();
    }
    /** One transaction either commits this entire batch or leaves both tables unchanged. */
    public ImportCommit saveImportedTracks(List<ImportedTrack> batch) {
        if (batch.size() > IMPORT_BATCH_SIZE) throw new IllegalArgumentException("Import batch exceeds 50");
        SQLiteDatabase database = getWritableDatabase();
        List<TrackEntry> added = new ArrayList<>(), duplicates = new ArrayList<>();
        database.beginTransaction();
        try (var lookup = database.compileStatement(IMPORT_LOOKUP);
             var insert = database.compileStatement("insert into tracks(path,title,artist,created_at,storage_type,file_name) values(?,?,?,?,?,?)");
             var mapping = database.compileStatement("insert or replace into imports(source_uri,path) values(?,?)")) {
            for (ImportedTrack item : batch) {
                TrackEntry entry = item.entry();
                if (hasImported(lookup, item.sourceUri())) { duplicates.add(entry); continue; }
                insert.clearBindings();
                insert.bindString(1, entry.location()); insert.bindString(2, entry.track().title());
                insert.bindString(3, entry.track().artist()); insert.bindLong(4, entry.createdAt());
                insert.bindString(5, entry.storageType().name());
                if (entry.fileName() != null) insert.bindString(6, entry.fileName());
                if (insert.executeInsert() == -1) throw new android.database.sqlite.SQLiteException("Imported track write failed");
                mapping.bindString(1, item.sourceUri()); mapping.bindString(2, entry.location());
                if (mapping.executeInsert() == -1) throw new android.database.sqlite.SQLiteException("Import mapping write failed");
                added.add(entry);
            }
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
        return new ImportCommit(added, duplicates);
    }

    public List<TrackEntry> loadTracks() {
        List<TrackEntry> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "tracks", new String[]{"path", "title", "artist", "created_at", "storage_type", "file_name"},
                null, null, null, null, "created_at asc")) {
            while (cursor.moveToNext()) {
                String location = cursor.getString(0);
                TrackEntry.StorageType storageType = parseStorageType(cursor.getString(4));
                if (!locationExists(location, storageType)) {
                    continue;
                }
                String fileName = cursor.getString(5);
                if (fileName == null || fileName.isBlank()) {
                    fileName = storageType == TrackEntry.StorageType.FILE
                            ? Paths.get(location).getFileName().toString()
                            : cursor.getString(1);
                }
                Path metadataPath = storageType == TrackEntry.StorageType.FILE
                        ? Paths.get(location)
                        : Paths.get(fileName);
                Track track = new Track(metadataPath);
                track.updateMetadata(cursor.getString(1), cursor.getString(2));
                result.add(new TrackEntry(track, cursor.getLong(3), location, storageType, fileName));
            }
        }
        return result;
    }

    public void saveTrack(TrackEntry entry) {
        getWritableDatabase().insertWithOnConflict("tracks", null, trackValues(entry), SQLiteDatabase.CONFLICT_REPLACE);
    }
    private static ContentValues trackValues(TrackEntry entry) {
        ContentValues values = new ContentValues();
        values.put("path", entry.location());
        values.put("title", entry.track().title());
        values.put("artist", entry.track().artist());
        values.put("created_at", entry.createdAt());
        values.put("storage_type", entry.storageType().name());
        values.put("file_name", entry.fileName());
        return values;
    }

    public void removeTrack(TrackEntry entry) {
        getWritableDatabase().delete("imports", "path=?", new String[]{entry.key()});
        getWritableDatabase().delete("lyrics", "path=?", new String[]{entry.key()});
        getWritableDatabase().delete("tracks", "path=?", new String[]{entry.key()});
    }

    public CachedLyrics loadLyrics(TrackEntry entry) {
        try (Cursor cursor = getReadableDatabase().query(
                "lyrics", new String[]{"source", "raw_text", "artwork_url"},
                "path=?", new String[]{entry.key()},
                null, null, null)) {
            return cursor.moveToFirst() ? new CachedLyrics(cursor.getString(0), cursor.getString(1), cursor.getString(2)) : null;
        }
    }

    public void saveLyrics(TrackEntry entry, Lyrics lyrics, String artworkUrl) {
        if (lyrics == null || lyrics.rawText() == null || lyrics.rawText().isBlank()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("path", entry.key());
        values.put("source", lyrics.source());
        values.put("raw_text", lyrics.rawText());
        values.put("artwork_url", artworkUrl);
        getWritableDatabase().insertWithOnConflict("lyrics", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private boolean locationExists(String location, TrackEntry.StorageType storageType) {
        if (storageType == TrackEntry.StorageType.FILE) {
            return Paths.get(location).toFile().isFile();
        }
        try (Cursor cursor = context.getContentResolver().query(
                Uri.parse(location), new String[]{"_id"}, null, null, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static TrackEntry.StorageType parseStorageType(String value) {
        try {
            return TrackEntry.StorageType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return TrackEntry.StorageType.FILE;
        }
    }
}
