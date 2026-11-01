package app.musicplayer.online;

import app.musicplayer.util.BoundedExpiringCache;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * 歌曲下载地址解析缓存。按来源身份缓存有时效的直链或空结果，定期清理，避免重复请求解析接口。 A cached failure is a result too; it must remain
 * distinct from a cache miss.
 */
final class ResolutionCache {
    static final int MAX_ENTRIES = 500;
    static final long SUCCESS_NANOS = TimeUnit.MINUTES.toNanos(4);
    static final long FAILURE_NANOS = TimeUnit.SECONDS.toNanos(45);

    /** 组合操作结果与状态。 */
    record Result(String url) {}

    private final BoundedExpiringCache<String, Result> entries;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    ResolutionCache(LongSupplier clock) {
        entries = new BoundedExpiringCache<>(MAX_ENTRIES, clock);
    }

    /** 读取该来源歌曲仍有效的地址解析记录，没有记录时返回空值。 */
    Result get(String key) {
        return entries.get(key);
    }

    /** 保存键对应的值，并按缓存容量及有效期规则维护已有条目。 */
    void put(String key, String url) {
        boolean success = url != null && !url.isBlank();
        entries.put(key, new Result(success ? url : null), success ? SUCCESS_NANOS : FAILURE_NANOS);
    }

    /** 移除已经过期的缓存项，保留仍在有效期内的数据。 */
    void purge() {
        entries.purge();
    }

    /** 返回当前集合或缓存的条目数量。 */
    int size() {
        return entries.size();
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    void close() {
        entries.close();
    }
}
