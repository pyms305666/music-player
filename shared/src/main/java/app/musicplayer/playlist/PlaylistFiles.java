package app.musicplayer.playlist;

import java.util.List;

/** File paths on desktop, MediaStore/document URIs on Android. Called on background workers. */
public interface PlaylistFiles {
    List<PlaylistDuplicates.Local> scan(String destination, boolean recursive);
    boolean readable(String location);
}
