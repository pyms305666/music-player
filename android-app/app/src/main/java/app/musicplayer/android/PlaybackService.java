package app.musicplayer.android;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** The service owns the complete queue, so automatic transitions do not require the UI. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    private MediaSession session;

    @Override public void onCreate() {
        super.onCreate();
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setVolume(0.7f);
        // Temporary API 33 CI diagnostics; removed before final publication.
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                android.util.Log.i("ZaPlaybackTrace", "state=" + state + " pwr=" + player.getPlayWhenReady()
                        + " playing=" + player.isPlaying() + " position=" + player.getCurrentPosition());
            }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                android.util.Log.e("ZaPlaybackTrace", "player error", error);
            }
        });
        Player traced = new ForwardingPlayer(player) {
            @Override public void stop() {
                MediaSession.ControllerInfo caller = session == null ? null : session.getControllerForCurrentRequest();
                android.util.Log.i("ZaPlaybackTrace", "stop called; origin=" + (caller == null ? "none"
                        : caller.getPackageName() + "/" + caller.hashCode()), new Throwable("stop caller"));
                super.stop();
            }
            @Override public void setPlayWhenReady(boolean value) {
                android.util.Log.i("ZaPlaybackTrace", "setPlayWhenReady=" + value, new Throwable("pwr caller"));
                super.setPlayWhenReady(value);
            }
            @Override public void release() {
                android.util.Log.i("ZaPlaybackTrace", "release called", new Throwable("release caller"));
                super.release();
            }
        };
        var openApp = android.app.PendingIntent.getActivity(this, 0,
                new android.content.Intent(this, MainActivity.class),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, traced).setSessionActivity(openApp)
                .setCallback(new MediaSession.Callback() {
                    @Override public int onPlayerCommandRequest(MediaSession session, MediaSession.ControllerInfo controller, int command) {
                        android.util.Log.i("ZaPlaybackTrace", "command=" + command + " controller="
                                + controller.getPackageName() + "/" + controller.hashCode());
                        return MediaSession.Callback.super.onPlayerCommandRequest(session, controller, command);
                    }
                    @Override public void onDisconnected(MediaSession session, MediaSession.ControllerInfo controller) {
                        android.util.Log.i("ZaPlaybackTrace", "disconnected=" + controller.getPackageName() + "/" + controller.hashCode());
                    }
                }).build();
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller) {
        return session;
    }

    @Override public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        android.view.KeyEvent key = intent == null ? null : intent.getParcelableExtra(android.content.Intent.EXTRA_KEY_EVENT);
        android.util.Log.i("ZaPlaybackTrace", "start action=" + (intent == null ? "null" : intent.getAction())
                + " key=" + (key == null ? -1 : key.getKeyCode()) + " dismissed="
                + (intent != null && intent.getBooleanExtra("androidx.media3.session.NOTIFICATION_DISMISSED_EVENT_KEY", false)));
        return super.onStartCommand(intent, flags, startId);
    }

    @Override public void onTaskRemoved(android.content.Intent rootIntent) {
        android.util.Log.i("ZaPlaybackTrace", "task removed foreground=" + isPlaybackOngoing());
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        android.util.Log.i("ZaPlaybackTrace", "service destroy");
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }
}
