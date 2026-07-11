package app.musicplayer.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobileWindowSizerTest {
    @Test
    void calculatesNineBySixteenDimensions() {
        assertEquals(720.0, MobileWindowSizer.heightForWidth(405.0));
        assertEquals(405.0, MobileWindowSizer.widthForHeight(720.0));
        assertEquals(640.0, MobileWindowSizer.heightForWidth(360.0));
    }
}
