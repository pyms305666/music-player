package app.musicplayer.config;

import app.musicplayer.data.MusicDatabase;

import java.nio.file.Path;
import java.util.prefs.Preferences;

/** 迁移崩溃测试子进程。仅使用父测试传入的夹具，在指定持久化阶段直接终止自己。 */
public final class DownloadMigrationCrashProbe {
    /** 工具类没有实例状态。 */
    private DownloadMigrationCrashProbe() {}

    /** 不经过 finally 关闭来模拟进程意外中断，父测试随后验证恢复。 */
    public static void main(String[] arguments) throws Exception {
        // 父进程用 Base64 包装 UTF-8，避免 Windows 系统代码页损坏中文路径和阶段。
        for (int index = 0; index < arguments.length; index++) {
            arguments[index] =
                    new String(
                            java.util.Base64.getDecoder().decode(arguments[index]),
                            java.nio.charset.StandardCharsets.UTF_8);
        }
        Path data = Path.of(arguments[0]);
        var settings =
                new DownloadDirectorySettings(
                        Preferences.userRoot().node(arguments[2]), Path.of(arguments[1]), null);
        try (var database = new MusicDatabase(data.resolve("music-player.db"))) {
            var service = new DownloadMigrationService(database, settings, data);
            service.execute(
                    database.pendingDownloadMigration().orElseThrow(),
                    () -> false,
                    progress -> {
                        if (progress.phase().equals(arguments[3])) Runtime.getRuntime().halt(91);
                    });
        }
        throw new IllegalStateException("测试阶段没有触发");
    }
}
