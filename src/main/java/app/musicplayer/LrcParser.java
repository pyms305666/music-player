package app.musicplayer;

import java.time.Duration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    // 匹配常见 LRC 时间标签：[mm:ss]、[mm:ss.xx]、[mm:ss.xxx]。
    // 一行歌词可能带多个时间标签，例如副歌复用：[01:10.00][02:40.00]歌词。
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]");

    private LrcParser() {
    }

    public static Lyrics parse(String source, String rawLyrics) {
        if (rawLyrics == null || rawLyrics.isBlank()) {
            return Lyrics.empty("暂无歌词");
        }

        // timedLines 用于可随播放进度高亮的歌词；plainLines 用于纯文本歌词。
        // 解析后如果存在任何时间轴歌词，就优先按时间轴模式显示。
        List<LyricLine> timedLines = new ArrayList<>();
        List<LyricLine> plainLines = new ArrayList<>();

        for (String line : rawLyrics.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            parseLine(line, timedLines, plainLines);
        }

        if (!timedLines.isEmpty()) {
            timedLines.sort(Comparator.comparing(LyricLine::time));
            return new Lyrics(source, timedLines, true, rawLyrics);
        }

        if (!plainLines.isEmpty()) {
            return new Lyrics(source, plainLines, false, rawLyrics);
        }

        return Lyrics.empty("暂无歌词");
    }

    private static void parseLine(String line, List<LyricLine> timedLines, List<LyricLine> plainLines) {
        Matcher matcher = TIME_TAG.matcher(line);
        List<Duration> timestamps = new ArrayList<>();
        int lastTagEnd = 0;

        // 收集一行里的所有时间标签，并记录最后一个标签结束位置；
        // 标签后面的内容就是实际歌词文本。
        while (matcher.find()) {
            timestamps.add(toDuration(matcher.group(1), matcher.group(2), matcher.group(3)));
            lastTagEnd = matcher.end();
        }

        String text = line.substring(Math.min(lastTagEnd, line.length())).trim();
        if (!timestamps.isEmpty()) {
            String lyricText = text.isBlank() ? " " : text;
            for (Duration timestamp : timestamps) {
                timedLines.add(new LyricLine(timestamp, lyricText));
            }
            return;
        }

        String plain = line.trim();
        // 没有时间标签时，把普通文本作为纯文本歌词；
        // 常见的 LRC 元信息标签不显示给用户。
        if (!plain.isBlank() && !plain.startsWith("[ti:") && !plain.startsWith("[ar:") && !plain.startsWith("[al:")
                && !plain.startsWith("[by:") && !plain.startsWith("[offset:")) {
            plainLines.add(new LyricLine(null, plain));
        }
    }

    private static Duration toDuration(String minutes, String seconds, String fraction) {
        int minuteValue = Integer.parseInt(minutes);
        int secondValue = Integer.parseInt(seconds);
        int millis = 0;

        if (fraction != null) {
            String padded = (fraction + "000").substring(0, 3);
            millis = Integer.parseInt(padded);
        }

        return Duration.ofMillis((minuteValue * 60L + secondValue) * 1000L + millis);
    }
}
