package app.musicplayer.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 集中管理应用运行目录和所有持久化数据目录。
 *
 * <p>打包后的 jpackage 应用优先使用 exe 所在目录；开发环境则回退到代码位置或当前工作目录。
 * 这样数据库、在线下载、歌词、封面和播放兼容缓存始终位于同一个 downloads 目录中。</p>
 */
public record AppPaths(
        Path baseDir,
        Path dataDir,
        Path cacheDir,
        Path lyricsCacheDir,
        Path artworkCacheDir,
        Path playbackCacheDir,
        Path databasePath
) {
    public static AppPaths resolve(Class<?> applicationClass) {
        Path baseDir = resolveBaseDir(applicationClass);
        Path dataDir = baseDir.resolve("downloads").toAbsolutePath().normalize();
        Path cacheDir = dataDir.resolve("cache");
        return new AppPaths(
                baseDir,
                dataDir,
                cacheDir,
                cacheDir.resolve("lyrics"),
                cacheDir.resolve("artwork"),
                cacheDir.resolve("playback"),
                dataDir.resolve("music-player.db")
        );
    }

    public void initialize() {
        try {
            Files.createDirectories(dataDir);
            Files.createDirectories(lyricsCacheDir);
            Files.createDirectories(artworkCacheDir);
            Files.createDirectories(playbackCacheDir);
            migrateLegacyDatabaseIfNeeded();
        } catch (IOException exception) {
            throw new IllegalStateException("无法初始化应用数据目录: " + dataDir, exception);
        }
    }

    private void migrateLegacyDatabaseIfNeeded() throws IOException {
        if (Files.exists(databasePath)) {
            return;
        }

        Set<Path> candidates = new LinkedHashSet<>();
        candidates.add(baseDir.resolve("music-player.db").toAbsolutePath().normalize());
        candidates.add(Path.of("music-player.db").toAbsolutePath().normalize());

        for (Path candidate : candidates) {
            if (!candidate.equals(databasePath) && Files.isRegularFile(candidate)) {
                Files.copy(candidate, databasePath, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
    }

    private static Path resolveBaseDir(Class<?> applicationClass) {
        if ("android".equalsIgnoreCase(System.getProperty("javafx.platform"))) {
            String userHome = System.getProperty("user.home");
            if (userHome != null && !userHome.isBlank()) {
                return Path.of(userHome).toAbsolutePath().normalize();
            }
        }

        String packagedAppPath = System.getProperty("jpackage.app-path");
        if (packagedAppPath != null && !packagedAppPath.isBlank()) {
            Path executable = Path.of(packagedAppPath).toAbsolutePath().normalize();
            if (executable.getParent() != null) {
                return executable.getParent();
            }
        }

        try {
            var codeSource = applicationClass.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path location = Path.of(codeSource.getLocation().toURI()).toAbsolutePath().normalize();
                if (!Files.isRegularFile(location)) {
                    return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
                }
                Path container = location.getParent();
                if (container != null && "app".equalsIgnoreCase(String.valueOf(container.getFileName()))) {
                    Path installRoot = container.getParent();
                    if (installRoot != null && Files.isDirectory(installRoot.resolve("runtime"))) {
                        return installRoot;
                    }
                }
                if (container != null && "lib".equalsIgnoreCase(String.valueOf(container.getFileName()))) {
                    Path distributionRoot = container.getParent();
                    if (distributionRoot != null && Files.isDirectory(distributionRoot.resolve("bin"))) {
                        return distributionRoot;
                    }
                }
                if (container != null) {
                    return container;
                }
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // 当前工作目录仍可作为开发环境的稳定回退路径。
        }

        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }
}
