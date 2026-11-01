package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.model.OnlineTrackInfo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

/**
 * SearchResponse
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：来源搜索响应的结构校验。区分接口失败、缺字段与合法空数组，保证错误响应不会作为成功搜索进入缓存。
 */
class SearchResponseTest {
    /** 专辑和专辑歌手的同名字段先出现时，仍读取歌曲和演唱者自身的 ID 与名称。 */
    @Test
    void neteaseReadsSongFieldsFromTheirOwnObject() {
        String json =
                "{\"code\":200,\"result\":{\"songs\":[{\"album\":{\"artist\":{\"id\":0,\"name\":\"\"},\"id\":99,\"name\":\"专辑\"},\"artists\":[{\"id\":77,\"name\":\"黄霄雲\"}],\"name\":\"星辰大海\",\"id\":123}]}}";
        var track = NeteaseSourceProvider.parseSearchResponse(json).get(0);
        assertEquals("123", track.primaryId());
        assertEquals("星辰大海", track.title());
        assertEquals("黄霄雲", track.artist());
        assertEquals("专辑", track.album());
    }

    /** 歌手搜索返回长列表时，三个来源及酷狗后备解析都应保留前 50 首，而不是截在旧的 8～10 首。 */
    @Test
    void longArtistSearchListsRetainFiftySongs() {
        var numbers = java.util.stream.IntStream.rangeClosed(1, 55).boxed().toList();
        String kugou =
                numbers.stream()
                        .map(i -> "{\"FileHash\":\"hash" + i + "\",\"SongName\":\"song" + i + "\"}")
                        .collect(java.util.stream.Collectors.joining(","));
        String netease =
                numbers.stream()
                        .map(i -> "{\"id\":" + i + ",\"name\":\"song" + i + "\"}")
                        .collect(java.util.stream.Collectors.joining(","));
        String kuwo =
                numbers.stream()
                        .map(i -> "{'MUSICRID':'MUSIC_" + i + "','NAME':'song" + i + "'}")
                        .collect(java.util.stream.Collectors.joining(","));
        var kg =
                KugouSourceProvider.parseSearchResponse(
                        "{\"status\":1,\"data\":{\"lists\":[" + kugou + "]}}");
        var kgFallback = KugouSourceProvider.parseSearchResponseByRegex("[" + kugou + "]");
        var wy =
                NeteaseSourceProvider.parseSearchResponse(
                        "{\"code\":200,\"result\":{\"songs\":[" + netease + "]}}");
        var kw = KuwoSourceProvider.parseSearchResponse("{'abslist':[" + kuwo + "]}");
        for (var results : List.of(kg, kgFallback, wy, kw)) {
            assertEquals(50, results.size());
            assertEquals("song50", results.get(49).title());
        }
    }

    /** 组合测试 HTTP 响应的状态、请求头及正文。 */
    private record Response(
            Function<String, List<OnlineTrackInfo>> parser, String empty, String failed) {}

    /** 提供同一结构边界所需的正反向响应样本。 */
    private List<Response> responses() {
        return List.of(
                new Response(
                        KugouSourceProvider::parseSearchResponse,
                        "{\"status\":1,\"data\":{\"lists\":[]}}",
                        "{\"status\":0,\"data\":{\"lists\":[]}}"),
                new Response(
                        MiguSourceProvider::parseSearchResponse,
                        "{\"code\":\"000000\",\"songResultData\":{\"result\":[]}}",
                        "{\"code\":\"200002\",\"songResultData\":{\"result\":[]}}"),
                new Response(
                        KuwoSourceProvider::parseSearchResponse,
                        "{'abslist':[]}",
                        "{'error':'rate limit'}"),
                new Response(
                        QqSourceProvider::parseSearchResponse,
                        "{\"code\":0,\"data\":{\"song\":{\"itemlist\":[]}}}",
                        "{\"code\":429,\"data\":{\"song\":{\"itemlist\":[]}}}"),
                new Response(
                        NeteaseSourceProvider::parseSearchResponse,
                        "{\"code\":200,\"result\":{\"songs\":[]}}",
                        "{\"code\":429,\"result\":{\"songs\":[]}}"));
    }

