package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** QueueOrder 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：共享播放队列索引算法。处理上一首、下一首和首尾回绕，也处理尚未选择歌曲时的起始索引。 */
class QueueOrderTest {
    /** 验证队列两个方向均可回绕，并处理尚未选择歌曲的起点。 */
    @Test
    void wrapsInBothDirectionsAndStartsCorrectlyWithoutSelection() {
        assertEquals(0, QueueOrder.relative(2, 1, 3));
        assertEquals(2, QueueOrder.relative(0, -1, 3));
        assertEquals(0, QueueOrder.relative(-1, 1, 3));
        assertEquals(2, QueueOrder.relative(-1, -1, 3));
        assertEquals(0, QueueOrder.relative(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> QueueOrder.relative(-1, 1, 0));
    }
}
