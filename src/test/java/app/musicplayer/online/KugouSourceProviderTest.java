package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KugouSourceProviderTest {
    /** 基于 2026-09 实测 song_search_v2 响应裁剪。 */
    private static final String SEARCH_JSON =
            "{\"status\":1,\"error_code\":0,\"error_msg\":\"\",\"data\":{\"pagesize\":2,\"page\":1,"
                    + "\"total\":480,\"lists\":["
                    + "{\"HQFileHash\":\"1B56126A8A03924F1DD066259C095CBC\",\"HQBitrate\":320,"
                    + "\"FileHash\":\"AAAA1111222233334444555566667777\",\"SongName\":\"晴天\","
                    + "\"SingerName\":\"周杰伦\",\"Image\":\"http://imge.kugou.com/stdmusic/150/202001011.jpg\","
                    + "\"AlbumID\":\"1293\",\"AlbumId\":\"1293\"},"
                    + "{\"HQFileHash\":\"2C67...\",\"FileHash\":\"BBBB1111222233334444555566667777\","
                    + "\"SongName\":\"<em>晴天</em>\",\"SingerName\":\"周杰伦\",\"Image\":\"\","
                    + "\"AlbumID\":\"1294\"}"
                    + "]}}";

    @Test
    void parsesStructuredSearchResults() {
        List<OnlineTrackInfo> results = KugouSourceProvider.parseSearchResponse(SEARCH_JSON);

        assertEquals(2, results.size());
        OnlineTrackInfo first = results.get(0);
        assertEquals("酷狗音乐", first.source());
        assertEquals("AAAA1111222233334444555566667777", first.primaryId());
        assertEquals("晴天", first.title());
        assertEquals("周杰伦", first.artist());
        assertEquals("1293", first.secondaryId());
        assertEquals("http://imge.kugou.com/stdmusic/150/202001011.jpg", first.artworkUrl());
    }

    @Test
    void stripsHtmlHighlightFromSongName() {
        List<OnlineTrackInfo> results = KugouSourceProvider.parseSearchResponse(SEARCH_JSON);

        assertEquals("晴天", results.get(1).title());
    }

    @Test
    void handlesBlankJson() {
        assertTrue(KugouSourceProvider.parseSearchResponse(null).isEmpty());
        assertTrue(KugouSourceProvider.parseSearchResponse("").isEmpty());
    }
}
