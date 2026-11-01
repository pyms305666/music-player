package app.musicplayer.playlist;

import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.Track;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 桌面曲库的串行 I/O 队列：扫描、读取与保存都离开 JavaFX 界面线程。 自有线程不是守护线程，退出时完成已排队的写入，再由最后一个任务关闭数据库。
 * 借用数据库和曲库规则服务，不自行创建第二份缓存或数据库连接。
 */
public final class DesktopLibraryController implements AutoCloseable {
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "library-io"));

    /** 供歌单登记、音频检查等同样需要串行数据库操作的调用者借用。 */
    public ExecutorService executor() {
        return worker;
    }

    /** 扫描文件夹；扫描异常通过 Future 交给界面显示。 */
    public CompletableFuture<List<Track>> scanFolder(TrackLibraryService library, Path directory) {
        return submit(() -> library.scanFolder(directory));
    }

    /** 从文件选择结果创建歌曲，并预先缓存创建时间。 */
    public CompletableFuture<List<Track>> fromFiles(TrackLibraryService library, List<Path> paths) {
        return submit(() -> library.fromFiles(paths));
    }

    /** 恢复仍存在且格式受支持的歌曲；界面负责与正在导入的歌曲合并。 */
    public CompletableFuture<List<Track>> restore(
            MusicDatabase database, TrackLibraryService library) {
        return submit(
                () -> {
                    List<Track> saved =
                            database.loadTracks().stream()
                                    .filter(track -> Files.isRegularFile(track.path()))
                                    .filter(track -> library.isSupportedAudio(track.path()))
                                    .toList();
                    library.primeCreationTimes(saved);
                    return saved;
                });
    }

    /** 在同一队列保存导入结果，完成后界面才把它加入可播放列表。 */
    public CompletableFuture<Void> save(MusicDatabase database, List<Track> tracks) {
        return CompletableFuture.runAsync(() -> database.saveTracks(tracks), worker);
    }

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    private <T> CompletableFuture<T> submit(Callable<T> action) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return action.call();
                    } catch (Exception error) {
                        throw new CompletionException(error);
                    }
                },
                worker);
    }

    /** 将关闭数据库的操作排在已提交的写入之后，再停止接收新任务。 */
    public void finish(Runnable cleanup) {
        worker.execute(cleanup);
        close();
    }

    /** 有序停止自有线程，保留已经提交的任务。 */
    @Override
    public void close() {
        worker.shutdown();
    }
}
