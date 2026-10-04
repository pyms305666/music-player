package app.musicplayer.android.ui;

import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.playlist.LocalSearch;
import app.musicplayer.playlist.SearchSnapshot;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SortDirection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Library changes sort and index once; typing only filters the captured rows. */
public final class LocalTrackList implements AutoCloseable {
    private final LocalSearch<TrackRow> search;

    public LocalTrackList(Executor ui, Consumer<List<TrackRow>> display) {
        search = new LocalSearch<>(ui, display);
    }

    public List<TrackEntry> replace(List<TrackEntry> entries, PlaylistSort sort,
                                   SortDirection direction, String query) {
        Comparator<TrackRow> comparator = switch (sort) {
            case ARTIST -> Comparator.comparing(TrackRow::artist, String.CASE_INSENSITIVE_ORDER);
            case FILE_NAME -> Comparator.comparing(TrackRow::fileName, String.CASE_INSENSITIVE_ORDER);
            case CREATED_AT -> Comparator.comparingLong(row -> row.entry().createdAt());
            case TITLE -> Comparator.comparing(TrackRow::title, String.CASE_INSENSITIVE_ORDER);
        };
        if (direction == SortDirection.DESCENDING) comparator = comparator.reversed();
        List<TrackRow> rows = entries.stream().map(TrackRow::of).sorted(comparator).toList();
        search.replace(new SearchSnapshot<>(rows.stream().map(row -> new SearchSnapshot.Entry<>(
                row, row.title(), row.artist(), row.fileName())).toList()), query);
        return rows.stream().map(TrackRow::entry).toList();
    }

    public void search(String query) { search.search(query); }
    @Override public void close() { search.close(); }
}
