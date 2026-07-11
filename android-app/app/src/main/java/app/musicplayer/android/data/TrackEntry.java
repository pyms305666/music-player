package app.musicplayer.android.data;

import app.musicplayer.model.Track;

public record TrackEntry(Track track, long createdAt) {
}
