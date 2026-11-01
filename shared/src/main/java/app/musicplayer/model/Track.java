package app.musicplayer.model;

import java.nio.file.Path;

/** 两端共享的歌曲模型。以音频路径标识本地歌曲，从文件名提供初始标题与歌手，再由音频标签或下载信息更新元数据。 */
public final class Track {
    private final Path path;
    private String title;
    private String artist;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public Track(Path path) {
        this.path = path;
        parseName(path);
    }

    /** 返回歌曲模型中保存的对应路径或显示元数据。 */
    public Path path() {
        return path;
    }

    /** 返回歌曲模型中保存的对应路径或显示元数据。 */
    public String title() {
        return title;
    }

    /** 返回歌曲模型中保存的对应路径或显示元数据。 */
    public String artist() {
        return artist;
    }

    /** 使用有效的音频标签或在线信息更新歌曲标题与歌手。 */
    public void updateMetadata(String title, String artist) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (artist != null && !artist.isBlank()) {
            this.artist = artist.trim();
        }
    }

    /** 从文件名分离初始歌名与歌手，作为标签读取前的显示值。 */
    private void parseName(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;

        // 约定优先支持“歌手 - 歌名.mp3”这种常见文件命名方式。
        // 如果文件本身带有音频元数据，播放时会再用 Media 元数据覆盖这里的初始值。
        String[] parts = baseName.split("\\s+-\\s+", 2);

        if (parts.length == 2) {
            artist = parts[0].trim();
            title = parts[1].trim();
        } else {
            artist = "未知歌手";
            title = baseName.trim();
        }
    }

    /** 返回适合界面或诊断显示的文本表示。 */
    @Override
    public String toString() {
        return artist == null || artist.isBlank() || "未知歌手".equals(artist)
                ? title
                : artist + " - " + title;
    }
}
