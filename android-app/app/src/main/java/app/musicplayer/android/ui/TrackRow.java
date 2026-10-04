package app.musicplayer.android.ui;

import app.musicplayer.android.data.TrackEntry;

/** Immutable display fields let DiffUtil detect changes to mutable Track metadata. */
public record TrackRow(TrackEntry entry, String key, String title, String artist, String fileName) {
    public static TrackRow of(TrackEntry entry) {
        return new TrackRow(entry, entry.key(), entry.track().title(), entry.track().artist(), entry.fileName());
    }
}
