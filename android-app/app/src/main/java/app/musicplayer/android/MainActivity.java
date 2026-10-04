package app.musicplayer.android;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.common.MediaMetadata;
import android.content.ComponentName;
import com.google.common.util.concurrent.ListenableFuture;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import app.musicplayer.android.data.AndroidMusicDatabase;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.android.ui.OnlineTrackAdapter;
import app.musicplayer.android.ui.TrackAdapter;
import app.musicplayer.android.ui.LocalTrackList;
import app.musicplayer.android.ui.VerticalVolumeView;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.LyricLine;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.model.PlayMode;
import app.musicplayer.model.Track;
import app.musicplayer.online.OnlineMusicSearchService;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SortDirection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class MainActivity extends AppCompatActivity {
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<TrackEntry> tracks = new ArrayList<>();

    private AndroidMusicDatabase database;
    private OnlineMusicSearchService onlineService;
    private AndroidLyricsService lyricsService;
    private MediaController player;
    private ListenableFuture<MediaController> controllerFuture;
    private boolean uiVisible;
    private int lastLyricIndex = Integer.MIN_VALUE;
    private Lyrics renderedLyrics;
    private final java.util.concurrent.ExecutorService libraryExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
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
    private int lyricsRequestId;
    private boolean downloadInProgress;
    private int searchRequestId;
    private int previewRequestId;
    private int playbackRequestId;
    private Button removeButton;
    private BottomNavigationView bottomNavigation;
    private View volumeDrawer;
    private View volumeScrim;
    private float lastAudibleVolume = 0.7f;
    private boolean volumeDrawerOpen;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        File externalMusicDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        privateMusicDir = new File(externalMusicDir == null ? getFilesDir() : externalMusicDir, "downloads");
        onlineTempDir = new File(getCacheDir(), "online-downloads");
        ensureDirectory(privateMusicDir);
        ensureDirectory(onlineTempDir);
        database = new AndroidMusicDatabase(this);
        onlineService = new OnlineMusicSearchService();
        lyricsService = new AndroidLyricsService();


        bindViews();
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

    private void configureWindowInsets() {
        View root = findViewById(R.id.rootView);
        View appContent = findViewById(R.id.appContent);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            appContent.setPadding(0, bars.top, 0, bars.bottom);
            FrameLayout.LayoutParams drawerParams = (FrameLayout.LayoutParams) volumeDrawer.getLayoutParams();
            drawerParams.bottomMargin = dp(112) + bars.bottom;
            volumeDrawer.setLayoutParams(drawerParams);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

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

    private void configureLists() {
        RecyclerView playlistView = findViewById(R.id.playlistView);
        playlistView.setLayoutManager(new LinearLayoutManager(this));
        trackAdapter = new TrackAdapter(entry -> {
            updateRemoveButton();
            playTrack(entry);
        });
        playlistView.setAdapter(trackAdapter);
        localTrackList = new LocalTrackList(this::runOnUiThread,
                rows -> trackAdapter.submit(rows, this::updateRemoveButton));

        RecyclerView onlineResults = findViewById(R.id.onlineResults);
        onlineResults.setLayoutManager(new LinearLayoutManager(this));
        onlineAdapter = new OnlineTrackAdapter(this::previewOnlineTrack);
        onlineResults.setAdapter(onlineAdapter);

        ArrayAdapter<PlaylistSort> sortAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, PlaylistSort.values());
        sortAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        sortType.setAdapter(sortAdapter);
        ArrayAdapter<SortDirection> directionAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, SortDirection.values());
        directionAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        sortDirection.setAdapter(directionAdapter);
        sortType.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refreshTrackList));
        sortDirection.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refreshTrackList));

        localSearch.addTextChangedListener(new SimpleTextWatcher(
                () -> localTrackList.search(localSearch.getText().toString())));
    }

    private void configureActions() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_playlist) showPage(playlistPage);
            else if (item.getItemId() == R.id.nav_online) showPage(onlinePage);
            else showPage(lyricsPage);
            return true;
        });
        findViewById(R.id.importButton).setOnClickListener(view -> importLauncher.launch(new String[]{"audio/*"}));
        removeButton.setOnClickListener(view -> confirmSelectedTrackRemoval());
        findViewById(R.id.onlineSearchButton).setOnClickListener(view -> searchOnline());
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
        onlineSearch.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            searchOnline();
            return true;
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
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

    private void configureImport() {
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(), this::importUris);
    }

    private void configureStorageAccess() {
        manageStorageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> resumePendingStorageDownload());
        legacyStorageLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> resumePendingStorageDownload());
    }

    private void resumePendingStorageDownload() {
        OnlineTrackInfo selected = pendingStorageDownload;
        pendingStorageDownload = null;
        if (selected != null) {
            beginOnlineDownload(selected, canWriteRootMusicDirectory());
        }
    }

    private void connectPlayer() {
        controllerFuture = new MediaController.Builder(this,
                new SessionToken(this, new ComponentName(this, PlaybackService.class))).buildAsync();
        controllerFuture.addListener(() -> {
            if (isDestroyed()) return;
            try {
                player = controllerFuture.get();
                configurePlayer();
                playMode = player.getShuffleModeEnabled() ? PlayMode.SHUFFLE
                        : player.getRepeatMode() == Player.REPEAT_MODE_ONE ? PlayMode.REPEAT_ONE : PlayMode.ORDER;
                updatePlayModeUi();
                syncCurrentTrack();
            } catch (Exception error) { showStatus("无法连接播放服务：" + rootMessage(error)); }
        }, ContextCompat.getMainExecutor(this));
    }

    private MediaItem mediaItem(TrackEntry entry) {
        return new MediaItem.Builder().setMediaId(entry.key()).setUri(playbackUri(entry))
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(entry.track().title())
                        .setArtist(entry.track().artist()).build()).build();
    }

    private void syncCurrentTrack() {
        if (!uiVisible || player == null || player.getCurrentMediaItem() == null) return;
        String key = player.getCurrentMediaItem().mediaId;
        for (TrackEntry entry : tracks) {
            if (entry.key().equals(key) && (currentTrack == null || !entry.key().equals(currentTrack.key()))) {
                previewRequestId++;
                playbackRequestId++;
                currentTrack = entry;
                displayTrack(entry);
                break;
            }
        }
        playButton.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void syncPlaybackQueue() {
        if (player == null || player.getMediaItemCount() == 0 || player.getCurrentMediaItem() == null) return;
        boolean equal = player.getMediaItemCount() == tracks.size();
        for (int i = 0; equal && i < tracks.size(); i++) equal = player.getMediaItemAt(i).mediaId.equals(tracks.get(i).key());
        if (equal) return;
        int index = -1;
        String playingKey = player.getCurrentMediaItem().mediaId;
        for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).key().equals(playingKey)) { index = i; break; }
        if (index >= 0) {
            long position = player.getCurrentPosition();
            boolean prepared = player.getPlaybackState() != Player.STATE_IDLE;
            player.setMediaItems(tracks.stream().map(this::mediaItem).toList(), index, position);
            if (prepared) player.prepare();
        }
    }

    private void configurePlayer() {
        verticalVolume.setProgress(Math.round(player.getVolume() * 100));
        updateVolumeUi(Math.round(player.getVolume() * 100));
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playButton.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                playButton.setContentDescription(getString(isPlaying ? R.string.pause : R.string.play));
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem item, int reason) {
                syncCurrentTrack();
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                showStatus("播放失败：" + error.getErrorCodeName());
            }

            @Override
            public void onVolumeChanged(float volume) {
                int percent = Math.round(volume * 100f);
                if (percent > 0) lastAudibleVolume = volume;
                verticalVolume.setProgress(percent);
                updateVolumeUi(percent);
            }
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                long duration = player == null ? 0 : player.getDuration();
                if (duration > 0) player.seekTo(duration * seekBar.getProgress() / seekBar.getMax());
                seeking = false;
            }
        });
        verticalVolume.setOnVolumeChangedListener((progress, fromUser) -> {
            if (!fromUser) return;
            float volume = progress / 100f;
            if (volume > 0f) lastAudibleVolume = volume;
            player.setVolume(volume);
            updateVolumeUi(progress);
        });
    }

    private void toggleVolumeDrawer() {
        if (volumeDrawerOpen) hideVolumeDrawer(true);
        else showVolumeDrawer();
    }

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
        volumeScrim.animate().alpha(0f).setDuration(150).withEndAction(() -> volumeScrim.setVisibility(View.GONE)).start();
        volumeDrawer.animate().alpha(0f).translationY(dp(24)).setDuration(150)
                .withEndAction(() -> volumeDrawer.setVisibility(View.GONE)).start();
    }

    private void toggleMute() {
        if (player == null) return;
        if (player.getVolume() > 0f) {
            lastAudibleVolume = player.getVolume();
            player.setVolume(0f);
        } else {
            player.setVolume(Math.max(0.05f, lastAudibleVolume));
        }
    }

    private void updateVolumeUi(int percent) {
        volumePercentText.setText(getString(R.string.volume_percent, percent));
        int icon = percent == 0 ? R.drawable.ic_volume_off : R.drawable.ic_volume;
        volumeButton.setImageResource(icon);
        muteButton.setImageResource(icon);
        muteButton.setContentDescription(getString(percent == 0 ? R.string.unmute : R.string.mute));
    }

    private void importUris(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        showStatus("正在导入 " + uris.size() + " 首歌曲");
        CompletableFuture.runAsync(() -> {
            int imported = 0;
            for (Uri uri : uris) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (RuntimeException ignored) {
                }
                try {
                    File file = copyIntoPrivateLibrary(uri);
                    TrackEntry entry = createEntry(file, System.currentTimeMillis());
                    database.saveTrack(entry);
                    imported++;
                } catch (Exception ignored) {
                }
            }
            int count = imported;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                reloadTracks();
                showStatus("已导入 " + count + " 首歌曲");
                bottomNavigation.setSelectedItemId(R.id.nav_playlist);
            });
        }, libraryExecutor);
    }

    private File copyIntoPrivateLibrary(Uri uri) throws IOException {
        String name = queryDisplayName(uri);
        File target = uniqueFile(privateMusicDir, sanitizeFileName(name));
        try (InputStream input = getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("无法读取文件");
            copyStream(input, output);
        }
        return target;
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        return "imported-" + System.currentTimeMillis() + ".mp3";
    }

    private TrackEntry createEntry(File file, long createdAt) {
        Track track = new Track(file.toPath());
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            track.updateMetadata(title, artist);
        } catch (RuntimeException ignored) {
        } finally {
            try { retriever.release(); } catch (IOException ignored) { }
        }
        return new TrackEntry(track, createdAt);
    }

    private void reloadTracks() { reloadTracks(() -> { }); }

    private void reloadTracks(Runnable afterLoad) {
        int request = ++libraryRequestId;
        CompletableFuture.supplyAsync(database::loadTracks, libraryExecutor).whenComplete((loaded, error) -> runOnUiThread(() -> {
            if (isDestroyed() || request != libraryRequestId) return;
            if (error != null) { showStatus("读取曲库失败：" + rootMessage(error)); return; }
            tracks.clear();
            tracks.addAll(loaded);
            syncCurrentTrack();
            refreshTrackList();
            afterLoad.run();
        }));
    }

    private void refreshTrackList() {
        if (trackAdapter == null) return;
        String query = localSearch == null ? "" : localSearch.getText().toString();
        PlaylistSort selectedSort = sortType != null && sortType.getSelectedItem() instanceof PlaylistSort value
                ? value : PlaylistSort.TITLE;
        SortDirection direction = sortDirection != null && sortDirection.getSelectedItem() instanceof SortDirection value
                ? value : SortDirection.ASCENDING;
        List<TrackEntry> ordered = localTrackList.replace(tracks, selectedSort, direction, query);
        tracks.clear();
        tracks.addAll(ordered);
        syncPlaybackQueue();
        updateRemoveButton();
    }

    private void playTrack(TrackEntry entry) {
        if (player == null) { showStatus("播放服务连接中，请稍后重试"); return; }
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

    private void displayTrack(TrackEntry entry) {
        titleText.setText(entry.track().title());
        artistText.setText(entry.track().artist());
        loadEmbeddedArtwork(entry);
        loadLyrics(entry);
        showStatus("正在播放：" + entry.track().title());
    }

    private void loadLyrics(TrackEntry entry) {
        int request = ++lyricsRequestId;
        setRefreshLoading(false);
        currentLyrics = Lyrics.empty("正在加载歌词...");
        renderLyrics(-1);
        CompletableFuture.supplyAsync(() -> {
            AndroidMusicDatabase.CachedLyrics cached = database.loadLyrics(entry);
            if (cached != null) return new LyricsLookupResult(LrcParser.parse(cached.source(), cached.rawText()), cached.artworkUrl());
            File local = localLrcFile(entry);
            if (local != null) {
                try {
                    Lyrics lyrics = LrcParser.parse("本地歌词：" + local.getName(), readLyricsText(local));
                    if (!lyrics.lines().isEmpty()) {
                        database.saveLyrics(entry, lyrics, null);
                        return LyricsLookupResult.lyricsOnly(lyrics);
                    }
                } catch (IOException ignored) { }
            }
            return null;
        }, libraryExecutor).whenComplete((lookup, error) -> runOnUiThread(() -> {
            if (isDestroyed() || request != lyricsRequestId) return;
            if (lookup != null) {
                currentLyrics = lookup.lyrics();
                renderLyrics(-1);
                if (!TextUtils.isEmpty(lookup.artworkUrl())) loadArtwork(lookup.artworkUrl());
            } else lookupLyricsOnline(entry, request, false);
        }));
    }

    /** 走共享歌词渠道（网易云 → QQ → 酷狗 → LRCLIB）在线查词，与下载来源完全解耦。 */
    private void lookupLyricsOnline(TrackEntry entry, int reqId, boolean forceRefresh) {
        setRefreshLoading(true);
        long duration = player == null ? 0 : player.getDuration();
        lyricsService.searchOnlineAsync(entry.track(), duration)
                .thenApplyAsync(lookup -> {
                    if (lookup != null) database.saveLyrics(entry, lookup.lyrics(), lookup.artworkUrl());
                    return lookup;
                }, libraryExecutor)
                .whenComplete((lookup, error) -> runOnUiThread(() -> {
                    if (isDestroyed() || reqId != lyricsRequestId) return;
                    setRefreshLoading(false);
                    if (error != null || lookup == null) {
                        currentLyrics = Lyrics.empty(forceRefresh ? "刷新失败，没有找到歌词" : "暂无歌词，可点右上角刷新重试");
                        renderLyrics(-1);
                        return;
                    }
                    currentLyrics = lookup.lyrics();
                    renderLyrics(-1);
                    if (!TextUtils.isEmpty(lookup.artworkUrl())) loadArtwork(lookup.artworkUrl());
                    if (forceRefresh) showStatus("歌词已更新：" + lookup.lyrics().source());
                }));
    }

    /** 强制刷新：跳过数据库缓存重新在线查词，成功后覆盖缓存。 */
    private void forceRefreshLyrics() {
        TrackEntry entry = currentTrack;
        if (entry == null) {
            showStatus("请先播放歌曲再刷新歌词");
            return;
        }
        int reqId = ++lyricsRequestId;
        currentLyrics = Lyrics.empty("正在刷新歌词...");
        renderLyrics(-1);
        lookupLyricsOnline(entry, reqId, true);
    }

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
    private File localLrcFile(TrackEntry entry) {
        if (entry.storageType() != TrackEntry.StorageType.FILE) return null;
        File audio = new File(entry.location());
        String name = audio.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || audio.getParentFile() == null) return null;
        File lrc = new File(audio.getParentFile(), name.substring(0, dot) + ".lrc");
        return lrc.isFile() ? lrc : null;
    }

    private static String readLyricsText(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    private void loadEmbeddedArtwork(TrackEntry entry) {
        int request = previewRequestId;
        showArtworkPlaceholder();
        CompletableFuture.supplyAsync(() -> {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                if (entry.storageType() == TrackEntry.StorageType.MEDIA_STORE) retriever.setDataSource(this, Uri.parse(entry.location()));
                else retriever.setDataSource(entry.location());
                return retriever.getEmbeddedPicture();
            } catch (RuntimeException ignored) { return null; }
            finally { try { retriever.release(); } catch (IOException ignored) { } }
        }, libraryExecutor).whenComplete((picture, error) -> runOnUiThread(() -> {
            if (isDestroyed() || request != previewRequestId || currentTrack == null || !currentTrack.key().equals(entry.key())) return;
            if (picture != null) loadArtwork(picture);
        }));
    }

    private void loadArtwork(Object source) {
        artworkView.setPadding(0, 0, 0, 0);
        ImageViewCompat.setImageTintList(artworkView, null);
        Glide.with(this).load(source).centerCrop().into(artworkView);
    }

    private void showArtworkPlaceholder() {
        Glide.with(this).clear(artworkView);
        int padding = dp(48);
        artworkView.setPadding(padding, padding, padding, padding);
        ImageViewCompat.setImageTintList(
                artworkView, ColorStateList.valueOf(getColor(R.color.text_muted)));
        artworkView.setImageResource(R.drawable.ic_music_note);
    }

    private void searchOnline() {
        int request = ++searchRequestId;
        previewRequestId++;
        String query = onlineSearch.getText().toString().trim();
        if (query.isBlank()) return;
        showStatus("正在搜索：" + query);
        onlineAdapter.submit(List.of());
        onlineService.searchAsync(query).whenComplete((results, error) -> runOnUiThread(() -> {
            if (isDestroyed() || request != searchRequestId) return;
            if (error != null) {
                showStatus("在线搜索失败：" + rootMessage(error));
                return;
            }
            onlineAdapter.submit(results);
            showStatus("找到 " + results.size() + " 个结果");
        }));
    }

    private void previewOnlineTrack(OnlineTrackInfo info) {
        int request = ++previewRequestId;
        lyricsRequestId++;
        setRefreshLoading(false);
        titleText.setText(info.title());
        artistText.setText(info.artist());
        if (!TextUtils.isEmpty(info.artworkUrl())) loadArtwork(info.artworkUrl());
        else showArtworkPlaceholder();
        onlineService.loadPreviewAsync(info).whenComplete((lookup, error) -> runOnUiThread(() -> {
            if (isDestroyed() || request != previewRequestId) return;
            currentLyrics = lookup == null || error != null ? Lyrics.empty("在线结果暂无歌词") : lookup.lyrics();
            renderLyrics(-1);
        }));
    }

    private void downloadSelectedOnlineTrack() {
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

    private void beginOnlineDownload(OnlineTrackInfo selected, boolean useRootDirectory) {
        if (downloadInProgress) { showStatus("请等待当前下载完成"); return; }
        downloadInProgress = true;
        int request = ++playbackRequestId;
        showStatus("正在下载：" + selected.title());
        onlineService.downloadAsync(selected, onlineTempDir.toPath())
                .thenApplyAsync(path -> {
                    try {
                        TrackEntry entry = publishDownloadedTrack(path.toFile(), selected, useRootDirectory);
                        database.saveTrack(entry);
                        return entry;
                    } catch (IOException error) {
                        throw new CompletionException(error);
                    }
                }, libraryExecutor)
                .whenComplete((entry, error) -> runOnUiThread(() -> {
            downloadInProgress = false;
            if (isDestroyed()) return;
            if (error != null || entry == null) {
                showStatus("下载失败：" + rootMessage(error));
                return;
            }
            reloadTracks(() -> {
                if (request == playbackRequestId) {
                    playTrack(entry);
                    showStatus("下载完成并开始播放");
                } else showStatus("下载完成，已加入曲库");
            });
        }));
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void requestAllFilesAccess(OnlineTrackInfo selected) {
        pendingStorageDownload = selected;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.storage_permission_title)
                .setMessage(R.string.storage_permission_message)
                .setNegativeButton(R.string.storage_permission_fallback, (dialog, which) -> {
                    pendingStorageDownload = null;
                    beginOnlineDownload(selected, false);
                })
                .setPositiveButton(R.string.storage_permission_action, (dialog, which) -> launchAllFilesSettings())
                .setOnCancelListener(dialog -> pendingStorageDownload = null)
                .show();
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private void launchAllFilesSettings() {
        Intent appSettings = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        try {
            manageStorageLauncher.launch(appSettings);
        } catch (ActivityNotFoundException firstError) {
            try {
                manageStorageLauncher.launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (ActivityNotFoundException secondError) {
                resumePendingStorageDownload();
            }
        }
    }

    private boolean canWriteRootMusicDirectory() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private TrackEntry publishDownloadedTrack(
            File temporaryFile,
            OnlineTrackInfo selected,
            boolean useRootDirectory) throws IOException {
        Track track = readTrackMetadata(temporaryFile);
        track.updateMetadata(selected.title(), selected.artist());
        long createdAt = System.currentTimeMillis();

        if (useRootDirectory) {
            File target = copyToDirectory(
                    temporaryFile,
                    new File(Environment.getExternalStorageDirectory(), "music"));
            Track publicTrack = new Track(target.toPath());
            publicTrack.updateMetadata(track.title(), track.artist());
            return new TrackEntry(publicTrack, createdAt);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStoreFile published = publishToMediaStore(temporaryFile);
            Files.deleteIfExists(temporaryFile.toPath());
            return TrackEntry.mediaStore(track, createdAt, published.uri().toString(), published.fileName());
        }

        File target = copyToDirectory(temporaryFile, privateMusicDir);
        Track privateTrack = new Track(target.toPath());
        privateTrack.updateMetadata(track.title(), track.artist());
        return new TrackEntry(privateTrack, createdAt);
    }

    private Track readTrackMetadata(File file) {
        return createEntry(file, System.currentTimeMillis()).track();
    }

    private File copyToDirectory(File source, File directory) throws IOException {
        if (!ensureDirectory(directory)) {
            throw new IOException("无法创建歌曲目录：" + directory.getAbsolutePath());
        }
        File target = uniqueFile(directory, sanitizeFileName(source.getName()));
        try {
            Files.copy(source.toPath(), target.toPath());
            Files.deleteIfExists(source.toPath());
            return target;
        } catch (IOException error) {
            Files.deleteIfExists(target.toPath());
            throw error;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private MediaStoreFile publishToMediaStore(File source) throws IOException {
        String relativePath = Environment.DIRECTORY_MUSIC + "/music/";
        String fileName = uniqueMediaStoreName(sanitizeFileName(source.getName()), relativePath);
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Audio.Media.MIME_TYPE, mimeTypeFor(fileName));
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);

        Uri uri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("系统媒体库无法创建歌曲文件");
        }
        try {
            try (InputStream input = Files.newInputStream(source.toPath());
                 OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("系统媒体库无法写入歌曲文件");
                copyStream(input, output);
            }
            ContentValues completed = new ContentValues();
            completed.put(MediaStore.Audio.Media.IS_PENDING, 0);
            getContentResolver().update(uri, completed, null, null);
            return new MediaStoreFile(uri, fileName);
        } catch (IOException | RuntimeException error) {
            getContentResolver().delete(uri, null, null);
            if (error instanceof IOException ioError) throw ioError;
            throw new IOException(error.getMessage(), error);
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private String uniqueMediaStoreName(String requestedName, String relativePath) {
        int dot = requestedName.lastIndexOf('.');
        String base = dot > 0 ? requestedName.substring(0, dot) : requestedName;
        String extension = dot > 0 ? requestedName.substring(dot) : "";
        String candidate = requestedName;
        int counter = 2;
        while (mediaStoreNameExists(candidate, relativePath)) {
            candidate = base + " (" + counter++ + ")" + extension;
        }
        return candidate;
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private boolean mediaStoreNameExists(String fileName, String relativePath) {
        String selection = MediaStore.Audio.Media.DISPLAY_NAME + "=? and "
                + MediaStore.Audio.Media.RELATIVE_PATH + "=?";
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID},
                selection,
                new String[]{fileName, relativePath},
                null)) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    private static String mimeTypeFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return detected == null ? "audio/mpeg" : detected;
    }

    private Uri playbackUri(TrackEntry entry) {
        return entry.storageType() == TrackEntry.StorageType.MEDIA_STORE
                ? Uri.parse(entry.location())
                : Uri.fromFile(Paths.get(entry.location()).toFile());
    }

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
                .setPositiveButton(R.string.confirm_remove, (dialog, which) -> removeSelectedTrack())
                .show();
    }

    private void removeSelectedTrack() {
        TrackEntry selected = trackAdapter.selected();
        if (selected == null) {
            showStatus("请先在歌单中选择歌曲");
            return;
        }
        if (currentTrack != null && currentTrack.key().equals(selected.key())) {
            if (player != null) { player.stop(); player.clearMediaItems(); }
            currentTrack = null;
        }
        CompletableFuture.supplyAsync(() -> {
            database.removeTrack(selected);
            return deleteStoredTrack(selected);
        }, libraryExecutor).whenComplete((deleted, error) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            if (error != null) { showStatus("移除失败：" + rootMessage(error)); return; }
            reloadTracks();
            showStatus(deleted ? "已从歌单和本地文件中删除" : "已移除歌单记录，文件可能已在外部删除");
        }));
    }

    private void updateRemoveButton() {
        if (removeButton != null && trackAdapter != null) {
            removeButton.setEnabled(trackAdapter.selected() != null);
        }
    }

    private void togglePlayback() {
        if (player == null) return;
        if (currentTrack == null) {
            if (!tracks.isEmpty()) playTrack(tracks.get(0));
            return;
        }
        if (player.isPlaying()) player.pause(); else player.play();
    }

    private void playRelative(int direction) {
        if (player == null || tracks.isEmpty()) return;
        if (player.getMediaItemCount() == 0) { playTrack(tracks.get(0)); return; }
        previewRequestId++;
        playbackRequestId++;
        if (direction > 0) player.seekToNextMediaItem(); else player.seekToPreviousMediaItem();
        player.play();
    }

    private int indexOfCurrent() {
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).key().equals(currentTrack.key())) return index;
        }
        return -1;
    }

    private void cyclePlayMode() {
        if (player == null) return;
        playMode = switch (playMode) {
            case ORDER -> PlayMode.SHUFFLE;
            case SHUFFLE -> PlayMode.REPEAT_ONE;
            case REPEAT_ONE -> PlayMode.ORDER;
        };
        player.setShuffleModeEnabled(playMode == PlayMode.SHUFFLE);
        player.setRepeatMode(playMode == PlayMode.REPEAT_ONE ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_ALL);
        updatePlayModeUi();
    }

    private void updatePlayModeUi() {
        int icon = switch (playMode) {
            case ORDER -> R.drawable.ic_repeat;
            case SHUFFLE -> R.drawable.ic_shuffle;
            case REPEAT_ONE -> R.drawable.ic_repeat_one;
        };
        int description = switch (playMode) {
            case ORDER -> R.string.play_mode_order;
            case SHUFFLE -> R.string.play_mode_shuffle;
            case REPEAT_ONE -> R.string.play_mode_repeat_one;
        };
        modeButton.setImageResource(icon);
        modeButton.setContentDescription(getString(description));
    }

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (!uiVisible) return;
            long duration = Math.max(0, player == null ? 0 : player.getDuration());
            long position = Math.max(0, player == null ? 0 : player.getCurrentPosition());
            if (!seeking && duration > 0) progressBar.setProgress((int) (position * progressBar.getMax() / duration));
            currentTimeText.setText(formatTime(position));
            durationText.setText(formatTime(duration));
            updateLyricPosition(position);
            progressHandler.postDelayed(this, 400);
        }
    };

    private void updateLyricPosition(long positionMillis) {
        if (lyricsPage.getVisibility() != View.VISIBLE || currentLyrics == null || !currentLyrics.timed()) return;
        int active = -1;
        for (int index = 0; index < currentLyrics.lines().size(); index++) {
            LyricLine line = currentLyrics.lines().get(index);
            if (line.time() != null && line.time().toMillis() <= positionMillis) active = index;
            else break;
        }
        renderLyrics(active);
    }

    private void renderLyrics(int activeIndex) {
        if (currentLyrics == null) return;
        if (renderedLyrics == currentLyrics && lastLyricIndex == activeIndex) return;
        renderedLyrics = currentLyrics;
        lastLyricIndex = activeIndex;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        int activeStart = -1;
        int activeEnd = -1;
        for (int index = 0; index < currentLyrics.lines().size(); index++) {
            if (index > 0) builder.append('\n');
            int start = builder.length();
            builder.append(currentLyrics.lines().get(index).text());
            if (index == activeIndex) {
                activeStart = start;
                activeEnd = builder.length();
            }
        }
        if (activeStart >= 0) {
            builder.setSpan(new ForegroundColorSpan(Color.rgb(240, 90, 60)), activeStart, activeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), activeStart, activeEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        lyricsText.setText(builder);
        if (activeIndex >= 0) {
            int lineHeight = Math.max(1, lyricsText.getLineHeight());
            int target = Math.max(0, activeIndex * lineHeight - lyricsScroll.getHeight() / 2);
            lyricsScroll.smoothScrollTo(0, target);
        }
    }

    private void showPage(View target) {
        playlistPage.setVisibility(target == playlistPage ? View.VISIBLE : View.GONE);
        lyricsPage.setVisibility(target == lyricsPage ? View.VISIBLE : View.GONE);
        onlinePage.setVisibility(target == onlinePage ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String message) {
        if (statusText != null) statusText.setText(message);
    }

    private static String rootMessage(Throwable error) {
        if (error == null) return "未知错误";
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[16_384];
        int length;
        while ((length = input.read(buffer)) >= 0) {
            output.write(buffer, 0, length);
        }
    }

    private static String sanitizeFileName(String value) {
        String safe = value == null || value.isBlank() ? "audio.mp3" : value;
        return safe.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static File uniqueFile(File directory, String name) {
        File candidate = new File(directory, name);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int counter = 2;
        while (candidate.exists()) candidate = new File(directory, base + " (" + counter++ + ")" + extension);
        return candidate;
    }

    private static boolean ensureDirectory(File directory) {
        return directory.isDirectory() || directory.mkdirs();
    }

    private record MediaStoreFile(Uri uri, String fileName) {
    }

    @Override protected void onStart() {
        super.onStart();
        uiVisible = true;
        syncCurrentTrack();
        progressHandler.removeCallbacks(progressUpdater);
        progressHandler.post(progressUpdater);
    }

    @Override protected void onStop() {
        uiVisible = false;
        progressHandler.removeCallbacks(progressUpdater);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (localTrackList != null) localTrackList.close();
        progressHandler.removeCallbacksAndMessages(null);
        if (controllerFuture != null) MediaController.releaseFuture(controllerFuture);
        player = null;
        if (onlineService != null) onlineService.close();
        if (lyricsService != null) lyricsService.close();
        if (database != null) libraryExecutor.execute(database::close);
        libraryExecutor.shutdown();
        super.onDestroy();
    }

    private static final class SimpleTextWatcher implements android.text.TextWatcher {
        private final Runnable callback;
        private SimpleTextWatcher(Runnable callback) { this.callback = callback; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { callback.run(); }
        @Override public void afterTextChanged(android.text.Editable s) { }
    }

    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable callback;
        private SimpleItemSelectedListener(Runnable callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { callback.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}
