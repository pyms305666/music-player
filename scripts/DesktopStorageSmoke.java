import app.musicplayer.MusicPlayerApp;
import app.musicplayer.config.AppPaths;
import app.musicplayer.config.DownloadDirectorySettings;
import app.musicplayer.config.DownloadMigrationService;
import app.musicplayer.config.StorageInstanceLock;
import app.musicplayer.data.MusicDatabase;
import app.musicplayer.lyrics.LrcParser;
import app.musicplayer.model.OnlineTrackInfo;
import app.musicplayer.model.Track;
import app.musicplayer.ui.DownloadSettingsDialog;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 桌面存储控件烟雾。只用独立数据和偏好节点；系统文件选择入口用确定的夹具路径替代。 */
public final class DesktopStorageSmoke {
    private static MusicPlayerApp app;
    private static Stage stage;

    /** 运行实际设置控件、播放取消保护及关闭后启动迁移，不安装程序或操作个人音乐。 */
    public static void main(String[] args) throws Exception {
        System.setOut(
                new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));
        System.setErr(
                new java.io.PrintStream(System.err, true, java.nio.charset.StandardCharsets.UTF_8));
        Thread.ofPlatform()
                .daemon()
                .name("storage-smoke-watchdog")
                .start(
                        () -> {
                            try {
                                Thread.sleep(120000);
                            } catch (InterruptedException cancelled) {
                                return;
                            }
                            System.err.println(
                                    "Storage UI smoke timed out; stopping only this fixture"
                                            + " process");
                            Runtime.getRuntime().halt(2);
                        });
        Path data = Path.of(System.getProperty("musicplayer.data-dir"));
        if (args.length > 0 && args[0].equals("resume")) {
            resume(data);
            return;
        }
        Path first = data.resolveSibling("music-a"), second = data.resolveSibling("music-b");
        Files.createDirectories(data);
        Files.createDirectories(first);
        Files.createDirectories(second);
        Path legacy = wave(data.resolve("legacy.wav"));
        try (var database = new MusicDatabase(data.resolve("music-player.db"))) {
            database.saveTracks(List.of(new Track(legacy)));
            database.saveLyrics(new Track(legacy), LrcParser.parse("fixture", "[00:00.00]夹具歌词"));
        }
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            startWithLockPrompt(data);
            await(() -> fx(() -> !((List<?>) field("tracks")).isEmpty()), "library not restored");
            fx(
                    () -> {
                        play((Track) ((List<?>) field("tracks")).getFirst());
                        return null;
                    });
            await(() -> fx(() -> field("mediaPlayer") != null), "player not created");
            Object player = fx(() -> field("mediaPlayer"));
            AtomicReference<Path> chosen = new AtomicReference<>();
            AtomicBoolean exitRequested = new AtomicBoolean();
            installPicker(chosen, exitRequested);

            OnlineTrackInfo online =
                    new OnlineTrackInfo("kugou", "fixture", "artist", "", "", "id", "");
            fx(
                    () -> {
                        invoke("downloadAndPlayOnlineTrack", OnlineTrackInfo.class, online);
                        return null;
                    });
            check(
                    fx(() -> field("mediaPlayer")) == player,
                    "cancelling initial selection disposed player");
            check(
                    fx(
                            () ->
                                    ((DownloadDirectorySettings) field("downloadSettings"))
                                            .configured()
                                            .isEmpty()),
                    "cancel persisted a directory");
            System.out.println(
                    "PASS: cancelling first folder selection preserves playback and settings");

            chosen.set(first);
            openSelection();
            click("下载位置设置", "选择文件夹");
            await(() -> fx(() -> pane("确认音乐下载位置") != null), "directory confirmation missing");
            click("确认音乐下载位置", "取消");
            check(
                    fx(() -> field("mediaPlayer")) == player,
                    "cancelling confirmation disposed player");
            check(
                    fx(
                            () ->
                                    ((DownloadDirectorySettings) field("downloadSettings"))
                                            .configured()
                                            .isEmpty()),
                    "confirmation cancel persisted setting");
            System.out.println("PASS: cancelling folder confirmation preserves playback");

