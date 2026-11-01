package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.model.OnlineTrackInfo;

import org.junit.jupiter.api.Test;

/** 固定爬虫原有换源规则，职责拆分不会改变下载候选选择。 */
class FallbackTrackMatcherTest {
    /** 其他来源先返回超过六项时，酷我和咪咕仍须进入换源候选并排在最前。 */
    @Test
    void preferredSourcesAreSelectedBeforeLimitingFallbackCandidates() {
        var others =
                java.util.stream.IntStream.range(0, 7)
                        .mapToObj(index -> track("其他", "other" + index, "歌名", "歌手"))
                        .toList();
        var kuwo = track("酷我音乐", "kuwo", "歌名", "歌手");
        var migu = track("咪咕音乐", "migu", "歌名", "歌手");
        var providers =
                java.util.List.of(
                        OnlineTestFixtures.provider("其他", () -> others),
                        OnlineTestFixtures.provider("咪咕音乐", () -> java.util.List.of(migu)),
                        OnlineTestFixtures.provider("酷我音乐", () -> java.util.List.of(kuwo)));
        try (var crawler = new MusicCrawler(providers, 1000)) {
            var candidates =
                    crawler.fallbackCandidates(
                            track("原来源", "original", "歌名", "歌手"), java.util.Set.of());
            assertEquals(6, candidates.size());
            assertEquals(kuwo.source(), candidates.get(0).source());
            assertEquals(migu.source(), candidates.get(1).source());
        }
    }

    /** 歌名规范化相同可匹配；包含关系还需要相同歌手，片段类名称始终排除。 */
    @Test
    void normalizedTitleArtistAndRejectedFragmentsKeepExistingRules() {
        OnlineTrackInfo original = track("source-one", "1", "歌名（说明）", "歌手");
        assertTrue(
                FallbackTrackMatcher.strongFallbackMatch(
                        original, track("two", "2", "歌 名", "其他歌手")));
        assertTrue(
                FallbackTrackMatcher.strongFallbackMatch(
                        original, track("two", "2", "歌名延长", "歌手")));
        assertFalse(
                FallbackTrackMatcher.strongFallbackMatch(
                        original, track("two", "2", "歌名延长", "别人")));
        assertFalse(
                FallbackTrackMatcher.strongFallbackMatch(
                        original, track("two", "2", "歌名伴奏", "歌手")));
        assertFalse(FallbackTrackMatcher.strongFallbackMatch(original, null));
    }

    /** 只有来源与主 ID 同时相同才表示原来源同一首歌。 */
    @Test
    void sameTrackRequiresBothSourceAndPrimaryId() {
        OnlineTrackInfo original = track("one", "1", "歌名", "歌手");
        assertTrue(FallbackTrackMatcher.sameOnlineTrack(original, track("one", "1", "另一名称", "歌手")));
        assertFalse(FallbackTrackMatcher.sameOnlineTrack(original, track("two", "1", "歌名", "歌手")));
        assertFalse(FallbackTrackMatcher.sameOnlineTrack(original, track("one", "2", "歌名", "歌手")));
    }

    /** 创建来源身份明确的可尝试下载记录。 */
    private static OnlineTrackInfo track(String source, String id, String title, String artist) {
        return new OnlineTrackInfo(source, title, artist, "", "", id, "");
    }
}
