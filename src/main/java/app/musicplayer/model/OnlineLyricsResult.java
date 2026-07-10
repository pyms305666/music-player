package app.musicplayer.model;

public record OnlineLyricsResult(String source, String rawLyrics, String artworkUrl, int score) {
    public boolean hasLyrics() {
        return rawLyrics != null && !rawLyrics.isBlank();
    }
}
