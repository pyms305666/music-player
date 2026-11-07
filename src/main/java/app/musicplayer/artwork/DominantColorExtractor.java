package app.musicplayer.artwork;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

/**
 * 桌面端封面主色调提取器。
 * 对封面图像进行快速采样，计算平均 RGB 并转换为 HSB，
 * 并对饱和度（0.30 - 0.70）与明度（0.15 - 0.35）进行安全夹取，防止暗淡无光或高亮眩光。
 */
public final class DominantColorExtractor {
    public static final Color DEFAULT_AMBIENT_COLOR = Color.rgb(79, 165, 255);

    private DominantColorExtractor() {}

    /**
     * 从给定的图像中提取主色调并夹取到舒适的环境光范围。
     *
     * @param image 封面图像
     * @return 经过安全夹取的环境光颜色；图像为空、出错或不可读时返回默认环境色
     */
    public static Color extract(Image image) {
        if (image == null || image.isError()) {
            return DEFAULT_AMBIENT_COLOR;
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        if (width <= 0 || height <= 0) {
            return DEFAULT_AMBIENT_COLOR;
        }
        PixelReader reader;
        try {
            reader = image.getPixelReader();
        } catch (Exception e) {
            return DEFAULT_AMBIENT_COLOR;
        }
        if (reader == null) {
            return DEFAULT_AMBIENT_COLOR;
        }

        double totalR = 0;
        double totalG = 0;
        double totalB = 0;
        int count = 0;

        int stepX = Math.max(1, width / 16);
        int stepY = Math.max(1, height / 16);

        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                Color c = reader.getColor(x, y);
                if (c.getOpacity() < 0.1) {
                    continue;
                }
                totalR += c.getRed();
                totalG += c.getGreen();
                totalB += c.getBlue();
                count++;
            }
        }

        if (count == 0) {
            return DEFAULT_AMBIENT_COLOR;
        }

        double avgR = Math.min(1.0, Math.max(0.0, totalR / count));
        double avgG = Math.min(1.0, Math.max(0.0, totalG / count));
        double avgB = Math.min(1.0, Math.max(0.0, totalB / count));
        Color avgColor = Color.color(avgR, avgG, avgB);

        // 近灰白封面没有可靠色相，直接回退默认幽蓝，避免 HSB 色相归零时产生脏红色光晕。
        if (avgColor.getSaturation() < 0.10) {
            return DEFAULT_AMBIENT_COLOR;
        }

        double hue = avgColor.getHue();
        double saturation = Math.max(0.30, Math.min(0.70, avgColor.getSaturation()));
        double brightness = Math.max(0.15, Math.min(0.35, avgColor.getBrightness()));

        return Color.hsb(hue, saturation, brightness);
    }
}
