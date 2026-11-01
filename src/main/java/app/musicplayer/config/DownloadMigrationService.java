package app.musicplayer.config;

import app.musicplayer.data.MusicDatabase;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 下载目录文件迁移和恢复。借用数据库及设置，不创建后台线程；由启动协调器在独占阶段调用。 复制完成前保留旧曲库，提交后才保存新设置并清理源文件，进程中断可从数据库清单恢复。 */
public final class DownloadMigrationService {
    private static final Gson JSON = new Gson();
    private static final Set<String> AUDIO =
            Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff", "flac", "ogg", "opus", "wma");
    private final MusicDatabase database;
    private final DownloadDirectorySettings settings;
    private final Path dataDirectory;

    /** 展示迁移范围；paths 只用于用户确认，实际文件在独占启动阶段重新扫描。 */
    public record Preview(int songs, long bytes, List<String> paths, List<String> skipped) {
        /** 没有可迁移旧根时只提供目录设置提示。 */
        public Preview(int songs, long bytes, List<String> paths) {
            this(songs, bytes, paths, List.of());
        }

        /** 确认清单同时列出未迁移记录，不把缺失或链接路径当作成功项目。 */
        public List<String> displayPaths() {
            List<String> result = new ArrayList<>(paths);
            skipped.forEach(path -> result.add("[未迁移：缺失或链接] " + path));
            return List.copyOf(result);
        }
    }

    /** 进度回调不会自行访问控件；界面负责节流并切换到 JavaFX 线程。 */
    public record Progress(String phase, String file, long bytes, long total) {}

    /** 提交后清理失败不等于曲库迁移失败，remaining 会明确列出保留文件。 */
    public record Result(boolean committed, boolean cancelled, List<String> remaining) {}

    /** 数据库 JSON 清单只存普通字符串和数字，避免把平台 Path 的内部结构写入文件。 */
    private static final class Plan {
        String id;
        String source;
        String sourceReal;
        String target;
        List<Item> files = new ArrayList<>();
    }

    /** 每项记录复制内容及创建归属；回滚绝不删除复用的目标原有文件。 */
    private static final class Item {
        String source;
        String target;
        String hash;
        long bytes;
        boolean audio;
        boolean created;
        boolean cleaned;
        String fileKey;
    }

    /** 保存借用资源，迁移备份仍位于原应用数据目录。 */
    public DownloadMigrationService(
            MusicDatabase database, DownloadDirectorySettings settings, Path dataDirectory) {
        this.database = database;
        this.settings = settings;
        this.dataDirectory = dataDirectory;
    }

    /** 后台扫描旧根的音频及配套歌词；链接、缓存、备份和暂存不在迁移范围。 */
    public Preview preview(Path source, Path target) throws IOException {
        validateRoots(source, target);
        List<Path> songs = songs(source);
        List<String> paths = new ArrayList<>();
        long bytes = 0;
        for (Path song : songs) {
            paths.add(song.toString());
            bytes += Files.size(song);
            Path lrc = sidecar(song);
            if (regular(lrc)) {
                paths.add(lrc.toString());
                bytes += Files.size(lrc);
            }
        }
        Set<Path> included = new java.util.HashSet<>(songs);
        Set<String> skipped = new java.util.LinkedHashSet<>();
        List<String> registered = new ArrayList<>();
        database.loadTracks().forEach(track -> registered.add(track.path().toString()));
        database.loadPlaylists()
                .forEach(
                        playlist ->
                                playlist.entries()
                                        .forEach(entry -> registered.add(entry.location())));
        Path oldRoot = source.toAbsolutePath().normalize();
        for (String value : registered) {
            if (value == null || value.isBlank()) continue;
            try {
                Path candidate = Path.of(value).toAbsolutePath().normalize();
                if (candidate.startsWith(oldRoot)
                        && audioName(candidate)
                        && !DownloadDirectorySettings.internal(candidate)
                        && !excludedSubtree(oldRoot, candidate)
                        && !included.contains(candidate)) skipped.add(value);
            } catch (java.nio.file.InvalidPathException ignored) {
                /* URI 不属于本地下载根。 */
            }
        }
        return new Preview(songs.size(), bytes, List.copyOf(paths), List.copyOf(skipped));
    }

