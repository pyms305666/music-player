package app.musicplayer.lyrics;

import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;

import java.time.Duration;
import java.util.Optional;

/** 一个在线歌词来源；HTTP 通过 LyricsHttp 注入，桌面与 Android 共享实现。 */
public interface OnlineLyricsProvider {
    Optional<OnlineLyricsResult> search(Track track, Duration duration, LyricsHttp http);
}
