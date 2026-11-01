package app.musicplayer.android.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.annotation.RequiresApi;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.model.Track;

import java.io.*;
import java.nio.file.Files;
import java.util.Locale;

/** 将单曲下载的临时音频保存到用户选择的目录，返回可登记到曲库的记录。 借用应用 Context；不写数据库。复制失败时撤销新文件或 MediaStore 条目。 */
public final class DownloadedTrackPublisher {
    private final Context context;
    private final File privateMusicDir;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public DownloadedTrackPublisher(Context context, File privateMusicDir) {
        this.context = context.getApplicationContext();
        this.privateMusicDir = privateMusicDir;
    }

    /** 保存单曲并返回曲库记录。useRootDirectory 选择根目录 music，否则 Q 起写入 MediaStore、旧系统写入私有目录；失败抛 IOException。 */
    public TrackEntry publish(
            File temporaryFile, OnlineTrackInfo selected, boolean useRootDirectory)
            throws IOException {
        Track track = readTrackMetadata(temporaryFile);
        track.updateMetadata(selected.title(), selected.artist());
        long createdAt = System.currentTimeMillis();

        if (useRootDirectory) {
            File target =
                    copyToDirectory(
                            temporaryFile,
                            new File(Environment.getExternalStorageDirectory(), "music"));
            Track publicTrack = new Track(target.toPath());
            publicTrack.updateMetadata(track.title(), track.artist());
            return new TrackEntry(publicTrack, createdAt);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStoreFile published = publishToMediaStore(temporaryFile);
            Files.deleteIfExists(temporaryFile.toPath());
            return TrackEntry.mediaStore(
                    track, createdAt, published.uri().toString(), published.fileName());
        }

        File target = copyToDirectory(temporaryFile, privateMusicDir);
        Track privateTrack = new Track(target.toPath());
        privateTrack.updateMetadata(track.title(), track.artist());
        return new TrackEntry(privateTrack, createdAt);
    }

    /** 从下载音频读取初始歌名和歌手，供在线元数据补充。 */
    private Track readTrackMetadata(File file) {
        return AndroidTrackFiles.entry(file, System.currentTimeMillis()).track();
    }

    /** 原子预留新文件名后复制音频，成功才移除临时源，失败则撤销目标。 */
    private File copyToDirectory(File source, File directory) throws IOException {
        if (!ensureDirectory(directory)) {
            throw new IOException("无法创建歌曲目录：" + directory.getAbsolutePath());
        }
        File target = AndroidTrackFiles.reserve(directory, source.getName());
        try {
            Files.copy(
                    source.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(source.toPath());
            return target;
        } catch (IOException error) {
            Files.deleteIfExists(target.toPath());
            throw error;
        }
    }

    /** 创建待发布的媒体库条目，写入成功后解除待发布标记，失败时删除新条目。 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private MediaStoreFile publishToMediaStore(File source) throws IOException {
        String relativePath = Environment.DIRECTORY_MUSIC + "/music/";
        String fileName =
                uniqueMediaStoreName(AndroidTrackFiles.safeName(source.getName()), relativePath);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeFor(fileName));
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri uri =
                context.getContentResolver()
                        .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("系统媒体库无法创建歌曲文件");
        }
        try {
            try (InputStream input = Files.newInputStream(source.toPath());
                    OutputStream output = context.getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("系统媒体库无法写入歌曲文件");
                copyStream(input, output);
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.Audio.Media.IS_PENDING, 0);
            context.getContentResolver().update(uri, completed, null, null);
            return new MediaStoreFile(uri, fileName);
        } catch (IOException | RuntimeException error) {
            context.getContentResolver().delete(uri, null, null);
            if (error instanceof IOException ioError) throw ioError;
            throw new IOException(error.getMessage(), error);
        }
    }

    /** 在目标媒体目录中选择不与已有歌曲同名的文件名。 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private String uniqueMediaStoreName(String requestedName, String relativePath) {
        int dot = requestedName.lastIndexOf('.');
        String base = dot > 0 ? requestedName.substring(0, dot) : requestedName;
        String extension = dot > 0 ? requestedName.substring(dot) : "";
        String candidate = requestedName;
        int counter = 2;
        while (mediaStoreNameExists(candidate, relativePath)) {
            candidate = base + " (" + counter++ + ")" + extension;
        }
        return candidate;
    }

    /** 查询目标目录是否已经存在指定显示文件名。 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private boolean mediaStoreNameExists(String fileName, String relativePath) {
        String selection =
                MediaStore.Audio.Media.DISPLAY_NAME
                        + "=? and "
                        + MediaStore.Audio.Media.RELATIVE_PATH
                        + "=?";
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                new String[] {MediaStore.Audio.Media._ID},
                                selection,
                                new String[] {fileName, relativePath},
                                null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    /** 按文件名选择 MIME 类型，无法识别时使用音频默认值。 */
    private static String mimeTypeFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return detected == null ? "audio/mpeg" : detected;
    }

    /** 按 16 KiB 缓冲复制媒体内容；不关闭借用的流，外层 try-with-resources 负责释放。 */
    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16_384];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
    }

    /** 确认目录已存在，必要时创建，返回创建是否成功。 */
    private static boolean ensureDirectory(File directory) {
        return directory.isDirectory() || directory.mkdirs();
    }

    /** 组合新媒体条目的 URI 与最终显示文件名，供曲库登记。 */
    private record MediaStoreFile(Uri uri, String fileName) {}
}
