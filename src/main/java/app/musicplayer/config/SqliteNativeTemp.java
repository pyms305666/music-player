package app.musicplayer.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 为当前进程创建独立的 SQLite JDBC 原生库目录。
 *
 * <p>Xerial SQLite 默认会扫描系统临时目录并清理旧 DLL。Windows 不允许删除仍被其他
 * 进程占用的 DLL，因此同时运行多个播放器或上次进程被强制结束时会输出访问拒绝异常。
 * 每个进程使用独立目录后，驱动只会查看自己本次启动创建的目录。</p>
 */
public final class SqliteNativeTemp {
    static final String SQLITE_TEMP_PROPERTY = "org.sqlite.tmpdir";

    private SqliteNativeTemp() {
        // 只提供启动前配置，不需要创建对象。
    }

    public static synchronized Path configure(Path cacheDir) {
        String configured = System.getProperty(SQLITE_TEMP_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        Objects.requireNonNull(cacheDir, "cacheDir");
        try {
            Path root = cacheDir.toAbsolutePath().normalize().resolve("sqlite-native");
            Files.createDirectories(root);
            Path processDirectory = Files.createTempDirectory(
                    root,
                    "process-" + ProcessHandle.current().pid() + "-");
            processDirectory.toFile().deleteOnExit();
            System.setProperty(SQLITE_TEMP_PROPERTY, processDirectory.toString());
            return processDirectory;
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 SQLite 原生库临时目录", exception);
        }
    }
}
