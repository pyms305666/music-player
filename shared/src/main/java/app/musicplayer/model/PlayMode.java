package app.musicplayer.model;

/** 共享播放模式枚举。表示顺序循环、单曲循环和随机播放，提供界面使用的中文名称。 */
public enum PlayMode {
    ORDER("顺序播放"),
    SHUFFLE("随机播放"),
    REPEAT_ONE("单曲循环");

    private final String label;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    PlayMode(String label) {
        this.label = label;
    }

    /** 返回适合界面或诊断显示的文本表示。 */
    @Override
    public String toString() {
        return label;
    }
}
