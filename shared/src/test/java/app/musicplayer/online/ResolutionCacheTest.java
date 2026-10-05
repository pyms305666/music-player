package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionCacheTest {
    @Test void preservesSuccessAndFailureLifetimesWithoutConfusingFailureWithMiss() {
        var clock = new AtomicLong(); var cache = new ResolutionCache(clock::get);
        cache.put("success", "https://fixture/audio"); cache.put("failure", " ");
        assertNotNull(cache.get("failure")); assertNull(cache.get("failure").url());
        clock.set(ResolutionCache.FAILURE_NANOS);
        assertNull(cache.get("failure")); assertEquals("https://fixture/audio", cache.get("success").url());
        clock.set(ResolutionCache.SUCCESS_NANOS); cache.purge(); assertEquals(0, cache.size());
    }
    @Test void keepsAtMost500AddressesAndRefreshesAccessOrder() {
        var cache = new ResolutionCache(() -> 0L);
        for (int i = 0; i < 500; i++) cache.put("song-" + i, "https://fixture/" + i);
        assertNotNull(cache.get("song-0")); cache.put("song-500", "https://fixture/500");
        assertNull(cache.get("song-1")); assertNotNull(cache.get("song-0")); assertEquals(500, cache.size());
    }
    @Test void activePurgeAndCloseReleaseExpiredAndLateResults() {
        var clock = new AtomicLong(); var cache = new ResolutionCache(clock::get);
        cache.put("failed", null); clock.set(ResolutionCache.FAILURE_NANOS);
        cache.purge(); assertEquals(0, cache.size());
        cache.close(); cache.put("late", "https://fixture/late"); assertEquals(0, cache.size());
    }
}
