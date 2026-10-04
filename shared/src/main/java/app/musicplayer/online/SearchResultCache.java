package app.musicplayer.online;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Bounded successful-query cache, with an injectable monotonic clock. */
final class SearchResultCache {
    static final int MAX_QUERIES = 50;
    static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(2);
    private record Entry(OnlineSearchSnapshot snapshot, long expiresAt) { }
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private final LongSupplier clock;
    SearchResultCache(LongSupplier clock) { this.clock = clock; }
    static String key(String query) {
        return query == null ? "" : query.strip().replaceAll("(?U)\\s+", " ").toLowerCase(Locale.ROOT);
    }
    synchronized OnlineSearchSnapshot get(String query) {
        purge();
        Entry entry = entries.get(key(query));
        return entry == null ? null : entry.snapshot().asCached();
    }
    synchronized void put(OnlineSearchSnapshot snapshot) {
        if (!snapshot.cacheable()) return;
        purge();
        entries.put(key(snapshot.query()), new Entry(snapshot, clock.getAsLong() + TTL_NANOS));
        while (entries.size() > MAX_QUERIES) entries.remove(entries.keySet().iterator().next());
    }
    private void purge() { long now = clock.getAsLong(); entries.values().removeIf(entry -> entry.expiresAt() <= now); }
    synchronized int size() { purge(); return entries.size(); }
    synchronized void clear() { entries.clear(); }
}
