package app.musicplayer.android;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import app.musicplayer.android.data.AndroidLibraryImporter;
import app.musicplayer.android.data.AndroidMusicDatabase;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.android.ui.AndroidLyricsPresenter;
import app.musicplayer.android.ui.AndroidOnlineTasks;
import app.musicplayer.android.ui.LocalTrackList;
import app.musicplayer.android.ui.OnlineTrackAdapter;
import app.musicplayer.android.ui.TrackAdapter;
import app.musicplayer.android.ui.VerticalVolumeView;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.model.PlayMode;
import app.musicplayer.online.OnlineMusicSearchService;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SortDirection;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Android 主页面与生命周期。绑定资源布局，连接播放服务，处理曲库列表、权限、在线操作及页面切换，委托歌词和下载保存组件。 */
public final class MainActivity extends AppCompatActivity {
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<TrackEntry> tracks = new ArrayList<>();

    private AndroidMusicDatabase database;
    private OnlineMusicSearchService onlineService;
    private AndroidLyricsService lyricsService;
    private app.musicplayer.android.data.DownloadedTrackPublisher downloadedTrackPublisher;
    private AndroidLyricsController lyricsController;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean uiVisible;
    private AndroidLyricsPresenter lyricsPresenter;
    private final java.util.concurrent.ExecutorService libraryExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private int libraryRequestId;
    private File privateMusicDir;
    private File onlineTempDir;
    private ActivityResultLauncher<String[]> importLauncher;
    private ActivityResultLauncher<Intent> manageStorageLauncher;
    private ActivityResultLauncher<String> legacyStorageLauncher;
    private OnlineTrackInfo pendingStorageDownload;

    private TrackAdapter trackAdapter;
    private LocalTrackList localTrackList;
    private OnlineTrackAdapter onlineAdapter;
    private AndroidOnlineTasks onlineTasks;
    private TrackEntry currentTrack;
    private Lyrics currentLyrics = Lyrics.empty("导入歌曲后开始播放");
    private PlayMode playMode = PlayMode.ORDER;
    private boolean seeking;

    private View playlistPage;
    private View lyricsPage;
    private View onlinePage;
    private TextView statusText;
    private TextView titleText;
    private TextView artistText;
    private TextView lyricsText;
    private TextView currentTimeText;
    private TextView durationText;
    private TextView volumePercentText;
    private ImageView artworkView;
    private ScrollView lyricsScroll;
    private EditText localSearch;
    private EditText onlineSearch;
    private Spinner sortType;
    private Spinner sortDirection;
    private SeekBar progressBar;
    private VerticalVolumeView verticalVolume;
    private ImageButton playButton;
    private ImageButton modeButton;
    private ImageButton volumeButton;
    private ImageButton muteButton;
    private ImageButton refreshButton;
    private ObjectAnimator refreshSpin;
    private int previewRequestId;
    private int playbackRequestId;
    private Button removeButton;
    private BottomNavigationView bottomNavigation;
    private View volumeDrawer;
    private View volumeScrim;
    private float lastAudibleVolume = 0.7f;
    private boolean volumeDrawerOpen;

    /** 创建页面或服务，初始化控件、持久化和生命周期依赖。 */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        File externalMusicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        privateMusicDir =
                new File(externalMusicDir == null ? getFilesDir() : externalMusicDir, "downloads");
        onlineTempDir = new File(getCacheDir(), "online-downloads");
        ensureDirectory(privateMusicDir);
        ensureDirectory(onlineTempDir);
        database = new AndroidMusicDatabase(this);
        onlineService = new OnlineMusicSearchService();
        lyricsService = new AndroidLyricsService();
        downloadedTrackPublisher =
                new app.musicplayer.android.data.DownloadedTrackPublisher(this, privateMusicDir);
        lyricsController =
                new AndroidLyricsController(
                        database,
                        lyricsService::searchOnlineAsync,
                        libraryExecutor,
                        this::runOnUiThread,
                        () -> player == null ? 0 : player.getDuration(),
                        new AndroidLyricsController.Callbacks(
                                this::setRefreshLoading,
                                lyrics -> {
                                    currentLyrics = lyrics;
                                    renderLyrics();
                                },
                                this::loadArtwork,
                                this::showStatus));

