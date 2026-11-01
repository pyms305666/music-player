package app.musicplayer.android;

import app.musicplayer.android.data.AndroidMusicDatabase;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.Track;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Android 歌词加载顺序：数据库缓存 → 同名 .lrc 文件 → 在线歌词渠道。 借用数据库、查询服务和 I/O 执行器；close 只阻止旧结果继续更新已经销毁的页面。 */
final class AndroidLyricsController implements AutoCloseable {
    /** 加载入口在界面线程发送初始状态，异步结果经 ui 投递；artwork 接收封面地址。 */
    record Callbacks(
            Consumer<Boolean> loading,
            Consumer<Lyrics> lyrics,
            Consumer<String> artwork,
            Consumer<String> status) {}

    private final AndroidMusicDatabase database;
    private final BiFunction<Track, Long, CompletableFuture<LyricsLookupResult>> online;
    private final Executor library;
    private final Executor ui;
    private final LongSupplier duration;
    private final Callbacks callbacks;
    private long generation;
    private boolean closed;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    AndroidLyricsController(
            AndroidMusicDatabase database,
            BiFunction<Track, Long, CompletableFuture<LyricsLookupResult>> online,
            Executor library,
            Executor ui,
            LongSupplier duration,
            Callbacks callbacks) {
        this.database = database;
        this.online = online;
        this.library = library;
        this.ui = ui;
        this.duration = duration;
        this.callbacks = callbacks;
    }

    /** 在界面线程发起加载，先尝试缓存与旁车文件，读取失败或没有内容时查询在线渠道。 */
    void load(TrackEntry entry) {
        if (closed) return;
        long request = ++generation;
        callbacks.loading().accept(false);
        callbacks.lyrics().accept(Lyrics.empty("正在加载歌词..."));
        CompletableFuture.supplyAsync(() -> localLookup(entry), library)
                .whenComplete(
                        (lookup, error) ->
                                ui.execute(
                                        () -> {
                                            if (!active(request)) return;
                                            if (lookup != null) display(lookup);
                                            else lookupOnline(entry, request, false);
                                        }));
    }

    /** 手动刷新跳过本地缓存；成功后在线结果覆盖该歌曲的数据库缓存。 */
    void refresh(TrackEntry entry) {
        if (closed) return;
        long request = ++generation;
        callbacks.lyrics().accept(Lyrics.empty("正在刷新歌词..."));
        lookupOnline(entry, request, true);
    }

    /** 先读 Android 数据库缓存，再尝试 FILE 歌曲的同名歌词文件。 */
    private LyricsLookupResult localLookup(TrackEntry entry) {
        AndroidMusicDatabase.CachedLyrics cached = database.loadLyrics(entry);
        if (cached != null)
            return new LyricsLookupResult(
                    LrcParser.parse(cached.source(), cached.rawText()), cached.artworkUrl());
        File file = localLrcFile(entry);
        if (file != null) {
            try {
                Lyrics lyrics = LrcParser.parse("本地歌词：" + file.getName(), readLyricsText(file));
                if (!lyrics.lines().isEmpty()) {
                    database.saveLyrics(entry, lyrics, null);
                    return LyricsLookupResult.lyricsOnly(lyrics);
                }
            } catch (IOException ignored) {
                // 本地歌词读不了时继续在线查询，不能阻止歌曲播放。
            }
        }
        return null;
    }

    /** 查询 Android 在线歌词，保存有效结果，再投递当前轮次的界面更新。 */
    private void lookupOnline(TrackEntry entry, long request, boolean forceRefresh) {
        callbacks.loading().accept(true);
        online.apply(entry.track(), duration.getAsLong())
                .thenApplyAsync(
                        lookup -> {
                            if (lookup != null)
                                database.saveLyrics(entry, lookup.lyrics(), lookup.artworkUrl());
                            return lookup;
                        },
                        library)
                .whenComplete(
                        (lookup, error) ->
                                ui.execute(
                                        () -> {
                                            if (!active(request)) return;
                                            callbacks.loading().accept(false);
                                            if (error != null || lookup == null) {
                                                callbacks
                                                        .lyrics()
                                                        .accept(
                                                                Lyrics.empty(
                                                                        forceRefresh
                                                                                ? "刷新失败，没有找到歌词"
                                                                                : "暂无歌词，可点右上角刷新重试"));
                                                return;
                                            }
                                            display(lookup);
                                            if (forceRefresh)
                                                callbacks
                                                        .status()
                                                        .accept(
                                                                "歌词已更新："
                                                                        + lookup.lyrics().source());
                                        }));
    }

    /** 检查请求编号或任务状态是否仍属于当前有效工作。 */
    private boolean active(long request) {
        return !closed && generation == request;
    }

    /** 通知页面显示歌词；结果含封面地址时再通知页面更新封面。 */
    private void display(LyricsLookupResult lookup) {
        callbacks.lyrics().accept(lookup.lyrics());
        if (lookup.artworkUrl() != null && !lookup.artworkUrl().isEmpty())
            callbacks.artwork().accept(lookup.artworkUrl());
    }

    /** FILE 歌曲可读取同目录同名 .lrc；MediaStore 歌曲没有可直接访问的旁车路径。 */
    static File localLrcFile(TrackEntry entry) {
        if (entry.storageType() != TrackEntry.StorageType.FILE) return null;
        File audio = new File(entry.location());
        String name = audio.getName();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || audio.getParentFile() == null) return null;
        File lrc = new File(audio.getParentFile(), name.substring(0, dot) + ".lrc");
        return lrc.isFile() ? lrc : null;
    }

    /** 优先严格按 UTF-8 解码，旧中文歌词文件再使用 GB18030。 */
    static String readLyricsText(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    /** 在线预览取代本地歌词时，只使正在加载的旧结果失效。 */
    void invalidate() {
        generation++;
    }

    /** 使旧回调失效；数据库、查询服务和执行器由 MainActivity 按原顺序关闭。 */
    @Override
    public void close() {
        closed = true;
        invalidate();
    }
}
