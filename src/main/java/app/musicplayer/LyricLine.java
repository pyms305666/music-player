package app.musicplayer;

import java.time.Duration;

public record LyricLine(Duration time, String text) {
    public boolean timed() {
        // time 为 null 表示这行来自纯文本歌词，无法和播放进度同步。
        return time != null;
    }
}
