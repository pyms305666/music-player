package app.musicplayer.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * PlaybackControls 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：JavaFX
 * 播放控制栏。组合播放按钮、进度、音量、时间与歌曲信息，处理拖动和直接点击滑块的交互。
 */
class PlaybackControlsTest {
    /** 验证可见轨道任意位置正确映射到滑块范围。 */
    @Test
    void mapsAnyVisibleTrackPositionToTheSliderRange() {
        assertEquals(0.0, PlaybackControls.valueAtPosition(20, 20, 200, 0, 1));
        assertEquals(0.5, PlaybackControls.valueAtPosition(120, 20, 200, 0, 1));
        assertEquals(1.0, PlaybackControls.valueAtPosition(220, 20, 200, 0, 1));
    }

    /** 验证滑块位置使用手柄实际所在的轨道中心线。 */
    @Test
    void mapsTheTrackCenterlineUsedByTheThumb() {
        double visibleTrackStart = 6;
        double visibleTrackWidth = 84;
        double capRadius = 3;

        assertEquals(
                0.0,
                PlaybackControls.valueAtPosition(
                        visibleTrackStart,
                        visibleTrackStart + capRadius,
                        visibleTrackWidth - capRadius * 2,
                        0,
                        1));
        assertEquals(
                1.0,
                PlaybackControls.valueAtPosition(
                        visibleTrackStart + visibleTrackWidth,
                        visibleTrackStart + capRadius,
                        visibleTrackWidth - capRadius * 2,
                        0,
                        1));
    }

    /** 验证轨道外点击被限制在滑块有效范围。 */
    @Test
    void clampsClicksOutsideTheVisibleTrack() {
        assertEquals(10.0, PlaybackControls.valueAtPosition(-50, 20, 200, 10, 30));
        assertEquals(30.0, PlaybackControls.valueAtPosition(500, 20, 200, 10, 30));
    }

    /** 验证非法窗口尺寸使用最小可用值。 */
    @Test
    void fallsBackToTheMinimumForInvalidGeometry() {
        assertEquals(0.0, PlaybackControls.valueAtPosition(50, 0, 0, 0, 1));
        assertEquals(0.0, PlaybackControls.valueAtPosition(Double.NaN, 0, 100, 0, 1));
    }
}
