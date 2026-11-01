package app.musicplayer.playlist;

/** 播放列表排序方向。 */
public enum SortDirection {
    ASCENDING("正序"),
    DESCENDING("倒序");

    private final String label;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    SortDirection(String label) {
        this.label = label;
    }

    /** 把界面中文标签转换为对应枚举值。 */
    public static SortDirection fromLabel(String label) {
        return "倒序".equals(label) ? DESCENDING : ASCENDING;
    }

    /** 返回适合界面或诊断显示的文本表示。 */
    @Override
    public String toString() {
        return label;
    }
}
