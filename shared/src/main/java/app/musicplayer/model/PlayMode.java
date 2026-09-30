package app.musicplayer.model;

public enum PlayMode {
    ORDER("顺序播放"),
    SHUFFLE("随机播放"),
    REPEAT_ONE("单曲循环");

    private final String label;

    PlayMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
