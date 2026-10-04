package app.musicplayer;

import app.musicplayer.artwork.ArtworkService;
import app.musicplayer.artwork.ArtworkPresenter;
import app.musicplayer.config.AppPaths;
import app.musicplayer.config.LayoutMode;
import app.musicplayer.data.MusicDatabase;
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
import app.musicplayer.playlist.LocalSearch;
import app.musicplayer.playlist.SearchSnapshot;
import app.musicplayer.playback.AudioFileInspector;
import app.musicplayer.playback.AudioFormat;
import app.musicplayer.playback.PlaybackFileResolver;
import app.musicplayer.ui.OnlineDrawer;
import app.musicplayer.ui.DesktopOnlineTasks;
import app.musicplayer.online.DownloadEvent;
import app.musicplayer.ui.MobileViewSwitcher;
import app.musicplayer.ui.MobileWindowSizer;
import app.musicplayer.ui.PlaybackControls;
import app.musicplayer.ui.PlaylistPane;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
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
import javafx.stage.Screen;
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
    private final ObservableList<Track> filteredTracks = FXCollections.observableArrayList();
    private final LocalSearch<Track> localSearch = new LocalSearch<>(Platform::runLater, this::displayFilteredTracks);
    private final ObservableList<String> lyricRows = FXCollections.observableArrayList();
    private final ObservableList<OnlineTrackInfo> onlineResults = FXCollections.observableArrayList();
    private final Random random = new Random();
    private final TrackLibraryService trackLibrary = new TrackLibraryService();
    private final AudioFileInspector audioFileInspector = new AudioFileInspector();
    private final PlaybackFileResolver playbackFileResolver =
            new PlaybackFileResolver(PLAYBACK_CACHE_DIR, audioFileInspector);
    private final ArtworkService artworkService = new ArtworkService(ARTWORK_CACHE_DIR);
    private final Preferences preferences = Preferences.userRoot().node(
            System.getProperty("musicplayer.preferences-node", "/app/musicplayer"));

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lyrics-retry"); t.setDaemon(true); return t;
    });

    private MusicDatabase database;
    private LyricsService lyricsService;
    private OnlineMusicSearchService onlineMusicSearchService;
    private PlaylistPane playlistPane;
    private OnlineDrawer onlineDrawer;
    private DesktopOnlineTasks onlineTasks;
    private MobileViewSwitcher mobileViews;
    private PlaybackControls playbackControls;
    private ListView<Track> playlistView;
    private ListView<String> lyricsView;
    private ListView<OnlineTrackInfo> onlineResultsView;
    private TextField searchField;
    private ComboBox<PlaylistSort> sortTypeBox;
    private ComboBox<SortDirection> sortOrderBox;
    private Label titleLabel;
    private Label artistLabel;
    private Label sourceLabel;
    private Label playbackContextLabel;
    private Label statusLabel;
    private Label timeLabel;
    private Slider progressSlider;
    private Slider volumeSlider;
    private ComboBox<PlayMode> playModeBox;
    private ProgressIndicator loadingLyrics;
    private ImageView artworkImageView;
    private ArtworkPresenter artworkPresenter;
    private Region artworkDimmer;
    private VBox lyricsMetaBox;
    private HBox sourceRow;
    private StackPane lyricsShell;
    private Button lyricsLockButton;
    private Button pureLyricsButton;
    private Button onlineToggleButton;
    private HBox desktopHero;

    private MediaPlayer mediaPlayer;
    private volatile boolean closing;
    private final java.util.concurrent.ExecutorService libraryExecutor = Executors.newSingleThreadExecutor(r -> {
        // Drain queued database writes before the JVM exits after closing the window.
        return new Thread(r, "library-io");
    });
    private final java.util.List<Track> pendingImports = new java.util.ArrayList<>();
    private Track currentTrack;
    private Lyrics currentLyrics = Lyrics.empty("导入歌曲后开始播放");
    private boolean previewingOnlineResult;
    private long lyricsRequestId;
    private long playbackRequestId;
    private long onlinePreviewRequestId;
    private ScheduledFuture<?> lyricRetryTask;
    private int lyricRetryAttempt;
    private boolean lyricsAutoScrollLocked;
    private boolean pureLyricsMode;
    private double lyricsFontSize = DEFAULT_LYRICS_FONT_SIZE;
    private LayoutMode layoutMode = LayoutMode.DESKTOP;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        layoutMode = LayoutMode.resolve(getParameters().getRaw(), Boolean.getBoolean("musicplayer.mobile"));
        initializeServices();
        stage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/app-icon.png"), "Missing application icon")));
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        if (layoutMode.isMobile()) {
            root.getStyleClass().add("mobile-root");
            root.setTop(createMobileTopBar(stage));
            root.setCenter(createMobileContent());
            VBox mobileBottom = new VBox(createControls(), mobileViews.navigationBar());
            mobileBottom.getStyleClass().add("mobile-bottom");
            root.setBottom(mobileBottom);
        } else {
            root.getStyleClass().add("desktop-root");
            root.setTop(createTopBar(stage));
            root.setCenter(createResizableContent());
            root.setBottom(createControls());
        }

        double initialWidth = layoutMode.isMobile() ? 405
                : Math.min(1320, Screen.getPrimary().getVisualBounds().getWidth() - 32);
        double initialHeight = layoutMode.isMobile() ? 720
                : Math.min(720, Screen.getPrimary().getVisualBounds().getHeight() - 32);
        Scene scene = new Scene(root, initialWidth, initialHeight);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        if (layoutMode.isMobile()) {
            scene.getStylesheets().add(getClass().getResource("/styles-mobile.css").toExternalForm());
        } else {
            scene.getStylesheets().add(getClass().getResource("/styles-desktop.css").toExternalForm());
        }
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE && !isTextInputFocused(scene)) { togglePlayPause(); event.consume(); }
        });
        scene.widthProperty().addListener((o, ov, nv) -> applyResponsiveLayout(nv.doubleValue()));

        stage.setTitle(layoutMode.isMobile() ? "ZA音乐 - 移动预览" : "ZA音乐");
        stage.setMinWidth(layoutMode.isMobile() ? 360 : Math.min(1120, initialWidth));
        stage.setMinHeight(layoutMode.isMobile() ? 640 : Math.min(560, initialHeight));
        stage.setScene(scene);
        if (!layoutMode.isMobile()) stage.centerOnScreen();
        stage.show();
        if (layoutMode.isMobile()) {
            MobileWindowSizer.bind(stage, scene);
        }
        Platform.runLater(() -> {
            if (!layoutMode.isMobile()) {
                onlineDrawer.restore(scene.getWidth());
                updateOnlineToggleButton();
            }
            applyResponsiveLayout(scene.getWidth());
        });
        restoreSavedTracks();
    }

    private SplitPane createResizableContent() {
        createPlaylistPane();
        StackPane nowPlaying = createNowPlaying();
        createOnlineDrawer();

        playlistPane.setMinWidth(256);
        playlistPane.setPrefWidth(292);
        nowPlaying.setMinWidth(480);
        onlineDrawer.setMinWidth(0);
        onlineDrawer.setPrefWidth(0);

        SplitPane splitPane = new SplitPane(playlistPane, nowPlaying, onlineDrawer);
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getStyleClass().add("main-split-pane");
        splitPane.setDividerPositions(0.23, 1.0);

        SplitPane.setResizableWithParent(playlistPane, true);
        SplitPane.setResizableWithParent(nowPlaying, true);
        SplitPane.setResizableWithParent(onlineDrawer, true);
        onlineDrawer.attach(splitPane);
        return splitPane;
    }

    private MobileViewSwitcher createMobileContent() {
        createPlaylistPane();
        StackPane nowPlaying = createNowPlaying();
        createOnlineDrawer();

        playlistPane.setMinWidth(0);
        playlistPane.setPrefWidth(405);
        playlistPane.setMaxWidth(Double.MAX_VALUE);
        onlineDrawer.enableMobileMode();
        mobileViews = new MobileViewSwitcher(playlistPane, nowPlaying, onlineDrawer);
        return mobileViews;
    }

    @Override
    public void stop() {
        closing = true;
        localSearch.close();
        cancelLyricRetry();
        retryExecutor.shutdownNow();
        if (artworkPresenter != null) artworkPresenter.close();
        artworkService.close();
        if (lyricsService != null) { lyricsService.close(); }
        if (onlineTasks != null) onlineTasks.close();
        if (onlineMusicSearchService != null) { onlineMusicSearchService.close(); }
        if (mediaPlayer != null) { mediaPlayer.dispose(); }
        if (database != null) libraryExecutor.execute(database::close);
        libraryExecutor.shutdown();
    }

    private void initializeServices() {
        System.setProperty("musicplayer.desktop", "true");
        while (true) {
            try { APP_PATHS.initialize(); break; }
            catch (RuntimeException failure) {
                var retry = new javafx.scene.control.ButtonType("重试");
                var choose = new javafx.scene.control.ButtonType("选择旧版数据目录");
                var exit = new javafx.scene.control.ButtonType("退出");
                var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
                        "曲库初始化或迁移失败，原数据未删除。请关闭旧版后重试。\n" + failure.getMessage(), retry, choose, exit);
                alert.setHeaderText("无法打开曲库");
                var selected = alert.showAndWait().orElse(exit);
                if (selected == exit) throw failure;
                if (selected == choose) {
                    DirectoryChooser chooser = new DirectoryChooser();
                    chooser.setTitle("选择含 music-player.db 的旧版 downloads 文件夹");
                    var directory = chooser.showDialog(null);
                    if (directory != null) System.setProperty("musicplayer.migrate-from", directory.getAbsolutePath());
                }
            }
        }
        try { database = new MusicDatabase(DATABASE_PATH); }
        catch (SQLException e) { throw new IllegalStateException("无法初始化本地数据库", e); }
        lyricsService = new LyricsService(database, LYRICS_CACHE_DIR);
        onlineMusicSearchService = new OnlineMusicSearchService();
    }

    private static boolean isTextInputFocused(Scene scene) { return scene.getFocusOwner() instanceof TextField; }

    private HBox createTopBar(Stage stage) {
        Label appTitle = new Label("ZA音乐");
        appTitle.getStyleClass().add("desktop-app-title");
        Label sectionLabel = new Label("本地曲库");
        sectionLabel.getStyleClass().add("desktop-section-label");

        MenuItem importFilesItem = new MenuItem("导入音频文件");
        importFilesItem.setOnAction(event -> importFiles(stage));
        MenuItem importFolderItem = new MenuItem("导入文件夹");
        importFolderItem.setOnAction(event -> importFolder(stage));
        MenuButton importButton = new MenuButton("导入音乐", null, importFilesItem, importFolderItem);
        importButton.getStyleClass().add("primary-button");

        playModeBox = createPlayModeBox();
        onlineToggleButton = new Button("在线搜索");
        onlineToggleButton.getStyleClass().add("online-toggle-button");
        onlineToggleButton.setOnAction(event -> {
            if (onlineDrawer != null) {
                onlineDrawer.toggleFromHeader();
                updateOnlineToggleButton();
            }
        });

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLabel = new Label("请选择文件夹或音频文件");
        statusLabel.getStyleClass().addAll("muted-label", "desktop-status");
        statusLabel.setMaxWidth(340);
        Tooltip statusTooltip = new Tooltip();
        statusTooltip.textProperty().bind(statusLabel.textProperty());
        statusLabel.setTooltip(statusTooltip);
        statusLabel.textProperty().addListener((observable, oldValue, value) -> {
            statusLabel.getStyleClass().remove("status-error");
            if (value != null && (value.contains("失败") || value.contains("无法"))) {
                statusLabel.getStyleClass().add("status-error");
            }
        });

        HBox topBar = new HBox(12, appTitle, sectionLabel, spacer, statusLabel, importButton, onlineToggleButton);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 24, 0, 24));
        topBar.setMinHeight(56);
        topBar.setPrefHeight(56);
        topBar.setMaxHeight(56);
        return topBar;
    }

    private void updateOnlineToggleButton() {
        if (onlineToggleButton == null || onlineDrawer == null) return;
        onlineToggleButton.setText(onlineDrawer.isExpanded() ? "收起搜索" : "在线搜索");
    }

    private VBox createMobileTopBar(Stage stage) {
        Label appTitle = new Label("ZA音乐");
        appTitle.getStyleClass().add("mobile-app-title");

        Button importFilesButton = new Button("导入音频");
        importFilesButton.getStyleClass().add("primary-button");
        importFilesButton.setOnAction(event -> importFiles(stage));

        MenuItem importFolderItem = new MenuItem("导入文件夹");
        importFolderItem.setOnAction(event -> importFolder(stage));
        MenuItem removeTrackItem = new MenuItem("移除选中歌曲");
        removeTrackItem.setOnAction(event -> removeSelectedTrack());
        MenuItem reloadLyricsItem = new MenuItem("重新搜索歌词");
        reloadLyricsItem.setOnAction(event -> {
            if (currentTrack != null) {
                loadLyrics(currentTrack, true);
            }
        });
        MenuButton manageButton = new MenuButton(
                "管理",
                null,
                importFolderItem,
                removeTrackItem,
                reloadLyricsItem);

        playModeBox = createPlayModeBox();
        playModeBox.getStyleClass().add("mobile-play-mode");
        playModeBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PlayMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : switch (item) {
                    case ORDER -> "顺序";
                    case SHUFFLE -> "随机";
                    case REPEAT_ONE -> "单曲";
                });
            }
        });

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox brandRow = new HBox(appTitle, titleSpacer);
        brandRow.getStyleClass().add("mobile-brand-row");
        brandRow.setAlignment(Pos.CENTER_LEFT);

        importFilesButton.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(importFilesButton, Priority.ALWAYS);
        HBox actions = new HBox(8, playModeBox, importFilesButton, manageButton);
        actions.getStyleClass().add("mobile-action-row");
        actions.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label("请选择音频文件");
        statusLabel.getStyleClass().addAll("muted-label", "mobile-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(Double.MAX_VALUE);

        VBox topBar = new VBox(6, brandRow, actions, statusLabel);
        topBar.getStyleClass().addAll("top-bar", "mobile-top-bar");
        return topBar;
    }

    private ComboBox<PlayMode> createPlayModeBox() {
        ComboBox<PlayMode> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(PlayMode.ORDER, PlayMode.SHUFFLE, PlayMode.REPEAT_ONE);
        comboBox.getSelectionModel().select(PlayMode.ORDER);
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PlayMode mode, boolean empty) {
                super.updateItem(mode, empty);
                setText(empty || mode == null ? null : switch (mode) {
                    case ORDER -> "顺序";
                    case SHUFFLE -> "随机";
                    case REPEAT_ONE -> "单曲";
                });
            }
        });
        return comboBox;
    }

    private void createPlaylistPane() {
        playlistPane = new PlaylistPane(
                filteredTracks,
                preferences,
                this::applyTrackFilter,
                this::sortTracks,
                this::playTrack,
                this::removeSelectedTrack,
                !layoutMode.isMobile());
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
        onlineDrawer.setOnExpandedChanged(expanded -> updateOnlineToggleButton());
        onlineResultsView = onlineDrawer.resultsView();
        onlineTasks = new DesktopOnlineTasks(onlineMusicSearchService, onlineDrawer, onlineResults,
                new DesktopOnlineTasks.Callbacks(text -> statusLabel.setText(text), () -> onlinePreviewRequestId++, notice -> {
                    OnlineTrackInfo selected = onlineResultsView.getSelectionModel().getSelectedItem();
                    if (!previewingOnlineResult || selected == null || !selected.identity().equals(notice.track().identity())) return;
                    if (notice.event().stage() == DownloadEvent.Stage.FAILED) showLyrics(Lyrics.empty("下载失败，可点击重试"));
                    else if (notice.event().stage() == DownloadEvent.Stage.CANCELLED) showLyrics(Lyrics.empty("下载已取消"));
                }));
    }

    private StackPane createNowPlaying() {
        titleLabel = new Label("未播放歌曲"); titleLabel.getStyleClass().add("track-title"); titleLabel.setWrapText(true);
        artistLabel = new Label("导入文件夹或音频文件后双击歌曲播放"); artistLabel.getStyleClass().add("track-artist"); artistLabel.setWrapText(true);
        sourceLabel = new Label("歌词来源：暂无"); sourceLabel.getStyleClass().add("muted-label");
        if (!layoutMode.isMobile()) sourceLabel.setWrapText(true);
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
        if (!layoutMode.isMobile()) {
            playbackContextLabel = new Label("未播放");
            playbackContextLabel.getStyleClass().add("playback-context-label");
            lyricsMetaBox.getChildren().addFirst(playbackContextLabel);
        }

        HBox lyricsToolbar = createLyricsToolbar();
        artworkImageView = new ImageView();
        artworkImageView.getStyleClass().add("artwork-background");
        artworkImageView.setSmooth(true);
        artworkImageView.setVisible(false);
        artworkDimmer = new Region();
        artworkDimmer.getStyleClass().add("artwork-dimmer");

        VBox nowPlaying;
        if (layoutMode.isMobile()) {
            nowPlaying = new VBox(12, lyricsToolbar, lyricsMetaBox, lyricsShell);
        } else {
            Label artworkPlaceholder = new Label("♫");
            artworkPlaceholder.getStyleClass().add("artwork-placeholder");
            StackPane artworkTile = new StackPane(artworkPlaceholder, artworkImageView);
            artworkTile.getStyleClass().add("artwork-tile");
            artworkTile.setMinSize(144, 144);
            artworkTile.setPrefSize(144, 144);
            artworkTile.setMaxSize(144, 144);
            artworkImageView.setFitWidth(144);
            artworkImageView.setFitHeight(144);
            artworkImageView.setPreserveRatio(true);
            artworkImageView.visibleProperty().addListener((observable, oldValue, visible) ->
                    artworkPlaceholder.setVisible(!visible));
            desktopHero = new HBox(20, artworkTile, lyricsMetaBox);
            desktopHero.getStyleClass().add("desktop-hero");
            desktopHero.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(lyricsMetaBox, Priority.ALWAYS);
            nowPlaying = new VBox(16, desktopHero, lyricsShell, lyricsToolbar);
        }
        nowPlaying.getStyleClass().add("content-foreground");
        nowPlaying.setPadding(layoutMode.isMobile()
                ? new Insets(24, 22, 16, 22)
                : new Insets(24));
        nowPlaying.setMinWidth(0);
        nowPlaying.setMinHeight(0);

        StackPane stack = layoutMode.isMobile()
                ? new StackPane(artworkImageView, artworkDimmer, nowPlaying)
                : new StackPane(nowPlaying);
        stack.getStyleClass().add("content");
        stack.setMinWidth(0);
        stack.setMinHeight(0);
        if (layoutMode.isMobile()) {
            artworkImageView.setPreserveRatio(false);
            artworkImageView.fitWidthProperty().bind(stack.widthProperty());
            artworkImageView.fitHeightProperty().bind(stack.heightProperty());
        }
        showLyrics(Lyrics.empty("导入歌曲后开始播放"));
        applyPureLyricsMode();
        return stack;
    }

    private HBox createLyricsToolbar() {
        lyricsLockButton = createLyricsToolButton("锁定歌词", this::toggleLyricsLock);
        Button zoomInButton = createLyricsToolButton("放大字体", () -> changeLyricsFontSize(1.5));
        Button zoomOutButton = createLyricsToolButton("缩小字体", () -> changeLyricsFontSize(-1.5));
        pureLyricsButton = createLyricsToolButton("纯歌词模式", this::togglePureLyricsMode);
        Button reloadLyricsButton = createLyricsToolButton("刷新歌词", () -> {
            if (currentTrack != null) loadLyrics(currentTrack, true);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = layoutMode.isMobile()
                ? new HBox(8, lyricsLockButton, zoomInButton, zoomOutButton, pureLyricsButton, spacer)
                : new HBox(8, spacer, reloadLyricsButton, lyricsLockButton, zoomInButton, zoomOutButton, pureLyricsButton);
        toolbar.getStyleClass().add("lyrics-toolbar");
        updateLyricsToolbarState();
        return toolbar;
    }

    private Button createLyricsToolButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("toolbar-button");
        button.setFocusTraversable(!layoutMode.isMobile());
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
        if (desktopHero != null) {
            desktopHero.setVisible(!pureLyricsMode);
            desktopHero.setManaged(!pureLyricsMode);
        }
        if (lyricsMetaBox != null) {
            lyricsMetaBox.setVisible(!pureLyricsMode);
            lyricsMetaBox.setManaged(!pureLyricsMode);
        }
        if (artworkDimmer != null && layoutMode.isMobile()) {
            artworkDimmer.setOpacity(pureLyricsMode ? 0.48 : 1.0);
        }
        if (artworkImageView != null) {
            artworkImageView.setOpacity(layoutMode.isMobile() ? (pureLyricsMode ? 0.16 : 0.34) : 1.0);
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
                },
                layoutMode.isMobile(),
                playModeBox);
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
        statusLabel.setText("正在扫描文件夹...");
        CompletableFuture.supplyAsync(() -> {
            try { return trackLibrary.scanFolder(dir.toPath()); }
            catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
        }, libraryExecutor).whenComplete((imported, error) -> Platform.runLater(() -> {
            if (closing) return;
            if (error != null) { statusLabel.setText("导入失败：" + error.getMessage()); return; }
            addImportedTracks(imported, "文件夹");
        }));
    }

    private void importFiles(Stage stage) {
        FileChooser c = new FileChooser(); c.setTitle("选择音频文件");
        c.getExtensionFilters().add(new FileChooser.ExtensionFilter("音频文件", "*.mp3", "*.m4a", "*.aac", "*.wav", "*.aif", "*.aiff"));
        List<java.io.File> files = c.showOpenMultipleDialog(stage); if (files == null || files.isEmpty()) return;
        CompletableFuture.supplyAsync(() -> trackLibrary.fromFiles(files.stream().map(java.io.File::toPath).toList()), libraryExecutor)
                .whenComplete((imported, error) -> Platform.runLater(() -> {
                    if (closing) return;
                    if (error != null) { statusLabel.setText("导入失败：" + error.getMessage()); return; }
                    addImportedTracks(imported, "音频文件");
                }));
    }

    private void addImportedTracks(List<Track> imported, String source) {
        if (imported == null || imported.isEmpty()) { statusLabel.setText("没有找到可导入的" + source); return; }
        var existing = new java.util.ArrayList<>(tracks);
        existing.addAll(pendingImports);
        var result = trackLibrary.mergeUnique(existing, imported);
        List<Track> added = result.addedTracks();
        if (added.isEmpty()) { statusLabel.setText("歌曲已经在曲库或导入任务中"); return; }
        pendingImports.addAll(added);
        CompletableFuture.runAsync(() -> database.saveTracks(added), libraryExecutor).whenComplete((ignored, error) -> Platform.runLater(() -> {
            pendingImports.removeAll(added);
            if (closing) return;
            if (error != null) { statusLabel.setText("保存曲库失败：" + error.getMessage()); return; }
            tracks.addAll(added);
            sortTracks();
            applyTrackFilter(searchField == null ? "" : searchField.getText());
            playlistView.getSelectionModel().select(added.get(0));
            statusLabel.setText("已新增 " + added.size() + " 首");
            if (currentTrack == null) playTrack(added.get(0));
        }));
    }

    private void restoreSavedTracks() {
        CompletableFuture.supplyAsync(() -> {
            var saved = database.loadTracks().stream().filter(t -> Files.isRegularFile(t.path()))
                    .filter(t -> trackLibrary.isSupportedAudio(t.path())).toList();
            trackLibrary.primeCreationTimes(saved);
            return saved;
        }, libraryExecutor).whenComplete((saved, error) -> Platform.runLater(() -> {
            if (closing) return;
            if (error != null) { statusLabel.setText("读取曲库失败：" + error.getMessage()); return; }
            if (!saved.isEmpty()) {
                tracks.addAll(trackLibrary.mergeUnique(tracks, saved).addedTracks());
                sortTracks(); applyTrackFilter(""); playlistView.getSelectionModel().select(0);
                statusLabel.setText("已恢复 " + tracks.size() + " 首歌曲");
            }
        }));
    }

    private void applyTrackFilter(String q) {
        localSearch.search(q);
    }

    private void displayFilteredTracks(List<Track> values) {
        if (closing) return;
        Track selected = playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        filteredTracks.setAll(values);
        if (playlistView == null) return;
        if (selected != null && filteredTracks.contains(selected)) playlistView.getSelectionModel().select(selected);
        else if (currentTrack != null && filteredTracks.contains(currentTrack)) playlistView.getSelectionModel().select(currentTrack);
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0);
    }

    private void playTrack(int idx) { if (idx >= 0 && idx < tracks.size()) playTrack(tracks.get(idx)); }

    private void playTrack(Track track) {
        onlinePreviewRequestId++;
        int idx = tracks.indexOf(track); if (idx < 0) return;
        disposePlayer(); previewingOnlineResult = false; currentTrack = track;
        if (playbackContextLabel != null) playbackContextLabel.setText("正在播放");
        playbackControls.setPlaying(false);
        if (mobileViews != null) {
            mobileViews.select(MobileViewSwitcher.Section.NOW_PLAYING);
        }
        if (filteredTracks.contains(track)) { playlistView.getSelectionModel().select(track); playlistView.scrollTo(track); }
        playlistPane.setCurrentTrack(track);
        titleLabel.setText(currentTrack.title()); artistLabel.setText(currentTrack.artist());
        playbackControls.setTrackInfo(currentTrack.title(), currentTrack.artist());
        progressSlider.setValue(0); timeLabel.setText("00:00 / 00:00");
        showArtwork(null); showLyrics(Lyrics.empty("正在准备歌词..."));
        long request = playbackRequestId;
        CompletableFuture.supplyAsync(() -> {
                    var resolved = playbackFileResolver.resolve(track.path());
                    if (audioFileInspector.detect(resolved.path()) == AudioFormat.RAW_AAC)
                        throw new IllegalStateException("JavaFX 无法稳定播放原始 AAC 音频");
                    return resolved;
                }, libraryExecutor)
                .whenComplete((resolution, error) -> Platform.runLater(() -> {
                    if (closing || request != playbackRequestId || currentTrack != track) return;
                    if (error != null) { showPlayerError(error); return; }
                    if (resolution.correctedExtension()) statusLabel.setText("已按实际音频格式准备播放");
                    createPlayer(track, resolution.path(), request);
                }));
    }

    private void createPlayer(Track track, Path playbackPath, long request) {
        try {
            Media media = new Media(playbackPath.toUri().toString());
            MediaPlayer created = new MediaPlayer(media);
            mediaPlayer = created;
            created.setVolume(volumeSlider.getValue());
            created.currentTimeProperty().addListener((o, ot, nt) -> { if (mediaPlayer == created) updatePlaybackProgress(nt); });
            created.setOnReady(() -> {
                if (mediaPlayer != created || closing || currentTrack != track) return;
                updateMetadata(media); updateDurationLabel(); loadLyrics(track, false); created.play();
            });
            created.setOnPlaying(() -> { if (mediaPlayer == created) playbackControls.setPlaying(true); });
            created.setOnPaused(() -> { if (mediaPlayer == created) playbackControls.setPlaying(false); });
            created.setOnStopped(() -> { if (mediaPlayer == created) playbackControls.setPlaying(false); });
            created.setOnEndOfMedia(() -> { if (mediaPlayer == created) handleEndOfMedia(); });
            created.setOnError(() -> { if (mediaPlayer == created) showPlayerError(created.getError()); });
            media.setOnError(() -> { if (mediaPlayer == created) showPlayerError(media.getError()); });
        } catch (MediaException error) { showPlayerError(error); }
    }

    private void updateMetadata(Media media) {
        String t = valueAsString(media.getMetadata().get("title")), a = valueAsString(media.getMetadata().get("artist"));
        String oldTitle = currentTrack.title(), oldArtist = currentTrack.artist();
        currentTrack.updateMetadata(t, a); titleLabel.setText(currentTrack.title()); artistLabel.setText(currentTrack.artist());
        playbackControls.setTrackInfo(currentTrack.title(), currentTrack.artist());
        playlistView.refresh();
        if (!Objects.equals(oldTitle, currentTrack.title()) || !Objects.equals(oldArtist, currentTrack.artist())) sortTracks();
        Track snapshot = new Track(currentTrack.path()); snapshot.updateMetadata(currentTrack.title(), currentTrack.artist());
        var duration = mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration());
        CompletableFuture.runAsync(() -> database.saveTrack(snapshot, duration), libraryExecutor)
                .whenComplete((ignored, error) -> { if (error != null && !closing) Platform.runLater(() -> statusLabel.setText("歌曲信息保存失败")); });
    }

    private static String valueAsString(Object v) { return v instanceof String s ? s : null; }

    
    // ========== 在线搜索结果：双击爬虫下载到本地再播放 ==========

    private void downloadAndPlayOnlineTrack(OnlineTrackInfo info) {
        if (info == null) return;
        if (onlineTasks.cancelDownload(info)) return;
        disposePlayer(); cancelLyricRetry();
        playbackControls.setPlaying(false);

        if (info.canAttemptDownload()) {
            statusLabel.setText("正在爬取下载：" + info.title());
        } else {
            // 受限歌曲不再直接拒绝，交给 MusicCrawler 自动换源到其他可用来源。
            statusLabel.setText("正在换源下载：" + info.title() + "（" + info.availabilityText() + "）");
        }
        showArtwork(info.artworkUrl());
        showLyrics(Lyrics.empty("正在从网页爬取下载 " + info.title() + "，请稍候..."));
        sourceLabel.setText("歌词来源：下载中...");

        long reqId = ++onlinePreviewRequestId;

        onlineTasks.download(info, DOWNLOAD_DIR, path -> CompletableFuture.supplyAsync(() -> {
            Track downloaded = new Track(path); database.saveTracks(List.of(downloaded));
            trackLibrary.primeCreationTimes(List.of(downloaded)); return path;
        }, libraryExecutor), downloadedPath -> {
            if (closing) return;
            boolean autoPlay = reqId == onlinePreviewRequestId;
            Track newTrack = new Track(downloadedPath);
            TrackLibraryService.ImportResult result = trackLibrary.mergeUnique(tracks, List.of(newTrack));
            if (result.addedTracks().isEmpty()) {
                statusLabel.setText("已在歌单中：" + info.title());
                Track et = tracks.stream().filter(t -> t.path().toAbsolutePath().normalize().toString().equals(downloadedPath.toAbsolutePath().normalize().toString())).findFirst().orElse(newTrack);
                if (autoPlay) playTrack(et); return;
            }

            tracks.add(newTrack);

            sortTracks();
            if (searchField != null && !searchField.getText().isBlank()) searchField.setText("");
            else applyTrackFilter("");
            statusLabel.setText("爬取下载完成：" + info.title());
            if (autoPlay) playTrack(newTrack);
        });
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
            case ORDER, REPEAT_ONE -> nextOrderedIndex(currentTrackIndex(), tracks.size());
        });
    }

    static int nextOrderedIndex(int currentIndex, int trackCount) {
        if (trackCount <= 0) throw new IllegalArgumentException("trackCount must be positive");
        return app.musicplayer.playlist.QueueOrder.relative(currentIndex, 1, trackCount);
    }

    private int randomIndex() { if (tracks.size() <= 1) return 0; int c = currentTrackIndex(), n; do { n = random.nextInt(tracks.size()); } while (n == c); return n; }

    private void handleEndOfMedia() {
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
        if (lyricsView != null) {
            if (!lyricRows.isEmpty() && (layoutMode.isMobile() || lyrics.timed())) {
                lyricsView.getSelectionModel().select(0);
                lyricsView.scrollTo(0);
            } else {
                lyricsView.getSelectionModel().clearSelection();
            }
        }
        if (lyricsView != null) {
            lyricsView.refresh();
        }
    }

    private void sortTracks() {
        PlaylistSort sort = sortTypeBox == null ? PlaylistSort.TITLE : sortTypeBox.getValue();
        SortDirection direction = sortOrderBox == null ? SortDirection.ASCENDING : sortOrderBox.getValue();
        Track selected = playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        tracks.sort(trackLibrary.comparator(sort, direction));
        localSearch.replace(SearchSnapshot.ofTracks(tracks), searchField == null ? "" : searchField.getText());
        if (selected != null && filteredTracks.contains(selected)) {
            playlistView.getSelectionModel().select(selected);
            playlistView.scrollTo(selected);
        }
    }

    private void applyResponsiveLayout(double windowWidth) {
        if (statusLabel != null) {
            statusLabel.setMaxWidth(layoutMode.isMobile()
                    ? Math.max(200, windowWidth - 28)
                    : clamp(windowWidth * 0.24, 220, 420));
        }
        if (onlineDrawer != null && !layoutMode.isMobile()) {
            onlineDrawer.applyResponsiveLayout(windowWidth);
        }
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private void showArtwork(String url) {
        if (artworkImageView == null) return;
        if (artworkPresenter == null) artworkPresenter = new ArtworkPresenter(artworkService, artworkImageView);
        artworkPresenter.show(url);
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
        playbackControls.setPlaying(false);
        showLyrics(Lyrics.empty("当前文件无法播放或编码不受支持"));
    }

    // ========== 在线搜索与预览 ==========

    private void searchOnlineTracks() {
        onlineTasks.search();
    }

    private void previewOnlineTrack(OnlineTrackInfo info) {
        if (info == null) return; long reqId = ++onlinePreviewRequestId; previewingOnlineResult = true;
        lyricsRequestId++; cancelLyricRetry();
        if (playbackContextLabel != null) playbackContextLabel.setText("在线预览");
        titleLabel.setText(info.title()); artistLabel.setText(info.subtitle());
        showArtwork(info.artworkUrl()); showLyrics(Lyrics.empty("正在加载在线预览歌词..."));
        statusLabel.setText("预览：" + info.title() + "（双击下载到本地播放）");
        onlineMusicSearchService.preview(info).result().whenComplete((r, err) -> Platform.runLater(() -> {
            if (closing || reqId != onlinePreviewRequestId || onlineResultsView.getSelectionModel().getSelectedItem() != info) return;
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
        CompletableFuture.runAsync(() -> database.removeTrack(sel), libraryExecutor).whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (closing) return;
            if (error != null) { statusLabel.setText("移除失败：" + error.getMessage()); return; }
        int ri = tracks.indexOf(sel); boolean removingCurrent = sel == currentTrack;
        tracks.remove(sel);
        sortTracks();
        if (removingCurrent) { cancelLyricRetry(); disposePlayer(); currentTrack = null; previewingOnlineResult = false; if (playbackContextLabel != null) playbackContextLabel.setText("未播放"); playlistPane.setCurrentTrack(null); playbackControls.setTrackInfo(null, null); playbackControls.setPlaying(false); titleLabel.setText("未播放歌曲"); artistLabel.setText("当前歌曲已从歌单和缓存移除"); timeLabel.setText("00:00 / 00:00"); showArtwork(null); showLyrics(Lyrics.empty("当前歌曲已移除")); if (!tracks.isEmpty()) { int ni = Math.min(ri, tracks.size() - 1); Track nt = tracks.get(ni); if (filteredTracks.contains(nt)) playlistView.getSelectionModel().select(nt); else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0); } }
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(Math.min(ri, filteredTracks.size() - 1));
        statusLabel.setText("已移除：" + sel);
        }));
    }

    private void disposePlayer() { lyricsRequestId++; playbackRequestId++; if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; } }
}
