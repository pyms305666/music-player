package app.musicplayer.model;

/**
 * 一次歌词搜索的完整结果。
 *
 * <p>lyrics: 已解析后的歌词； artworkUrl: 可选的封面/背景图片地址，UI 会把它显示到歌词区域背景。
 */
public record LyricsLookupResult(Lyrics lyrics, String artworkUrl) {
    /** 构造只包含歌词、没有封面地址的查词结果。 */
    public static LyricsLookupResult lyricsOnly(Lyrics lyrics) {
        return new LyricsLookupResult(lyrics, null);
    }
}
