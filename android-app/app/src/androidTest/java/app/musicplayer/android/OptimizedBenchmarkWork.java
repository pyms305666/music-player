package app.musicplayer.android;

import android.widget.ScrollView;
import android.widget.TextView;
import app.musicplayer.android.data.*;
import app.musicplayer.android.ui.AndroidLyricsPresenter;
import app.musicplayer.model.Lyrics;
import java.util.*;

/** Direct production API calls; kept separate so legacy APK comparisons never load these types. */
final class OptimizedBenchmarkWork {
    private OptimizedBenchmarkWork() { }
    static void imports(AndroidMusicDatabase database, List<TrackEntry> entries) {
        for (int start = 0; start < entries.size(); start += AndroidMusicDatabase.IMPORT_BATCH_SIZE) {
            List<AndroidMusicDatabase.ImportedTrack> items = new ArrayList<>();
            for (int i = start; i < Math.min(entries.size(), start + AndroidMusicDatabase.IMPORT_BATCH_SIZE); i++)
                items.add(new AndroidMusicDatabase.ImportedTrack("content://qa-import/" + i, entries.get(i)));
            database.saveImportedTracks(items);
        }
    }
    static Object presenter(TextView text, ScrollView scroll) { return new AndroidLyricsPresenter(text, scroll); }
    static void show(Object presenter, Lyrics lyrics) { ((AndroidLyricsPresenter)presenter).show(lyrics); }
    static void update(Object presenter, long millis) { ((AndroidLyricsPresenter)presenter).update(millis); }
}
