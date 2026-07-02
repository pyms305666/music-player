// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.List;

/**
 * 歌词展示模型。
 *
 * source: 展示给用户看的来源，例如本地歌词、联网歌词或数据库缓存。
 * lines: 已解析成行的歌词，用于列表显示和时间轴高亮。
 * timed: 是否包含时间轴；只有 timed=true 时才随播放进度滚动高亮。
 * rawText: 原始歌词文本，用于写入数据库缓存，避免保存后丢失时间标签。
 */
// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public record Lyrics(String source, List<LyricLine> lines, boolean timed, String rawText) {
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public static Lyrics empty(String message) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return new Lyrics(message, List.of(new LyricLine(null, message)), false, message);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
