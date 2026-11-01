package app.musicplayer.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * BoundedExpiringCache
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：通用有容量及有效期限制的缓存。支持读取、写入、主动清理和关闭，使用传入时钟便于确定性测试。
 */
class BoundedExpiringCacheTest {
    /** 验证有效期边界全部到期，容量淘汰遵循最近访问顺序。 */
    @Test
    void expiresAllEntriesAtBoundaryAndEvictsLeastRecentlyAccessed() {
        var clock = new AtomicLong(100);
        var cache = new BoundedExpiringCache<String, String>(2, clock::get);
        cache.put("a", "A", 10);
        cache.put("b", "B", 20);
        assertEquals("A", cache.get("a"));
        cache.put("c", "C", 30);
        assertNull(cache.get("b"));
        assertEquals(2, cache.size());
        clock.set(110);
        cache.purge();
        assertNull(cache.get("a"));
        assertEquals(1, cache.size());
        clock.set(130);
        assertEquals(0, cache.size());
    }

    /** 验证单调时钟溢出前后仍按时间差判断有效期。 */
    @Test
    void expirationUsesMonotonicDifferencesAcrossClockOverflow() {
        var clock = new AtomicLong(Long.MAX_VALUE - 5);
        var cache = new BoundedExpiringCache<String, String>(2, clock::get);
        cache.put("x", "value", 10);
        clock.addAndGet(9);
        assertEquals("value", cache.get("x"));
        clock.incrementAndGet();
        assertNull(cache.get("x"));
    }

    /** 验证关闭丢弃迟到写入，清空后则仍可复用缓存。 */
    @Test
    void closeDiscardsLateWritesWhileClearAllowsReuse() {
        var cache = new BoundedExpiringCache<String, String>(1, () -> 0L);
        cache.put("x", "a", 10);
        cache.clear();
        cache.put("x", "b", 10);
        assertEquals("b", cache.get("x"));
        cache.close();
        cache.put("x", "late", 10);
        assertNull(cache.get("x"));
        assertEquals(0, cache.size());
    }

    /** 验证拒绝非法缓存策略，并发写入不超过容量。 */
    @Test
    void rejectsInvalidPoliciesAndNeverExceedsCapacityUnderConcurrentWrites() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new BoundedExpiringCache<>(0, () -> 0L));
        var cache = new BoundedExpiringCache<Integer, Integer>(25, System::nanoTime);
        assertThrows(IllegalArgumentException.class, () -> cache.put(1, 1, 0));
        var workers = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                int offset = i * 1_000;
                tasks.add(
                        workers.submit(
                                () -> {
                                    for (int key = offset; key < offset + 1_000; key++) {
                                        cache.put(
                                                key,
                                                key,
                                                java.util.concurrent.TimeUnit.MINUTES.toNanos(1));
                                        assertTrue(cache.size() <= 25);
                                    }
                                }));
            }
            for (var task : tasks) task.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            cache.close();
        }
    }
}
