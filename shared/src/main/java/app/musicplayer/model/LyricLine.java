package app.musicplayer.model;

import java.time.Duration;

/** 共享单行歌词。关联歌词文本和可选时间，供 LRC 解析及播放位置高亮使用。 */
public record LyricLine(Duration time, String text) {
    /** 检查该歌词行是否包含可用于播放定位的时间。 */
    public boolean timed() {
        // time 为 null 表示这行来自纯文本歌词，无法和播放进度同步。
        return time != null;
    }
}
