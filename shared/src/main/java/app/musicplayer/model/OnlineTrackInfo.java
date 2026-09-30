package app.musicplayer.model;

public record OnlineTrackInfo(
        String source,
        String title,
        String artist,
        String album,
        String artworkUrl,
        String primaryId,
        String secondaryId,
        boolean downloadable,
        String availabilityText
) {
    public OnlineTrackInfo(
            String source,
            String title,
            String artist,
            String album,
            String artworkUrl,
            String primaryId,
            String secondaryId
    ) {
        this(source, title, artist, album, artworkUrl, primaryId, secondaryId, false, "待检测");
    }

    public OnlineTrackInfo withAvailability(boolean downloadable, String availabilityText) {
        return new OnlineTrackInfo(
                source,
                title,
                artist,
                album,
                artworkUrl,
                primaryId,
                secondaryId,
                downloadable,
                availabilityText
        );
    }

    public boolean canAttemptDownload() {
        return downloadable || "可尝试下载".equals(availabilityText);
    }

    public String subtitle() {
        String albumText = album == null || album.isBlank() ? "未知专辑" : album;
        String artistText = artist == null || artist.isBlank() ? "未知歌手" : artist;
        return artistText + " · " + albumText + " · " + source + " · " + availabilityText;
    }
}
