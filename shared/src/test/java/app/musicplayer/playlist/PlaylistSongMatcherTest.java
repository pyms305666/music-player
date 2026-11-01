package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.model.OnlineTrackInfo;

import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * PlaylistSongMatcher
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：歌单条目与在线候选的版本匹配。保留现场版等版本差异，规范化歌手顺序和标点，并按来源及匹配程度排序。
 */
class PlaylistSongMatcherTest {
    /** 按测试参数创建歌曲模型或持久化记录。 */
    private OnlineTrackInfo track(String source, String title, String artist, String id) {
        return new OnlineTrackInfo(source, title, artist, "", "", id, "");
    }

    /** 验证全角标点、空白和歌手顺序可规范化，而歌曲版本保持区分。 */
    @Test
    void acceptsFullWidthPunctuationSpacingAndArtistOrderButPreservesVersion() {
        var expected = track("酷狗音乐", "歌曲 （Live）", "甲、乙", "kg");
        assertTrue(PlaylistSongMatcher.matches(expected, track("酷我音乐", "歌曲(Live)", "乙&甲", "kw")));
        assertFalse(PlaylistSongMatcher.matches(expected, track("咪咕音乐", "歌曲", "甲、乙", "mg")));
        assertFalse(PlaylistSongMatcher.matches(expected, track("咪咕音乐", "歌曲(Live)", "甲", "mg")));
        assertFalse(PlaylistSongMatcher.matches(expected, track("咪咕音乐", "歌曲(Remix)", "甲、乙", "mg")));
    }

    /** 验证未知歌手需要手选，来源身份一致时可确认匹配。 */
    @Test
    void unknownArtistRequiresManualChoiceUnlessSourceIdentityMatches() {
        var expected = track("网易云音乐", "歌曲", "未知歌手", "1");
        assertFalse(PlaylistSongMatcher.matches(expected, track("酷我音乐", "歌曲", "歌手", "kw")));
        assertTrue(PlaylistSongMatcher.matches(expected, track("网易云音乐", "歌曲", "歌手", "1")));
    }

    /** 验证翻唱署名仅在同歌手及明确专辑证据下调和。 */
    @Test
    void reconcilesCoverCreditOnlyForTheSamePerformerAndExplicitAlbum() {
        var expected = new OnlineTrackInfo("酷狗音乐", "爱最闪耀", "何锘情", "梦", "", "kg", "");
        assertTrue(
                PlaylistSongMatcher.matches(
                        expected,
                        new OnlineTrackInfo(
                                "酷我音乐", "爱最闪耀 (cover: 许馨文)", "何锘情", "梦", "", "kw", "")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        expected,
                        new OnlineTrackInfo(
                                "酷我音乐", "爱最闪耀 (cover: 许馨文)", "何锘情", "其他专辑", "", "kw", "")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        expected, new OnlineTrackInfo("酷我音乐", "爱最闪耀", "许馨文", "梦", "", "kw", "")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        expected, track("酷我音乐", "爱最闪耀 (cover: 许馨文)", "何锘情", "kw")));
    }

    /** 验证专辑中的现场标记可调和，但不接受其他演唱会或录音室版本。 */
    @Test
    void reconcilesLiveAnnotationInAlbumWithoutAcceptingOtherConcertsOrStudioVersions() {
        var expected = new OnlineTrackInfo("酷狗音乐", "平凡之路 (Live)", "朴树", "朴树 猎户星座·专场", "", "kg", "");
        assertTrue(
                PlaylistSongMatcher.matches(
                        expected,
                        new OnlineTrackInfo("酷我音乐", "平凡之路", "朴树", "朴树 猎户星座·专场", "", "kw", "")));
        assertTrue(
                PlaylistSongMatcher.matches(
                        expected,
                        new OnlineTrackInfo(
                                "咪咕音乐", "平凡之路（Live版）", "朴树", "朴树 猎户星座·专场", "", "mg", "")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        expected,
                        new OnlineTrackInfo("酷我音乐", "平凡之路 (Live)", "朴树", "其他演唱会", "", "kw", "")));
        assertFalse(PlaylistSongMatcher.matches(expected, track("酷我音乐", "平凡之路", "朴树", "kw")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        expected,
                        new OnlineTrackInfo(
                                "酷我音乐", "平凡之路 (Remix)", "朴树", "朴树 猎户星座·专场", "", "kw", "")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        expected,
                        new OnlineTrackInfo(
                                "酷我音乐", "平凡之路 (cover: 甲)", "朴树", "朴树 猎户星座·专场", "", "kw", "")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        track("酷狗音乐", "海阔天空 (3D环绕版)", "BEYOND", "kg"),
                        track("酷我音乐", "海阔天空", "BEYOND", "kw")));
        assertFalse(
                PlaylistSongMatcher.matches(
                        track("酷狗音乐", "消愁 (2018王者荣耀三周年音乐盛典现场)", "毛不易", "kg"),
                        track("酷我音乐", "消愁 (Live)", "毛不易", "kw")));
    }

    /** 验证手选不同版本不作为原曲的自动匹配证据。 */
    @Test
    void manualDifferentVersionDoesNotBecomeProofForOriginalSong() {
        var original = track("QQ音乐", "歌曲", "歌手", "qq");
        var live = track("酷我音乐", "歌曲 (Live)", "歌手", "kw");
        var entry = NamedPlaylist.Entry.create(original, 0).downloadFrom(live).local("file");
        var matcher = new PlaylistDuplicates(PlaylistDuplicates.associations(entry));
        assertTrue(matcher.match(List.of(NamedPlaylist.Entry.create(original, 0))).isEmpty());
        assertTrue(matcher.match(List.of(NamedPlaylist.Entry.create(live, 0))).get(0).confirmed());
    }
}
