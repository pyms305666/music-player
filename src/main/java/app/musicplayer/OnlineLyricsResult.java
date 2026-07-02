package app.musicplayer;

record OnlineLyricsResult(String source, String rawLyrics, String artworkUrl, int score) {
    boolean hasLyrics() {
        return rawLyrics != null && !rawLyrics.isBlank();
    }
}
