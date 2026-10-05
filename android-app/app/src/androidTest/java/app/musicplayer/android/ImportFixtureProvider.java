package app.musicplayer.android;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Test APK only: exposes synthetic bytes, never reads app or user files. */
public class ImportFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[]{uri.getLastPathSegment() + ".mp3"}); return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if ("denied".equals(uri.getLastPathSegment())) throw new FileNotFoundException("fixture permission failure");
        try {
            var pipe = ParcelFileDescriptor.createReliablePipe();
            Thread writer = new Thread(() -> {
                try {
                    var output = new FileOutputStream(pipe[1].getFileDescriptor());
                    output.write(new byte[]{1, 2, 3, 4}); output.flush();
                    if ("partial".equals(uri.getLastPathSegment())) pipe[1].closeWithError("fixture read failure");
                    else pipe[1].close();
                } catch (IOException ignored) { try { pipe[1].close(); } catch (IOException ignoredClose) { } }
            }, "qa-import-provider");
            writer.setDaemon(true); writer.start(); return pipe[0];
        } catch (IOException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public String getType(Uri uri) { return "audio/mpeg"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
