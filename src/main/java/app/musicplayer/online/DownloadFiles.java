package app.musicplayer.online;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Keeps incomplete downloads out of the library and reserves names across workers. */
public final class DownloadFiles {
    private DownloadFiles() { }

    public static synchronized Path publish(Path temporary, Path directory, String name, String extension)
            throws IOException {
        Path target = directory.resolve(name + extension);
        int suffix = 2;
        while (Files.exists(target)) target = directory.resolve(name + " (" + suffix++ + ")" + extension);
        // CREATE_NEW reserves the name for concurrent downloaders and other processes.
        while (true) {
            try { Files.createFile(target); break; }
            catch (java.nio.file.FileAlreadyExistsException collision) {
                target = directory.resolve(name + " (" + suffix++ + ")" + extension);
            }
        }
        try {
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException error) {
            Files.deleteIfExists(target);
            throw error;
        }
    }

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
                if (n >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') return ".m4a";
            }
            return fallback;
        }
    }
}
