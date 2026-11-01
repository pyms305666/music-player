package app.musicplayer.android;

import android.content.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.*;

/**
 * Android 导入测试的 ContentProvider。提供可控文档及失败响应，用来验证 SAF 授权、复制与失败清理。 Test APK only: exposes synthetic
 * bytes, never reads app or user files.
 */
public class ImportFixtureProvider extends ContentProvider {
    /** 创建页面或服务，初始化控件、持久化和生命周期依赖。 */
    @Override
    public boolean onCreate() {
        return true;
    }

    /** 执行本测试的数据库、ContentProvider 或查询测量。 */
    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[] {uri.getLastPathSegment() + ".mp3"});
        return cursor;
    }

    /** 为测试 ContentProvider 打开指定文档夹具。 */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if ("denied".equals(uri.getLastPathSegment()))
            throw new FileNotFoundException("fixture permission failure");
        try {
            var pipe = ParcelFileDescriptor.createReliablePipe();
            Thread writer =
                    new Thread(
                            () -> {
                                try {
                                    var output = new FileOutputStream(pipe[1].getFileDescriptor());
                                    output.write(new byte[] {1, 2, 3, 4});
                                    output.flush();
                                    if ("partial".equals(uri.getLastPathSegment()))
                                        pipe[1].closeWithError("fixture read failure");
                                    else pipe[1].close();
                                } catch (IOException ignored) {
                                    try {
                                        pipe[1].close();
                                    } catch (IOException ignoredClose) {
                                    }
                                }
                            },
                            "qa-import-provider");
            writer.setDaemon(true);
            writer.start();
            return pipe[0];
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
    }

    /** 返回测试文档的 MIME 类型。 */
    @Override
    public String getType(Uri uri) {
        return "audio/mpeg";
    }

    /** 实现测试 ContentProvider 的写接口约定。 */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    /** 删除当前选中对象，并处理对应持久化和界面状态。 */
    @Override
    public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }

    /** 实现测试 ContentProvider 的写接口约定。 */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
