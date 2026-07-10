package app.musicplayer;

import app.musicplayer.artwork.ArtworkService;
import app.musicplayer.data.MusicDatabase;
import app.musicplayer.config.AppPaths;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.lyrics.LyricsService;
import app.musicplayer.model.LyricLine;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.model.PlayMode;
import app.musicplayer.model.Track;
import app.musicplayer.online.OnlineMusicSearchService;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SortDirection;
import app.musicplayer.playlist.TrackLibraryService;
import app.musicplayer.playback.AudioFileInspector;
import app.musicplayer.playback.AudioFormat;
import app.musicplayer.playback.PlaybackFileResolver;
import app.musicplayer.ui.OnlineDrawer;
import app.musicplayer.ui.PlaybackControls;
import app.musicplayer.ui.PlaylistPane;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

public final class MusicPlayerApp extends Application {
    private static final int[] LYRIC_RETRY_DELAYS_SECONDS = {10, 20, 30, 60};
    private static final AppPaths APP_PATHS = AppPaths.resolve(MusicPlayerApp.class);
    private static final Path LYRICS_CACHE_DIR = APP_PATHS.lyricsCacheDir();
    private static final Path ARTWORK_CACHE_DIR = APP_PATHS.artworkCacheDir();
    private static final Path PLAYBACK_CACHE_DIR = APP_PATHS.playbackCacheDir();
    private static final Path DOWNLOAD_DIR = APP_PATHS.dataDir();
    private static final Path DATABASE_PATH = APP_PATHS.databasePath();
    private static final double DEFAULT_LYRICS_FONT_SIZE = 17.0;
    private static final double MIN_LYRICS_FONT_SIZE = 13.0;
    private static final double MAX_LYRICS_FONT_SIZE = 30.0;

    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    private final FilteredList<Track> filteredTracks = new FilteredList<>(tracks, track -> true);
    private final ObservableList<String> lyricRows = FXCollections.observableArrayList();
    private final ObservableList<OnlineTrackInfo> onlineResults = FXCollections.observableArrayList();
    private final Random random = new Random();
    private final TrackLibraryService trackLibrary = new TrackLibraryService();
    private final AudioFileInspector audioFileInspector = new AudioFileInspector();
    private final PlaybackFileResolver playbackFileResolver =
            new PlaybackFileResolver(PLAYBACK_CACHE_DIR, audioFileInspector);
    private final ArtworkService artworkService = new ArtworkService(ARTWORK_CACHE_DIR);
    private final Preferences preferences = Preferences.userNodeForPackage(MusicPlayerApp.class);

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lyrics-retry"); t.setDaemon(true); return t;
    });

    private MusicDatabase database;
    private LyricsService lyricsService;
    private OnlineMusicSearchService onlineMusicSearchService;
    private PlaylistPane playlistPane;
    private OnlineDrawer onlineDrawer;
    private PlaybackControls playbackControls;
    private ListView<Track> playlistView;
    private ListView<String> lyricsView;
    private ListView<OnlineTrackInfo> onlineResultsView;
    private TextField searchField;
    private TextField onlineSearchField;
    private ComboBox<PlaylistSort> sortTypeBox;
    private ComboBox<SortDirection> sortOrderBox;
    private Label titleLabel;
    private Label artistLabel;
    private Label sourceLabel;
    private Label statusLabel;
    private Label timeLabel;
    private Button playPauseButton;
    private Slider progressSlider;
    private Slider volumeSlider;
    private ComboBox<PlayMode> playModeBox;
    private ProgressIndicator loadingLyrics;
    private ProgressIndicator loadingOnlineSearch;
    private ImageView artworkImageView;
    private Region artworkDimmer;
    private VBox lyricsMetaBox;
    private HBox sourceRow;
    private StackPane lyricsShell;
    private Button lyricsLockButton;
    private Button pureLyricsButton;

    private MediaPlayer mediaPlayer;
    private Track currentTrack;
    private String currentArtworkSource;
    private Lyrics currentLyrics = Lyrics.empty("导入歌曲后开始播放");
    private boolean previewingOnlineResult;
    private long lyricsRequestId;
    private long onlineSearchRequestId;
    private long onlinePreviewRequestId;
    private ScheduledFuture<?> lyricRetryTask;
    private int lyricRetryAttempt;
    private boolean lyricsAutoScrollLocked;
    private boolean pureLyricsMode;
    private double lyricsFontSize = DEFAULT_LYRICS_FONT_SIZE;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        initializeServices();
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(createTopBar(stage));
        root.setCenter(createResizableContent());
        root.setBottom(createControls());

        Scene scene = new Scene(root, 1320, 720);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE && !isTextInputFocused(scene)) { togglePlayPause(); event.consume(); }
        });
        scene.widthProperty().addListener((o, ov, nv) -> applyResponsiveLayout(nv.doubleValue()));

        stage.setTitle("简约音乐播放器");
        stage.setMinWidth(1120);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();
        Platform.runLater(() -> {
            onlineDrawer.restore(scene.getWidth());
            applyResponsiveLayout(scene.getWidth());
        });
        restoreSavedTracks();
    }

    private SplitPane createResizableContent() {
        createPlaylistPane();
        StackPane nowPlaying = createNowPlaying();
        createOnlineDrawer();

        playlistPane.setMinWidth(240);
        playlistPane.setPrefWidth(320);
        nowPlaying.setMinWidth(420);
        onlineDrawer.setMinWidth(58);
        onlineDrawer.setPrefWidth(360);

        SplitPane splitPane = new SplitPane(playlistPane, nowPlaying, onlineDrawer);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getStyleClass().add("main-split-pane");
        splitPane.setDividerPositions(0.24, 0.78);

        SplitPane.setResizableWithParent(playlistPane, true);
        SplitPane.setResizableWithParent(nowPlaying, true);
        SplitPane.setResizableWithParent(onlineDrawer, true);
        onlineDrawer.attach(splitPane);
        return splitPane;
    }

    @Override
    public void stop() {
        cancelLyricRetry();
        retryExecutor.shutdownNow();
        artworkService.close();
        if (lyricsService != null) { lyricsService.close(); }
        if (onlineMusicSearchService != null) { onlineMusicSearchService.close(); }
        if (mediaPlayer != null) { mediaPlayer.dispose(); }
        if (database != null) { database.close(); }
    }

    private void initializeServices() {
        APP_PATHS.initialize();
        try { database = new MusicDatabase(DATABASE_PATH); }
        catch (SQLException e) { throw new IllegalStateException("无法初始化本地数据库", e); }
        lyricsService = new LyricsService(database, LYRICS_CACHE_DIR);
        onlineMusicSearchService = new OnlineMusicSearchService();
    }

    private static boolean isTextInputFocused(Scene scene) { return scene.getFocusOwner() instanceof TextField; }

    private HBox createTopBar(Stage stage) {
        Button importButton = new Button("导入文件夹");
        importButton.getStyleClass().add("primary-button");
        importButton.setOnAction(event -> importFolder(stage));

        Button importFilesButton = new Button("导入音频");
        importFilesButton.setOnAction(event -> importFiles(stage));

        Button removeTrackButton = new Button("移除选中");
        removeTrackButton.setOnAction(event -> removeSelectedTrack());

        playModeBox = new ComboBox<>();
        playModeBox.getItems().setAll(PlayMode.ORDER, PlayMode.SHUFFLE, PlayMode.REPEAT_ONE);
        playModeBox.getSelectionModel().select(PlayMode.ORDER);

        Button reloadLyricsButton = new Button("搜索歌词");
        reloadLyricsButton.setOnAction(event -> { if (currentTrack != null) { loadLyrics(currentTrack, true); } });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLabel = new Label("请选择文件夹或音频文件");
        statusLabel.getStyleClass().addAll("muted-label", "status-chip");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(360);

        HBox topBar = new HBox(12, importButton, importFilesButton, removeTrackButton, playModeBox, reloadLyricsButton, spacer, statusLabel);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(18, 22, 12, 22));
        return topBar;
    }

    private void createPlaylistPane() {
        playlistPane = new PlaylistPane(
                filteredTracks,
                preferences,
                this::applyTrackFilter,
                this::sortTracks,
                this::playTrack);
        searchField = playlistPane.searchField();
        sortTypeBox = playlistPane.sortTypeBox();
        sortOrderBox = playlistPane.sortOrderBox();
        playlistView = playlistPane.playlistView();
    }

    private void createOnlineDrawer() {
        onlineDrawer = new OnlineDrawer(
                onlineResults,
                preferences,
                this::searchOnlineTracks,
                this::previewOnlineTrack,
                this::downloadAndPlayOnlineTrack);
        onlineSearchField = onlineDrawer.searchField();
        onlineResultsView = onlineDrawer.resultsView();
        loadingOnlineSearch = onlineDrawer.loadingIndicator();
    }

    private StackPane createNowPlaying() {
        titleLabel = new Label("未播放歌曲"); titleLabel.getStyleClass().add("track-title"); titleLabel.setWrapText(true);
        artistLabel = new Label("导入文件夹或音频文件后双击歌曲播放"); artistLabel.getStyleClass().add("track-artist"); artistLabel.setWrapText(true);
        sourceLabel = new Label("歌词来源：暂无"); sourceLabel.getStyleClass().add("muted-label");
        loadingLyrics = new ProgressIndicator(); loadingLyrics.setMaxSize(18, 18); loadingLyrics.setVisible(false); loadingLyrics.setManaged(false);

        sourceRow = new HBox(8, sourceLabel, loadingLyrics);
        sourceRow.getStyleClass().add("lyrics-source-row");

        lyricsView = new ListView<>(lyricRows);
        lyricsView.getStyleClass().add("lyrics-view");
        lyricsView.setFocusTraversable(false);
        lyricsView.setMinWidth(0);
        lyricsView.setMinHeight(0);
        lyricsView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setWrapText(true);
                setStyle(String.format(Locale.US, "-fx-font-size: %.1fpx;", lyricsFontSize));
            }
        });

        lyricsShell = new StackPane(lyricsView);
        lyricsShell.getStyleClass().add("lyrics-shell");
        lyricsShell.setMinWidth(0);
        lyricsShell.setMinHeight(0);
        VBox.setVgrow(lyricsShell, Priority.ALWAYS);

        lyricsMetaBox = new VBox(8, titleLabel, artistLabel, sourceRow);
        lyricsMetaBox.getStyleClass().add("lyrics-meta");

        HBox lyricsToolbar = createLyricsToolbar();
        VBox nowPlaying = new VBox(12, lyricsToolbar, lyricsMetaBox, lyricsShell);
        nowPlaying.getStyleClass().add("content-foreground");
        nowPlaying.setPadding(new Insets(24, 22, 16, 22));
        nowPlaying.setMinWidth(0);
        nowPlaying.setMinHeight(0);

        artworkImageView = new ImageView();
        artworkImageView.getStyleClass().add("artwork-background");
        artworkImageView.setPreserveRatio(false);
        artworkImageView.setSmooth(true);
        artworkImageView.setVisible(false);
        artworkDimmer = new Region();
        artworkDimmer.getStyleClass().add("artwork-dimmer");

        StackPane stack = new StackPane(artworkImageView, artworkDimmer, nowPlaying);
        stack.getStyleClass().add("content");
        stack.setMinWidth(0);
        stack.setMinHeight(0);
        artworkImageView.fitWidthProperty().bind(stack.widthProperty());
        artworkImageView.fitHeightProperty().bind(stack.heightProperty());
        showLyrics(Lyrics.empty("导入歌曲后开始播放"));
        applyPureLyricsMode();
        return stack;
    }

    private HBox createLyricsToolbar() {
        lyricsLockButton = createLyricsToolButton("锁定歌词", this::toggleLyricsLock);
        Button zoomInButton = createLyricsToolButton("放大字体", () -> changeLyricsFontSize(1.5));
        Button zoomOutButton = createLyricsToolButton("缩小字体", () -> changeLyricsFontSize(-1.5));
        pureLyricsButton = createLyricsToolButton("纯歌词模式", this::togglePureLyricsMode);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(8, lyricsLockButton, zoomInButton, zoomOutButton, pureLyricsButton, spacer);
        toolbar.getStyleClass().add("lyrics-toolbar");
        updateLyricsToolbarState();
        return toolbar;
    }

    private Button createLyricsToolButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("toolbar-button");
        button.setFocusTraversable(false);
        button.setOnAction(e -> action.run());
        return button;
    }

    private void toggleLyricsLock() {
        lyricsAutoScrollLocked = !lyricsAutoScrollLocked;
        updateLyricsToolbarState();
        if (!lyricsAutoScrollLocked && mediaPlayer != null) {
            updateHighlightedLyric(mediaPlayer.getCurrentTime());
        }
    }

    private void changeLyricsFontSize(double delta) {
        lyricsFontSize = clamp(lyricsFontSize + delta, MIN_LYRICS_FONT_SIZE, MAX_LYRICS_FONT_SIZE);
        if (lyricsView != null) lyricsView.refresh();
    }

    private void togglePureLyricsMode() {
        pureLyricsMode = !pureLyricsMode;
        applyPureLyricsMode();
        updateLyricsToolbarState();
    }

    private void applyPureLyricsMode() {
        if (lyricsMetaBox != null) {
            lyricsMetaBox.setVisible(!pureLyricsMode);
            lyricsMetaBox.setManaged(!pureLyricsMode);
        }
        if (artworkDimmer != null) {
            artworkDimmer.setOpacity(pureLyricsMode ? 0.48 : 1.0);
        }
        if (artworkImageView != null) {
            artworkImageView.setOpacity(pureLyricsMode ? 0.16 : 0.34);
        }
        if (lyricsShell != null) {
            StackPane.setMargin(lyricsShell, pureLyricsMode ? new Insets(2, 0, 0, 0) : Insets.EMPTY);
        }
    }

    private void updateLyricsToolbarState() {
        toggleToolbarButtonState(lyricsLockButton, lyricsAutoScrollLocked);
        toggleToolbarButtonState(pureLyricsButton, pureLyricsMode);
    }

    private static void toggleToolbarButtonState(Button button, boolean active) {
        if (button == null) return;
        if (active) {
            if (!button.getStyleClass().contains("toolbar-button-active")) {
                button.getStyleClass().add("toolbar-button-active");
            }
        } else {
            button.getStyleClass().remove("toolbar-button-active");
        }
    }

    private PlaybackControls createControls() {
        playbackControls = new PlaybackControls(
                tracks,
                this::previousTrack,
                this::togglePlayPause,
                () -> nextTrack(true),
                this::seekToProgress,
                volume -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.setVolume(volume);
                    }
                });
        playPauseButton = playbackControls.playPauseButton();
        progressSlider = playbackControls.progressSlider();
        volumeSlider = playbackControls.volumeSlider();
        timeLabel = playbackControls.timeLabel();
        return playbackControls;
    }

    private void importFolder(Stage stage) {
        DirectoryChooser c = new DirectoryChooser(); c.setTitle("选择歌曲文件夹");
        Path dp = Path.of(System.getProperty("user.home"), "Music");
        if (Files.isDirectory(dp)) c.setInitialDirectory(dp.toFile());
        var dir = c.showDialog(stage); if (dir == null) return;
        try {
            addImportedTracks(trackLibrary.scanFolder(dir.toPath()), "文件夹");
        } catch (IOException e) { statusLabel.setText("导入失败：" + e.getMessage()); }
    }

    private void importFiles(Stage stage) {
        FileChooser c = new FileChooser(); c.setTitle("选择音频文件");
        c.getExtensionFilters().add(new FileChooser.ExtensionFilter("音频文件", "*.mp3", "*.m4a", "*.aac", "*.wav", "*.aif", "*.aiff"));
        List<java.io.File> files = c.showOpenMultipleDialog(stage); if (files == null || files.isEmpty()) return;
        addImportedTracks(trackLibrary.fromFiles(files.stream().map(java.io.File::toPath).toList()), "音频文件");
    }

    private void addImportedTracks(List<Track> imported, String source) {
        if (imported == null || imported.isEmpty()) { statusLabel.setText("没有找到可导入的" + source); return; }
        TrackLibraryService.ImportResult result = trackLibrary.mergeUnique(tracks, imported);
        List<Track> added = result.addedTracks();
        int dup = result.duplicateCount();
        if (added.isEmpty()) { statusLabel.setText("全部 " + imported.size() + " 首都已在歌单中"); return; }
        tracks.addAll(added); database.saveTracks(added);
        sortTracks();
        applyTrackFilter(searchField == null ? "" : searchField.getText());
        playlistView.getSelectionModel().select(added.get(0));
        statusLabel.setText("已新增 " + added.size() + " 首" + (dup > 0 ? "，忽略重复 " + dup + " 首" : ""));
        if (currentTrack == null) playTrack(added.get(0));
    }

    private void restoreSavedTracks() {
        List<Track> saved = database.loadTracks().stream()
                .filter(t -> Files.isRegularFile(t.path()))
                .filter(t -> trackLibrary.isSupportedAudio(t.path()))
                .toList();
        if (!saved.isEmpty()) { tracks.setAll(saved); sortTracks(); applyTrackFilter(""); playlistView.getSelectionModel().select(0); statusLabel.setText("已恢复 " + saved.size() + " 首歌曲"); }
    }

    private void applyTrackFilter(String q) {
        filteredTracks.setPredicate(track -> trackLibrary.matches(track, q));
        if (currentTrack != null && filteredTracks.contains(currentTrack)) playlistView.getSelectionModel().select(currentTrack);
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0);
    }

    private void playTrack(int idx) { if (idx >= 0 && idx < tracks.size()) playTrack(tracks.get(idx)); }

    private void playTrack(Track track) {
        int idx = tracks.indexOf(track); if (idx < 0) return;
        disposePlayer(); previewingOnlineResult = false; currentTrack = track;
        if (filteredTracks.contains(track)) { playlistView.getSelectionModel().select(track); playlistView.scrollTo(track); }
        titleLabel.setText(currentTrack.title()); artistLabel.setText(currentTrack.artist());
        progressSlider.setValue(0); timeLabel.setText("00:00 / 00:00");
        showArtwork(null); showLyrics(Lyrics.empty("正在准备歌词..."));
        PlaybackFileResolver.Resolution playbackResolution = playbackFileResolver.resolve(currentTrack.path());
        Path playbackPath = playbackResolution.path();
        if (playbackResolution.correctedExtension()) {
            statusLabel.setText("检测到下载文件扩展名异常，已按 MP3 兼容播放");
        }
        if (audioFileInspector.detect(playbackPath) == AudioFormat.RAW_AAC) {
            showPlayerError(new IllegalStateException("JavaFX 无法稳定播放原始 AAC 音频"));
            return;
        }

        try {
            Media media = new Media(playbackPath.toUri().toString());
            mediaPlayer = new MediaPlayer(media); mediaPlayer.setVolume(volumeSlider.getValue());
            mediaPlayer.currentTimeProperty().addListener((o, ot, nt) -> updatePlaybackProgress(nt));
            mediaPlayer.setOnReady(() -> { updateMetadata(media); database.saveTrack(currentTrack, javaDuration(mediaPlayer.getTotalDuration())); updateDurationLabel(); loadLyrics(currentTrack, false); mediaPlayer.play(); });
            mediaPlayer.setOnPlaying(() -> playPauseButton.setText("暂停"));
            mediaPlayer.setOnPaused(() -> playPauseButton.setText("播放"));
            mediaPlayer.setOnStopped(() -> playPauseButton.setText("播放"));
            mediaPlayer.setOnEndOfMedia(this::handleEndOfMedia);
            mediaPlayer.setOnError(() -> showPlayerError(mediaPlayer.getError()));
            media.setOnError(() -> showPlayerError(media.getError()));
        } catch (MediaException e) { showPlayerError(e); }
    }

    private void updateMetadata(Media media) {
        String t = valueAsString(media.getMetadata().get("title")), a = valueAsString(media.getMetadata().get("artist"));
        currentTrack.updateMetadata(t, a); titleLabel.setText(currentTrack.title()); artistLabel.setText(currentTrack.artist());
        playlistView.refresh(); database.saveTrack(currentTrack, mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration()));
    }

    private static String valueAsString(Object v) { return v instanceof String s ? s : null; }

    
    // ========== 在线搜索结果：双击爬虫下载到本地再播放 ==========

    private void downloadAndPlayOnlineTrack(OnlineTrackInfo info) {
        if (info == null) return;
        if (!info.canAttemptDownload()) {
            statusLabel.setText("当前结果不可下载：" + info.availabilityText());
            showLyrics(Lyrics.empty("当前在线结果不可下载"));
            return;
        }
        disposePlayer(); cancelLyricRetry();

        statusLabel.setText("正在爬取下载：" + info.title());
        showArtwork(info.artworkUrl());
        showLyrics(Lyrics.empty("正在从网页爬取下载 " + info.title() + "，请稍候..."));
        sourceLabel.setText("歌词来源：下载中...");

        long reqId = ++onlinePreviewRequestId;

        onlineMusicSearchService.downloadAsync(info, DOWNLOAD_DIR).whenComplete((downloadedPath, err) -> Platform.runLater(() -> {
            if (reqId != onlinePreviewRequestId) return;
            if (err != null || downloadedPath == null) {
                showLyrics(Lyrics.empty("爬取下载失败"));
                statusLabel.setText("下载失败：" + (err != null ? err.getMessage() : "未知错误"));
                return;
            }

            Track newTrack = new Track(downloadedPath);
            TrackLibraryService.ImportResult result = trackLibrary.mergeUnique(tracks, List.of(newTrack));
            if (result.addedTracks().isEmpty()) {
                statusLabel.setText("已在歌单中：" + info.title());
                Track et = tracks.stream().filter(t -> t.path().toAbsolutePath().normalize().toString().equals(downloadedPath.toAbsolutePath().normalize().toString())).findFirst().orElse(newTrack);
                playTrack(et); return;
            }

            tracks.add(newTrack);
            database.saveTracks(List.of(newTrack));
            sortTracks();
            if (searchField != null && !searchField.getText().isBlank()) searchField.setText("");
            else applyTrackFilter("");
            statusLabel.setText("爬取下载完成：" + info.title());
            playTrack(newTrack);
        }));
    }
