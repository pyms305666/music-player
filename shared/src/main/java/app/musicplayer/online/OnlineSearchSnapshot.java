package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;

/**
 * 增量搜索的不可变快照。关联查询、累计歌曲、各来源结果、整体状态和缓存标记，供两端渐进展示。 Immutable results in arrival order, with source
 * outcomes independent of UI wording.
 */
public record OnlineSearchSnapshot(
        String query,
        List<OnlineTrackInfo> tracks,
        List<Source> sources,
        State state,
        boolean cached) {
    /** 表示任务状态。 */
    public enum State {
        SEARCHING,
        COMPLETE,
        EMPTY,
        PARTIAL_FAILURE,
        FAILED,
        CANCELLED
    }

    /** 表示搜索来源的成功、失败、超时或取消结果。 */
    public enum Outcome {
        PENDING,
        COMPLETE,
        FAILED,
        TIMED_OUT,
        SUSPENDED,
        CANCELLED
    }

    /** 保存单来源搜索状态。 */
    public record Source(String name, Outcome outcome) {}

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public OnlineSearchSnapshot {
        tracks = List.copyOf(tracks);
        sources = List.copyOf(sources);
    }

    /** 检查搜索快照是否已经结束，不再等待其他来源。 */
    public boolean finished() {
        return state != State.SEARCHING;
    }

    /** 统计来源状态，供进度和失败提示使用。 */
    public long completedSources() {
        return sources.stream().filter(s -> s.outcome() != Outcome.PENDING).count();
    }

    /** 统计来源状态，供进度和失败提示使用。 */
    public long failedSources() {
        return sources.stream()
                .filter(
                        s ->
                                s.outcome() == Outcome.FAILED
                                        || s.outcome() == Outcome.TIMED_OUT
                                        || s.outcome() == Outcome.SUSPENDED)
                .count();
    }

    /** 检查结果是否满足成功查询缓存的条件。 */
    public boolean cacheable() {
        return finished()
                && state != State.CANCELLED
                && !sources.isEmpty()
                && sources.stream().allMatch(s -> s.outcome() == Outcome.COMPLETE);
    }

    /** 保留查询结果内容，标记它来自已有缓存。 */
    public OnlineSearchSnapshot asCached() {
        return new OnlineSearchSnapshot(query, tracks, sources, state, true);
    }
}
