package app.musicplayer.android;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.musicplayer.lyrics.KugouMusicProvider;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.lyrics.LrclibLyricsProvider;
import app.musicplayer.lyrics.LyricsHttp;
import app.musicplayer.lyrics.NeteaseMusicProvider;
import app.musicplayer.lyrics.OnlineLyricsProvider;
import app.musicplayer.lyrics.QqMusicProvider;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;
import app.musicplayer.online.CrawlerSession;

/**
 * Android 端独立歌词查询：与桌面 LyricsService 相同的在线渠道
 * （网易云 → QQ → 酷狗 → LRCLIB），不再借用在线下载搜索的结果。
 */
public final class AndroidLyricsService {
    private final CrawlerSession session = new CrawlerSession();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "android-lyrics");
        thread.setDaemon(true);
        return thread;
    });
    private final List<OnlineLyricsProvider> providers = List.of(
            new NeteaseMusicProvider(),
            new QqMusicProvider(),
            new KugouMusicProvider(),
            new LrclibLyricsProvider()
    );
    private final LyricsHttp http = (url, referer) -> {
        session.ensurePrimed();
        return session.fetch(url, referer);
    };

    /** 在线四连查，返回 null 表示所有渠道都没有歌词。 */
    public LyricsLookupResult searchOnline(Track track, long durationMillis) {
        Duration duration = durationMillis > 0 ? Duration.ofMillis(durationMillis) : null;
        for (OnlineLyricsProvider provider : providers) {
            Optional<OnlineLyricsResult> result = provider.search(track, duration, http);
            if (result.isPresent() && result.get().hasLyrics()) {
                OnlineLyricsResult found = result.get();
                Lyrics lyrics = LrcParser.parse("联网歌词：" + found.source(), found.rawLyrics());
                return new LyricsLookupResult(lyrics, found.artworkUrl());
            }
        }
        return null;
    }

    public CompletableFuture<LyricsLookupResult> searchOnlineAsync(Track track, long durationMillis) {
        return CompletableFuture.supplyAsync(() -> searchOnline(track, durationMillis), executor);
    }

    public void close() {
        executor.shutdownNow();
    }
}
