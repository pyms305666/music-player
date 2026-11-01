package app.musicplayer.playlist;

import java.util.Arrays;

/** 播放列表支持的排序字段。 */
public enum PlaylistSort {
    TITLE("按名称排序"),
    ARTIST("按歌手排序"),
    FILE_NAME("按文件名排序"),
    CREATED_AT("按创建日期排序");

    private final String label;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    PlaylistSort(String label) {
        this.label = label;
    }

    /** 把界面中文标签转换为对应枚举值。 */
    public static PlaylistSort fromLabel(String label) {
        return Arrays.stream(values())
                .filter(value -> value.label.equals(label))
                .findFirst()
                .orElse(TITLE);
    }

    /** 返回适合界面或诊断显示的文本表示。 */
    @Override
    public String toString() {
        return label;
    }
}