            openSelection();
            click("下载位置设置", "选择文件夹");
            await(() -> fx(() -> pane("确认音乐下载位置") != null), "second confirmation missing");
            fx(
                    () -> {
                        ((CheckBox) pane("确认音乐下载位置").lookupAll(".check-box").iterator().next())
                                .setSelected(false);
                        return null;
                    });
            click("确认音乐下载位置", "确定");
            await(
                    () ->
                            fx(
                                    () ->
                                            ((DownloadDirectorySettings) field("downloadSettings"))
                                                    .configured()
                                                    .map(path -> path.equals(first))
                                                    .orElse(false)),
                    "setting not saved");
            check(Files.exists(legacy), "future-only setting moved old audio");
            System.out.println(
                    "PASS: future-only directory setting is persistent and leaves legacy audio");

            Path moving = wave(first.resolve("迁移歌曲.wav"));
            Files.writeString(first.resolve("迁移歌曲.lrc"), "[00:00.00]同名歌词");
            MusicDatabase database = fx(() -> (MusicDatabase) field("database"));
            ExecutorService executor = fx(() -> (ExecutorService) field("libraryExecutor"));
            executor.submit(() -> database.saveTracks(List.of(new Track(moving))))
                    .get(15, TimeUnit.SECONDS);
            fx(
                    () -> {
                        var method = MusicPlayerApp.class.getDeclaredMethod("restoreSavedTracks");
                        method.setAccessible(true);
                        method.invoke(app);
                        return null;
                    });
            await(
                    () ->
                            fx(
                                    () ->
                                            ((List<?>) field("tracks"))
                                                    .stream()
                                                            .anyMatch(
                                                                    value ->
                                                                            ((Track) value)
                                                                                    .path()
                                                                                    .equals(
                                                                                            moving))),
                    "moving track not restored");
            fx(
                    () -> {
                        play(
                                (Track)
                                        ((List<?>) field("tracks"))
                                                .stream()
                                                        .filter(
                                                                value ->
                                                                        ((Track) value)
                                                                                .path()
                                                                                .equals(moving))
                                                        .findFirst()
                                                        .orElseThrow());
                        return null;
                    });
            chosen.set(second);
            openSelection();
            click("下载位置设置", "选择文件夹");
            await(() -> fx(() -> pane("确认音乐下载位置") != null), "migration confirmation missing");
            check(
                    fx(
                            () ->
                                    ((CheckBox)
                                                    pane("确认音乐下载位置")
                                                            .lookupAll(".check-box")
                                                            .iterator()
                                                            .next())
                                            .isSelected()),
                    "migration should be selected by default");
            click("确认音乐下载位置", "确定");
            await(() -> fx(() -> header("重新启动后迁移歌曲") != null), "restart message missing");
            clickHeader("重新启动后迁移歌曲", "确定");
            await(exitRequested::get, "exit callback missing");
            check(database.pendingDownloadMigration().isPresent(), "migration not scheduled");
            check(Files.exists(moving), "file moved before restart");
            stop();
            System.out.println(
                    "PASS: migration checkbox schedules durable work and exits gracefully");

