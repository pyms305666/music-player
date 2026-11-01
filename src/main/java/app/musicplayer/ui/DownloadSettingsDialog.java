package app.musicplayer.ui;

import app.musicplayer.config.DownloadDirectorySettings;
import app.musicplayer.config.DownloadMigrationService;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** 下载设置和首次选择的界面。借用曲库执行器，文件检查和偏好写入离开界面线程。 */
public final class DownloadSettingsDialog {
    private final DownloadDirectorySettings settings;
    private final DownloadMigrationService migration;
    private final Executor io;
    private final BooleanSupplier closing;
    private final Runnable exit;
    private final Consumer<Path> openFolder;
    private final Function<Stage, Path> picker;
    private boolean choosing;

    /** 资源仍由主应用管理；本组件不拥有数据库、执行器或播放器。 */
    public DownloadSettingsDialog(
            DownloadDirectorySettings settings,
            DownloadMigrationService migration,
            Executor io,
            BooleanSupplier closing,
            Runnable exit,
            Consumer<Path> openFolder) {
        this(settings, migration, io, closing, exit, openFolder, null);
    }

    /** 可注入文件夹选择入口，界面回归使用独立夹具，正式运行仍使用系统选择窗口。 */
    public DownloadSettingsDialog(
            DownloadDirectorySettings settings,
            DownloadMigrationService migration,
            Executor io,
            BooleanSupplier closing,
            Runnable exit,
            Consumer<Path> openFolder,
            Function<Stage, Path> picker) {
        this.settings = settings;
        this.migration = migration;
        this.io = io;
        this.closing = closing;
        this.exit = exit;
        this.openFolder = openFolder;
        this.picker = picker;
    }

    /** 显示当前保存位置；选择另一个盘和文件夹时会确认旧歌曲处理方式。 */
    public void show(Stage owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("下载位置设置");
        String description;
        try {
            description =
                    settings.configured()
                            .map(Path::toString)
                            .orElse("尚未选择，首次下载时将询问。\n建议位置：" + settings.selectionSuggestion());
        } catch (java.nio.file.InvalidPathException failure) {
            description = "保存的路径无效，请重新选择文件夹。";
        }
        Label path = new Label("音乐保存位置：\n" + description);
        path.setWrapText(true);
        Label note = new Label("数据库和应用缓存仍由用户数据目录管理。更改位置时可一起迁移旧歌曲。");
        note.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(12, path, note));
        ButtonType choose = new ButtonType("选择文件夹", ButtonBar.ButtonData.OK_DONE);
        ButtonType open = new ButtonType("打开文件夹", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(choose, open, ButtonType.CANCEL);
        dialog.showAndWait()
                .ifPresent(
                        result -> {
                            if (result == choose) choose(owner, ignored -> {});
                            else if (result == open)
                                openFolder.accept(settings.selectionSuggestion());
                        });
    }