// ========== 共享播放控制 ==========

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            Track sel = playlistView.getSelectionModel().getSelectedItem();
            if (sel != null) playTrack(sel); else if (!tracks.isEmpty()) playTrack(0);
            return;
        }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) mediaPlayer.pause(); else mediaPlayer.play();
    }

    private void previousTrack() {
        if (tracks.isEmpty()) return;
        int cur = currentTrackIndex(); playTrack(cur <= 0 ? tracks.size() - 1 : cur - 1);
    }

    private void nextTrack(boolean manual) {
        if (tracks.isEmpty()) return;
        PlayMode m = playModeBox.getValue();
        if (m == PlayMode.REPEAT_ONE && !manual) { replayCurrent(); return; }
        playTrack(switch (m) {
            case SHUFFLE -> randomIndex();
            case ORDER, REPEAT_ONE -> { int cur = currentTrackIndex(); yield cur + 1 >= tracks.size() ? 0 : cur + 1; }
        });
    }

    private int randomIndex() { if (tracks.size() <= 1) return 0; int c = currentTrackIndex(), n; do { n = random.nextInt(tracks.size()); } while (n == c); return n; }

    private void handleEndOfMedia() {
        if (playModeBox.getValue() == PlayMode.ORDER) {
            int cur = currentTrackIndex(); if (cur >= tracks.size() - 1) { mediaPlayer.stop(); mediaPlayer.seek(Duration.ZERO); progressSlider.setValue(0); updatePlaybackProgress(Duration.ZERO); statusLabel.setText("顺序播放结束"); return; }
        }
        nextTrack(false);
    }

    private int currentTrackIndex() { int i = currentTrack == null ? -1 : tracks.indexOf(currentTrack); if (i >= 0) return i; Track s = playlistView.getSelectionModel().getSelectedItem(); return s == null ? -1 : tracks.indexOf(s); }

    private void replayCurrent() { if (mediaPlayer != null) { mediaPlayer.seek(Duration.ZERO); mediaPlayer.play(); } }

    private void seekToProgress() {
        if (mediaPlayer == null || mediaPlayer.getTotalDuration() == null) return;
        Duration t = mediaPlayer.getTotalDuration(); if (t.greaterThan(Duration.ZERO)) mediaPlayer.seek(t.multiply(progressSlider.getValue()));
    }

    private void updatePlaybackProgress(Duration ct) {
        if (mediaPlayer == null) return;
        Duration t = mediaPlayer.getTotalDuration();
        if (t != null && t.greaterThan(Duration.ZERO) && !playbackControls.isSeeking()) {
            progressSlider.setValue(ct.toMillis() / t.toMillis());
        }
        updateDurationLabel(); updateHighlightedLyric(ct);
    }

    private void updateDurationLabel() {
        if (mediaPlayer == null) { timeLabel.setText("00:00 / 00:00"); return; }
        Duration c = mediaPlayer.getCurrentTime(), t = mediaPlayer.getTotalDuration();
        timeLabel.setText(formatTime(c) + " / " + formatTime(t));
    }

    private static String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.lessThan(Duration.ZERO)) {
            return "00:00";
        }
        long seconds = (long) Math.floor(duration.toSeconds());
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    // ========== 歌词与封面 ==========

    private void loadLyrics(Track track, boolean manual) {
        if (track == null) return;
        previewingOnlineResult = false; cancelLyricRetry();
        long reqId = ++lyricsRequestId;
        loadingLyrics.setVisible(true); loadingLyrics.setManaged(true);
        sourceLabel.setText(manual ? "歌词来源：正在重新搜索..." : "歌词来源：正在搜索...");

        java.time.Duration dur = mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration());
        CompletableFuture<LyricsLookupResult> f = manual ? lyricsService.searchOnlineAsync(track, dur) : lyricsService.findLyrics(track, dur);
        f.whenComplete((r, err) -> Platform.runLater(() -> {
            if (reqId != lyricsRequestId || track != currentTrack) return;
            loadingLyrics.setVisible(false); loadingLyrics.setManaged(false);
            if (err != null) {
                showLyrics(Lyrics.empty("歌词加载失败，稍后继续搜索"));
                scheduleLyricRetry(track, dur, reqId);
                return;
            }
            showLyrics(r.lyrics()); showArtwork(r.artworkUrl());
            if (isMissingLyrics(r.lyrics())) scheduleLyricRetry(track, dur, reqId);
        }));
    }

    private void scheduleLyricRetry(Track track, java.time.Duration dur, long reqId) {
        if (track != currentTrack || reqId != lyricsRequestId) return;
        int delay = LYRIC_RETRY_DELAYS_SECONDS[Math.min(lyricRetryAttempt, LYRIC_RETRY_DELAYS_SECONDS.length - 1)];
        lyricRetryAttempt++; sourceLabel.setText("歌词来源：未找到，" + delay + " 秒后继续搜索");
        lyricRetryTask = retryExecutor.schedule(() -> retryLyrics(track, dur, reqId), delay, TimeUnit.SECONDS);
    }

    private void retryLyrics(Track track, java.time.Duration dur, long reqId) {
        if (reqId != lyricsRequestId || track != currentTrack) return;
        lyricsService.searchOnlineAsync(track, dur).whenComplete((result, error) -> Platform.runLater(() -> {
            if (reqId != lyricsRequestId || track != currentTrack) return;
            if (error != null || result == null || isMissingLyrics(result.lyrics())) {
                scheduleLyricRetry(track, dur, reqId);
                return;
            }
            loadingLyrics.setVisible(false);
            loadingLyrics.setManaged(false);
            showLyrics(result.lyrics());
            showArtwork(result.artworkUrl());
        }));
    }

    private void cancelLyricRetry() { lyricRetryAttempt = 0; if (lyricRetryTask != null) { lyricRetryTask.cancel(true); lyricRetryTask = null; } }

    private static boolean isMissingLyrics(Lyrics l) { return l == null || l.source().startsWith("没有找到") || l.source().startsWith("暂无") || l.source().contains("继续搜索"); }

    private static java.time.Duration javaDuration(Duration d) { if (d == null || d.isUnknown() || d.lessThanOrEqualTo(Duration.ZERO)) return null; return java.time.Duration.ofMillis(Math.max(0, Math.round(d.toMillis()))); }

    private void showLyrics(Lyrics lyrics) {
        currentLyrics = lyrics;
        lyricRows.setAll(lyrics.lines().stream().map(LyricLine::text).toList());
        sourceLabel.setText("歌词来源：" + lyrics.source());
        if (lyricsView != null && !lyricRows.isEmpty()) {
            lyricsView.getSelectionModel().select(0);
            lyricsView.scrollTo(0);
        }
        if (lyricsView != null) {
            lyricsView.refresh();
        }
    }

    private void sortTracks() {
        if (tracks.isEmpty()) return;
        PlaylistSort sort = sortTypeBox == null ? PlaylistSort.TITLE : sortTypeBox.getValue();
        SortDirection direction = sortOrderBox == null ? SortDirection.ASCENDING : sortOrderBox.getValue();
        Track selected = playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        tracks.sort(trackLibrary.comparator(sort, direction));
        if (selected != null && filteredTracks.contains(selected)) {
            playlistView.getSelectionModel().select(selected);
            playlistView.scrollTo(selected);
        }
    }

    private void applyResponsiveLayout(double windowWidth) {
        if (statusLabel != null) {
            statusLabel.setMaxWidth(clamp(windowWidth * 0.24, 220, 420));
        }
        if (onlineDrawer != null) {
            onlineDrawer.applyResponsiveLayout(windowWidth);
        }
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private void showArtwork(String url) {
        currentArtworkSource = url;
        if (artworkImageView == null) return;
        if (url == null || url.isBlank()) {
            artworkImageView.setImage(null);
            artworkImageView.setVisible(false);
            return;
        }
        if (!artworkService.isRemoteUrl(url)) {
            artworkImageView.setImage(new Image(url, true));
            artworkImageView.setVisible(true);
            return;
        }
        Path cached = artworkService.cachedPath(url);
        if (Files.isRegularFile(cached)) {
            artworkImageView.setImage(new Image(cached.toUri().toString(), true));
            artworkImageView.setVisible(true);
            return;
        }
        artworkImageView.setImage(new Image(url, true));
        artworkImageView.setVisible(true);
        artworkService.cache(url).thenAccept(target -> {
            if (target == null || !Objects.equals(currentArtworkSource, url)) {
                return;
            }
            Platform.runLater(() -> {
                if (Objects.equals(currentArtworkSource, url) && artworkImageView != null) {
                    artworkImageView.setImage(new Image(target.toUri().toString(), true));
                    artworkImageView.setVisible(true);
                }
            });
        });
    }

    private void updateHighlightedLyric(Duration ct) {
        if (lyricsAutoScrollLocked || previewingOnlineResult || currentLyrics == null || !currentLyrics.timed() || currentLyrics.lines().isEmpty()) return;
        List<LyricLine> lines = currentLyrics.lines();
        int sel = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).time().toMillis() <= Math.round(ct.toMillis())) sel = i;
            else break;
        }
        if (lyricsView.getSelectionModel().getSelectedIndex() != sel) {
            lyricsView.getSelectionModel().select(sel);
            lyricsView.scrollTo(Math.max(0, sel - 4));
        }
    }

    private void showPlayerError(Throwable err) {
        String message = err == null ? "未知错误" : err.getMessage();
        if (audioFileInspector.isUnsupportedMediaError(err)) {
            message = "当前文件编码不受支持，建议换成 mp3 / m4a";
        }
        statusLabel.setText("播放失败：" + message);
        playPauseButton.setText("播放");
        showLyrics(Lyrics.empty("当前文件无法播放或编码不受支持"));
    }

    // ========== 在线搜索与预览 ==========

    private void searchOnlineTracks() {
        String q = onlineSearchField == null ? "" : onlineSearchField.getText();
        if (q == null || q.isBlank()) { onlineResults.clear(); statusLabel.setText("请输入在线搜索关键词"); return; }
        long reqId = ++onlineSearchRequestId; loadingOnlineSearch.setVisible(true); loadingOnlineSearch.setManaged(true);
        statusLabel.setText("正在在线搜索：" + q.trim());
        onlineMusicSearchService.searchAsync(q).whenComplete((results, err) -> Platform.runLater(() -> {
            if (reqId != onlineSearchRequestId) return;
            loadingOnlineSearch.setVisible(false); loadingOnlineSearch.setManaged(false);
            if (err != null) { onlineResults.clear(); statusLabel.setText("在线搜索失败"); return; }
            onlineResults.setAll(results);
            long downloadableCount = results.stream().filter(OnlineTrackInfo::canAttemptDownload).count();
            statusLabel.setText(results.isEmpty()
                    ? "没有找到在线结果"
                    : "在线搜索完成，共 " + results.size() + " 条结果，可下载 " + downloadableCount + " 条");
            if (!results.isEmpty()) onlineResultsView.getSelectionModel().select(0);
        }));
    }

    private void previewOnlineTrack(OnlineTrackInfo info) {
        if (info == null) return; long reqId = ++onlinePreviewRequestId; previewingOnlineResult = true;
        titleLabel.setText(info.title()); artistLabel.setText(info.subtitle());
        showArtwork(info.artworkUrl()); showLyrics(Lyrics.empty("正在加载在线预览歌词..."));
        statusLabel.setText("预览：" + info.title() + "（双击下载到本地播放）");
        onlineMusicSearchService.loadPreviewAsync(info).whenComplete((r, err) -> Platform.runLater(() -> {
            if (reqId != onlinePreviewRequestId || onlineResultsView.getSelectionModel().getSelectedItem() != info) return;
            previewingOnlineResult = true;
            if (err != null) { showLyrics(Lyrics.empty("在线预览加载失败")); return; }
            showArtwork(r.artworkUrl() == null || r.artworkUrl().isBlank() ? info.artworkUrl() : r.artworkUrl());
            showLyrics(r.lyrics()); statusLabel.setText("预览：" + info.title());
        }));
    }

    // ========== 歌单管理 ==========

    private void removeSelectedTrack() {
        Track sel = playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("请先选择要移除的歌曲"); return; }
        int ri = tracks.indexOf(sel); boolean removingCurrent = sel == currentTrack;
        tracks.remove(sel); database.removeTrack(sel);
        applyTrackFilter(searchField == null ? "" : searchField.getText());
        if (removingCurrent) { cancelLyricRetry(); disposePlayer(); currentTrack = null; previewingOnlineResult = false; titleLabel.setText("未播放歌曲"); artistLabel.setText("当前歌曲已从歌单和缓存移除"); timeLabel.setText("00:00 / 00:00"); showArtwork(null); showLyrics(Lyrics.empty("当前歌曲已移除")); if (!tracks.isEmpty()) { int ni = Math.min(ri, tracks.size() - 1); Track nt = tracks.get(ni); if (filteredTracks.contains(nt)) playlistView.getSelectionModel().select(nt); else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0); } }
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(Math.min(ri, filteredTracks.size() - 1));
        statusLabel.setText("已移除：" + sel);
    }

    private void disposePlayer() { lyricsRequestId++; if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; } }
}
