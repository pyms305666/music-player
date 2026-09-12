package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiguSourceProviderTest {
    /** 基于 2026-09 实测 search_all.do 响应裁剪。 */
    private static final String SEARCH_JSON =
            "{\"code\":\"000000\",\"info\":\"成功\",\"songResultData\":{\"totalCount\":\"401\","
                    + "\"correct\":[],\"resultType\":\"2\",\"result\":["
                    + "{\"id\":\"3790007\",\"resourceType\":\"2\",\"contentId\":\"600902000006889366\","
                    + "\"copyrightId\":\"60054701923\",\"name\":\"晴天\","
                    + "\"singers\":[{\"id\":\"112\",\"name\":\"周杰伦\"}],"
                    + "\"albums\":[{\"id\":\"8592\",\"name\":\"叶惠美\",\"type\":\"1\"}],"
                    + "\"imgItems\":[{\"imgSizeType\":\"01\",\"img\":\"https://d.musicapp.migu.cn/cover-small.webp\"},"
                    + "{\"imgSizeType\":\"02\",\"img\":\"https://d.musicapp.migu.cn/cover-large.webp\"}]},"
                    + "{\"id\":\"1140505222\",\"resourceType\":\"2\",\"contentId\":\"600929000000096577\","
                    + "\"copyrightId\":\"60054704965\",\"name\":\"圣诞星（feat. 杨瑞代）\","
                    + "\"singers\":[{\"id\":\"112\",\"name\":\"周杰伦\"}],\"albums\":[{\"id\":\"1140505221\","
                    + "\"name\":\"圣诞星\",\"type\":\"1\"}]}"
                    + "]}}";

    @Test
    void parsesSearchResults() {
        List<OnlineTrackInfo> results = MiguSourceProvider.parseSearchResponse(SEARCH_JSON);

        assertEquals(2, results.size());
        OnlineTrackInfo first = results.get(0);
        assertEquals("咪咕音乐", first.source());
        assertEquals("600902000006889366", first.primaryId());
        assertEquals("60054701923", first.secondaryId());
        assertEquals("晴天", first.title());
        assertEquals("周杰伦", first.artist());
        assertEquals("叶惠美", first.album());
        assertEquals("https://d.musicapp.migu.cn/cover-small.webp", first.artworkUrl());
    }

    @Test
    void deduplicatesByContentId() {
        String duplicated = SEARCH_JSON.replace(
                "\"id\":\"1140505222\"", "\"id\":\"3790007\"")
                .replace("600929000000096577", "600902000006889366");

        List<OnlineTrackInfo> results = MiguSourceProvider.parseSearchResponse(duplicated);

        assertEquals(1, results.size());
    }

    @Test
    void handlesBlankJson() {
        assertTrue(MiguSourceProvider.parseSearchResponse(null).isEmpty());
        assertTrue(MiguSourceProvider.parseSearchResponse("").isEmpty());
    }

    @Test
    void parsesListenUrlPlayUrl() {
        String json = "{\"code\":\"000000\",\"info\":\"操作成功\",\"data\":{\"songItem\":{"
                + "\"songId\":\"3790007\",\"songName\":\"晴天\"},"
                + "\"url\":\"https://freetyst.nf.migu.cn/public/product9th/\\u6807\\u6e05\\u9ad8\\u6e05"
                + "/MP3_128_16_Stero/6005753G176132921.mp3\"}}";

        assertEquals(
                "https://freetyst.nf.migu.cn/public/product9th/标清高清"
                        + "/MP3_128_16_Stero/6005753G176132921.mp3",
                MiguSourceProvider.parsePlayUrl(json));
    }

    @Test
    void returnsNullWhenDataMissing() {
        assertNull(MiguSourceProvider.parsePlayUrl("{\"code\":\"200002\",\"info\":\"参数校验失败\"}"));
        assertNull(MiguSourceProvider.parsePlayUrl(null));
    }

    @Test
    void rewrites128kSegmentTo320k() {
        String pq = "https://freetyst.nf.migu.cn/public/a/标清高清/MP3_128_16_Stero/song.mp3";

        assertEquals(
                "https://freetyst.nf.migu.cn/public/a/标清高清/MP3_320_16_Stero/song.mp3",
                MiguSourceProvider.highQualityCandidate(pq));
    }

    @Test
    void skipsRewriteWhenSegmentMissing() {
        assertNull(MiguSourceProvider.highQualityCandidate(
                "https://freetyst.nf.migu.cn/public/a/歌曲下载/flac/song.flac"));
        assertNull(MiguSourceProvider.highQualityCandidate(null));
    }

    @Test
    void rewritesFtpFormToHttpsCdn() {
        assertEquals(
                "https://freetyst.nf.migu.cn/public/a/%E6%AD%8C%E6%9B%B2%E4%B8%8B%E8%BD%BD/flac/song.flac",
                MiguSourceProvider.normalizeCdnUrl(
                        "ftp://218.200.160.122:21/public/a/歌曲下载/flac/song.flac"));
        assertEquals(
                "https://freetyst.nf.migu.cn/keep.mp3",
                MiguSourceProvider.normalizeCdnUrl("https://freetyst.nf.migu.cn/keep.mp3"));
    }

    @Test
    void percentEncodesNonAsciiPaths() {
        String encoded = MiguSourceProvider.normalizeCdnUrl(
                "https://freetyst.nf.migu.cn/标清高清/a.mp3");

        assertEquals("https://freetyst.nf.migu.cn/%E6%A0%87%E6%B8%85%E9%AB%98%E6%B8%85/a.mp3", encoded);
    }
}
