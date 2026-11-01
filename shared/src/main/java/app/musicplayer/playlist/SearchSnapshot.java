package app.musicplayer.playlist;

import app.musicplayer.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地搜索的不可变元数据快照。捕获名称、歌手和文件名的规范化文本，避免筛选过程中读取正在改变的模型。 Matching fields are captured once; filtering
 * never reads mutable track metadata.
 */
public final class SearchSnapshot<T> {
    /** 表示一条歌单或歌曲记录。 */
    public record Entry<T>(T value, String title, String artist, String fileName) {
        /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
        public Entry {
            title = normalize(title);
            artist = normalize(artist);
            fileName = normalize(fileName);
        }

        /** 应用本组件的名称、身份或搜索规则，检查候选是否匹配。 */
        boolean matches(String query) {
            return title.contains(query) || artist.contains(query) || fileName.contains(query);
        }
    }

    private final List<Entry<T>> entries;
    private final List<T> values;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public SearchSnapshot(List<Entry<T>> entries) {
        this.entries = List.copyOf(entries);
        this.values = this.entries.stream().map(Entry::value).toList();
    }

    /** 捕获歌曲的可搜索元数据，建立不可变搜索快照。 */
    public static SearchSnapshot<Track> ofTracks(List<Track> tracks) {
        return new SearchSnapshot<>(
                tracks.stream()
                        .map(
                                track ->
                                        new Entry<>(
                                                track,
                                                track.title(),
                                                track.artist(),
                                                track.path().getFileName().toString()))
                        .toList());
    }

    /** 返回快照或集合中保存的对应值。 */
    public List<T> values() {
        return values;
    }

    /** 根据规范化查询筛选快照中的条目。 */
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

    /** 按本组件的比较规则统一文本或路径，减少大小写与标点差异。 */
    private static String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
