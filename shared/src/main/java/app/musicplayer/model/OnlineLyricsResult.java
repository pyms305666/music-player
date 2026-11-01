package app.musicplayer.model;

/** 在线歌词提供者的原始结果。保存来源、原始歌词和可选封面，用于检查有无内容并交给 LRC 解析器。 */
public record OnlineLyricsResult(String source, String rawLyrics, String artworkUrl, int score) {
    /** 检查原始歌词内容是否可供解析和显示。 */
    public boolean hasLyrics() {
        return rawLyrics != null && !rawLyrics.isBlank();
    }
}
