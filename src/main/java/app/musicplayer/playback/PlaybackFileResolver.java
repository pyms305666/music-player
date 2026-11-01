package app.musicplayer.playback;

import app.musicplayer.cache.GeneratedFileCache;
import app.musicplayer.util.Hashing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 处理下载文件扩展名与真实编码不一致的情况。 JavaFX 会参考扩展名选择解码器，因此把实际为 MP3 的 m4a/aac 复制为缓存 mp3 后再播放。 */
public final class PlaybackFileResolver implements AutoCloseable {
    private final Path playbackCacheDir;
    private final AudioFileInspector inspector;
    private final GeneratedFileCache diskCache;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public PlaybackFileResolver(Path playbackCacheDir, AudioFileInspector inspector) {
        this(
                playbackCacheDir,
                inspector,
                new GeneratedFileCache(playbackCacheDir, GeneratedFileCache.Kind.PLAYBACK));
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    PlaybackFileResolver(
            Path playbackCacheDir, AudioFileInspector inspector, GeneratedFileCache diskCache) {
        this.playbackCacheDir = playbackCacheDir;
        this.inspector = inspector;
        this.diskCache = diskCache;
    }

    /** 准备或解析可实际使用的地址、路径或运行配置。 */
    public Resolution resolve(Path source) {
        if (source == null
                || inspector.detect(source) != AudioFormat.MP3
                || !inspector.hasExtension(source, "m4a", "aac")) {
            return new Resolution(source, false);
        }

        GeneratedFileCache.Lease lease = null;
        Path temporary = null;
        try {
            String signature =
                    source.toAbsolutePath().normalize()
                            + ":"
                            + Files.size(source)
                            + ":"
                            + Files.getLastModifiedTime(source).toMillis();
            Path target = playbackCacheDir.resolve(Hashing.sha1(signature) + ".mp3");
            lease = diskCache.acquire(target);
            if (!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.size(target) != Files.size(source)) {
                Files.createDirectories(playbackCacheDir);
                temporary = Files.createTempFile(playbackCacheDir, ".za-playback-", ".part");
                Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("Playback preparation cancelled");
                try {
                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
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
            if (temporary != null)
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
        }
    }

    /** 组合适配后的播放文件与释放句柄，播放结束时归还缓存使用权。 */
    public record Resolution(Path path, boolean correctedExtension, GeneratedFileCache.Lease lease)
            implements AutoCloseable {
        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        public Resolution(Path path, boolean correctedExtension) {
            this(path, correctedExtension, null);
        }

        /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
        @Override
        public void close() {
            if (lease != null) lease.close();
        }
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        diskCache.close();
    }
}
