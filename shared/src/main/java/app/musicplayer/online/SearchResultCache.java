package app.musicplayer.online;

import app.musicplayer.util.BoundedExpiringCache;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Bounded successful-query cache, with an injectable monotonic clock. */
final class SearchResultCache {
    static final int MAX_QUERIES = 50;
    static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(2);
    private final BoundedExpiringCache<String, OnlineSearchSnapshot> entries;
    SearchResultCache(LongSupplier clock) { entries = new BoundedExpiringCache<>(MAX_QUERIES, clock); }
    static String key(String query) {
        if (query == null) return "";
        StringBuilder normalized = new StringBuilder(query.length());
        boolean space = false;
        for (int offset = 0; offset < query.length();) {
            int codePoint = query.codePointAt(offset);
            offset += Character.charCount(codePoint);
            // Android uses ICU regex and does not support Java's (?U) flag.
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x85) {
                space = normalized.length() > 0;
            } else {
                if (space) normalized.append(' ');
                normalized.appendCodePoint(codePoint);
                space = false;
            }
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }
    OnlineSearchSnapshot get(String query) {
        OnlineSearchSnapshot snapshot = entries.get(key(query));
        return snapshot == null ? null : snapshot.asCached();
    }
    void put(OnlineSearchSnapshot snapshot) {
        if (!snapshot.cacheable()) return;
        entries.put(key(snapshot.query()), snapshot, TTL_NANOS);
    }
    int size() { return entries.size(); }
    void clear() { entries.clear(); }
    void close() { entries.close(); }
}
