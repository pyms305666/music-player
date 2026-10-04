import app.musicplayer.artwork.ArtworkPresenter;
import app.musicplayer.artwork.ArtworkDecoder;
import app.musicplayer.artwork.DecodedArtworkCache;
import javafx.application.Platform;
import javafx.scene.image.Image;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.Locale;

/** Compare decode footprint for the same large local image, without network noise. */
public final class ArtworkBenchmark {
    private static volatile Image retained;
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Path fixture = directory.resolve("large-cover.png");
        if (!Files.exists(fixture)) {
            BufferedImage image = new BufferedImage(4000, 4000, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 4000; y++) for (int x = 0; x < 4000; x++)
                image.setRGB(x, y, ((x * 255 / 4000) << 16) | (y * 255 / 4000 << 8));
            ImageIO.write(image, "png", fixture.toFile());
        }
        Platform.startup(() -> {});
        try {
            for (String mode : new String[]{"legacy", "sized"}) {
                var cache = new DecodedArtworkCache();
                for (int batch = 1; batch <= 3; batch++) {
                    retained = null;
                    System.gc();
                    var pools = ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(pool -> pool.getType() == java.lang.management.MemoryType.HEAP).toList();
                    pools.forEach(pool -> pool.resetPeakUsage());
                    long start = System.nanoTime();
                    retained = mode.equals("legacy") ? new Image(fixture.toUri().toString())
                            : ArtworkDecoder.decode(fixture.toUri().toString(), 288, 288);
                    if (retained == null) throw new AssertionError("Artwork decode failed");
                    if (retained.isError()) throw new AssertionError(retained.getException());
                    long elapsed = System.nanoTime() - start;
                    long peak = pools.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
                    System.gc();
                    long heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
                    System.out.printf(Locale.ROOT,
                            "mode=%s batch=%d decodeMs=%.3f width=%.0f height=%.0f decodedBytes=%.0f peakHeapBytes=%d stableHeapBytes=%d%n",
                            mode, batch, elapsed / 1e6, retained.getWidth(), retained.getHeight(),
                            retained.getWidth() * retained.getHeight() * 4, peak, heap);
                    if (mode.equals("sized")) cache.put("fixture", retained);
                    start = System.nanoTime();
                    for (int i = 0; i < 10; i++) retained = mode.equals("legacy")
                            ? new Image(fixture.toUri().toString()) : cache.get("fixture");
                    System.out.printf(Locale.ROOT, "mode=%s batch=%d revisitMs=%.4f%n",
                            mode, batch, (System.nanoTime() - start) / 1e7);
                }
            }
        } finally { retained = null; Platform.exit(); }
    }
}
