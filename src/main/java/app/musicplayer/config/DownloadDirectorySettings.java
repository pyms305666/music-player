package app.musicplayer.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** 音乐下载目录设置。借用偏好设置，数据库与缓存目录继续由 AppPaths 管理。 */
public final class DownloadDirectorySettings {
    public static final String STAGING_DIRECTORY = ".za-music-staging";
    private static final String KEY = "download.directory";
    private final Preferences preferences;
    private final Path legacyDirectory;
    private final Path installDirectory;

    /** 传入旧默认目录及安装根；开发运行时安装根可以为空。 */
    public DownloadDirectorySettings(
            Preferences preferences, Path legacyDirectory, Path installDirectory) {
        this.preferences = preferences;
        this.legacyDirectory = legacyDirectory.toAbsolutePath().normalize();
        this.installDirectory =
                installDirectory == null ? null : installDirectory.toAbsolutePath().normalize();
    }

    /** 已保存目录或显式启动参数。未设置时由界面询问，不自动开始向旧目录下载。 */
    public Optional<Path> configured() {
        String override = System.getProperty("musicplayer.download-dir");
        String value = override == null || override.isBlank() ? preferences.get(KEY, "") : override;
        return value.isBlank()
                ? Optional.empty()
                : Optional.of(Path.of(value).toAbsolutePath().normalize());
    }

    /** 供设置页及新歌单预填位置；未确认时只作为建议，不代表单曲下载已获准。 */
    public Path suggested() {
        return configured().orElse(legacyDirectory);
    }

    /** 保存值损坏时仍允许重新选择，不把损坏路径当作有效下载目录。 */
    public Path selectionSuggestion() {
        try {
            return suggested();
        } catch (java.nio.file.InvalidPathException failure) {
            return legacyDirectory;
        }
    }

    /** 在后台确认目录真实位置及写入能力，防止选入程序安装目录。 */
    public Path validate(Path directory) throws IOException {
        if (directory == null || !directory.isAbsolute()) throw new IOException("请选择完整的文件夹路径");
        Path normalized = resolveExistingParent(directory);
        if (internal(normalized)) throw new IOException("此目录用于内部暂存，请选择普通音乐文件夹");
        if (installDirectory != null
                && normalized.startsWith(resolveExistingParent(installDirectory))) {
            throw new IOException("音乐目录不能位于程序安装目录内，请选择其他文件夹");
        }
        Files.createDirectories(normalized);
        Path actual = normalized.toRealPath();
        if (!Files.isDirectory(actual)) throw new IOException("所选路径不是文件夹");
        Path probe = Files.createTempFile(actual, ".za-write-check-", ".tmp");
        Files.delete(probe);
        return actual;
    }

    /** 设置只有在持久化成功后生效；写入失败恢复原来的内存值。 */
    public void save(Path directory) throws IOException {
        Path actual = validate(directory);
        persist(actual);
    }

    /** 已提交迁移的目录曾经完整校验；恢复设置不要求移动盘此刻在线，清源另行验证。 */
    public void restoreCommitted(Path directory) throws IOException {
        if (!directory.isAbsolute()) throw new IOException("迁移记录中的下载路径无效");
        persist(directory.normalize());
    }

    /** 偏好保存失败恢复内存值，数据库提交记录仍然是恢复依据。 */
    private void persist(Path actual) throws IOException {
        String previous = preferences.get(KEY, null);
        preferences.put(KEY, actual.toString());
        try {
            preferences.flush();
        } catch (BackingStoreException failure) {
            if (previous == null) preferences.remove(KEY);
            else preferences.put(KEY, previous);
            throw new IOException("无法保存下载位置，请重试", failure);
        }
    }

    /** 解析现有祖先的真实路径，识别通过目录链接进入安装根或产生目录重叠的情况。 */
    public static Path resolveExistingParent(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        Path parent = absolute;
        while (parent != null && !Files.exists(parent)) parent = parent.getParent();
        if (parent == null) throw new IOException("磁盘不可用：" + directory);
        return parent.toRealPath().resolve(parent.relativize(absolute)).normalize();
    }

    /** 内部暂存目录中的音频不能进入曲库或查重结果。 */
    public static boolean internal(Path path) {
        for (Path part : path) {
            String name = part.toString();
            if (name.equalsIgnoreCase(STAGING_DIRECTORY)
                    || name.equalsIgnoreCase(".playlist-staging")) return true;
        }
        return false;
    }
}
