package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

class SearchResponseTest {
    private record Response(Function<String, List<OnlineTrackInfo>> parser, String empty, String failed) { }
    private List<Response> responses() {
        return List.of(
                new Response(KugouSourceProvider::parseSearchResponse, "{\"status\":1,\"data\":{\"lists\":[]}}", "{\"status\":0,\"data\":{\"lists\":[]}}"),
                new Response(MiguSourceProvider::parseSearchResponse, "{\"code\":\"000000\",\"songResultData\":{\"result\":[]}}", "{\"code\":\"200002\",\"songResultData\":{\"result\":[]}}"),
                new Response(KuwoSourceProvider::parseSearchResponse, "{'abslist':[]}", "{'error':'rate limit'}"),
                new Response(QqSourceProvider::parseSearchResponse, "{\"code\":0,\"data\":{\"song\":{\"itemlist\":[]}}}", "{\"code\":429,\"data\":{\"song\":{\"itemlist\":[]}}}"),
                new Response(NeteaseSourceProvider::parseSearchResponse, "{\"code\":200,\"result\":{\"songs\":[]}}", "{\"code\":429,\"result\":{\"songs\":[]}}"));
    }
    @Test void everySourceDistinguishesValidEmptySearchFromErrorsAndMissingPayload() {
        for (var response : responses()) {
            assertTrue(response.parser().apply(response.empty()).isEmpty());
            for (String invalid : List.of("", "<html>gateway failure</html>", "{\"unexpected\":true}", "{\"code\":429}", response.failed())) {
                assertThrows(IllegalStateException.class, () -> response.parser().apply(invalid), invalid);
            }
            assertThrows(IllegalStateException.class, () -> response.parser().apply(response.empty().substring(0, response.empty().length() - 1)));
            assertThrows(IllegalStateException.class, () -> response.parser().apply("<html>" + response.empty() + "</html>"));
        }
    }
    @Test void neteaseAllowsExplicitZeroSongCountWithoutAnArray() {
        assertTrue(NeteaseSourceProvider.parseSearchResponse("{\"code\":200,\"result\":{\"songCount\":0}}").isEmpty());
        assertThrows(IllegalStateException.class, () -> NeteaseSourceProvider.parseSearchResponse("{\"code\":200,\"result\":{\"songCount\":1}}"));
    }
    @Test void kuwoRejectsTruncatedOrWronglyTypedArrays() {
        assertThrows(IllegalStateException.class, () -> KuwoSourceProvider.parseSearchResponse("{'abslist':["));
        assertThrows(IllegalStateException.class, () -> KuwoSourceProvider.parseSearchResponse("{'abslist':null,'other':[]}"));
    }
    @Test void qqAndNeteaseParseValidResultsThroughTheSameValidatedBoundary() {
        var qq = QqSourceProvider.parseSearchResponse("{\"code\":0,\"data\":{\"song\":{\"itemlist\":[{\"mid\":\"song\",\"name\":\"夜曲\",\"singer\":\"歌手\"}]}}}");
        assertEquals("song", qq.get(0).primaryId()); assertEquals("夜曲", qq.get(0).title());
        var netease = NeteaseSourceProvider.parseSearchResponse("{\"code\":200,\"result\":{\"songCount\":1,\"songs\":[{\"id\":123,\"name\":\"夜曲\",\"artists\":[{\"name\":\"歌手\"}]}]}}");
        assertEquals("123", netease.get(0).primaryId()); assertEquals("歌手", netease.get(0).artist());
    }
    @Test void sourceParseErrorsProduceFailedSnapshotsAndNeverEnterTheQueryCache() {
        for (var response : responses()) {
            try (var crawler = new MusicCrawler(List.of(OnlineTestFixtures.provider("source", () -> response.parser().apply("<html>busy</html>"))), 1000)) {
                var snapshot = crawler.search("song"); assertTrue(snapshot.isEmpty());
                var complete = crawler.searchIncrementally("song", ignored -> { });
                assertEquals(OnlineSearchSnapshot.State.FAILED, complete.state()); assertEquals(1, complete.failedSources());
                var cache = new SearchResultCache(() -> 0L); cache.put(complete); assertEquals(0, cache.size());
            }
        }
    }
}
