package app.musicplayer.playlist;

import app.musicplayer.online.PlaylistImportService;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 共享歌单工作区。组织持久化、文件状态刷新、候选匹配与下载任务，并通过后台工作队列协调操作。 Serial background edits; all file checks and
 * database work are outside the UI thread.
 */
public final class PlaylistWorkspace implements AutoCloseable {
    public final PlaylistStore store;
    public final PlaylistDownloads downloads;
    private final PlaylistImportService importer = new PlaylistImportService();
    private final ExecutorService io =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "playlist-library"));

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public PlaylistWorkspace(
            PlaylistStore store, Path scratch, PlaylistDownloads.Publisher publisher, Executor ui) {
        this.store = store;
        downloads = new PlaylistDownloads(store, scratch, publisher, ui);
    }

    /** 提交歌单工作区后台操作，将结果交给指定的界面执行器。 */
    public <T> CompletableFuture<T> work(Supplier<T> operation) {
        return CompletableFuture.supplyAsync(operation, io);
    }

    /** 读取或解析当前工作所需的数据。 */
    public CompletableFuture<NamedPlaylist> read(String link) {
        return work(() -> importer.load(link));
    }

    /** 读取已保存的歌单列表。 */
    public CompletableFuture<List<NamedPlaylist>> list() {
        return work(store::loadPlaylists);
    }

    /** 检查歌单条目的位置可读性并刷新本地或缺失状态。 */
    public CompletableFuture<Void> refreshFiles(PlaylistFiles files) {
        return work(
                () -> {
                    Map<String, Boolean> readable = new HashMap<>();
                    for (var p : store.loadPlaylists())
                        if (!downloads.active(p.id()))
                            for (var e : p.entries()) {
                                if (e.state() == NamedPlaylist.State.READY
                                        && !readable.computeIfAbsent(e.location(), files::readable))
                                    store.updatePlaylistEntry(
                                            p.id(),
                                            e.pendingCopy(NamedPlaylist.State.MISSING)
                                                    .status(
                                                            NamedPlaylist.State.MISSING,
                                                            "本地文件已失效"));
                                else if (e.state() == NamedPlaylist.State.WAITING
                                        || e.state() == NamedPlaylist.State.DOWNLOADING)
                                    store.updatePlaylistEntry(
                                            p.id(),
                                            e.status(
                                                    NamedPlaylist.State.CANCELLED, "上次任务中断，可继续下载"));
                            }
                    return null;
                });
    }

    /** 保存当前数据或歌单状态，随后通知界面结果。 */
    public CompletableFuture<NamedPlaylist> save(NamedPlaylist incoming, String existingId) {
        return work(
                () -> {
                    if (existingId != null && downloads.active(existingId))
                        throw new IllegalStateException("请先取消该歌单的下载任务再更新");
                    synchronized (store) {
                        NamedPlaylist playlist = incoming;
                        if (existingId != null) {
                            var existing = store.findPlaylist(existingId).orElseThrow();
                            Map<String, ArrayDeque<NamedPlaylist.Entry>> old = new HashMap<>();
                            for (var e : existing.entries())
                                old.computeIfAbsent(e.track().identity(), k -> new ArrayDeque<>())
                                        .add(e);
                            List<NamedPlaylist.Entry> merged = new ArrayList<>();
                            for (var e : incoming.entries()) {
                                var candidates = old.get(e.track().identity());
                                var prior = candidates == null ? null : candidates.poll();
                                if (prior != null)
                                    e =
                                            new NamedPlaylist.Entry(
                                                    prior.id(),
                                                    e.track(),
                                                    e.durationMillis(),
                                                    e.location(),
                                                    e.state(),
                                                    e.message(),
                                                    e.downloadTrack(),
                                                    e.userSelectedVersion());
                                merged.add(e);
                            }
                            playlist =
                                    incoming.configured(
                                                    existingId,
                                                    incoming.name(),
                                                    incoming.directory(),
                                                    incoming.recursive())
                                            .withEntries(merged);
                        }
                        store.savePlaylist(playlist);
                        return playlist;
                    }
                });
    }

    /** 查找歌单或为歌单歌曲产生可选择的下载候选。 */
    public CompletableFuture<List<app.musicplayer.model.OnlineTrackInfo>> candidates(
            NamedPlaylist.Entry entry) {
        return work(() -> downloads.candidates(entry));
    }

    /** 修改已保存歌单并保留其身份和顺序。 */
    public CompletableFuture<Void> edit(
            String id, java.util.function.UnaryOperator<NamedPlaylist> change) {
        return work(
                () -> {
                    if (downloads.active(id)) throw new IllegalStateException("请先取消该歌单的下载任务再编辑");
                    synchronized (store) {
                        store.savePlaylist(change.apply(store.findPlaylist(id).orElseThrow()));
                    }
                    return null;
                });
    }

    /** 删除当前选中对象，并处理对应持久化和界面状态。 */
    public CompletableFuture<Void> delete(String id) {
        return work(
                () -> {
                    if (downloads.active(id)) throw new IllegalStateException("请先取消该歌单的下载任务再删除");
                    store.deletePlaylist(id);
                    return null;
                });
    }

    /** 停止接收新工作，等待或取消本组件持有的任务并释放资源。 */
    public CompletableFuture<Void> shutdown() {
        importer.close();
        io.shutdown();
        return downloads
                .shutdown()
                .thenRunAsync(
                        () -> {
                            boolean interrupted = false;
                            while (!io.isTerminated())
                                try {
                                    io.awaitTermination(5, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    interrupted = true;
                                }
                            if (interrupted) Thread.currentThread().interrupt();
                        });
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        shutdown();
    }
}
