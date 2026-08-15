package app.musicplayer.android.data;

import app.musicplayer.model.Track;

public record TrackEntry(
        Track track,
        long createdAt,
        String location,
        StorageType storageType,
        String fileName) {

    public enum StorageType {
        FILE,
        MEDIA_STORE
    }

    public TrackEntry(Track track, long createdAt) {
        this(
                track,
                createdAt,
                track.path().toAbsolutePath().toString(),
                StorageType.FILE,
                track.path().getFileName().toString());
    }

    public static TrackEntry mediaStore(
            Track track,
            long createdAt,
            String contentUri,
            String fileName) {
        return new TrackEntry(track, createdAt, contentUri, StorageType.MEDIA_STORE, fileName);
    }

    public String key() {
        return location;
    }
}
