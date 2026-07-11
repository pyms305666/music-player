package app.musicplayer.android;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import app.musicplayer.android.data.AndroidMusicDatabase;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.android.ui.OnlineTrackAdapter;
import app.musicplayer.android.ui.TrackAdapter;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public final class MainActivity extends AppCompatActivity {
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<TrackEntry> tracks = new ArrayList<>();

    private AndroidMusicDatabase database;
    private OnlineMusicSearchService onlineService;
    private ExoPlayer player;
    private File downloadsDir;
    private ActivityResultLauncher<String[]> importLauncher;

    private TrackAdapter trackAdapter;
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
    private TextView timeText;
    private ImageView artworkView;
    private ScrollView lyricsScroll;
    private EditText localSearch;
    private EditText onlineSearch;
    private Spinner sortType;
    private Spinner sortDirection;
    private SeekBar progressBar;
    private SeekBar volumeBar;
    private Button playButton;
    private Button modeButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        downloadsDir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "downloads");
        if (!downloadsDir.exists() && !downloadsDir.mkdirs()) {
            showStatus("无法创建下载目录");
        }
        database = new AndroidMusicDatabase(this);
        onlineService = new OnlineMusicSearchService();
        player = new ExoPlayer.Builder(this).build();

        bindViews();
        configureLists();
        configureActions();
        configurePlayer();
        configureImport();
        reloadTracks();
        showPage(lyricsPage);
        progressHandler.post(progressUpdater);
    }

    private void bindViews() {
        playlistPage = findViewById(R.id.playlistPage);
        lyricsPage = findViewById(R.id.lyricsPage);
        onlinePage = findViewById(R.id.onlinePage);
        statusText = findViewById(R.id.statusText);
        titleText = findViewById(R.id.titleText);
        artistText = findViewById(R.id.artistText);
        lyricsText = findViewById(R.id.lyricsText);
        timeText = findViewById(R.id.timeText);
        artworkView = findViewById(R.id.artworkView);
        lyricsScroll = findViewById(R.id.lyricsScroll);
        localSearch = findViewById(R.id.localSearch);
        onlineSearch = findViewById(R.id.onlineSearch);
        sortType = findViewById(R.id.sortType);
        sortDirection = findViewById(R.id.sortDirection);
        progressBar = findViewById(R.id.progressBar);
        volumeBar = findViewById(R.id.volumeBar);
        playButton = findViewById(R.id.playButton);
        modeButton = findViewById(R.id.modeButton);
    }

    private void configureLists() {
        RecyclerView playlistView = findViewById(R.id.playlistView);
        playlistView.setLayoutManager(new LinearLayoutManager(this));
        trackAdapter = new TrackAdapter(this::playTrack);
        playlistView.setAdapter(trackAdapter);

        RecyclerView onlineResults = findViewById(R.id.onlineResults);
        onlineResults.setLayoutManager(new LinearLayoutManager(this));
        onlineAdapter = new OnlineTrackAdapter(this::previewOnlineTrack);
        onlineResults.setAdapter(onlineAdapter);

        ArrayAdapter<PlaylistSort> sortAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, PlaylistSort.values());
        sortType.setAdapter(sortAdapter);
        ArrayAdapter<SortDirection> directionAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, SortDirection.values());
        sortDirection.setAdapter(directionAdapter);
        sortType.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refreshTrackList));
        sortDirection.setOnItemSelectedListener(new SimpleItemSelectedListener(this::refreshTrackList));

        localSearch.addTextChangedListener(new SimpleTextWatcher(this::refreshTrackList));
    }

    private void configureActions() {
        findViewById(R.id.playlistTab).setOnClickListener(view -> showPage(playlistPage));
        findViewById(R.id.lyricsTab).setOnClickListener(view -> showPage(lyricsPage));
        findViewById(R.id.onlineTab).setOnClickListener(view -> showPage(onlinePage));
        findViewById(R.id.importButton).setOnClickListener(view -> importLauncher.launch(new String[]{"audio/*"}));
        findViewById(R.id.removeButton).setOnClickListener(view -> removeSelectedTrack());
        findViewById(R.id.onlineSearchButton).setOnClickListener(view -> searchOnline());
        findViewById(R.id.downloadButton).setOnClickListener(view -> downloadSelectedOnlineTrack());
        playButton.setOnClickListener(view -> togglePlayback());
        findViewById(R.id.previousButton).setOnClickListener(view -> playRelative(-1));
        findViewById(R.id.nextButton).setOnClickListener(view -> playRelative(1));
        modeButton.setOnClickListener(view -> cyclePlayMode());
    }

    private void configureImport() {
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(), this::importUris);
    }

    private void configurePlayer() {
        player.setVolume(0.7f);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                playButton.setText(isPlaying ? "Ⅱ" : "▶");
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_ENDED) {
                    if (playMode == PlayMode.REPEAT_ONE && currentTrack != null) {
                        player.seekTo(0);
                        player.play();
                    } else {
                        playRelative(1);
                    }
                }
            }

            @Override
            public void onPlayerError(androidx.media3.common.PlaybackException error) {
                showStatus("播放失败：" + error.getErrorCodeName());
            }
        });

        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                long duration = player.getDuration();
                if (duration > 0) player.seekTo(duration * seekBar.getProgress() / seekBar.getMax());
                seeking = false;
            }
        });
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) player.setVolume(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
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
                    File file = copyIntoDownloads(uri);
                    TrackEntry entry = createEntry(file, System.currentTimeMillis());
                    database.saveTrack(entry);
                    imported++;
                } catch (Exception ignored) {
                }
            }
            int count = imported;
            runOnUiThread(() -> {
                reloadTracks();
                showStatus("已导入 " + count + " 首歌曲");
                showPage(playlistPage);
            });
        });
    }

    private File copyIntoDownloads(Uri uri) throws IOException {
        String name = queryDisplayName(uri);
        File target = uniqueFile(downloadsDir, sanitizeFileName(name));
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

    private void reloadTracks() {
        tracks.clear();
        tracks.addAll(database.loadTracks());
        refreshTrackList();
    }

    private void refreshTrackList() {
        if (trackAdapter == null) return;
        String query = localSearch == null ? "" : localSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        PlaylistSort selectedSort = sortType != null && sortType.getSelectedItem() instanceof PlaylistSort value
                ? value : PlaylistSort.TITLE;
        SortDirection direction = sortDirection != null && sortDirection.getSelectedItem() instanceof SortDirection value
                ? value : SortDirection.ASCENDING;
        Comparator<TrackEntry> comparator = switch (selectedSort) {
            case ARTIST -> Comparator.comparing(entry -> safe(entry.track().artist()), String.CASE_INSENSITIVE_ORDER);
            case FILE_NAME -> Comparator.comparing(entry -> entry.track().path().getFileName().toString(), String.CASE_INSENSITIVE_ORDER);
            case CREATED_AT -> Comparator.comparingLong(TrackEntry::createdAt);
            case TITLE -> Comparator.comparing(entry -> safe(entry.track().title()), String.CASE_INSENSITIVE_ORDER);
        };
        if (direction == SortDirection.DESCENDING) comparator = comparator.reversed();
        List<TrackEntry> visible = tracks.stream()
                .filter(entry -> query.isBlank()
                        || safe(entry.track().title()).toLowerCase(Locale.ROOT).contains(query)
                        || safe(entry.track().artist()).toLowerCase(Locale.ROOT).contains(query)
                        || entry.track().path().getFileName().toString().toLowerCase(Locale.ROOT).contains(query))
                .sorted(comparator)
                .toList();
        trackAdapter.submit(visible);
    }

    private void playTrack(TrackEntry entry) {
        currentTrack = entry;
        titleText.setText(entry.track().title());
        artistText.setText(entry.track().artist());
        showPage(lyricsPage);
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(entry.track().path().toFile())));
        player.prepare();
        player.play();
        loadLyrics(entry);
        loadEmbeddedArtwork(entry);
        showStatus("正在播放：" + entry.track().title());
    }

    private void loadLyrics(TrackEntry entry) {
        AndroidMusicDatabase.CachedLyrics cached = database.loadLyrics(entry);
        if (cached != null) {
            currentLyrics = LrcParser.parse(cached.source(), cached.rawText());
            renderLyrics(-1);
            if (!TextUtils.isEmpty(cached.artworkUrl())) Glide.with(this).load(cached.artworkUrl()).into(artworkView);
            return;
        }
        currentLyrics = Lyrics.empty("正在搜索歌词...");
        renderLyrics(-1);
        onlineService.searchAsync(entry.track().artist() + " " + entry.track().title())
                .thenCompose(results -> results.isEmpty()
                        ? CompletableFuture.completedFuture(null)
                        : onlineService.loadPreviewAsync(results.get(0)))
                .whenComplete((lookup, error) -> runOnUiThread(() -> {
                    if (lookup == null || error != null) {
                        currentLyrics = Lyrics.empty("暂无歌词");
                        renderLyrics(-1);
                        return;
                    }
                    currentLyrics = lookup.lyrics();
                    database.saveLyrics(entry, lookup.lyrics(), lookup.artworkUrl());
                    renderLyrics(-1);
                    if (!TextUtils.isEmpty(lookup.artworkUrl())) Glide.with(this).load(lookup.artworkUrl()).into(artworkView);
                }));
    }

    private void loadEmbeddedArtwork(TrackEntry entry) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(entry.track().path().toString());
            byte[] picture = retriever.getEmbeddedPicture();
            if (picture != null) Glide.with(this).load(picture).into(artworkView);
            else artworkView.setImageDrawable(null);
        } catch (RuntimeException ignored) {
            artworkView.setImageDrawable(null);
        } finally {
            try { retriever.release(); } catch (IOException ignored) { }
        }
    }

    private void searchOnline() {
        String query = onlineSearch.getText().toString().trim();
        if (query.isBlank()) return;
        showStatus("正在搜索：" + query);
        onlineAdapter.submit(List.of());
        onlineService.searchAsync(query).whenComplete((results, error) -> runOnUiThread(() -> {
            if (error != null) {
                showStatus("在线搜索失败：" + rootMessage(error));
                return;
            }
            onlineAdapter.submit(results);
            showStatus("找到 " + results.size() + " 个结果");
        }));
    }

    private void previewOnlineTrack(OnlineTrackInfo info) {
        titleText.setText(info.title());
        artistText.setText(info.artist());
        if (!TextUtils.isEmpty(info.artworkUrl())) Glide.with(this).load(info.artworkUrl()).into(artworkView);
        onlineService.loadPreviewAsync(info).whenComplete((lookup, error) -> runOnUiThread(() -> {
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
        showStatus("正在下载：" + selected.title());
        onlineService.downloadAsync(selected, downloadsDir.toPath()).whenComplete((path, error) -> runOnUiThread(() -> {
            if (error != null || path == null) {
                showStatus("下载失败：" + rootMessage(error));
                return;
            }
            TrackEntry entry = createEntry(path.toFile(), System.currentTimeMillis());
            entry.track().updateMetadata(selected.title(), selected.artist());
            database.saveTrack(entry);
            reloadTracks();
            playTrack(entry);
            showStatus("下载完成并开始播放");
        }));
    }

    private void removeSelectedTrack() {
        TrackEntry selected = trackAdapter.selected();
        if (selected == null) {
            showStatus("请先在歌单中选择歌曲");
            return;
        }
        if (currentTrack != null && currentTrack.track().path().equals(selected.track().path())) {
            player.stop();
            currentTrack = null;
        }
        database.removeTrack(selected);
        try { Files.deleteIfExists(selected.track().path()); } catch (IOException ignored) { }
        reloadTracks();
        showStatus("已从歌单和缓存删除");
    }

    private void togglePlayback() {
        if (currentTrack == null) {
            if (!tracks.isEmpty()) playTrack(tracks.get(0));
            return;
        }
        if (player.isPlaying()) player.pause(); else player.play();
    }

    private void playRelative(int direction) {
        if (tracks.isEmpty()) return;
        int index = currentTrack == null ? -1 : indexOfCurrent();
        if (playMode == PlayMode.SHUFFLE && tracks.size() > 1) {
            int next;
            do { next = random.nextInt(tracks.size()); } while (next == index);
            playTrack(tracks.get(next));
            return;
        }
        int next = Math.floorMod(index + direction, tracks.size());
        playTrack(tracks.get(next));
    }

    private int indexOfCurrent() {
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).track().path().equals(currentTrack.track().path())) return index;
        }
        return -1;
    }

    private void cyclePlayMode() {
        playMode = switch (playMode) {
            case ORDER -> PlayMode.SHUFFLE;
            case SHUFFLE -> PlayMode.REPEAT_ONE;
            case REPEAT_ONE -> PlayMode.ORDER;
        };
        modeButton.setText(switch (playMode) {
            case ORDER -> "顺序";
            case SHUFFLE -> "随机";
            case REPEAT_ONE -> "单曲";
        });
    }

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            long duration = Math.max(0, player == null ? 0 : player.getDuration());
            long position = Math.max(0, player == null ? 0 : player.getCurrentPosition());
            if (!seeking && duration > 0) progressBar.setProgress((int) (position * progressBar.getMax() / duration));
            timeText.setText(formatTime(position) + " / " + formatTime(duration));
            updateLyricPosition(position);
            progressHandler.postDelayed(this, 400);
        }
    };

    private void updateLyricPosition(long positionMillis) {
        if (currentLyrics == null || !currentLyrics.timed()) return;
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

    private static void copyStream(InputStream input, FileOutputStream output) throws IOException {
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

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacksAndMessages(null);
        if (player != null) player.release();
        if (onlineService != null) onlineService.close();
        if (database != null) database.close();
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
