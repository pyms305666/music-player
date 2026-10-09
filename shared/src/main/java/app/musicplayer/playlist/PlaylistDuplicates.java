package app.musicplayer.playlist;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Source identity is evidence; matching metadata is a candidate requiring user confirmation. */
public final class PlaylistDuplicates {
    public record Local(String location, String title, String artist, String sourceIdentity) { }
    public record Match(NamedPlaylist.Entry entry, List<Local> candidates, boolean confirmed) { }
    public static List<Local> associations(NamedPlaylist.Entry entry){
        var original=entry.track();var actual=entry.downloadTrack();List<Local> result=new ArrayList<>();
        if(actual==null||PlaylistSongMatcher.matches(original,actual))result.add(new Local(entry.location(),original.title(),original.artist(),original.identity()));
        if(actual!=null)result.add(new Local(entry.location(),actual.title(),actual.artist(),actual.identity()));
        return result;
    }
    private final Map<String, List<Local>> bySource = new HashMap<>(), byMetadata = new HashMap<>();
    public PlaylistDuplicates(List<Local> files) {
        for (Local file : files) {
            if (!file.sourceIdentity().isBlank()) bySource.computeIfAbsent(file.sourceIdentity(), k -> new ArrayList<>()).add(file);
            if (known(file.artist())) byMetadata.computeIfAbsent(key(file.title(), file.artist()), k -> new ArrayList<>()).add(file);
        }
    }
    public List<Match> match(List<NamedPlaylist.Entry> entries) {
        List<Match> result = new ArrayList<>();
        for (var entry : entries) {
            var target = entry.userSelectedVersion() ? entry.downloadTrack() : entry.track();
            var exact = bySource.get(target.identity());
            var similar = byMetadata.get(key(target.title(), target.artist()));
            if (exact != null) result.add(new Match(entry, unique(exact), true));
            else if (similar != null) result.add(new Match(entry, unique(similar), false));
        }
        return List.copyOf(result);
    }
    private static List<Local> unique(List<Local> files) {
        Map<String,Local> locations=new java.util.LinkedHashMap<>();
        for(var file:files)locations.putIfAbsent(file.location(),file);
        return List.copyOf(locations.values());
    }
    private static boolean known(String value) { return value != null && !value.isBlank() && !"未知歌手".equals(value); }
    private static String key(String title, String artist) { return normalize(title) + "|" + normalize(artist); }
    private static String normalize(String value) {
        // Keep punctuation and version suffixes: Live/remix/cover must never collapse into the original.
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
