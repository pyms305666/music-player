package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 换源时比较歌名、歌手及来源身份，过滤片段、伴奏等不适合替代的结果。 */
final class FallbackTrackMatcher {
    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    private FallbackTrackMatcher() {}

    /** 比较来源及主标识，判断是否是同一在线歌曲。 */
    static boolean sameOnlineTrack(OnlineTrackInfo first, OnlineTrackInfo second) {
        return Objects.equals(first.source(), second.source())
                && Objects.equals(first.primaryId(), second.primaryId());
    }

    /** 检查歌名与歌手是否足以支持换源，排除不适合替代的版本。 */
    static boolean strongFallbackMatch(OnlineTrackInfo expected, OnlineTrackInfo candidate) {
        if (candidate == null
                || isBadFallbackText(candidate.title())
                || isBadFallbackText(candidate.artist())) {
            return false;
        }
        String expectedTitle = normalizeTitle(expected.title());
        String candidateTitle = normalizeTitle(candidate.title());
        if (expectedTitle.isBlank() || candidateTitle.isBlank()) {
            return false;
        }

        boolean exactTitle = expectedTitle.equals(candidateTitle);
        boolean relatedTitle =
                expectedTitle.contains(candidateTitle) || candidateTitle.contains(expectedTitle);
        if (!exactTitle && !relatedTitle) {
            return false;
        }

        String expectedArtist = normalizeArtist(expected.artist());
        String candidateArtist = normalizeArtist(candidate.artist());
        boolean artistMatches =
                !expectedArtist.isBlank()
                        && !candidateArtist.isBlank()
                        && (expectedArtist.equals(candidateArtist)
                                || expectedArtist.contains(candidateArtist)
                                || candidateArtist.contains(expectedArtist));
        return exactTitle || artistMatches;
    }

    /** 识别片段、伴奏、童谣等需要排除的候选名称或歌手文本。 */
    private static boolean isBadFallbackText(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String term :
                List.of("MusicPart", "儿歌", "童谣", "预告", "片段", "串烧", "铃声", "故事", "伴奏", "广播剧")) {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** 规范化歌手名称，并把未知歌手视为缺失信息。 */
    private static String normalizeArtist(String value) {
        String normalized = normalizeTitle(value);
        return normalized.equals(normalizeTitle("未知歌手")) ? "" : normalized;
    }

    /** 移除括号内容与无关标点，保留用于匹配的中文、字母及数字。 */
    private static String normalizeTitle(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int asciiParentheses = 0;
        int fullWidthParentheses = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if (character == '(') {
                asciiParentheses++;
                continue;
            }
            if (character == ')' && asciiParentheses > 0) {
                asciiParentheses--;
                continue;
            }
            if (character == '（') {
                fullWidthParentheses++;
                continue;
            }
            if (character == '）' && fullWidthParentheses > 0) {
                fullWidthParentheses--;
                continue;
            }
            if (asciiParentheses == 0
                    && fullWidthParentheses == 0
                    && (isChinese(character)
                            || character >= 'a' && character <= 'z'
                            || character >= '0' && character <= '9')) {
                result.append(character);
            }
        }
        return result.toString();
    }

    /** 检查字符是否位于此匹配规则使用的常见汉字范围。 */
    private static boolean isChinese(char character) {
        return character >= '\u4e00' && character <= '\u9fff';
    }
}