    /** 用户已确认范围后保存待办；不在运行中的播放器里复制或重写路径。 */
    public void schedule(Path source, Path target) throws IOException {
        validateRoots(source, target);
        ensureNoPending();
        Plan plan = new Plan();
        plan.id = UUID.randomUUID().toString();
        plan.source = source.toAbsolutePath().normalize().toString();
        plan.sourceReal = source.toRealPath().toString();
        plan.target = settings.validate(target).toString();
        try {
            save(plan, "PLANNED");
        } catch (RuntimeException failure) {
            if (database.pendingDownloadMigration().isPresent())
                throw new IOException("另一个实例已保存迁移计划，请先完成该任务", failure);
            throw failure;
        }
    }

    /** 启动阶段执行或恢复迁移；取消只在提交前回滚，提交后停止清理并保留有效新曲库。 */
    public Result execute(
            MusicDatabase.DownloadMigration migration,
            BooleanSupplier cancelled,
            Consumer<Progress> progress)
            throws IOException {
        migration = current(migration);
        Plan plan = read(migration);
        boolean committed = committed(migration.status());
        database.acquireMigrationExclusivity();
        try {
            if (!committed) {
                validateRoots(Path.of(plan.source), Path.of(plan.target));
                if (!Path.of(plan.source).toRealPath().equals(Path.of(plan.sourceReal))
                        || !Path.of(plan.target).toRealPath().equals(Path.of(plan.target)))
                    throw new IOException("目录指向已改变，请取消后重新确认迁移");
                cleanStaging(plan);
                checkCancelled(cancelled);
                if (plan.files.isEmpty()) prepare(plan, cancelled, progress);
                Path backup =
                        dataDirectory
                                .resolve("backups")
                                .resolve("download-migration-" + plan.id + ".db");
                Files.createDirectories(backup.getParent());
                if (!Files.exists(backup)) database.backup(backup);
                save(plan, "COPYING");
                long total = plan.files.stream().mapToLong(item -> item.bytes).sum();
                long required =
                        plan.files.stream()
                                .filter(
                                        item ->
                                                !Files.exists(
                                                        Path.of(item.target),
                                                        LinkOption.NOFOLLOW_LINKS))
                                .mapToLong(item -> item.bytes)
                                .sum();
                if (Files.getFileStore(Path.of(plan.target)).getUsableSpace() < required)
                    throw new IOException("目标磁盘可用空间不足，请释放空间后重试");
                long done = 0;
                for (Item item : plan.files) {
                    checkCancelled(cancelled);
                    long base = done;
                    progress.accept(new Progress("正在复制", item.source, done, total));
                    copy(
                            plan,
                            item,
                            cancelled,
                            transferred ->
                                    progress.accept(
                                            new Progress(
                                                    "正在复制",
                                                    item.source,
                                                    base + transferred,
                                                    total)));
                    done += item.bytes;
                    progress.accept(new Progress("复制并校验", item.source, done, total));
                }
                checkCancelled(cancelled);
                Map<Path, Path> mappings = new LinkedHashMap<>();
                for (Item item : plan.files)
                    if (item.audio) mappings.put(Path.of(item.source), Path.of(item.target));
                progress.accept(new Progress("更新曲库、歌词和歌单", "", total, total));
                database.migrateDownloadPaths(
                        plan.id,
                        JSON.toJson(plan),
                        mappings,
                        Path.of(plan.source),
                        Path.of(plan.target));
                committed = true;
            }
            // 设置与 SQLite 无法共同提交，恢复始终先补全设置，再考虑删除源文件。
            progress.accept(new Progress("保存新下载位置", plan.target, 0, 0));
            settings.restoreCommitted(Path.of(plan.target));
            save(plan, "CLEANUP");
            return cleanup(plan, cancelled, progress);
        } catch (CancellationException cancellation) {
            boolean interrupted = Thread.interrupted();
            try {
                if (committed) {
                    save(plan, "CLEANUP_PENDING");
                    return new Result(true, true, remaining(plan));
                }
                rollback(plan);
                save(plan, "CANCELLED");
                return new Result(false, true, List.of());
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
        } catch (IOException | RuntimeException failure) {
            if (!committed) {
                rollback(plan);
                save(plan, "COPYING");
            }
            throw failure instanceof IOException io
                    ? io
                    : new IOException(failure.getMessage(), failure);
        } finally {
            database.releaseMigrationExclusivity();
        }
    }

    /** 提交前可以撤销待办；提交后只能明确保留旧副本，不能退回旧数据库。 */
    public void dismiss(MusicDatabase.DownloadMigration migration) throws IOException {
        migration = current(migration);
        Plan plan = read(migration);
        if (committed(migration.status())) {
            settings.restoreCommitted(Path.of(plan.target));
            save(plan, "DONE_PRESERVED");
        } else {
            rollback(plan);
            save(plan, "CANCELLED");
        }
    }

    /** 普通目录设置不能覆盖另一实例安排的迁移意图。 */
    public void ensureNoPending() throws IOException {
        if (database.pendingDownloadMigration().isPresent()) throw new IOException("请先处理尚未完成的迁移任务");
    }

    /** 弹窗可能持有旧记录，执行或取消前必须重新读取数据库已提交的真实阶段。 */
    private MusicDatabase.DownloadMigration current(MusicDatabase.DownloadMigration supplied)
            throws IOException {
        return database.pendingDownloadMigration()
                .filter(record -> record.id().equals(supplied.id()))
                .orElseThrow(() -> new IOException("迁移任务状态已变化，请刷新后重试"));
    }

    /** 用稳定内容确定目标名称；音频和旁车一起处理冲突。 */
    private void prepare(Plan plan, BooleanSupplier cancelled, Consumer<Progress> progress)
            throws IOException {
        Path root = Path.of(plan.source), target = Path.of(plan.target);
        Set<Path> reserved = new java.util.HashSet<>();
        List<Item> prepared = new ArrayList<>();
        for (Path song : songs(root)) {
            checkCancelled(cancelled);
            progress.accept(new Progress("扫描并校验源文件", song.toString(), 0, 0));
            String audioHash = hash(song, cancelled);
            Path lrc = sidecar(song);
            String lyricHash = regular(lrc) ? hash(lrc, cancelled) : null;
            Path desired = target.resolve(root.relativize(song));
            Path destination = destination(desired, audioHash, lyricHash, reserved, cancelled);
            reserved.add(destination);
            prepared.add(item(song, destination, audioHash, true));
            if (lyricHash != null) {
                Path lyricTarget = sidecar(destination);
                reserved.add(lyricTarget);
                prepared.add(item(lrc, lyricTarget, lyricHash, false));
            }
        }
        plan.files = prepared;
        save(plan, "COPYING");
    }

    /** 同内容且没有歌词冲突时复用，否则递增编号并保留原来的文件。 */
    private static Path destination(
            Path desired,
            String audioHash,
            String lyricHash,
            Set<Path> reserved,
            BooleanSupplier cancelled)
            throws IOException {
        String file = desired.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String stem = dot < 0 ? file : file.substring(0, dot),
                extension = dot < 0 ? "" : file.substring(dot);
        for (int suffix = 1; ; suffix++) {
            Path candidate =
                    suffix == 1
                            ? desired
                            : desired.resolveSibling(stem + " (" + suffix + ")" + extension);
            Path lyric = sidecar(candidate);
            if (reserved.contains(candidate) || (lyricHash != null && reserved.contains(lyric)))
                continue;
            boolean audioOk =
                    !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)
                            || (regular(candidate) && hash(candidate, cancelled).equals(audioHash));
            boolean lyricOk =
                    lyricHash == null
                            || !Files.exists(lyric, LinkOption.NOFOLLOW_LINKS)
                            || (regular(lyric) && hash(lyric, cancelled).equals(lyricHash));
            if (audioOk && lyricOk) return candidate;
        }
    }