        bindViews();
        lyricsPresenter = new AndroidLyricsPresenter(lyricsText, lyricsScroll);
        lyricsPresenter.show(currentLyrics);
        configureWindowInsets();
        applyResponsiveArtworkSize();
        configureStorageAccess();
        configureLists();
        configureImport();
        configureActions();
        connectPlayer();
        reloadTracks();
        bottomNavigation.setSelectedItemId(R.id.nav_lyrics);
    }

    /** 通过 XML 资源 ID 取得主页面控件。 */
    private void bindViews() {
        playlistPage = findViewById(R.id.playlistPage);
        lyricsPage = findViewById(R.id.lyricsPage);
        onlinePage = findViewById(R.id.onlinePage);
        statusText = findViewById(R.id.statusText);
        titleText = findViewById(R.id.titleText);
        artistText = findViewById(R.id.artistText);
        lyricsText = findViewById(R.id.lyricsText);
        currentTimeText = findViewById(R.id.currentTimeText);
        durationText = findViewById(R.id.durationText);
        volumePercentText = findViewById(R.id.volumePercentText);
        artworkView = findViewById(R.id.artworkView);
        artworkView.setClipToOutline(true);
        lyricsScroll = findViewById(R.id.lyricsScroll);
        localSearch = findViewById(R.id.localSearch);
        onlineSearch = findViewById(R.id.onlineSearch);
        sortType = findViewById(R.id.sortType);
        sortDirection = findViewById(R.id.sortDirection);
        progressBar = findViewById(R.id.progressBar);
        verticalVolume = findViewById(R.id.verticalVolume);
        playButton = findViewById(R.id.playButton);
        modeButton = findViewById(R.id.modeButton);
        volumeButton = findViewById(R.id.volumeButton);
        muteButton = findViewById(R.id.muteButton);
        refreshButton = findViewById(R.id.refreshLyricsButton);
        removeButton = findViewById(R.id.removeButton);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        volumeDrawer = findViewById(R.id.volumeDrawer);
        volumeScrim = findViewById(R.id.volumeScrim);
    }

    /** 按系统栏及窗口边距调整页面，避免控件被遮挡。 */
    private void configureWindowInsets() {
        View root = findViewById(R.id.rootView);
        View appContent = findViewById(R.id.appContent);
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, windowInsets) -> {
                    Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                    appContent.setPadding(0, bars.top, 0, bars.bottom);
                    FrameLayout.LayoutParams drawerParams =
                            (FrameLayout.LayoutParams) volumeDrawer.getLayoutParams();
                    drawerParams.bottomMargin = dp(112) + bars.bottom;
                    volumeDrawer.setLayoutParams(drawerParams);
                    return windowInsets;
                });
        ViewCompat.requestApplyInsets(root);
    }

    /** 根据窗口或设备的可用尺寸调整布局或封面大小。 */
    private void applyResponsiveArtworkSize() {
        float density = getResources().getDisplayMetrics().density;
        float widthDp = getResources().getDisplayMetrics().widthPixels / density;
        float heightDp = getResources().getDisplayMetrics().heightPixels / density;
        int artworkDp = Math.round(Math.max(148f, Math.min(224f, widthDp * 0.54f)));
        if (heightDp < 700f) artworkDp = Math.min(artworkDp, 160);
        artworkView.getLayoutParams().width = dp(artworkDp);
        artworkView.getLayoutParams().height = dp(artworkDp);
        artworkView.requestLayout();
    }

    /** 创建曲库及在线结果适配器，并连接选择和播放事件。 */
    private void configureLists() {
        RecyclerView playlistView = findViewById(R.id.playlistView);
        playlistView.setLayoutManager(new LinearLayoutManager(this));
        trackAdapter =
                new TrackAdapter(
                        entry -> {
                            updateRemoveButton();
                            playTrack(entry);
                        });
        playlistView.setAdapter(trackAdapter);
        localTrackList =
                new LocalTrackList(
                        this::runOnUiThread,
                        rows -> trackAdapter.submit(rows, this::updateRemoveButton));

        RecyclerView onlineResults = findViewById(R.id.onlineResults);
        onlineResults.setLayoutManager(new LinearLayoutManager(this));
        onlineAdapter =
                new OnlineTrackAdapter(
                        info -> {
                            onlineTasks.selectionChanged();
                            previewOnlineTrack(info);
                        });
        onlineResults.setAdapter(onlineAdapter);
        onlineTasks =
                new AndroidOnlineTasks(
                        onlineService,
                        this::runOnUiThread,
                        new AndroidOnlineTasks.Controls(
                                onlineSearch,
                                findViewById(R.id.onlineSearchButton),
                                findViewById(R.id.downloadButton),
                                onlineAdapter),
                        this::showStatus,
                        () -> previewRequestId++);

        ArrayAdapter<PlaylistSort> sortAdapter =
                new ArrayAdapter<>(this, R.layout.item_spinner, PlaylistSort.values());
        sortAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        sortType.setAdapter(sortAdapter);
        ArrayAdapter<SortDirection> directionAdapter =
                new ArrayAdapter<>(this, R.layout.item_spinner, SortDirection.values());
        directionAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        sortDirection.setAdapter(directionAdapter);
        sortType.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refreshTrackList));
        sortDirection.setOnItemSelectedListener(
                new SimpleItemSelectedListener(this::refreshTrackList));

        localSearch.addTextChangedListener(
                new SimpleTextWatcher(
                        () -> localTrackList.search(localSearch.getText().toString())));
    }

    private final ActivityResultLauncher<Intent> namedPlaylistLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(), result -> reloadTracks());
    private final java.util.function.Consumer<String> playlistDownloadListener =
            id -> {
                if (!isDestroyed() && uiVisible) reloadTracks();
            };

    /** 连接按钮、输入和导航的用户操作。 */
    private void configureActions() {
        findViewById(R.id.namedPlaylistsButton)
                .setOnClickListener(
                        view ->
                                namedPlaylistLauncher.launch(
                                        new Intent(this, PlaylistActivity.class)));
        bottomNavigation.setOnItemSelectedListener(
                item -> {
                    if (item.getItemId() == R.id.nav_playlist) showPage(playlistPage);
                    else if (item.getItemId() == R.id.nav_online) showPage(onlinePage);
                    else showPage(lyricsPage);
                    return true;
                });
        findViewById(R.id.importButton)
                .setOnClickListener(view -> importLauncher.launch(new String[] {"audio/*"}));
        removeButton.setOnClickListener(view -> confirmSelectedTrackRemoval());
        findViewById(R.id.onlineSearchButton)
                .setOnClickListener(view -> onlineTasks.toggleSearch());
        findViewById(R.id.downloadButton).setOnClickListener(view -> downloadSelectedOnlineTrack());
        playButton.setOnClickListener(view -> togglePlayback());
        findViewById(R.id.previousButton).setOnClickListener(view -> playRelative(-1));
        findViewById(R.id.nextButton).setOnClickListener(view -> playRelative(1));
        modeButton.setOnClickListener(view -> cyclePlayMode());
        volumeButton.setOnClickListener(view -> toggleVolumeDrawer());
        muteButton.setOnClickListener(view -> toggleMute());
        volumeScrim.setOnClickListener(view -> hideVolumeDrawer(true));
        refreshButton.setOnClickListener(view -> forceRefreshLyrics());
        refreshSpin = ObjectAnimator.ofFloat(refreshButton, View.ROTATION, 0f, 360f);
        refreshSpin.setDuration(900);
        refreshSpin.setRepeatCount(ObjectAnimator.INFINITE);
        refreshSpin.setInterpolator(new LinearInterpolator());
        onlineSearch.setOnEditorActionListener(
                (view, actionId, event) -> {
                    if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
                    searchOnline();
                    return true;
                });
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            /** 处理返回动作，按当前页面状态关闭抽屉或退出页面。 */
                            @Override
                            public void handleOnBackPressed() {
                                if (volumeDrawerOpen) {
                                    hideVolumeDrawer(true);
                                    return;
                                }
                                setEnabled(false);
                                getOnBackPressedDispatcher().onBackPressed();
                            }
                        });
    }

    /** 注册 Android 文档选择结果入口，交给批量导入器处理。 */
    private void configureImport() {
        importLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.OpenMultipleDocuments(), this::importUris);
    }

    /** 注册存储权限或设置页返回结果，恢复等待中的下载。 */
    private void configureStorageAccess() {
        manageStorageLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        result -> resumePendingStorageDownload());
        legacyStorageLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> resumePendingStorageDownload());
    }

    /** 权限界面返回后继续先前等待的单曲下载。 */
    private void resumePendingStorageDownload() {
        OnlineTrackInfo selected = pendingStorageDownload;
        pendingStorageDownload = null;
        if (selected != null) {
            beginOnlineDownload(selected, canWriteRootMusicDirectory());
        }
    }

    /** 异步连接后台 MediaSession，完成后同步队列和控件。 */
    private void connectPlayer() {
        controllerFuture =
                new MediaController.Builder(
                                this,
                                new SessionToken(
                                        this, new ComponentName(this, PlaybackService.class)))
                        .buildAsync();
        controllerFuture.addListener(
                () -> {
                    if (isDestroyed()) return;
                    try {
                        player = controllerFuture.get();
                        configurePlayer();
                        playMode =
                                player.getShuffleModeEnabled()
                                        ? PlayMode.SHUFFLE
                                        : player.getRepeatMode() == Player.REPEAT_MODE_ONE
                                                ? PlayMode.REPEAT_ONE
                                                : PlayMode.ORDER;
                        updatePlayModeUi();
                        syncCurrentTrack();
                    } catch (Exception error) {
                        showStatus("无法连接播放服务：" + rootMessage(error));
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    /** 将持久化歌曲记录转换为 Media3 可播放条目。 */
    private MediaItem mediaItem(TrackEntry entry) {
        return new MediaItem.Builder()
                .setMediaId(entry.key())
                .setUri(playbackUri(entry))
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(entry.track().title())
                                .setArtist(entry.track().artist())
                                .build())
                .build();
    }

    /** 从服务的当前媒体条目恢复页面显示的歌曲。 */
    private void syncCurrentTrack() {
        if (!uiVisible || player == null || player.getCurrentMediaItem() == null) return;
        String key = player.getCurrentMediaItem().mediaId;
        for (TrackEntry entry : tracks) {
            if (entry.key().equals(key)
                    && (currentTrack == null || !entry.key().equals(currentTrack.key()))) {
                previewRequestId++;
                playbackRequestId++;
                currentTrack = entry;
                displayTrack(entry);
                break;
            }
        }
        playButton.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    /** 把曲库或已保存队列与后台播放服务同步。 */
    private void syncPlaybackQueue() {
        if (player == null
                || player.getMediaItemCount() == 0
                || player.getCurrentMediaItem() == null) return;
        for (int i = 0; i < player.getMediaItemCount(); i++) {
            Bundle extras = player.getMediaItemAt(i).mediaMetadata.extras;
            if (extras != null && extras.getBoolean("namedPlaylistQueue", false)) return;
        }
        boolean equal = player.getMediaItemCount() == tracks.size();
        for (int i = 0; equal && i < tracks.size(); i++)
            equal = player.getMediaItemAt(i).mediaId.equals(tracks.get(i).key());
        if (equal) return;
        int index = -1;
        String playingKey = player.getCurrentMediaItem().mediaId;
        for (int i = 0; i < tracks.size(); i++)
            if (tracks.get(i).key().equals(playingKey)) {
                index = i;
                break;
            }
        if (index >= 0) {
            long position = player.getCurrentPosition();
            boolean prepared = player.getPlaybackState() != Player.STATE_IDLE;
            player.setMediaItems(tracks.stream().map(this::mediaItem).toList(), index, position);
            if (prepared) player.prepare();
        }
    }

    /** 连接播放器事件、进度、音量及控制按钮。 */
    private void configurePlayer() {
        verticalVolume.setProgress(Math.round(player.getVolume() * 100));
        updateVolumeUi(Math.round(player.getVolume() * 100));
        player.addListener(
                new Player.Listener() {
                    /** 将播放器的实际播放状态同步到界面。 */
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        playButton.setImageResource(
                                isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                        playButton.setContentDescription(
                                getString(isPlaying ? R.string.pause : R.string.play));
                    }

                    /** 切换媒体条目时同步当前歌曲及歌词。 */
                    @Override
                    public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
                        syncCurrentTrack();
                    }

                    /** 处理实际播放器错误并提供可显示的原因。 */
                    @Override
                    public void onPlayerError(androidx.media3.common.PlaybackException error) {
                        showStatus("播放失败：" + error.getErrorCodeName());
                    }

                    /** 将播放器或音量控件的变化同步到对应界面。 */
                    @Override
                    public void onVolumeChanged(float volume) {
                        int percent = Math.round(volume * 100f);
                        if (percent > 0) lastAudibleVolume = volume;
                        verticalVolume.setProgress(percent);
                        updateVolumeUi(percent);
                    }
                });

        progressBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    /** 更新用户操作的进度或音量，按控件类型分发处理。 */
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar, int progress, boolean fromUser) {}

                    /** 标记滑块拖动开始或结束，避免自动刷新覆盖手动位置。 */
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        seeking = true;
                    }

                    /** 标记滑块拖动开始或结束，避免自动刷新覆盖手动位置。 */
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        long duration = player == null ? 0 : player.getDuration();
                        if (duration > 0) {
                            long target = duration * seekBar.getProgress() / seekBar.getMax();
                            player.seekTo(target);
                            lyricsPresenter.seek(target);
                        }
                        seeking = false;
                    }
                });
        verticalVolume.setOnVolumeChangedListener(
                (progress, fromUser) -> {
                    if (!fromUser) return;
                    float volume = progress / 100f;
                    if (volume > 0f) lastAudibleVolume = volume;
                    player.setVolume(volume);
                    updateVolumeUi(progress);
                });
    }

    /** 切换竖向音量面板的显示状态。 */
    private void toggleVolumeDrawer() {
        if (volumeDrawerOpen) hideVolumeDrawer(true);
        else showVolumeDrawer();
    }

    /** 显示或收起音量面板，并按参数处理动画。 */
    private void showVolumeDrawer() {
        volumeDrawerOpen = true;
        volumeScrim.animate().cancel();
        volumeDrawer.animate().cancel();
        volumeScrim.setAlpha(0f);
        volumeScrim.setVisibility(View.VISIBLE);
        volumeDrawer.setAlpha(0f);
        volumeDrawer.setTranslationY(dp(24));
        volumeDrawer.setVisibility(View.VISIBLE);
        volumeScrim.animate().alpha(1f).setDuration(180).start();
        volumeDrawer.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    /** 显示或收起音量面板，并按参数处理动画。 */
    private void hideVolumeDrawer(boolean animated) {
        if (!volumeDrawerOpen && volumeDrawer.getVisibility() != View.VISIBLE) return;
        volumeDrawerOpen = false;
        volumeScrim.animate().cancel();
        volumeDrawer.animate().cancel();
        if (!animated) {
            volumeScrim.setVisibility(View.GONE);
            volumeDrawer.setVisibility(View.GONE);
            return;
        }
        volumeScrim
                .animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> volumeScrim.setVisibility(View.GONE))
                .start();
        volumeDrawer
                .animate()
                .alpha(0f)
                .translationY(dp(24))
                .setDuration(150)
                .withEndAction(() -> volumeDrawer.setVisibility(View.GONE))
                .start();
    }

    /** 切换静音，保留恢复时需要的原音量。 */
    private void toggleMute() {
        if (player == null) return;
        if (player.getVolume() > 0f) {
            lastAudibleVolume = player.getVolume();
            player.setVolume(0f);
        } else {
            player.setVolume(Math.max(0.05f, lastAudibleVolume));
        }
    }

    /** 把实际音量百分比同步到图标、滑块和文字。 */
    private void updateVolumeUi(int percent) {
        volumePercentText.setText(getString(R.string.volume_percent, percent));
        int icon = percent == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume;
        volumeButton.setImageResource(icon);
        muteButton.setImageResource(icon);
        muteButton.setContentDescription(getString(percent == 0 ? R.string.unmute : R.string.mute));
    }

    private AndroidLibraryImporter importer;
    private int importRequestId;

    /** 把文档选择的 URI 列表交给串行批量导入任务。 */
    private void importUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        if (importer == null)
            importer =
                    new AndroidLibraryImporter(
                            getApplicationContext(),
                            privateMusicDir,
                            database,
                            libraryExecutor,
                            this::runOnUiThread);
        int request = ++importRequestId;
        showStatus("正在导入 " + uris.size() + " 首歌曲");
        importer.submit(
                uris,
                progress -> {
                    if (isDestroyed()) return;
                    if (progress.finished() && progress.success() > 0) reloadTracks();
                    if (request != importRequestId) return;
                    int failed = progress.processed() - progress.success() - progress.duplicates();
                    String message =
                            (progress.finished()
                                            ? "导入完成"
                                            : "导入 " + progress.processed() + "/" + progress.total())
                                    + "：成功 "
                                    + progress.success()
                                    + "，重复 "
                                    + progress.duplicates()
                                    + "，失败 "
                                    + failed;
                    if (!progress.failures().isEmpty())
                        message += "（" + progress.failures().get(0).detail() + "）";
                    showStatus(message);
                    if (progress.finished()) bottomNavigation.setSelectedItemId(R.id.nav_playlist);
                });
    }

    /** 在后台重新读取曲库，完成后刷新列表及可选的后续操作。 */
    private void reloadTracks() {
        reloadTracks(() -> {});
    }

    /** 在后台重新读取曲库，完成后刷新列表及可选的后续操作。 */
    private void reloadTracks(Runnable afterLoad) {
        int request = ++libraryRequestId;
        CompletableFuture.supplyAsync(database::loadTracks, libraryExecutor)
                .whenComplete(
                        (loaded, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (isDestroyed() || request != libraryRequestId)
                                                return;
                                            if (error != null) {
                                                showStatus("读取曲库失败：" + rootMessage(error));
                                                return;
                                            }
                                            tracks.clear();
                                            tracks.addAll(loaded);
                                            syncCurrentTrack();
                                            refreshTrackList();
                                            afterLoad.run();
                                        }));
    }

    /** 按当前搜索和排序规则刷新曲库的显示快照。 */
    private void refreshTrackList() {
        if (trackAdapter == null) return;
        String query = localSearch == null ? "" : localSearch.getText().toString();
        PlaylistSort selectedSort =
                sortType != null && sortType.getSelectedItem() instanceof PlaylistSort value
                        ? value
                        : PlaylistSort.TITLE;
        SortDirection direction =
                sortDirection != null
                                && sortDirection.getSelectedItem() instanceof SortDirection value
                        ? value
                        : SortDirection.ASCENDING;
        List<TrackEntry> ordered = localTrackList.replace(tracks, selectedSort, direction, query);
        tracks.clear();
        tracks.addAll(ordered);
        syncPlaybackQueue();
        updateRemoveButton();
    }

    /** 播放指定歌曲或队列位置，并开始对应的歌词与封面加载。 */
    private void playTrack(TrackEntry entry) {
        if (player == null) {
            showStatus("播放服务连接中，请稍后重试");
            return;
        }
        previewRequestId++;
        playbackRequestId++;
        currentTrack = entry;
        int index = indexOfCurrent();
        if (index < 0) return;
        player.setMediaItems(tracks.stream().map(this::mediaItem).toList(), index, 0);
        player.prepare();
        player.play();
        bottomNavigation.setSelectedItemId(R.id.nav_lyrics);
        displayTrack(entry);
    }

    /** 更新当前歌曲名称、歌手及播放标记。 */
    private void displayTrack(TrackEntry entry) {
        titleText.setText(entry.track().title());
        artistText.setText(entry.track().artist());
        loadEmbeddedArtwork(entry);
        loadLyrics(entry);
        showStatus("正在播放：" + entry.track().title());
    }

    /** 把当前歌曲交给 AndroidLyricsController，按缓存、旁车和在线顺序加载。 */
    private void loadLyrics(TrackEntry entry) {
        lyricsController.load(entry);
    }

    /** 走共享歌词渠道（网易云 → QQ → 酷狗 → LRCLIB）在线查词，与下载来源完全解耦。 */

    /** 强制刷新：跳过数据库缓存重新在线查词，成功后覆盖缓存。 */
    private void forceRefreshLyrics() {
        if (currentTrack == null) {
            showStatus("请先播放歌曲再刷新歌词");
            return;
        }
        lyricsController.refresh(currentTrack);
    }

    /** 同步歌词刷新按钮、透明度及旋转动画。 */
    private void setRefreshLoading(boolean loading) {
        refreshButton.setEnabled(!loading);
        refreshButton.setAlpha(loading ? 0.6f : 1f);
        if (loading) {
            refreshSpin.start();
        } else {
            refreshSpin.cancel();
            refreshButton.setRotation(0f);
        }
    }

    /** FILE 类型的歌曲支持同目录同名 .lrc 旁车文件；MediaStore 歌曲跳过。 */
    private void loadEmbeddedArtwork(TrackEntry entry) {
        int request = previewRequestId;
        showArtworkPlaceholder();
        CompletableFuture.supplyAsync(
                        () -> {
                            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                            try {
                                if (entry.storageType() == TrackEntry.StorageType.MEDIA_STORE)
                                    retriever.setDataSource(this, Uri.parse(entry.location()));
                                else retriever.setDataSource(entry.location());
                                return retriever.getEmbeddedPicture();
                            } catch (RuntimeException ignored) {
                                return null;
                            } finally {
                                try {
                                    retriever.release();
                                } catch (IOException ignored) {
                                }
                            }
                        },
                        libraryExecutor)
                .whenComplete(
                        (picture, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (isDestroyed()
                                                    || request != previewRequestId
                                                    || currentTrack == null
                                                    || !currentTrack.key().equals(entry.key()))
                                                return;
                                            if (picture != null) loadArtwork(picture);
                                        }));
    }

    /** 加载指定封面，空地址或失败时使用现有占位规则。 */
    private void loadArtwork(Object source) {
        artworkView.setPadding(0, 0, 0, 0);
        ImageViewCompat.setImageTintList(artworkView, null);
        Glide.with(this).load(source).centerCrop().into(artworkView);
    }

    /** 显示默认封面占位，清除上一首歌曲的图像。 */
    private void showArtworkPlaceholder() {
        Glide.with(this).clear(artworkView);
        int padding = dp(48);
        artworkView.setPadding(padding, padding, padding, padding);
        ImageViewCompat.setImageTintList(
                artworkView, ColorStateList.valueOf(getColor(R.color.text_muted)));
        artworkView.setImageResource(R.drawable.ic_music_note);
    }

    /** 从当前搜索输入启动在线查询，结果由专用协调器更新。 */
    private void searchOnline() {
        onlineTasks.search();
    }

    /** 显示选中在线歌曲的歌词与封面，先使旧歌词请求失效。 */
    private void previewOnlineTrack(OnlineTrackInfo info) {
        int request = ++previewRequestId;
        lyricsController.invalidate();
        setRefreshLoading(false);
        titleText.setText(info.title());
        artistText.setText(info.artist());
        if (!TextUtils.isEmpty(info.artworkUrl())) loadArtwork(info.artworkUrl());
        else showArtworkPlaceholder();
        onlineService
                .preview(info)
                .result()
                .whenComplete(
                        (lookup, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (isDestroyed() || request != previewRequestId)
                                                return;
                                            currentLyrics =
                                                    lookup == null || error != null
                                                            ? Lyrics.empty("在线结果暂无歌词")
                                                            : lookup.lyrics();
                                            renderLyrics();
                                        }));
    }

    /** 检查在线选择和存储权限，再开始下载选中歌曲。 */
    private void downloadSelectedOnlineTrack() {
        if (onlineTasks.cancelSelectedDownload()) return;
        OnlineTrackInfo selected = onlineAdapter.selected();
        if (selected == null) {
            showStatus("请先选择在线歌曲");
            return;
        }
        if (canWriteRootMusicDirectory()) {
            beginOnlineDownload(selected, true);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestAllFilesAccess(selected);
            return;
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            pendingStorageDownload = selected;
            legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            return;
        }
        beginOnlineDownload(selected, false);
    }

    /** 启动单曲下载，后台保存及登记完成后刷新曲库并播放。 */
    private void beginOnlineDownload(OnlineTrackInfo selected, boolean useRootDirectory) {
        int request = ++playbackRequestId;
        onlineTasks.download(
                selected,
                onlineTempDir.toPath().resolve(useRootDirectory ? "public" : "media"),
                path ->
                        CompletableFuture.supplyAsync(
                                () -> {
                                    try {
                                        TrackEntry entry =
                                                downloadedTrackPublisher.publish(
                                                        path.toFile(), selected, useRootDirectory);
                                        database.saveTrack(entry);
                                        return entry;
                                    } catch (IOException error) {
                                        throw new CompletionException(error);
                                    } finally {
                                        try {
                                            Files.deleteIfExists(path);
                                        } catch (IOException ignored) {
                                        }
                                    }
                                },
                                libraryExecutor),
                entry ->
                        reloadTracks(
                                () -> {
                                    if (request == playbackRequestId) {
                                        playTrack(entry);
                                        showStatus("下载完成并开始播放");
                                    } else showStatus("下载完成，已加入曲库");
                                }));
    }

    /** 为保存到指定公共目录申请对应版本的存储权限。 */
    @RequiresApi(Build.VERSION_CODES.R)
    private void requestAllFilesAccess(OnlineTrackInfo selected) {
        pendingStorageDownload = selected;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.storage_permission_title)
                .setMessage(R.string.storage_permission_message)
                .setNegativeButton(
                        R.string.storage_permission_fallback,
                        (dialog, which) -> {
                            pendingStorageDownload = null;
                            beginOnlineDownload(selected, false);
                        })
                .setPositiveButton(
                        R.string.storage_permission_action,
                        (dialog, which) -> launchAllFilesSettings())
                .setOnCancelListener(dialog -> pendingStorageDownload = null)
                .show();
    }

    /** 打开 Android 全文件访问设置页，处理不可用的设置入口。 */
    @RequiresApi(Build.VERSION_CODES.R)
    private void launchAllFilesSettings() {
        Intent appSettings =
                new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
        try {
            manageStorageLauncher.launch(appSettings);
        } catch (ActivityNotFoundException firstError) {
            try {
                manageStorageLauncher.launch(
                        new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException secondError) {
                resumePendingStorageDownload();
            }
        }
    }

    /** 按 Android 版本及授权判断能否直接写公共 music 目录。 */
    private boolean canWriteRootMusicDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(
                                this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED;
    }

    /** 按 FILE 或 MediaStore 存储类型取得可交给播放器的位置。 */
    private Uri playbackUri(TrackEntry entry) {
        return entry.storageType() == TrackEntry.StorageType.MEDIA_STORE
                ? Uri.parse(entry.location())
                : Uri.fromFile(Paths.get(entry.location()).toFile());
    }

    /** 按记录的存储类型删除受管理音频位置。 */
    private boolean deleteStoredTrack(TrackEntry entry) {
        try {
            if (entry.storageType() == TrackEntry.StorageType.MEDIA_STORE) {
                return getContentResolver().delete(Uri.parse(entry.location()), null, null) > 0;
            }
            return Files.deleteIfExists(Paths.get(entry.location()));
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    /** 显示移除确认，确认后交给曲库删除流程。 */
    private void confirmSelectedTrackRemoval() {
        TrackEntry selected = trackAdapter.selected();
        if (selected == null) {
            showStatus("请先在歌单中选择歌曲");
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remove_confirm_title)
                .setMessage(getString(R.string.remove_confirm_message, selected.track().title()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.confirm_remove, (dialog, which) -> removeSelectedTrack())
                .show();
    }

    /** 删除选中歌曲记录并刷新选择和播放状态。 */
    private void removeSelectedTrack() {
        TrackEntry selected = trackAdapter.selected();
        if (selected == null) {
            showStatus("请先在歌单中选择歌曲");
            return;
        }
        if (currentTrack != null && currentTrack.key().equals(selected.key())) {
            if (player != null) {
                player.stop();
                player.clearMediaItems();
            }
            currentTrack = null;
        }
        CompletableFuture.supplyAsync(
                        () -> {
                            database.removeTrack(selected);
                            return deleteStoredTrack(selected);
                        },
                        libraryExecutor)
                .whenComplete(
                        (deleted, error) ->
                                runOnUiThread(
                                        () -> {
                                            if (isDestroyed()) return;
                                            if (error != null) {
                                                showStatus("移除失败：" + rootMessage(error));
                                                return;
                                            }
                                            reloadTracks();
                                            showStatus(
                                                    deleted
                                                            ? "已从歌单和本地文件中删除"
                                                            : "已移除歌单记录，文件可能已在外部删除");
                                        }));
    }

    /** 根据当前选择和可删除状态更新移除按钮。 */
    private void updateRemoveButton() {
        if (removeButton != null && trackAdapter != null) {
            removeButton.setEnabled(trackAdapter.selected() != null);
        }
    }

    /** 在播放与暂停之间切换；尚无播放器时从有效选择开始播放。 */
    private void togglePlayback() {
        if (player == null) return;
        if (currentTrack == null) {
            if (!tracks.isEmpty()) playTrack(tracks.get(0));
            return;
        }
        if (player.isPlaying()) player.pause();
        else player.play();
    }

    /** 按上一首或下一首方向选择队列条目，保留首尾回绕规则。 */
    private void playRelative(int direction) {
        if (player == null || tracks.isEmpty()) return;
        if (player.getMediaItemCount() == 0) {
            playTrack(tracks.get(0));
            return;
        }
        previewRequestId++;
        playbackRequestId++;
        if (direction > 0) player.seekToNextMediaItem();
        else player.seekToPreviousMediaItem();
        player.play();
    }

    /** 取得当前歌曲在播放队列中的位置，必要时使用有效选择作为起点。 */
    private int indexOfCurrent() {
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).key().equals(currentTrack.key())) return index;
        }
        return -1;
    }

    /** 按现有顺序切换播放模式并同步到服务。 */
    private void cyclePlayMode() {
        if (player == null) return;
        playMode =
                switch (playMode) {
                    case ORDER -> PlayMode.SHUFFLE;
                    case SHUFFLE -> PlayMode.REPEAT_ONE;
                    case REPEAT_ONE -> PlayMode.ORDER;
                };
        player.setShuffleModeEnabled(playMode == PlayMode.SHUFFLE);
        player.setRepeatMode(
                playMode == PlayMode.REPEAT_ONE ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_ALL);
        updatePlayModeUi();
    }

    /** 把实际播放模式同步到按钮文字或图标。 */
    private void updatePlayModeUi() {
        int icon =
                switch (playMode) {
                    case ORDER -> R.drawable.ic_repeat;
                    case SHUFFLE -> R.drawable.ic_shuffle;
                    case REPEAT_ONE -> R.drawable.ic_repeat_one;
                };
        int description =
                switch (playMode) {
                    case ORDER -> R.string.play_mode_order;
                    case SHUFFLE -> R.string.play_mode_shuffle;
                    case REPEAT_ONE -> R.string.play_mode_repeat_one;
                };
        modeButton.setImageResource(icon);
        modeButton.setContentDescription(getString(description));
    }

    private final Runnable progressUpdater =
            new Runnable() {
                /** 执行当前任务的实际工作；调度方决定所在线程。 */
                @Override
                public void run() {
                    if (!uiVisible) return;
                    long duration = Math.max(0, player == null ? 0 : player.getDuration());
                    long position = Math.max(0, player == null ? 0 : player.getCurrentPosition());
                    if (!seeking && duration > 0)
                        progressBar.setProgress((int) (position * progressBar.getMax() / duration));
                    currentTimeText.setText(formatTime(position));
                    durationText.setText(formatTime(duration));
                    updateLyricPosition(position);
                    progressHandler.postDelayed(this, 400);
                }
            };

    /** 把实际播放位置交给歌词时间轴，更新活动行。 */
    private void updateLyricPosition(long positionMillis) {
        if (lyricsPage.getVisibility() != View.VISIBLE
                || currentLyrics == null
                || !currentLyrics.timed()) return;
        lyricsPresenter.update(positionMillis);
    }

    /** 将当前歌词模型交给界面，并重新建立时间轴或文本布局。 */
    private void renderLyrics() {
        if (currentLyrics != null) lyricsPresenter.show(currentLyrics);
    }

    /** 切换 Android 主页面的可见区块。 */
    private void showPage(View target) {
        playlistPage.setVisibility(target == playlistPage ? View.VISIBLE : View.GONE);
        lyricsPage.setVisibility(target == lyricsPage ? View.VISIBLE : View.GONE);
        onlinePage.setVisibility(target == onlinePage ? View.VISIBLE : View.GONE);
    }

    /** 在主页面显示操作或错误提示。 */
    private void showStatus(String message) {
        if (statusText != null) statusText.setText(message);
    }

    /** 提取异常链最内层的原因或可显示信息。 */
    private static String rootMessage(Throwable error) {
        if (error == null) return "未知错误";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    /** 把有效播放时长格式化为分秒文字，无效时长使用零值显示。 */
    private static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    /** 为显示或字段读取提取安全的字符串值。 */
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** 将 dp 尺寸按设备密度转换为实际像素。 */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 确认目录已存在，必要时创建，返回创建是否成功。 */
    private static boolean ensureDirectory(File directory) {
        return directory.isDirectory() || directory.mkdirs();
    }

    /** 页面进入可见状态时接通监听并恢复界面更新。 */
    @Override
    protected void onStart() {
        super.onStart();
        uiVisible = true;
        PlaylistRuntime.get(this).workspace.downloads.listenPublished(playlistDownloadListener);
        syncCurrentTrack();
        progressHandler.removeCallbacks(progressUpdater);
        progressHandler.post(progressUpdater);
    }

    /** 页面离开可见状态时停止界面更新，后台播放继续由服务管理。 */
    @Override
    protected void onStop() {
        uiVisible = false;
        PlaylistRuntime.get(this).workspace.downloads.unlistenPublished(playlistDownloadListener);
        progressHandler.removeCallbacks(progressUpdater);
        super.onStop();
    }

    /** 页面或服务销毁时解除监听并释放自身资源，按顺序结束数据库工作。 */
    @Override
    protected void onDestroy() {
        if (importer != null) importer.close();
        if (lyricsPresenter != null) lyricsPresenter.close();
        if (onlineTasks != null) onlineTasks.close();
        if (localTrackList != null) localTrackList.close();
        progressHandler.removeCallbacksAndMessages(null);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        player = null;
        if (onlineService != null) onlineService.close();
        if (lyricsController != null) lyricsController.close();
        if (lyricsService != null) lyricsService.close();
        if (database != null) libraryExecutor.execute(database::close);
        libraryExecutor.shutdown();
        super.onDestroy();
    }

    /** 将 Android 文字变化转为一个回调，供搜索框刷新列表。 */
    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable callback;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        private SimpleTextWatcher(Runnable callback) {
            this.callback = callback;
        }

        /** 接收输入变化；实际筛选操作由该监听器所在的界面决定。 */
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        /** 接收输入变化；实际筛选操作由该监听器所在的界面决定。 */
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            callback.run();
        }

        /** 接收输入变化；实际筛选操作由该监听器所在的界面决定。 */
        @Override
        public void afterTextChanged(android.text.Editable s) {}
    }

    /** 将下拉项选择变化转交指定回调，减少页面中的重复监听代码。 */
    private static final class SimpleItemSelectedListener
            implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable callback;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        private SimpleItemSelectedListener(Runnable callback) {
            this.callback = callback;
        }

        /** 接收下拉选择变化，并按当前页面刷新对应数据。 */
        @Override
        public void onItemSelected(
                android.widget.AdapterView<?> parent, View view, int position, long id) {
            callback.run();
        }

        /** 接收下拉选择变化，并按当前页面刷新对应数据。 */
        @Override
        public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}
