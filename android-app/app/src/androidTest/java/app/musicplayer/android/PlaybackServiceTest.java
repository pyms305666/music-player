package app.musicplayer.android;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import android.os.Handler;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Uses disposable cache audio only. Never resets or imports into the personal library. */
@RunWith(AndroidJUnit4.class)
public class PlaybackServiceTest {
    private MainActivity activity;
    private MediaController controller;
    private final List<File> fixtures = new ArrayList<>();
    private List<MediaItem> original;
    private int originalIndex, repeat;
    private long position;
    private boolean shuffle, playing;
    private float volume;

    private void main(Runnable work) {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        new Handler(Looper.getMainLooper()).post(() -> {
            try { work.run(); } catch (Throwable failure) { error.set(failure); }
            finally { finished.countDown(); }
        });
        try { assertTrue("Main thread callback timed out", finished.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); throw new AssertionError(cancelled); }
        if (error.get() != null) throw new AssertionError(error.get());
    }

    private MainActivity openActivity() throws Exception {
        // Shell launch avoids vendor restrictions on instrumentation activity starts.
        try (var command = InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("am start -n app.musicplayer.android/.MainActivity")) {
            try (var input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(command)) { while (input.read(new byte[1024]) != -1) { /* Drain shell output on API 28 too. */ } }
        }
        for (int i = 0; i < 100; i++) {
            AtomicReference<MainActivity> screen = new AtomicReference<>();
            main(() -> {
                for (var resumed : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if (resumed instanceof MainActivity) screen.set((MainActivity)resumed);
            });
            if (screen.get() != null) return screen.get();
            Thread.sleep(100);
        }
        throw new AssertionError("Activity did not resume");
    }

    private void closeActivity() throws Exception {
        if (activity == null) return;
        MainActivity screen = activity;
        main(screen::finish);
        for (int i = 0; i < 50 && !screen.isDestroyed(); i++) Thread.sleep(100);
        assertTrue(screen.isDestroyed());
        activity = null;
    }
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    private MediaController connect() throws Exception {
        AtomicReference<ListenableFuture<MediaController>> future = new AtomicReference<>();
        main(() -> future.set(new MediaController.Builder(context(),
                new SessionToken(context(), new ComponentName(context(), PlaybackService.class))).buildAsync()));
        return future.get().get(10, TimeUnit.SECONDS);
    }

    @Before public void before() throws Exception {
        activity = openActivity();
        controller = connect();
        main(() -> {
            original = new ArrayList<>();
            for (int i = 0; i < controller.getMediaItemCount(); i++) original.add(controller.getMediaItemAt(i));
            originalIndex = controller.getCurrentMediaItemIndex(); position = controller.getCurrentPosition();
            repeat = controller.getRepeatMode(); shuffle = controller.getShuffleModeEnabled();
            playing = controller.getPlayWhenReady(); volume = controller.getVolume();
        });
    }

    @After public void after() throws Exception {
        if (controller != null) main(() -> {
            controller.stop();
            if (original != null && !original.isEmpty()) {
                controller.setMediaItems(original, originalIndex, position); controller.prepare();
            } else controller.clearMediaItems();
            controller.setRepeatMode(repeat); controller.setShuffleModeEnabled(shuffle);
            controller.setVolume(volume); controller.setPlayWhenReady(playing);
            controller.release();
        });
        closeActivity();
        for (File file : fixtures) file.delete();
    }

    private MediaItem audio(String id, int seconds) throws Exception {
        File file = new File(context().getCacheDir(), "qa-" + id + ".wav");
        fixtures.add(file);
        int size = 8000 * 2 * seconds;
        ByteBuffer wav = ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN);
        wav.put(new byte[]{'R','I','F','F'}).putInt(36 + size).put(new byte[]{'W','A','V','E','f','m','t',' '});
        wav.putInt(16).putShort((short)1).putShort((short)1).putInt(8000).putInt(16000).putShort((short)2).putShort((short)16);
        wav.put(new byte[]{'d','a','t','a'}).putInt(size);
        try (var output = new FileOutputStream(file)) { output.write(wav.array()); }
        return new MediaItem.Builder().setMediaId(id).setUri(Uri.fromFile(file)).build();
    }

