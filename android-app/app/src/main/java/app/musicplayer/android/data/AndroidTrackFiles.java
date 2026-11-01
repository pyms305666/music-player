package app.musicplayer.android.data;

import android.media.MediaMetadataRetriever;

import app.musicplayer.model.Track;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;

/**
 * Android 音频元数据与文件名规则。安全生成文件名，原子预留不冲突的目标文件，并在标签读取失败时保留文件名元数据。 Metadata and filename rules shared by
 * imports and download publication.
 */
public final class AndroidTrackFiles {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private AndroidTrackFiles() {}

    /** 编码歌单头或条目，或构造当前平台的歌曲记录。 */
    public static TrackEntry entry(File file, long createdAt) {
        Track track = new Track(file.toPath());
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            track.updateMetadata(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
        } catch (RuntimeException ignored) {
            /* Preserve filename metadata for unsupported tags. */
        } finally {
            try {
                retriever.release();
            } catch (IOException ignored) {
            }
        }
        return new TrackEntry(track, createdAt);
    }

    /** 替换文件名中的非法字符，生成适合保存到磁盘的名称。 */
    public static String safeName(String value) {
        String name = value == null || value.isBlank() ? "audio.mp3" : value;
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return name.equals(".") || name.equals("..") ? "audio.mp3" : name;
    }

    /**
     * 以创建新文件的方式原子预留目标名称，重名时选择下一个后缀。 Atomically reserve a new file: an import never overwrites an
     * existing song.
     */
    public static File reserve(File directory, String name) throws IOException {
        Files.createDirectories(directory.toPath());
        for (int suffix = 1; ; suffix++) {
            File file = candidate(directory, safeName(name), suffix);
            try {
                Files.createFile(file.toPath());
                return file;
            } catch (FileAlreadyExistsException occupied) {
                /* Try the next filename. */
            }
        }
    }

    /** 按基础文件名及后缀序号构造候选目标路径。 */
    private static File candidate(File directory, String name, int suffix) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        return new File(directory, suffix == 1 ? name : base + " (" + suffix + ")" + extension);
    }
}
