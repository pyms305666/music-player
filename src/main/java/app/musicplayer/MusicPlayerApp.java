package app.musicplayer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
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
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class MusicPlayerApp extends Application {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff");

    // 歌词未找到时的后台重试间隔。前几次更积极，之后稳定到 60 秒一次。
    // 切换歌曲会取消旧任务，避免旧歌曲的歌词结果覆盖当前界面。
    private static final int[] LYRIC_RETRY_DELAYS_SECONDS = {10, 20, 30, 60};

    private final ObservableList<Track> tracks = FXCollections.observableArrayList();

    // tracks 是完整歌单，filteredTracks 是搜索框当前筛选出来的视图。
    // 这样搜索不会破坏真实播放顺序，上一首/下一首仍按完整歌单执行。
    private final FilteredList<Track> filteredTracks = new FilteredList<>(tracks, track -> true);
    private final ObservableList<String> lyricRows = FXCollections.observableArrayList();
    private final Random random = new Random();

    // 歌词重试放到单独后台线程，避免网络请求阻塞 JavaFX UI 线程。
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lyrics-retry");
        thread.setDaemon(true);
        return thread;
    });

    private MusicDatabase database;
    private LyricsService lyricsService;
    private ListView<Track> playlistView;
    private ListView<String> lyricsView;
    private TextField searchField;
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

    private MediaPlayer mediaPlayer;
    private Track currentTrack;
    private Lyrics currentLyrics = Lyrics.empty("导入歌曲后开始播放");
    private boolean seeking;
    private long lyricsRequestId;
    private ScheduledFuture<?> lyricRetryTask;
    private int lyricRetryAttempt;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        initializeServices();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(createTopBar(stage));
        root.setLeft(createPlaylist());
        root.setCenter(createNowPlaying());
        root.setBottom(createControls());

        Scene scene = new Scene(root, 1040, 680);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        // 空格键用于播放/暂停；但搜索框获得焦点时要保留正常输入空格的行为。
        // 使用事件过滤器而不是 setOnKeyPressed，能在按钮、列表等控件消费空格前先处理。
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.SPACE && !isTextInputFocused(scene)) {
                togglePlayPause();
                event.consume();
            }
        });

        stage.setTitle("简约音乐播放器");
        stage.setMinWidth(860);
        stage.setMinHeight(560);
        stage.setScene(scene);
        stage.show();

        // UI 创建完再恢复歌曲，确保播放列表控件已经存在。
        restoreSavedTracks();
    }

    @Override
    public void stop() {
        cancelLyricRetry();
        retryExecutor.shutdownNow();
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
        }
        if (database != null) {
            database.close();
        }
    }

    private void initializeServices() {
        try {
            database = new MusicDatabase(Path.of("music-player.db"));
        } catch (SQLException exception) {
            throw new IllegalStateException("无法初始化本地数据库", exception);
        }
        lyricsService = new LyricsService(database);
    }

    private static boolean isTextInputFocused(Scene scene) {
        return scene.getFocusOwner() instanceof TextField;
    }

    private HBox createTopBar(Stage stage) {
        Button importButton = new Button("导入文件夹");
        importButton.getStyleClass().add("primary-button");
        importButton.setOnAction(event -> importFolder(stage));

        playModeBox = new ComboBox<>();
        playModeBox.getItems().setAll(PlayMode.ORDER, PlayMode.SHUFFLE, PlayMode.REPEAT_ONE);
        playModeBox.getSelectionModel().select(PlayMode.ORDER);

        // 手动搜索歌词会取消当前后台重试，并立即重新走一次歌词加载流程。
        Button reloadLyricsButton = new Button("搜索歌词");
        reloadLyricsButton.setOnAction(event -> {
            if (currentTrack != null) {
                loadLyrics(currentTrack, true);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusLabel = new Label("请选择歌曲文件夹");
        statusLabel.getStyleClass().add("muted-label");

        HBox topBar = new HBox(12, importButton, playModeBox, reloadLyricsButton, spacer, statusLabel);
        topBar.getStyleClass().add("top-bar");
        topBar.setPadding(new Insets(18, 22, 12, 22));
        return topBar;
    }

    private VBox createPlaylist() {
        Label header = new Label("播放列表");
        header.getStyleClass().add("section-title");

        searchField = new TextField();
        searchField.setPromptText("搜索歌曲、歌手或文件名");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyTrackFilter(newValue));

        // ListView 绑定 filteredTracks，所以界面显示的是搜索结果；
        // 双击时拿到 Track 对象本身，再去完整 tracks 里定位播放。
        playlistView = new ListView<>(filteredTracks);
        playlistView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        playlistView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Track track, boolean empty) {
                super.updateItem(track, empty);
                setText(empty || track == null ? null : track.toString());
            }
        });
        playlistView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Track selected = playlistView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    playTrack(selected);
                }
            }
        });

        VBox sidebar = new VBox(10, header, searchField, playlistView);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(8, 0, 18, 22));
        sidebar.setPrefWidth(330);
        VBox.setVgrow(playlistView, Priority.ALWAYS);
        return sidebar;
    }

    private VBox createNowPlaying() {
        titleLabel = new Label("未播放歌曲");
        titleLabel.getStyleClass().add("track-title");
        titleLabel.setWrapText(true);

        artistLabel = new Label("导入文件夹后双击歌曲播放");
        artistLabel.getStyleClass().add("track-artist");
        artistLabel.setWrapText(true);

        sourceLabel = new Label("歌词来源：暂无");
        sourceLabel.getStyleClass().add("muted-label");

        loadingLyrics = new ProgressIndicator();
        loadingLyrics.setMaxSize(18, 18);
        loadingLyrics.setVisible(false);
        loadingLyrics.setManaged(false);

        HBox sourceRow = new HBox(8, sourceLabel, loadingLyrics);

        lyricsView = new ListView<>(lyricRows);
        lyricsView.getStyleClass().add("lyrics-view");
        lyricsView.setFocusTraversable(false);

        VBox nowPlaying = new VBox(10, titleLabel, artistLabel, sourceRow, lyricsView);
        nowPlaying.getStyleClass().add("content");
        nowPlaying.setPadding(new Insets(24, 22, 16, 22));
        VBox.setVgrow(lyricsView, Priority.ALWAYS);

        showLyrics(Lyrics.empty("导入歌曲后开始播放"));
        return nowPlaying;
    }

    private VBox createControls() {
        Button previousButton = new Button("上一首");
        previousButton.setOnAction(event -> previousTrack());

        playPauseButton = new Button("播放");
        playPauseButton.getStyleClass().add("primary-button");
        playPauseButton.setOnAction(event -> togglePlayPause());

        Button nextButton = new Button("下一首");
        nextButton.setOnAction(event -> nextTrack(true));

        progressSlider = new Slider(0, 1, 0);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        progressSlider.valueChangingProperty().addListener((observable, wasChanging, isChanging) -> {
            seeking = isChanging;
            if (!isChanging) {
                seekToProgress();
            }
        });
        progressSlider.setOnMouseReleased(event -> seekToProgress());

        timeLabel = new Label("00:00 / 00:00");
        timeLabel.getStyleClass().add("time-label");

        Label volumeLabel = new Label("音量");
        volumeLabel.getStyleClass().add("muted-label");

        volumeSlider = new Slider(0, 1, 0.75);
        volumeSlider.setPrefWidth(140);
        volumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newValue.doubleValue());
            }
        });

        HBox buttons = new HBox(10, previousButton, playPauseButton, nextButton, new Separator(Orientation.VERTICAL),
                volumeLabel, volumeSlider);
        buttons.getStyleClass().add("control-row");

        HBox progressRow = new HBox(12, progressSlider, timeLabel);
        progressRow.getStyleClass().add("progress-row");
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        VBox controls = new VBox(10, progressRow, buttons);
        controls.getStyleClass().add("controls");
        controls.setPadding(new Insets(12, 22, 22, 22));

        playPauseButton.disableProperty().bind(Bindings.isEmpty(tracks));
        previousButton.disableProperty().bind(Bindings.isEmpty(tracks));
        nextButton.disableProperty().bind(Bindings.isEmpty(tracks));

        return controls;
    }

    private void importFolder(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择歌曲文件夹");
        Path defaultPath = Path.of(System.getProperty("user.home"), "Music");
        if (Files.isDirectory(defaultPath)) {
            chooser.setInitialDirectory(defaultPath.toFile());
        }

        var directory = chooser.showDialog(stage);
        if (directory == null) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directory.toPath())) {
            // 递归扫描文件夹，导入所有受 JavaFX Media 支持的音频格式。
            List<Track> importedTracks = stream
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedAudio)
                    .sorted()
                    .map(Track::new)
                    .toList();

            tracks.setAll(importedTracks);
            // 导入结果写入数据库，下次启动时可以恢复歌单。
            database.saveTracks(importedTracks);
            applyTrackFilter(searchField == null ? "" : searchField.getText());
            statusLabel.setText(importedTracks.isEmpty()
                    ? "没有找到可播放的音频文件"
                    : "已导入 " + importedTracks.size() + " 首歌曲");

            if (!importedTracks.isEmpty()) {
                playTrack(importedTracks.get(0));
            }
        } catch (IOException exception) {
            statusLabel.setText("导入失败：" + exception.getMessage());
        }
    }

    private void restoreSavedTracks() {
        // 数据库里可能保存着已经被用户移动/删除的文件，所以恢复时再次检查文件是否存在。
        List<Track> savedTracks = database.loadTracks().stream()
                .filter(track -> Files.isRegularFile(track.path()))
                .filter(track -> isSupportedAudio(track.path()))
                .toList();

        if (!savedTracks.isEmpty()) {
            tracks.setAll(savedTracks);
            applyTrackFilter("");
            playlistView.getSelectionModel().select(0);
            statusLabel.setText("已恢复 " + savedTracks.size() + " 首歌曲");
        }
    }

    private void applyTrackFilter(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filteredTracks.setPredicate(track -> {
            if (normalized.isBlank()) {
                return true;
            }
            String fileName = track.path().getFileName().toString().toLowerCase(Locale.ROOT);
            return track.title().toLowerCase(Locale.ROOT).contains(normalized)
                    || track.artist().toLowerCase(Locale.ROOT).contains(normalized)
                    || fileName.contains(normalized);
        });

        // 搜索条件变化后，尽量保持当前歌曲仍被选中；如果当前歌曲不在结果里，就选中第一条结果。
        if (currentTrack != null && filteredTracks.contains(currentTrack)) {
            playlistView.getSelectionModel().select(currentTrack);
        } else if (!filteredTracks.isEmpty()) {
            playlistView.getSelectionModel().select(0);
        }
    }

    private boolean isSupportedAudio(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private void playTrack(int index) {
        if (index < 0 || index >= tracks.size()) {
            return;
        }
        playTrack(tracks.get(index));
    }

    private void playTrack(Track track) {
        int index = tracks.indexOf(track);
        if (index < 0) {
            return;
        }

        // 每次切歌都销毁旧 MediaPlayer。JavaFX MediaPlayer 绑定具体媒体文件，
        // 重用旧实例容易残留监听器和播放状态。
        disposePlayer();
        currentTrack = track;
        if (filteredTracks.contains(track)) {
            playlistView.getSelectionModel().select(track);
        }
        titleLabel.setText(currentTrack.title());
        artistLabel.setText(currentTrack.artist());
        progressSlider.setValue(0);
        timeLabel.setText("00:00 / 00:00");
        showLyrics(Lyrics.empty("正在准备歌词..."));

        try {
            Media media = new Media(currentTrack.path().toUri().toString());
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider.getValue());
            mediaPlayer.currentTimeProperty().addListener((observable, oldTime, newTime) -> updatePlaybackProgress(newTime));
            mediaPlayer.setOnReady(() -> {
                // Media 准备好后才能可靠读取时长和元数据。
                updateMetadata(media);
                database.saveTrack(currentTrack, javaDuration(mediaPlayer.getTotalDuration()));
                updateDurationLabel();
                loadLyrics(currentTrack, false);
                mediaPlayer.play();
            });
            mediaPlayer.setOnPlaying(() -> playPauseButton.setText("暂停"));
            mediaPlayer.setOnPaused(() -> playPauseButton.setText("播放"));
            mediaPlayer.setOnStopped(() -> playPauseButton.setText("播放"));
            mediaPlayer.setOnEndOfMedia(this::handleEndOfMedia);
            mediaPlayer.setOnError(() -> showPlayerError(mediaPlayer.getError()));
            media.setOnError(() -> showPlayerError(media.getError()));
        } catch (MediaException exception) {
            showPlayerError(exception);
        }
    }

    private void updateMetadata(Media media) {
        String title = valueAsString(media.getMetadata().get("title"));
        String artist = valueAsString(media.getMetadata().get("artist"));

        // 文件名只是初始推断；如果音频文件里带标题/歌手元数据，以元数据为准。
        currentTrack.updateMetadata(title, artist);
        titleLabel.setText(currentTrack.title());
        artistLabel.setText(currentTrack.artist());
        playlistView.refresh();
        database.saveTrack(currentTrack, mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration()));
    }

    private static String valueAsString(Object value) {
        return value instanceof String text ? text : null;
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            Track selected = playlistView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                playTrack(selected);
            } else if (!tracks.isEmpty()) {
                playTrack(0);
            }
            return;
        }

        MediaPlayer.Status status = mediaPlayer.getStatus();
        if (status == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
    }

    private void previousTrack() {
        if (tracks.isEmpty()) {
            return;
        }
        int current = currentTrackIndex();
        int previous = current <= 0 ? tracks.size() - 1 : current - 1;
        playTrack(previous);
    }

    private void nextTrack(boolean manual) {
        if (tracks.isEmpty()) {
            return;
        }

        PlayMode mode = playModeBox.getValue();
        // 自动播放结束时，单曲循环重播当前歌曲；用户手动点“下一首”时仍跳到下一首。
        if (mode == PlayMode.REPEAT_ONE && !manual) {
            replayCurrent();
            return;
        }

        int nextIndex = switch (mode) {
            case SHUFFLE -> randomIndex();
            case ORDER, REPEAT_ONE -> {
                int current = currentTrackIndex();
                int next = current + 1;
                yield next >= tracks.size() ? 0 : next;
            }
        };
        playTrack(nextIndex);
    }

    private int randomIndex() {
        if (tracks.size() <= 1) {
            return 0;
        }
        int current = currentTrackIndex();
        int next;
        do {
            next = random.nextInt(tracks.size());
        } while (next == current);
        return next;
    }

    private void handleEndOfMedia() {
        // 顺序播放到最后一首时停止，而不是回到第一首。
        // 随机播放和单曲循环则由 nextTrack(false) 继续处理。
        if (playModeBox.getValue() == PlayMode.ORDER) {
            int current = currentTrackIndex();
            if (current >= tracks.size() - 1) {
                mediaPlayer.stop();
                mediaPlayer.seek(Duration.ZERO);
                progressSlider.setValue(0);
                updatePlaybackProgress(Duration.ZERO);
                statusLabel.setText("顺序播放结束");
                return;
            }
        }
        nextTrack(false);
    }

    private int currentTrackIndex() {
        int index = currentTrack == null ? -1 : tracks.indexOf(currentTrack);
        if (index >= 0) {
            return index;
        }
        Track selected = playlistView.getSelectionModel().getSelectedItem();
        return selected == null ? -1 : tracks.indexOf(selected);
    }

    private void replayCurrent() {
        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.ZERO);
            mediaPlayer.play();
        }
    }

    private void seekToProgress() {
        if (mediaPlayer == null || mediaPlayer.getTotalDuration() == null) {
            return;
        }
        Duration total = mediaPlayer.getTotalDuration();
        if (total.greaterThan(Duration.ZERO)) {
            mediaPlayer.seek(total.multiply(progressSlider.getValue()));
        }
    }

    private void updatePlaybackProgress(Duration currentTime) {
        if (mediaPlayer == null) {
            return;
        }

        Duration total = mediaPlayer.getTotalDuration();
        if (total != null && total.greaterThan(Duration.ZERO) && !seeking) {
            progressSlider.setValue(currentTime.toMillis() / total.toMillis());
        }

        updateDurationLabel();
        updateHighlightedLyric(currentTime);
    }

    private void updateDurationLabel() {
        if (mediaPlayer == null) {
            timeLabel.setText("00:00 / 00:00");
            return;
        }

        Duration current = mediaPlayer.getCurrentTime();
        Duration total = mediaPlayer.getTotalDuration();
        timeLabel.setText(formatTime(current) + " / " + formatTime(total));
    }

    private static String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.lessThan(Duration.ZERO)) {
            return "00:00";
        }
        long totalSeconds = Math.round(duration.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return "%02d:%02d".formatted(minutes, seconds);
    }

    private void loadLyrics(Track track, boolean manual) {
        if (track == null) {
            return;
        }

        // 新一轮歌词加载开始时取消上一轮重试，并用 requestId 防止异步结果串台。
        cancelLyricRetry();
        long requestId = ++lyricsRequestId;
        loadingLyrics.setVisible(true);
        loadingLyrics.setManaged(true);
        sourceLabel.setText(manual ? "歌词来源：正在重新搜索..." : "歌词来源：正在搜索...");

        java.time.Duration duration = mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration());
        CompletableFuture<Lyrics> future = lyricsService.findLyrics(track, duration);
        future.whenComplete((lyrics, error) -> Platform.runLater(() -> {
            if (requestId != lyricsRequestId || track != currentTrack) {
                return;
            }

            loadingLyrics.setVisible(false);
            loadingLyrics.setManaged(false);

            if (error != null) {
                showLyrics(Lyrics.empty("歌词加载失败"));
                return;
            }
            showLyrics(lyrics);
            if (isMissingLyrics(lyrics)) {
                // 没找到就进入后台持续搜索，直到找到歌词、切歌或关闭应用。
                scheduleLyricRetry(track, duration, requestId);
            }
        }));
    }

    private void scheduleLyricRetry(Track track, java.time.Duration duration, long requestId) {
        if (track != currentTrack || requestId != lyricsRequestId) {
            return;
        }

        int delay = LYRIC_RETRY_DELAYS_SECONDS[Math.min(lyricRetryAttempt, LYRIC_RETRY_DELAYS_SECONDS.length - 1)];
        lyricRetryAttempt++;
        sourceLabel.setText("歌词来源：未找到，" + delay + " 秒后继续搜索");
        lyricRetryTask = retryExecutor.schedule(() -> retryLyrics(track, duration, requestId), delay, TimeUnit.SECONDS);
    }

    private void retryLyrics(Track track, java.time.Duration duration, long requestId) {
        // 后台线程里再次校验 requestId，避免用户已经切歌后还继续搜索旧歌曲。
        if (requestId != lyricsRequestId || track != currentTrack) {
            return;
        }

        Lyrics lyrics = lyricsService.searchOnlineAsync(track, duration).join();
        Platform.runLater(() -> {
            if (requestId != lyricsRequestId || track != currentTrack) {
                return;
            }
            if (isMissingLyrics(lyrics)) {
                scheduleLyricRetry(track, duration, requestId);
            } else {
                loadingLyrics.setVisible(false);
                loadingLyrics.setManaged(false);
                showLyrics(lyrics);
            }
        });
    }

    private void cancelLyricRetry() {
        lyricRetryAttempt = 0;
        if (lyricRetryTask != null) {
            lyricRetryTask.cancel(true);
            lyricRetryTask = null;
        }
    }

    private static boolean isMissingLyrics(Lyrics lyrics) {
        return lyrics == null || lyrics.source().startsWith("没有找到") || lyrics.source().startsWith("暂无")
                || lyrics.source().contains("继续搜索");
    }

    private static java.time.Duration javaDuration(Duration duration) {
        if (duration == null || duration.isUnknown() || duration.lessThanOrEqualTo(Duration.ZERO)) {
            return null;
        }
        return java.time.Duration.ofMillis(Math.max(0, Math.round(duration.toMillis())));
    }

    private void showLyrics(Lyrics lyrics) {
        currentLyrics = lyrics;
        lyricRows.setAll(lyrics.lines().stream().map(LyricLine::text).toList());
        sourceLabel.setText("歌词来源：" + lyrics.source());
        if (!lyricRows.isEmpty()) {
            lyricsView.getSelectionModel().select(0);
            lyricsView.scrollTo(0);
        }
    }

    private void updateHighlightedLyric(Duration currentTime) {
        if (currentLyrics == null || !currentLyrics.timed() || currentLyrics.lines().isEmpty()) {
            return;
        }

        List<LyricLine> lines = currentLyrics.lines();
        int selected = 0;
        // 找到最后一行时间小于等于当前播放时间的歌词，并滚动到它附近。
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).time().toMillis() <= Math.round(currentTime.toMillis())) {
                selected = i;
            } else {
                break;
            }
        }

        if (lyricsView.getSelectionModel().getSelectedIndex() != selected) {
            lyricsView.getSelectionModel().select(selected);
            lyricsView.scrollTo(Math.max(0, selected - 4));
        }
    }

    private void showPlayerError(Throwable error) {
        String message = error == null ? "未知错误" : error.getMessage();
        statusLabel.setText("播放失败：" + message);
        playPauseButton.setText("播放");
        showLyrics(Lyrics.empty("当前文件无法播放或编码不受支持"));
    }

    private void disposePlayer() {
        lyricsRequestId++;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }
}
