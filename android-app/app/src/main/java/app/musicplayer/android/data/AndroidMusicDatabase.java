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
    private static final int DATABASE_VERSION = 2;

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
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL("alter table tracks add column storage_type text not null default 'FILE'");
            database.execSQL("alter table tracks add column file_name text");
        }
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
        ContentValues values = new ContentValues();
        values.put("path", entry.location());
        values.put("title", entry.track().title());
        values.put("artist", entry.track().artist());
        values.put("created_at", entry.createdAt());
        values.put("storage_type", entry.storageType().name());
        values.put("file_name", entry.fileName());
        getWritableDatabase().insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void removeTrack(TrackEntry entry) {
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
