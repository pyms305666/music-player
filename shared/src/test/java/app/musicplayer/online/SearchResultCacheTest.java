package app.musicplayer.online;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SearchResultCacheTest {
    private OnlineSearchSnapshot result(String query, OnlineSearchSnapshot.Outcome outcome) {
        return new OnlineSearchSnapshot(query, List.of(), List.of(new OnlineSearchSnapshot.Source("source", outcome)),
                outcome == OnlineSearchSnapshot.Outcome.COMPLETE ? OnlineSearchSnapshot.State.EMPTY : OnlineSearchSnapshot.State.FAILED, false);
    }
    @Test void normalizesKeysAndExpiresSuccessfulEmptyResults() {
        var clock = new AtomicLong(); var cache = new SearchResultCache(clock::get);
        cache.put(result("  ARTIST　夜曲  ", OnlineSearchSnapshot.Outcome.COMPLETE));
        assertTrue(cache.get("artist 夜曲").cached());
        clock.set(SearchResultCache.TTL_NANOS);
        assertNull(cache.get("artist 夜曲")); assertEquals(0, cache.size());
    }
    @Test void doesNotCacheFailuresTimeoutsOrCancellation() {
        var cache = new SearchResultCache(() -> 0L);
        for (var outcome : OnlineSearchSnapshot.Outcome.values()) {
            if (outcome != OnlineSearchSnapshot.Outcome.COMPLETE) cache.put(result(outcome.name(), outcome));
        }
        assertEquals(0, cache.size());
    }
    @Test void evictsLeastRecentlyUsedQueryAtFiftyAndPurgesAllExpiredEntries() {
        var clock = new AtomicLong(); var cache = new SearchResultCache(clock::get);
        for (int i = 0; i < 50; i++) cache.put(result("q" + i, OnlineSearchSnapshot.Outcome.COMPLETE));
        assertNotNull(cache.get("q0"));
        cache.put(result("q50", OnlineSearchSnapshot.Outcome.COMPLETE));
        assertNull(cache.get("q1")); assertNotNull(cache.get("q0")); assertEquals(50, cache.size());
        clock.set(SearchResultCache.TTL_NANOS); assertEquals(0, cache.size());
    }
}
