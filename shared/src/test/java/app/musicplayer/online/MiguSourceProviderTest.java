package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import app.musicplayer.model.OnlineTrackInfo;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * MiguSourceProvider
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：咪咕歌曲来源适配器。解析来源身份、封面与播放地址，检测加密或不可播放的数据，避免把错误响应当作音频。
 */
class MiguSourceProviderTest {
    /** 本地 HTTP 服务模拟三页、跨页重复及第二页失败，验证真实请求的页码与保留结果。 */
    @Test
    void searchesSeveralPagesDeduplicatesAndKeepsFirstPageOnLaterFailure() throws Exception {
        var server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var pages = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        var failSecond = new java.util.concurrent.atomic.AtomicBoolean();
        server.createContext(
                "/search",
                exchange -> {
                    String query = exchange.getRequestURI().getRawQuery();
                    int page = query.contains("pageNo=3") ? 3 : query.contains("pageNo=2") ? 2 : 1;
                    pages.add(page);
                    if (page == 2 && failSecond.get()) {
                        exchange.sendResponseHeaders(503, -1);
                        exchange.close();
                        return;
                    }
                    int start = page == 1 ? 1 : page == 2 ? 20 : 40;
                    int end = page == 1 ? 20 : page == 2 ? 39 : 42;
                    String items =
                            java.util.stream.IntStream.rangeClosed(start, end)
                                    .mapToObj(
                                            i ->
                                                    "{\"contentId\":\"id"
                                                            + i
                                                            + "\",\"name\":\"song"
                                                            + i
                                                            + "\"}")
                                    .collect(java.util.stream.Collectors.joining(","));
                    byte[] body =
                            ("{\"code\":\"000000\",\"songResultData\":{\"result\":["
                                            + items
                                            + "]}}")
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body);
                    }
                });
        server.start();
        try (var session = new CrawlerSession()) {
            var provider =
                    new MiguSourceProvider(
                            session,
                            "http://127.0.0.1:" + server.getAddress().getPort() + "/search");
            var results = provider.search("黄霄云");
            assertEquals(List.of(1, 2, 3), pages);
            assertEquals(42, results.size());
            assertEquals("song42", results.get(41).title());
            pages.clear();
            failSecond.set(true);
            assertEquals(20, provider.search("黄霄云").size());
            assertEquals(List.of(1, 2), pages);
        } finally {
            server.stop(0);
        }
    }

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
                + "{\"id\":\"1140505222\",\"resourceType\":\"2\",\"contentId\":\"600929000000096577\",\"copyrightId\":\"60054704965\",\"name\":\"圣诞星（feat."
                + " 杨瑞代）\","
                + "\"singers\":[{\"id\":\"112\",\"name\":\"周杰伦\"}],\"albums\":[{\"id\":\"1140505221\","
                + "\"name\":\"圣诞星\",\"type\":\"1\"}]}]}}";

    /** 验证来源搜索响应转为统一歌曲模型。 */
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

    /** 验证按来源稳定歌曲身份去重。 */
    @Test
    void deduplicatesByContentId() {
        String duplicated =
                SEARCH_JSON
                        .replace("\"id\":\"1140505222\"", "\"id\":\"3790007\"")
                        .replace("600929000000096577", "600902000006889366");

        List<OnlineTrackInfo> results = MiguSourceProvider.parseSearchResponse(duplicated);

        assertEquals(1, results.size());
    }

    /** 验证空白响应被视为解析失败。 */
    @Test
    void rejectsBlankJson() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> MiguSourceProvider.parseSearchResponse(null));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> MiguSourceProvider.parseSearchResponse(""));
    }

    /** 验证来源播放地址响应解析。 */
    @Test
    void parsesListenUrlPlayUrl() {
        String json =
                "{\"code\":\"000000\",\"info\":\"操作成功\",\"data\":{\"songItem\":{"
                    + "\"songId\":\"3790007\",\"songName\":\"晴天\"},"
                    + "\"url\":\"https://freetyst.nf.migu.cn/public/product9th/\\u6807\\u6e05\\u9ad8\\u6e05"
                    + "/MP3_128_16_Stero/6005753G176132921.mp3\"}}";

        assertEquals(
                "https://freetyst.nf.migu.cn/public/product9th/标清高清"
                        + "/MP3_128_16_Stero/6005753G176132921.mp3",
                MiguSourceProvider.parsePlayUrl(json));
    }

    /** 验证缺少数据或播放地址时返回空值。 */
    @Test
    void returnsNullWhenDataMissing() {
        assertNull(MiguSourceProvider.parsePlayUrl("{\"code\":\"200002\",\"info\":\"参数校验失败\"}"));
        assertNull(MiguSourceProvider.parsePlayUrl(null));
    }

    /** 验证咪咕地址的既有码率片段改写规则。 */
    @Test
    void rewrites128kSegmentTo320k() {
        String pq = "https://freetyst.nf.migu.cn/public/a/标清高清/MP3_128_16_Stero/song.mp3";

        assertEquals(
                "https://freetyst.nf.migu.cn/public/a/标清高清/MP3_320_16_Stero/song.mp3",
                MiguSourceProvider.highQualityCandidate(pq));
    }

    /** 验证地址不含目标码率片段时保持原值。 */
    @Test
    void skipsRewriteWhenSegmentMissing() {
        assertNull(
                MiguSourceProvider.highQualityCandidate(
                        "https://freetyst.nf.migu.cn/public/a/歌曲下载/flac/song.flac"));
        assertNull(MiguSourceProvider.highQualityCandidate(null));
    }

    /** 验证旧 FTP 形式地址转换为已有 HTTPS CDN 形式。 */
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

    /** 验证非 ASCII URL 路径按百分号编码。 */
    @Test
    void percentEncodesNonAsciiPaths() {
        String encoded =
                MiguSourceProvider.normalizeCdnUrl("https://freetyst.nf.migu.cn/标清高清/a.mp3");

        assertEquals(
                "https://freetyst.nf.migu.cn/%E6%A0%87%E6%B8%85%E9%AB%98%E6%B8%85/a.mp3", encoded);
    }
}
