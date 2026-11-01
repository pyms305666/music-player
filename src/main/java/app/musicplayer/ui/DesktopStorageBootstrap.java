package app.musicplayer.ui;

import app.musicplayer.config.*;
import app.musicplayer.data.MusicDatabase;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** 启动迁移协调器。先独占并恢复文件与数据库，再启动普通播放服务，后台执行器由本组件关闭。 */
public final class DesktopStorageBootstrap {
    /** 返回给应用的正常运行资源，退出时数据库与实例锁必须一起释放。 */
    public record Session(MusicDatabase database, StorageInstanceLock lock)
            implements AutoCloseable {
        /** 关闭数据库后释放实例锁，不提前允许其他迁移接管仍在写入的连接。 */
        @Override
        public void close() throws IOException {
            try {
                database.close();
            } finally {
                lock.close();
            }
        }
    }

    /** 本类型只提供启动入口，不持有跨次启动的静态资源。 */
    private DesktopStorageBootstrap() {}

    /** 正常启动共享实例锁；发现待办时关闭自己的连接并改为独占迁移。 */
    public static Session open(Stage owner, AppPaths paths, DownloadDirectorySettings settings)
            throws Exception {
        StorageInstanceLock lock = acquireLock(owner, paths.dataDir(), true);
        MusicDatabase database = null;
        try {
            database = new MusicDatabase(paths.databasePath());
            if (database.pendingDownloadMigration().isEmpty()) return new Session(database, lock);
            database.close();
            database = null;
            lock.close();
            lock = acquireLock(owner, paths.dataDir(), false);
            database = new MusicDatabase(paths.databasePath());
            recover(
                    owner,
                    new DownloadMigrationService(database, settings, paths.dataDir()),
                    database);
            database.close();
            database = null;
            lock.close();
            lock = acquireLock(owner, paths.dataDir(), true);
            database = new MusicDatabase(paths.databasePath());
            return new Session(database, lock);
        } catch (Exception failure) {
            if (database != null) database.close();
            try {
                lock.close();
            } catch (IOException ignored) {
                /* 获取失败时锁可能已经释放。 */
            }
            throw failure;
        }
    }

    /** 有其他实例时给出关闭后重试入口，不因锁失败静默退出或强制结束其他进程。 */
    private static StorageInstanceLock acquireLock(
            Stage owner, java.nio.file.Path dataDirectory, boolean shared) throws IOException {
        while (true) {
            try {
                return StorageInstanceLock.acquire(dataDirectory, shared);
            } catch (IOException failure) {
                ButtonType retry = new ButtonType("关闭其他播放器后重试"), exit = new ButtonType("退出");
                Alert alert = new Alert(Alert.AlertType.ERROR, failure.getMessage(), retry, exit);
                attachOwner(alert, owner);
                alert.setHeaderText("应用数据目录暂时无法独占访问");
                if (alert.showAndWait().orElse(exit) != retry)
                    throw new MigrationExit(failure.getMessage());
            }
        }
    }

