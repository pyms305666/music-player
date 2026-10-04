package app.musicplayer.android.ui;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import app.musicplayer.android.R;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.online.CancellableTask;
import app.musicplayer.online.DownloadController;
import app.musicplayer.online.DownloadEvent;
import app.musicplayer.online.OnlineMusicSearchService;
import app.musicplayer.online.OnlineSearchSnapshot;
import app.musicplayer.online.OnlineTaskMessages;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** Connects existing Android controls to task states, without library or playback ownership. */
public final class AndroidOnlineTasks implements AutoCloseable {
    public record Controls(EditText query, ImageButton search, Button download, OnlineTrackAdapter results) { }
    private final OnlineMusicSearchService service;
    private final Executor ui;
    private final Controls controls;
    private final Consumer<String> status;
    private final Runnable beforeSearch;
    private final DownloadController<TrackEntry> downloads;
    private CancellableTask<OnlineSearchSnapshot> search;
    private boolean searching;
    private boolean closed;
    private long generation;

    public AndroidOnlineTasks(OnlineMusicSearchService service, Executor ui, Controls controls,
                              Consumer<String> status, Runnable beforeSearch) {
        this.service = service; this.ui = ui; this.controls = controls; this.status = status; this.beforeSearch = beforeSearch;
        downloads = new DownloadController<>(service, ui, notice -> {
            status.accept(OnlineTaskMessages.download(notice)); updateDownloadButton();
        }, path -> {
            try { java.nio.file.Files.deleteIfExists(path); }
            catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        });
        updateDownloadButton();
    }
    public void toggleSearch() { if (searching && search != null) search.cancel(); else search(); }
    public void search() {
        if (closed) return;
        long request = ++generation;
        beforeSearch.run(); service.cancelPreview();
        String query = controls.query().getText().toString();
        controls.results().clearSelection(); controls.results().submit(List.of(), this::updateDownloadButton);
        searching = !query.isBlank(); updateSearchButton(false);
        search = service.search(query, ui, snapshot -> {
            if (closed || request != generation) return;
            controls.results().submit(snapshot.tracks(), this::updateDownloadButton);
            searching = !snapshot.finished();
            updateSearchButton(snapshot.failedSources() > 0);
            status.accept(OnlineTaskMessages.search(snapshot));
        });
    }
    public void selectionChanged() { updateDownloadButton(); }
    private void updateSearchButton(boolean retry) {
        String action = searching ? "取消搜索" : retry ? "重试搜索" : "搜索";
        controls.search().setContentDescription(action);
        controls.search().setTooltipText(action);
        controls.search().setImageResource(searching ? R.drawable.ic_close
                : retry ? R.drawable.ic_refresh : R.drawable.ic_search);
    }
    public boolean cancelSelectedDownload() {
        var result = downloads.cancel(controls.results().selected());
        if (result == DownloadController.CancelResult.PUBLISHING) status.accept("正在加入曲库，请稍候");
        return result != DownloadController.CancelResult.NONE;
    }
    public void download(OnlineTrackInfo track, Path directory,
            Function<Path, CompletableFuture<TrackEntry>> publish, Consumer<TrackEntry> completed) {
        downloads.start(track, directory, publish, completed); updateDownloadButton();
    }
    private void updateDownloadButton() {
        if (closed) return;
        OnlineTrackInfo track = controls.results().selected();
        var event = downloads.event(track);
        boolean saving = event.isPresent() && event.get().stage() == DownloadEvent.Stage.PUBLISHING;
        controls.download().setEnabled(track != null && !saving);
        controls.download().setText(saving ? "正在加入曲库" : event.isPresent() ? "取消下载"
                : downloads.retry(track) ? "重试下载" : "下载并播放");
    }
    @Override public void close() {
        closed = true; generation++;
        if (search != null) search.cancel();
        downloads.close();
    }
}
