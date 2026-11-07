package app.musicplayer.artwork;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import org.junit.jupiter.api.Test;

/**
 * DominantColorExtractor 的单元测试。
 * 验证桌面端封面主色调提取、色彩边界安全夹取（Saturation & Brightness Clamping）及异常处理。
 */
class DominantColorExtractorTest {

    @Test
    void nullImageReturnsDefaultAmbientColor() {
        Color color = DominantColorExtractor.extract(null);
        assertEquals(DominantColorExtractor.DEFAULT_AMBIENT_COLOR, color);
    }

    @Test
    void transparentImageReturnsDefaultAmbientColor() {
        WritableImage transparent = new WritableImage(16, 16);
        Color color = DominantColorExtractor.extract(transparent);
        assertEquals(DominantColorExtractor.DEFAULT_AMBIENT_COLOR, color);
    }

    @Test
    void brightWhiteImageFallsBackToDefaultAmbientColor() {
        WritableImage white = new WritableImage(32, 32);
        PixelWriter writer = white.getPixelWriter();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                writer.setColor(x, y, Color.WHITE);
            }
        }

        // 近灰白封面饱和度低于 0.10，应回退默认幽蓝，避免色相归零产生脏红光晕。
        Color color = DominantColorExtractor.extract(white);
        assertEquals(DominantColorExtractor.DEFAULT_AMBIENT_COLOR, color);
    }

    @Test
    void pureBlackImageFallsBackToDefaultAmbientColor() {
        WritableImage black = new WritableImage(32, 32);
        PixelWriter writer = black.getPixelWriter();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                writer.setColor(x, y, Color.BLACK);
            }
        }

        Color color = DominantColorExtractor.extract(black);
        assertEquals(DominantColorExtractor.DEFAULT_AMBIENT_COLOR, color);
    }

    @Test
    void pureRedImagePreservesHueWithinTolerance() {
        WritableImage red = new WritableImage(32, 32);
        PixelWriter writer = red.getPixelWriter();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                writer.setColor(x, y, Color.RED);
            }
        }

        Color color = DominantColorExtractor.extract(red);
        assertNotNull(color);
        double hue = color.getHue();
        assertTrue(hue <= 5.0 || hue >= 355.0, "Red hue should be around 0 deg, actual: " + hue);
        assertEquals(0.70, color.getSaturation(), 0.01, "Saturation should be clamped to max 0.70");
        assertEquals(0.35, color.getBrightness(), 0.01, "Brightness should be clamped to max 0.35");
    }

    @Test
    void pureBlueImagePreservesHueWithinTolerance() {
        WritableImage blue = new WritableImage(32, 32);
        PixelWriter writer = blue.getPixelWriter();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                writer.setColor(x, y, Color.BLUE);
            }
        }

        Color color = DominantColorExtractor.extract(blue);
        assertNotNull(color);
        assertEquals(240.0, color.getHue(), 5.0, "Blue hue should be ~240 deg");
        assertEquals(0.70, color.getSaturation(), 0.01, "Saturation should be clamped to max 0.70");
        assertEquals(0.35, color.getBrightness(), 0.01, "Brightness should be clamped to max 0.35");
    }
}
