package app.musicplayer;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
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
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.prefs.Preferences;

public final class MusicPlayerApp extends Application {
    private static final String TEXT_PREV = new String(new char[]{19978, 19968, 39318});
    private static final String TEXT_PLAY = new String(new char[]{25773, 25918});
    private static final String TEXT_PAUSE = new String(new char[]{26242, 20572});
    private static final String TEXT_NEXT = new String(new char[]{19979, 19968, 39318});
    private static final String TEXT_VOLUME = new String(new char[]{38899, 37327});
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff");
    private static final int[] LYRIC_RETRY_DELAYS_SECONDS = {10, 20, 30, 60};
    private static final Path APP_BASE_DIR = resolveAppBaseDir();
    private static final Path APP_DATA_DIR = APP_BASE_DIR.resolve("downloads").toAbsolutePath().normalize();
    private static final Path CACHE_DIR = APP_DATA_DIR.resolve("cache");
    private static final Path LYRICS_CACHE_DIR = CACHE_DIR.resolve("lyrics");
    private static final Path ARTWORK_CACHE_DIR = CACHE_DIR.resolve("artwork");
    private static final Path PLAYBACK_CACHE_DIR = CACHE_DIR.resolve("playback");
    private static final Path DOWNLOAD_DIR = APP_DATA_DIR;
    private static final Path DATABASE_PATH = APP_DATA_DIR.resolve("music-player.db");
    private static final String PREF_SORT_TYPE = "playlist.sort.type";
    private static final String PREF_SORT_ORDER = "playlist.sort.order";
    private static final String PREF_ONLINE_PANEL_EXPANDED = "ui.online.panel.expanded";
    private static final String PREF_DIVIDER_LEFT = "ui.split.divider.left";
    private static final String PREF_DIVIDER_RIGHT = "ui.split.divider.right";
    private static final double DEFAULT_LEFT_DIVIDER = 0.24;
    private static final double DEFAULT_RIGHT_DIVIDER = 0.78;
    private static final double COLLAPSED_ONLINE_PANEL_WIDTH = 66;
    private static final double EXPANDED_ONLINE_PANEL_MIN_WIDTH = 320;
    private static final double EXPANDED_ONLINE_PANEL_PREF_WIDTH = 360;
    private static final double EXPANDED_ONLINE_PANEL_MAX_WIDTH = 520;
    private static final double DEFAULT_LYRICS_FONT_SIZE = 17.0;
    private static final double MIN_LYRICS_FONT_SIZE = 13.0;
    private static final double MAX_LYRICS_FONT_SIZE = 30.0;

    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    private final FilteredList<Track> filteredTracks = new FilteredList<>(tracks, track -> true);
    private final ObservableList<String> lyricRows = FXCollections.observableArrayList();
    private final ObservableList<OnlineTrackInfo> onlineResults = FXCollections.observableArrayList();
    private final Random random = new Random();
    private final HttpClient artworkHttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private final Map<String, Long> creationTimeCache = new HashMap<>();
    private final Preferences preferences = Preferences.userNodeForPackage(MusicPlayerApp.class);

    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lyrics-retry"); t.setDaemon(true); return t;
    });

    private MusicDatabase database;
    private LyricsService lyricsService;
    private OnlineMusicSearchService onlineMusicSearchService;
    private SplitPane mainSplitPane;
    private VBox playlistSidebar;
    private ListView<Track> playlistView;
    private ListView<String> lyricsView;
    private ListView<OnlineTrackInfo> onlineResultsView;
    private TextField searchField;
    private TextField onlineSearchField;
    private ComboBox<String> sortTypeBox;
    private ComboBox<String> sortOrderBox;
    private VBox onlinePanelContent;
    private HBox onlinePanelContainer;
    private Button onlinePanelToggleButton;
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
    private boolean onlinePanelExpanded;
    private boolean seeking;
    private boolean previewingOnlineResult;
    private long lyricsRequestId;
    private long onlineSearchRequestId;
    private long onlinePreviewRequestId;
    private ScheduledFuture<?> lyricRetryTask;
    private int lyricRetryAttempt;
    private boolean syncingOnlinePanelState;
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
            restoreMainSplitPaneState();
            applyResponsiveLayout(scene.getWidth());
        });
        restoreSavedTracks();
    }

    private SplitPane createResizableContent() {
        createPlaylist();
        StackPane nowPlaying = createNowPlaying();
        HBox onlinePanel = createOnlineSearchPanel();

        playlistSidebar.setMinWidth(240);
        playlistSidebar.setPrefWidth(320);
        nowPlaying.setMinWidth(420);
        onlinePanel.setMinWidth(58);
        onlinePanel.setPrefWidth(360);

        mainSplitPane = new SplitPane(playlistSidebar, nowPlaying, onlinePanel);
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.getStyleClass().add("main-split-pane");
        mainSplitPane.setDividerPositions(DEFAULT_LEFT_DIVIDER, DEFAULT_RIGHT_DIVIDER);

        SplitPane.setResizableWithParent(playlistSidebar, true);
        SplitPane.setResizableWithParent(nowPlaying, true);
        SplitPane.setResizableWithParent(onlinePanel, true);
        bindSplitPanePersistence();
        return mainSplitPane;
    }

    @Override
    public void stop() {
        cancelLyricRetry();
        retryExecutor.shutdownNow();
        if (mediaPlayer != null) { mediaPlayer.dispose(); }
        if (database != null) { database.close(); }
    }

    private void initializeServices() {
        ensureAppDataDirectory();
        migrateLegacyDatabaseIfNeeded();
        try { database = new MusicDatabase(DATABASE_PATH); }
        catch (SQLException e) { throw new IllegalStateException("无法初始化本地数据库", e); }
        lyricsService = new LyricsService(database, LYRICS_CACHE_DIR);
        onlineMusicSearchService = new OnlineMusicSearchService();
    }

    private void ensureAppDataDirectory() {
        try {
            Files.createDirectories(APP_DATA_DIR);
            Files.createDirectories(CACHE_DIR);
            Files.createDirectories(LYRICS_CACHE_DIR);
            Files.createDirectories(ARTWORK_CACHE_DIR);
            Files.createDirectories(PLAYBACK_CACHE_DIR);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 downloads 文件夹: " + APP_DATA_DIR, e);
        }
    }

    private void migrateLegacyDatabaseIfNeeded() {
        Path legacyDatabasePath = Path.of("music-player.db").toAbsolutePath().normalize();
        if (legacyDatabasePath.equals(DATABASE_PATH)) return;
        if (!Files.exists(legacyDatabasePath) || Files.exists(DATABASE_PATH)) return;
        try {
            Files.move(legacyDatabasePath, DATABASE_PATH, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException moveError) {
            try {
                Files.copy(legacyDatabasePath, DATABASE_PATH, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException copyError) {
                throw new IllegalStateException("无法迁移数据库到 downloads 文件夹: " + DATABASE_PATH, copyError);
            }
        }
    }

    private static Path resolveAppBaseDir() {
        try {
            var codeSource = MusicPlayerApp.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                if (Files.isRegularFile(location)) {
                    Path parent = location.getParent();
                    if (parent != null) return parent;
                }
            }
        } catch (Exception ignored) {
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
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

    private VBox createPlaylist() {
        Label header = new Label("播放列表"); header.getStyleClass().add("section-title");
        searchField = new TextField(); searchField.setPromptText("搜索歌曲、歌手或文件名");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((o, ov, nv) -> applyTrackFilter(nv));

        sortTypeBox = new ComboBox<>();
        sortTypeBox.getItems().setAll("按名称排序", "按歌手排序", "按文件名排序", "按创建日期排序");
        sortTypeBox.getSelectionModel().select(preferences.get(PREF_SORT_TYPE, "按名称排序"));
        if (sortTypeBox.getSelectionModel().getSelectedIndex() < 0) sortTypeBox.getSelectionModel().selectFirst();
        sortTypeBox.setMaxWidth(Double.MAX_VALUE);
        sortTypeBox.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null) preferences.put(PREF_SORT_TYPE, nv);
            sortTracks();
        });

        sortOrderBox = new ComboBox<>();
        sortOrderBox.getItems().setAll("正序", "倒序");
        sortOrderBox.getSelectionModel().select(preferences.get(PREF_SORT_ORDER, "正序"));
        if (sortOrderBox.getSelectionModel().getSelectedIndex() < 0) sortOrderBox.getSelectionModel().selectFirst();
        sortOrderBox.setMaxWidth(Double.MAX_VALUE);
        sortOrderBox.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null) preferences.put(PREF_SORT_ORDER, nv);
            sortTracks();
        });

        HBox sortRow = new HBox(8, sortTypeBox, sortOrderBox);
        sortRow.getStyleClass().add("sort-row");
        HBox.setHgrow(sortTypeBox, Priority.ALWAYS);
        HBox.setHgrow(sortOrderBox, Priority.ALWAYS);

        playlistView = new ListView<>(filteredTracks);
        playlistView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        playlistView.setCellFactory(v -> new ListCell<>() {
            protected void updateItem(Track tr, boolean empty) { super.updateItem(tr, empty); setText(empty || tr == null ? null : tr.toString()); }
        });
        playlistView.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) { Track sel = playlistView.getSelectionModel().getSelectedItem(); if (sel != null) playTrack(sel); }
        });

        playlistSidebar = new VBox(10, header, searchField, sortRow, playlistView);
        playlistSidebar.getStyleClass().add("sidebar");
        playlistSidebar.setPadding(new Insets(8, 0, 18, 22));
        playlistSidebar.setMinWidth(240);
        playlistSidebar.setPrefWidth(320);
        VBox.setVgrow(playlistView, Priority.ALWAYS);
        return playlistSidebar;
    }

    private HBox createOnlineSearchPanel() {
        onlinePanelExpanded = preferences.getBoolean(PREF_ONLINE_PANEL_EXPANDED, false);
        Label header = new Label("在线下载"); header.getStyleClass().add("section-title");
        onlineSearchField = new TextField(); onlineSearchField.setPromptText("搜索歌曲名 / 歌手名");
        onlineSearchField.getStyleClass().add("search-field");
        onlineSearchField.setOnAction(e -> searchOnlineTracks());

        Button searchButton = new Button("搜索"); searchButton.getStyleClass().add("primary-button");
        searchButton.setOnAction(e -> searchOnlineTracks());

        loadingOnlineSearch = new ProgressIndicator(); loadingOnlineSearch.setMaxSize(18, 18);
        loadingOnlineSearch.setVisible(false); loadingOnlineSearch.setManaged(false);

        HBox searchRow = new HBox(8, onlineSearchField, searchButton, loadingOnlineSearch);
        searchRow.getStyleClass().add("search-row");
        searchRow.setMinWidth(0);
        HBox.setHgrow(onlineSearchField, Priority.ALWAYS);

        onlineResultsView = new ListView<>(onlineResults);
        onlineResultsView.getStyleClass().add("online-results-view");
        onlineResultsView.setPlaceholder(new Label("搜索 QQMP3 / 网易云 / QQ / 酷狗，双击下载到本地"));
        onlineResultsView.setCellFactory(v -> new ListCell<>() {
            protected void updateItem(OnlineTrackInfo item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("result-downloadable", "result-tryable", "result-unavailable");
                if (empty || item == null) { setText(null); return; }
                setText(item.title() + "\n" + item.subtitle()); setWrapText(true);
                if (item.downloadable()) getStyleClass().add("result-downloadable");
                else if ("可尝试下载".equals(item.availabilityText())) getStyleClass().add("result-tryable");
                else getStyleClass().add("result-unavailable");
            }
        });
        onlineResultsView.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> { if (nv != null) previewOnlineTrack(nv); });
        onlineResultsView.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) { OnlineTrackInfo sel = onlineResultsView.getSelectionModel().getSelectedItem(); if (sel != null) downloadAndPlayOnlineTrack(sel); }
        });

        Label hint = new Label("单击预览 · 双击下载到本地播放");
        hint.getStyleClass().add("muted-label");

        onlinePanelContent = new VBox(10, header, searchRow, hint, onlineResultsView);
        onlinePanelContent.getStyleClass().add("online-panel-content");
        onlinePanelContent.setPadding(new Insets(12, 16, 16, 12));
        onlinePanelContent.setFillWidth(true);
        HBox.setHgrow(onlinePanelContent, Priority.ALWAYS);
        VBox.setVgrow(onlineResultsView, Priority.ALWAYS);

        onlinePanelToggleButton = new Button();
        onlinePanelToggleButton.getStyleClass().add("drawer-toggle-button");
        onlinePanelToggleButton.setWrapText(true);
        onlinePanelToggleButton.setFocusTraversable(false);
        onlinePanelToggleButton.setOnAction(e -> toggleOnlinePanel());

        onlinePanelContainer = new HBox(onlinePanelToggleButton, onlinePanelContent);
        onlinePanelContainer.getStyleClass().add("online-panel");
        onlinePanelContainer.setAlignment(Pos.CENTER_LEFT);
        onlinePanelContainer.setMinWidth(COLLAPSED_ONLINE_PANEL_WIDTH);
        onlinePanelContainer.setPrefWidth(EXPANDED_ONLINE_PANEL_PREF_WIDTH);
        onlinePanelContainer.setMaxWidth(EXPANDED_ONLINE_PANEL_MAX_WIDTH);
        applyOnlinePanelState(false);
        return onlinePanelContainer;
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

    private VBox createControls() {
        Button prevBtn = new Button(TEXT_PREV);
        prevBtn.getStyleClass().add("control-button");
        prevBtn.setOnAction(e -> previousTrack());

        playPauseButton = new Button(TEXT_PLAY);
        playPauseButton.getStyleClass().addAll("primary-button", "control-button", "play-button");
        playPauseButton.setOnAction(e -> togglePlayPause());

        Button nextBtn = new Button(TEXT_NEXT);
        nextBtn.getStyleClass().add("control-button");
        nextBtn.setOnAction(e -> nextTrack(true));

        progressSlider = new Slider(0, 1, 0);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        progressSlider.setMinHeight(28);
        progressSlider.setPrefHeight(28);
        progressSlider.setMaxHeight(28);
        progressSlider.valueChangingProperty().addListener((o, was, is) -> { seeking = is; if (!is) seekToProgress(); });
        progressSlider.setOnMouseReleased(e -> seekToProgress());

        timeLabel = new Label("00:00 / 00:00");
        timeLabel.getStyleClass().add("time-label");
        timeLabel.setMinWidth(112);
        timeLabel.setMinHeight(28);
        timeLabel.setPrefHeight(28);
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        Label volLbl = new Label(TEXT_VOLUME);
        volLbl.getStyleClass().add("muted-label");
        volumeSlider = new Slider(0, 1, 0.75);
        volumeSlider.getStyleClass().add("volume-slider");
        volumeSlider.setMinWidth(120);
        volumeSlider.setPrefWidth(140);
        volumeSlider.setMaxWidth(140);
        volumeSlider.setMinSize(120, Region.USE_PREF_SIZE);
        volumeSlider.setPrefSize(140, Region.USE_PREF_SIZE);
        volumeSlider.setMaxSize(140, Region.USE_PREF_SIZE);
        HBox.setHgrow(volumeSlider, Priority.NEVER);
        volumeSlider.valueProperty().addListener((o, ov, nv) -> { if (mediaPlayer != null) mediaPlayer.setVolume(nv.doubleValue()); });

        HBox transportGroup = new HBox(10, prevBtn, playPauseButton, nextBtn);
        transportGroup.getStyleClass().add("transport-group");
        transportGroup.setAlignment(Pos.CENTER_LEFT);

        StackPane volumeSliderHolder = new StackPane(volumeSlider);
        volumeSliderHolder.getStyleClass().add("volume-slider-holder");
        volumeSliderHolder.setMinWidth(150);
        volumeSliderHolder.setPrefWidth(150);
        volumeSliderHolder.setMaxWidth(150);
        volumeSliderHolder.setMinSize(150, 42);
        volumeSliderHolder.setPrefSize(150, 42);
        volumeSliderHolder.setMaxSize(150, 42);
        Rectangle volumeClip = new Rectangle();
        volumeClip.widthProperty().bind(volumeSliderHolder.widthProperty());
        volumeClip.heightProperty().bind(volumeSliderHolder.heightProperty());
        volumeSliderHolder.setClip(volumeClip);
        HBox.setHgrow(volumeSliderHolder, Priority.NEVER);

        HBox volumeGroup = new HBox(8, volLbl, volumeSliderHolder);
        volumeGroup.getStyleClass().add("volume-group");
        volumeGroup.setAlignment(Pos.CENTER_LEFT);
        volumeGroup.setMinWidth(198);
        volumeGroup.setPrefWidth(210);
        volumeGroup.setMaxWidth(220);
        HBox.setHgrow(volumeGroup, Priority.NEVER);

        HBox btns = new HBox(14, transportGroup, new Separator(Orientation.VERTICAL), volumeGroup);
        btns.getStyleClass().add("control-row");
        btns.setAlignment(Pos.CENTER_LEFT);
        btns.setMinHeight(56);
        btns.setPrefHeight(56);
        btns.setMaxHeight(56);

        HBox progressRow = new HBox(12, progressSlider, timeLabel);
        progressRow.getStyleClass().add("progress-row");
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        VBox controls = new VBox(8, progressRow, btns);
        controls.getStyleClass().add("controls");
        controls.setPadding(new Insets(10, 22, 14, 22));
        controls.setMinHeight(118);
        controls.setPrefHeight(124);
        controls.setMaxHeight(132);

        playPauseButton.disableProperty().bind(Bindings.isEmpty(tracks));
        prevBtn.disableProperty().bind(Bindings.isEmpty(tracks));
        nextBtn.disableProperty().bind(Bindings.isEmpty(tracks));
        return controls;
    }

    private void importFolder(Stage stage) {
        DirectoryChooser c = new DirectoryChooser(); c.setTitle("选择歌曲文件夹");
        Path dp = Path.of(System.getProperty("user.home"), "Music");
        if (Files.isDirectory(dp)) c.setInitialDirectory(dp.toFile());
        var dir = c.showDialog(stage); if (dir == null) return;
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            addImportedTracks(s.filter(Files::isRegularFile).filter(this::isSupportedAudio).sorted().map(Track::new).toList(), "文件夹");
        } catch (IOException e) { statusLabel.setText("导入失败：" + e.getMessage()); }
    }

    private void importFiles(Stage stage) {
        FileChooser c = new FileChooser(); c.setTitle("选择音频文件");
        c.getExtensionFilters().add(new FileChooser.ExtensionFilter("音频文件", "*.mp3", "*.m4a", "*.aac", "*.wav", "*.aif", "*.aiff"));
        List<java.io.File> files = c.showOpenMultipleDialog(stage); if (files == null || files.isEmpty()) return;
        addImportedTracks(files.stream().map(java.io.File::toPath).filter(Files::isRegularFile).filter(this::isSupportedAudio).map(Track::new).toList(), "音频文件");
    }

    private void addImportedTracks(List<Track> imported, String source) {
        if (imported == null || imported.isEmpty()) { statusLabel.setText("没有找到可导入的" + source); return; }
        Set<String> existing = new HashSet<>();
        for (Track t : tracks) existing.add(t.path().toAbsolutePath().normalize().toString());
        List<Track> added = new ArrayList<>(); int dup = 0;
        for (Track t : imported) {
            if (existing.add(t.path().toAbsolutePath().normalize().toString())) added.add(t); else dup++;
        }
        if (added.isEmpty()) { statusLabel.setText("全部 " + imported.size() + " 首都已在歌单中"); return; }
        tracks.addAll(added); database.saveTracks(added);
        sortTracks();
        applyTrackFilter(searchField == null ? "" : searchField.getText());
        playlistView.getSelectionModel().select(added.get(0));
        statusLabel.setText("已新增 " + added.size() + " 首" + (dup > 0 ? "，忽略重复 " + dup + " 首" : ""));
        if (currentTrack == null) playTrack(added.get(0));
    }

    private void restoreSavedTracks() {
        List<Track> saved = database.loadTracks().stream().filter(t -> Files.isRegularFile(t.path())).filter(t -> isSupportedAudio(t.path())).toList();
        if (!saved.isEmpty()) { tracks.setAll(saved); sortTracks(); applyTrackFilter(""); playlistView.getSelectionModel().select(0); statusLabel.setText("已恢复 " + saved.size() + " 首歌曲"); }
    }

    private void applyTrackFilter(String q) {
        String n = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        filteredTracks.setPredicate(tr -> {
            if (n.isBlank()) return true;
            String fn = tr.path().getFileName().toString().toLowerCase(Locale.ROOT);
            return tr.title().toLowerCase(Locale.ROOT).contains(n) || tr.artist().toLowerCase(Locale.ROOT).contains(n) || fn.contains(n);
        });
        if (currentTrack != null && filteredTracks.contains(currentTrack)) playlistView.getSelectionModel().select(currentTrack);
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0);
    }

    private boolean isSupportedAudio(Path p) { String fn = p.getFileName().toString(); int dot = fn.lastIndexOf('.'); return dot >= 0 && SUPPORTED_EXTENSIONS.contains(fn.substring(dot + 1).toLowerCase(Locale.ROOT)); }

    private void playTrack(int idx) { if (idx >= 0 && idx < tracks.size()) playTrack(tracks.get(idx)); }

    private void playTrack(Track track) {
        int idx = tracks.indexOf(track); if (idx < 0) return;
        disposePlayer(); previewingOnlineResult = false; currentTrack = track;
        if (filteredTracks.contains(track)) { playlistView.getSelectionModel().select(track); playlistView.scrollTo(track); }
        titleLabel.setText(currentTrack.title()); artistLabel.setText(currentTrack.artist());
        progressSlider.setValue(0); timeLabel.setText("00:00 / 00:00");
        showArtwork(null); showLyrics(Lyrics.empty("正在准备歌词..."));
        Path playbackPath = resolvePlayableMediaPath(currentTrack.path());
        if (looksLikeUnsupportedRawAac(playbackPath)) {
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
            Set<String> existing = new HashSet<>();
            for (Track t : tracks) existing.add(t.path().toAbsolutePath().normalize().toString());
            if (!existing.add(downloadedPath.toAbsolutePath().normalize().toString())) {
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
        if (t != null && t.greaterThan(Duration.ZERO) && !seeking) progressSlider.setValue(ct.toMillis() / t.toMillis());
        updateDurationLabel(); updateHighlightedLyric(ct);
    }

    private void updateDurationLabel() {
        if (mediaPlayer == null) { timeLabel.setText("00:00 / 00:00"); return; }
        Duration c = mediaPlayer.getCurrentTime(), t = mediaPlayer.getTotalDuration();
        timeLabel.setText(formatTime(c) + " / " + formatTime(t));
    }

    private static String formatTime(Duration d) { if (d == null || d.isUnknown() || d.lessThan(Duration.ZERO)) return "00:00"; long s = Math.round(d.toSeconds()); return "%02d:%02d".formatted(s / 60, s % 60); }

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
            if (err != null) { showLyrics(Lyrics.empty("歌词加载失败")); return; }
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
        LyricsLookupResult r = lyricsService.searchOnlineAsync(track, dur).join();
        Platform.runLater(() -> {
            if (reqId != lyricsRequestId || track != currentTrack) return;
            if (isMissingLyrics(r.lyrics())) scheduleLyricRetry(track, dur, reqId);
            else { loadingLyrics.setVisible(false); loadingLyrics.setManaged(false); showLyrics(r.lyrics()); showArtwork(r.artworkUrl()); }
        });
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
        String sortMode = sortTypeBox == null ? "按名称排序" : sortTypeBox.getValue();
        Comparator<Track> comparator = switch (sortMode) {
            case "按歌手排序" -> Comparator.comparing(this::trackArtistKey).thenComparing(this::trackNameKey);
            case "按文件名排序" -> Comparator.comparing(this::trackFileNameKey).thenComparing(this::trackNameKey);
            case "按创建日期排序" -> Comparator.comparingLong(this::trackCreatedTimeMillis).thenComparing(this::trackNameKey);
            default -> Comparator.comparing(this::trackNameKey).thenComparing(this::trackArtistKey);
        };
        if (isSortDescending()) comparator = comparator.reversed();
        Track selected = playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        tracks.sort(comparator);
        if (selected != null && filteredTracks.contains(selected)) {
            playlistView.getSelectionModel().select(selected);
            playlistView.scrollTo(selected);
        }
    }

    private boolean isSortDescending() { return sortOrderBox != null && Objects.equals(sortOrderBox.getValue(), "倒序"); }

    private void toggleOnlinePanel() {
        if (onlinePanelExpanded && mainSplitPane != null && mainSplitPane.getDividers().size() >= 2) {
            double[] positions = mainSplitPane.getDividerPositions();
            preferences.putDouble(PREF_DIVIDER_LEFT, positions[0]);
            preferences.putDouble(PREF_DIVIDER_RIGHT, positions[1]);
        }
        onlinePanelExpanded = !onlinePanelExpanded;
        animateOnlinePanelState();
    }

    private void applyOnlinePanelState(boolean persist) {
        if (persist) preferences.putBoolean(PREF_ONLINE_PANEL_EXPANDED, onlinePanelExpanded);
        if (onlinePanelContent != null) {
            onlinePanelContent.setVisible(onlinePanelExpanded);
            onlinePanelContent.setManaged(onlinePanelExpanded);
            onlinePanelContent.setPrefWidth(onlinePanelExpanded ? EXPANDED_ONLINE_PANEL_PREF_WIDTH - COLLAPSED_ONLINE_PANEL_WIDTH : 0);
            onlinePanelContent.setMinWidth(onlinePanelExpanded ? 240 : 0);
            onlinePanelContent.setMaxWidth(onlinePanelExpanded ? Double.MAX_VALUE : 0);
            onlinePanelContent.setOpacity(onlinePanelExpanded ? 1.0 : 0.0);
        }
        if (onlinePanelContainer != null) {
            double minWidth = onlinePanelExpanded ? EXPANDED_ONLINE_PANEL_MIN_WIDTH : COLLAPSED_ONLINE_PANEL_WIDTH;
            double prefWidth = onlinePanelExpanded ? EXPANDED_ONLINE_PANEL_PREF_WIDTH : COLLAPSED_ONLINE_PANEL_WIDTH;
            double maxWidth = onlinePanelExpanded ? Double.MAX_VALUE : COLLAPSED_ONLINE_PANEL_WIDTH;
            onlinePanelContainer.setMinWidth(minWidth);
            onlinePanelContainer.setPrefWidth(prefWidth);
            onlinePanelContainer.setMaxWidth(maxWidth);
            onlinePanelContainer.getStyleClass().removeAll("online-panel-expanded", "online-panel-collapsed");
            onlinePanelContainer.getStyleClass().add(onlinePanelExpanded ? "online-panel-expanded" : "online-panel-collapsed");
        }
        updateOnlineToggleButton();
        syncOnlinePanelDivider();
    }

    private void animateOnlinePanelState() {
        preferences.putBoolean(PREF_ONLINE_PANEL_EXPANDED, onlinePanelExpanded);
        if (onlinePanelContent == null) {
            applyOnlinePanelState(false);
            return;
        }
        ScaleTransition pulse = new ScaleTransition(Duration.millis(180), onlinePanelToggleButton);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);

        if (onlinePanelExpanded) {
            if (onlinePanelContent != null) {
                onlinePanelContent.setManaged(true);
                onlinePanelContent.setVisible(true);
                onlinePanelContent.setOpacity(0.0);
                onlinePanelContent.setTranslateX(24);
            }
            applyOnlinePanelState(false);
            FadeTransition fade = new FadeTransition(Duration.millis(180), onlinePanelContent);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            TranslateTransition slide = new TranslateTransition(Duration.millis(220), onlinePanelContent);
            slide.setFromX(24);
            slide.setToX(0);
            new ParallelTransition(fade, slide, pulse).play();
        } else {
            updateOnlineToggleButton();
            FadeTransition fade = new FadeTransition(Duration.millis(150), onlinePanelContent);
            fade.setFromValue(onlinePanelContent.getOpacity());
            fade.setToValue(0.0);
            TranslateTransition slide = new TranslateTransition(Duration.millis(180), onlinePanelContent);
            slide.setFromX(0);
            slide.setToX(24);
            ParallelTransition animation = new ParallelTransition(fade, slide, pulse);
            animation.setOnFinished(e -> applyOnlinePanelState(false));
            animation.play();
        }
    }

    private void updateOnlineToggleButton() {
        if (onlinePanelToggleButton == null) return;
        onlinePanelToggleButton.setText(onlinePanelExpanded ? "‹\n收起\n搜索" : "☰\n在线\n搜索\n›");
        onlinePanelToggleButton.setTooltip(new javafx.scene.control.Tooltip(onlinePanelExpanded ? "收起在线搜索抽屉" : "展开在线搜索抽屉"));
        onlinePanelToggleButton.getStyleClass().removeAll("drawer-expanded", "drawer-collapsed");
        onlinePanelToggleButton.getStyleClass().add(onlinePanelExpanded ? "drawer-expanded" : "drawer-collapsed");
    }

    private void bindSplitPanePersistence() {
        if (mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        mainSplitPane.getDividers().get(0).positionProperty().addListener((o, ov, nv) -> persistMainSplitPaneState());
        mainSplitPane.getDividers().get(1).positionProperty().addListener((o, ov, nv) -> persistMainSplitPaneState());
    }

    private void restoreMainSplitPaneState() {
        if (mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        double left = clamp(preferences.getDouble(PREF_DIVIDER_LEFT, DEFAULT_LEFT_DIVIDER), 0.16, 0.52);
        double right = onlinePanelExpanded
                ? expandedDividerPosition(currentWindowWidth(), left)
                : collapsedDividerPosition(currentWindowWidth(), left);
        setMainSplitPanePositions(left, right);
        applyOnlinePanelState(false);
    }

    private void persistMainSplitPaneState() {
        if (syncingOnlinePanelState || mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        double[] positions = mainSplitPane.getDividerPositions();
        double left = clamp(positions[0], 0.16, 0.52);
        preferences.putDouble(PREF_DIVIDER_LEFT, left);
        if (onlinePanelExpanded) {
            double right = clamp(positions[1], left + 0.18, 0.90);
            preferences.putDouble(PREF_DIVIDER_RIGHT, right);
        }
    }

    private void syncOnlinePanelDivider() {
        if (mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        double left = clamp(mainSplitPane.getDividerPositions()[0], 0.16, 0.52);
        double targetRight = onlinePanelExpanded
                ? expandedDividerPosition(currentWindowWidth(), left)
                : collapsedDividerPosition(currentWindowWidth(), left);
        double currentRight = mainSplitPane.getDividerPositions()[1];
        if (Math.abs(currentRight - targetRight) > 0.002 || Math.abs(mainSplitPane.getDividerPositions()[0] - left) > 0.002) {
            setMainSplitPanePositions(left, targetRight);
        }
    }

    private void setMainSplitPanePositions(double left, double right) {
        if (mainSplitPane == null) return;
        syncingOnlinePanelState = true;
        mainSplitPane.setDividerPositions(left, right);
        Platform.runLater(() -> {
            if (mainSplitPane != null) {
                mainSplitPane.setDividerPositions(left, right);
            }
            syncingOnlinePanelState = false;
        });
    }

    private double expandedDividerPosition(double windowWidth, double leftDivider) {
        double width = Math.max(1120, windowWidth);
        double preferredRight = preferences.getDouble(PREF_DIVIDER_RIGHT, DEFAULT_RIGHT_DIVIDER);
        double maxRightForReadableSidebar = 1.0 - (EXPANDED_ONLINE_PANEL_PREF_WIDTH / width);
        return clamp(Math.min(preferredRight, maxRightForReadableSidebar), leftDivider + 0.18, maxRightForReadableSidebar);
    }

    private double collapsedDividerPosition(double windowWidth, double leftDivider) {
        double width = Math.max(1120, windowWidth);
        double collapsedFraction = COLLAPSED_ONLINE_PANEL_WIDTH / width;
        return clamp(1.0 - collapsedFraction, leftDivider + 0.18, 0.97);
    }

    private void applyResponsiveLayout(double windowWidth) {
        if (statusLabel != null) {
            statusLabel.setMaxWidth(clamp(windowWidth * 0.24, 220, 420));
        }
        if (!onlinePanelExpanded) {
            syncOnlinePanelDivider();
        }
    }

    private double currentWindowWidth() {
        if (playPauseButton != null && playPauseButton.getScene() != null) {
            return playPauseButton.getScene().getWidth();
        }
        return 1320;
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private String trackNameKey(Track track) {
        String title = track == null || track.title() == null ? "" : track.title().trim().toLowerCase(Locale.ROOT);
        String artist = track == null || track.artist() == null ? "" : track.artist().trim().toLowerCase(Locale.ROOT);
        return title + "\u0000" + artist;
    }

    private String trackArtistKey(Track track) {
        String artist = track == null || track.artist() == null ? "" : track.artist().trim().toLowerCase(Locale.ROOT);
        return artist + "\u0000" + trackNameKey(track);
    }

    private String trackFileNameKey(Track track) {
        if (track == null || track.path() == null || track.path().getFileName() == null) return "";
        return track.path().getFileName().toString().trim().toLowerCase(Locale.ROOT);
    }

    private long trackCreatedTimeMillis(Track track) {
        if (track == null || track.path() == null) return Long.MIN_VALUE;
        String key = track.path().toAbsolutePath().normalize().toString();
        Long cached = creationTimeCache.get(key);
        if (cached != null) return cached;
        long value = Long.MIN_VALUE;
        try {
            BasicFileAttributes attrs = Files.readAttributes(track.path(), BasicFileAttributes.class);
            value = attrs.creationTime().toMillis();
        } catch (IOException ignored) {
            try {
                value = Files.getLastModifiedTime(track.path()).toMillis();
            } catch (IOException ignoredAgain) {
                value = Long.MIN_VALUE;
            }
        }
        creationTimeCache.put(key, value);
        return value;
    }

    private void showArtwork(String url) {
        currentArtworkSource = url;
        if (artworkImageView == null) return;
        if (url == null || url.isBlank()) {
            artworkImageView.setImage(null);
            artworkImageView.setVisible(false);
            return;
        }
        if (!isRemoteUrl(url)) {
            artworkImageView.setImage(new Image(url, true));
            artworkImageView.setVisible(true);
            return;
        }
        Path cached = artworkCachePath(url);
        if (Files.isRegularFile(cached)) {
            artworkImageView.setImage(new Image(cached.toUri().toString(), true));
            artworkImageView.setVisible(true);
            return;
        }
        artworkImageView.setImage(new Image(url, true));
        artworkImageView.setVisible(true);
        cacheArtworkAsync(url, cached);
    }

    private void cacheArtworkAsync(String url, Path target) {
        CompletableFuture.runAsync(() -> {
            try {
                if (Files.isRegularFile(target)) return;
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = artworkHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 400) return;
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                if (!contentType.startsWith("image/")) return;
                byte[] body = response.body();
                if (body == null || body.length == 0) return;
                Files.createDirectories(target.getParent());
                Files.write(target, body);
                if (Objects.equals(currentArtworkSource, url)) {
                    Platform.runLater(() -> {
                        if (Objects.equals(currentArtworkSource, url) && artworkImageView != null) {
                            artworkImageView.setImage(new Image(target.toUri().toString(), true));
                            artworkImageView.setVisible(true);
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static boolean isRemoteUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static Path artworkCachePath(String url) { return ARTWORK_CACHE_DIR.resolve(sha1(url) + artworkExtension(url)); }

    private static String artworkExtension(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    String ext = path.substring(dot).toLowerCase(Locale.ROOT);
                    if (ext.length() <= 8) return ext;
                }
            }
        } catch (Exception ignored) {
        }
        return ".img";
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
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
        if (isUnsupportedAudioError(err)) {
            message = "当前文件编码不受支持，建议换成 mp3 / m4a";
        }
        statusLabel.setText("播放失败：" + message);
        playPauseButton.setText("播放");
        showLyrics(Lyrics.empty("当前文件无法播放或编码不受支持"));
    }

    private Path resolvePlayableMediaPath(Path source) {
        if (source == null) return source;
        String detectedFormat = sniffAudioFormat(source);
        if (!"mp3".equals(detectedFormat) || !hasExtension(source, "m4a", "aac")) {
            return source;
        }
        try {
            String cacheKey = sha1(source.toAbsolutePath().normalize() + ":" + Files.size(source) + ":" + Files.getLastModifiedTime(source).toMillis());
            Path target = PLAYBACK_CACHE_DIR.resolve(cacheKey + ".mp3");
            if (!Files.isRegularFile(target) || Files.size(target) != Files.size(source)) {
                Files.createDirectories(PLAYBACK_CACHE_DIR);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            statusLabel.setText("检测到下载文件扩展名异常，已按 MP3 兼容播放");
            return target;
        } catch (IOException ignored) {
            return source;
        }
    }

    private boolean looksLikeUnsupportedRawAac(Path path) {
        return "aac".equals(sniffAudioFormat(path));
    }

    private String sniffAudioFormat(Path path) {
        if (path == null || !Files.isRegularFile(path)) return "unknown";
        try (InputStream in = Files.newInputStream(path)) {
            byte[] header = in.readNBytes(512);
            if (header.length < 4) return "unknown";
            if (containsAscii(header, "ftyp", 0, 64)) return "mp4";
            int offset = skipId3Header(header);
            if (isMp3FrameHeader(header, offset)) return "mp3";
            if (isAdtsHeader(header, offset)) return "aac";
        } catch (IOException ignored) {
        }
        return "unknown";
    }

    private int skipId3Header(byte[] header) {
        if (header.length < 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') return 0;
        int size = (header[6] & 0x7f) << 21 | (header[7] & 0x7f) << 14 | (header[8] & 0x7f) << 7 | (header[9] & 0x7f);
        return Math.min(header.length - 1, 10 + size);
    }

    private boolean isMp3FrameHeader(byte[] header, int offset) {
        if (offset + 1 >= header.length) return false;
        int b0 = header[offset] & 0xff;
        int b1 = header[offset + 1] & 0xff;
        return b0 == 0xff && (b1 & 0xe0) == 0xe0 && (b1 & 0x06) != 0;
    }

    private boolean isAdtsHeader(byte[] header, int offset) {
        if (offset + 1 >= header.length) return false;
        int b0 = header[offset] & 0xff;
        int b1 = header[offset + 1] & 0xff;
        return b0 == 0xff && (b1 & 0xf6) == 0xf0;
    }

    private boolean containsAscii(byte[] header, String token, int start, int endExclusive) {
        int end = Math.min(header.length - token.length() + 1, endExclusive);
        for (int i = Math.max(0, start); i < end; i++) {
            boolean matched = true;
            for (int j = 0; j < token.length(); j++) {
                if (header[i + j] != (byte) token.charAt(j)) {
                    matched = false;
                    break;
                }
            }
            if (matched) return true;
        }
        return false;
    }

    private boolean hasExtension(Path path, String... extensions) {
        if (path == null || path.getFileName() == null) return false;
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String extension : extensions) {
            if (fileName.endsWith("." + extension.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private boolean isUnsupportedAudioError(Throwable err) {
        if (err == null) return false;
        String msg = (err.getMessage() == null ? "" : err.getMessage()).toLowerCase(Locale.ROOT);
        return msg.contains("corrupted") || msg.contains("unsupported") || msg.contains("error_media_corrupted");
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
