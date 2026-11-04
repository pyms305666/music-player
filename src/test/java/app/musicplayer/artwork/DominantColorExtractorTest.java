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
    void brightWhiteImageClampedToMaxBrightness() {
        WritableImage white = new WritableImage(32, 32);
        PixelWriter writer = white.getPixelWriter();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                writer.setColor(x, y, Color.WHITE);
            }
        }

        Color color = DominantColorExtractor.extract(white);
        assertNotNull(color);
        assertTrue(color.getBrightness() <= 0.45, "Max brightness must be clamped <= 0.45, actual: " + color.getBrightness());
        assertTrue(color.getBrightness() >= 0.20, "Brightness must be >= 0.20, actual: " + color.getBrightness());
        assertTrue(color.getSaturation() >= 0.30 && color.getSaturation() <= 0.70,
                "Saturation must be clamped in [0.30, 0.70], actual: " + color.getSaturation());
    }

    @Test
    void pureBlackImageClampedToMinBrightness() {
        WritableImage black = new WritableImage(32, 32);
        PixelWriter writer = black.getPixelWriter();
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                writer.setColor(x, y, Color.BLACK);
            }
        }

        Color color = DominantColorExtractor.extract(black);
        assertNotNull(color);
        assertTrue(color.getBrightness() >= 0.20, "Min brightness must be clamped >= 0.20, actual: " + color.getBrightness());
        assertTrue(color.getBrightness() <= 0.40, "Brightness must be <= 0.40, actual: " + color.getBrightness());
        assertTrue(color.getSaturation() >= 0.30 && color.getSaturation() <= 0.70,
                "Saturation must be clamped in [0.30, 0.70], actual: " + color.getSaturation());
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
        assertEquals(0.40, color.getBrightness(), 0.01, "Brightness should be clamped to max 0.40");
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
        assertEquals(0.40, color.getBrightness(), 0.01, "Brightness should be clamped to max 0.40");
    }
}
