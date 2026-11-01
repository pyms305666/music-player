package app.musicplayer.online;

import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.Lyrics;
import app.musicplayer.model.LyricsLookupResult;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.util.JsonSupport;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 共享在线功能门面。组合搜索协调、下载队列和歌词封面预览，提供单曲搜索、歌单候选搜索、下载及取消入口。
 *
 * <p>Online music search service — delegates scraping to MusicCrawler, delegates lyrics fetching to
 * site-specific providers, and reuses the crawler's HTTP and Cookie session so all requests carry
 * the same session.
 */
public final class OnlineMusicSearchService implements AutoCloseable {
    private final app.musicplayer.util.LatestRequest<LyricsLookupResult> previews =
            new app.musicplayer.util.LatestRequest<>();

    private final MusicCrawler crawler;
    private final SearchCoordinator searches;
    private final DownloadQueue downloads;
    private RequestCancellation previewCancellation;
    private boolean closed;
    private final ExecutorService previewExecutor =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "online-preview");
                        thread.setDaemon(true);
                        return thread;
                    });

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public OnlineMusicSearchService() {
        this(new MusicCrawler(), System::nanoTime);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public OnlineMusicSearchService(boolean allowFallback) {
        this(new MusicCrawler(allowFallback), System::nanoTime);
    }

    /**
     * 创建使用歌单批量搜索预算的在线服务。 Two batch songs search independently; each needs capacity for all of its
     * sources.
     */
    public static OnlineMusicSearchService forPlaylists() {
        return new OnlineMusicSearchService(new MusicCrawler(false, 2), System::nanoTime);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    OnlineMusicSearchService(MusicCrawler crawler, java.util.function.LongSupplier clock) {
        this(crawler, clock, crawler::download);
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    OnlineMusicSearchService(
            MusicCrawler crawler,
            java.util.function.LongSupplier clock,
            DownloadQueue.Transfer transfer) {
        this.crawler = crawler;
        searches = new SearchCoordinator(crawler, clock);
        downloads = new DownloadQueue(transfer);
    }

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    public CancellableTask<OnlineSearchSnapshot> search(
            String query,
            java.util.concurrent.Executor ui,
            java.util.function.Consumer<OnlineSearchSnapshot> progress) {
        return searches.search(query, ui, progress);
    }

    /** 启动或执行下载，接通传输进度、文件发布与完成处理。 */
    public CancellableTask<Path> download(
            OnlineTrackInfo info,
            Path targetDir,
            java.util.concurrent.Executor ui,
            java.util.function.Consumer<DownloadEvent> progress) {
        return downloads.submit(info, targetDir, ui, progress);
    }

    /**
     * 为歌单条目查询可下载版本并应用匹配规则。
     *
     * <p>Independent batch searches must not cancel one another through the interactive search
     * coordinator.
     */
    public java.util.List<OnlineTrackInfo> playlistCandidates(
            OnlineTrackInfo requested, RequestCancellation cancellation) {
        return playlistSearch(requested, cancellation).tracks();
    }

    /** 为歌单条目搜索下载候选，使用独立于交互查询的并发预算。 */
    public OnlineSearchSnapshot playlistSearch(
            OnlineTrackInfo requested, RequestCancellation cancellation) {
        String query = (requested.artist() + " " + requested.title()).trim();
        var snapshot = crawler.searchIncrementally(query, ignored -> {}, cancellation);
        var results = new java.util.ArrayList<>(snapshot.tracks());
        cancellation.check();
        if (!requested.primaryId().isBlank())
            results.add(requested.withAvailability(OnlineTrackInfo.Availability.TENTATIVE, "待下载"));
        return new OnlineSearchSnapshot(
                query,
                app.musicplayer.playlist.PlaylistSongMatcher.ranked(requested, results, false),
                snapshot.sources(),
                snapshot.state(),
                false);
    }

    // ---- lyrics preview (reuses crawler's HTTP session) ----

    /** 准备预览数据或页面，展示结果而不改变歌曲下载状态。 */
    public synchronized CancellableTask<LyricsLookupResult> preview(OnlineTrackInfo trackInfo) {
        if (closed) throw new IllegalStateException("Online service is closed");
        cancelPreview();
        RequestCancellation token = new RequestCancellation();
        previewCancellation = token;
        var result =
                previews.submit(
                        previewExecutor,
                        () ->
                                crawler.withinCancellation(
                                        token,
                                        () ->
                                                loadPreview(trackInfo)
                                                        .orElseGet(
                                                                () ->
                                                                        LyricsLookupResult
                                                                                .lyricsOnly(
                                                                                        Lyrics
                                                                                                .empty(
                                                                                                        "在线结果暂无歌词")))));
        return new CancellableTask<>(
                result,
                () -> {
                    synchronized (OnlineMusicSearchService.this) {
                        token.close();
                        if (previewCancellation == token) previews.close();
                    }
                });
    }

    /** 取消正在进行的在线预览，使过期结果不再更新界面。 */
    public synchronized void cancelPreview() {
        if (previewCancellation != null) {
            previewCancellation.close();
            previewCancellation = null;
        }
        previews.close();
    }

    /** 按歌曲来源获取在线歌词与封面预览。 */
    private Optional<LyricsLookupResult> loadPreview(OnlineTrackInfo info) {
        if (info == null || info.source() == null) return Optional.empty();
        return switch (info.source()) {
            case "网易云音乐" -> loadNeteasePreview(info);
            case "QQ音乐" -> loadQqPreview(info);
            case "酷狗音乐" -> loadKugouPreview(info);
            default -> Optional.empty();
        };
    }

    // ---- NetEase lyrics ----

    /** 读取对应来源的歌曲预览信息并组合歌词与封面。 */
    private Optional<LyricsLookupResult> loadNeteasePreview(OnlineTrackInfo info) {
        try {
            String json =
                    fetch(
                            "https://music.163.com/api/song/lyric?id="
                                    + enc(info.primaryId())
                                    + "&lv=1&kv=1&tv=-1",
                            "https://music.163.com/");
            String lrcObj = JsonSupport.objectValue(json, "lrc");
            String lyric = JsonSupport.stringValue(lrcObj == null ? json : lrcObj, "lyric");
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            return Optional.of(
                    new LyricsLookupResult(
                            LrcParser.parse("在线预览：网易云音乐：" + rd(info.title(), info.artist()), lyric),
                            info.artworkUrl()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    // ---- QQ lyrics ----

    /** 读取对应来源的歌曲预览信息并组合歌词与封面。 */
    private Optional<LyricsLookupResult> loadQqPreview(OnlineTrackInfo info) {
        try {
            String json =
                    fetch(
                            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg"
                                    + "?format=json&nobase64=1&g_tk=5381&loginUin=0&hostUin=0"
                                    + "&inCharset=utf8&outCharset=utf-8&notice=0"
                                    + "&platform=yqq.json&needNewCode=0"
                                    + "&songmid="
                                    + enc(info.primaryId()),
                            "https://y.qq.com/");
            String lyric = JsonSupport.htmlDecode(JsonSupport.stringValue(json, "lyric"));
            String decoded = JsonSupport.decodeBase64Text(lyric);
            if (decoded != null && !decoded.isBlank()) lyric = decoded;
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            return Optional.of(
                    new LyricsLookupResult(
                            LrcParser.parse("在线预览：QQ音乐：" + rd(info.title(), info.artist()), lyric),
                            info.artworkUrl()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    // ---- Kugou lyrics ----

    /** 读取对应来源的歌曲预览信息并组合歌词与封面。 */
    private Optional<LyricsLookupResult> loadKugouPreview(OnlineTrackInfo info) {
        try {
            String playJson =
                    fetch(
                            playDataUrl(info.primaryId(), info.secondaryId()),
                            "https://www.kugou.com/");
            String lyric = JsonSupport.stringValue(playJson, "lyrics");
            if (lyric == null || lyric.isBlank()) lyric = downloadKugouLrc(info);
            if (lyric == null || lyric.isBlank()) return Optional.empty();
            String art = info.artworkUrl();
            if (art == null || art.isBlank())
                art = value(playJson, "img", "album_img", "Image", "image");
            return Optional.of(
                    new LyricsLookupResult(
                            LrcParser.parse("在线预览：酷狗音乐：" + rd(info.title(), info.artist()), lyric),
                            art));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** 读取酷狗的 LRC 歌词文本并处理响应编码。 */
    private String downloadKugouLrc(OnlineTrackInfo info) throws Exception {
        String searchJson =
                fetch(
                        "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc"
                                + "&keyword="
                                + enc(queryText(info))
                                + "&duration=0&hash="
                                + enc(info.primaryId()),
                        "https://www.kugou.com/");
        String first = JsonSupport.firstObject(JsonSupport.arrayValue(searchJson, "candidates"));
        String id = value(first, "id");
        String ak = value(first, "accesskey");
        if (id == null || ak == null) return null;
        String dl =
                fetch(
                        "https://lyrics.kugou.com/download?ver=1&client=pc"
                                + "&fmt=lrc&charset=utf8&id="
                                + enc(id)
                                + "&accesskey="
                                + enc(ak),
                        "https://www.kugou.com/");
        return JsonSupport.decodeBase64Text(JsonSupport.stringValue(dl, "content"));
    }

    // ---- shared HTTP helper (reuses crawler's session) ----

    /** 通过共享 HTTP 会话读取指定地址的响应文本。 */
    private String fetch(String url, String referer) throws Exception {
        // Preview requests reuse search/download cookies and own a separate cancellation scope.
        return crawler.fetch(url, referer);
    }

    // ---- tiny utilities ----

    /** 按请求或持久化格式编码文本，避免特殊字符改变数据结构。 */
    private static String enc(String s) {
        return JsonSupport.encode(s == null ? "" : s);
    }

    /** 取得 JSON 响应中的对象或字段，供在线预览解析使用。 */
    private static String rd(String title, String artist) {
        return (artist == null || artist.isBlank() ? "" : artist + " - ")
                + (title == null ? "" : title);
    }

    /** 提取歌曲名称与歌手形成查询文本，或解析查询参数中的文字。 */
    private static String queryText(OnlineTrackInfo i) {
        String a = i.artist() == null ? "" : i.artist().trim();
        String t = i.title() == null ? "" : i.title().trim();
        return a.isBlank() ? t : a + " " + t;
    }

    /** 构造酷狗播放信息请求地址，供歌词和封面预览使用。 */
    private static String playDataUrl(String hash, String aid) {
        return "https://wwwapi.kugou.com/yy/index.php?r=play/getdata&platid=4&hash="
                + enc(hash)
                + (aid != null && !aid.isBlank() ? "&album_id=" + enc(aid) : "");
    }

    /** 提取指定字段或当前控件的值，缺失值使用本组件约定的备用结果。 */
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

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public synchronized void close() {
        closed = true;
        searches.close();
        cancelPreview();
        downloads.close();
        previewExecutor.shutdownNow();
        crawler.close();
    }
}
