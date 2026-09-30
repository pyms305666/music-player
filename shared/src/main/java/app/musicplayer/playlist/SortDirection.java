package app.musicplayer.playlist;

/** 播放列表排序方向。 */
public enum SortDirection {
    ASCENDING("正序"),
    DESCENDING("倒序");

    private final String label;

    SortDirection(String label) {
        this.label = label;
    }

    public static SortDirection fromLabel(String label) {
        return "倒序".equals(label) ? DESCENDING : ASCENDING;
    }

    @Override
    public String toString() {
        return label;
    }
}
