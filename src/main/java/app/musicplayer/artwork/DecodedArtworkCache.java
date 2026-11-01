package app.musicplayer.artwork;

import javafx.scene.image.Image;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 桌面已解码图像的内存缓存。按估计像素字节数限制占用，避免只按图片数量限制导致大图占用过多内存。 Access-ordered decoded images, bounded
 * independently of compressed file size.
 */
public final class DecodedArtworkCache {
    public static final int MAX_IMAGES = 8;
    public static final long MAX_BYTES = 16L * 1024 * 1024;
    private final Map<String, Image> images = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;

    /** 按当前组件的键或上下文取得已有值；缓存的期限规则见类说明。 */
    public synchronized Image get(String key) {
        return images.get(key);
    }

    /** 保存键对应的值，并按缓存容量及有效期规则维护已有条目。 */
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

    /** 返回当前集合或缓存的条目数量。 */
    public synchronized int size() {
        return images.size();
    }

    /** 返回或格式化字节数，供缓存占用或下载进度显示。 */
    public synchronized long bytes() {
        return bytes;
    }

    /** 清空当前集合或缓存，为后续操作保留可复用的组件。 */
    public synchronized void clear() {
        images.clear();
        bytes = 0;
    }

    /** 估算已解码图片占用的像素字节数。 */
    private static long weight(Image image) {
        return (long) image.getWidth() * (long) image.getHeight() * 4;
    }
}
