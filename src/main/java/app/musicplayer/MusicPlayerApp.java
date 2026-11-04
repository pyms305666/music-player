package app.musicplayer;

import app.musicplayer.artwork.ArtworkPresenter;
import app.musicplayer.artwork.ArtworkService;
import app.musicplayer.artwork.DominantColorExtractor;
import app.musicplayer.config.AppPaths;
import app.musicplayer.config.LayoutMode;
import app.musicplayer.data.MusicDatabase;
import app.musicplayer.lyrics.LyricsService;
import app.musicplayer.model.LyricLine;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.model.PlayMode;
import app.musicplayer.model.Track;
import app.musicplayer.online.DownloadEvent;
import app.musicplayer.online.OnlineMusicSearchService;
import app.musicplayer.playback.AudioFileInspector;
import app.musicplayer.playback.AudioFormat;
import app.musicplayer.playback.PlaybackFileResolver;
import app.musicplayer.playlist.LocalSearch;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SearchSnapshot;
import app.musicplayer.playlist.SortDirection;
import app.musicplayer.playlist.TrackLibraryService;
import app.musicplayer.ui.DesktopOnlineTasks;
import app.musicplayer.ui.MobileViewSwitcher;
import app.musicplayer.ui.MobileWindowSizer;
import app.musicplayer.ui.OnlineDrawer;
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
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
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
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/** 桌面主界面与应用生命周期。组装曲库、歌词、封面、在线搜索和播放控件，将用户操作交给专门的服务与控制器处理。 */
public final class MusicPlayerApp extends Application {
    private static final AppPaths APP_PATHS = AppPaths.resolve(MusicPlayerApp.class);
    private static final Path LYRICS_CACHE_DIR = APP_PATHS.lyricsCacheDir();
    private static final Path ARTWORK_CACHE_DIR = APP_PATHS.artworkCacheDir();
    private static final Path PLAYBACK_CACHE_DIR = APP_PATHS.playbackCacheDir();
    private static final double DEFAULT_LYRICS_FONT_SIZE = 17.0;
    private static final double MIN_LYRICS_FONT_SIZE = 13.0;
    private static final double MAX_LYRICS_FONT_SIZE = 30.0;

    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    private final ObservableList<Track> filteredTracks = FXCollections.observableArrayList();
    private final LocalSearch<Track> localSearch =
            new LocalSearch<>(Platform::runLater, this::displayFilteredTracks);
    private final ObservableList<String> lyricRows = FXCollections.observableArrayList();
    private final ObservableList<OnlineTrackInfo> onlineResults =
            FXCollections.observableArrayList();
    private final Random random = new Random();
    private final TrackLibraryService trackLibrary = new TrackLibraryService();
    private final AudioFileInspector audioFileInspector = new AudioFileInspector();
    private final PlaybackFileResolver playbackFileResolver =
            new PlaybackFileResolver(PLAYBACK_CACHE_DIR, audioFileInspector);
    private final ArtworkService artworkService = new ArtworkService(ARTWORK_CACHE_DIR);
    private final Preferences preferences =
            Preferences.userRoot()
                    .node(System.getProperty("musicplayer.preferences-node", "/app/musicplayer"));
    private final app.musicplayer.config.DownloadDirectorySettings downloadSettings =
            new app.musicplayer.config.DownloadDirectorySettings(
                    preferences,
                    APP_PATHS.dataDir(),
                    System.getProperty("jpackage.app-path") == null ? null : APP_PATHS.baseDir());
    private app.musicplayer.ui.DesktopStorageBootstrap.Session storageSession;
    private app.musicplayer.ui.DownloadSettingsDialog downloadSettingsDialog;
    private Stage primaryStage;

    private app.musicplayer.lyrics.DesktopLyricsController lyricsController;

    private app.musicplayer.playlist.PlaylistWorkspace playlistWorkspace;
    private app.musicplayer.playlist.DesktopPlaylistFiles playlistFiles;
    private app.musicplayer.ui.PlaylistWindow savedPlaylistWindow;
    private final java.util.List<Track> playbackOrder = new java.util.ArrayList<>();
    private int savedPlaybackIndex = -1;
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
    private StackPane contentPane;
    private StackPane artworkTile;
    private VBox lyricsMetaBox;
    private HBox sourceRow;
    private StackPane lyricsShell;
    private Button lyricsLockButton;
    private Button pureLyricsButton;
    private Button onlineToggleButton;
    private HBox desktopHero;

    private MediaPlayer mediaPlayer;
    private volatile boolean closing;
    private final app.musicplayer.playlist.DesktopLibraryController libraryController =
            new app.musicplayer.playlist.DesktopLibraryController();
    // 同一串行队列也供歌单登记与下载入库使用，不能另建线程并发访问数据库。
    private final java.util.concurrent.ExecutorService libraryExecutor =
            libraryController.executor();
    private final java.util.List<Track> pendingImports = new java.util.ArrayList<>();
    private Track currentTrack;
    private Lyrics currentLyrics = Lyrics.empty("导入歌曲后开始播放");
    private app.musicplayer.lyrics.LyricTimeline lyricTimeline =
            new app.musicplayer.lyrics.LyricTimeline(currentLyrics.lines());
    private boolean previewingOnlineResult;
    private long playbackRequestId;
    private long onlinePreviewRequestId;
    private boolean lyricsAutoScrollLocked;
    private boolean pureLyricsMode;
    private double lyricsFontSize = DEFAULT_LYRICS_FONT_SIZE;
    private LayoutMode layoutMode = LayoutMode.DESKTOP;

    /** 命令行或应用启动入口，准备运行环境并启动本文件的主流程。 */
    public static void main(String[] args) {
        launch(args);
    }

    /** 开始当前组件的工作，并接通所需状态与完成回调。 */
    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        layoutMode =
                LayoutMode.resolve(
                        getParameters().getRaw(), Boolean.getBoolean("musicplayer.mobile"));
        initializeServices(stage);
        stage.getIcons()
                .add(
                        new Image(
                                Objects.requireNonNull(
                                        getClass().getResourceAsStream("/app-icon.png"),
                                        "Missing application icon")));
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

