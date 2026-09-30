package app.musicplayer.android;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/** The service owns the complete queue, so automatic transitions do not require the UI. */
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
        var openApp = android.app.PendingIntent.getActivity(this, 0,
                new android.content.Intent(this, MainActivity.class),
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(openApp).build();
    }

    @Nullable @Override public MediaSession onGetSession(MediaSession.ControllerInfo controller) {
        return session;
    }

    @Override public void onDestroy() {
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }
}
