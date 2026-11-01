package app.musicplayer.online;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 下载文件的格式识别与原子发布。根据文件头确定扩展名，预留不冲突的最终文件，避免覆盖已有歌曲。 Keeps incomplete downloads out of the library and
 * reserves names across workers.
 */
public final class DownloadFiles {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private DownloadFiles() {}

    /** 将准备好的数据发布到目标位置，并把结果交给后续处理。 */
    public static synchronized Path publish(
            Path temporary, Path directory, String name, String extension) throws IOException {
        Path target = directory.resolve(name + extension);
        int suffix = 2;
        while (Files.exists(target))
            target = directory.resolve(name + " (" + suffix++ + ")" + extension);
        // CREATE_NEW reserves the name for concurrent downloaders and other processes.
        while (true) {
            try {
                Files.createFile(target);
                break;
            } catch (java.nio.file.FileAlreadyExistsException collision) {
                target = directory.resolve(name + " (" + suffix++ + ")" + extension);
            }
        }
        try {
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException error) {
            Files.deleteIfExists(target);
            throw error;
        }
    }

    /** 检查实际音频文件头，修正下载地址推测的扩展名。 */
    public static String detectedExtension(Path file, String fallback) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            byte[] h = new byte[16];
            int n = input.read(h);
            if (n >= 4) {
                if (h[0] == 'f' && h[1] == 'L' && h[2] == 'a' && h[3] == 'C') return ".flac";
                if (h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S') return ".ogg";
                if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') return ".mp3";
                if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F') return ".wav";
                if ((h[0] & 255) == 255 && (h[1] & 246) == 240) return ".aac";
                if ((h[0] & 255) == 255 && (h[1] & 224) == 224) return ".mp3";
                if (n >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p')
                    return ".m4a";
            }
            return fallback;
        }
    }
}
