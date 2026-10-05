package app.musicplayer.android.data;

import android.media.MediaMetadataRetriever;
import app.musicplayer.model.Track;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;

/** Metadata and filename rules shared by imports and download publication. */
public final class AndroidTrackFiles {
    private AndroidTrackFiles() { }
    public static TrackEntry entry(File file, long createdAt) {
        Track track = new Track(file.toPath());
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            track.updateMetadata(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
        } catch (RuntimeException ignored) { /* Preserve filename metadata for unsupported tags. */ }
        finally { try { retriever.release(); } catch (IOException ignored) { } }
        return new TrackEntry(track, createdAt);
    }
    public static String safeName(String value) {
        String name = value == null || value.isBlank() ? "audio.mp3" : value;
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return name.equals(".") || name.equals("..") ? "audio.mp3" : name;
    }
    /** Atomically reserve a new file: an import never overwrites an existing song. */
    public static File reserve(File directory, String name) throws IOException {
        Files.createDirectories(directory.toPath());
        for (int suffix = 1; ; suffix++) {
            File file = candidate(directory, safeName(name), suffix);
            try { Files.createFile(file.toPath()); return file; }
            catch (FileAlreadyExistsException occupied) { /* Try the next filename. */ }
        }
    }
    private static File candidate(File directory, String name, int suffix) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        return new File(directory, suffix == 1 ? name : base + " (" + suffix + ")" + extension);
    }
}
