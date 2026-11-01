package app.musicplayer.artwork;

import static org.junit.jupiter.api.Assertions.*;

import javafx.scene.image.WritableImage;

import org.junit.jupiter.api.Test;

/**
 * DecodedArtworkCache
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：桌面已解码图像的内存缓存。按估计像素字节数限制占用，避免只按图片数量限制导致大图占用过多内存。
 */
class DecodedArtworkCacheTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    /** 验证大图按尺寸采样，损坏数据被拒绝。 */
    @Test
    void samplesLargeImageAndRejectsCorruptBytes() throws Exception {
        var source =
                new java.awt.image.BufferedImage(
                        2048, 1024, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xff123456);
        var path = directory.resolve("large.png");
        javax.imageio.ImageIO.write(source, "png", path.toFile());
        var image = ArtworkDecoder.decode(path.toUri().toString(), 256, 256);
        assertNotNull(image);
        assertEquals(256, image.getWidth());
        assertEquals(128, image.getHeight());
        assertEquals(0xff123456, image.getPixelReader().getArgb(0, 0));
        var corrupt =
                java.nio.file.Files.write(directory.resolve("broken.png"), new byte[] {1, 2, 3});
        assertNull(ArtworkDecoder.decode(corrupt.toUri().toString(), 256, 256));
    }

    /** 验证图片缓存同时满足数量、字节预算及最近访问规则。 */
    @Test
    void honorsCountByteBudgetAndRecentAccess() {
        var cache = new DecodedArtworkCache();
        for (int i = 0; i < 8; i++) cache.put("small" + i, new WritableImage(16, 16));
        cache.get("small0");
        cache.put("small8", new WritableImage(16, 16));
        assertEquals(8, cache.size());
        assertNotNull(cache.get("small0"));
        assertNull(cache.get("small1"));
        for (int i = 0; i < 8; i++) cache.put("large" + i, new WritableImage(1024, 1024));
        assertTrue(cache.size() <= 8);
        assertTrue(cache.bytes() <= 16L * 1024 * 1024);
        assertNull(cache.get("large0"));
        assertNotNull(cache.get("large7"));
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.bytes());
    }

    /** 验证考虑窗口缩放后的解码尺寸仍不超过上限。 */
    @Test
    void scaleAwareDimensionsNeverExceedLimit() {
        assertArrayEquals(new int[] {288, 288}, ArtworkPresenter.decodeSize(144, 144, 2));
        assertArrayEquals(new int[] {1024, 512}, ArtworkPresenter.decodeSize(2000, 1000, 2));
        assertArrayEquals(new int[] {1, 1}, ArtworkPresenter.decodeSize(0, 0, 1));
    }
}
