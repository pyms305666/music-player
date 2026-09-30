package app.musicplayer.playlist;

import java.util.Arrays;

/** 播放列表支持的排序字段。 */
public enum PlaylistSort {
    TITLE("按名称排序"),
    ARTIST("按歌手排序"),
    FILE_NAME("按文件名排序"),
    CREATED_AT("按创建日期排序");

    private final String label;

    PlaylistSort(String label) {
        this.label = label;
    }

    public static PlaylistSort fromLabel(String label) {
        return Arrays.stream(values())
                .filter(value -> value.label.equals(label))
                .findFirst()
                .orElse(TITLE);
    }

    @Override
    public String toString() {
        return label;
    }
}
