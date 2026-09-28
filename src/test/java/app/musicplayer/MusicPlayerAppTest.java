package app.musicplayer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicPlayerAppTest {
    @Test
    void sequentialPlaybackWrapsAfterLastTrack() {
        assertEquals(1, MusicPlayerApp.nextOrderedIndex(0, 3));
        assertEquals(2, MusicPlayerApp.nextOrderedIndex(1, 3));
        assertEquals(0, MusicPlayerApp.nextOrderedIndex(2, 3));
    }

    @Test
    void sequentialPlaybackRepeatsSingleTrack() {
        assertEquals(0, MusicPlayerApp.nextOrderedIndex(0, 1));
    }
}
