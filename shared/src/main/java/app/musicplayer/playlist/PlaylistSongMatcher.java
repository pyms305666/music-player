package app.musicplayer.playlist;

import app.musicplayer.model.OnlineTrackInfo;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 歌单条目与在线候选的版本匹配。保留现场版等版本差异，规范化歌手顺序和标点，并按来源及匹配程度排序。 Playlist metadata identifies the requested
 * recording; download channels are independent.
 */
public final class PlaylistSongMatcher {
    private static final Pattern METADATA =
            Pattern.compile("\\((live(?:版)?|现场(?:版)?|cover:[^()]+)\\)");

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private PlaylistSongMatcher() {}

    /** 应用本组件的名称、身份或搜索规则，检查候选是否匹配。 */
    public static boolean matches(OnlineTrackInfo expected, OnlineTrackInfo candidate) {
        if (expected.primaryId() != null
                && !expected.primaryId().isBlank()
                && expected.identity().equals(candidate.identity())) return true;
        if (!known(expected.artist())
                || !known(candidate.artist())
                || !artists(expected.artist()).equals(artists(candidate.artist()))) return false;
        String title = normalize(expected.title()), other = normalize(candidate.title());
        if (title.isBlank()) return false;
        String album = normalize(expected.album()), otherAlbum = normalize(candidate.album());
        boolean sameAlbum = !album.isBlank() && album.equals(otherAlbum);
        if (title.equals(other)) {
            // A named performance must not be replaced by a different concert with the same "Live"
            // title.
            if (live(title) && !album.isBlank() && !sameAlbum) return false;
            return true;
        }
        if (!sameAlbum) return false;
        boolean cover = title.contains("(cover:"), otherCover = other.contains("(cover:");
        if (cover && otherCover) return false;
        if (live(title) != live(other) && (live(title) ? other : title).contains("(")) return false;
        // Some catalogs put Live in the album, or add the original artist as a cover credit.
        // Only reconcile those annotations with the SAME performer and an explicit identical album.
        return !metadataFree(title).isBlank() && metadataFree(title).equals(metadataFree(other));
    }

    /** 识别现场版本标记，防止与录音室版本误匹配。 */
    private static boolean live(String title) {
        return title.contains("(live)")
                || title.contains("(live版)")
                || title.contains("(现场)")
                || title.contains("(现场版)");
    }

    /** 提取用于比较的名称主体，去除不影响歌曲身份的元数据标记。 */
    private static String metadataFree(String title) {
        return METADATA.matcher(title).replaceAll("");
    }

    /** 按匹配程度和来源优先级排列候选歌曲。 */
    public static List<OnlineTrackInfo> ranked(
            OnlineTrackInfo expected, List<OnlineTrackInfo> results, boolean exactOnly) {
        Map<String, OnlineTrackInfo> unique = new LinkedHashMap<>();
        for (var info : results)
            if (info.primaryId() != null
                    && !info.primaryId().isBlank()
                    && (!exactOnly || matches(expected, info)))
                unique.putIfAbsent(info.identity(), info);
        return unique.values().stream()
                .sorted(
                        Comparator.comparingInt(
                                        (OnlineTrackInfo info) -> matches(expected, info) ? 0 : 1)
                                .thenComparingInt(info -> priority(info.source())))
                .toList();
    }

    /** 返回当前来源或候选的排序优先级。 */
    private static int priority(String source) {
        return switch (source) {
            case "酷我音乐" -> 0;
            case "咪咕音乐" -> 1;
            case "QQ音乐" -> 2;
            case "网易云音乐" -> 3;
            default -> 4;
        };
    }

    /** 检查歌曲的身份、歌名或歌手是否具有可匹配的有效信息。 */
    private static boolean known(String artist) {
        return artist != null && !artist.isBlank() && !artist.equals("未知歌手");
    }

    /** 规范化并分离合作歌手信息，减少歌手顺序对匹配的影响。 */
    private static Set<String> artists(String text) {
        return new TreeSet<>(Arrays.asList(normalize(text).split("[、/&,+;，；]+")));
    }

    /** 按本组件的比较规则统一文本或路径，减少大小写与标点差异。 */
    private static String normalize(String value) {
        return Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    /** 生成歌单条目或进度的简短界面标签。 */
    public static String label(OnlineTrackInfo info) {
        return info.source()
                + " · "
                + info.artist()
                + " - "
                + info.title()
                + (info.album() == null || info.album().isBlank() ? "" : " · " + info.album());
    }
}
