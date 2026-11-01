package app.musicplayer.playlist;

/**
 * 共享播放队列索引算法。处理上一首、下一首和首尾回绕，也处理尚未选择歌曲时的起始索引。 Shared circular queue boundaries; filtering the UI
 * never changes this queue.
 */
public final class QueueOrder {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private QueueOrder() {}

    /** 计算队列中相对移动的索引，支持首尾回绕及尚未选择歌曲的状态。 */
    public static int relative(int current, int direction, int count) {
        if (count <= 0) throw new IllegalArgumentException("Queue is empty");
        if (current < 0) return direction < 0 ? count - 1 : 0;
        return Math.floorMod(current + direction, count);
    }
}
