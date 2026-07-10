package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicCrawlerTest {
    @Test
    void keepsSameSongFromDifferentSourcesForDownloadFallback() {
        OnlineTrackInfo qqmp3 = track("QQMP3", "qqmp3-id");
        OnlineTrackInfo netease = track("网易云音乐", "netease-id");

        assertEquals(List.of(qqmp3, netease), MusicCrawler.deduplicate(List.of(qqmp3, netease)));
    }

    @Test
    void removesRepeatedResultFromTheSameSource() {
        OnlineTrackInfo first = track("QQ音乐", "same-id");
        OnlineTrackInfo duplicate = track("QQ音乐", "same-id");

        assertEquals(List.of(first), MusicCrawler.deduplicate(List.of(first, duplicate)));
    }

    private static OnlineTrackInfo track(String source, String id) {
        return new OnlineTrackInfo(source, "天黑黑", "孙燕姿", "", null, id, null);
    }
}
