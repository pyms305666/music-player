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

        // 近乎纯灰白的封面没有可靠色相，回退默认幽蓝；淡色封面保留真实色相，
        // 由下方饱和度钳制抬到可见水平，避免色相归零产生脏红光晕。
        if (avgColor.getSaturation() < 0.03) {
            return DEFAULT_AMBIENT_COLOR;
        }

        double hue = avgColor.getHue();
        double saturation = Math.max(0.30, Math.min(0.70, avgColor.getSaturation()));
        double brightness = Math.max(0.15, Math.min(0.35, avgColor.getBrightness()));

        return Color.hsb(hue, saturation, brightness);
    }

    /**
     * 从封面提取流体光效色板：整体平均色 + 四象限区域色，共 5 个。
     * 所有颜色统一走"高亮度钳制 + 近灰回退默认幽蓝"：深色封面按方案兜底为幽蓝光晕，
     * 而不是把钳制后的暗色直接拿来渲染（暗色色团在深底上会完全隐身）。
     */
    public static Color[] extractPalette(Image image) {
        Color[] palette = new Color[5];
        if (image == null || image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0) {
            for (int i = 0; i < palette.length; i++) palette[i] = DEFAULT_AMBIENT_COLOR;
            return palette;
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int midX = width / 2;
        int midY = height / 2;
        palette[0] = regionColor(image, 0, 0, width, height, DEFAULT_AMBIENT_COLOR);
        palette[1] = regionColor(image, 0, 0, midX, midY, palette[0]);
        palette[2] = regionColor(image, midX, 0, width, midY, palette[0]);
        palette[3] = regionColor(image, 0, midY, midX, height, palette[0]);
        palette[4] = regionColor(image, midX, midY, width, height, palette[0]);
        return palette;
    }

    /** 计算图像某个矩形区域的夹取后平均色；区域近乎无色时沿用整体平均色。 */
    private static Color regionColor(Image image, int x0, int y0, int x1, int y1, Color fallback) {
        PixelReader reader;
        try {
            reader = image.getPixelReader();
        } catch (Exception e) {
            return fallback;
        }
        if (reader == null) return fallback;
        double totalR = 0;
        double totalG = 0;
        double totalB = 0;
        int count = 0;
        int stepX = Math.max(1, (x1 - x0) / 8);
        int stepY = Math.max(1, (y1 - y0) / 8);
        for (int y = y0; y < y1; y += stepY) {
            for (int x = x0; x < x1; x += stepX) {
                Color c = reader.getColor(x, y);
                if (c.getOpacity() < 0.1) continue;
                totalR += c.getRed();
                totalG += c.getGreen();
                totalB += c.getBlue();
                count++;
            }
        }
        if (count == 0) return fallback;
        Color avg =
                Color.color(
                        Math.min(1.0, totalR / count),
                        Math.min(1.0, totalG / count),
                        Math.min(1.0, totalB / count));
        if (avg.getSaturation() < 0.03) return fallback;
        // 色团颜色必须保持明亮鲜活：亮度压太低会在深底上完全隐身（验收中踩过的坑）。
        double saturation = Math.max(0.45, Math.min(0.85, avg.getSaturation()));
        double brightness = Math.max(0.50, Math.min(0.85, avg.getBrightness()));
        return Color.hsb(avg.getHue(), saturation, brightness);
    }
}
