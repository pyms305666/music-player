package app.musicplayer.artwork;

import app.musicplayer.util.LatestRequest;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 桌面封面显示协调器。按窗口缩放比例选择解码尺寸，过滤过期加载结果，管理显示句柄及窗口监听。 One download path and sized background decode, with
 * lifecycle-owned cancellation.
 */
public final class ArtworkPresenter implements AutoCloseable {
    private final ArtworkService service;
    private final ImageView view;
    private final DecodedArtworkCache cache = new DecodedArtworkCache();
    private final LatestRequest<DecodedArtwork> decode = new LatestRequest<>();
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(
                    runnable -> {
                        Thread thread = new Thread(runnable, "artwork-decode");
                        thread.setDaemon(true);
                        return thread;
                    });
    private ArtworkService.Request request;
    private String currentKey;
    private String currentSource;
    private String resolvedSource;
    private int[] currentSize;
    private boolean closed;
    private Window window;
    private Consumer<Color> onColorExtracted;
    private final ChangeListener<Number> scaleChanged = (ignored, old, next) -> show(currentSource);
    private final ChangeListener<Number> sizeChanged = (ignored, old, next) -> show(currentSource);

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public ArtworkPresenter(ArtworkService service, ImageView view) {
        this.service = service;
        this.view = view;
        view.fitWidthProperty().addListener(sizeChanged);
        view.fitHeightProperty().addListener(sizeChanged);
    }

    /** 设置提取到主色调时的回调监听器。 */
    public void setOnColorExtracted(Consumer<Color> listener) {
        this.onColorExtracted = listener;
    }

    /** 展示窗口或当前歌单条目，并保留有效选择状态。 */
    public void show(String source) {
        if (closed) return;
        observeWindow();
        int[] size = decodeSize(view.getFitWidth(), view.getFitHeight(), outputScale());
        String key =
                source == null || source.isBlank() ? null : source + "\n" + size[0] + "x" + size[1];
        if (Objects.equals(key, currentKey)) return;
        boolean sourceChanged = !Objects.equals(source, currentSource);
        currentSource = source;
        currentKey = key;
        currentSize = size;
        if (sourceChanged) {
            resolvedSource = null;
            if (request != null) {
                request.close();
                request = null;
            }
        }
        decode.close();
        display(null);
        if (closed || key == null) {
            Platform.runLater(
                    () -> {
                        if (onColorExtracted != null) {
                            onColorExtracted.accept(DominantColorExtractor.DEFAULT_AMBIENT_COLOR);
                        }
                    });
            return;
        }
        Image existing = cache.get(key);
        if (existing != null) {
            display(existing);
            worker.execute(
                    () -> {
                        Color ambient = DominantColorExtractor.extract(existing);
                        Platform.runLater(
                                () -> {
                                    if (!closed
                                            && Objects.equals(key, currentKey)
                                            && onColorExtracted != null) {
                                        onColorExtracted.accept(ambient);
                                    }
                                });
                    });
            return;
        }
        if (resolvedSource != null) {
            load(key, resolvedSource, size);
            return;
        }
        if (service.isRemoteUrl(source)) {
            if (request != null) return;
            request = service.acquire(source);
            request.result()
                    .thenAccept(
                            path ->
                                    Platform.runLater(
                                            () -> {
                                                if (!closed
                                                        && Objects.equals(source, currentSource)) {
                                                    if (path != null) {
                                                        resolvedSource = path.toUri().toString();
                                                        load(currentKey, resolvedSource, currentSize);
                                                    } else if (onColorExtracted != null) {
                                                        onColorExtracted.accept(
                                                                DominantColorExtractor.DEFAULT_AMBIENT_COLOR);
                                                    }
                                                }
                                            }));
        } else load(key, source, size);
    }

    /** 加载当前组件需要的文件、歌单或界面数据，并返回结果。 */
    private void load(String key, String source, int[] size) {
        decode.submit(
                        worker,
                        () -> {
                            Image image = ArtworkDecoder.decode(source, size[0], size[1]);
                            Color ambient = DominantColorExtractor.extract(image);
                            return new DecodedArtwork(image, ambient);
                        })
                .thenAccept(
                        result ->
                                Platform.runLater(
                                        () -> {
                                            if (closed || !Objects.equals(key, currentKey)) return;
                                            cache.put(key, result.image());
                                            display(result.image());
                                            if (onColorExtracted != null) {
                                                onColorExtracted.accept(result.ambient());
                                            }
                                        }));
    }

    /** 取得窗口输出缩放比例，供封面解码尺寸计算。 */
    private double outputScale() {
        return window == null ? 1 : Math.max(window.getOutputScaleX(), window.getOutputScaleY());
    }

    /** 监听窗口变化，按显示尺度更新封面请求。 */
    private void observeWindow() {
        if (window != null || view.getScene() == null || view.getScene().getWindow() == null)
            return;
        window = view.getScene().getWindow();
        window.outputScaleXProperty().addListener(scaleChanged);
        window.outputScaleYProperty().addListener(scaleChanged);
    }

    /** 计算目标解码尺寸，并限制像素边界。 */
    public static int[] decodeSize(double width, double height, double scale) {
        double w = Math.max(1, width) * Math.max(1, scale);
        double h = Math.max(1, height) * Math.max(1, scale);
        double factor = Math.min(1, ArtworkDecoder.MAX_EDGE / Math.max(w, h));
        return new int[] {
            Math.max(1, (int) Math.round(w * factor)), Math.max(1, (int) Math.round(h * factor))
        };
    }

    /** 把当前有效结果显示到界面，释放不再需要的旧句柄。 */
    private void display(Image image) {
        view.setImage(image);
        view.setVisible(image != null);
    }

    /** 取消显示请求，释放当前图片句柄，并解除窗口缩放监听。 */
    @Override
    public void close() {
        closed = true;
        currentKey = null;
        currentSource = null;
        if (request != null) request.close();
        decode.close();
        worker.shutdownNow();
        cache.clear();
        view.fitWidthProperty().removeListener(sizeChanged);
        view.fitHeightProperty().removeListener(sizeChanged);
        if (window != null) {
            window.outputScaleXProperty().removeListener(scaleChanged);
            window.outputScaleYProperty().removeListener(scaleChanged);
        }
        display(null);
    }

    private record DecodedArtwork(Image image, Color ambient) {}
}
