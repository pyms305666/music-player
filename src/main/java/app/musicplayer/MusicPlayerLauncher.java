package app.musicplayer;

import app.musicplayer.config.AppPaths;
import app.musicplayer.config.SqliteNativeTemp;
import javafx.application.Application;

/**
 * 普通 Java 启动入口。
 *
 * <p>不要直接把继承 {@link Application} 的 {@link MusicPlayerApp} 作为 jpackage 的 main class。
 * 某些 classpath 打包方式下，Java 启动器会误判“缺少 JavaFX 运行时组件”，导致 exe 双击后直接退出。
 * 这个类本身不继承 Application，只负责把启动请求转交给真正的 JavaFX 应用类。</p>
 */
public final class MusicPlayerLauncher {
    private MusicPlayerLauncher() {
        // 工具启动类不需要被创建对象。
    }

    public static void main(String[] args) {
        AppPaths paths = AppPaths.resolve(MusicPlayerLauncher.class);
        SqliteNativeTemp.configure(paths.cacheDir());
        Application.launch(MusicPlayerApp.class, args);
    }
}
