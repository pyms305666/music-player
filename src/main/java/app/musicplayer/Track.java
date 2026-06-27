package app.musicplayer;

import java.nio.file.Path;

public final class Track {
    private final Path path;
    private String title;
    private String artist;

    public Track(Path path) {
        this.path = path;
        parseName(path);
    }

    public Path path() {
        return path;
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public void updateMetadata(String title, String artist) {
        if (title != null && !title.isBlank()) {
            this.title = title.trim();
        }
        if (artist != null && !artist.isBlank()) {
            this.artist = artist.trim();
        }
    }

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

    @Override
    public String toString() {
        return artist == null || artist.isBlank() || "未知歌手".equals(artist)
                ? title
                : artist + " - " + title;
    }
}
