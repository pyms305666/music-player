package app.musicplayer.artwork;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import java.net.URI;

/** Subsample while reading: JavaFX's requested size alone still decodes a full PNG first. */
public final class ArtworkDecoder {
    public static final int MAX_EDGE = 1024;
    private ArtworkDecoder() { }

    public static Image decode(String source, int width, int height) {
        try (var input = URI.create(source).toURL().openStream();
             var imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null || Thread.currentThread().isInterrupted()) return null;
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int targetWidth = Math.max(1, Math.min(MAX_EDGE, width));
                int targetHeight = Math.max(1, Math.min(MAX_EDGE, height));
                int step = Math.max(1, (int) Math.ceil(Math.max(
                        (double) reader.getWidth(0) / targetWidth, (double) reader.getHeight(0) / targetHeight)));
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(step, step, 0, 0);
                var pixels = reader.read(0, parameters);
                if (Thread.currentThread().isInterrupted()) return null;
                int w = pixels.getWidth(), h = pixels.getHeight();
                WritableImage image = new WritableImage(w, h);
                image.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(),
                        pixels.getRGB(0, 0, w, h, null, 0, w), 0, w);
                return image;
            } finally { reader.dispose(); }
        } catch (java.io.IOException | IllegalArgumentException invalid) { return null; }
    }
}
