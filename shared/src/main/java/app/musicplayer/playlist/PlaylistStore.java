package app.musicplayer.playlist;

import java.util.List;
import java.util.Optional;

/**
 * 歌单持久化接口。由桌面和 Android 数据库分别实现，向共享工作区提供读取、保存、更新条目和删除操作。 Implementations persist a header and
 * ordered item rows in one transaction.
 */
public interface PlaylistStore {
    /** 从本平台数据库读取对应歌单、歌曲或歌词缓存。 */
    List<NamedPlaylist> loadPlaylists();

    /** 把对应歌单、歌曲或歌词数据写入数据库，遵守事务及线程约定。 */
    void savePlaylist(NamedPlaylist playlist);

    /** 保存歌单中指定条目的最新状态与配置。 */
    void updatePlaylistEntry(String playlistId, NamedPlaylist.Entry entry);

    /** 删除歌单及其条目记录，保留关联音频和歌词。 */
    void deletePlaylist(String playlistId);

    /** 按稳定歌单 ID 查找已保存的歌单。 */
    default Optional<NamedPlaylist> findPlaylist(String id) {
        return loadPlaylists().stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
