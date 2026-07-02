// BEGINNER_COMMENTED_BY_CODEX: 本文件已加入面向初学者的中文说明。
// 说明：声明这个 Java 文件属于哪个包，方便其他类按包名找到它。
package app.musicplayer;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.animation.FadeTransition;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.animation.ParallelTransition;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.animation.ScaleTransition;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.animation.TranslateTransition;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.application.Application;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.application.Platform;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.beans.binding.Bindings;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.collections.FXCollections;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.collections.ObservableList;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.collections.transformation.FilteredList;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.geometry.Insets;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.geometry.Orientation;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.geometry.Pos;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.Scene;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.Button;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.ComboBox;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.Label;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.ListCell;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.ListView;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.ProgressIndicator;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.SelectionMode;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.Separator;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.Slider;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.SplitPane;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.control.TextField;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.image.Image;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.image.ImageView;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.input.KeyCode;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.input.KeyEvent;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.layout.BorderPane;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.layout.HBox;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.layout.Priority;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.layout.Region;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.layout.StackPane;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.layout.VBox;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.media.Media;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.media.MediaException;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.media.MediaPlayer;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.scene.shape.Rectangle;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.stage.DirectoryChooser;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.stage.FileChooser;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.stage.Stage;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import javafx.util.Duration;

// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.io.IOException;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.io.InputStream;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.URI;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpClient;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpRequest;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.net.http.HttpResponse;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.attribute.BasicFileAttributes;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.Files;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.Path;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.nio.file.StandardCopyOption;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.security.MessageDigest;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.sql.SQLException;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.ArrayList;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Comparator;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.HashMap;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.HashSet;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.List;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Locale;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Map;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Objects;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Random;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.Set;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.CompletableFuture;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.Executors;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.ScheduledExecutorService;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.ScheduledFuture;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.concurrent.TimeUnit;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.stream.Stream;
// 说明：引入外部类或标准库类，下面的代码才能直接使用这些类型。
import java.util.prefs.Preferences;

// 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
public final class MusicPlayerApp extends Application {
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String TEXT_PREV = new String(new char[]{19978, 19968, 39318});
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String TEXT_PLAY = new String(new char[]{25773, 25918});
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String TEXT_PAUSE = new String(new char[]{26242, 20572});
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String TEXT_NEXT = new String(new char[]{19979, 19968, 39318});
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String TEXT_VOLUME = new String(new char[]{38899, 37327});
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff");
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final int[] LYRIC_RETRY_DELAYS_SECONDS = {10, 20, 30, 60};
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path APP_BASE_DIR = resolveAppBaseDir();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path APP_DATA_DIR = APP_BASE_DIR.resolve("downloads").toAbsolutePath().normalize();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path CACHE_DIR = APP_DATA_DIR.resolve("cache");
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path LYRICS_CACHE_DIR = CACHE_DIR.resolve("lyrics");
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path ARTWORK_CACHE_DIR = CACHE_DIR.resolve("artwork");
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path PLAYBACK_CACHE_DIR = CACHE_DIR.resolve("playback");
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path DOWNLOAD_DIR = APP_DATA_DIR;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final Path DATABASE_PATH = APP_DATA_DIR.resolve("music-player.db");
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String PREF_SORT_TYPE = "playlist.sort.type";
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String PREF_SORT_ORDER = "playlist.sort.order";
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String PREF_ONLINE_PANEL_EXPANDED = "ui.online.panel.expanded";
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String PREF_DIVIDER_LEFT = "ui.split.divider.left";
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final String PREF_DIVIDER_RIGHT = "ui.split.divider.right";
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double DEFAULT_LEFT_DIVIDER = 0.24;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double DEFAULT_RIGHT_DIVIDER = 0.78;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double COLLAPSED_ONLINE_PANEL_WIDTH = 66;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double EXPANDED_ONLINE_PANEL_MIN_WIDTH = 320;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double EXPANDED_ONLINE_PANEL_PREF_WIDTH = 360;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double EXPANDED_ONLINE_PANEL_MAX_WIDTH = 520;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double DEFAULT_LYRICS_FONT_SIZE = 17.0;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double MIN_LYRICS_FONT_SIZE = 13.0;
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private static final double MAX_LYRICS_FONT_SIZE = 30.0;

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final ObservableList<Track> tracks = FXCollections.observableArrayList();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final FilteredList<Track> filteredTracks = new FilteredList<>(tracks, track -> true);
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final ObservableList<String> lyricRows = FXCollections.observableArrayList();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final ObservableList<OnlineTrackInfo> onlineResults = FXCollections.observableArrayList();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final Random random = new Random();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final HttpClient artworkHttpClient = HttpClient.newBuilder()
            // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
            .followRedirects(HttpClient.Redirect.ALWAYS)
            // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
            .connectTimeout(java.time.Duration.ofSeconds(10))
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            .build();
    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final Map<String, Long> creationTimeCache = new HashMap<>();
    // 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
    private final Preferences preferences = Preferences.userNodeForPackage(MusicPlayerApp.class);

