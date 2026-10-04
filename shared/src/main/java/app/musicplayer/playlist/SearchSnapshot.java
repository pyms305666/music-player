package app.musicplayer.playlist;

import app.musicplayer.model.Track;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

/** Matching fields are captured once; filtering never reads mutable track metadata. */
public final class SearchSnapshot<T> {
    public record Entry<T>(T value, String title, String artist, String fileName) {
        public Entry {
            title = normalize(title);
            artist = normalize(artist);
            fileName = normalize(fileName);
        }
        boolean matches(String query) {
            return title.contains(query) || artist.contains(query) || fileName.contains(query);
        }
    }

    private final List<Entry<T>> entries;
    private final List<T> values;

    public SearchSnapshot(List<Entry<T>> entries) {
        this.entries = List.copyOf(entries);
        this.values = this.entries.stream().map(Entry::value).toList();
    }

    public static SearchSnapshot<Track> ofTracks(List<Track> tracks) {
        return new SearchSnapshot<>(tracks.stream().map(track -> new Entry<>(track,
                track.title(), track.artist(), track.path().getFileName().toString())).toList());
    }

    public List<T> values() { return values; }

    public List<T> filter(String query) {
        String key = normalize(query);
        if (key.isEmpty()) return values;
        List<T> matches = new ArrayList<>();
        for (Entry<T> entry : entries) {
            if (Thread.currentThread().isInterrupted()) return List.of();
            if (entry.matches(key)) matches.add(entry.value());
        }
        return List.copyOf(matches);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
