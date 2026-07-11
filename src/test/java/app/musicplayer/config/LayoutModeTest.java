package app.musicplayer.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutModeTest {
    @Test
    void defaultsToDesktop() {
        assertEquals(LayoutMode.DESKTOP, LayoutMode.resolve(List.of(), false));
    }

    @Test
    void enablesMobileModeFromArgumentOrProperty() {
        assertEquals(LayoutMode.MOBILE, LayoutMode.resolve(List.of("--mobile"), false));
        assertEquals(LayoutMode.MOBILE, LayoutMode.resolve(List.of(), true));
    }

    @Test
    void enablesMobileModeOnAndroid() {
        assertEquals(LayoutMode.MOBILE, LayoutMode.resolve(List.of(), false, "android"));
        assertEquals(LayoutMode.DESKTOP, LayoutMode.resolve(List.of(), false, "windows"));
    }
}
