package app.musicplayer.playlist;

import java.util.List;

/**
 * 歌单所需的平台文件接口。提供目标扫描及位置可读性检查，使共享代码兼容桌面路径与 Android 文档 URI。 File paths on desktop,
 * MediaStore/document URIs on Android. Called on background workers.
 */
public interface PlaylistFiles {
    /** 扫描目标目录及已有记录，生成供重复检查使用的本地歌曲信息。 */
    List<PlaylistDuplicates.Local> scan(String destination, boolean recursive);

    /** 检查位置是否仍可读取，兼容本平台的路径或文档 URI。 */
    boolean readable(String location);
}