        double initialWidth =
                layoutMode.isMobile()
                        ? 405
                        : Math.min(1320, Screen.getPrimary().getVisualBounds().getWidth() - 32);
        double initialHeight =
                layoutMode.isMobile()
                        ? 720
                        : Math.min(720, Screen.getPrimary().getVisualBounds().getHeight() - 32);
        Scene scene = new Scene(root, initialWidth, initialHeight);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        if (layoutMode.isMobile()) {
            scene.getStylesheets()
                    .add(getClass().getResource("/styles-mobile.css").toExternalForm());
        } else {
            scene.getStylesheets()
                    .add(getClass().getResource("/styles-desktop.css").toExternalForm());
        }
        scene.addEventFilter(
                KeyEvent.KEY_PRESSED,
                event -> {
                    if (event.getCode() == KeyCode.SPACE && !isTextInputFocused(scene)) {
                        togglePlayPause();
                        event.consume();
                    }
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
        Platform.runLater(
                () -> {
                    if (!layoutMode.isMobile()) {
                        onlineDrawer.restore(scene.getWidth());
                        updateOnlineToggleButton();
                    }
                    applyResponsiveLayout(scene.getWidth());
                });
        restoreSavedTracks();
    }

    /** 创建桌面分栏或手机比例的主内容布局。 */
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

    /** 创建桌面分栏或手机比例的主内容布局。 */
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

    /** 停止当前工作，按应用生命周期保留或释放相关资源。 */
    @Override
    public void stop() {
        closing = true;
        localSearch.close();
        if (lyricsController != null) lyricsController.close();
        if (artworkPresenter != null) artworkPresenter.close();
        artworkService.close();
        if (lyricsService != null) {
            lyricsService.close();
        }
        if (onlineTasks != null) onlineTasks.close();
        if (onlineMusicSearchService != null) {
            onlineMusicSearchService.close();
        }
        disposePlayer();
        playbackFileResolver.close();
        libraryController.finish(
                () -> {
                    if (playlistWorkspace != null) playlistWorkspace.shutdown().join();
                    if (storageSession != null) {
                        try {
                            storageSession.close();
                        } catch (java.io.IOException failure) {
                            java.util.logging.Logger.getLogger(MusicPlayerApp.class.getName())
                                    .warning(failure.getMessage());
                        }
                    } else if (database != null) database.close();
                });
    }

    /** 准备桌面数据目录、数据库、共享服务与平台控制器。 */
    private void initializeServices(Stage owner) {
        System.setProperty("musicplayer.desktop", "true");
        while (true) {
            try {
                APP_PATHS.initialize();
                break;
            } catch (RuntimeException failure) {
                var retry = new javafx.scene.control.ButtonType("重试");
                var choose = new javafx.scene.control.ButtonType("选择旧版数据目录");
                var exit = new javafx.scene.control.ButtonType("退出");
                var alert =
                        new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.ERROR,
                                "曲库初始化或迁移失败，原数据未删除。请关闭旧版后重试。\n" + failure.getMessage(),
                                retry,
                                choose,
                                exit);
                alert.setHeaderText("无法打开曲库");
                var selected = alert.showAndWait().orElse(exit);
                if (selected == exit) throw failure;
                if (selected == choose) {
                    DirectoryChooser chooser = new DirectoryChooser();
                    chooser.setTitle("选择含 music-player.db 的旧版 downloads 文件夹");
                    var directory = chooser.showDialog(null);
                    if (directory != null)
                        System.setProperty("musicplayer.migrate-from", directory.getAbsolutePath());
                }
            }
        }
        try {
            storageSession =
                    app.musicplayer.ui.DesktopStorageBootstrap.open(
                            owner, APP_PATHS, downloadSettings);
            database = storageSession.database();
        } catch (Exception e) {
            throw new IllegalStateException("无法初始化本地数据库", e);
        }
        downloadSettingsDialog =
                new app.musicplayer.ui.DownloadSettingsDialog(
                        downloadSettings,
                        new app.musicplayer.config.DownloadMigrationService(
                                database, downloadSettings, APP_PATHS.dataDir()),
                        libraryExecutor,
                        () -> closing,
                        Platform::exit,
                        path -> getHostServices().showDocument(path.toUri().toString()));
        lyricsService = new LyricsService(database, LYRICS_CACHE_DIR);
        lyricsController =
                new app.musicplayer.lyrics.DesktopLyricsController(
                        (track, duration, manual) ->
                                manual
                                        ? lyricsService.searchOnlineAsync(track, duration)
                                        : lyricsService.findLyrics(track, duration),
                        Platform::runLater,
                        () -> currentTrack,
                        new app.musicplayer.lyrics.DesktopLyricsController.Callbacks(
                                loading -> {
                                    loadingLyrics.setVisible(loading);
                                    loadingLyrics.setManaged(loading);
                                },
                                text -> sourceLabel.setText(text),
                                result -> {
                                    showLyrics(result.lyrics());
                                    showArtwork(result.artworkUrl());
                                },
                                this::showLyrics));
        onlineMusicSearchService = new OnlineMusicSearchService();
        playlistFiles = new app.musicplayer.playlist.DesktopPlaylistFiles(database);
        playlistWorkspace =
                new app.musicplayer.playlist.PlaylistWorkspace(
                        database,
                        APP_PATHS.dataDir().resolve(".playlist-staging"),
                        playlistFiles,
                        Platform::runLater);
        playlistWorkspace.downloads.listenPublished(
                id -> {
                    if (!closing) restoreSavedTracks();
                });
    }

    /** 检查焦点是否在输入框，避免播放快捷键影响文字输入。 */
    private static boolean isTextInputFocused(Scene scene) {
        return scene.getFocusOwner() instanceof TextField;
    }

