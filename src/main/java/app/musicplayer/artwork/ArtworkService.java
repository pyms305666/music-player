package app.musicplayer.artwork;

import app.musicplayer.util.Hashing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 下载并缓存封面图片，避免 JavaFX UI 类直接承担网络和文件操作。 */
public final class ArtworkService implements AutoCloseable {
    private final Path cacheDir;
    private final ExecutorService executor;
    private final HttpClient httpClient;

    public ArtworkService(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "artwork-cache");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .executor(executor)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isRemoteUrl(String source) {
        if (source == null) {
            return false;
        }
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    public Path cachedPath(String url) {
        return cacheDir.resolve(Hashing.sha1(url) + extension(url));
    }

    public CompletableFuture<Path> cache(String url) {
        if (!isRemoteUrl(url)) {
            return CompletableFuture.completedFuture(null);
        }
        Path target = cachedPath(url);
        if (Files.isRegularFile(target)) {
            return CompletableFuture.completedFuture(target);
        }
        return CompletableFuture.supplyAsync(() -> download(url, target), executor);
    }

    private Path download(String url, Path target) {
        try {
            if (Files.isRegularFile(target)) {
                return target;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() < 200
                    || response.statusCode() >= 400
                    || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                    || response.body() == null
                    || response.body().length == 0) {
                return null;
            }
            Files.createDirectories(cacheDir);
            Files.write(target, response.body());
            return target;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extension(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null) {
                int dot = path.lastIndexOf('.');
                if (dot >= 0 && dot < path.length() - 1) {
                    String extension = path.substring(dot).toLowerCase(Locale.ROOT);
                    if (extension.length() <= 8) {
                        return extension;
                    }
                }
            }
        } catch (Exception ignored) {
            // 未知后缀统一使用 .img，不影响 JavaFX 按内容加载图片。
        }
        return ".img";
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
