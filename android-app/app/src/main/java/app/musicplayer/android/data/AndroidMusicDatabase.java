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

/** Android SQLite 存储实现。维护歌曲、歌词缓存、导入记录和独立歌单，处理旧数据库升级，并实现共享 PlaylistStore 接口。 */
public final class AndroidMusicDatabase extends SQLiteOpenHelper
        implements app.musicplayer.playlist.PlaylistStore {
    private static final int DATABASE_VERSION = 4;
    public static final int IMPORT_BATCH_SIZE = 50;
    private static final String IMPORT_LOOKUP =
            "select (select imports.path from imports join tracks on tracks.path=imports.path where"
                    + " source_uri=? limit 1)";

    /** 组合待提交歌曲、来源位置与创建时间，用于批量导入。 */
    public record ImportedTrack(String sourceUri, TrackEntry entry) {}

    /** 保存一批事务提交后的新增与重复歌曲，供导入进度及最终报告汇总。 */
    public record ImportCommit(List<TrackEntry> added, List<TrackEntry> duplicates) {
        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        public ImportCommit {
            added = List.copyOf(added);
            duplicates = List.copyOf(duplicates);
        }
    }

    private final Context context;

    /** 保存数据库中的歌词来源、原始文本和可选封面地址。 */
    public record CachedLyrics(String source, String rawText, String artworkUrl) {}

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public AndroidMusicDatabase(Context context) {
        super(context, "music-player.db", null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    /** 首次打开数据库时创建歌曲、歌词、导入关联及歌单表；已有数据库由升级回调处理。 */
    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "create table tracks(path text primary key,title text not null,artist text not"
                        + " null,created_at integer not null,storage_type text not null default"
                        + " 'FILE',file_name text)");
        database.execSQL(
                "create table lyrics(path text primary key,source text not null,raw_text text not"
                        + " null,artwork_url text)");
        createImports(database);
        createPlaylists(database);
    }

    /** 升级歌曲、歌词和歌单表结构，保留已有音频及导入记录。 */
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL(
                    "alter table tracks add column storage_type text not null default 'FILE'");
            database.execSQL("alter table tracks add column file_name text");
        }
        if (oldVersion < 3) createImports(database);
        if (oldVersion < 4) createPlaylists(database);
    }

    /** 创建对应的导入历史或独立歌单数据库表。 */
    private static void createPlaylists(SQLiteDatabase database) {
        database.execSQL(
                "create table named_playlists(id text primary key,metadata text not null)");
        database.execSQL(
                "create table named_playlist_items(playlist_id text not null,item_id text not"
                        + " null,position integer not null,payload text not null,primary"
                        + " key(playlist_id,item_id))");
    }

    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
    @Override
    public synchronized List<app.musicplayer.playlist.NamedPlaylist> loadPlaylists() {
        var result = new ArrayList<app.musicplayer.playlist.NamedPlaylist>();
        SQLiteDatabase db = getReadableDatabase();
        try (var rows =
                db.rawQuery("select id,metadata from named_playlists order by rowid", null)) {
            while (rows.moveToNext()) {
                var entries = new ArrayList<app.musicplayer.playlist.NamedPlaylist.Entry>();
                try (var items =
                        db.rawQuery(
                                "select payload from named_playlist_items where playlist_id=? order"
                                        + " by position",
                                new String[] {rows.getString(0)})) {
                    while (items.moveToNext())
                        entries.add(
                                app.musicplayer.playlist.PlaylistCodec.entry(items.getString(0)));
                }
                result.add(
                        app.musicplayer.playlist.PlaylistCodec.header(rows.getString(1), entries));
            }
        }
        return List.copyOf(result);
    }

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    @Override
    public synchronized void savePlaylist(app.musicplayer.playlist.NamedPlaylist playlist) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues header = new ContentValues();
            header.put("id", playlist.id());
            header.put("metadata", app.musicplayer.playlist.PlaylistCodec.header(playlist));
            if (db.insertWithOnConflict(
                            "named_playlists", null, header, SQLiteDatabase.CONFLICT_REPLACE)
                    == -1) throw new IllegalStateException("保存歌单失败");
            db.delete("named_playlist_items", "playlist_id=?", new String[] {playlist.id()});
            int position = 0;
            try (var insert =
                    db.compileStatement(
                            "insert into named_playlist_items(playlist_id,item_id,position,payload)"
                                    + " values(?,?,?,?)")) {
                for (var e : playlist.entries()) {
                    insert.bindString(1, playlist.id());
                    insert.bindString(2, e.id());
                    insert.bindLong(3, position++);
                    insert.bindString(4, app.musicplayer.playlist.PlaylistCodec.entry(e));
                    insert.executeInsert();
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 保存歌单中指定条目的最新状态与配置。 */
    @Override
    public synchronized void updatePlaylistEntry(
            String playlistId, app.musicplayer.playlist.NamedPlaylist.Entry entry) {
        ContentValues value = new ContentValues();
        value.put("payload", app.musicplayer.playlist.PlaylistCodec.entry(entry));
        if (getWritableDatabase()
                        .update(
                                "named_playlist_items",
                                value,
                                "playlist_id=? and item_id=?",
                                new String[] {playlistId, entry.id()})
                != 1) throw new IllegalStateException("歌单条目已移除");
    }

    /** 删除歌单及其条目记录，保留关联音频和歌词。 */
    @Override
    public synchronized void deletePlaylist(String playlistId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("named_playlist_items", "playlist_id=?", new String[] {playlistId});
            db.delete("named_playlists", "id=?", new String[] {playlistId});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 创建对应的导入历史或独立歌单数据库表。 */
    private static void createImports(SQLiteDatabase database) {
        database.execSQL("create table imports(source_uri text primary key,path text not null)");
        database.execSQL("create index imports_path on imports(path)");
    }

    /** 检查给定文档身份是否已在导入历史中登记。 */
    public boolean hasImported(String sourceUri) {
        return hasImported(getReadableDatabase(), sourceUri);
    }

    /** 检查给定文档身份是否已在导入历史中登记。 */
    private boolean hasImported(SQLiteDatabase database, String sourceUri) {
        // A scalar statement avoids creating a CursorWindow for every imported URI.
        try (var statement = database.compileStatement(IMPORT_LOOKUP)) {
            return hasImported(statement, sourceUri);
        }
    }

    /** 检查给定文档身份是否已在导入历史中登记。 */
    private static boolean hasImported(
            android.database.sqlite.SQLiteStatement statement, String sourceUri) {
        statement.bindString(1, sourceUri);
        String path = statement.simpleQueryForString();
        return path != null && new java.io.File(path).isFile();
    }

    /**
     * 在事务中保存 Android 导入的歌曲及文档历史，失败时整体回滚。 One transaction either commits this entire batch or leaves
     * both tables unchanged.
     */
    public ImportCommit saveImportedTracks(List<ImportedTrack> batch) {
        if (batch.size() > IMPORT_BATCH_SIZE)
            throw new IllegalArgumentException("Import batch exceeds 50");
        SQLiteDatabase database = getWritableDatabase();
        List<TrackEntry> added = new ArrayList<>(), duplicates = new ArrayList<>();
        database.beginTransaction();
        try (var lookup = database.compileStatement(IMPORT_LOOKUP);
                var insert =
                        database.compileStatement(
                                "insert into"
                                    + " tracks(path,title,artist,created_at,storage_type,file_name)"
                                    + " values(?,?,?,?,?,?)");
                var mapping =
                        database.compileStatement(
                                "insert or replace into imports(source_uri,path) values(?,?)")) {
            for (ImportedTrack item : batch) {
                TrackEntry entry = item.entry();
                if (hasImported(lookup, item.sourceUri())) {
                    duplicates.add(entry);
                    continue;
                }
                insert.clearBindings();
                insert.bindString(1, entry.location());
                insert.bindString(2, entry.track().title());
                insert.bindString(3, entry.track().artist());
                insert.bindLong(4, entry.createdAt());
                insert.bindString(5, entry.storageType().name());
                if (entry.fileName() != null) insert.bindString(6, entry.fileName());
                if (insert.executeInsert() == -1)
                    throw new android.database.sqlite.SQLiteException(
                            "Imported track write failed");
                mapping.bindString(1, item.sourceUri());
                mapping.bindString(2, entry.location());
                if (mapping.executeInsert() == -1)
                    throw new android.database.sqlite.SQLiteException(
                            "Import mapping write failed");
                added.add(entry);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return new ImportCommit(added, duplicates);
    }

    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
    public List<TrackEntry> loadTracks() {
        List<TrackEntry> result = new ArrayList<>();
        try (Cursor cursor =
                getReadableDatabase()
                        .query(
                                "tracks",
                                new String[] {
                                    "path",
                                    "title",
                                    "artist",
                                    "created_at",
                                    "storage_type",
                                    "file_name"
                                },
                                null,
                                null,
                                null,
                                null,
                                "created_at asc")) {
            while (cursor.moveToNext()) {
                String location = cursor.getString(0);
                TrackEntry.StorageType storageType = parseStorageType(cursor.getString(4));
                if (!locationExists(location, storageType)) {
                    continue;
                }
                String fileName = cursor.getString(5);
                if (fileName == null || fileName.isBlank()) {
                    fileName =
                            storageType == TrackEntry.StorageType.FILE
                                    ? Paths.get(location).getFileName().toString()
                                    : cursor.getString(1);
                }
                Path metadataPath =
                        storageType == TrackEntry.StorageType.FILE
                                ? Paths.get(location)
                                : Paths.get(fileName);
                Track track = new Track(metadataPath);
                track.updateMetadata(cursor.getString(1), cursor.getString(2));
                result.add(
                        new TrackEntry(track, cursor.getLong(3), location, storageType, fileName));
            }
        }
        return result;
    }

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    public void saveTrack(TrackEntry entry) {
        if (getWritableDatabase()
                        .insertWithOnConflict(
                                "tracks", null, trackValues(entry), SQLiteDatabase.CONFLICT_REPLACE)
                == -1) throw new android.database.sqlite.SQLiteException("保存歌曲记录失败");
    }

    /** 将歌曲记录转换为 Android SQLite 所需的 ContentValues。 */
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

    /** 删除歌曲的曲库记录，并按本数据库约定清理关联缓存。 */
    public void removeTrack(TrackEntry entry) {
        getWritableDatabase().delete("imports", "path=?", new String[] {entry.key()});
        getWritableDatabase().delete("lyrics", "path=?", new String[] {entry.key()});
        getWritableDatabase().delete("tracks", "path=?", new String[] {entry.key()});
    }

    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
    public CachedLyrics loadLyrics(TrackEntry entry) {
        try (Cursor cursor =
                getReadableDatabase()
                        .query(
                                "lyrics",
                                new String[] {"source", "raw_text", "artwork_url"},
                                "path=?",
                                new String[] {entry.key()},
                                null,
                                null,
                                null)) {
            return cursor.moveToFirst()
                    ? new CachedLyrics(
                            cursor.getString(0), cursor.getString(1), cursor.getString(2))
                    : null;
        }
    }

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    public void saveLyrics(TrackEntry entry, Lyrics lyrics, String artworkUrl) {
        if (lyrics == null || lyrics.rawText() == null || lyrics.rawText().isBlank()) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("path", entry.key());
        values.put("source", lyrics.source());
        values.put("raw_text", lyrics.rawText());
        values.put("artwork_url", artworkUrl);
        getWritableDatabase()
                .insertWithOnConflict("lyrics", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** 检查歌曲位置是否已经登记到数据库。 */
    private boolean locationExists(String location, TrackEntry.StorageType storageType) {
        if (storageType == TrackEntry.StorageType.FILE) {
            return Paths.get(location).toFile().isFile();
        }
        try (var descriptor =
                context.getContentResolver().openFileDescriptor(Uri.parse(location), "r")) {
            return descriptor != null;
        } catch (java.io.IOException | RuntimeException ignored) {
            return false;
        }
    }

    /** 把存储类型字段转换为 FILE 或 MediaStore 记录类型。 */
    private static TrackEntry.StorageType parseStorageType(String value) {
        try {
            return TrackEntry.StorageType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return TrackEntry.StorageType.FILE;
        }
    }
}
