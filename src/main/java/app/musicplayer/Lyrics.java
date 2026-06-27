package app.musicplayer;

import java.util.List;

/**
 * 歌词展示模型。
 *
 * source: 展示给用户看的来源，例如本地歌词、联网歌词或数据库缓存。
 * lines: 已解析成行的歌词，用于列表显示和时间轴高亮。
 * timed: 是否包含时间轴；只有 timed=true 时才随播放进度滚动高亮。
 * rawText: 原始歌词文本，用于写入数据库缓存，避免保存后丢失时间标签。
 */
public record Lyrics(String source, List<LyricLine> lines, boolean timed, String rawText) {
    public static Lyrics empty(String message) {
        return new Lyrics(message, List.of(new LyricLine(null, message)), false, message);
    }
}
