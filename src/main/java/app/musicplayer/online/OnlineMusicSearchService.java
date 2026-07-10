package app.musicplayer.online;

import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Online music search service — delegates scraping to MusicCrawler,
 * delegates lyrics fetching to site-specific providers,
 * and reuses the crawler's shared HttpClient + CookieManager
 * so all requests carry the same session.
 */
public final class OnlineMusicSearchService implements AutoCloseable {

    private final MusicCrawler crawler = new MusicCrawler();
    private final ExecutorService executor = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "online-music");
        thread.setDaemon(true);
        return thread;
    });

    // ---- search / download (delegated to crawler) ----

    public CompletableFuture<List<OnlineTrackInfo>> searchAsync(String query) {
        return CompletableFuture.supplyAsync(() -> crawler.search(query), executor);
    }

    public CompletableFuture<String> resolveDownloadUrlAsync(OnlineTrackInfo info) {
        return CompletableFuture.supplyAsync(() -> crawler.resolveDownloadUrl(info), executor);
    }

    public CompletableFuture<Path> downloadAsync(OnlineTrackInfo info, Path targetDir) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return crawler.download(info, targetDir);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    // ---- lyrics preview (reuses crawler's HTTP session) ----

    public CompletableFuture<LyricsLookupResult> loadPreviewAsync(OnlineTrackInfo trackInfo) {
        return CompletableFuture.supplyAsync(() ->
            loadPreview(trackInfo).orElseGet(() ->
                LyricsLookupResult.lyricsOnly(Lyrics.empty("在线结果暂无歌词"))), executor);
    }

    private Optional<LyricsLookupResult> loadPreview(OnlineTrackInfo info) {
        if (info == null || info.source() == null) return Optional.empty();
        return switch (info.source()) {
            case "QQMP3"      -> loadQqmp3Preview(info);
            case "网易云音乐" -> loadNeteasePreview(info);
            case "QQ音乐"     -> loadQqPreview(info);
            case "酷狗音乐"   -> loadKugouPreview(info);
            default          -> Optional.empty();
        };
    }

    // ---- QQMP3 lyrics ----

    private Optional<LyricsLookupResult> loadQqmp3Preview(OnlineTrackInfo info) {
        try {
            String json = crawler.fetchQqmp3SongData(info.primaryId());
            String data = JsonSupport.objectValue(json, "data");
            String lyric = JsonSupport.stringValue(data == null ? json : data, "lrc");
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            String artwork = info.artworkUrl();
            if (artwork == null || artwork.isBlank()) {
                artwork = JsonSupport.stringValue(data == null ? json : data, "pic");
            }
            return Optional.of(new LyricsLookupResult(
                    LrcParser.parse("在线预览：QQMP3：" + rd(info.title(), info.artist()), lyric),
                    artwork));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    // ---- NetEase lyrics ----

    private Optional<LyricsLookupResult> loadNeteasePreview(OnlineTrackInfo info) {
        try {
            String json = fetch(
                "https://music.163.com/api/song/lyric?id="
                    + enc(info.primaryId()) + "&lv=1&kv=1&tv=-1",
                "https://music.163.com/");
            String lrcObj = JsonSupport.objectValue(json, "lrc");
            String lyric  = JsonSupport.stringValue(
                lrcObj == null ? json : lrcObj, "lyric");
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            return Optional.of(new LyricsLookupResult(
                LrcParser.parse("在线预览：网易云音乐：" + rd(info.title(), info.artist()), lyric),
                info.artworkUrl()));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    // ---- QQ lyrics ----

    private Optional<LyricsLookupResult> loadQqPreview(OnlineTrackInfo info) {
        try {
            String json = fetch(
                "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                    + "?format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0"
                    + "&inCharset=utf8&outCharset=utf-8&notice=0"
                    + "&platform=yqq.json&needNewCode=0"
                    + "&songmid=" + enc(info.primaryId()),
                "https://y.qq.com/");
            String lyric = JsonSupport.htmlDecode(
                JsonSupport.stringValue(json, "lyric"));
            String decoded = JsonSupport.decodeBase64Text(lyric);
            if (decoded != null && !decoded.isBlank()) lyric = decoded;
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            return Optional.of(new LyricsLookupResult(
                LrcParser.parse("在线预览：QQ音乐：" + rd(info.title(), info.artist()), lyric),
                info.artworkUrl()));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    // ---- Kugou lyrics ----

    private Optional<LyricsLookupResult> loadKugouPreview(OnlineTrackInfo info) {
        try {
            String playJson = fetch(
                playDataUrl(info.primaryId(), info.secondaryId()),
                "https://www.kugou.com/");
            String lyric = JsonSupport.stringValue(playJson, "lyrics");
            if (lyric == null || lyric.isBlank())
                lyric = downloadKugouLrc(info);
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            String art = info.artworkUrl();
            if (art == null || art.isBlank())
                art = value(playJson, "img", "album_img", "Image", "image");
            return Optional.of(new LyricsLookupResult(
                LrcParser.parse("在线预览：酷狗音乐：" + rd(info.title(), info.artist()), lyric),
                art));
        } catch (Exception ignored) { return Optional.empty(); }
    }

    private String downloadKugouLrc(OnlineTrackInfo info) throws Exception {
        String searchJson = fetch(
            "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc"
                + "&keyword=" + enc(queryText(info))
                + "&duration=0&hash=" + enc(info.primaryId()),
            "https://www.kugou.com/");
        String first = JsonSupport.firstObject(
            JsonSupport.arrayValue(searchJson, "candidates"));
        String id = value(first, "id");
        String ak = value(first, "accesskey");
        if (id == null || ak == null) return null;
        String dl = fetch(
            "https://lyrics.kugou.com/download?ver=1&client=pc"
                + "&fmt=lrc&charset=utf8&id=" + enc(id)
                + "&accesskey=" + enc(ak),
            "https://www.kugou.com/");
        return JsonSupport.decodeBase64Text(
            JsonSupport.stringValue(dl, "content"));
    }

    // ---- shared HTTP helper (reuses crawler's session) ----

    private String fetch(String url, String referer) throws Exception {
        // Delegates to crawler's fetch which uses the shared HttpClient + CookieManager.
        // This ensures lyrics preview requests carry the same session cookies
        // as search/download requests, reducing anti-crawling blocks.
        return crawler.fetch(url, referer);
    }

    // ---- tiny utilities ----

    private static String enc(String s) {
        return java.net.URLEncoder.encode(
            s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String rd(String title, String artist) {
        return (artist == null || artist.isBlank() ? "" : artist + " - ")
            + (title == null ? "" : title);
    }

    private static String queryText(OnlineTrackInfo i) {
        String a = i.artist() == null ? "" : i.artist().trim();
        String t = i.title()  == null ? "" : i.title().trim();
        return a.isBlank() ? t : a + " " + t;
    }

    private static String playDataUrl(String hash, String aid) {
        return "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash="
            + enc(hash)
            + (aid != null && !aid.isBlank() ? "&album_id=" + enc(aid) : "");
    }

    private static String value(String json, String... names) {
        if (json == null) return null;
        for (String n : names) {
            String t = JsonSupport.stringValue(json, n);
            if (t != null && !t.isBlank()) return t;
            t = JsonSupport.numberValue(json, n);
            if (t != null && !t.isBlank()) return t;
        }
        return null;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
