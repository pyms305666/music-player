// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpClient;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.time.Duration;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Optional;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
interface OnlineLyricsProvider {
    // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
    Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient);
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