    /** 每次读取最新持久化阶段，重试不会拿过时状态重复提交数据库。 */
    private static void recover(
            Stage owner, DownloadMigrationService service, MusicDatabase database)
            throws Exception {
        while (database.pendingDownloadMigration().isPresent()) {
            var migration = database.pendingDownloadMigration().orElseThrow();
            try {
                if (migration.status().equals("PLANNED")) {
                    var payload =
                            com.google.gson.JsonParser.parseString(migration.payload())
                                    .getAsJsonObject();
                    var preview =
                            task(
                                    owner,
                                    "确认重启后的迁移清单",
                                    (cancel, progress) -> {
                                        var value =
                                                service.preview(
                                                        java.nio.file.Path.of(
                                                                payload.get("source")
                                                                        .getAsString()),
                                                        java.nio.file.Path.of(
                                                                payload.get("target")
                                                                        .getAsString()));
                                        if (cancel.get())
                                            throw new java.util.concurrent.CancellationException();
                                        return value;
                                    });
                    Alert confirm =
                            new Alert(
                                    Alert.AlertType.CONFIRMATION,
                                    preview.songs()
                                            + " 首歌曲及配套歌词将迁移到：\n"
                                            + payload.get("target").getAsString()
                                            + "\n迁移成功后清理旧音频，数据库和缓存保留。\n缺失或链接记录 "
                                            + preview.skipped().size()
                                            + " 项不迁移，详见展开清单。",
                                    ButtonType.OK,
                                    ButtonType.CANCEL);
                    attachOwner(confirm, owner);
                    confirm.setHeaderText("开始旧歌曲迁移");
                    ListView<String> files =
                            new ListView<>(
                                    javafx.collections.FXCollections.observableArrayList(
                                            preview.displayPaths()));
                    files.setPrefHeight(200);
                    confirm.getDialogPane().setExpandableContent(files);
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        service.dismiss(migration);
                        continue;
                    }
                }
                var result =
                        task(
                                owner,
                                "正在迁移旧歌曲",
                                (cancel, progress) ->
                                        service.execute(migration, cancel::get, progress));
                if (result.remaining().isEmpty()) continue;
                ButtonType retry = new ButtonType("重试清理"),
                        keep = new ButtonType("保留旧副本并完成迁移"),
                        exit = new ButtonType("退出，下次重试");
                Alert alert =
                        new Alert(
                                Alert.AlertType.WARNING,
                                String.join("\n", result.remaining()),
                                retry,
                                keep,
                                exit);
                attachOwner(alert, owner);
                alert.setHeaderText("新曲库已生效，部分旧文件尚未清理");
                ButtonType selected = alert.showAndWait().orElse(exit);
                if (selected == keep)
                    service.dismiss(database.pendingDownloadMigration().orElseThrow());
                else if (selected == exit) throw new MigrationExit("迁移清理未完成，下次启动可以继续");
            } catch (Exception failure) {
                if (failure instanceof MigrationExit) throw failure;
                if (failure instanceof java.util.concurrent.CancellationException
                        && migration.status().equals("PLANNED")) {
                    service.dismiss(migration);
                    continue;
                }
                java.util.logging.Logger.getLogger(DesktopStorageBootstrap.class.getName())
                        .log(java.util.logging.Level.WARNING, "启动迁移失败，文件保留", failure);
                ButtonType retry = new ButtonType("重试"),
                        cancel = new ButtonType("保留文件并取消剩余迁移"),
                        exit = new ButtonType("退出");
                Alert alert =
                        new Alert(Alert.AlertType.ERROR, failure.getMessage(), retry, cancel, exit);
                attachOwner(alert, owner);
                alert.setHeaderText("迁移未完成，文件保留");
                ButtonType choice = alert.showAndWait().orElse(exit);
                if (choice == cancel)
                    service.dismiss(database.pendingDownloadMigration().orElseThrow());
                else if (choice == exit) throw failure;
            }
        }
    }

    /** 用户已明确选择退出，直接交回启动流程，不再次弹出相同错误确认。 */
    private static final class MigrationExit extends IOException {
        /** 保留待办说明，下次启动仍可恢复。 */
        MigrationExit(String message) {
            super(message);
        }
    }

    /** 后台任务签名：取消由当前弹窗提供，进度由界面线程接收。 */
    @FunctionalInterface
    private interface Work<T> {
        /** 执行本次启动工作，异常由迁移恢复页处理。 */
        T run(AtomicBoolean cancel, Consumer<DownloadMigrationService.Progress> progress)
                throws Exception;
    }

    /** 模态进度页仍响应界面事件，取消后等待任务安全返回，不中途关闭数据库。 */
    private static <T> T task(Stage owner, String title, Work<T> work) throws Exception {
        Dialog<Void> dialog = new Dialog<>();
        attachOwner(dialog, owner);
        dialog.setTitle(title);
        Label label = new Label(title);
        label.setWrapText(true);
        ProgressBar bar = new ProgressBar();
        bar.setPrefWidth(500);
        dialog.getDialogPane().setContent(new VBox(12, label, bar));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        AtomicBoolean cancelled = new AtomicBoolean(), finished = new AtomicBoolean();
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        dialog.setOnCloseRequest(
                event -> {
                    if (!finished.get()) {
                        cancelled.set(true);
                        label.setText("正在安全停止，请稍候…");
                        event.consume();
                    }
                });
        var worker =
                Executors.newSingleThreadExecutor(
                        runnable -> new Thread(runnable, "storage-migration"));
        Runnable operation =
                () -> {
                    long[] last = {0};
                    try {
                        value.set(
                                work.run(
                                        cancelled,
                                        progress -> {
                                            long now = System.nanoTime();
                                            if (now - last[0] < 150_000_000L) return;
                                            last[0] = now;
                                            Platform.runLater(
                                                    () -> {
                                                        label.setText(
                                                                progress.phase()
                                                                        + "\n"
                                                                        + progress.file());
                                                        bar.setProgress(
                                                                progress.total() > 0
                                                                        ? (double) progress.bytes()
                                                                                / progress.total()
                                                                        : -1);
                                                    });
                                        }));
                    } catch (Exception failure) {
                        error.set(failure);
                    } finally {
                        worker.shutdown();
                        Platform.runLater(
                                () -> {
                                    finished.set(true);
                                    dialog.close();
                                });
                    }
                };
        // 先进入弹窗事件循环再启动快任务，防止任务完成时弹窗尚未注册事件循环。
        var delay = new javafx.animation.PauseTransition(javafx.util.Duration.millis(50));
        delay.setOnFinished(event -> worker.execute(operation));
        dialog.setOnShown(event -> delay.play());
        try {
            dialog.showAndWait();
        } finally {
            delay.stop();
            if (!finished.get()) {
                cancelled.set(true);
                worker.shutdownNow();
            } else worker.shutdown();
            while (!worker.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                /* 数据库不能在本组件任务结束前关闭。 */
            }
        }
        if (error.get() != null) throw error.get();
        return value.get();
    }

    /** 启动服务之前主窗口尚未创建 Scene，JavaFX 此时不能直接绑定 Dialog owner。 */
    private static void attachOwner(Dialog<?> dialog, Stage owner) {
        if (owner != null && owner.getScene() != null) dialog.initOwner(owner);
    }
}
