// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
record OnlineLyricsResult(String source, String rawLyrics, String artworkUrl, int score) {
    // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
    boolean hasLyrics() {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return rawLyrics != null && !rawLyrics.isBlank();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
