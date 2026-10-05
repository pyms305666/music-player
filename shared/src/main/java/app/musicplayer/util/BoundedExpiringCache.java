package app.musicplayer.util;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Access-ordered cache with per-entry lifetimes and an injectable monotonic clock. */
public final class BoundedExpiringCache<K, V> implements AutoCloseable {
    private record Entry<V>(V value, long expiresAt) { }
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final int capacity;
    private final LongSupplier clock;
    private boolean closed;

    public BoundedExpiringCache(int capacity, LongSupplier clock) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock);
    }
    public synchronized V get(K key) {
        if (closed) return null;
        purge(clock.getAsLong());
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value();
    }
    public synchronized void put(K key, V value, long lifetimeNanos) {
        if (closed) return;
        if (lifetimeNanos <= 0) throw new IllegalArgumentException("Positive lifetime required");
        long now = clock.getAsLong();
        purge(now);
        entries.put(Objects.requireNonNull(key), new Entry<>(Objects.requireNonNull(value), now + lifetimeNanos));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }
    private void purge(long now) { entries.values().removeIf(entry -> now - entry.expiresAt() >= 0); }
    public synchronized void purge() { purge(clock.getAsLong()); }
    public synchronized int size() { purge(clock.getAsLong()); return entries.size(); }
    public synchronized void clear() { entries.clear(); }
    @Override public synchronized void close() { closed = true; entries.clear(); }
}