    // 说明：定义常量或不可随意替换的字段，用来保存程序会重复使用的固定值。
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Thread t = new Thread(r, "lyrics-retry"); t.setDaemon(true); return t;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    });

    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private MusicDatabase database;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private LyricsService lyricsService;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private OnlineMusicSearchService onlineMusicSearchService;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private SplitPane mainSplitPane;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private VBox playlistSidebar;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ListView<Track> playlistView;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ListView<String> lyricsView;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ListView<OnlineTrackInfo> onlineResultsView;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private TextField searchField;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private TextField onlineSearchField;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ComboBox<String> sortTypeBox;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ComboBox<String> sortOrderBox;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private VBox onlinePanelContent;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private HBox onlinePanelContainer;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Button onlinePanelToggleButton;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Label titleLabel;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Label artistLabel;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Label sourceLabel;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Label statusLabel;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Label timeLabel;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Button playPauseButton;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Slider progressSlider;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Slider volumeSlider;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ComboBox<PlayMode> playModeBox;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ProgressIndicator loadingLyrics;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ProgressIndicator loadingOnlineSearch;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ImageView artworkImageView;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Region artworkDimmer;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private VBox lyricsMetaBox;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private HBox sourceRow;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private StackPane lyricsShell;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Button lyricsLockButton;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Button pureLyricsButton;

    // 说明：JavaFX 媒体播放相关代码，用来加载、播放或控制音频。
    private MediaPlayer mediaPlayer;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private Track currentTrack;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private String currentArtworkSource;
    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private Lyrics currentLyrics = Lyrics.empty("导入歌曲后开始播放");
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private boolean onlinePanelExpanded;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private boolean seeking;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private boolean previewingOnlineResult;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private long lyricsRequestId;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private long onlineSearchRequestId;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private long onlinePreviewRequestId;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private ScheduledFuture<?> lyricRetryTask;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private int lyricRetryAttempt;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private boolean syncingOnlinePanelState;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private boolean lyricsAutoScrollLocked;
    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
    private boolean pureLyricsMode;
    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private double lyricsFontSize = DEFAULT_LYRICS_FONT_SIZE;

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    public static void main(String[] args) { launch(args); }

    // 说明：这是注解，用来告诉 Java 或框架这个声明有特殊含义。
    @Override
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public void start(Stage stage) {
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        initializeServices();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        BorderPane root = new BorderPane();
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        root.getStyleClass().add("app-root");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        root.setTop(createTopBar(stage));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        root.setCenter(createResizableContent());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        root.setBottom(createControls());

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Scene scene = new Scene(root, 1320, 720);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (event.getCode() == KeyCode.SPACE && !isTextInputFocused(scene)) { togglePlayPause(); event.consume(); }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        scene.widthProperty().addListener((o, ov, nv) -> applyResponsiveLayout(nv.doubleValue()));

        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        stage.setTitle("简约音乐播放器");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        stage.setMinWidth(1120);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        stage.setMinHeight(560);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        stage.setScene(scene);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        stage.show();
        // 说明：把界面更新任务交回 JavaFX 主线程执行，避免后台线程直接改 UI。
        Platform.runLater(() -> {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            restoreMainSplitPaneState();
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            applyResponsiveLayout(scene.getWidth());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        restoreSavedTracks();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private SplitPane createResizableContent() {
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        createPlaylist();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StackPane nowPlaying = createNowPlaying();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox onlinePanel = createOnlineSearchPanel();

        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        playlistSidebar.setMinWidth(240);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        playlistSidebar.setPrefWidth(320);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        nowPlaying.setMinWidth(420);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        onlinePanel.setMinWidth(58);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        onlinePanel.setPrefWidth(360);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        mainSplitPane = new SplitPane(playlistSidebar, nowPlaying, onlinePanel);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        mainSplitPane.getStyleClass().add("main-split-pane");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        mainSplitPane.setDividerPositions(DEFAULT_LEFT_DIVIDER, DEFAULT_RIGHT_DIVIDER);

        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        SplitPane.setResizableWithParent(playlistSidebar, true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        SplitPane.setResizableWithParent(nowPlaying, true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        SplitPane.setResizableWithParent(onlinePanel, true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        bindSplitPanePersistence();
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return mainSplitPane;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：这是注解，用来告诉 Java 或框架这个声明有特殊含义。
    @Override
    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    public void stop() {
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        cancelLyricRetry();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        retryExecutor.shutdownNow();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mediaPlayer != null) { mediaPlayer.dispose(); }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (database != null) { database.close(); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void initializeServices() {
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        ensureAppDataDirectory();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        migrateLegacyDatabaseIfNeeded();
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try { database = new MusicDatabase(DATABASE_PATH); }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        catch (SQLException e) { throw new IllegalStateException("无法初始化本地数据库", e); }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricsService = new LyricsService(database, LYRICS_CACHE_DIR);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlineMusicSearchService = new OnlineMusicSearchService();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void ensureAppDataDirectory() {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.createDirectories(APP_DATA_DIR);
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.createDirectories(CACHE_DIR);
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.createDirectories(LYRICS_CACHE_DIR);
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.createDirectories(ARTWORK_CACHE_DIR);
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.createDirectories(PLAYBACK_CACHE_DIR);
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException e) {
            // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
            throw new IllegalStateException("无法创建 downloads 文件夹: " + APP_DATA_DIR, e);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void migrateLegacyDatabaseIfNeeded() {
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path legacyDatabasePath = Path.of("music-player.db").toAbsolutePath().normalize();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (legacyDatabasePath.equals(DATABASE_PATH)) return;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!Files.exists(legacyDatabasePath) || Files.exists(DATABASE_PATH)) return;
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Files.move(legacyDatabasePath, DATABASE_PATH, StandardCopyOption.REPLACE_EXISTING);
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException moveError) {
            // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
            try {
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Files.copy(legacyDatabasePath, DATABASE_PATH, StandardCopyOption.REPLACE_EXISTING);
            // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
            } catch (IOException copyError) {
                // 说明：主动抛出异常，表示当前情况无法继续按正常流程执行。
                throw new IllegalStateException("无法迁移数据库到 downloads 文件夹: " + DATABASE_PATH, copyError);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static Path resolveAppBaseDir() {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
            var codeSource = MusicPlayerApp.class.getProtectionDomain().getCodeSource();
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (codeSource != null && codeSource.getLocation() != null) {
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (Files.isRegularFile(location)) {
                    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                    Path parent = location.getParent();
                    // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                    if (parent != null) return parent;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private static boolean isTextInputFocused(Scene scene) { return scene.getFocusOwner() instanceof TextField; }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private HBox createTopBar(Stage stage) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button importButton = new Button("导入文件夹");
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        importButton.getStyleClass().add("primary-button");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        importButton.setOnAction(event -> importFolder(stage));

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button importFilesButton = new Button("导入音频");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        importFilesButton.setOnAction(event -> importFiles(stage));

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button removeTrackButton = new Button("移除选中");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        removeTrackButton.setOnAction(event -> removeSelectedTrack());

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        playModeBox = new ComboBox<>();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        playModeBox.getItems().setAll(PlayMode.ORDER, PlayMode.SHUFFLE, PlayMode.REPEAT_ONE);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        playModeBox.getSelectionModel().select(PlayMode.ORDER);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button reloadLyricsButton = new Button("搜索歌词");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        reloadLyricsButton.setOnAction(event -> { if (currentTrack != null) { loadLyrics(currentTrack, true); } });

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        statusLabel = new Label("请选择文件夹或音频文件");
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        statusLabel.getStyleClass().addAll("muted-label", "status-chip");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        statusLabel.setWrapText(true);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        statusLabel.setMaxWidth(360);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox topBar = new HBox(12, importButton, importFilesButton, removeTrackButton, playModeBox, reloadLyricsButton, spacer, statusLabel);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        topBar.getStyleClass().add("top-bar");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        topBar.setAlignment(Pos.CENTER_LEFT);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        topBar.setPadding(new Insets(18, 22, 12, 22));
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return topBar;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private VBox createPlaylist() {
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        Label header = new Label("播放列表"); header.getStyleClass().add("section-title");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        searchField = new TextField(); searchField.setPromptText("搜索歌曲、歌手或文件名");
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        searchField.getStyleClass().add("search-field");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        searchField.textProperty().addListener((o, ov, nv) -> applyTrackFilter(nv));

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        sortTypeBox = new ComboBox<>();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        sortTypeBox.getItems().setAll("按名称排序", "按歌手排序", "按文件名排序", "按创建日期排序");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        sortTypeBox.getSelectionModel().select(preferences.get(PREF_SORT_TYPE, "按名称排序"));
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (sortTypeBox.getSelectionModel().getSelectedIndex() < 0) sortTypeBox.getSelectionModel().selectFirst();
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        sortTypeBox.setMaxWidth(Double.MAX_VALUE);
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        sortTypeBox.valueProperty().addListener((o, ov, nv) -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (nv != null) preferences.put(PREF_SORT_TYPE, nv);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            sortTracks();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        sortOrderBox = new ComboBox<>();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        sortOrderBox.getItems().setAll("正序", "倒序");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        sortOrderBox.getSelectionModel().select(preferences.get(PREF_SORT_ORDER, "正序"));
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (sortOrderBox.getSelectionModel().getSelectedIndex() < 0) sortOrderBox.getSelectionModel().selectFirst();
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        sortOrderBox.setMaxWidth(Double.MAX_VALUE);
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        sortOrderBox.valueProperty().addListener((o, ov, nv) -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (nv != null) preferences.put(PREF_SORT_ORDER, nv);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            sortTracks();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox sortRow = new HBox(8, sortTypeBox, sortOrderBox);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        sortRow.getStyleClass().add("sort-row");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(sortTypeBox, Priority.ALWAYS);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(sortOrderBox, Priority.ALWAYS);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        playlistView = new ListView<>(filteredTracks);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        playlistView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
        playlistView.setCellFactory(v -> new ListCell<>() {
            // 说明：设置界面文字，让用户看到当前状态或提示信息。
            protected void updateItem(Track tr, boolean empty) { super.updateItem(tr, empty); setText(empty || tr == null ? null : tr.toString()); }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        playlistView.setOnMouseClicked(ev -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (ev.getClickCount() == 2) { Track sel = playlistView.getSelectionModel().getSelectedItem(); if (sel != null) playTrack(sel); }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        playlistSidebar = new VBox(10, header, searchField, sortRow, playlistView);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        playlistSidebar.getStyleClass().add("sidebar");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        playlistSidebar.setPadding(new Insets(8, 0, 18, 22));
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        playlistSidebar.setMinWidth(240);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        playlistSidebar.setPrefWidth(320);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        VBox.setVgrow(playlistView, Priority.ALWAYS);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return playlistSidebar;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private HBox createOnlineSearchPanel() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlinePanelExpanded = preferences.getBoolean(PREF_ONLINE_PANEL_EXPANDED, false);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        Label header = new Label("在线下载"); header.getStyleClass().add("section-title");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlineSearchField = new TextField(); onlineSearchField.setPromptText("搜索歌曲名 / 歌手名");
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        onlineSearchField.getStyleClass().add("search-field");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        onlineSearchField.setOnAction(e -> searchOnlineTracks());

        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        Button searchButton = new Button("搜索"); searchButton.getStyleClass().add("primary-button");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        searchButton.setOnAction(e -> searchOnlineTracks());

        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        loadingOnlineSearch = new ProgressIndicator(); loadingOnlineSearch.setMaxSize(18, 18);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        loadingOnlineSearch.setVisible(false); loadingOnlineSearch.setManaged(false);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox searchRow = new HBox(8, onlineSearchField, searchButton, loadingOnlineSearch);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        searchRow.getStyleClass().add("search-row");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        searchRow.setMinWidth(0);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(onlineSearchField, Priority.ALWAYS);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlineResultsView = new ListView<>(onlineResults);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        onlineResultsView.getStyleClass().add("online-results-view");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlineResultsView.setPlaceholder(new Label("搜索 QQMP3 / 网易云 / QQ / 酷狗，双击下载到本地"));
        // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
        onlineResultsView.setCellFactory(v -> new ListCell<>() {
            // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
            protected void updateItem(OnlineTrackInfo item, boolean empty) {
                // 说明：调用父类的实现，用来复用或初始化继承来的逻辑。
                super.updateItem(item, empty);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                getStyleClass().removeAll("result-downloadable", "result-tryable", "result-unavailable");
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (empty || item == null) { setText(null); return; }
                // 说明：设置界面文字，让用户看到当前状态或提示信息。
                setText(item.title() + "\n" + item.subtitle()); setWrapText(true);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (item.downloadable()) getStyleClass().add("result-downloadable");
                // 说明：追加条件判断：前面的条件不成立时，再检查这个新条件。
                else if ("可尝试下载".equals(item.availabilityText())) getStyleClass().add("result-tryable");
                // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
                else getStyleClass().add("result-unavailable");
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        onlineResultsView.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> { if (nv != null) previewOnlineTrack(nv); });
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        onlineResultsView.setOnMouseClicked(ev -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (ev.getClickCount() == 2) { OnlineTrackInfo sel = onlineResultsView.getSelectionModel().getSelectedItem(); if (sel != null) downloadAndPlayOnlineTrack(sel); }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Label hint = new Label("单击预览 · 双击下载到本地播放");
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        hint.getStyleClass().add("muted-label");

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlinePanelContent = new VBox(10, header, searchRow, hint, onlineResultsView);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        onlinePanelContent.getStyleClass().add("online-panel-content");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlinePanelContent.setPadding(new Insets(12, 16, 16, 12));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlinePanelContent.setFillWidth(true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(onlinePanelContent, Priority.ALWAYS);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        VBox.setVgrow(onlineResultsView, Priority.ALWAYS);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlinePanelToggleButton = new Button();
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        onlinePanelToggleButton.getStyleClass().add("drawer-toggle-button");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlinePanelToggleButton.setWrapText(true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlinePanelToggleButton.setFocusTraversable(false);
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        onlinePanelToggleButton.setOnAction(e -> toggleOnlinePanel());

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlinePanelContainer = new HBox(onlinePanelToggleButton, onlinePanelContent);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        onlinePanelContainer.getStyleClass().add("online-panel");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlinePanelContainer.setAlignment(Pos.CENTER_LEFT);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        onlinePanelContainer.setMinWidth(COLLAPSED_ONLINE_PANEL_WIDTH);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        onlinePanelContainer.setPrefWidth(EXPANDED_ONLINE_PANEL_PREF_WIDTH);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        onlinePanelContainer.setMaxWidth(EXPANDED_ONLINE_PANEL_MAX_WIDTH);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        applyOnlinePanelState(false);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return onlinePanelContainer;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private StackPane createNowPlaying() {
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        titleLabel = new Label("未播放歌曲"); titleLabel.getStyleClass().add("track-title"); titleLabel.setWrapText(true);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        artistLabel = new Label("导入文件夹或音频文件后双击歌曲播放"); artistLabel.getStyleClass().add("track-artist"); artistLabel.setWrapText(true);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        sourceLabel = new Label("歌词来源：暂无"); sourceLabel.getStyleClass().add("muted-label");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        loadingLyrics = new ProgressIndicator(); loadingLyrics.setMaxSize(18, 18); loadingLyrics.setVisible(false); loadingLyrics.setManaged(false);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        sourceRow = new HBox(8, sourceLabel, loadingLyrics);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        sourceRow.getStyleClass().add("lyrics-source-row");

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricsView = new ListView<>(lyricRows);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        lyricsView.getStyleClass().add("lyrics-view");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        lyricsView.setFocusTraversable(false);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        lyricsView.setMinWidth(0);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        lyricsView.setMinHeight(0);
        // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
        lyricsView.setCellFactory(v -> new ListCell<>() {
            // 说明：这是注解，用来告诉 Java 或框架这个声明有特殊含义。
            @Override
            // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
            protected void updateItem(String item, boolean empty) {
                // 说明：调用父类的实现，用来复用或初始化继承来的逻辑。
                super.updateItem(item, empty);
                // 说明：设置界面文字，让用户看到当前状态或提示信息。
                setText(empty || item == null ? null : item);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                setWrapText(true);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                setStyle(String.format(Locale.US, "-fx-font-size: %.1fpx;", lyricsFontSize));
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricsShell = new StackPane(lyricsView);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        lyricsShell.getStyleClass().add("lyrics-shell");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        lyricsShell.setMinWidth(0);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        lyricsShell.setMinHeight(0);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        VBox.setVgrow(lyricsShell, Priority.ALWAYS);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricsMetaBox = new VBox(8, titleLabel, artistLabel, sourceRow);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        lyricsMetaBox.getStyleClass().add("lyrics-meta");

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox lyricsToolbar = createLyricsToolbar();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        VBox nowPlaying = new VBox(12, lyricsToolbar, lyricsMetaBox, lyricsShell);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        nowPlaying.getStyleClass().add("content-foreground");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        nowPlaying.setPadding(new Insets(24, 22, 16, 22));
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        nowPlaying.setMinWidth(0);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        nowPlaying.setMinHeight(0);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        artworkImageView = new ImageView();
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        artworkImageView.getStyleClass().add("artwork-background");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        artworkImageView.setPreserveRatio(false);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        artworkImageView.setSmooth(true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        artworkImageView.setVisible(false);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        artworkDimmer = new Region();
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        artworkDimmer.getStyleClass().add("artwork-dimmer");

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StackPane stack = new StackPane(artworkImageView, artworkDimmer, nowPlaying);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        stack.getStyleClass().add("content");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        stack.setMinWidth(0);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        stack.setMinHeight(0);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        artworkImageView.fitWidthProperty().bind(stack.widthProperty());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        artworkImageView.fitHeightProperty().bind(stack.heightProperty());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        showLyrics(Lyrics.empty("导入歌曲后开始播放"));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        applyPureLyricsMode();
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return stack;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private HBox createLyricsToolbar() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricsLockButton = createLyricsToolButton("锁定歌词", this::toggleLyricsLock);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button zoomInButton = createLyricsToolButton("放大字体", () -> changeLyricsFontSize(1.5));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button zoomOutButton = createLyricsToolButton("缩小字体", () -> changeLyricsFontSize(-1.5));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        pureLyricsButton = createLyricsToolButton("纯歌词模式", this::togglePureLyricsMode);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Region spacer = new Region();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox toolbar = new HBox(8, lyricsLockButton, zoomInButton, zoomOutButton, pureLyricsButton, spacer);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        toolbar.getStyleClass().add("lyrics-toolbar");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        updateLyricsToolbarState();
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return toolbar;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Button createLyricsToolButton(String text, Runnable action) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button button = new Button(text);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        button.getStyleClass().add("toolbar-button");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        button.setFocusTraversable(false);
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        button.setOnAction(e -> action.run());
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return button;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void toggleLyricsLock() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricsAutoScrollLocked = !lyricsAutoScrollLocked;
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        updateLyricsToolbarState();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!lyricsAutoScrollLocked && mediaPlayer != null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            updateHighlightedLyric(mediaPlayer.getCurrentTime());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void changeLyricsFontSize(double delta) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricsFontSize = clamp(lyricsFontSize + delta, MIN_LYRICS_FONT_SIZE, MAX_LYRICS_FONT_SIZE);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyricsView != null) lyricsView.refresh();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void togglePureLyricsMode() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        pureLyricsMode = !pureLyricsMode;
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        applyPureLyricsMode();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        updateLyricsToolbarState();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void applyPureLyricsMode() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyricsMetaBox != null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            lyricsMetaBox.setVisible(!pureLyricsMode);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            lyricsMetaBox.setManaged(!pureLyricsMode);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (artworkDimmer != null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkDimmer.setOpacity(pureLyricsMode ? 0.48 : 1.0);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (artworkImageView != null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkImageView.setOpacity(pureLyricsMode ? 0.16 : 0.34);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyricsShell != null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            StackPane.setMargin(lyricsShell, pureLyricsMode ? new Insets(2, 0, 0, 0) : Insets.EMPTY);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void updateLyricsToolbarState() {
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        toggleToolbarButtonState(lyricsLockButton, lyricsAutoScrollLocked);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        toggleToolbarButtonState(pureLyricsButton, pureLyricsMode);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static void toggleToolbarButtonState(Button button, boolean active) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (button == null) return;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (active) {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (!button.getStyleClass().contains("toolbar-button-active")) {
                // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
                button.getStyleClass().add("toolbar-button-active");
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
        } else {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            button.getStyleClass().remove("toolbar-button-active");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private VBox createControls() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button prevBtn = new Button(TEXT_PREV);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        prevBtn.getStyleClass().add("control-button");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        prevBtn.setOnAction(e -> previousTrack());

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        playPauseButton = new Button(TEXT_PLAY);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        playPauseButton.getStyleClass().addAll("primary-button", "control-button", "play-button");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        playPauseButton.setOnAction(e -> togglePlayPause());

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Button nextBtn = new Button(TEXT_NEXT);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        nextBtn.getStyleClass().add("control-button");
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        nextBtn.setOnAction(e -> nextTrack(true));

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        progressSlider = new Slider(0, 1, 0);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        progressSlider.setMinHeight(28);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        progressSlider.setPrefHeight(28);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        progressSlider.setMaxHeight(28);
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        progressSlider.valueChangingProperty().addListener((o, was, is) -> { seeking = is; if (!is) seekToProgress(); });
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        progressSlider.setOnMouseReleased(e -> seekToProgress());

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        timeLabel = new Label("00:00 / 00:00");
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        timeLabel.getStyleClass().add("time-label");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        timeLabel.setMinWidth(112);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        timeLabel.setMinHeight(28);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        timeLabel.setPrefHeight(28);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Label volLbl = new Label(TEXT_VOLUME);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        volLbl.getStyleClass().add("muted-label");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        volumeSlider = new Slider(0, 1, 0.75);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        volumeSlider.getStyleClass().add("volume-slider");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSlider.setMinWidth(120);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSlider.setPrefWidth(140);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSlider.setMaxWidth(140);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSlider.setMinSize(120, Region.USE_PREF_SIZE);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSlider.setPrefSize(140, Region.USE_PREF_SIZE);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSlider.setMaxSize(140, Region.USE_PREF_SIZE);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(volumeSlider, Priority.NEVER);
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        volumeSlider.valueProperty().addListener((o, ov, nv) -> { if (mediaPlayer != null) mediaPlayer.setVolume(nv.doubleValue()); });

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox transportGroup = new HBox(10, prevBtn, playPauseButton, nextBtn);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        transportGroup.getStyleClass().add("transport-group");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        transportGroup.setAlignment(Pos.CENTER_LEFT);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        StackPane volumeSliderHolder = new StackPane(volumeSlider);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        volumeSliderHolder.getStyleClass().add("volume-slider-holder");
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSliderHolder.setMinWidth(150);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSliderHolder.setPrefWidth(150);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSliderHolder.setMaxWidth(150);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSliderHolder.setMinSize(150, 42);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSliderHolder.setPrefSize(150, 42);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeSliderHolder.setMaxSize(150, 42);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Rectangle volumeClip = new Rectangle();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        volumeClip.widthProperty().bind(volumeSliderHolder.widthProperty());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        volumeClip.heightProperty().bind(volumeSliderHolder.heightProperty());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        volumeSliderHolder.setClip(volumeClip);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(volumeSliderHolder, Priority.NEVER);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox volumeGroup = new HBox(8, volLbl, volumeSliderHolder);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        volumeGroup.getStyleClass().add("volume-group");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        volumeGroup.setAlignment(Pos.CENTER_LEFT);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeGroup.setMinWidth(198);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeGroup.setPrefWidth(210);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        volumeGroup.setMaxWidth(220);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(volumeGroup, Priority.NEVER);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox btns = new HBox(14, transportGroup, new Separator(Orientation.VERTICAL), volumeGroup);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        btns.getStyleClass().add("control-row");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        btns.setAlignment(Pos.CENTER_LEFT);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        btns.setMinHeight(56);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        btns.setPrefHeight(56);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        btns.setMaxHeight(56);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        HBox progressRow = new HBox(12, progressSlider, timeLabel);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        progressRow.getStyleClass().add("progress-row");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        progressRow.setAlignment(Pos.CENTER_LEFT);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        VBox controls = new VBox(8, progressRow, btns);
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        controls.getStyleClass().add("controls");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        controls.setPadding(new Insets(10, 22, 14, 22));
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        controls.setMinHeight(118);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        controls.setPrefHeight(124);
        // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
        controls.setMaxHeight(132);

        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        playPauseButton.disableProperty().bind(Bindings.isEmpty(tracks));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        prevBtn.disableProperty().bind(Bindings.isEmpty(tracks));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        nextBtn.disableProperty().bind(Bindings.isEmpty(tracks));
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return controls;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void importFolder(Stage stage) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        DirectoryChooser c = new DirectoryChooser(); c.setTitle("选择歌曲文件夹");
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path dp = Path.of(System.getProperty("user.home"), "Music");
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (Files.isDirectory(dp)) c.setInitialDirectory(dp.toFile());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        var dir = c.showDialog(stage); if (dir == null) return;
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (Stream<Path> s = Files.walk(dir.toPath())) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            addImportedTracks(s.filter(Files::isRegularFile).filter(this::isSupportedAudio).sorted().map(Track::new).toList(), "文件夹");
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException e) { statusLabel.setText("导入失败：" + e.getMessage()); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void importFiles(Stage stage) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        FileChooser c = new FileChooser(); c.setTitle("选择音频文件");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        c.getExtensionFilters().add(new FileChooser.ExtensionFilter("音频文件", "*.mp3", "*.m4a", "*.aac", "*.wav", "*.aif", "*.aiff"));
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<java.io.File> files = c.showOpenMultipleDialog(stage); if (files == null || files.isEmpty()) return;
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        addImportedTracks(files.stream().map(java.io.File::toPath).filter(Files::isRegularFile).filter(this::isSupportedAudio).map(Track::new).toList(), "音频文件");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void addImportedTracks(List<Track> imported, String source) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (imported == null || imported.isEmpty()) { statusLabel.setText("没有找到可导入的" + source); return; }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Set<String> existing = new HashSet<>();
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (Track t : tracks) existing.add(t.path().toAbsolutePath().normalize().toString());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<Track> added = new ArrayList<>(); int dup = 0;
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (Track t : imported) {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (existing.add(t.path().toAbsolutePath().normalize().toString())) added.add(t); else dup++;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (added.isEmpty()) { statusLabel.setText("全部 " + imported.size() + " 首都已在歌单中"); return; }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        tracks.addAll(added); database.saveTracks(added);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        sortTracks();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        applyTrackFilter(searchField == null ? "" : searchField.getText());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        playlistView.getSelectionModel().select(added.get(0));
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        statusLabel.setText("已新增 " + added.size() + " 首" + (dup > 0 ? "，忽略重复 " + dup + " 首" : ""));
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (currentTrack == null) playTrack(added.get(0));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void restoreSavedTracks() {
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        List<Track> saved = database.loadTracks().stream().filter(t -> Files.isRegularFile(t.path())).filter(t -> isSupportedAudio(t.path())).toList();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!saved.isEmpty()) { tracks.setAll(saved); sortTracks(); applyTrackFilter(""); playlistView.getSelectionModel().select(0); statusLabel.setText("已恢复 " + saved.size() + " 首歌曲"); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void applyTrackFilter(String q) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String n = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
        filteredTracks.setPredicate(tr -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (n.isBlank()) return true;
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            String fn = tr.path().getFileName().toString().toLowerCase(Locale.ROOT);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return tr.title().toLowerCase(Locale.ROOT).contains(n) || tr.artist().toLowerCase(Locale.ROOT).contains(n) || fn.contains(n);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (currentTrack != null && filteredTracks.contains(currentTrack)) playlistView.getSelectionModel().select(currentTrack);
        // 说明：追加条件判断：前面的条件不成立时，再检查这个新条件。
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
    private boolean isSupportedAudio(Path p) { String fn = p.getFileName().toString(); int dot = fn.lastIndexOf('.'); return dot >= 0 && SUPPORTED_EXTENSIONS.contains(fn.substring(dot + 1).toLowerCase(Locale.ROOT)); }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private void playTrack(int idx) { if (idx >= 0 && idx < tracks.size()) playTrack(tracks.get(idx)); }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void playTrack(Track track) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int idx = tracks.indexOf(track); if (idx < 0) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        disposePlayer(); previewingOnlineResult = false; currentTrack = track;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (filteredTracks.contains(track)) { playlistView.getSelectionModel().select(track); playlistView.scrollTo(track); }
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        titleLabel.setText(currentTrack.title()); artistLabel.setText(currentTrack.artist());
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        progressSlider.setValue(0); timeLabel.setText("00:00 / 00:00");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        showArtwork(null); showLyrics(Lyrics.empty("正在准备歌词..."));
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path playbackPath = resolvePlayableMediaPath(currentTrack.path());
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (looksLikeUnsupportedRawAac(playbackPath)) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            showPlayerError(new IllegalStateException("JavaFX 无法稳定播放原始 AAC 音频"));
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }

        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Media media = new Media(playbackPath.toUri().toString());
            // 说明：JavaFX 媒体播放相关代码，用来加载、播放或控制音频。
            mediaPlayer = new MediaPlayer(media); mediaPlayer.setVolume(volumeSlider.getValue());
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            mediaPlayer.currentTimeProperty().addListener((o, ot, nt) -> updatePlaybackProgress(nt));
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            mediaPlayer.setOnReady(() -> { updateMetadata(media); database.saveTrack(currentTrack, javaDuration(mediaPlayer.getTotalDuration())); updateDurationLabel(); loadLyrics(currentTrack, false); mediaPlayer.play(); });
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            mediaPlayer.setOnPlaying(() -> playPauseButton.setText("暂停"));
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            mediaPlayer.setOnPaused(() -> playPauseButton.setText("播放"));
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            mediaPlayer.setOnStopped(() -> playPauseButton.setText("播放"));
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            mediaPlayer.setOnEndOfMedia(this::handleEndOfMedia);
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            mediaPlayer.setOnError(() -> showPlayerError(mediaPlayer.getError()));
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            media.setOnError(() -> showPlayerError(media.getError()));
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (MediaException e) { showPlayerError(e); }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void updateMetadata(Media media) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String t = valueAsString(media.getMetadata().get("title")), a = valueAsString(media.getMetadata().get("artist"));
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        currentTrack.updateMetadata(t, a); titleLabel.setText(currentTrack.title()); artistLabel.setText(currentTrack.artist());
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        playlistView.refresh(); database.saveTrack(currentTrack, mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration()));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private static String valueAsString(Object v) { return v instanceof String s ? s : null; }

    
    // ========== 在线搜索结果：双击爬虫下载到本地再播放 ==========

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void downloadAndPlayOnlineTrack(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null) return;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!info.canAttemptDownload()) {
            // 说明：设置界面文字，让用户看到当前状态或提示信息。
            statusLabel.setText("当前结果不可下载：" + info.availabilityText());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            showLyrics(Lyrics.empty("当前在线结果不可下载"));
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        disposePlayer(); cancelLyricRetry();

        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        statusLabel.setText("正在爬取下载：" + info.title());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        showArtwork(info.artworkUrl());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        showLyrics(Lyrics.empty("正在从网页爬取下载 " + info.title() + "，请稍候..."));
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        sourceLabel.setText("歌词来源：下载中...");

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        long reqId = ++onlinePreviewRequestId;

        // 说明：异步任务相关代码：把耗时操作放到后台执行，完成后再处理结果。
        onlineMusicSearchService.downloadAsync(info, DOWNLOAD_DIR).whenComplete((downloadedPath, err) -> Platform.runLater(() -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (reqId != onlinePreviewRequestId) return;
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (err != null || downloadedPath == null) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                showLyrics(Lyrics.empty("爬取下载失败"));
                // 说明：设置界面文字，让用户看到当前状态或提示信息。
                statusLabel.setText("下载失败：" + (err != null ? err.getMessage() : "未知错误"));
                // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
                return;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }

            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Track newTrack = new Track(downloadedPath);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Set<String> existing = new HashSet<>();
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (Track t : tracks) existing.add(t.path().toAbsolutePath().normalize().toString());
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (!existing.add(downloadedPath.toAbsolutePath().normalize().toString())) {
                // 说明：设置界面文字，让用户看到当前状态或提示信息。
                statusLabel.setText("已在歌单中：" + info.title());
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Track et = tracks.stream().filter(t -> t.path().toAbsolutePath().normalize().toString().equals(downloadedPath.toAbsolutePath().normalize().toString())).findFirst().orElse(newTrack);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                playTrack(et); return;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }

            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            tracks.add(newTrack);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            database.saveTracks(List.of(newTrack));
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            sortTracks();
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (searchField != null && !searchField.getText().isBlank()) searchField.setText("");
            // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
            else applyTrackFilter("");
            // 说明：设置界面文字，让用户看到当前状态或提示信息。
            statusLabel.setText("爬取下载完成：" + info.title());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            playTrack(newTrack);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }
// ========== 共享播放控制 ==========

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void togglePlayPause() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mediaPlayer == null) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            Track sel = playlistView.getSelectionModel().getSelectedItem();
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (sel != null) playTrack(sel); else if (!tracks.isEmpty()) playTrack(0);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) mediaPlayer.pause(); else mediaPlayer.play();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void previousTrack() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (tracks.isEmpty()) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int cur = currentTrackIndex(); playTrack(cur <= 0 ? tracks.size() - 1 : cur - 1);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void nextTrack(boolean manual) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (tracks.isEmpty()) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        PlayMode m = playModeBox.getValue();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (m == PlayMode.REPEAT_ONE && !manual) { replayCurrent(); return; }
        // 说明：开始一个新的代码块，下面缩进的内容属于这个结构。
        playTrack(switch (m) {
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case SHUFFLE -> randomIndex();
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case ORDER, REPEAT_ONE -> { int cur = currentTrackIndex(); yield cur + 1 >= tracks.size() ? 0 : cur + 1; }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private int randomIndex() { if (tracks.size() <= 1) return 0; int c = currentTrackIndex(), n; do { n = random.nextInt(tracks.size()); } while (n == c); return n; }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void handleEndOfMedia() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (playModeBox.getValue() == PlayMode.ORDER) {
            // 说明：设置界面文字，让用户看到当前状态或提示信息。
            int cur = currentTrackIndex(); if (cur >= tracks.size() - 1) { mediaPlayer.stop(); mediaPlayer.seek(Duration.ZERO); progressSlider.setValue(0); updatePlaybackProgress(Duration.ZERO); statusLabel.setText("顺序播放结束"); return; }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        nextTrack(false);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private int currentTrackIndex() { int i = currentTrack == null ? -1 : tracks.indexOf(currentTrack); if (i >= 0) return i; Track s = playlistView.getSelectionModel().getSelectedItem(); return s == null ? -1 : tracks.indexOf(s); }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private void replayCurrent() { if (mediaPlayer != null) { mediaPlayer.seek(Duration.ZERO); mediaPlayer.play(); } }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void seekToProgress() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mediaPlayer == null || mediaPlayer.getTotalDuration() == null) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Duration t = mediaPlayer.getTotalDuration(); if (t.greaterThan(Duration.ZERO)) mediaPlayer.seek(t.multiply(progressSlider.getValue()));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void updatePlaybackProgress(Duration ct) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mediaPlayer == null) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Duration t = mediaPlayer.getTotalDuration();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (t != null && t.greaterThan(Duration.ZERO) && !seeking) progressSlider.setValue(ct.toMillis() / t.toMillis());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        updateDurationLabel(); updateHighlightedLyric(ct);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void updateDurationLabel() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mediaPlayer == null) { timeLabel.setText("00:00 / 00:00"); return; }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Duration c = mediaPlayer.getCurrentTime(), t = mediaPlayer.getTotalDuration();
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        timeLabel.setText(formatTime(c) + " / " + formatTime(t));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static String formatTime(Duration d) { if (d == null || d.isUnknown() || d.lessThan(Duration.ZERO)) return "00:00"; long s = Math.round(d.toSeconds()); return "%02d:%02d".formatted(s / 60, s % 60); }

    // ========== 歌词与封面 ==========

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void loadLyrics(Track track, boolean manual) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (track == null) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        previewingOnlineResult = false; cancelLyricRetry();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        long reqId = ++lyricsRequestId;
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        loadingLyrics.setVisible(true); loadingLyrics.setManaged(true);
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        sourceLabel.setText(manual ? "歌词来源：正在重新搜索..." : "歌词来源：正在搜索...");

        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        java.time.Duration dur = mediaPlayer == null ? null : javaDuration(mediaPlayer.getTotalDuration());
        // 说明：异步任务相关代码：把耗时操作放到后台执行，完成后再处理结果。
        CompletableFuture<LyricsLookupResult> f = manual ? lyricsService.searchOnlineAsync(track, dur) : lyricsService.findLyrics(track, dur);
        // 说明：异步任务相关代码：把耗时操作放到后台执行，完成后再处理结果。
        f.whenComplete((r, err) -> Platform.runLater(() -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (reqId != lyricsRequestId || track != currentTrack) return;
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            loadingLyrics.setVisible(false); loadingLyrics.setManaged(false);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (err != null) { showLyrics(Lyrics.empty("歌词加载失败")); return; }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            showLyrics(r.lyrics()); showArtwork(r.artworkUrl());
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (isMissingLyrics(r.lyrics())) scheduleLyricRetry(track, dur, reqId);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void scheduleLyricRetry(Track track, java.time.Duration dur, long reqId) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (track != currentTrack || reqId != lyricsRequestId) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int delay = LYRIC_RETRY_DELAYS_SECONDS[Math.min(lyricRetryAttempt, LYRIC_RETRY_DELAYS_SECONDS.length - 1)];
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        lyricRetryAttempt++; sourceLabel.setText("歌词来源：未找到，" + delay + " 秒后继续搜索");
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        lyricRetryTask = retryExecutor.schedule(() -> retryLyrics(track, dur, reqId), delay, TimeUnit.SECONDS);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void retryLyrics(Track track, java.time.Duration dur, long reqId) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (reqId != lyricsRequestId || track != currentTrack) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        LyricsLookupResult r = lyricsService.searchOnlineAsync(track, dur).join();
        // 说明：把界面更新任务交回 JavaFX 主线程执行，避免后台线程直接改 UI。
        Platform.runLater(() -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (reqId != lyricsRequestId || track != currentTrack) return;
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (isMissingLyrics(r.lyrics())) scheduleLyricRetry(track, dur, reqId);
            // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
            else { loadingLyrics.setVisible(false); loadingLyrics.setManaged(false); showLyrics(r.lyrics()); showArtwork(r.artworkUrl()); }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private void cancelLyricRetry() { lyricRetryAttempt = 0; if (lyricRetryTask != null) { lyricRetryTask.cancel(true); lyricRetryTask = null; } }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static boolean isMissingLyrics(Lyrics l) { return l == null || l.source().startsWith("没有找到") || l.source().startsWith("暂无") || l.source().contains("继续搜索"); }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private static java.time.Duration javaDuration(Duration d) { if (d == null || d.isUnknown() || d.lessThanOrEqualTo(Duration.ZERO)) return null; return java.time.Duration.ofMillis(Math.max(0, Math.round(d.toMillis()))); }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void showLyrics(Lyrics lyrics) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        currentLyrics = lyrics;
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        lyricRows.setAll(lyrics.lines().stream().map(LyricLine::text).toList());
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        sourceLabel.setText("歌词来源：" + lyrics.source());
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyricsView != null && !lyricRows.isEmpty()) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            lyricsView.getSelectionModel().select(0);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            lyricsView.scrollTo(0);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyricsView != null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            lyricsView.refresh();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void sortTracks() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (tracks.isEmpty()) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String sortMode = sortTypeBox == null ? "按名称排序" : sortTypeBox.getValue();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Comparator<Track> comparator = switch (sortMode) {
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "按歌手排序" -> Comparator.comparing(this::trackArtistKey).thenComparing(this::trackNameKey);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "按文件名排序" -> Comparator.comparing(this::trackFileNameKey).thenComparing(this::trackNameKey);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            case "按创建日期排序" -> Comparator.comparingLong(this::trackCreatedTimeMillis).thenComparing(this::trackNameKey);
            // 说明：switch 的一个分支，匹配到这个情况时使用这一段结果或逻辑。
            default -> Comparator.comparing(this::trackNameKey).thenComparing(this::trackArtistKey);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        };
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (isSortDescending()) comparator = comparator.reversed();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Track selected = playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        tracks.sort(comparator);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (selected != null && filteredTracks.contains(selected)) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            playlistView.getSelectionModel().select(selected);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            playlistView.scrollTo(selected);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private boolean isSortDescending() { return sortOrderBox != null && Objects.equals(sortOrderBox.getValue(), "倒序"); }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void toggleOnlinePanel() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (onlinePanelExpanded && mainSplitPane != null && mainSplitPane.getDividers().size() >= 2) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            double[] positions = mainSplitPane.getDividerPositions();
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            preferences.putDouble(PREF_DIVIDER_LEFT, positions[0]);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            preferences.putDouble(PREF_DIVIDER_RIGHT, positions[1]);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        onlinePanelExpanded = !onlinePanelExpanded;
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        animateOnlinePanelState();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void applyOnlinePanelState(boolean persist) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (persist) preferences.putBoolean(PREF_ONLINE_PANEL_EXPANDED, onlinePanelExpanded);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (onlinePanelContent != null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            onlinePanelContent.setVisible(onlinePanelExpanded);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            onlinePanelContent.setManaged(onlinePanelExpanded);
            // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
            onlinePanelContent.setPrefWidth(onlinePanelExpanded ? EXPANDED_ONLINE_PANEL_PREF_WIDTH - COLLAPSED_ONLINE_PANEL_WIDTH : 0);
            // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
            onlinePanelContent.setMinWidth(onlinePanelExpanded ? 240 : 0);
            // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
            onlinePanelContent.setMaxWidth(onlinePanelExpanded ? Double.MAX_VALUE : 0);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            onlinePanelContent.setOpacity(onlinePanelExpanded ? 1.0 : 0.0);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (onlinePanelContainer != null) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            double minWidth = onlinePanelExpanded ? EXPANDED_ONLINE_PANEL_MIN_WIDTH : COLLAPSED_ONLINE_PANEL_WIDTH;
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            double prefWidth = onlinePanelExpanded ? EXPANDED_ONLINE_PANEL_PREF_WIDTH : COLLAPSED_ONLINE_PANEL_WIDTH;
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            double maxWidth = onlinePanelExpanded ? Double.MAX_VALUE : COLLAPSED_ONLINE_PANEL_WIDTH;
            // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
            onlinePanelContainer.setMinWidth(minWidth);
            // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
            onlinePanelContainer.setPrefWidth(prefWidth);
            // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
            onlinePanelContainer.setMaxWidth(maxWidth);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            onlinePanelContainer.getStyleClass().removeAll("online-panel-expanded", "online-panel-collapsed");
            // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
            onlinePanelContainer.getStyleClass().add(onlinePanelExpanded ? "online-panel-expanded" : "online-panel-collapsed");
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        updateOnlineToggleButton();
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        syncOnlinePanelDivider();
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void animateOnlinePanelState() {
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        preferences.putBoolean(PREF_ONLINE_PANEL_EXPANDED, onlinePanelExpanded);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (onlinePanelContent == null) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            applyOnlinePanelState(false);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        ScaleTransition pulse = new ScaleTransition(Duration.millis(180), onlinePanelToggleButton);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        pulse.setFromX(1.0);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        pulse.setFromY(1.0);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        pulse.setToX(1.08);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        pulse.setToY(1.08);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        pulse.setAutoReverse(true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        pulse.setCycleCount(2);

        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (onlinePanelExpanded) {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (onlinePanelContent != null) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                onlinePanelContent.setManaged(true);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                onlinePanelContent.setVisible(true);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                onlinePanelContent.setOpacity(0.0);
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                onlinePanelContent.setTranslateX(24);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            applyOnlinePanelState(false);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            FadeTransition fade = new FadeTransition(Duration.millis(180), onlinePanelContent);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            fade.setFromValue(0.0);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            fade.setToValue(1.0);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            TranslateTransition slide = new TranslateTransition(Duration.millis(220), onlinePanelContent);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            slide.setFromX(24);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            slide.setToX(0);
            // 说明：创建一个新对象实例，让程序可以使用这个对象提供的功能。
            new ParallelTransition(fade, slide, pulse).play();
        // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
        } else {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            updateOnlineToggleButton();
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            FadeTransition fade = new FadeTransition(Duration.millis(150), onlinePanelContent);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            fade.setFromValue(onlinePanelContent.getOpacity());
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            fade.setToValue(0.0);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            TranslateTransition slide = new TranslateTransition(Duration.millis(180), onlinePanelContent);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            slide.setFromX(0);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            slide.setToX(24);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            ParallelTransition animation = new ParallelTransition(fade, slide, pulse);
            // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
            animation.setOnFinished(e -> applyOnlinePanelState(false));
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            animation.play();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void updateOnlineToggleButton() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (onlinePanelToggleButton == null) return;
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        onlinePanelToggleButton.setText(onlinePanelExpanded ? "‹\n收起\n搜索" : "☰\n在线\n搜索\n›");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlinePanelToggleButton.setTooltip(new javafx.scene.control.Tooltip(onlinePanelExpanded ? "收起在线搜索抽屉" : "展开在线搜索抽屉"));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        onlinePanelToggleButton.getStyleClass().removeAll("drawer-expanded", "drawer-collapsed");
        // 说明：给界面控件添加 CSS 样式类，让 styles.css 可以控制它的外观。
        onlinePanelToggleButton.getStyleClass().add(onlinePanelExpanded ? "drawer-expanded" : "drawer-collapsed");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void bindSplitPanePersistence() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        mainSplitPane.getDividers().get(0).positionProperty().addListener((o, ov, nv) -> persistMainSplitPaneState());
        // 说明：注册事件监听器；用户操作或属性变化时会自动触发这里的代码。
        mainSplitPane.getDividers().get(1).positionProperty().addListener((o, ov, nv) -> persistMainSplitPaneState());
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void restoreMainSplitPaneState() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double left = clamp(preferences.getDouble(PREF_DIVIDER_LEFT, DEFAULT_LEFT_DIVIDER), 0.16, 0.52);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double right = onlinePanelExpanded
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                ? expandedDividerPosition(currentWindowWidth(), left)
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                : collapsedDividerPosition(currentWindowWidth(), left);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        setMainSplitPanePositions(left, right);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        applyOnlinePanelState(false);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void persistMainSplitPaneState() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (syncingOnlinePanelState || mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double[] positions = mainSplitPane.getDividerPositions();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double left = clamp(positions[0], 0.16, 0.52);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        preferences.putDouble(PREF_DIVIDER_LEFT, left);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (onlinePanelExpanded) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            double right = clamp(positions[1], left + 0.18, 0.90);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            preferences.putDouble(PREF_DIVIDER_RIGHT, right);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void syncOnlinePanelDivider() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mainSplitPane == null || mainSplitPane.getDividers().size() < 2) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double left = clamp(mainSplitPane.getDividerPositions()[0], 0.16, 0.52);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double targetRight = onlinePanelExpanded
                // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                ? expandedDividerPosition(currentWindowWidth(), left)
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                : collapsedDividerPosition(currentWindowWidth(), left);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double currentRight = mainSplitPane.getDividerPositions()[1];
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (Math.abs(currentRight - targetRight) > 0.002 || Math.abs(mainSplitPane.getDividerPositions()[0] - left) > 0.002) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            setMainSplitPanePositions(left, targetRight);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void setMainSplitPanePositions(double left, double right) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (mainSplitPane == null) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        syncingOnlinePanelState = true;
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        mainSplitPane.setDividerPositions(left, right);
        // 说明：把界面更新任务交回 JavaFX 主线程执行，避免后台线程直接改 UI。
        Platform.runLater(() -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (mainSplitPane != null) {
                // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                mainSplitPane.setDividerPositions(left, right);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            syncingOnlinePanelState = false;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private double expandedDividerPosition(double windowWidth, double leftDivider) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double width = Math.max(1120, windowWidth);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double preferredRight = preferences.getDouble(PREF_DIVIDER_RIGHT, DEFAULT_RIGHT_DIVIDER);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double maxRightForReadableSidebar = 1.0 - (EXPANDED_ONLINE_PANEL_PREF_WIDTH / width);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return clamp(Math.min(preferredRight, maxRightForReadableSidebar), leftDivider + 0.18, maxRightForReadableSidebar);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private double collapsedDividerPosition(double windowWidth, double leftDivider) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double width = Math.max(1120, windowWidth);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        double collapsedFraction = COLLAPSED_ONLINE_PANEL_WIDTH / width;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return clamp(1.0 - collapsedFraction, leftDivider + 0.18, 0.97);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void applyResponsiveLayout(double windowWidth) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (statusLabel != null) {
            // 说明：设置控件尺寸范围，控制界面布局时它能占用多大空间。
            statusLabel.setMaxWidth(clamp(windowWidth * 0.24, 220, 420));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!onlinePanelExpanded) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            syncOnlinePanelDivider();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private double currentWindowWidth() {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (playPauseButton != null && playPauseButton.getScene() != null) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return playPauseButton.getScene().getWidth();
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return 1320;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String trackNameKey(Track track) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String title = track == null || track.title() == null ? "" : track.title().trim().toLowerCase(Locale.ROOT);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String artist = track == null || track.artist() == null ? "" : track.artist().trim().toLowerCase(Locale.ROOT);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return title + "\u0000" + artist;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String trackArtistKey(Track track) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String artist = track == null || track.artist() == null ? "" : track.artist().trim().toLowerCase(Locale.ROOT);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return artist + "\u0000" + trackNameKey(track);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String trackFileNameKey(Track track) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (track == null || track.path() == null || track.path().getFileName() == null) return "";
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return track.path().getFileName().toString().trim().toLowerCase(Locale.ROOT);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private long trackCreatedTimeMillis(Track track) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (track == null || track.path() == null) return Long.MIN_VALUE;
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        String key = track.path().toAbsolutePath().normalize().toString();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Long cached = creationTimeCache.get(key);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (cached != null) return cached;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        long value = Long.MIN_VALUE;
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：定义一个类型；类、记录、接口、枚举都是组织程序逻辑和数据的基本单元。
            BasicFileAttributes attrs = Files.readAttributes(track.path(), BasicFileAttributes.class);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            value = attrs.creationTime().toMillis();
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException ignored) {
            // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
            try {
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                value = Files.getLastModifiedTime(track.path()).toMillis();
            // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
            } catch (IOException ignoredAgain) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                value = Long.MIN_VALUE;
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        creationTimeCache.put(key, value);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return value;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void showArtwork(String url) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        currentArtworkSource = url;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (artworkImageView == null) return;
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (url == null || url.isBlank()) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkImageView.setImage(null);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkImageView.setVisible(false);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!isRemoteUrl(url)) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkImageView.setImage(new Image(url, true));
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkImageView.setVisible(true);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
        Path cached = artworkCachePath(url);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (Files.isRegularFile(cached)) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkImageView.setImage(new Image(cached.toUri().toString(), true));
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            artworkImageView.setVisible(true);
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        artworkImageView.setImage(new Image(url, true));
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        artworkImageView.setVisible(true);
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        cacheArtworkAsync(url, cached);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void cacheArtworkAsync(String url, Path target) {
        // 说明：异步任务相关代码：把耗时操作放到后台执行，完成后再处理结果。
        CompletableFuture.runAsync(() -> {
            // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
            try {
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (Files.isRegularFile(target)) return;
                // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                        .timeout(java.time.Duration.ofSeconds(20))
                        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                        .header("User-Agent", "Mozilla/5.0")
                        // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                        .GET()
                        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                        .build();
                // 说明：HTTP 网络请求相关代码，用来访问在线歌词、封面或搜索接口。
                HttpResponse<byte[]> response = artworkHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (response.statusCode() < 200 || response.statusCode() >= 400) return;
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (!contentType.startsWith("image/")) return;
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                byte[] body = response.body();
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (body == null || body.length == 0) return;
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Files.createDirectories(target.getParent());
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Files.write(target, body);
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (Objects.equals(currentArtworkSource, url)) {
                    // 说明：把界面更新任务交回 JavaFX 主线程执行，避免后台线程直接改 UI。
                    Platform.runLater(() -> {
                        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                        if (Objects.equals(currentArtworkSource, url) && artworkImageView != null) {
                            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                            artworkImageView.setImage(new Image(target.toUri().toString(), true));
                            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                            artworkImageView.setVisible(true);
                        // 说明：代码块结束，表示前面的大括号范围到这里为止。
                        }
                    // 说明：代码块结束，表示前面的大括号范围到这里为止。
                    });
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
            } catch (Exception ignored) {
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        });
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static boolean isRemoteUrl(String url) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String lower = url.toLowerCase(Locale.ROOT);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return lower.startsWith("http://") || lower.startsWith("https://");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
    private static Path artworkCachePath(String url) { return ARTWORK_CACHE_DIR.resolve(sha1(url) + artworkExtension(url)); }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String artworkExtension(String url) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            String path = URI.create(url).getPath();
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (path != null) {
                // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                int dot = path.lastIndexOf('.');
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (dot >= 0 && dot < path.length() - 1) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    String ext = path.substring(dot).toLowerCase(Locale.ROOT);
                    // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                    if (ext.length() <= 8) return ext;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception ignored) {
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return ".img";
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private static String sha1(String value) {
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            StringBuilder sb = new StringBuilder(hash.length * 2);
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (byte b : hash) sb.append(String.format("%02x", b));
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return sb.toString();
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (Exception e) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return Integer.toHexString(value.hashCode());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void updateHighlightedLyric(Duration ct) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyricsAutoScrollLocked || previewingOnlineResult || currentLyrics == null || !currentLyrics.timed() || currentLyrics.lines().isEmpty()) return;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        List<LyricLine> lines = currentLyrics.lines();
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int sel = 0;
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int i = 0; i < lines.size(); i++) {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (lines.get(i).time().toMillis() <= Math.round(ct.toMillis())) sel = i;
            // 说明：否则分支：前面的条件都不成立时，执行这里的代码。
            else break;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (lyricsView.getSelectionModel().getSelectedIndex() != sel) {
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            lyricsView.getSelectionModel().select(sel);
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            lyricsView.scrollTo(Math.max(0, sel - 4));
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void showPlayerError(Throwable err) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String message = err == null ? "未知错误" : err.getMessage();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (isUnsupportedAudioError(err)) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            message = "当前文件编码不受支持，建议换成 mp3 / m4a";
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        statusLabel.setText("播放失败：" + message);
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        playPauseButton.setText("播放");
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        showLyrics(Lyrics.empty("当前文件无法播放或编码不受支持"));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private Path resolvePlayableMediaPath(Path source) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (source == null) return source;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String detectedFormat = sniffAudioFormat(source);
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (!"mp3".equals(detectedFormat) || !hasExtension(source, "m4a", "aac")) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return source;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try {
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            String cacheKey = sha1(source.toAbsolutePath().normalize() + ":" + Files.size(source) + ":" + Files.getLastModifiedTime(source).toMillis());
            // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
            Path target = PLAYBACK_CACHE_DIR.resolve(cacheKey + ".mp3");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (!Files.isRegularFile(target) || Files.size(target) != Files.size(source)) {
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Files.createDirectories(PLAYBACK_CACHE_DIR);
                // 说明：文件路径或文件系统操作，用来读取、保存或检查本地文件。
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：设置界面文字，让用户看到当前状态或提示信息。
            statusLabel.setText("检测到下载文件扩展名异常，已按 MP3 兼容播放");
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return target;
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException ignored) {
            // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
            return source;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private boolean looksLikeUnsupportedRawAac(Path path) {
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return "aac".equals(sniffAudioFormat(path));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private String sniffAudioFormat(Path path) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (path == null || !Files.isRegularFile(path)) return "unknown";
        // 说明：开始一段可能出错的代码，后面通常用 catch 捕获异常。
        try (InputStream in = Files.newInputStream(path)) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            byte[] header = in.readNBytes(512);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (header.length < 4) return "unknown";
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (containsAscii(header, "ftyp", 0, 64)) return "mp4";
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            int offset = skipId3Header(header);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (isMp3FrameHeader(header, offset)) return "mp3";
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (isAdtsHeader(header, offset)) return "aac";
        // 说明：异常处理分支：上面的 try 出错时，会进入这里处理问题。
        } catch (IOException ignored) {
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return "unknown";
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private int skipId3Header(byte[] header) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (header.length < 10 || header[0] != 'I' || header[1] != 'D' || header[2] != '3') return 0;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int size = (header[6] & 0x7f) << 21 | (header[7] & 0x7f) << 14 | (header[8] & 0x7f) << 7 | (header[9] & 0x7f);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return Math.min(header.length - 1, 10 + size);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private boolean isMp3FrameHeader(byte[] header, int offset) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (offset + 1 >= header.length) return false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int b0 = header[offset] & 0xff;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int b1 = header[offset + 1] & 0xff;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return b0 == 0xff && (b1 & 0xe0) == 0xe0 && (b1 & 0x06) != 0;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private boolean isAdtsHeader(byte[] header, int offset) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (offset + 1 >= header.length) return false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int b0 = header[offset] & 0xff;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int b1 = header[offset + 1] & 0xff;
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return b0 == 0xff && (b1 & 0xf6) == 0xf0;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private boolean containsAscii(byte[] header, String token, int start, int endExclusive) {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int end = Math.min(header.length - token.length() + 1, endExclusive);
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (int i = Math.max(0, start); i < end; i++) {
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            boolean matched = true;
            // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
            for (int j = 0; j < token.length(); j++) {
                // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
                if (header[i + j] != (byte) token.charAt(j)) {
                    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
                    matched = false;
                    // 说明：跳出当前循环或 switch 分支，不再继续执行后面的同级逻辑。
                    break;
                // 说明：代码块结束，表示前面的大括号范围到这里为止。
                }
            // 说明：代码块结束，表示前面的大括号范围到这里为止。
            }
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (matched) return true;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return false;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private boolean hasExtension(Path path, String... extensions) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (path == null || path.getFileName() == null) return false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        // 说明：循环结构：按规则重复执行代码，常用于遍历列表或数组。
        for (String extension : extensions) {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (fileName.endsWith("." + extension.toLowerCase(Locale.ROOT))) return true;
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return false;
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private boolean isUnsupportedAudioError(Throwable err) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (err == null) return false;
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String msg = (err.getMessage() == null ? "" : err.getMessage()).toLowerCase(Locale.ROOT);
        // 说明：返回结果并结束当前方法；调用者会拿到这里返回的值。
        return msg.contains("corrupted") || msg.contains("unsupported") || msg.contains("error_media_corrupted");
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ========== 在线搜索与预览 ==========

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void searchOnlineTracks() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        String q = onlineSearchField == null ? "" : onlineSearchField.getText();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (q == null || q.isBlank()) { onlineResults.clear(); statusLabel.setText("请输入在线搜索关键词"); return; }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        long reqId = ++onlineSearchRequestId; loadingOnlineSearch.setVisible(true); loadingOnlineSearch.setManaged(true);
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        statusLabel.setText("正在在线搜索：" + q.trim());
        // 说明：异步任务相关代码：把耗时操作放到后台执行，完成后再处理结果。
        onlineMusicSearchService.searchAsync(q).whenComplete((results, err) -> Platform.runLater(() -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (reqId != onlineSearchRequestId) return;
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            loadingOnlineSearch.setVisible(false); loadingOnlineSearch.setManaged(false);
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (err != null) { onlineResults.clear(); statusLabel.setText("在线搜索失败"); return; }
            // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
            onlineResults.setAll(results);
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            long downloadableCount = results.stream().filter(OnlineTrackInfo::canAttemptDownload).count();
            // 说明：设置界面文字，让用户看到当前状态或提示信息。
            statusLabel.setText(results.isEmpty()
                    // 说明：这一行是当前逻辑的一部分，用来配合上下文完成程序功能。
                    ? "没有找到在线结果"
                    // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
                    : "在线搜索完成，共 " + results.size() + " 条结果，可下载 " + downloadableCount + " 条");
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (!results.isEmpty()) onlineResultsView.getSelectionModel().select(0);
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void previewOnlineTrack(OnlineTrackInfo info) {
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (info == null) return; long reqId = ++onlinePreviewRequestId; previewingOnlineResult = true;
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        titleLabel.setText(info.title()); artistLabel.setText(info.subtitle());
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        showArtwork(info.artworkUrl()); showLyrics(Lyrics.empty("正在加载在线预览歌词..."));
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        statusLabel.setText("预览：" + info.title() + "（双击下载到本地播放）");
        // 说明：异步任务相关代码：把耗时操作放到后台执行，完成后再处理结果。
        onlineMusicSearchService.loadPreviewAsync(info).whenComplete((r, err) -> Platform.runLater(() -> {
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (reqId != onlinePreviewRequestId || onlineResultsView.getSelectionModel().getSelectedItem() != info) return;
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            previewingOnlineResult = true;
            // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
            if (err != null) { showLyrics(Lyrics.empty("在线预览加载失败")); return; }
            // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
            showArtwork(r.artworkUrl() == null || r.artworkUrl().isBlank() ? info.artworkUrl() : r.artworkUrl());
            // 说明：设置界面文字，让用户看到当前状态或提示信息。
            showLyrics(r.lyrics()); statusLabel.setText("预览：" + info.title());
        // 说明：代码块结束，表示前面的大括号范围到这里为止。
        }));
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // ========== 歌单管理 ==========

    // 说明：定义一个方法；方法把一段可复用的操作封装起来，调用时会执行里面的代码。
    private void removeSelectedTrack() {
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        Track sel = playlistView == null ? null : playlistView.getSelectionModel().getSelectedItem();
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (sel == null) { statusLabel.setText("请先选择要移除的歌曲"); return; }
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        int ri = tracks.indexOf(sel); boolean removingCurrent = sel == currentTrack;
        // 说明：执行一条普通语句；分号表示这条 Java 语句结束。
        tracks.remove(sel); database.removeTrack(sel);
        // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
        applyTrackFilter(searchField == null ? "" : searchField.getText());
        // 说明：条件判断：只有括号里的条件成立时，才会执行后面的代码块。
        if (removingCurrent) { cancelLyricRetry(); disposePlayer(); currentTrack = null; previewingOnlineResult = false; titleLabel.setText("未播放歌曲"); artistLabel.setText("当前歌曲已从歌单和缓存移除"); timeLabel.setText("00:00 / 00:00"); showArtwork(null); showLyrics(Lyrics.empty("当前歌曲已移除")); if (!tracks.isEmpty()) { int ni = Math.min(ri, tracks.size() - 1); Track nt = tracks.get(ni); if (filteredTracks.contains(nt)) playlistView.getSelectionModel().select(nt); else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(0); } }
        // 说明：追加条件判断：前面的条件不成立时，再检查这个新条件。
        else if (!filteredTracks.isEmpty()) playlistView.getSelectionModel().select(Math.min(ri, filteredTracks.size() - 1));
        // 说明：设置界面文字，让用户看到当前状态或提示信息。
        statusLabel.setText("已移除：" + sel);
    // 说明：代码块结束，表示前面的大括号范围到这里为止。
    }

    // 说明：赋值或初始化语句：把右边计算出的结果保存到左边的变量或字段中。
    private void disposePlayer() { lyricsRequestId++; if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; } }
// 说明：代码块结束，表示前面的大括号范围到这里为止。
}
