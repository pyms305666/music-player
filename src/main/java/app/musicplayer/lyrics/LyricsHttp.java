package app.musicplayer.lyrics;

/** 歌词抓取所需的最小 HTTP 能力；桌面与 Android 各自注入实现。 */
public interface LyricsHttp {
    String fetch(String url, String referer) throws Exception;
}
