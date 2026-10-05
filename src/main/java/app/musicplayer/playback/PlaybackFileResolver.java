package app.musicplayer.playback;

import app.musicplayer.util.Hashing;
import app.musicplayer.cache.GeneratedFileCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 处理下载文件扩展名与真实编码不一致的情况。
 * JavaFX 会参考扩展名选择解码器，因此把实际为 MP3 的 m4a/aac 复制为缓存 mp3 后再播放。
 */
public final class PlaybackFileResolver implements AutoCloseable {
    private final Path playbackCacheDir;
    private final AudioFileInspector inspector;
    private final GeneratedFileCache diskCache;

    public PlaybackFileResolver(Path playbackCacheDir, AudioFileInspector inspector) {
        this(playbackCacheDir, inspector, new GeneratedFileCache(playbackCacheDir, GeneratedFileCache.Kind.PLAYBACK));
    }
    PlaybackFileResolver(Path playbackCacheDir, AudioFileInspector inspector, GeneratedFileCache diskCache) {
        this.playbackCacheDir = playbackCacheDir;
        this.inspector = inspector;
        this.diskCache = diskCache;
    }

    public Resolution resolve(Path source) {
        if (source == null
                || inspector.detect(source) != AudioFormat.MP3
                || !inspector.hasExtension(source, "m4a", "aac")) {
            return new Resolution(source, false);
        }

        GeneratedFileCache.Lease lease = null;
        Path temporary = null;
        try {
            String signature = source.toAbsolutePath().normalize()
                    + ":" + Files.size(source)
                    + ":" + Files.getLastModifiedTime(source).toMillis();
            Path target = playbackCacheDir.resolve(Hashing.sha1(signature) + ".mp3");
            lease = diskCache.acquire(target);
            if (!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.size(target) != Files.size(source)) {
                Files.createDirectories(playbackCacheDir);
                temporary = Files.createTempFile(playbackCacheDir, ".za-playback-", ".part");
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Playback preparation cancelled");
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return new Resolution(target, true, lease);
        } catch (IOException ignored) {
            if (lease != null) lease.close();
            return new Resolution(source, false);
        } catch (RuntimeException failure) {
            if (lease != null) lease.close();
            throw failure;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    public record Resolution(Path path, boolean correctedExtension, GeneratedFileCache.Lease lease) implements AutoCloseable {
        public Resolution(Path path, boolean correctedExtension) { this(path, correctedExtension, null); }
        @Override public void close() { if (lease != null) lease.close(); }
    }
    @Override public void close() { diskCache.close(); }
}
