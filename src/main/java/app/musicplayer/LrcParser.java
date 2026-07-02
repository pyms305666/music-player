// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.time.Duration;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.ArrayList;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Comparator;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.List;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.regex.Matcher;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.regex.Pattern;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public final class LrcParser {
    // 匹配常见 LRC 时间标签：[mm:ss]、[mm:ss.xx]、[mm:ss.xxx]。
    // 一行歌词可能带多个时间标签，例如副歌复用：[01:10.00][02:40.00]歌词。
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]");

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private LrcParser() {
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public static Lyrics parse(String source, String rawLyrics) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (rawLyrics == null || rawLyrics.isBlank()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Lyrics.empty("暂无歌词");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // timedLines 用于可随播放进度高亮的歌词；plainLines 用于纯文本歌词。
        // 解析后如果存在任何时间轴歌词，就优先按时间轴模式显示。
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<LyricLine> timedLines = new ArrayList<>();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<LyricLine> plainLines = new ArrayList<>();

        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (String line : rawLyrics.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            parseLine(line, timedLines, plainLines);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!timedLines.isEmpty()) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            timedLines.sort(Comparator.comparing(LyricLine::time));
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return new Lyrics(source, timedLines, true, rawLyrics);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!plainLines.isEmpty()) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return new Lyrics(source, plainLines, false, rawLyrics);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Lyrics.empty("暂无歌词");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static void parseLine(String line, List<LyricLine> timedLines, List<LyricLine> plainLines) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Matcher matcher = TIME_TAG.matcher(line);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<Duration> timestamps = new ArrayList<>();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int lastTagEnd = 0;

        // 收集一行里的所有时间标签，并记录最后一个标签结束位置；
        // 标签后面的内容就是实际歌词文本。
        // 说明：while 循环：只要条件继续成立，就反复执行代码块。
        while (matcher.find()) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            timestamps.add(toDuration(matcher.group(1), matcher.group(2), matcher.group(3)));
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            lastTagEnd = matcher.end();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String text = line.substring(Math.min(lastTagEnd, line.length())).trim();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!timestamps.isEmpty()) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String lyricText = text.isBlank() ? " " : text;
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (Duration timestamp : timestamps) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                timedLines.add(new LyricLine(timestamp, lyricText));
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String plain = line.trim();
        // 没有时间标签时，把普通文本作为纯文本歌词；
        // 常见的 LRC 元信息标签不显示给用户。
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!plain.isBlank() && !plain.startsWith("[ti:") && !plain.startsWith("[ar:") && !plain.startsWith("[al:")
                // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
                && !plain.startsWith("[by:") && !plain.startsWith("[offset:")) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            plainLines.add(new LyricLine(null, plain));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static Duration toDuration(String minutes, String seconds, String fraction) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int minuteValue = Integer.parseInt(minutes);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int secondValue = Integer.parseInt(seconds);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int millis = 0;

        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (fraction != null) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String padded = (fraction + "000").substring(0, 3);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            millis = Integer.parseInt(padded);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Duration.ofMillis((minuteValue * 60L + secondValue) * 1000L + millis);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
