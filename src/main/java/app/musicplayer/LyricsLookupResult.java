// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

/**
 * 一次歌词搜索的完整结果。
 *
 * lyrics: 已解析后的歌词；
 * artworkUrl: 可选的封面/背景图片地址，UI 会把它显示到歌词区域背景。
 */
// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public record LyricsLookupResult(Lyrics lyrics, String artworkUrl) {
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public static LyricsLookupResult lyricsOnly(Lyrics lyrics) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return new LyricsLookupResult(lyrics, null);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
