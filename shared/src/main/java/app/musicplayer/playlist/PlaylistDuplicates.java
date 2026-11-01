package app.musicplayer.playlist;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 歌单下载前的本地重复检查。将曲库和已有歌单关联为候选，按位置及名称规则生成重复匹配结果。 Source identity is evidence; matching metadata is a
 * candidate requiring user confirmation.
 */
public final class PlaylistDuplicates {
    /** 保存可参与重复检查的本地歌曲。 */
    public record Local(String location, String title, String artist, String sourceIdentity) {}

    /** 保存重复或版本匹配结果。 */
    public record Match(NamedPlaylist.Entry entry, List<Local> candidates, boolean confirmed) {}

    /** 从歌单条目生成可参与重复检查的本地关联信息。 */
    public static List<Local> associations(NamedPlaylist.Entry entry) {
        var original = entry.track();
        var actual = entry.downloadTrack();
        List<Local> result = new ArrayList<>();
        if (actual == null || PlaylistSongMatcher.matches(original, actual))
            result.add(
                    new Local(
                            entry.location(),
                            original.title(),
                            original.artist(),
                            original.identity()));
        if (actual != null)
            result.add(
                    new Local(
                            entry.location(), actual.title(), actual.artist(), actual.identity()));
        return result;
    }

    private final Map<String, List<Local>> bySource = new HashMap<>(), byMetadata = new HashMap<>();

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public PlaylistDuplicates(List<Local> files) {
        for (Local file : files) {
            if (!file.sourceIdentity().isBlank())
                bySource.computeIfAbsent(file.sourceIdentity(), k -> new ArrayList<>()).add(file);
            if (known(file.artist()))
                byMetadata
                        .computeIfAbsent(key(file.title(), file.artist()), k -> new ArrayList<>())
                        .add(file);
        }
    }

    /** 应用本组件的名称、身份或搜索规则，检查候选是否匹配。 */
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

    /** 在已有集合中选择不冲突的名称或去重后的候选。 */
    private static List<Local> unique(List<Local> files) {
        Map<String, Local> locations = new java.util.LinkedHashMap<>();
        for (var file : files) locations.putIfAbsent(file.location(), file);
        return List.copyOf(locations.values());
    }

    /** 检查歌曲的身份、歌名或歌手是否具有可匹配的有效信息。 */
    private static boolean known(String value) {
        return value != null && !value.isBlank() && !"未知歌手".equals(value);
    }

    /** 生成当前数据的稳定键，供去重、索引或缓存使用。 */
    private static String key(String title, String artist) {
        return normalize(title) + "|" + normalize(artist);
    }

    /** 按本组件的比较规则统一文本或路径，减少大小写与标点差异。 */
    private static String normalize(String value) {
        // Keep punctuation and version suffixes: Live/remix/cover must never collapse into the
        // original.
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
