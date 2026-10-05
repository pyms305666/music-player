package app.musicplayer.online;

import app.musicplayer.util.BoundedExpiringCache;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** A cached failure is a result too; it must remain distinct from a cache miss. */
final class ResolutionCache {
    static final int MAX_ENTRIES = 500;
    static final long SUCCESS_NANOS = TimeUnit.MINUTES.toNanos(4);
    static final long FAILURE_NANOS = TimeUnit.SECONDS.toNanos(45);
    record Result(String url) { }
    private final BoundedExpiringCache<String, Result> entries;
    ResolutionCache(LongSupplier clock) { entries = new BoundedExpiringCache<>(MAX_ENTRIES, clock); }
    Result get(String key) { return entries.get(key); }
    void put(String key, String url) {
        boolean success = url != null && !url.isBlank();
        entries.put(key, new Result(success ? url : null), success ? SUCCESS_NANOS : FAILURE_NANOS);
    }
    void purge() { entries.purge(); }
    int size() { return entries.size(); }
    void close() { entries.close(); }
}