    /** 创建对应布局的标题栏及主要操作入口。 */
    private HBox createTopBar(Stage stage) {
        Label appTitle = new Label("ZA音乐");
        appTitle.getStyleClass().add("desktop-app-title");
        Label sectionLabel = new Label("本地曲库");
        sectionLabel.getStyleClass().add("desktop-section-label");

        MenuItem importFilesItem = new MenuItem("导入音频文件");
        importFilesItem.setOnAction(event -> importFiles(stage));
        MenuItem importFolderItem = new MenuItem("导入文件夹");
        importFolderItem.setOnAction(event -> importFolder(stage));
        MenuItem playlistImport = new MenuItem("歌单导入 / 我的歌单");
        playlistImport.setOnAction(event -> showSavedPlaylists(stage));
        MenuButton importButton =
                new MenuButton("导入音乐", null, importFilesItem, importFolderItem, playlistImport);
        importButton.getStyleClass().add("primary-button");

        Button savedPlaylists = new Button("我的歌单");
        savedPlaylists.setOnAction(event -> showSavedPlaylists(stage));
        Button storageSettingsButton = new Button("下载位置");
        storageSettingsButton.setId("download-settings");
        storageSettingsButton.setOnAction(event -> downloadSettingsDialog.show(stage));
        playModeBox = createPlayModeBox();
        onlineToggleButton = new Button("在线搜索");
        onlineToggleButton.getStyleClass().add("online-toggle-button");
        onlineToggleButton.setOnAction(
                event -> {
                    if (onlineDrawer != null) {
                        onlineDrawer.toggleFromHeader();
                        updateOnlineToggleButton();
                    }
                });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusLabel = new Label("请选择文件夹或音频文件");
        statusLabel.getStyleClass().addAll("muted-label", "desktop-status");
        statusLabel.setMaxWidth(340);
        Tooltip statusTooltip = new Tooltip();
        statusTooltip.textProperty().bind(statusLabel.textProperty());
        statusLabel.setTooltip(statusTooltip);
        statusLabel
                .textProperty()
                .addListener(
                        (observable, oldValue, value) -> {
                            statusLabel.getStyleClass().remove("status-error");
                            if (value != null && (value.contains("失败") || value.contains("无法"))) {
                                statusLabel.getStyleClass().add("status-error");
                            }
                        });

        HBox topBar =
                new HBox(
                        12,
                        appTitle,
                        sectionLabel,
                        spacer,
                        statusLabel,
                        importButton,
                        savedPlaylists,
                        storageSettingsButton,
                        onlineToggleButton);
        topBar.getStyleClass().add("top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 24, 0, 24));
        topBar.setMinHeight(56);
        topBar.setPrefHeight(56);
        topBar.setMaxHeight(56);
        return topBar;
    }

    /** 取得当前有效播放队列，独立歌单队列优先于曲库顺序。 */
    private List<Track> playbackQueue() {
        return playbackOrder.isEmpty() ? tracks : playbackOrder;
    }

    /** 显示已保存项，供本文件的主流程使用。 */
    private void showSavedPlaylists(Stage stage) {
        if (savedPlaylistWindow == null)
            savedPlaylistWindow =
                    new app.musicplayer.ui.PlaylistWindow(
                            stage,
                            playlistWorkspace,
                            playlistFiles,
                            () -> downloadSettings.suggested().toString(),
                            this::playSavedPlaylist);
        savedPlaylistWindow.show();
    }

    /** 把已保存位置登记为歌曲，再按替换或追加选项更新播放队列。 */
    private void playSavedPlaylist(List<String> locations, boolean append) {
        CompletableFuture.supplyAsync(() -> playlistFiles.register(locations), libraryExecutor)
                .whenComplete(
                        (saved, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing) return;
                                            if (error != null) {
                                                statusLabel.setText(
                                                        "读取歌单歌曲失败：" + error.getMessage());
                                                return;
                                            }
                                            if (append && playbackOrder.isEmpty()) {
                                                playbackOrder.addAll(tracks);
                                                savedPlaybackIndex = tracks.indexOf(currentTrack);
                                            }
                                            tracks.addAll(
                                                    trackLibrary
                                                            .mergeUnique(tracks, saved)
                                                            .addedTracks());
                                            sortTracks();
                                            var ordered =
                                                    saved.stream()
                                                            .map(
                                                                    t ->
                                                                            tracks.stream()
                                                                                    .filter(
                                                                                            existing ->
                                                                                                    existing.path()
                                                                                                            .toAbsolutePath()
                                                                                                            .normalize()
                                                                                                            .equals(
                                                                                                                    t.path()
                                                                                                                            .toAbsolutePath()
                                                                                                                            .normalize()))
                                                                                    .findFirst()
                                                                                    .orElse(t))
                                                            .toList();
                                            if (append) playbackOrder.addAll(ordered);
                                            else {
                                                playbackOrder.clear();
                                                playbackOrder.addAll(ordered);
                                                savedPlaybackIndex = -1;
                                                if (!ordered.isEmpty()) playTrack(0);
                                            }
                                            statusLabel.setText(
                                                    append ? "已加入播放队列" : "正在播放歌单中的本地歌曲");
                                        }));
    }

    /** 同步按钮，供本文件的主流程使用。 */
    private void updateOnlineToggleButton() {
        if (onlineToggleButton == null || onlineDrawer == null) return;
        onlineToggleButton.setText(onlineDrawer.isExpanded() ? "收起搜索" : "在线搜索");
    }

    /** 创建对应布局的标题栏及主要操作入口。 */
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
        reloadLyricsItem.setOnAction(
                event -> {
                    if (currentTrack != null) {
                        loadLyrics(currentTrack, true);
                    }
                });
        MenuItem savedPlaylistsItem = new MenuItem("我的歌单 / 导入歌单");
        savedPlaylistsItem.setOnAction(event -> showSavedPlaylists(stage));
        MenuItem storageSettingsItem = new MenuItem("下载位置设置");
        storageSettingsItem.setOnAction(event -> downloadSettingsDialog.show(stage));
        MenuButton manageButton =
                new MenuButton(
                        "管理",
                        null,
                        savedPlaylistsItem,
                        storageSettingsItem,
                        importFolderItem,
                        removeTrackItem,
                        reloadLyricsItem);

        playModeBox = createPlayModeBox();
        playModeBox.getStyleClass().add("mobile-play-mode");
        playModeBox.setButtonCell(
                new ListCell<>() {
                    /** 在 JavaFX 复用列表行时绑定当前对象或清空旧内容。 */
                    @Override
                    protected void updateItem(PlayMode item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(
                                empty || item == null
                                        ? null
                                        : switch (item) {
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

    /** 创建播放模式选择器并连接设置保存与播放器更新。 */
    private ComboBox<PlayMode> createPlayModeBox() {
        ComboBox<PlayMode> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(PlayMode.ORDER, PlayMode.SHUFFLE, PlayMode.REPEAT_ONE);
        comboBox.getSelectionModel().select(PlayMode.ORDER);
        comboBox.setButtonCell(
                new ListCell<>() {
                    /** 在 JavaFX 复用列表行时绑定当前对象或清空旧内容。 */
                    @Override
                    protected void updateItem(PlayMode mode, boolean empty) {
                        super.updateItem(mode, empty);
                        setText(
                                empty || mode == null
                                        ? null
                                        : switch (mode) {
                                            case ORDER -> "顺序";
                                            case SHUFFLE -> "随机";
                                            case REPEAT_ONE -> "单曲";
                                        });
                    }
                });
        return comboBox;
    }

    /** 创建本地曲库面板，并连接搜索、排序、导入和播放操作。 */
    private void createPlaylistPane() {
        playlistPane =
                new PlaylistPane(
                        filteredTracks,
                        preferences,
                        this::applyTrackFilter,
                        this::sortTracks,
                        track -> {
                            playbackOrder.clear();
                            playTrack(track);
                        },
                        this::removeSelectedTrack,
                        !layoutMode.isMobile());
        searchField = playlistPane.searchField();
        sortTypeBox = playlistPane.sortTypeBox();
        sortOrderBox = playlistPane.sortOrderBox();
        playlistView = playlistPane.playlistView();
    }

    /** 创建在线面板并接通搜索、预览与下载协调器。 */
    private void createOnlineDrawer() {
        onlineDrawer =
                new OnlineDrawer(
                        onlineResults,
                        preferences,
                        this::searchOnlineTracks,
                        this::previewOnlineTrack,
                        this::downloadAndPlayOnlineTrack);
        onlineDrawer.setOnExpandedChanged(expanded -> updateOnlineToggleButton());
        onlineResultsView = onlineDrawer.resultsView();
        onlineTasks =
                new DesktopOnlineTasks(
                        onlineMusicSearchService,
                        onlineDrawer,
                        onlineResults,
                        new DesktopOnlineTasks.Callbacks(
                                text -> statusLabel.setText(text),
                                () -> onlinePreviewRequestId++,
                                notice -> {
                                    OnlineTrackInfo selected =
                                            onlineResultsView.getSelectionModel().getSelectedItem();
                                    if (!previewingOnlineResult
                                            || selected == null
                                            || !selected.identity()
                                                    .equals(notice.track().identity())) return;
                                    if (notice.event().stage() == DownloadEvent.Stage.FAILED)
                                        showLyrics(Lyrics.empty("下载失败，可点击重试"));
                                    else if (notice.event().stage()
                                            == DownloadEvent.Stage.CANCELLED)
                                        showLyrics(Lyrics.empty("下载已取消"));
                                }));
    }

    /** 组装当前播放歌曲、封面与歌词显示区。 */
    private StackPane createNowPlaying() {
        titleLabel = new Label("未播放歌曲");
        titleLabel.getStyleClass().add("track-title");
        titleLabel.setWrapText(true);
        artistLabel = new Label("导入文件夹或音频文件后双击歌曲播放");
        artistLabel.getStyleClass().add("track-artist");
        artistLabel.setWrapText(true);
        sourceLabel = new Label("歌词来源：暂无");
        sourceLabel.getStyleClass().add("muted-label");
        if (!layoutMode.isMobile()) sourceLabel.setWrapText(true);
        loadingLyrics = new ProgressIndicator();
        loadingLyrics.setMaxSize(18, 18);
        loadingLyrics.setVisible(false);
        loadingLyrics.setManaged(false);

        sourceRow = new HBox(8, sourceLabel, loadingLyrics);
        sourceRow.getStyleClass().add("lyrics-source-row");

        lyricsView = new ListView<>(lyricRows);
        lyricsView.getStyleClass().add("lyrics-view");
        lyricsView.setFocusTraversable(false);
        lyricsView.setMinWidth(0);
        lyricsView.setMinHeight(0);
        lyricsView.setCellFactory(
                v ->
                        new ListCell<>() {
                            /** 在 JavaFX 复用列表行时绑定当前对象或清空旧内容。 */
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                setText(empty || item == null ? null : item);
                                setWrapText(true);
                                setStyle(
                                        String.format(
                                                Locale.US,
                                                "-fx-font-size: %.1fpx;",
                                                lyricsFontSize));
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
            artworkTile = new StackPane(artworkPlaceholder, artworkImageView);
            artworkTile.getStyleClass().add("artwork-tile");
            artworkTile.setMinSize(144, 144);
            artworkTile.setPrefSize(144, 144);
            artworkTile.setMaxSize(144, 144);
            artworkImageView.setFitWidth(144);
            artworkImageView.setFitHeight(144);
            artworkImageView.setPreserveRatio(true);
            artworkImageView
                    .visibleProperty()
                    .addListener(
                            (observable, oldValue, visible) ->
                                    artworkPlaceholder.setVisible(!visible));
            desktopHero = new HBox(20, artworkTile, lyricsMetaBox);
            desktopHero.getStyleClass().add("desktop-hero");
            desktopHero.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(lyricsMetaBox, Priority.ALWAYS);
            nowPlaying = new VBox(16, desktopHero, lyricsShell, lyricsToolbar);
        }
        nowPlaying.getStyleClass().add("content-foreground");
        nowPlaying.setPadding(layoutMode.isMobile() ? new Insets(24, 22, 16, 22) : new Insets(24));
        nowPlaying.setMinWidth(0);
        nowPlaying.setMinHeight(0);

        StackPane stack =
                layoutMode.isMobile()
                        ? new StackPane(artworkImageView, artworkDimmer, nowPlaying)
                        : new StackPane(nowPlaying);
        stack.getStyleClass().add("content");
        stack.setMinWidth(0);
        stack.setMinHeight(0);
        contentPane = stack;
        applyAmbientGlow(DominantColorExtractor.DEFAULT_AMBIENT_COLOR);
        if (layoutMode.isMobile()) {
            artworkImageView.setPreserveRatio(false);
            artworkImageView.fitWidthProperty().bind(stack.widthProperty());
            artworkImageView.fitHeightProperty().bind(stack.heightProperty());
        }
        showLyrics(Lyrics.empty("导入歌曲后开始播放"));
        applyPureLyricsMode();
        return stack;
    }

    /** 创建歌词刷新、锁定、字号及纯歌词模式控件。 */
    private HBox createLyricsToolbar() {
        lyricsLockButton = createLyricsToolButton("锁定歌词", this::toggleLyricsLock);
        Button zoomInButton = createLyricsToolButton("放大字体", () -> changeLyricsFontSize(1.5));
        Button zoomOutButton = createLyricsToolButton("缩小字体", () -> changeLyricsFontSize(-1.5));
        pureLyricsButton = createLyricsToolButton("纯歌词模式", this::togglePureLyricsMode);
        Button reloadLyricsButton =
                createLyricsToolButton(
                        "刷新歌词",
                        () -> {
                            if (currentTrack != null) loadLyrics(currentTrack, true);
                        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar =
                layoutMode.isMobile()
                        ? new HBox(
                                8,
                                lyricsLockButton,
                                zoomInButton,
                                zoomOutButton,
                                pureLyricsButton,
                                spacer)
                        : new HBox(
                                8,
                                spacer,
                                reloadLyricsButton,
                                lyricsLockButton,
                                zoomInButton,
                                zoomOutButton,
                                pureLyricsButton);
        toolbar.getStyleClass().add("lyrics-toolbar");
        updateLyricsToolbarState();
        return toolbar;
    }

    /** 创建具有统一样式和操作回调的歌词工具按钮。 */
    private Button createLyricsToolButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("toolbar-button");
        button.setFocusTraversable(!layoutMode.isMobile());
        button.setOnAction(e -> action.run());
        return button;
    }

    /** 切换歌词自动滚动锁定状态并保存设置。 */
    private void toggleLyricsLock() {
        lyricsAutoScrollLocked = !lyricsAutoScrollLocked;
        updateLyricsToolbarState();
        if (!lyricsAutoScrollLocked && mediaPlayer != null) {
            updateHighlightedLyric(mediaPlayer.getCurrentTime());
        }
    }

    /** 在约定上下限内调整歌词字号并刷新界面。 */
    private void changeLyricsFontSize(double delta) {
        lyricsFontSize = clamp(lyricsFontSize + delta, MIN_LYRICS_FONT_SIZE, MAX_LYRICS_FONT_SIZE);
        if (lyricsView != null) lyricsView.refresh();
    }

    /** 切换纯歌词显示方式并保存用户设置。 */
    private void togglePureLyricsMode() {
        pureLyricsMode = !pureLyricsMode;
        applyPureLyricsMode();
        updateLyricsToolbarState();
    }

    /** 根据纯歌词设置更新封面、标题及歌词区的可见性。 */
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
            artworkImageView.setOpacity(
                    layoutMode.isMobile() ? (pureLyricsMode ? 0.16 : 0.34) : 1.0);
        }
        if (lyricsShell != null) {
            StackPane.setMargin(
                    lyricsShell, pureLyricsMode ? new Insets(2, 0, 0, 0) : Insets.EMPTY);
        }
    }

    /** 把当前歌词设置同步到工具栏按钮状态。 */
    private void updateLyricsToolbarState() {
        toggleToolbarButtonState(lyricsLockButton, lyricsAutoScrollLocked);
        toggleToolbarButtonState(pureLyricsButton, pureLyricsMode);
    }

    /** 按启用状态切换工具按钮的样式与选择标记。 */
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

    /** 创建底部播放控制栏并连接播放、进度与音量操作。 */
    private PlaybackControls createControls() {
        playbackControls =
                new PlaybackControls(
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

    /** 打开文件夹或音频选择器，再交给后台曲库控制器读取。 */
    private void importFolder(Stage stage) {
        DirectoryChooser c = new DirectoryChooser();
        c.setTitle("选择歌曲文件夹");
        Path dp = Path.of(System.getProperty("user.home"), "Music");
        if (Files.isDirectory(dp)) c.setInitialDirectory(dp.toFile());
        var dir = c.showDialog(stage);
        if (dir == null) return;
        statusLabel.setText("正在扫描文件夹...");
        libraryController
                .scanFolder(trackLibrary, dir.toPath())
                .whenComplete(
                        (imported, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing) return;
                                            if (error != null) {
                                                statusLabel.setText("导入失败：" + error.getMessage());
                                                return;
                                            }
                                            addImportedTracks(imported, "文件夹");
                                        }));
    }

    /** 打开文件夹或音频选择器，再交给后台曲库控制器读取。 */
    private void importFiles(Stage stage) {
        FileChooser c = new FileChooser();
        c.setTitle("选择音频文件");
        c.getExtensionFilters()
                .add(
                        new FileChooser.ExtensionFilter(
                                "音频文件", "*.mp3", "*.m4a", "*.aac", "*.wav", "*.aif", "*.aiff"));
        List<java.io.File> files = c.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;
        libraryController
                .fromFiles(trackLibrary, files.stream().map(java.io.File::toPath).toList())
                .whenComplete(
                        (imported, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing) return;
                                            if (error != null) {
                                                statusLabel.setText("导入失败：" + error.getMessage());
                                                return;
                                            }
                                            addImportedTracks(imported, "音频文件");
                                        }));
    }

    /** 合并待导入歌曲并持久化，保存成功后才更新可播放列表。 */
    private void addImportedTracks(List<Track> imported, String source) {
        if (imported == null || imported.isEmpty()) {
            statusLabel.setText("没有找到可导入的" + source);
            return;
        }
        var existing = new java.util.ArrayList<>(tracks);
        existing.addAll(pendingImports);
        var result = trackLibrary.mergeUnique(existing, imported);
        List<Track> added = result.addedTracks();
        if (added.isEmpty()) {
            statusLabel.setText("歌曲已经在曲库或导入任务中");
            return;
        }
        pendingImports.addAll(added);
        libraryController
                .save(database, added)
                .whenComplete(
                        (ignored, error) ->
                                Platform.runLater(
                                        () -> {
                                            pendingImports.removeAll(added);
                                            if (closing) return;
                                            if (error != null) {
                                                statusLabel.setText("保存曲库失败：" + error.getMessage());
                                                return;
                                            }
                                            tracks.addAll(added);
                                            sortTracks();
                                            applyTrackFilter(
                                                    searchField == null
                                                            ? ""
                                                            : searchField.getText());
                                            playlistView.getSelectionModel().select(added.get(0));
                                            statusLabel.setText("已新增 " + added.size() + " 首");
                                            if (currentTrack == null) playTrack(added.get(0));
                                        }));
    }

    /** 后台恢复有效歌曲，并与当前界面已有曲库去重合并。 */
    private void restoreSavedTracks() {
        libraryController
                .restore(database, trackLibrary)
                .whenComplete(
                        (saved, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing) return;
                                            if (error != null) {
                                                statusLabel.setText("读取曲库失败：" + error.getMessage());
                                                return;
                                            }
                                            if (!saved.isEmpty()) {
                                                tracks.addAll(
                                                        trackLibrary
                                                                .mergeUnique(tracks, saved)
                                                                .addedTracks());
                                                sortTracks();
                                                applyTrackFilter("");
                                                playlistView.getSelectionModel().select(0);
                                                statusLabel.setText(
                                                        "已恢复 " + tracks.size() + " 首歌曲");
                                            }
                                        }));
    }

    /** 把最新搜索文字交给本地搜索协调器。 */
    private void applyTrackFilter(String q) {
        localSearch.search(q);
    }

    /** 应用新的搜索结果，保留有效选择或当前播放歌曲。 */
    private void displayFilteredTracks(List<Track> values) {
        if (closing) return;
        Track selected =
                playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        filteredTracks.setAll(values);
        if (playlistView == null) return;
        if (selected != null && filteredTracks.contains(selected))
            playlistView.getSelectionModel().select(selected);
        else if (currentTrack != null && filteredTracks.contains(currentTrack))
            playlistView.getSelectionModel().select(currentTrack);
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0);
    }

    /** 播放指定歌曲或队列位置，并开始对应的歌词与封面加载。 */
    private void playTrack(int idx) {
        var queue = playbackQueue();
        if (idx >= 0 && idx < queue.size()) {
            if (!playbackOrder.isEmpty()) savedPlaybackIndex = idx;
            playTrack(queue.get(idx));
        }
    }

    /** 播放指定歌曲或队列位置，并开始对应的歌词与封面加载。 */
    private void playTrack(Track track) {
        onlinePreviewRequestId++;
        int idx = tracks.indexOf(track);
        if (idx < 0) return;
        if (!playbackOrder.isEmpty()) {
            if (!playbackOrder.contains(track)) {
                playbackOrder.clear();
                savedPlaybackIndex = -1;
            } else if (savedPlaybackIndex < 0
                    || savedPlaybackIndex >= playbackOrder.size()
                    || playbackOrder.get(savedPlaybackIndex) != track)
                savedPlaybackIndex = playbackOrder.indexOf(track);
        }
        disposePlayer();
        previewingOnlineResult = false;
        currentTrack = track;
        if (playbackContextLabel != null) playbackContextLabel.setText("正在播放");
        playbackControls.setPlaying(false);
        if (mobileViews != null) {
            mobileViews.select(MobileViewSwitcher.Section.NOW_PLAYING);
        }
        if (filteredTracks.contains(track)) {
            playlistView.getSelectionModel().select(track);
            playlistView.scrollTo(track);
        }
        playlistPane.setCurrentTrack(track);
        titleLabel.setText(currentTrack.title());
        artistLabel.setText(currentTrack.artist());
        playbackControls.setTrackInfo(currentTrack.title(), currentTrack.artist());
        progressSlider.setValue(0);
        timeLabel.setText("00:00 / 00:00");
        showArtwork(null);
        showLyrics(Lyrics.empty("正在准备歌词..."));
        long request = playbackRequestId;
        CompletableFuture.supplyAsync(
                        () -> {
                            var resolved = playbackFileResolver.resolve(track.path());
                            if (audioFileInspector.detect(resolved.path()) == AudioFormat.RAW_AAC) {
                                resolved.close();
                                throw new IllegalStateException("JavaFX 无法稳定播放原始 AAC 音频");
                            }
                            return resolved;
                        },
                        libraryExecutor)
                .whenComplete(
                        (resolution, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing
                                                    || request != playbackRequestId
                                                    || currentTrack != track) {
                                                if (resolution != null) resolution.close();
                                                return;
                                            }
                                            if (error != null) {
                                                showPlayerError(error);
                                                return;
                                            }
                                            if (resolution.correctedExtension())
                                                statusLabel.setText("已按实际音频格式准备播放");
                                            playbackResolution = resolution;
                                            createPlayer(track, resolution.path(), request);
                                        }));
    }

    /** 为解析后的音频创建 JavaFX 播放器，只允许当前请求接管播放状态。 */
    private void createPlayer(Track track, Path playbackPath, long request) {
        try {
            Media media = new Media(playbackPath.toUri().toString());
            MediaPlayer created = new MediaPlayer(media);
            mediaPlayer = created;
            created.setVolume(volumeSlider.getValue());
            created.currentTimeProperty()
                    .addListener(
                            (o, ot, nt) -> {
                                if (mediaPlayer == created) updatePlaybackProgress(nt);
                            });
            created.setOnReady(
                    () -> {
                        if (mediaPlayer != created || closing || currentTrack != track) return;
                        updateMetadata(media);
                        updateDurationLabel();
                        loadLyrics(track, false);
                        created.play();
                    });
            created.setOnPlaying(
                    () -> {
                        if (mediaPlayer == created) playbackControls.setPlaying(true);
                    });
            created.setOnPaused(
                    () -> {
                        if (mediaPlayer == created) playbackControls.setPlaying(false);
                    });
            created.setOnStopped(
                    () -> {
                        if (mediaPlayer == created) playbackControls.setPlaying(false);
                    });
            created.setOnEndOfMedia(
                    () -> {
                        if (mediaPlayer == created) handleEndOfMedia();
                    });
            created.setOnError(
                    () -> {
                        if (mediaPlayer == created) showPlayerError(created.getError());
                    });
            media.setOnError(
                    () -> {
                        if (mediaPlayer == created) showPlayerError(media.getError());
                    });
        } catch (MediaException error) {
            disposePlayer();
            showPlayerError(error);
        }
    }

    /** 使用有效的音频标签或在线信息更新歌曲标题与歌手。 */
    private void updateMetadata(Media media) {
        String t = valueAsString(media.getMetadata().get("title")),
                a = valueAsString(media.getMetadata().get("artist"));
        String oldTitle = currentTrack.title(), oldArtist = currentTrack.artist();
        currentTrack.updateMetadata(t, a);
        titleLabel.setText(currentTrack.title());
        artistLabel.setText(currentTrack.artist());
        playbackControls.setTrackInfo(currentTrack.title(), currentTrack.artist());
        playlistView.refresh();
        if (!Objects.equals(oldTitle, currentTrack.title())
                || !Objects.equals(oldArtist, currentTrack.artist())) sortTracks();
        Track snapshot = new Track(currentTrack.path());
        snapshot.updateMetadata(currentTrack.title(), currentTrack.artist());
        var duration = mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration());
        CompletableFuture.runAsync(() -> database.saveTrack(snapshot, duration), libraryExecutor)
                .whenComplete(
                        (ignored, error) -> {
                            if (error != null && !closing)
                                Platform.runLater(() -> statusLabel.setText("歌曲信息保存失败"));
                        });
    }

    /** 为显示或字段读取提取安全的字符串值。 */
    private static String valueAsString(Object v) {
        return v instanceof String s ? s : null;
    }

    // ========== 在线搜索结果：双击爬虫下载到本地再播放 ==========

    /** 下载并登记在线歌曲，完成后按当前操作决定是否自动播放。 */
    private void downloadAndPlayOnlineTrack(OnlineTrackInfo info) {
        if (info == null) return;
        if (onlineTasks.cancelDownload(info)) return;
        downloadSettingsDialog.resolve(
                primaryStage,
                directory -> beginDownloadAndPlay(info, directory, ++onlinePreviewRequestId));
    }

    /** 已确认可写目标后才调整播放；每次任务使用捕获的目录和请求编号。 */
    private void beginDownloadAndPlay(OnlineTrackInfo info, Path directory, long reqId) {
        if (closing) return;
        if (reqId == onlinePreviewRequestId) {
            disposePlayer();
            cancelLyricRetry();
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
        }

        onlineTasks.download(
                info,
                directory,
                path ->
                        CompletableFuture.supplyAsync(
                                () -> {
                                    Track downloaded = new Track(path);
                                    database.saveTracks(List.of(downloaded));
                                    trackLibrary.primeCreationTimes(List.of(downloaded));
                                    return path;
                                },
                                libraryExecutor),
                downloadedPath -> {
                    if (closing) return;
                    boolean autoPlay = reqId == onlinePreviewRequestId;
                    Track newTrack = new Track(downloadedPath);
                    TrackLibraryService.ImportResult result =
                            trackLibrary.mergeUnique(tracks, List.of(newTrack));
                    if (result.addedTracks().isEmpty()) {
                        statusLabel.setText("已在歌单中：" + info.title());
                        Track et =
                                tracks.stream()
                                        .filter(
                                                t ->
                                                        t.path()
                                                                .toAbsolutePath()
                                                                .normalize()
                                                                .toString()
                                                                .equals(
                                                                        downloadedPath
                                                                                .toAbsolutePath()
                                                                                .normalize()
                                                                                .toString()))
                                        .findFirst()
                                        .orElse(newTrack);
                        if (autoPlay) playTrack(et);
                        return;
                    }

                    tracks.add(newTrack);

                    sortTracks();
                    if (searchField != null && !searchField.getText().isBlank())
                        searchField.setText("");
                    else applyTrackFilter("");
                    statusLabel.setText("爬取下载完成：" + info.title());
                    if (autoPlay) playTrack(newTrack);
                });
    }

    // ========== 共享播放控制 ==========

    /** 在播放与暂停之间切换；尚无播放器时从有效选择开始播放。 */
    private void togglePlayPause() {
        if (mediaPlayer == null) {
            Track sel = playlistView.getSelectionModel().getSelectedItem();
            if (sel != null) playTrack(sel);
            else if (!tracks.isEmpty()) playTrack(0);
            return;
        }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) mediaPlayer.pause();
        else mediaPlayer.play();
    }

    /** 按当前队列和播放模式选择上一首或下一首。 */
    private void previousTrack() {
        if (tracks.isEmpty()) return;
        int cur = currentTrackIndex();
        playTrack(cur <= 0 ? playbackQueue().size() - 1 : cur - 1);
    }

    /** 按当前队列和播放模式选择上一首或下一首。 */
    private void nextTrack(boolean manual) {
        if (tracks.isEmpty()) return;
        PlayMode m = playModeBox.getValue();
        if (m == PlayMode.REPEAT_ONE && !manual) {
            replayCurrent();
            return;
        }
        playTrack(
                switch (m) {
                    case SHUFFLE -> randomIndex();
                    case ORDER, REPEAT_ONE ->
                            nextOrderedIndex(currentTrackIndex(), playbackQueue().size());
                });
    }

    /** 计算顺序模式下一首的队列索引，末曲后回到第一首。 */
    static int nextOrderedIndex(int currentIndex, int trackCount) {
        if (trackCount <= 0) throw new IllegalArgumentException("trackCount must be positive");
        return app.musicplayer.playlist.QueueOrder.relative(currentIndex, 1, trackCount);
    }

    /** 随机选择不同于当前歌曲的队列位置，单曲队列保留唯一位置。 */
    private int randomIndex() {
        int size = playbackQueue().size();
        if (size <= 1) return 0;
        int c = currentTrackIndex(), n;
        do {
            n = random.nextInt(size);
        } while (n == c);
        return n;
    }

    /** 在自然播放结束时按当前播放模式继续。 */
    private void handleEndOfMedia() {
        nextTrack(false);
    }

    /** 取得当前歌曲在播放队列中的位置，必要时使用有效选择作为起点。 */
    private int currentTrackIndex() {
        if (!playbackOrder.isEmpty()
                && savedPlaybackIndex >= 0
                && savedPlaybackIndex < playbackOrder.size()
                && playbackOrder.get(savedPlaybackIndex) == currentTrack) return savedPlaybackIndex;
        int i = currentTrack == null ? -1 : playbackQueue().indexOf(currentTrack);
        if (i >= 0) return i;
        Track s = playlistView.getSelectionModel().getSelectedItem();
        return s == null ? -1 : playbackQueue().indexOf(s);
    }

    /** 将当前音频回到起点并开始播放。 */
    private void replayCurrent() {
        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.ZERO);
            mediaPlayer.play();
        }
    }

    /** 把进度滑块换算为音频时间，跳转播放器及歌词时间轴。 */
    private void seekToProgress() {
        if (mediaPlayer == null || mediaPlayer.getTotalDuration() == null) return;
        Duration t = mediaPlayer.getTotalDuration();
        if (t.greaterThan(Duration.ZERO)) {
            Duration target = t.multiply(progressSlider.getValue());
            mediaPlayer.seek(target);
            highlightLyric(lyricTimeline.seek(Math.round(target.toMillis())));
        }
    }

    /** 刷新播放进度与歌词；拖动进度时避免覆盖用户选择。 */
    private void updatePlaybackProgress(Duration ct) {
        if (mediaPlayer == null) return;
        Duration t = mediaPlayer.getTotalDuration();
        if (t != null && t.greaterThan(Duration.ZERO) && !playbackControls.isSeeking()) {
            progressSlider.setValue(ct.toMillis() / t.toMillis());
        }
        updateDurationLabel();
        updateHighlightedLyric(ct);
    }

    /** 同步当前时长和总时长的显示。 */
    private void updateDurationLabel() {
        if (mediaPlayer == null) {
            timeLabel.setText("00:00 / 00:00");
            return;
        }
        Duration c = mediaPlayer.getCurrentTime(), t = mediaPlayer.getTotalDuration();
        timeLabel.setText(formatTime(c) + " / " + formatTime(t));
    }

    /** 把有效播放时长格式化为分秒文字，无效时长使用零值显示。 */
    private static String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.lessThan(Duration.ZERO)) {
            return "00:00";
        }
        long seconds = (long) Math.floor(duration.toSeconds());
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    // ========== 歌词与封面 ==========

    /** 把当前歌曲及时长交给桌面歌词控制器，手动刷新时跳过缓存。 */
    private void loadLyrics(Track track, boolean manual) {
        if (track == null) return;
        previewingOnlineResult = false;
        java.time.Duration duration =
                mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration());
        lyricsController.load(track, duration, manual);
    }

    /** 取消等待中的歌词重试，保留歌词服务供后续歌曲使用。 */
    private void cancelLyricRetry() {
        if (lyricsController != null) lyricsController.cancelRetry();
    }

    /** 把 JavaFX 时长转换为共享服务使用的 java.time.Duration。 */
    private static java.time.Duration javaDuration(Duration d) {
        if (d == null || d.isUnknown() || d.lessThanOrEqualTo(Duration.ZERO)) return null;
        return java.time.Duration.ofMillis(Math.max(0, Math.round(d.toMillis())));
    }

    /** 将当前歌词模型交给界面，并重新建立时间轴或文本布局。 */
    private void showLyrics(Lyrics lyrics) {
        currentLyrics = lyrics;
        lyricTimeline = new app.musicplayer.lyrics.LyricTimeline(lyrics.lines());
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

    /** 按当前排序选项调整曲库顺序并刷新搜索快照。 */
    private void sortTracks() {
        PlaylistSort sort = sortTypeBox == null ? PlaylistSort.TITLE : sortTypeBox.getValue();
        SortDirection direction =
                sortOrderBox == null ? SortDirection.ASCENDING : sortOrderBox.getValue();
        Track selected =
                playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        tracks.sort(trackLibrary.comparator(sort, direction));
        localSearch.replace(
                SearchSnapshot.ofTracks(tracks), searchField == null ? "" : searchField.getText());
        if (selected != null && filteredTracks.contains(selected)) {
            playlistView.getSelectionModel().select(selected);
            playlistView.scrollTo(selected);
        }
    }

    /** 根据窗口或设备的可用尺寸调整布局或封面大小。 */
    private void applyResponsiveLayout(double windowWidth) {
        if (statusLabel != null) {
            statusLabel.setMaxWidth(
                    layoutMode.isMobile()
                            ? Math.max(200, windowWidth - 28)
                            : clamp(windowWidth * 0.24, 220, 420));
        }
        if (onlineDrawer != null && !layoutMode.isMobile()) {
            onlineDrawer.applyResponsiveLayout(windowWidth);
        }
    }

    /** 将数值限制在指定上下界之间。 */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 加载指定封面，空地址或失败时使用现有占位规则。 */
    private void showArtwork(String url) {
        if (artworkImageView == null) return;
        if (artworkPresenter == null) {
            artworkPresenter = new ArtworkPresenter(artworkService, artworkImageView);
            artworkPresenter.setOnColorExtracted(this::applyAmbientGlow);
        }
        artworkPresenter.show(url);
    }

    /** 响应封面主色调提取结果，平滑应用主视口背景流光与封面阴影环境光。 */
    private void applyAmbientGlow(Color color) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> applyAmbientGlow(color));
            return;
        }
        Color effective = color != null ? color : DominantColorExtractor.DEFAULT_AMBIENT_COLOR;
        int r = (int) Math.round(effective.getRed() * 255);
        int g = (int) Math.round(effective.getGreen() * 255);
        int b = (int) Math.round(effective.getBlue() * 255);

        if (contentPane != null) {
            String gradient =
                    String.format(
                            Locale.ROOT,
                            "-fx-background-color: radial-gradient(focus-angle 0deg, focus-distance 0%%, center 65%% 25%%, radius 65%%, rgba(%d, %d, %d, 0.25) 0%%, rgba(11, 14, 20, 0.98) 100%%);",
                            r,
                            g,
                            b);
            contentPane.setStyle(gradient);
        }

        if (artworkTile != null && (layoutMode == null || !layoutMode.isMobile())) {
            DropShadow glow =
                    new DropShadow(
                            BlurType.GAUSSIAN,
                            Color.color(effective.getRed(), effective.getGreen(), effective.getBlue(), 0.35),
                            24,
                            0.12,
                            0,
                            4);
            artworkTile.setEffect(glow);
        }
    }

    /** 把实际播放位置交给歌词时间轴，更新活动行。 */
    private void updateHighlightedLyric(Duration ct) {
        if (lyricsAutoScrollLocked
                || previewingOnlineResult
                || currentLyrics == null
                || !currentLyrics.timed()
                || currentLyrics.lines().isEmpty()) return;
        int sel = lyricTimeline.advance(Math.round(ct.toMillis()));
        highlightLyric(sel);
    }

    /** 选中当前活动歌词行，并按滚动锁定设置决定是否滚动。 */
    private void highlightLyric(int sel) {
        if (lyricsAutoScrollLocked
                || previewingOnlineResult
                || currentLyrics == null
                || !currentLyrics.timed()) return;
        if (lyricsView.getSelectionModel().getSelectedIndex() != sel) {
            if (sel < 0) lyricsView.getSelectionModel().clearSelection();
            else {
                lyricsView.getSelectionModel().select(sel);
                lyricsView.scrollTo(Math.max(0, sel - 4));
            }
        }
    }

    /** 把播放器异常转换为可显示提示，保留可诊断的具体原因。 */
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

    /** 从当前搜索输入启动在线查询，结果由专用协调器更新。 */
    private void searchOnlineTracks() {
        onlineTasks.search();
    }

    /** 显示选中在线歌曲的歌词与封面，先使旧歌词请求失效。 */
    private void previewOnlineTrack(OnlineTrackInfo info) {
        if (info == null) return;
        long reqId = ++onlinePreviewRequestId;
        previewingOnlineResult = true;
        if (lyricsController != null) lyricsController.invalidate();
        cancelLyricRetry();
        if (playbackContextLabel != null) playbackContextLabel.setText("在线预览");
        titleLabel.setText(info.title());
        artistLabel.setText(info.subtitle());
        showArtwork(info.artworkUrl());
        showLyrics(Lyrics.empty("正在加载在线预览歌词..."));
        statusLabel.setText("预览：" + info.title() + "（双击下载到本地播放）");
        onlineMusicSearchService
                .preview(info)
                .result()
                .whenComplete(
                        (r, err) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing
                                                    || reqId != onlinePreviewRequestId
                                                    || onlineResultsView
                                                                    .getSelectionModel()
                                                                    .getSelectedItem()
                                                            != info) return;
                                            previewingOnlineResult = true;
                                            if (err != null) {
                                                showLyrics(Lyrics.empty("在线预览加载失败"));
                                                return;
                                            }
                                            showArtwork(
                                                    r.artworkUrl() == null
                                                                    || r.artworkUrl().isBlank()
                                                            ? info.artworkUrl()
                                                            : r.artworkUrl());
                                            showLyrics(r.lyrics());
                                            statusLabel.setText("预览：" + info.title());
                                        }));
    }

    // ========== 歌单管理 ==========

    /** 删除选中歌曲记录并刷新选择和播放状态。 */
    private void removeSelectedTrack() {
        Track sel =
                playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        if (sel == null) {
            statusLabel.setText("请先选择要移除的歌曲");
            return;
        }
        CompletableFuture.runAsync(() -> database.removeTrack(sel), libraryExecutor)
                .whenComplete(
                        (ignored, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing) return;
                                            if (error != null) {
                                                statusLabel.setText("移除失败：" + error.getMessage());
                                                return;
                                            }
                                            int ri = tracks.indexOf(sel);
                                            boolean removingCurrent = sel == currentTrack;
                                            tracks.remove(sel);
                                            playbackOrder.removeIf(t -> t == sel);
                                            sortTracks();
                                            if (removingCurrent) {
                                                cancelLyricRetry();
                                                disposePlayer();
                                                currentTrack = null;
                                                previewingOnlineResult = false;
                                                if (playbackContextLabel != null)
                                                    playbackContextLabel.setText("未播放");
                                                playlistPane.setCurrentTrack(null);
                                                playbackControls.setTrackInfo(null, null);
                                                playbackControls.setPlaying(false);
                                                titleLabel.setText("未播放歌曲");
                                                artistLabel.setText("当前歌曲已从歌单和缓存移除");
                                                timeLabel.setText("00:00 / 00:00");
                                                showArtwork(null);
                                                showLyrics(Lyrics.empty("当前歌曲已移除"));
                                                if (!tracks.isEmpty()) {
                                                    int ni = Math.min(ri, tracks.size() - 1);
                                                    Track nt = tracks.get(ni);
                                                    if (filteredTracks.contains(nt))
                                                        playlistView.getSelectionModel().select(nt);
                                                    else if (!filteredTracks.isEmpty())
                                                        playlistView.getSelectionModel().select(0);
                                                }
                                            } else if (!filteredTracks.isEmpty())
                                                playlistView
                                                        .getSelectionModel()
                                                        .select(
                                                                Math.min(
                                                                        ri,
                                                                        filteredTracks.size() - 1));
                                            statusLabel.setText("已移除：" + sel);
                                        }));
    }

    private PlaybackFileResolver.Resolution playbackResolution;

    /** 使旧播放及歌词请求失效，释放当前播放器和对应文件句柄。 */
    private void disposePlayer() {
        if (lyricsController != null) lyricsController.invalidate();
        playbackRequestId++;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        if (playbackResolution != null) {
            playbackResolution.close();
            playbackResolution = null;
        }
    }
}
