package app.musicplayer.lyrics;

import app.musicplayer.model.OnlineLyricsResult;
import app.musicplayer.model.Track;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

interface OnlineLyricsProvider {
    Optional<OnlineLyricsResult> search(Track track, Duration duration, HttpClient httpClient);
}
