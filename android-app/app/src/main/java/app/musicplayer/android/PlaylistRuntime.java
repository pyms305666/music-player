package app.musicplayer.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import app.musicplayer.android.data.*;
import app.musicplayer.playlist.PlaylistWorkspace;

/** Application-context owner: leaving the playlist screen does not cancel downloads. */
public final class PlaylistRuntime {
    private static PlaylistRuntime instance;
    public final AndroidMusicDatabase database;
    public final AndroidPlaylistFiles files;
    public final PlaylistWorkspace workspace;
    private PlaylistRuntime(Context context){
        Context app=context.getApplicationContext();database=new AndroidMusicDatabase(app);files=new AndroidPlaylistFiles(app,database);
        Handler main=new Handler(Looper.getMainLooper());workspace=new PlaylistWorkspace(database,new java.io.File(app.getCacheDir(),"playlist-downloads").toPath(),files,main::post);
    }
    public static synchronized PlaylistRuntime get(Context context){if(instance==null)instance=new PlaylistRuntime(context);return instance;}
}
