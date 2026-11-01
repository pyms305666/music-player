package app.musicplayer.playlist;

import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.Track;
import app.musicplayer.online.DownloadFiles;

import java.nio.file.*;
import java.util.*;

/** 桌面歌单的文件适配器。检查本地路径、扫描重复歌曲、保存下载结果并登记歌曲，实现共享歌单所需的平台接口。 */
public final class DesktopPlaylistFiles implements PlaylistFiles, PlaylistDownloads.Publisher {
    private final MusicDatabase database;
    private final TrackLibraryService library = new TrackLibraryService();

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public DesktopPlaylistFiles(MusicDatabase database) {
        this.database = database;
    }

    /** 检查位置是否仍可读取，兼容本平台的路径或文档 URI。 */
    @Override
    public boolean readable(String location) {
        try {
            return !location.isBlank()
                    && !app.musicplayer.config.DownloadDirectorySettings.internal(Path.of(location))
                    && Files.isRegularFile(Path.of(location))
                    && Files.isReadable(Path.of(location));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 扫描目标目录及已有记录，生成供重复检查使用的本地歌曲信息。 */
    @Override
    public List<PlaylistDuplicates.Local> scan(String directory, boolean recursive) {
        Map<String, Track> files = new LinkedHashMap<>();
        for (var track : database.loadTracks())
            if (readable(track.path().toString()))
                files.put(track.path().toAbsolutePath().normalize().toString(), track);
        if (!directory.isBlank() && Files.exists(Path.of(directory)))
            try (var paths = Files.walk(Path.of(directory), recursive ? Integer.MAX_VALUE : 1)) {
                paths.filter(Files::isRegularFile)
                        .filter(
                                path ->
                                        !app.musicplayer.config.DownloadDirectorySettings.internal(
                                                path))
                        .filter(library::isSupportedAudio)
                        .forEach(
                                path -> {
                                    if (Thread.currentThread().isInterrupted())
                                        throw new java.util.concurrent.CancellationException();
                                    files.putIfAbsent(
                                            path.toAbsolutePath().normalize().toString(),
                                            new Track(path));
                                });
            } catch (java.io.IOException e) {
                throw new IllegalStateException("无法扫描下载目录：" + e.getMessage(), e);
            }
        List<PlaylistDuplicates.Local> result = new ArrayList<>();
        files.forEach(
                (location, track) ->
                        result.add(
                                new PlaylistDuplicates.Local(
                                        location, track.title(), track.artist(), "")));
        for (var playlist : database.loadPlaylists())
            for (var e : playlist.entries())
                if (readable(e.location())) result.addAll(PlaylistDuplicates.associations(e));
        return result;
    }

    /** 为当前任务选择实际目标盘的独立暂存路径，不使用应用数据盘中转。 */
    @Override
    public Path stagingDirectory(String destination, Path fallback) {
        return Path.of(destination)
                .resolve(app.musicplayer.config.DownloadDirectorySettings.STAGING_DIRECTORY)
                .resolve(java.util.UUID.randomUUID().toString());
    }

    /** 将校验通过的音频发布并登记曲库。 */
    @Override
    public String publish(Path downloaded, NamedPlaylist.Entry entry, String destination)
            throws Exception {
        Path folder = Path.of(destination);
        Files.createDirectories(folder);
        String name = downloaded.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path target =
                DownloadFiles.publish(
                        downloaded,
                        folder,
                        dot > 0 ? name.substring(0, dot) : name,
                        dot > 0 ? name.substring(dot) : ".mp3");
        var metadata = entry.downloadTrack() == null ? entry.track() : entry.downloadTrack();
        Track track = new Track(target);
        track.updateMetadata(metadata.title(), metadata.artist());
        database.saveTracks(List.of(track));
        return target.toAbsolutePath().normalize().toString();
    }

    /** 把可读的歌曲位置登记到曲库，返回对应的歌曲记录。 */
    public List<Track> register(List<String> locations) {
        Map<String, Track> saved = new HashMap<>();
        for (var t : database.loadTracks())
            saved.put(t.path().toAbsolutePath().normalize().toString(), t);
        List<Track> result = new ArrayList<>();
        for (String location : locations)
            if (readable(location))
                result.add(saved.getOrDefault(location, new Track(Path.of(location))));
        database.saveTracks(result);
        return List.copyOf(result);
    }
}
