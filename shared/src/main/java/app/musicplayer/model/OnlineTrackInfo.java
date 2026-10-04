package app.musicplayer.model;

public record OnlineTrackInfo(
        String source,
        String title,
        String artist,
        String album,
        String artworkUrl,
        String primaryId,
        String secondaryId,
        Availability availability,
        String availabilityText
) {
    public enum Availability { PENDING, TENTATIVE, AVAILABLE, UNAVAILABLE }
    public OnlineTrackInfo(
            String source,
            String title,
            String artist,
            String album,
            String artworkUrl,
            String primaryId,
            String secondaryId
    ) {
        this(source, title, artist, album, artworkUrl, primaryId, secondaryId, Availability.PENDING, "待检测");
    }

    public OnlineTrackInfo withAvailability(Availability availability, String availabilityText) {
        return new OnlineTrackInfo(
                source,
                title,
                artist,
                album,
                artworkUrl,
                primaryId,
                secondaryId,
                availability,
                availabilityText
        );
    }

    public boolean canAttemptDownload() {
        return availability == Availability.AVAILABLE || availability == Availability.TENTATIVE;
    }

    public boolean downloadable() { return availability == Availability.AVAILABLE; }

    public String identity() { return (source == null ? "" : source) + "|" + (primaryId == null ? "" : primaryId); }

    public String subtitle() {
        String albumText = album == null || album.isBlank() ? "未知专辑" : album;
        String artistText = artist == null || artist.isBlank() ? "未知歌手" : artist;
        return artistText + " · " + albumText + " · " + source + " · " + availabilityText;
    }
}
