package app.musicplayer.android.ui;

import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.playlist.LocalSearch;
import app.musicplayer.playlist.PlaylistSort;
import app.musicplayer.playlist.SearchSnapshot;
import app.musicplayer.playlist.SortDirection;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Android 本地曲库搜索与排序协调。捕获不可变搜索快照，后台筛选后交给 TrackAdapter 显示，避免搜索替换正在播放的队列。 Library changes sort and
 * index once; typing only filters the captured rows.
 */
public final class LocalTrackList implements AutoCloseable {
    private final LocalSearch<TrackRow> search;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public LocalTrackList(Executor ui, Consumer<List<TrackRow>> display) {
        search = new LocalSearch<>(ui, display);
    }

    /** 用新的不可变列表或控件替换旧数据，保留组件规定的选择规则。 */
    public List<TrackEntry> replace(
            List<TrackEntry> entries, PlaylistSort sort, SortDirection direction, String query) {
        Comparator<TrackRow> comparator =
                switch (sort) {
                    case ARTIST ->
                            Comparator.comparing(TrackRow::artist, String.CASE_INSENSITIVE_ORDER);
                    case FILE_NAME ->
                            Comparator.comparing(TrackRow::fileName, String.CASE_INSENSITIVE_ORDER);
                    case CREATED_AT -> Comparator.comparingLong(row -> row.entry().createdAt());
                    case TITLE ->
                            Comparator.comparing(TrackRow::title, String.CASE_INSENSITIVE_ORDER);
                };
        if (direction == SortDirection.DESCENDING) comparator = comparator.reversed();
        List<TrackRow> rows = entries.stream().map(TrackRow::of).sorted(comparator).toList();
        search.replace(
                new SearchSnapshot<>(
                        rows.stream()
                                .map(
                                        row ->
                                                new SearchSnapshot.Entry<>(
                                                        row,
                                                        row.title(),
                                                        row.artist(),
                                                        row.fileName()))
                                .toList()),
                query);
        return rows.stream().map(TrackRow::entry).toList();
    }

    /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
    public void search(String query) {
        search.search(query);
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        search.close();
    }
}
