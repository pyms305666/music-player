package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicCrawlerTest {
    @Test
    void keepsSameSongFromDifferentSourcesForDownloadFallback() {
        OnlineTrackInfo kuwo = track("酷我音乐", "kuwo-id");
        OnlineTrackInfo netease = track("网易云音乐", "netease-id");

        assertEquals(List.of(kuwo, netease), MusicCrawler.deduplicate(List.of(kuwo, netease)));
    }

    @Test
    void removesRepeatedResultFromTheSameSource() {
        OnlineTrackInfo first = track("QQ音乐", "same-id");
        OnlineTrackInfo duplicate = track("QQ音乐", "same-id");

        assertEquals(List.of(first), MusicCrawler.deduplicate(List.of(first, duplicate)));
    }

    @Test
    void detectsFlacExtensionWithoutDot() {
        OnlineTrackInfo kuwo = track("酷我音乐", "228908");
        assertEquals(".flac", MusicCrawler.guessExtension(kuwo,
                "http://kw-er.kuwo.cn/abc/6aa4f2e8/resource/30106/trackmedia/F000000bYDlc2XxKLsflac?bitrate$2000"));
        assertEquals(".mp3", MusicCrawler.guessExtension(kuwo,
                "http://kw-er.kuwo.cn/abc/6aa4f2e8/resource/30106/trackmedia/M800000bYDlc2XxKLsmp3?bitrate$320"));
    }

    @Test
    void keepsLegacyExtensionRules() {
        OnlineTrackInfo qq = track("QQ音乐", "mid");
        OnlineTrackInfo netease = track("网易云音乐", "id");
        assertEquals(".m4a", MusicCrawler.guessExtension(qq, "http://aqqmusic.tc.qq.com/amobile.music.tc.qq.com/M8000.m4a"));
        assertEquals(".mp3", MusicCrawler.guessExtension(netease,
                "https://music.163.com/song/media/outer/url?id=1.mp3"));
    }

    private static OnlineTrackInfo track(String source, String id) {
        return new OnlineTrackInfo(source, "天黑黑", "孙燕姿", "", null, id, null);
    }
}
