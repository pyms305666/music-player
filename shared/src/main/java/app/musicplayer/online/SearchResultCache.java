package app.musicplayer.online;

import app.musicplayer.util.BoundedExpiringCache;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 在线查询快照缓存。规范化查询键，只保存可缓存的成功结果，按容量和期限限制占用。 Bounded successful-query cache, with an injectable
 * monotonic clock.
 */
final class SearchResultCache {
    static final int MAX_QUERIES = 50;
    static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(2);
    private final BoundedExpiringCache<String, OnlineSearchSnapshot> entries;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    SearchResultCache(LongSupplier clock) {
        entries = new BoundedExpiringCache<>(MAX_QUERIES, clock);
    }

    /** 生成当前数据的稳定键，供去重、索引或缓存使用。 */
    static String key(String query) {
        if (query == null) return "";
        StringBuilder normalized = new StringBuilder(query.length());
        boolean space = false;
        for (int offset = 0; offset < query.length(); ) {
            int codePoint = query.codePointAt(offset);
            offset += Character.charCount(codePoint);
            // Android uses ICU regex and does not support Java's (?U) flag.
            if (Character.isWhitespace(codePoint)
                    || Character.isSpaceChar(codePoint)
                    || codePoint == 0x85) {
                space = normalized.length() > 0;
            } else {
                if (space) normalized.append(' ');
                normalized.appendCodePoint(codePoint);
                space = false;
            }
        }
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    /** 按规范化查询取得仍有效的成功搜索快照，未命中时返回空值。 */
    OnlineSearchSnapshot get(String query) {
        OnlineSearchSnapshot snapshot = entries.get(key(query));
        return snapshot == null ? null : snapshot.asCached();
    }

    /** 保存键对应的值，并按缓存容量及有效期规则维护已有条目。 */
    void put(OnlineSearchSnapshot snapshot) {
        if (!snapshot.cacheable()) return;
        entries.put(key(snapshot.query()), snapshot, TTL_NANOS);
    }

    /** 返回当前集合或缓存的条目数量。 */
    int size() {
        return entries.size();
    }

    /** 清空当前集合或缓存，为后续操作保留可复用的组件。 */
    void clear() {
        entries.clear();
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    void close() {
        entries.close();
    }
}
