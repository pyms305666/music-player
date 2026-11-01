package app.musicplayer.artwork;

import app.musicplayer.cache.GeneratedFileCache;
import app.musicplayer.util.Hashing;
import app.musicplayer.util.LatestRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 下载并缓存封面图片，避免 JavaFX UI 类直接承担网络和文件操作。 */
public final class ArtworkService implements AutoCloseable {
    private final Path cacheDir;
    private final ExecutorService executor;
    private final HttpClient httpClient;
    private final GeneratedFileCache diskCache;
    private final Map<String, Download> downloads = new HashMap<>();
    private boolean closed;

    /** 记录正在下载的封面和等待其结果的使用者。 */
    private static final class Download {
        final LatestRequest<Path> work = new LatestRequest<>();
        CompletableFuture<Path> result;
        int consumers;
    }

    /**
     * 持有一次封面请求的取消及资源释放操作，防止取消单个使用者影响其他使用者。 Each consumer can cancel independently; the last one
     * interrupts network work.
     */
    public final class Request implements AutoCloseable {
        private final CompletableFuture<Path> result = new CompletableFuture<>();
        private final String url;
        private final Download download;
        private final GeneratedFileCache.Lease lease;
        private boolean released;

        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        private Request(String url, Download download, GeneratedFileCache.Lease lease) {
            this.url = url;
            this.download = download;
            this.lease = lease;
            download.consumers++;
            download.result.whenComplete(
                    (path, error) -> {
                        if (error == null) result.complete(path);
                        else result.completeExceptionally(error);
                    });
        }

        /** 返回此任务或句柄关联的结果，供调用者等待或使用。 */
        public CompletableFuture<Path> result() {
            return result;
        }

        /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
        @Override
        public void close() {
            boolean abandoned;
            synchronized (ArtworkService.this) {
                if (released) return;
                released = true;
                abandoned = --download.consumers == 0;
                if (abandoned) downloads.remove(url, download);
            }
            result.cancel(false);
            if (abandoned) download.work.close();
            lease.close();
        }
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public ArtworkService(Path cacheDir) {
        this(cacheDir, new GeneratedFileCache(cacheDir, GeneratedFileCache.Kind.ARTWORK));
    }

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    ArtworkService(Path cacheDir, GeneratedFileCache diskCache) {
        this.cacheDir = cacheDir;
        this.diskCache = diskCache;
        this.executor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "artwork-cache");
                            thread.setDaemon(true);
                            return thread;
                        });
        this.httpClient =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
    }

    /** 检查封面地址是否属于可下载的网络资源。 */
    public boolean isRemoteUrl(String source) {
        if (source == null) {
            return false;
        }
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** 根据封面地址取得对应的本地缓存位置。 */
    public Path cachedPath(String url) {
        return cacheDir.resolve(Hashing.sha1(url) + extension(url));
    }

    /** 下载远程封面并保存到受管理缓存目录，返回本地文件路径。 */
    public CompletableFuture<Path> cache(String url) {
        if (!isRemoteUrl(url)) {
            return CompletableFuture.completedFuture(null);
        }
        Request request = acquire(url);
        request.result().whenComplete((path, error) -> request.close());
        return request.result();
    }

    /** 取得可释放的资源句柄，在使用期间保护相关缓存文件。 */
    public synchronized Request acquire(String url) {
        if (closed) throw new IllegalStateException("Artwork service is closed");
        if (!isRemoteUrl(url)) throw new IllegalArgumentException("Remote artwork URL required");
        var lease = diskCache.acquire(cachedPath(url));
        Download download = downloads.get(url);
        if (download == null) {
            download = new Download();
            downloads.put(url, download);
            download.result = download.work.submit(executor, () -> download(url, cachedPath(url)));
        }
        return new Request(url, download, lease);
    }

    /** 启动或执行下载，接通传输进度、文件发布与完成处理。 */
    private Path download(String url, Path target) {
        Path temporary = null;
        try {
            if (Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return target;
            }
            HttpRequest request =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("User-Agent", "Mozilla/5.0")
                            .GET()
                            .build();
            HttpResponse<byte[]> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.limiting(
                                    HttpResponse.BodyHandlers.ofByteArray(), 12 * 1024 * 1024));
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() < 200
                    || response.statusCode() >= 400
                    || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                    || response.body() == null
                    || response.body().length == 0) {
                return null;
            }
            Files.createDirectories(cacheDir);
            temporary = Files.createTempFile(cacheDir, ".za-artwork-", ".part");
            Files.write(temporary, response.body());
            if (Thread.currentThread().isInterrupted()) return null;
            try {
                Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target);
            }
            return target;
        } catch (Exception ignored) {
            if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        } finally {
            if (temporary != null)
                try {
                    Files.deleteIfExists(temporary);
                } catch (java.io.IOException ignored) {
                }
        }
    }

    /** 为生成的封面文件选择合适的扩展名。 */
    private String extension(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    String extension = path.substring(dot).toLowerCase(Locale.ROOT);
                    if (extension.matches("\\.(png|jpe?g|webp|gif|bmp|img)")) {
                        return extension;
                    }
                }
            }
        } catch (Exception ignored) {
            // 未知后缀统一使用 .img，不影响 JavaFX 按内容加载图片。
        }
        return ".img";
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        List<Download> active;
        synchronized (this) {
            if (closed) return;
            closed = true;
            active = List.copyOf(downloads.values());
            downloads.clear();
        }
        active.forEach(download -> download.work.close());
        executor.shutdownNow();
        httpClient.shutdownNow();
        diskCache.close();
    }
}
