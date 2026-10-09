package app.musicplayer.playlist;

import java.util.List;
import java.util.Optional;

/** Implementations persist a header and ordered item rows in one transaction. */
public interface PlaylistStore {
    List<NamedPlaylist> loadPlaylists();
    void savePlaylist(NamedPlaylist playlist);
    void updatePlaylistEntry(String playlistId, NamedPlaylist.Entry entry);
    void deletePlaylist(String playlistId);
    default Optional<NamedPlaylist> findPlaylist(String id) {
        return loadPlaylists().stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
