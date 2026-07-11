package app.musicplayer.android.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import app.musicplayer.model.Lyrics;
import app.musicplayer.model.Track;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class AndroidMusicDatabase extends SQLiteOpenHelper {
    public record CachedLyrics(String source, String rawText, String artworkUrl) {
    }

    public AndroidMusicDatabase(Context context) {
        super(context, "music-player.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("create table tracks(path text primary key,title text not null,artist text not null,created_at integer not null)");
        database.execSQL("create table lyrics(path text primary key,source text not null,raw_text text not null,artwork_url text)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
    }

    public List<TrackEntry> loadTracks() {
        List<TrackEntry> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "tracks", new String[]{"path", "title", "artist", "created_at"},
                null, null, null, null, "created_at asc")) {
            while (cursor.moveToNext()) {
                Path path = Paths.get(cursor.getString(0));
                if (!path.toFile().isFile()) {
                    continue;
                }
                Track track = new Track(path);
                track.updateMetadata(cursor.getString(1), cursor.getString(2));
                result.add(new TrackEntry(track, cursor.getLong(3)));
            }
        }
        return result;
    }

    public void saveTrack(TrackEntry entry) {
        ContentValues values = new ContentValues();
        values.put("path", entry.track().path().toAbsolutePath().toString());
        values.put("title", entry.track().title());
        values.put("artist", entry.track().artist());
        values.put("created_at", entry.createdAt());
        getWritableDatabase().insertWithOnConflict("tracks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void removeTrack(TrackEntry entry) {
        String path = entry.track().path().toAbsolutePath().toString();
        getWritableDatabase().delete("lyrics", "path=?", new String[]{path});
        getWritableDatabase().delete("tracks", "path=?", new String[]{path});
    }

    public CachedLyrics loadLyrics(TrackEntry entry) {
        try (Cursor cursor = getReadableDatabase().query(
                "lyrics", new String[]{"source", "raw_text", "artwork_url"},
                "path=?", new String[]{entry.track().path().toAbsolutePath().toString()},
                null, null, null)) {
            return cursor.moveToFirst() ? new CachedLyrics(cursor.getString(0), cursor.getString(1), cursor.getString(2)) : null;
        }
    }

    public void saveLyrics(TrackEntry entry, Lyrics lyrics, String artworkUrl) {
        if (lyrics == null || lyrics.rawText() == null || lyrics.rawText().isBlank()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("path", entry.track().path().toAbsolutePath().toString());
        values.put("source", lyrics.source());
        values.put("raw_text", lyrics.rawText());
        values.put("artwork_url", artworkUrl);
        getWritableDatabase().insertWithOnConflict("lyrics", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
