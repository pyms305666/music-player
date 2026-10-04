package app.musicplayer.artwork;

import javafx.scene.image.Image;
import java.util.LinkedHashMap;
import java.util.Map;

/** Access-ordered decoded images, bounded independently of compressed file size. */
public final class DecodedArtworkCache {
    public static final int MAX_IMAGES = 8;
    public static final long MAX_BYTES = 16L * 1024 * 1024;
    private final Map<String, Image> images = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;

    public synchronized Image get(String key) { return images.get(key); }

    public synchronized void put(String key, Image image) {
        if (image == null || image.isError()) return;
        Image old = images.remove(key);
        if (old != null) bytes -= weight(old);
        long size = weight(image);
        if (size > MAX_BYTES) return;
        images.put(key, image);
        bytes += size;
        var entries = images.entrySet().iterator();
        while (images.size() > MAX_IMAGES || bytes > MAX_BYTES) {
            bytes -= weight(entries.next().getValue());
            entries.remove();
        }
    }

    public synchronized int size() { return images.size(); }
    public synchronized long bytes() { return bytes; }
    public synchronized void clear() { images.clear(); bytes = 0; }
    private static long weight(Image image) { return (long) image.getWidth() * (long) image.getHeight() * 4; }
}
