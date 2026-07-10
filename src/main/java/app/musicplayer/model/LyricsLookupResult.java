package app.musicplayer.model;

/**
 * 一次歌词搜索的完整结果。
 *
 * lyrics: 已解析后的歌词；
 * artworkUrl: 可选的封面/背景图片地址，UI 会把它显示到歌词区域背景。
 */
public record LyricsLookupResult(Lyrics lyrics, String artworkUrl) {
    public static LyricsLookupResult lyricsOnly(Lyrics lyrics) {
        return new LyricsLookupResult(lyrics, null);
    }
}
