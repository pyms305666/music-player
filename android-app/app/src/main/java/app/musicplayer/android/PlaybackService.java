package app.musicplayer.android;

import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.DefaultMediaNotificationProvider;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

/**
 * Android 后台播放服务。持有 Media3 播放器及媒体会话，支持页面退出后继续播放、系统媒体控制和播放队列恢复。 The service owns the complete queue,
 * so automatic transitions do not require the UI.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
    private MediaSession session;
    private int notificationId = new java.util.Random().nextInt(Integer.MAX_VALUE - 1) + 1;
    private boolean notificationCycleEnded = true;

    /** 创建页面或服务，初始化控件、持久化和生命周期依赖。 */
    @Override
    public void onCreate() {
        super.onCreate();
        ExoPlayer player =
                new ExoPlayer.Builder(this)
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(C.USAGE_MEDIA)
                                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                                        .build(),
                                true)
                        .setHandleAudioBecomingNoisy(true)
                        .setWakeMode(C.WAKE_MODE_LOCAL)
                        .build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setVolume(0.7f);
        player.addListener(
                new Player.Listener() {
                    /** 播放队列变化时同步服务保存的状态与媒体通知。 */
                    @Override
                    public void onTimelineChanged(
                            androidx.media3.common.Timeline timeline, int reason) {
                        if (timeline.isEmpty()) notificationCycleEnded = true;
                    }
                });
        setMediaNotificationProvider(
                new DefaultMediaNotificationProvider.Builder(this)
                        .setNotificationIdProvider(
                                mediaSession -> {
                                    // Android 13 SystemUI may process an old removal after a new
                                    // notification
                                    // has appeared. Give each new nonempty queue lifecycle a
                                    // different key.
                                    if (notificationCycleEnded
                                            && mediaSession.getPlayer().getMediaItemCount() > 0) {
                                        // A paused notification may never have belonged to a
                                        // foreground
                                        // service, so changing startForeground's ID cannot remove
                                        // it.
                                        getSystemService(android.app.NotificationManager.class)
                                                .cancel(notificationId);
                                        notificationId =
                                                notificationId == Integer.MAX_VALUE
                                                        ? 1
                                                        : notificationId + 1;
                                        notificationCycleEnded = false;
                                    }
                                    return notificationId;
                                })
                        .build());
        var openApp =
                android.app.PendingIntent.getActivity(
                        this,
                        0,
                        new android.content.Intent(this, MainActivity.class),
                        android.app.PendingIntent.FLAG_IMMUTABLE
                                | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        session = new MediaSession.Builder(this, player).setSessionActivity(openApp).build();
    }

    /** 向媒体控制器提供本服务持有的 MediaSession。 */
    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controller) {
        return session;
    }

    /** 页面或服务销毁时解除监听并释放自身资源，按顺序结束数据库工作。 */
    @Override
    public void onDestroy() {
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        super.onDestroy();
    }
}
