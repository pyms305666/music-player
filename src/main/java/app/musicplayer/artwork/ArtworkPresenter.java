package app.musicplayer.artwork;

import app.musicplayer.util.LatestRequest;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Window;
import javafx.beans.value.ChangeListener;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** One download path and sized background decode, with lifecycle-owned cancellation. */
public final class ArtworkPresenter implements AutoCloseable {
    private final ArtworkService service;
    private final ImageView view;
    private final DecodedArtworkCache cache = new DecodedArtworkCache();
    private final LatestRequest<Image> decode = new LatestRequest<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
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
    private final ChangeListener<Number> scaleChanged = (ignored, old, next) -> show(currentSource);
    private final ChangeListener<Number> sizeChanged = (ignored, old, next) -> show(currentSource);

    public ArtworkPresenter(ArtworkService service, ImageView view) {
        this.service = service;
        this.view = view;
        view.fitWidthProperty().addListener(sizeChanged);
        view.fitHeightProperty().addListener(sizeChanged);
    }

    public void show(String source) {
        if (closed) return;
        observeWindow();
        int[] size = decodeSize(view.getFitWidth(), view.getFitHeight(), outputScale());
        String key = source == null || source.isBlank() ? null : source + "\n" + size[0] + "x" + size[1];
        if (Objects.equals(key, currentKey)) return;
        boolean sourceChanged = !Objects.equals(source, currentSource);
        currentSource = source;
        currentKey = key;
        currentSize = size;
        if (sourceChanged) {
            resolvedSource = null;
            if (request != null) { request.close(); request = null; }
        }
        decode.close();
        display(null);
        if (closed || key == null) return;
        Image existing = cache.get(key);
        if (existing != null) { display(existing); return; }
        if (resolvedSource != null) { load(key, resolvedSource, size); return; }
        if (service.isRemoteUrl(source)) {
            if (request != null) return;
            request = service.acquire(source);
            request.result().thenAccept(path -> Platform.runLater(() -> {
                if (!closed && Objects.equals(source, currentSource) && path != null) {
                    resolvedSource = path.toUri().toString();
                    load(currentKey, resolvedSource, currentSize);
                }
            }));
        } else load(key, source, size);
    }

    private void load(String key, String source, int[] size) {
        decode.submit(worker, () -> ArtworkDecoder.decode(source, size[0], size[1]))
                .thenAccept(image -> Platform.runLater(() -> {
            if (closed || !Objects.equals(key, currentKey)) return;
            cache.put(key, image);
            display(image);
        }));
    }

    private double outputScale() {
        return window == null ? 1 : Math.max(window.getOutputScaleX(), window.getOutputScaleY());
    }

    private void observeWindow() {
        if (window != null || view.getScene() == null || view.getScene().getWindow() == null) return;
        window = view.getScene().getWindow();
        window.outputScaleXProperty().addListener(scaleChanged);
        window.outputScaleYProperty().addListener(scaleChanged);
    }

    public static int[] decodeSize(double width, double height, double scale) {
        double w = Math.max(1, width) * Math.max(1, scale);
        double h = Math.max(1, height) * Math.max(1, scale);
        double factor = Math.min(1, ArtworkDecoder.MAX_EDGE / Math.max(w, h));
        return new int[]{Math.max(1, (int) Math.round(w * factor)), Math.max(1, (int) Math.round(h * factor))};
    }

    private void display(Image image) { view.setImage(image); view.setVisible(image != null); }

    @Override public void close() {
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
}