    /** 下载前捕获一次有效目录；取消或不可用时不打断当前播放、不回退到 C 盘。 */
    public void resolve(Stage owner, Consumer<Path> accepted) {
        try {
            var configured = settings.configured();
            if (configured.isEmpty()) {
                choose(owner, accepted);
                return;
            }
            CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return settings.validate(configured.get());
                                } catch (IOException failure) {
                                    throw new java.util.concurrent.CompletionException(failure);
                                }
                            },
                            io)
                    .whenComplete(
                            (directory, failure) ->
                                    Platform.runLater(
                                            () -> {
                                                if (closing.getAsBoolean()) return;
                                                if (failure == null) accepted.accept(directory);
                                                else
                                                    unavailable(
                                                            owner,
                                                            failure,
                                                            () -> choose(owner, accepted));
                                            }));
        } catch (RuntimeException failure) {
            unavailable(owner, failure, () -> choose(owner, accepted));
        }
    }

    /** 选择与确认两步独立，取消任何确认步骤都保留旧设置。 */
    private void choose(Stage owner, Consumer<Path> accepted) {
        if (choosing || closing.getAsBoolean()) return;
        Path folder = pick(owner);
        if (folder == null) return;
        choosing = true;
        Dialog<Void> waiting = new Dialog<>();
        waiting.initOwner(owner);
        waiting.setTitle("检查音乐目录");
        waiting.getDialogPane()
                .setContent(new VBox(10, new Label("正在检查写入权限及旧目录歌曲…"), new ProgressIndicator()));
        waiting.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var finished = new java.util.concurrent.atomic.AtomicBoolean();
        waiting.setOnCloseRequest(
                event -> {
                    if (!finished.get()) {
                        cancelled.set(true);
                        event.consume();
                    }
                });
        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                Path target = settings.validate(folder);
                                Path source = settings.selectionSuggestion();
                                long available = Files.getFileStore(target).getUsableSpace();
                                if (!Files.isDirectory(source))
                                    return new Selection(
                                            source,
                                            target,
                                            new DownloadMigrationService.Preview(
                                                    0, 0, java.util.List.of()),
                                            true,
                                            available);
                                Path realSource =
                                        DownloadDirectorySettings.resolveExistingParent(source);
                                boolean overlap =
                                        realSource.startsWith(target)
                                                || target.startsWith(realSource);
                                var preview =
                                        overlap
                                                ? new DownloadMigrationService.Preview(
                                                        0, 0, java.util.List.of())
                                                : migration.preview(source, target);
                                return new Selection(
                                        source,
                                        target,
                                        preview,
                                        overlap && !realSource.equals(target),
                                        available);
                            } catch (IOException failure) {
                                throw new java.util.concurrent.CompletionException(failure);
                            }
                        },
                        io)
                .whenComplete(
                        (selection, failure) ->
                                Platform.runLater(
                                        () -> {
                                            finished.set(true);
                                            waiting.close();
                                            choosing = false;
                                            if (closing.getAsBoolean() || cancelled.get()) return;
                                            if (failure != null) {
                                                unavailable(
                                                        owner,
                                                        failure,
                                                        () -> choose(owner, accepted));
                                                return;
                                            }
                                            Platform.runLater(
                                                    () -> {
                                                        if (!closing.getAsBoolean())
                                                            confirm(owner, selection, accepted);
                                                    });
                                        }));
        waiting.show();
    }

    /** 保留用户确认所依据的旧根、新根与扫描结果。 */
    private record Selection(
            Path source,
            Path target,
            DownloadMigrationService.Preview preview,
            boolean overlap,
            long available) {}

    /** 系统选择窗口只在界面线程调用；测试替代入口不影响后续真实文件检查。 */
    private Path pick(Stage owner) {
        if (picker != null) return picker.apply(owner);
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择音乐下载盘和文件夹");
        Path suggestion = settings.selectionSuggestion();
        if (Files.isDirectory(suggestion)) chooser.setInitialDirectory(suggestion.toFile());
        var folder = chooser.showDialog(owner);
        return folder == null ? null : folder.toPath();
    }

    /** 默认勾选迁移，但只有用户确认后才保存待办；重启前不移动旧文件。 */
    private void confirm(Stage owner, Selection selection, Consumer<Path> accepted) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("确认音乐下载位置");
        Label locations = new Label("旧位置：" + selection.source + "\n新位置：" + selection.target);
        locations.setWrapText(true);
        CheckBox migrate = new CheckBox("一起迁移旧歌曲，成功后清理旧文件（需要重新启动）");
        migrate.setSelected(selection.preview.songs() > 0);
        migrate.setDisable(selection.preview.songs() == 0);
        Label summary =
                new Label(
                        selection.overlap
                                ? "旧目录不可用或新旧目录互相包含，本次只修改未来下载位置，已有文件保持原位。"
                                : selection.preview.songs()
                                        + " 首歌曲及配套歌词，共 "
                                        + String.format(
                                                java.util.Locale.ROOT,
                                                "%.1f MiB",
                                                selection.preview.bytes() / 1048576.0));
        Label available =
                new Label(
                        String.format(
                                java.util.Locale.ROOT,
                                "目标磁盘可用空间：%.1f GiB（执行时会再次检查）",
                                selection.available / 1073741824.0));
        ListView<String> files =
                new ListView<>(
                        javafx.collections.FXCollections.observableArrayList(
                                selection.preview.displayPaths()));
        files.setPrefHeight(180);
        Label note =
                new Label(
                        "清单只包含旧下载目录中的音乐，其他文件夹的导入歌曲保持原位置。未迁移记录 "
                                + selection.preview.skipped().size()
                                + " 项。迁移在下次启动、播放与下载服务初始化前执行。");
        note.setWrapText(true);
        dialog.getDialogPane()
                .setContent(new VBox(10, locations, summary, available, migrate, files, note));
        dialog.getDialogPane().setPrefWidth(620);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        boolean moving = migrate.isSelected();
        CompletableFuture.runAsync(
                        () -> {
                            try {
                                if (moving) migration.schedule(selection.source, selection.target);
                                else {
                                    migration.ensureNoPending();
                                    settings.save(selection.target);
                                }
                            } catch (IOException failure) {
                                throw new java.util.concurrent.CompletionException(failure);
                            }
                        },
                        io)
                .whenComplete(
                        (ignored, failure) ->
                                Platform.runLater(
                                        () -> {
                                            if (closing.getAsBoolean()) return;
                                            if (failure != null) {
                                                unavailable(
                                                        owner,
                                                        failure,
                                                        () -> choose(owner, accepted));
                                                return;
                                            }
                                            if (moving) {
                                                Alert message =
                                                        new Alert(
                                                                Alert.AlertType.INFORMATION,
                                                                "迁移计划已保存。播放器将正常退出，请重新打开程序完成复制、路径更新和旧文件清理。",
                                                                ButtonType.OK);
                                                message.initOwner(owner);
                                                message.setHeaderText("重新启动后迁移歌曲");
                                                message.showAndWait();
                                                exit.run();
                                            } else accepted.accept(selection.target);
                                        }));
    }

    /** 错误提供重新选择入口，不在未告知用户时换盘下载。 */
    private static void unavailable(Stage owner, Throwable failure, Runnable choose) {
        while (failure.getCause() != null) failure = failure.getCause();
        ButtonType select = new ButtonType("重新选择文件夹", ButtonBar.ButtonData.OK_DONE);
        Alert alert =
                new Alert(Alert.AlertType.ERROR, failure.getMessage(), select, ButtonType.CANCEL);
        alert.initOwner(owner);
        alert.setHeaderText("音乐目录不可用");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == select) choose.run();
    }
}