    /** 建立可恢复条目，文件内容变化时拒绝用过时的记录清源。 */
    private static Item item(Path source, Path target, String hash, boolean audio)
            throws IOException {
        Item item = new Item();
        item.source = source.toAbsolutePath().normalize().toString();
        item.target = target.toAbsolutePath().normalize().toString();
        item.hash = hash;
        item.bytes = Files.size(source);
        item.audio = audio;
        return item;
    }

    /** 先完整写入任务临时文件，再不覆盖地发布；发布后才保存创建归属。 */
    private void copy(
            Plan plan,
            Item item,
            BooleanSupplier cancelled,
            java.util.function.LongConsumer transferred)
            throws IOException {
        Path source = Path.of(item.source), target = Path.of(item.target);
        if (!safeFile(plan, item, true) || !hash(source, cancelled).equals(item.hash))
            throw new IOException("源文件已变化，请取消后重新确认迁移：" + source);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!safeFile(plan, item, false) || !hash(target, cancelled).equals(item.hash))
                throw new IOException("目标文件已被其他程序改变，未覆盖：" + target);
            return;
        }
        if (!DownloadDirectorySettings.resolveExistingParent(target.getParent())
                .equals(target.getParent())) throw new IOException("目标子目录包含链接，未写入：" + target);
        Files.createDirectories(target.getParent());
        Path scratch =
                Path.of(plan.target)
                        .resolve(DownloadDirectorySettings.STAGING_DIRECTORY)
                        .resolve("migration-" + plan.id);
        if (!DownloadDirectorySettings.resolveExistingParent(scratch).equals(scratch))
            throw new IOException("迁移暂存位置包含链接，未写入");
        Files.createDirectories(scratch);
        Path temporary = Files.createTempFile(scratch, "copy-", ".part");
        try {
            try (InputStream input = Files.newInputStream(source);
                    OutputStream output =
                            Files.newOutputStream(temporary, StandardOpenOption.WRITE)) {
                byte[] buffer = new byte[64 * 1024];
                int length;
                long copied = 0;
                while ((length = input.read(buffer)) != -1) {
                    checkCancelled(cancelled);
                    output.write(buffer, 0, length);
                    copied += length;
                    transferred.accept(copied);
                }
            }
            if (Files.size(temporary) != item.bytes
                    || !hash(temporary, cancelled).equals(item.hash))
                throw new IOException("复制校验失败：" + source);
            try (var channel =
                    java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            checkCancelled(cancelled);
            Files.move(temporary, target);
            item.created = true;
            item.fileKey = fileKey(target);
            save(plan, "COPYING");
        } finally {
            Files.deleteIfExists(temporary);
            try {
                Files.deleteIfExists(scratch);
            } catch (java.nio.file.DirectoryNotEmptyException retained) {
                /* 恢复时只清理本任务的临时文件。 */
            }
        }
    }

    /** 新设置已生效后逐文件校验清理，失败保留原文件供用户重试。 */
    private Result cleanup(Plan plan, BooleanSupplier cancelled, Consumer<Progress> progress)
            throws IOException {
        List<String> failures = new ArrayList<>();
        for (Item item : plan.files) {
            if (item.cleaned) continue;
            if (cancelled.getAsBoolean()) {
                save(plan, "CLEANUP_PENDING");
                return new Result(true, true, remaining(plan));
            }
            Path source = Path.of(item.source), target = Path.of(item.target);
            progress.accept(new Progress("清理旧文件", item.source, 0, 0));
            try {
                if (!safeFile(plan, item, false) || !hash(target, () -> false).equals(item.hash))
                    throw new IOException("目标文件不可用或已变化，旧文件保留");
                if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                    if (!safeFile(plan, item, true) || !hash(source, () -> false).equals(item.hash))
                        throw new IOException("旧文件已变化，未删除");
                    Files.delete(source);
                }
                item.cleaned = true;
                save(plan, "CLEANUP");
            } catch (IOException failure) {
                failures.add(source + "：" + failure.getMessage());
            }
        }
        save(plan, failures.isEmpty() ? "DONE" : "CLEANUP_PENDING");
        return new Result(true, false, List.copyOf(failures));
    }

    /** 仅撤销能证明由本次创建且内容未变化的目标；复用文件和被修改的文件均保留。 */
    private void rollback(Plan plan) throws IOException {
        for (Item item : plan.files)
            if (item.created) {
                Path target = Path.of(item.target);
                if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    item.created = false;
                    continue;
                }
                if (safeFile(plan, item, false)
                        && item.fileKey != null
                        && item.fileKey.equals(fileKey(target))
                        && hash(target, () -> false).equals(item.hash)) {
                    try {
                        Files.delete(target);
                        item.created = false;
                    } catch (IOException ignored) {
                        /* 保留文件，后续重试仍有清单依据。 */
                    }
                }
            }
    }

    /** 扫描真实根目录，但用用户原路径构造映射，保持与已有曲库键一致。 */
    private static List<Path> songs(Path source) throws IOException {
        Path root = source.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("旧下载目录不可用：" + root);
        Path real = root.toRealPath();
        List<Path> result = new ArrayList<>();
        Files.walkFileTree(
                real,
                new SimpleFileVisitor<>() {
                    /** 不进入缓存、备份、内部暂存或指向根外的目录链接。 */
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path directory, BasicFileAttributes attributes) throws IOException {
                        if (directory.equals(real)) return FileVisitResult.CONTINUE;
                        String first =
                                real.relativize(directory)
                                        .getName(0)
                                        .toString()
                                        .toLowerCase(Locale.ROOT);
                        if (first.equals("cache")
                                || first.equals("backups")
                                || DownloadDirectorySettings.internal(directory)
                                || Files.isSymbolicLink(directory)
                                || !directory
                                        .toRealPath()
                                        .equals(directory.toAbsolutePath().normalize()))
                            return FileVisitResult.SKIP_SUBTREE;
                        return FileVisitResult.CONTINUE;
                    }

                    /** 只有普通音频文件进入清单，符号链接文件不复制。 */
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        String name = file.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        if (attributes.isRegularFile()
                                && !attributes.isSymbolicLink()
                                && dot > 0
                                && AUDIO.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT)))
                            result.add(root.resolve(real.relativize(file)));
                        return FileVisitResult.CONTINUE;
                    }
                });
        return result.stream().sorted().toList();
    }

    /** 判断历史记录是否是本次负责的音频扩展名。 */
    private static boolean audioName(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && AUDIO.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** 缓存与备份属于应用数据，不能混入缺失音乐的迁移提示。 */
    private static boolean excludedSubtree(Path root, Path file) {
        Path relative = root.relativize(file);
        String first = relative.getName(0).toString();
        return first.equalsIgnoreCase("cache") || first.equalsIgnoreCase("backups");
    }

    /** 根目录重叠会产生递归复制或删除歧义，必须在扫描与恢复前拒绝。 */
    private void validateRoots(Path source, Path target) throws IOException {
        Path oldRoot = DownloadDirectorySettings.resolveExistingParent(source);
        Path newRoot = settings.validate(target);
        if (oldRoot.startsWith(newRoot) || newRoot.startsWith(oldRoot))
            throw new IOException("新旧音乐目录不能相同或互相包含");
    }

    /** 检查恢复清单的边界，损坏记录不能让清理越过已确认根目录。 */
    private static Plan read(MusicDatabase.DownloadMigration migration) throws IOException {
        try {
            if (!Set.of("PLANNED", "COPYING", "COMMITTED", "CLEANUP", "CLEANUP_PENDING")
                    .contains(migration.status())) throw new IllegalArgumentException("未知迁移阶段");
            Plan plan = JSON.fromJson(migration.payload(), Plan.class);
            if (plan == null
                    || !migration.id().equals(plan.id)
                    || !UUID.fromString(plan.id).toString().equals(plan.id)
                    || plan.files == null
                    || plan.sourceReal == null) throw new IllegalArgumentException("迁移记录无效");
            Path source = Path.of(plan.source).toAbsolutePath().normalize(),
                    target = Path.of(plan.target).toAbsolutePath().normalize();
            if (!Path.of(plan.source).isAbsolute()
                    || !Path.of(plan.target).isAbsolute()
                    || !Path.of(plan.sourceReal).isAbsolute())
                throw new IllegalArgumentException("迁移根目录不是绝对路径");
            Path realSource = Path.of(plan.sourceReal).normalize();
            if (source.startsWith(target)
                    || target.startsWith(source)
                    || realSource.startsWith(target)
                    || target.startsWith(realSource))
                throw new IllegalArgumentException("迁移清单根目录重叠");
            for (Item item : plan.files)
                if (!Path.of(item.source).toAbsolutePath().normalize().startsWith(source)
                        || !Path.of(item.target).toAbsolutePath().normalize().startsWith(target)
                        || item.hash == null
                        || !item.hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("迁移清单越界或损坏");
            return plan;
        } catch (RuntimeException failure) {
            throw new IOException("无法安全读取迁移记录，请保留文件并恢复数据库备份", failure);
        }
    }

    /** 小步持久化阶段，提交标记由数据库路径事务单独原子写入。 */
    private void save(Plan plan, String state) {
        database.saveDownloadMigration(plan.id, state, JSON.toJson(plan));
    }

    /** 提交之后只能前进到设置恢复和清理，不能再撤回文件路径。 */
    private static boolean committed(String state) {
        return state.equals("COMMITTED")
                || state.equals("CLEANUP")
                || state.equals("CLEANUP_PENDING");
    }

    /** 配套歌词与音频使用相同文件名主体。 */
    private static Path sidecar(Path audio) {
        String name = audio.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return audio.resolveSibling((dot < 0 ? name : name.substring(0, dot)) + ".lrc");
    }

    /** 不跟随文件符号链接，防止清理用户确认范围之外的内容。 */
    private static boolean regular(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    /** 恢复和清理前重新检查真实路径，目录被换成链接时不能越过确认的边界。 */
    private static boolean safeFile(Plan plan, Item item, boolean source) throws IOException {
        Path file = Path.of(source ? item.source : item.target);
        if (!regular(file)) return false;
        Path expected =
                source
                        ? Path.of(plan.sourceReal).resolve(Path.of(plan.source).relativize(file))
                        : file;
        return file.toRealPath().equals(expected.toAbsolutePath().normalize());
    }

    /** 清理进程中断留下的本任务 .part，其他名称及链接全部保留。 */
    private static void cleanStaging(Plan plan) throws IOException {
        Path directory =
                Path.of(plan.target)
                        .resolve(DownloadDirectorySettings.STAGING_DIRECTORY)
                        .resolve("migration-" + plan.id);
        if (!Files.exists(directory)) return;
        if (!directory.toRealPath().equals(directory.toAbsolutePath().normalize()))
            throw new IOException("迁移暂存目录包含链接");
        try (var entries = Files.list(directory)) {
            for (Path path : entries.toList())
                if (regular(path)
                        && path.getFileName().toString().startsWith("copy-")
                        && path.getFileName().toString().endsWith(".part")) Files.delete(path);
        }
    }

    /** 内容哈希也支持取消，避免大文件校验时界面取消迟迟不生效。 */
    private static String hash(Path path, BooleanSupplier cancelled) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    checkCancelled(cancelled);
                    digest.update(buffer, 0, size);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java 运行时缺少 SHA-256", impossible);
        }
    }

    /** 优先使用文件标识；Windows 没有标识时用创建及修改时间检查发布后是否被替换。 */
    private static String fileKey(Path path) throws IOException {
        BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        Object key = attributes.fileKey();
        return key == null
                ? "times:" + attributes.creationTime() + ":" + attributes.lastModifiedTime()
                : "key:" + key;
    }

    /** 取消只由当前任务的控制器提供，不改变其他下载的取消范围。 */
    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new CancellationException();
    }

    /** 返回尚未清理的旧文件，便于提交后的取消和失败提示。 */
    private static List<String> remaining(Plan plan) {
        return plan.files.stream().filter(item -> !item.cleaned).map(item -> item.source).toList();
    }
}
