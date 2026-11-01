package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SearchResultCache 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：在线查询快照缓存。规范化查询键，只保存可缓存的成功结果，按容量和期限限制占用。
 */
class SearchResultCacheTest {
    /** 验证 Unicode 空白规范化无需平台专有正则标志。 */
    @Test
    void normalizesUnicodeWhitespaceWithoutPlatformSpecificRegexFlags() {
        assertEquals("", SearchResultCache.key(null));
        assertEquals("", SearchResultCache.key(" \t\n\u00a0\u0085\u3000"));
        assertEquals("artist 夜曲", SearchResultCache.key("\u00a0 ARTIST\u3000\t夜曲\u0085 "));
        assertEquals("歌手 夜曲", SearchResultCache.key("歌手\u2007\u202f夜曲"));
        assertEquals("🎵music 𠀀", SearchResultCache.key(" 🎵MUSIC\n𠀀 "));
    }

    /** 返回此任务或句柄关联的结果，供调用者等待或使用。 */
    private OnlineSearchSnapshot result(String query, OnlineSearchSnapshot.Outcome outcome) {
        return new OnlineSearchSnapshot(
                query,
                List.of(),
                List.of(new OnlineSearchSnapshot.Source("source", outcome)),
                outcome == OnlineSearchSnapshot.Outcome.COMPLETE
                        ? OnlineSearchSnapshot.State.EMPTY
                        : OnlineSearchSnapshot.State.FAILED,
                false);
    }

    /** 验证查询键规范化，成功空结果也遵循有效期。 */
    @Test
    void normalizesKeysAndExpiresSuccessfulEmptyResults() {
        var clock = new AtomicLong();
        var cache = new SearchResultCache(clock::get);
        cache.put(result("  ARTIST　夜曲  ", OnlineSearchSnapshot.Outcome.COMPLETE));
        assertTrue(cache.get("artist 夜曲").cached());
        clock.set(SearchResultCache.TTL_NANOS);
        assertNull(cache.get("artist 夜曲"));
        assertEquals(0, cache.size());
    }

    /** 验证失败、超时和取消不会进入成功查询缓存。 */
    @Test
    void doesNotCacheFailuresTimeoutsOrCancellation() {
        var cache = new SearchResultCache(() -> 0L);
        for (var outcome : OnlineSearchSnapshot.Outcome.values()) {
            if (outcome != OnlineSearchSnapshot.Outcome.COMPLETE)
                cache.put(result(outcome.name(), outcome));
        }
        assertEquals(0, cache.size());
    }

    /** 验证查询缓存超过五十项淘汰最久未用项，并清理全部过期项。 */
    @Test
    void evictsLeastRecentlyUsedQueryAtFiftyAndPurgesAllExpiredEntries() {
        var clock = new AtomicLong();
        var cache = new SearchResultCache(clock::get);
        for (int i = 0; i < 50; i++)
            cache.put(result("q" + i, OnlineSearchSnapshot.Outcome.COMPLETE));
        assertNotNull(cache.get("q0"));
        cache.put(result("q50", OnlineSearchSnapshot.Outcome.COMPLETE));
        assertNull(cache.get("q1"));
        assertNotNull(cache.get("q0"));
        assertEquals(50, cache.size());
        clock.set(SearchResultCache.TTL_NANOS);
        assertEquals(0, cache.size());
    }
}
