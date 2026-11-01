package app.musicplayer.lyrics;

import app.musicplayer.data.MusicDatabase;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;
import app.musicplayer.util.Hashing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 桌面歌词获取与缓存。按本地歌词文件、缓存及在线渠道获取结果，保存原始歌词与封面地址，用 LatestRequest 管理异步查询。 */
public final class LyricsService implements AutoCloseable {
    private final app.musicplayer.util.LatestRequest<LyricsLookupResult> requests =
            new app.musicplayer.util.LatestRequest<>();
    private final MusicDatabase database;
    private final Path lyricsCacheDir;
    private final ThreadLocal<Long> lookupDeadline = new ThreadLocal<>();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ExecutorService executor =
            Executors.newFixedThreadPool(
                    2,
                    runnable -> {
                        Thread thread = new Thread(runnable, "lyrics-search");
                        thread.setDaemon(true);
                        return thread;
                    });
    private final List<OnlineLyricsProvider> onlineProviders =
            List.of(
                    // 优先查能同时给出歌词和封面图的中文音乐站，再回退到原来的 LRCLIB。
                    new NeteaseMusicProvider(),
                    new QqMusicProvider(),
                    new KugouMusicProvider(),
                    new LrclibLyricsProvider());

    /** 桌面端用 java.net.http 实现 LyricsHttp；Android 端注入 CrawlerSession 实现。 */
    private final LyricsHttp lyricsHttp =
            new LyricsHttp() {
                /** 通过共享 HTTP 会话读取指定地址的响应文本。 */
                @Override
                public String fetch(String url, String referer) throws Exception {
                    Long limit = lookupDeadline.get();
                    long remaining =
                            limit == null
                                    ? TimeUnit.SECONDS.toNanos(12)
                                    : limit - System.nanoTime();
                    if (remaining <= 0)
                        throw new java.util.concurrent.TimeoutException(
                                "Lyrics lookup deadline expired");
                    HttpRequest request =
                            HttpRequest.newBuilder(URI.create(url))
                                    .timeout(
                                            Duration.ofNanos(
                                                    Math.min(
                                                            TimeUnit.SECONDS.toNanos(12),
                                                            remaining)))
                                    .header("User-Agent", "Mozilla/5.0 SimpleMusicPlayer/1.0")
                                    .header("Referer", referer)
                                    .GET()
                                    .build();
                    HttpResponse<String> response;
                    try {
                        response =
                                httpClient.send(
                                        request,
                                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    } catch (InterruptedException cancelled) {
                        Thread.currentThread().interrupt();
                        throw cancelled;
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    return response.body();
                }
            };

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public LyricsService(MusicDatabase database, Path lyricsCacheDir) {
        this.database = database;
        this.lyricsCacheDir = lyricsCacheDir;
        try {
            Files.createDirectories(lyricsCacheDir);
        } catch (IOException ignored) {
        }
    }

    /** 按桌面本地文件、缓存及在线规则取得歌词，异步返回结果。 */
    public CompletableFuture<LyricsLookupResult> findLyrics(Track track, Duration duration) {
        return requests.submit(
                executor,
                () -> {
                    var cached = database.loadLyricsLookup(track);
                    if (cached.isPresent()) return cached.get();
                    Optional<Lyrics> local = readLocalLyrics(track);
                    if (local.isEmpty()) local = readCachedLyrics(track);
                    if (local.isPresent()) {
                        database.saveLyrics(track, local.get());
                        return LyricsLookupResult.lyricsOnly(local.get());
                    }
                    return lookupAndCache(track, duration);
                });
    }

    /** 在后台查询在线歌词，使用异步结果通知调用者。 */
    public CompletableFuture<LyricsLookupResult> searchOnlineAsync(Track track, Duration duration) {
        return requests.submit(executor, () -> lookupAndCache(track, duration));
    }

    /** 查询在线歌词并把有效结果保存到缓存。 */
    private LyricsLookupResult lookupAndCache(Track track, Duration duration) {
        lookupDeadline.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(15));
        try {
            Optional<LyricsLookupResult> result = searchOnline(track, duration);
            if (Thread.currentThread().isInterrupted())
                throw new java.util.concurrent.CancellationException();
            result.ifPresent(
                    found -> {
                        database.saveLyrics(track, found.lyrics(), found.artworkUrl());
                        saveCachedLyrics(track, found.lyrics());
                    });
            return result.orElseGet(
                    () -> LyricsLookupResult.lyricsOnly(Lyrics.empty("没有找到歌词，正在后台继续搜索")));
        } finally {
            lookupDeadline.remove();
        }
    }

    /** 读取音频旁的同名 .lrc 文件，缺失时允许继续查询。 */
    private Optional<Lyrics> readLocalLyrics(Track track) {
        Path audioPath = track.path();
        String fileName = audioPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }

        Path lrcPath = audioPath.resolveSibling(fileName.substring(0, dot) + ".lrc");
        if (!Files.isRegularFile(lrcPath)) {
            return Optional.empty();
        }

        try {
            String content = readLyricsText(lrcPath);
            return Optional.of(LrcParser.parse("本地歌词：" + lrcPath.getFileName(), content));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    /** 从当前搜索输入启动在线查询，结果由专用协调器更新。 */
    private Optional<LyricsLookupResult> searchOnline(Track track, Duration duration) {
        for (OnlineLyricsProvider provider : onlineProviders) {
            if (Thread.currentThread().isInterrupted()
                    || lookupDeadline.get() != null && System.nanoTime() >= lookupDeadline.get())
                return Optional.empty();
            Optional<OnlineLyricsResult> result = provider.search(track, duration, lyricsHttp);
            if (result.isPresent() && result.get().hasLyrics()) {
                OnlineLyricsResult found = result.get();
                Lyrics lyrics = LrcParser.parse("联网歌词：" + found.source(), found.rawLyrics());
                return Optional.of(new LyricsLookupResult(lyrics, found.artworkUrl()));
            }
        }
        return Optional.empty();
    }

    /** 从数据库及歌词文件缓存读取已有结果。 */
    private Optional<Lyrics> readCachedLyrics(Track track) {
        Path cachePath = lyricsCachePath(track);
        if (!Files.isRegularFile(cachePath)) return Optional.empty();
        try {
            String content = Files.readString(cachePath, StandardCharsets.UTF_8);
            return Optional.of(LrcParser.parse("缓存歌词：" + cachePath.getFileName(), content));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    /** 保存歌词缓存及封面关联，供后续播放复用。 */
    private void saveCachedLyrics(Track track, Lyrics lyrics) {
        if (track == null
                || lyrics == null
                || lyrics.rawText() == null
                || lyrics.rawText().isBlank()) return;
        try {
            Files.createDirectories(lyricsCacheDir);
            Files.writeString(lyricsCachePath(track), lyrics.rawText(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** 根据歌曲身份生成歌词缓存文件位置。 */
    private Path lyricsCachePath(Track track) {
        String key = Hashing.sha1(track.path().toAbsolutePath().normalize().toString());
        return lyricsCacheDir.resolve(key + ".lrc");
    }

    /** 优先严格解码 UTF-8，旧中文歌词再使用 GB18030。 */
    private String readLyricsText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return Charset.forName("GB18030").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        requests.close();
        executor.shutdownNow();
    }
}
