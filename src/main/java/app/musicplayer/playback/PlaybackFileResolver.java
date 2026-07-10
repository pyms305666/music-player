package app.musicplayer.playback;

import app.musicplayer.util.Hashing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 处理下载文件扩展名与真实编码不一致的情况。
 * JavaFX 会参考扩展名选择解码器，因此把实际为 MP3 的 m4a/aac 复制为缓存 mp3 后再播放。
 */
public final class PlaybackFileResolver {
    private final Path playbackCacheDir;
    private final AudioFileInspector inspector;

    public PlaybackFileResolver(Path playbackCacheDir, AudioFileInspector inspector) {
        this.playbackCacheDir = playbackCacheDir;
        this.inspector = inspector;
    }

    public Resolution resolve(Path source) {
        if (source == null
                || inspector.detect(source) != AudioFormat.MP3
                || !inspector.hasExtension(source, "m4a", "aac")) {
            return new Resolution(source, false);
        }

        try {
            String signature = source.toAbsolutePath().normalize()
                    + ":" + Files.size(source)
                    + ":" + Files.getLastModifiedTime(source).toMillis();
            Path target = playbackCacheDir.resolve(Hashing.sha1(signature) + ".mp3");
            if (!Files.isRegularFile(target) || Files.size(target) != Files.size(source)) {
                Files.createDirectories(playbackCacheDir);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Resolution(target, true);
        } catch (IOException ignored) {
            return new Resolution(source, false);
        }
    }

    public record Resolution(Path path, boolean correctedExtension) {
    }
}