    /** 验证各来源区分合法空搜索、错误状态和缺失数据。 */
    @Test
    void everySourceDistinguishesValidEmptySearchFromErrorsAndMissingPayload() {
        for (var response : responses()) {
            assertTrue(response.parser().apply(response.empty()).isEmpty());
            for (String invalid :
                    List.of(
                            "",
                            "<html>gateway failure</html>",
                            "{\"unexpected\":true}",
                            "{\"code\":429}",
                            response.failed())) {
                assertThrows(
                        IllegalStateException.class,
                        () -> response.parser().apply(invalid),
                        invalid);
            }
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            response.parser()
                                    .apply(
                                            response.empty()
                                                    .substring(0, response.empty().length() - 1)));
            assertThrows(
                    IllegalStateException.class,
                    () -> response.parser().apply("<html>" + response.empty() + "</html>"));
        }
    }

    /** 验证网易云显式零歌曲数允许缺少结果数组。 */
    @Test
    void neteaseAllowsExplicitZeroSongCountWithoutAnArray() {
        assertTrue(
                NeteaseSourceProvider.parseSearchResponse(
                                "{\"code\":200,\"result\":{\"songCount\":0}}")
                        .isEmpty());
        assertThrows(
                IllegalStateException.class,
                () ->
                        NeteaseSourceProvider.parseSearchResponse(
                                "{\"code\":200,\"result\":{\"songCount\":1}}"));
    }

    /** 验证酷我拒绝截断或类型错误的结果数组。 */
    @Test
    void kuwoRejectsTruncatedOrWronglyTypedArrays() {
        assertThrows(
                IllegalStateException.class,
                () -> KuwoSourceProvider.parseSearchResponse("{'abslist':["));
        assertThrows(
                IllegalStateException.class,
                () -> KuwoSourceProvider.parseSearchResponse("{'abslist':null,'other':[]}"));
    }

    /** 验证 QQ 和网易云通过同一结构校验边界解析合法结果。 */
    @Test
    void qqAndNeteaseParseValidResultsThroughTheSameValidatedBoundary() {
        var qq =
                QqSourceProvider.parseSearchResponse(
                        "{\"code\":0,\"data\":{\"song\":{\"itemlist\":[{\"mid\":\"song\",\"name\":\"夜曲\",\"singer\":\"歌手\"}]}}}");
        assertEquals("song", qq.get(0).primaryId());
        assertEquals("夜曲", qq.get(0).title());
        var netease =
                NeteaseSourceProvider.parseSearchResponse(
                        "{\"code\":200,\"result\":{\"songCount\":1,\"songs\":[{\"id\":123,\"name\":\"夜曲\",\"artists\":[{\"name\":\"歌手\"}]}]}}");
        assertEquals("123", netease.get(0).primaryId());
        assertEquals("歌手", netease.get(0).artist());
    }

    /** 验证来源解析错误产生失败快照且不进入成功缓存。 */
    @Test
    void sourceParseErrorsProduceFailedSnapshotsAndNeverEnterTheQueryCache() {
        for (var response : responses()) {
            try (var crawler =
                    new MusicCrawler(
                            List.of(
                                    OnlineTestFixtures.provider(
                                            "source",
                                            () -> response.parser().apply("<html>busy</html>"))),
                            1000)) {
                var snapshot = crawler.search("song");
                assertTrue(snapshot.isEmpty());
                var complete = crawler.searchIncrementally("song", ignored -> {});
                assertEquals(OnlineSearchSnapshot.State.FAILED, complete.state());
                assertEquals(1, complete.failedSources());
                var cache = new SearchResultCache(() -> 0L);
                cache.put(complete);
                assertEquals(0, cache.size());
            }
        }
    }
}
