package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * ResolutionCache
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：歌曲下载地址解析缓存。按来源身份缓存有时效的直链或空结果，定期清理，避免重复请求解析接口。
 */
class ResolutionCacheTest {
    /** 验证成功和失败缓存各自期限，失败记录不等同于未命中。 */
    @Test
    void preservesSuccessAndFailureLifetimesWithoutConfusingFailureWithMiss() {
        var clock = new AtomicLong();
        var cache = new ResolutionCache(clock::get);
        cache.put("success", "https://fixture/audio");
        cache.put("failure", " ");
        assertNotNull(cache.get("failure"));
        assertNull(cache.get("failure").url());
        clock.set(ResolutionCache.FAILURE_NANOS);
        assertNull(cache.get("failure"));
        assertEquals("https://fixture/audio", cache.get("success").url());
        clock.set(ResolutionCache.SUCCESS_NANOS);
        cache.purge();
        assertEquals(0, cache.size());
    }

    /** 验证地址缓存最多五百项，读取更新访问顺序。 */
    @Test
    void keepsAtMost500AddressesAndRefreshesAccessOrder() {
        var cache = new ResolutionCache(() -> 0L);
        for (int i = 0; i < 500; i++) cache.put("song-" + i, "https://fixture/" + i);
        assertNotNull(cache.get("song-0"));
        cache.put("song-500", "https://fixture/500");
        assertNull(cache.get("song-1"));
        assertNotNull(cache.get("song-0"));
        assertEquals(500, cache.size());
    }

    /** 验证主动清理和关闭释放过期或迟到的缓存结果。 */
    @Test
    void activePurgeAndCloseReleaseExpiredAndLateResults() {
        var clock = new AtomicLong();
        var cache = new ResolutionCache(clock::get);
        cache.put("failed", null);
        clock.set(ResolutionCache.FAILURE_NANOS);
        cache.purge();
        assertEquals(0, cache.size());
        cache.close();
        cache.put("late", "https://fixture/late");
        assertEquals(0, cache.size());
    }
}
