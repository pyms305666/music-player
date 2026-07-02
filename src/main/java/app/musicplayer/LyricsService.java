package app.musicplayer;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class LyricsService {
    private final MusicDatabase database;
    private final Path lyricsCacheDir;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final List<OnlineLyricsProvider> onlineProviders = List.of(
            // 优先查能同时给出歌词和封面图的中文音乐站，再回退到原来的 LRCLIB。
            new NeteaseMusicProvider(),
            new QqMusicProvider(),
            new KugouMusicProvider(),
            new LrclibLyricsProvider()
    );

    public LyricsService(MusicDatabase database, Path lyricsCacheDir) {
        this.database = database;
        this.lyricsCacheDir = lyricsCacheDir;
        try {
            Files.createDirectories(lyricsCacheDir);
        } catch (IOException ignored) {
        }
    }

    public CompletableFuture<LyricsLookupResult> findLyrics(Track track, Duration duration) {
        // 歌词加载优先级：
        // 1. 数据库缓存，最快，也能离线使用；
        // 2. 歌曲同目录同名 .lrc；
        // 3. 网易云音乐 / QQ 音乐 / 酷狗音乐 / LRCLIB 联网搜索。
        Optional<Lyrics> cachedLyrics = database.loadLyrics(track);
        if (cachedLyrics.isPresent()) {
            return CompletableFuture.completedFuture(LyricsLookupResult.lyricsOnly(cachedLyrics.get()));
        }

        Optional<Lyrics> fileCacheLyrics = readCachedLyrics(track);
        if (fileCacheLyrics.isPresent()) {
            database.saveLyrics(track, fileCacheLyrics.get());
            return CompletableFuture.completedFuture(LyricsLookupResult.lyricsOnly(fileCacheLyrics.get()));
        }

        Optional<Lyrics> localLyrics = readLocalLyrics(track);
        if (localLyrics.isPresent()) {
            database.saveLyrics(track, localLyrics.get());
            saveCachedLyrics(track, localLyrics.get());
            return CompletableFuture.completedFuture(LyricsLookupResult.lyricsOnly(localLyrics.get()));
        }

        return searchOnlineAsync(track, duration);
    }

    public CompletableFuture<LyricsLookupResult> searchOnlineAsync(Track track, Duration duration) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<LyricsLookupResult> result = searchOnline(track, duration);
            // 联网搜到后立即写入缓存，后续播放同一首歌不再依赖网络。
            result.ifPresent(found -> {
                database.saveLyrics(track, found.lyrics());
                saveCachedLyrics(track, found.lyrics());
            });
            return result.orElseGet(() -> LyricsLookupResult.lyricsOnly(
                    Lyrics.empty("没有找到歌词，正在后台继续搜索")));
        });
    }

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
            // 本地 .lrc 默认按 UTF-8 读取；如果用户的歌词文件是其他编码，可能需要另行转换。
            String content = Files.readString(lrcPath, StandardCharsets.UTF_8);
            return Optional.of(LrcParser.parse("本地歌词：" + lrcPath.getFileName(), content));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<LyricsLookupResult> searchOnline(Track track, Duration duration) {
        for (OnlineLyricsProvider provider : onlineProviders) {
            Optional<OnlineLyricsResult> result = provider.search(track, duration, httpClient);
            if (result.isPresent() && result.get().hasLyrics()) {
                OnlineLyricsResult found = result.get();
                Lyrics lyrics = LrcParser.parse("联网歌词：" + found.source(), found.rawLyrics());
                return Optional.of(new LyricsLookupResult(lyrics, found.artworkUrl()));
            }
        }
        return Optional.empty();
    }

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

    private void saveCachedLyrics(Track track, Lyrics lyrics) {
        if (track == null || lyrics == null || lyrics.rawText() == null || lyrics.rawText().isBlank()) return;
        try {
            Files.createDirectories(lyricsCacheDir);
            Files.writeString(lyricsCachePath(track), lyrics.rawText(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private Path lyricsCachePath(Track track) {
        String key = Integer.toHexString(track.path().toAbsolutePath().normalize().toString().hashCode());
        return lyricsCacheDir.resolve(key + ".lrc");
    }
}