            System.out.println(
                    "DESKTOP STORAGE PHASE 1 PASSED: 5 scenarios; waiting for a fresh process");
        } catch (Exception failure) {
            failure.printStackTrace();
            throw failure;
        } finally {
            try {
                if (app != null && !fx(() -> (Boolean) field("closing"))) stop();
            } catch (Exception ignored) {
                /* 保留原验证异常。 */
            }
            Platform.exit();
        }
    }

    /** 由第二个 JVM 检查已保存的启动迁移，真实进程退出后 Windows 播放句柄应已释放。 */
    private static void resume(Path data) throws Exception {
        Path first = data.resolveSibling("music-a"), second = data.resolveSibling("music-b");
        Path moving = first.resolve("迁移歌曲.wav"), legacy = data.resolve("legacy.wav");
        Platform.startup(() -> Platform.setImplicitExit(false));
        try {
            FutureTask<Void> restarted =
                    new FutureTask<>(
                            () -> {
                                app = application();
                                stage = new Stage();
                                app.start(stage);
                                return null;
                            });
            Platform.runLater(restarted);
            await(
                    () -> {
                        if (restarted.isDone()) {
                            restarted.get();
                            throw new IllegalStateException(
                                    "startup returned without migration confirmation");
                        }
                        return fx(() -> header("开始旧歌曲迁移") != null);
                    },
                    "startup migration confirmation missing");
            clickHeader("开始旧歌曲迁移", "确定");
            await(
                    () -> {
                        String error =
                                fx(
                                        () ->
                                                header("迁移未完成，文件保留") == null
                                                        ? null
                                                        : header("迁移未完成，文件保留").getContentText());
                        if (error != null)
                            throw new IllegalStateException("startup migration failed: " + error);
                        return restarted.isDone();
                    },
                    "startup migration did not finish");
            restarted.get(60, TimeUnit.SECONDS);
            check(Files.exists(second.resolve("迁移歌曲.wav")), "new audio missing");
            check(Files.exists(second.resolve("迁移歌曲.lrc")), "new sidecar missing");
            check(!Files.exists(moving), "source not cleaned after startup migration");
            check(Files.exists(legacy), "external legacy audio was incorrectly cleaned");
            check(
                    fx(
                            () ->
                                    ((DownloadDirectorySettings) field("downloadSettings"))
                                            .configured()
                                            .orElseThrow()
                                            .equals(second)),
                    "new setting not restored");
            check(
                    fx(
                            () ->
                                    ((MusicDatabase) field("database"))
                                            .pendingDownloadMigration()
                                            .isEmpty()),
                    "startup migration incomplete");
            System.out.println(
                    "PASS: real startup recovery migrates audio, sidecar and directory before"
                            + " services");
            stop();
            java.util.prefs.Preferences.userRoot()
                    .node(System.getProperty("musicplayer.preferences-node"))
                    .removeNode();
            System.out.println("DESKTOP STORAGE SMOKE PASSED: 6 scenarios");
        } finally {
            try {
                if (app != null && !fx(() -> (Boolean) field("closing"))) stop();
            } catch (Exception ignored) {
                /* 保留测试失败信息。 */
            }
            Platform.exit();
        }
    }

    /** 保持独立夹具锁制造占用，真实提示出现后释放并点击重试，再完成应用启动。 */
    private static void startWithLockPrompt(Path data) throws Exception {
        FutureTask<Void> startup =
                new FutureTask<>(
                        () -> {
                            app = application();
                            stage = new Stage();
                            app.start(stage);
                            return null;
                        });
        try (var busy = StorageInstanceLock.acquire(data, true)) {
            Platform.runLater(startup);
            await(
                    () -> fx(() -> header("应用数据目录暂时无法独占访问") != null),
                    "busy directory prompt missing");
            check(
                    fx(() -> field("database")) == null,
                    "ordinary services started before acquiring data lock");
        }
        clickHeader("应用数据目录暂时无法独占访问", "关闭其他播放器后重试");
        startup.get(15, TimeUnit.SECONDS);
        System.out.println(
                "PASS: occupied data directory prompts and retry starts only after lock release");
    }

    /** 与既有桌面烟雾一致，注册启动参数后才能直接调用 JavaFX Application.start。 */
    private static MusicPlayerApp application() throws Exception {
        MusicPlayerApp application = new MusicPlayerApp();
        Class<?> parameters = Class.forName("com.sun.javafx.application.ParametersImpl");
        Object value = parameters.getConstructor(List.class).newInstance(List.of());
        parameters
                .getMethod(
                        "registerParameters",
                        javafx.application.Application.class,
                        javafx.application.Application.Parameters.class)
                .invoke(null, application, value);
        return application;
    }

    /** 等待所有已提交写入和实例锁释放，然后才能启动下一实例。 */
    private static void stop() throws Exception {
        ExecutorService executor = fx(() -> (ExecutorService) field("libraryExecutor"));
        fx(
                () -> {
                    app.stop();
                    stage.hide();
                    return null;
                });
        check(executor.awaitTermination(30, TimeUnit.SECONDS), "application library did not close");
    }

    /** 文件夹入口只替换原生选择步骤，设置弹窗和真实后台校验均正常执行。 */
    private static void installPicker(AtomicReference<Path> chosen, AtomicBoolean exitRequested)
            throws Exception {
        fx(
                () -> {
                    var settings = (DownloadDirectorySettings) field("downloadSettings");
                    var paths = AppPaths.resolve(MusicPlayerApp.class);
                    var dialog =
                            new DownloadSettingsDialog(
                                    settings,
                                    new DownloadMigrationService(
                                            (MusicDatabase) field("database"),
                                            settings,
                                            paths.dataDir()),
                                    (ExecutorService) field("libraryExecutor"),
                                    () -> false,
                                    () -> exitRequested.set(true),
                                    ignored -> {},
                                    ignored -> chosen.get());
                    var field = MusicPlayerApp.class.getDeclaredField("downloadSettingsDialog");
                    field.setAccessible(true);
                    field.set(app, dialog);
                    return null;
                });
    }

    /** 点击主窗口真实下载位置入口，不直接调用保存方法。 */
    private static void openSelection() throws Exception {
        Platform.runLater(() -> ((Button) stage.getScene().lookup("#download-settings")).fire());
        await(() -> fx(() -> pane("下载位置设置") != null), "settings missing");
    }

    /** 查找当前进程的自有 JavaFX 弹窗，不读取其他应用。 */
    private static DialogPane pane(String title) {
        for (Window window : Window.getWindows())
            if (window instanceof Stage candidate
                    && candidate.isShowing()
                    && title.equals(candidate.getTitle())
                    && candidate.getScene().getRoot() instanceof DialogPane pane) return pane;
        return null;
    }

    /** 标准 Alert 标题会受语言影响，启动页按稳定的中文 header 查找。 */
    private static DialogPane header(String text) {
        for (Window window : Window.getWindows())
            if (window.isShowing()
                    && window.getScene().getRoot() instanceof DialogPane pane
                    && text.equals(pane.getHeaderText())) return pane;
        return null;
    }

    /** 触发指定弹窗的实际按钮操作。 */
    private static void click(String title, String text) throws Exception {
        Platform.runLater(() -> fire(pane(title), text));
    }

    /** 触发标准 Alert 中的实际按钮。 */
    private static void clickHeader(String title, String text) throws Exception {
        Platform.runLater(() -> fire(header(title), text));
    }

    /** 使用界面标签定位按钮，兼容中文和默认英文确认标签。 */
    private static void fire(DialogPane pane, String text) {
        check(pane != null, "dialog missing");
        for (ButtonType type : pane.getButtonTypes())
            if (type.getText().equals(text)
                    || (text.equals("确定") && type == ButtonType.OK)
                    || (text.equals("取消") && type == ButtonType.CANCEL)) {
                ((Button) pane.lookupButton(type)).fire();
                return;
            }
        throw new IllegalStateException("Button not found: " + text);
    }

    /** 读取烟雾所需的状态，测试保持行为断言而不改应用内部字段语义。 */
    private static Object field(String name) throws Exception {
        var field = MusicPlayerApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(app);
    }

    /** 调用既有下载操作，检验取消选择确实先于播放器释放。 */
    private static void invoke(String name, Class<?> type, Object argument) throws Exception {
        var method = MusicPlayerApp.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        method.invoke(app, argument);
    }

    /** 用真实播放器加载静音夹具，后续比较播放器实例是否被取消操作替换。 */
    private static void play(Track track) throws Exception {
        invoke("playTrack", Track.class, track);
    }

    /** 所有 JavaFX 读写都切到界面线程，等待只发生在驱动线程。 */
    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    /** 带超时等待异步界面，失败不会让验证进程无限悬挂。 */
    private static void await(Callable<Boolean> condition, String message) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < end) {
            if (condition.call()) return;
            Thread.sleep(50);
        }
        System.err.println(
                "Visible fixture dialogs: "
                        + fx(
                                () ->
                                        Window.getWindows().stream()
                                                .filter(Window::isShowing)
                                                .map(
                                                        window -> {
                                                            String title =
                                                                    window instanceof Stage value
                                                                            ? value.getTitle()
                                                                            : window.getClass()
                                                                                    .getSimpleName();
                                                            return title
                                                                    + " / "
                                                                    + (window.getScene().getRoot()
                                                                                    instanceof
                                                                                    DialogPane pane
                                                                            ? pane.getHeaderText()
                                                                                    + " / "
                                                                                    + pane
                                                                                            .getContentText()
                                                                            : window.getScene()
                                                                                    .getRoot()
                                                                                    .getClass()
                                                                                    .getName());
                                                        })
                                                .toList()));
        throw new IllegalStateException(message);
    }

    /** 验证失败返回非零进程退出码。 */
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** 两分钟静音 WAV 避免测试过程中自然播完影响播放器身份断言。 */
    private static Path wave(Path file) throws Exception {
        int bytes = 8000 * 120 * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .putInt(bytes + 36)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        buffer.putInt(16)
                .putShort((short) 1)
                .putShort((short) 1)
                .putInt(8000)
                .putInt(16000)
                .putShort((short) 2)
                .putShort((short) 16);
        buffer.put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(bytes);
        return Files.write(file, buffer.array());
    }
}
