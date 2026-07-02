package app.musicplayer;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

interface OnlineLyricsProvider {
    Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient);
}
