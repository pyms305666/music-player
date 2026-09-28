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
        if (baseDir.getFileName() == null || !NEW_APP_NAME.equals(baseDir.getFileName().toString())
                || !Files.isRegularFile(baseDir.resolve(NEW_APP_NAME + ".exe"))) return;

        Path oldDataDir = baseDir.resolveSibling(OLD_APP_NAME).resolve("downloads").toAbsolutePath().normalize();
        Path newDataDir = dataDir.toAbsolutePath().normalize();
        if (!Files.isDirectory(oldDataDir) || Files.exists(databasePath)
                || Files.exists(newDataDir.resolve(MARKER_FILE))) return;

        Files.walkFileTree(oldDataDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path relative = oldDataDir.relativize(dir);
                if (relative.startsWith(Path.of("cache", "sqlite-native"))) return FileVisitResult.SKIP_SUBTREE;
                Files.createDirectories(newDataDir.resolve(relative));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                if (Files.isSymbolicLink(file) || name.equals(DATABASE_FILE)
                        || name.equals(DATABASE_FILE + "-journal")
                        || name.equals(DATABASE_FILE + "-wal")
                        || name.equals(DATABASE_FILE + "-shm")) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = newDataDir.resolve(oldDataDir.relativize(file));
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });

        Path oldDatabase = oldDataDir.resolve(DATABASE_FILE);
        if (Files.isRegularFile(oldDatabase)) {
            Path temporaryDatabase = newDataDir.resolve(".migration-" + DATABASE_FILE);
            Files.copy(oldDatabase, temporaryDatabase, StandardCopyOption.REPLACE_EXISTING);
            rewriteDatabasePaths(temporaryDatabase, oldDataDir, newDataDir);
            Files.move(temporaryDatabase, databasePath, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(newDataDir.resolve(MARKER_FILE), oldDataDir.toString());
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
