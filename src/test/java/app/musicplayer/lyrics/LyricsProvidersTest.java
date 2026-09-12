package app.musicplayer.lyrics;

import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricsProvidersTest {
    private static final Track TRACK = new Track(Path.of("周杰伦 - 晴天.mp3"));

    /** 用固定响应充当 HTTP 层，离线验证各歌词渠道的解析逻辑。 */
    private static LyricsHttp stub(Map<String, String> responses) {
        return (url, referer) -> {
            for (Map.Entry<String, String> entry : responses.entrySet()) {
                if (url.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            throw new IllegalStateException("no stub for " + url);
        };
    }

    @Test
    void neteaseSearchesSongThenFetchesLyric() {
        LyricsHttp http = stub(Map.of(
                "/api/search/get/web",
                "{\"songs\":[{\"id\":186016,\"name\":\"晴天\",\"artists\":[{\"name\":\"周杰伦\"}],"
                        + "\"album\":{\"name\":\"叶惠美\",\"picUrl\":\"https://p.example/cover.jpg\"}}]}",
                "/api/song/lyric",
                "{\"lrc\":{\"lyric\":\"[00:01.00]刮风这天\"}}"));

        Optional<OnlineLyricsResult> result = new NeteaseMusicProvider().search(TRACK, null, http);

        assertTrue(result.isPresent());
        assertTrue(result.get().rawLyrics().contains("刮风这天"));
        assertTrue(result.get().source().contains("网易云音乐"));
        assertEquals("https://p.example/cover.jpg", result.get().artworkUrl());
    }

    @Test
    void qqSearchesSongThenFetchesLyric() {
        LyricsHttp http = stub(Map.of(
                "client_search_cp",
                "{\"song\":{\"list\":[{\"songmid\":\"0039MnYb0qxYhV\",\"songname\":\"晴天\","
                        + "\"singer\":[{\"name\":\"周杰伦\"}],\"albummid\":\"002Neh8l0uciQZ\"}]}}",
                "fcg_query_lyric_new",
                "{\"lyric\":\"[00:01.00]故事的小黄花\"}"));

        Optional<OnlineLyricsResult> result = new QqMusicProvider().search(TRACK, null, http);

        assertTrue(result.isPresent());
        assertTrue(result.get().rawLyrics().contains("故事的小黄花"));
        assertTrue(result.get().source().contains("QQ音乐"));
        assertNotNull(result.get().artworkUrl());
        assertTrue(result.get().artworkUrl().contains("002Neh8l0uciQZ"));
    }

    @Test
    void kugouReadsLyricFromPlayData() {
        LyricsHttp http = stub(Map.of(
                "song_search_v2",
                "{\"data\":{\"lists\":[{\"FileHash\":\"AAAA\",\"AlbumID\":\"1293\",\"SongName\":\"晴天\","
                        + "\"SingerName\":\"周杰伦\",\"Image\":\"http://img.example/x.jpg\"}]}}",
                "play/getdata",
                "{\"lyrics\":\"[00:01.00]刮风这天\"}"));

        Optional<OnlineLyricsResult> result = new KugouMusicProvider().search(TRACK, null, http);

        assertTrue(result.isPresent());
        assertTrue(result.get().rawLyrics().contains("刮风这天"));
        assertTrue(result.get().source().contains("酷狗音乐"));
    }

    @Test
    void kugouFallsBackToLyricsDownloadWhenPlayDataEmpty() {
        String encoded = Base64.getEncoder()
                .encodeToString("[00:01.00]备用歌词".getBytes(StandardCharsets.UTF_8));
        LyricsHttp http = stub(Map.of(
                "song_search_v2",
                "{\"data\":{\"lists\":[{\"FileHash\":\"AAAA\",\"AlbumID\":\"1293\",\"SongName\":\"晴天\","
                        + "\"SingerName\":\"周杰伦\"}]}}",
                "play/getdata",
                "{}",
                "lyrics.kugou.com/search",
                "{\"candidates\":[{\"id\":\"123\",\"accesskey\":\"KEY\"}]}",
                "lyrics.kugou.com/download",
                "{\"content\":\"" + encoded + "\"}"));

        Optional<OnlineLyricsResult> result = new KugouMusicProvider().search(TRACK, null, http);

        assertTrue(result.isPresent());
        assertTrue(result.get().rawLyrics().contains("备用歌词"));
    }

    @Test
    void lrclibPrefersSyncedOverPlainLyrics() {
        LyricsHttp http = stub(Map.of(
                "/api/search",
                "[{\"trackName\":\"Other\",\"artistName\":\"X\",\"syncedLyrics\":\"\",\"plainLyrics\":\"plain\","
                        + "},{\"trackName\":\"晴天\",\"artistName\":\"周杰伦\","
                        + "\"syncedLyrics\":\"[00:01.00]lrc-line\",\"plainLyrics\":\"plain2\"}]"));

        Optional<OnlineLyricsResult> result = new LrclibLyricsProvider().search(TRACK, null, http);

        assertTrue(result.isPresent());
        assertTrue(result.get().rawLyrics().contains("lrc-line"));
        assertTrue(result.get().source().contains("LRCLIB"));
    }

    @Test
    void lrclibReturnsEmptyWhenNoCandidates() {
        LyricsHttp http = stub(Map.of("/api/search", "[]"));

        assertTrue(new LrclibLyricsProvider().search(TRACK, null, http).isEmpty());
    }
}
