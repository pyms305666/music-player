package app.musicplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 验证桌面顺序播放在末曲及单曲队列中的回绕行为。 */
class MusicPlayerAppTest {
    /** 验证顺序播放在末曲后回到第一首。 */
    @Test
    void sequentialPlaybackWrapsAfterLastTrack() {
        assertEquals(1, MusicPlayerApp.nextOrderedIndex(0, 3));
        assertEquals(2, MusicPlayerApp.nextOrderedIndex(1, 3));
        assertEquals(0, MusicPlayerApp.nextOrderedIndex(2, 3));
    }

    /** 验证顺序播放在单曲队列中保持同一首。 */
    @Test
    void sequentialPlaybackRepeatsSingleTrack() {
        assertEquals(0, MusicPlayerApp.nextOrderedIndex(0, 1));
    }
}
