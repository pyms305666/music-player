package app.musicplayer.config;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Copies data from the former Windows installation without changing or removing the source. */
final class LegacyInstallMigration {
    private static final String OLD_APP_NAME = "简约音乐播放器";
    private static final String NEW_APP_NAME = "ZA音乐";
    private static final String DATABASE_FILE = "music-player.db";
    private static final String MARKER_FILE = ".legacy-install-migrated";

    private LegacyInstallMigration() { }

    static void migrateIfNeeded(Path baseDir, Path dataDir, Path databasePath) throws IOException {
        if (Files.exists(databasePath)) return;
        List<Path> candidates = new ArrayList<>();
        String configured = System.getProperty("musicplayer.migrate-from");
        if (configured != null && !configured.isBlank()) candidates.add(Path.of(configured));
        candidates.add(baseDir.resolve("downloads"));
        candidates.add(baseDir.resolveSibling(NEW_APP_NAME).resolve("downloads"));
        candidates.add(baseDir.resolveSibling(OLD_APP_NAME).resolve("downloads"));
        Path destination = dataDir.toAbsolutePath().normalize();
        for (Path candidate : candidates) {
            Path source = candidate.toAbsolutePath().normalize();
            if (source.equals(destination) || !Files.isRegularFile(source.resolve(DATABASE_FILE))) continue;
            if (destination.startsWith(source)) throw new IOException("目标目录不能位于旧数据目录内部");
            Files.createDirectories(destination);
            try (var channel = java.nio.channels.FileChannel.open(destination.resolve(".migration.lock"),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                 var lock = channel.tryLock()) {
                if (lock == null) throw new IOException("另一个实例正在迁移曲库，请稍后重试");
                if (Files.exists(databasePath)) return;
                migrate(source, destination, databasePath);
            } catch (java.nio.channels.OverlappingFileLockException busy) {
                throw new IOException("曲库正在迁移，请稍后重试", busy);
            }
            return;
        }
    }

    private static void migrate(Path source, Path destination, Path databasePath) throws IOException {
        Path snapshot = destination.resolve(".migration-" + DATABASE_FILE);
        Files.deleteIfExists(snapshot);
        // SQLite snapshots also include committed WAL pages.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source.resolve(DATABASE_FILE));
             Statement statement = connection.createStatement()) {
            statement.execute("pragma busy_timeout = 5000");
            statement.execute("vacuum into '" + snapshot.toString().replace("'", "''") + "'");
        } catch (SQLException error) {
            throw new IOException("无法备份旧曲库，请关闭旧版后重试；原数据保留在 " + source, error);
        }
        Path backup = destination.resolve("backups");
        Files.createDirectories(backup);
        Files.copy(snapshot, backup.resolve("before-migration-" + System.currentTimeMillis() + ".db"));
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = source.relativize(dir);
                if (relative.startsWith("cache") || relative.startsWith("backups")) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(destination.resolve(relative));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (Files.isSymbolicLink(file) || name.startsWith(DATABASE_FILE) || name.startsWith("."))
                    return FileVisitResult.CONTINUE;
                Path target = destination.resolve(source.relativize(file));
                if (Files.exists(target)) {
                    if (Files.mismatch(file, target) != -1) throw new IOException("迁移文件冲突，未覆盖：" + target);
                } else {
                    Path part = Files.createTempFile(target.getParent(), ".migration-", ".part");
                    try {
                        Files.copy(file, part, StandardCopyOption.REPLACE_EXISTING);
                        if (Files.mismatch(file, part) != -1) throw new IOException("迁移文件校验失败：" + file);
                        Files.move(part, target);
                    } finally { Files.deleteIfExists(part); }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        rewriteDatabasePaths(snapshot, source, destination);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + snapshot);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("pragma integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) throw new IOException("迁移数据库校验失败");
        } catch (SQLException error) { throw new IOException("迁移数据库校验失败", error); }
        Files.move(snapshot, databasePath);
        Files.writeString(destination.resolve(MARKER_FILE), source.toString());
    }

    private static void rewriteDatabasePaths(Path databasePath, Path oldDataDir, Path newDataDir)
            throws IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("pragma foreign_keys = off");
            }
            connection.setAutoCommit(false);
            try {
                rewriteColumn(connection, "tracks", "path", oldDataDir, newDataDir);
                rewriteColumn(connection, "lyrics", "track_path", oldDataDir, newDataDir);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("pragma foreign_key_check")) {
                if (result.next()) throw new IOException("迁移后的曲库存在无效歌词关联");
            }
        } catch (SQLException exception) {
            throw new IOException("迁移旧版曲库失败，请关闭旧版播放器后重试", exception);
        }
    }

    private static void rewriteColumn(Connection connection, String table, String column,
                                      Path oldDataDir, Path newDataDir) throws SQLException {
        List<PathChange> changes = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select " + column + " from " + table)) {
            while (result.next()) {
                String value = result.getString(1);
                Path original;
                try {
                    original = Path.of(value).toAbsolutePath().normalize();
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (original.startsWith(oldDataDir)) {
                    changes.add(new PathChange(value, newDataDir.resolve(oldDataDir.relativize(original)).toString()));
                }
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "update " + table + " set " + column + " = ? where " + column + " = ?")) {
            for (PathChange change : changes) {
                update.setString(1, change.newPath());
                update.setString(2, change.oldPath());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private record PathChange(String oldPath, String newPath) { }
}
