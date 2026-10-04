package app.musicplayer.artwork;

import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecodedArtworkCacheTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @Test void samplesLargeImageAndRejectsCorruptBytes() throws Exception {
        var source = new java.awt.image.BufferedImage(2048, 1024, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xff123456);
        var path = directory.resolve("large.png");
        javax.imageio.ImageIO.write(source, "png", path.toFile());
        var image = ArtworkDecoder.decode(path.toUri().toString(), 256, 256);
        assertNotNull(image);
        assertEquals(256, image.getWidth()); assertEquals(128, image.getHeight());
        assertEquals(0xff123456, image.getPixelReader().getArgb(0, 0));
        var corrupt = java.nio.file.Files.write(directory.resolve("broken.png"), new byte[]{1, 2, 3});
        assertNull(ArtworkDecoder.decode(corrupt.toUri().toString(), 256, 256));
    }
    @Test void honorsCountByteBudgetAndRecentAccess() {
        var cache = new DecodedArtworkCache();
        for (int i = 0; i < 8; i++) cache.put("small" + i, new WritableImage(16, 16));
        cache.get("small0");
        cache.put("small8", new WritableImage(16, 16));
        assertEquals(8, cache.size());
        assertNotNull(cache.get("small0")); assertNull(cache.get("small1"));
        for (int i = 0; i < 8; i++) cache.put("large" + i, new WritableImage(1024, 1024));
        assertTrue(cache.size() <= 8);
        assertTrue(cache.bytes() <= 16L * 1024 * 1024);
        assertNull(cache.get("large0")); assertNotNull(cache.get("large7"));
        cache.clear(); assertEquals(0, cache.size()); assertEquals(0, cache.bytes());
    }

    @Test void scaleAwareDimensionsNeverExceedLimit() {
        assertArrayEquals(new int[]{288, 288}, ArtworkPresenter.decodeSize(144, 144, 2));
        assertArrayEquals(new int[]{1024, 512}, ArtworkPresenter.decodeSize(2000, 1000, 2));
        assertArrayEquals(new int[]{1, 1}, ArtworkPresenter.decodeSize(0, 0, 1));
    }
}