    private void start(List<MediaItem> queue, int repeatMode) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 10_000;
        main(() -> {
            controller.setVolume(0f); controller.setShuffleModeEnabled(false); controller.setRepeatMode(repeatMode);
            controller.setMediaItems(queue); controller.prepare(); controller.play();
        });
        long previous = -1;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicReference<Long> sample = new AtomicReference<>(-1L);
            main(() -> {
                if (!controller.isPlaying() || controller.getCurrentMediaItemIndex() != 0
                        || controller.getRepeatMode() != repeatMode || controller.getShuffleModeEnabled()
                        || controller.getMediaItemCount() != queue.size()) return;
                for (int i = 0; i < queue.size(); i++)
                    if (!queue.get(i).mediaId.equals(controller.getMediaItemAt(i).mediaId)) return;
                sample.set(controller.getCurrentPosition());
            });
            if (previous >= 0 && sample.get() >= previous + 50) return;
            previous = sample.get();
            Thread.sleep(100);
        }
        fail("Expected audio did not advance; " + playbackDiagnostics());
    }

    // No URI or personal song identifiers: diagnostics are safe on the user's library too.
    private String playbackDiagnostics() {
        AtomicReference<String> state = new AtomicReference<>();
        main(() -> state.set("state=" + controller.getPlaybackState()
                + ", playing=" + controller.isPlaying() + ", playWhenReady=" + controller.getPlayWhenReady()
                + ", suppression=" + controller.getPlaybackSuppressionReason()
                + ", repeat=" + controller.getRepeatMode() + ", shuffle=" + controller.getShuffleModeEnabled()
                + ", index=" + controller.getCurrentMediaItemIndex() + ", count=" + controller.getMediaItemCount()
                + ", position=" + controller.getCurrentPosition() + ", duration=" + controller.getDuration()
                + ", error=" + (controller.getPlayerError() == null ? "none" : controller.getPlayerError().getErrorCodeName())));
        return state.get();
    }

    @Test public void queueLoopsAfterActivityDestroyedAndReopensWithSameSession() throws Exception {
        List<String> transitions = Collections.synchronizedList(new ArrayList<>());
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        long began = android.os.SystemClock.elapsedRealtime();
        var activityDestroyed = new java.util.concurrent.atomic.AtomicBoolean();
        CountDownLatch wrapped = new CountDownLatch(1);
        main(() -> controller.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                if (item == null) return;
                String id = item.mediaId.equals("first") || item.mediaId.equals("second") ? item.mediaId : "<other>";
                transitions.add(id);
                if (events.size() < 64) events.add((android.os.SystemClock.elapsedRealtime() - began)
                        + "ms transition=" + id + " reason=" + reason);
                if (activityDestroyed.get() && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                        && item.mediaId.equals("first")) wrapped.countDown();
            }
            @Override public void onEvents(Player player, Player.Events changed) {
                if (events.size() < 64) events.add((android.os.SystemClock.elapsedRealtime() - began)
                        + "ms state=" + player.getPlaybackState() + " playing=" + player.isPlaying()
                        + " suppression=" + player.getPlaybackSuppressionReason());
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                    Player.PositionInfo newPosition, int reason) {
                if (events.size() < 64) events.add((android.os.SystemClock.elapsedRealtime() - began)
                        + "ms discontinuity=" + oldPosition.mediaItemIndex + ":" + oldPosition.positionMs
                        + "->" + newPosition.mediaItemIndex + ":" + newPosition.positionMs + " reason=" + reason);
            }
            @Override public void onPlayWhenReadyChanged(boolean value, int reason) {
                if (events.size() < 64) events.add((android.os.SystemClock.elapsedRealtime() - began)
                        + "ms playWhenReady=" + value + " reason=" + reason);
            }
        }));
        start(List.of(audio("first", 2), audio("second", 2)), Player.REPEAT_MODE_ALL);
        closeActivity();
        main(() -> {
            activityDestroyed.set(true);
            if (events.size() < 64) events.add((android.os.SystemClock.elapsedRealtime() - began) + "ms Activity destroyed");
        });
        boolean didWrap = wrapped.await(12, TimeUnit.SECONDS);
        assertTrue("Last track failed to wrap; " + playbackDiagnostics() + "; events=" + events, didWrap);
        assertEquals(Arrays.asList("first", "second", "first"), transitions.subList(0, 3));
        // Remove every UI/controller binding, leaving only the foreground service.
        main(controller::release); controller = null;
        Thread.sleep(1200);
        controller = connect();
        main(() -> { assertTrue(controller.isPlaying()); assertEquals(2, controller.getMediaItemCount()); });
        activity = openActivity();
        Thread.sleep(500);
        main(() -> {
            try {
                var field = MainActivity.class.getDeclaredField("player"); field.setAccessible(true);
                MediaController ui = (MediaController)field.get(activity);
                assertNotNull(ui); assertEquals(controller.getCurrentMediaItem().mediaId, ui.getCurrentMediaItem().mediaId);
                assertTrue(ui.isPlaying());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    @Test public void repeatOneAndTransportCommandsRemainAvailable() throws Exception {
        start(List.of(audio("single", 1), audio("other", 1)), Player.REPEAT_MODE_ONE);
        long previous = 0;
        boolean repeated = false;
        for (int i = 0; i < 60; i++) {
            AtomicReference<Long> current = new AtomicReference<>();
            main(() -> current.set(controller.getCurrentPosition()));
            if (previous > 600 && current.get() < previous - 400) { repeated = true; break; }
            previous = current.get();
            Thread.sleep(100);
        }
        assertTrue("Single-track position did not wrap", repeated);
        main(() -> {
            assertEquals("single", controller.getCurrentMediaItem().mediaId);
            controller.pause(); assertFalse(controller.getPlayWhenReady());
            controller.setRepeatMode(Player.REPEAT_MODE_ALL);
            controller.seekToNextMediaItem();
        });
        awaitMediaId("other");
        // Commands are updated asynchronously by the session after a transition.
        awaitCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM);
        main(controller::seekToPreviousMediaItem);
        awaitMediaId("single");
        main(() -> { controller.setShuffleModeEnabled(true); assertTrue(controller.getShuffleModeEnabled()); });
    }

    @Test public void screenOffQueueContinuesAndWraps() throws Exception {
        CountDownLatch wrapped = new CountDownLatch(1);
        main(() -> controller.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && item != null && item.mediaId.equals("screen-first")) wrapped.countDown();
            }
        }));
        start(List.of(audio("screen-first", 2), audio("screen-second", 2)), Player.REPEAT_MODE_ALL);
        try {
            shell("input keyevent 223");
            boolean didWrap = wrapped.await(12, TimeUnit.SECONDS);
            assertTrue("Screen-off queue failed to loop; " + playbackDiagnostics(), didWrap);
            main(() -> assertTrue(controller.isPlaying()));
        } finally { shell("input keyevent 224"); shell("wm dismiss-keyguard"); }
    }

    @Test public void clearedQueueNotificationCannotDismissRestartedPlayback() throws Exception {
        start(List.of(audio("notify-first", 20)), Player.REPEAT_MODE_ALL);
        android.service.notification.StatusBarNotification oldNotification = awaitMediaNotification(-1);
        main(() -> { controller.stop(); controller.clearMediaItems(); });
        long deadline = android.os.SystemClock.elapsedRealtime() + 5_000;
        while (!mediaNotifications().isEmpty() && android.os.SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(50);
        assertTrue("Cleared queue retained a media notification", mediaNotifications().isEmpty());

        start(List.of(audio("notify-second", 20)), Player.REPEAT_MODE_ALL);
        var current = awaitMediaNotification(oldNotification.getId());
        var manager = (android.app.NotificationManager) context().getSystemService(Context.NOTIFICATION_SERVICE);
        // An asynchronous system removal for the old key must not affect the new queue.
        manager.cancel(oldNotification.getId());
        AtomicReference<Long> before = new AtomicReference<>();
        main(() -> before.set(controller.getCurrentPosition()));
        Thread.sleep(600);
        String diagnostic = playbackDiagnostics();
        main(() -> {
            assertTrue("Obsolete notification removal stopped playback; " + diagnostic, controller.isPlaying());
            assertTrue(controller.getCurrentPosition() >= before.get() + 300);
        });

        // A dismissal of the current notification must still stop playback normally.
        assertNotNull(current.getNotification().deleteIntent);
        current.getNotification().deleteIntent.send();
        deadline = android.os.SystemClock.elapsedRealtime() + 5_000;
        boolean stopped = false;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            AtomicReference<Boolean> value = new AtomicReference<>(false);
            main(() -> value.set(controller.getPlaybackState() == Player.STATE_IDLE && !controller.isPlaying()));
            if (value.get()) { stopped = true; break; }
            Thread.sleep(50);
        }
        assertTrue("Current notification dismissal did not stop playback; " + playbackDiagnostics(), stopped);
    }

    private List<android.service.notification.StatusBarNotification> mediaNotifications() {
        var manager = (android.app.NotificationManager) context().getSystemService(Context.NOTIFICATION_SERVICE);
        List<android.service.notification.StatusBarNotification> result = new ArrayList<>();
        for (var notification : manager.getActiveNotifications()) {
            if (notification.getNotification().extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION))
                result.add(notification);
        }
        return result;
    }

    private android.service.notification.StatusBarNotification awaitMediaNotification(int obsoleteId) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 5_000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            var notifications = mediaNotifications();
            if (notifications.size() == 1 && notifications.get(0).getId() != obsoleteId) return notifications.get(0);
            Thread.sleep(50);
        }
        fail("Expected exactly one current media notification after queue restart");
        return null;
    }

    private void shell(String command) throws Exception {
        try (var descriptor = InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
             var input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)) { while (input.read(new byte[1024]) != -1) { /* Drain shell output on API 28 too. */ } }
    }

    @Test public void temporaryAudioFocusLossPausesAndResumes() throws Exception {
        start(List.of(audio("focus", 20)), Player.REPEAT_MODE_ALL);
        AudioManager manager = (AudioManager) context().getSystemService(Context.AUDIO_SERVICE);
        AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                .setOnAudioFocusChangeListener(value -> {}).build();
        try {
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(focus));
            awaitPlaying(false);
        } finally { manager.abandonAudioFocusRequest(focus); }
        awaitPlaying(true);
    }

    @Test public void typingLocalSearchDoesNotReplaceThePlayingQueue() throws Exception {
        start(List.of(audio("search-fixture", 20)), Player.REPEAT_MODE_ALL);
        var changed = new java.util.concurrent.atomic.AtomicInteger();
        main(() -> controller.addListener(new Player.Listener() {
            @Override public void onTimelineChanged(androidx.media3.common.Timeline timeline, int reason) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) changed.incrementAndGet();
            }
        }));
        main(() -> {
            android.widget.EditText search = activity.findViewById(R.id.localSearch);
            search.setText("a"); search.setText("ab"); search.setText("abc"); search.setText("");
        });
        Thread.sleep(500);
        main(() -> {
            assertEquals(0, changed.get());
            assertTrue(controller.isPlaying());
            assertEquals("search-fixture", controller.getCurrentMediaItem().mediaId);
            assertEquals(1, controller.getMediaItemCount());
        });
    }

    @Test public void personalLibraryAudioAdvancesInBackground() throws Exception {
        app.musicplayer.android.data.TrackEntry entry;
        try (var database = new app.musicplayer.android.data.AndroidMusicDatabase(context())) {
            var tracks = database.loadTracks();
            org.junit.Assume.assumeFalse("Disposable CI library is empty", tracks.isEmpty());
            entry = tracks.get(0);
        }
        // Close the screen to prevent lyric lookup or metadata writes during a read-only check.
        closeActivity();
        Uri uri = entry.storageType() == app.musicplayer.android.data.TrackEntry.StorageType.MEDIA_STORE
                ? Uri.parse(entry.location()) : Uri.fromFile(new File(entry.location()));
        start(List.of(new MediaItem.Builder().setMediaId("personal-validation").setUri(uri).build()),
                Player.REPEAT_MODE_ALL);
        Thread.sleep(500);
        AtomicReference<Long> before = new AtomicReference<>();
        main(() -> before.set(controller.getCurrentPosition()));
        Thread.sleep(700);
        main(() -> {
            assertTrue(controller.isPlaying());
            assertTrue("Personal library audio did not advance", controller.getCurrentPosition() > before.get() + 300);
        });
    }

    private void awaitPlaying(boolean expected) throws Exception {
        for (int i = 0; i < 50; i++) {
            AtomicReference<Boolean> value = new AtomicReference<>(); main(() -> value.set(controller.isPlaying()));
            if (value.get() == expected) return;
            Thread.sleep(100);
        }
        fail("Expected isPlaying=" + expected);
    }

    private void awaitMediaId(String expected) throws Exception {
        for (int i = 0; i < 50; i++) {
            AtomicReference<String> id = new AtomicReference<>();
            main(() -> id.set(controller.getCurrentMediaItem().mediaId));
            if (expected.equals(id.get())) return;
            Thread.sleep(100);
        }
        fail("Expected mediaId=" + expected);
    }

    private void awaitCommand(int command) throws Exception {
        for (int i = 0; i < 50; i++) {
            AtomicReference<Boolean> available = new AtomicReference<>();
            main(() -> available.set(controller.isCommandAvailable(command)));
            if (available.get()) return;
            Thread.sleep(100);
        }
        fail("Transport command unavailable: " + command);
    }
}
