package app.musicplayer.ui;

import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.online.CancellableTask;
import app.musicplayer.online.DownloadController;
import app.musicplayer.online.DownloadEvent;
import app.musicplayer.online.OnlineMusicSearchService;
import app.musicplayer.online.OnlineSearchSnapshot;
import app.musicplayer.online.OnlineTaskMessages;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 桌面在线操作的界面协调器。接收增量搜索、保留结果选择、更新下载按钮及任务提示，借用共享搜索服务。 Owns online-control feedback and preserves
 * selection when source batches append.
 */
public final class DesktopOnlineTasks implements AutoCloseable {
    /** 保存界面通知回调。 */
    public record Callbacks(
            Consumer<String> status,
            Runnable beforeSearch,
            Consumer<DownloadController.Notice> downloadChanged) {}

    private final OnlineMusicSearchService service;
    private final OnlineDrawer drawer;
    private final ObservableList<OnlineTrackInfo> results;
    private final Callbacks callbacks;
    private final DownloadController<Path> downloads;
    private final ChangeListener<OnlineTrackInfo> selection =
            (ignored, old, next) -> updateDownloadButton();
    private CancellableTask<OnlineSearchSnapshot> search;
    private boolean searching;
    private boolean closed;
    private long generation;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public DesktopOnlineTasks(
            OnlineMusicSearchService service,
            OnlineDrawer drawer,
            ObservableList<OnlineTrackInfo> results,
            Callbacks callbacks) {
        this.service = service;
        this.drawer = drawer;
        this.results = results;
        this.callbacks = callbacks;
        downloads =
                new DownloadController<>(
                        service,
                        Platform::runLater,
                        notice -> {
                            String message = OnlineTaskMessages.download(notice);
                            drawer.setTaskHint(message);
                            callbacks.status().accept(message);
                            updateDownloadButton();
                            callbacks.downloadChanged().accept(notice);
                        });
        drawer.setSearchActions(
                this::search,
                () -> {
                    if (searching && search != null) search.cancel();
                    else search();
                });
        drawer.resultsView().getSelectionModel().selectedItemProperty().addListener(selection);
        updateDownloadButton();
    }

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    public void search() {
        if (closed) return;
        long request = ++generation;
        callbacks.beforeSearch().run();
        service.cancelPreview();
        String query = drawer.searchField().getText();
        results.clear();
        searching = query != null && !query.isBlank();
        drawer.setSearchState(searching, false);
        search =
                service.search(
                        query,
                        Platform::runLater,
                        snapshot -> {
                            if (closed || request != generation) return;
                            boolean firstBatch = results.isEmpty();
                            if (snapshot.tracks().size() > results.size())
                                results.addAll(
                                        snapshot.tracks()
                                                .subList(results.size(), snapshot.tracks().size()));
                            searching = !snapshot.finished();
                            drawer.setSearchState(searching, snapshot.failedSources() > 0);
                            String message = OnlineTaskMessages.search(snapshot);
                            drawer.setTaskHint(message);
                            callbacks.status().accept(message);
                            if (firstBatch && !results.isEmpty())
                                drawer.resultsView().getSelectionModel().select(0);
                        });
    }

    /** 取消选中歌曲的下载任务并刷新控件状态。 */
    public boolean cancelDownload(OnlineTrackInfo track) {
        var result = downloads.cancel(track);
        if (result == DownloadController.CancelResult.PUBLISHING)
            callbacks.status().accept("正在加入曲库，请稍候");
        return result != DownloadController.CancelResult.NONE;
    }

    /** 启动或执行下载，接通传输进度、文件发布与完成处理。 */
    public void download(
            OnlineTrackInfo track,
            Path directory,
            Function<Path, CompletableFuture<Path>> publish,
            Consumer<Path> completed) {
        downloads.start(track, directory, publish, completed);
        updateDownloadButton();
    }

    /** 同步下载、按钮，供本文件的主流程使用。 */
    private void updateDownloadButton() {
        if (closed) return;
        OnlineTrackInfo track = drawer.resultsView().getSelectionModel().getSelectedItem();
        var event = downloads.event(track);
        boolean saving = event.isPresent() && event.get().stage() == DownloadEvent.Stage.PUBLISHING;
        drawer.setDownloadState(
                saving
                        ? "正在加入曲库"
                        : event.isPresent() ? "取消下载" : downloads.retry(track) ? "重试下载" : "下载并播放",
                track == null || saving);
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        closed = true;
        generation++;
        drawer.resultsView().getSelectionModel().selectedItemProperty().removeListener(selection);
        if (search != null) search.cancel();
        downloads.close();
    }
}
