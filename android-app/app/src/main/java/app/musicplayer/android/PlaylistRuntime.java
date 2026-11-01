package app.musicplayer.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import app.musicplayer.android.data.*;
import app.musicplayer.playlist.PlaylistWorkspace;

/**
 * Android 应用级歌单运行环境。持有歌单数据库、文件适配器和共享工作区，使批量下载可在离开歌单页面后继续。 Application-context owner: leaving the
 * playlist screen does not cancel downloads.
 */
public final class PlaylistRuntime {
    private static PlaylistRuntime instance;
    public final AndroidMusicDatabase database;
    public final AndroidPlaylistFiles files;
    public final PlaylistWorkspace workspace;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private PlaylistRuntime(Context context) {
        Context app = context.getApplicationContext();
        database = new AndroidMusicDatabase(app);
        files = new AndroidPlaylistFiles(app, database);
        Handler main = new Handler(Looper.getMainLooper());
        workspace =
                new PlaylistWorkspace(
                        database,
                        new java.io.File(app.getCacheDir(), "playlist-downloads").toPath(),
                        files,
                        main::post);
    }

    /** 按应用上下文取得唯一歌单运行环境，让下载任务跨页面继续。 */
    public static synchronized PlaylistRuntime get(Context context) {
        if (instance == null) instance = new PlaylistRuntime(context);
        return instance;
    }
}
