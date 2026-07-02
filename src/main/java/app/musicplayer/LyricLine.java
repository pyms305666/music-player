// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.time.Duration;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public record LyricLine(Duration time, String text) {
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public boolean timed() {
        // time 为 null 表示这行来自纯文本歌词，无法和播放进度同步。
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return time != null;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
