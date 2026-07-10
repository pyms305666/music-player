package app.musicplayer.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaybackControlsTest {
    @Test
    void mapsAnyVisibleTrackPositionToTheSliderRange() {
        assertEquals(0.0, PlaybackControls.valueAtPosition(20, 20, 200, 0, 1));
        assertEquals(0.5, PlaybackControls.valueAtPosition(120, 20, 200, 0, 1));
        assertEquals(1.0, PlaybackControls.valueAtPosition(220, 20, 200, 0, 1));
    }

    @Test
    void mapsTheTrackCenterlineUsedByTheThumb() {
        double visibleTrackStart = 6;
        double visibleTrackWidth = 84;
        double capRadius = 3;

        assertEquals(0.0, PlaybackControls.valueAtPosition(
                visibleTrackStart,
                visibleTrackStart + capRadius,
                visibleTrackWidth - capRadius * 2,
                0,
                1));
        assertEquals(1.0, PlaybackControls.valueAtPosition(
                visibleTrackStart + visibleTrackWidth,
                visibleTrackStart + capRadius,
                visibleTrackWidth - capRadius * 2,
                0,
                1));
    }

    @Test
    void clampsClicksOutsideTheVisibleTrack() {
        assertEquals(10.0, PlaybackControls.valueAtPosition(-50, 20, 200, 10, 30));
        assertEquals(30.0, PlaybackControls.valueAtPosition(500, 20, 200, 10, 30));
    }

    @Test
    void fallsBackToTheMinimumForInvalidGeometry() {
        assertEquals(0.0, PlaybackControls.valueAtPosition(50, 0, 0, 0, 1));
        assertEquals(0.0, PlaybackControls.valueAtPosition(Double.NaN, 0, 100, 0, 1));
    }
}
