package app.musicplayer.util;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 通用有容量及有效期限制的缓存。支持读取、写入、主动清理和关闭，使用传入时钟便于确定性测试。 Access-ordered cache with per-entry lifetimes and
 * an injectable monotonic clock.
 */
public final class BoundedExpiringCache<K, V> implements AutoCloseable {
    /** 表示一条歌单或歌曲记录。 */
    private record Entry<V>(V value, long expiresAt) {}

    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final int capacity;
    private final LongSupplier clock;
    private boolean closed;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public BoundedExpiringCache(int capacity, LongSupplier clock) {
        if (capacity < 1) throw new IllegalArgumentException("Positive capacity required");
        this.capacity = capacity;
        this.clock = Objects.requireNonNull(clock);
    }

    /** 读取未过期的缓存项；过期或不存在时返回空值。 */
    public synchronized V get(K key) {
        if (closed) return null;
        purge(clock.getAsLong());
        Entry<V> entry = entries.get(key);
        return entry == null ? null : entry.value();
    }

    /** 保存键对应的值，并按缓存容量及有效期规则维护已有条目。 */
    public synchronized void put(K key, V value, long lifetimeNanos) {
        if (closed) return;
        if (lifetimeNanos <= 0) throw new IllegalArgumentException("Positive lifetime required");
        long now = clock.getAsLong();
        purge(now);
        entries.put(
                Objects.requireNonNull(key),
                new Entry<>(Objects.requireNonNull(value), now + lifetimeNanos));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }

    /** 移除已经过期的缓存项，保留仍在有效期内的数据。 */
    private void purge(long now) {
        entries.values().removeIf(entry -> now - entry.expiresAt() >= 0);
    }

    /** 移除已经过期的缓存项，保留仍在有效期内的数据。 */
    public synchronized void purge() {
        purge(clock.getAsLong());
    }

    /** 返回当前集合或缓存的条目数量。 */
    public synchronized int size() {
        purge(clock.getAsLong());
        return entries.size();
    }

    /** 清空当前集合或缓存，为后续操作保留可复用的组件。 */
    public synchronized void clear() {
        entries.clear();
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public synchronized void close() {
        closed = true;
        entries.clear();
    }
}
