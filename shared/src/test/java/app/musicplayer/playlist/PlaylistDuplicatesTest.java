package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.*;

import app.musicplayer.model.OnlineTrackInfo;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * PlaylistDuplicates
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：歌单下载前的本地重复检查。将曲库和已有歌单关联为候选，按位置及名称规则生成重复匹配结果。
 */
class PlaylistDuplicatesTest {
    /** 编码歌单头或条目，或构造当前平台的歌曲记录。 */
    private NamedPlaylist.Entry entry(String id, String title, String artist) {
        return NamedPlaylist.Entry.create(
                new OnlineTrackInfo("QQ音乐", title, artist, "", "", id, ""), 0);
    }

    /** 验证稳定身份可确认重复，元数据相似仍需选择，版本保持独立。 */
    @Test
    void identitiesConfirmButMetadataRequiresUserAndVersionsRemainDistinct() {
        var matcher =
                new PlaylistDuplicates(
                        List.of(
                                new PlaylistDuplicates.Local("/a.mp3", "同名", "甲", "QQ音乐|1"),
                                new PlaylistDuplicates.Local("/b.mp3", "同名", "乙", ""),
                                new PlaylistDuplicates.Local("/c.mp3", "原曲 (Live)", "甲", ""),
                                new PlaylistDuplicates.Local("/d.mp3", "Ａ B", "甲", "")));
        var result =
                matcher.match(
                        List.of(
                                entry("1", "同名", "甲"),
                                entry("2", "同名", "乙"),
                                entry("3", "原曲", "甲"),
                                entry("4", "a   b", "甲"),
                                entry("5", "同名", "未知歌手")));
        assertEquals(3, result.size());
        assertTrue(result.get(0).confirmed());
        assertFalse(result.get(1).confirmed());
        assertFalse(result.get(2).confirmed());
        assertEquals("/b.mp3", result.get(1).candidates().get(0).location());
    }

    /** 验证歌单编解码保留引号、换行和条目状态。 */
    @Test
    void codecRoundTripsQuotesNewlinesAndStates() {
        var e =
                entry("1", "歌\"名\n (Live)", "歌手")
                        .local("content://media/a")
                        .status(NamedPlaylist.State.FAILED, "权限\n已撤销");
        assertEquals(e, PlaylistCodec.entry(PlaylistCodec.entry(e)));
        var p =
                new NamedPlaylist(
                        "p",
                        "歌单",
                        "QQ音乐",
                        "1",
                        "https://y.qq.com/n/ryqq/playlist/1",
                        "",
                        "我",
                        1,
                        "MEDIA_STORE",
                        true,
                        List.of(e));
        assertEquals(p, PlaylistCodec.header(PlaylistCodec.header(p), p.entries()));
    }

    /** 验证旧条目保留自动模式，自动渠道可在重试时重新匹配。 */
    @Test
    void legacyEntriesRemainAutomaticAndAutomaticChannelsCanBeMatchedAgain() {
        var e =
                entry("1", "原曲", "歌手")
                        .downloadFrom(new OnlineTrackInfo("酷我音乐", "原曲", "歌手", "", "", "kw", ""));
        var legacy = PlaylistCodec.entry(e).replace(",\"userSelectedVersion\":false", "");
        var restored = PlaylistCodec.entry(legacy);
        assertFalse(restored.userSelectedVersion());
        assertNull(restored.prepareDownload().downloadTrack());
    }

    /** 验证手选版本只检查和复用所选录音。 */
    @Test
    void manualVersionChecksAndReusesOnlyTheChosenRecording() {
        var original = entry("1", "歌曲", "歌手");
        var chosen = new OnlineTrackInfo("酷我音乐", "歌曲 (Live)", "歌手", "", "", "live", "");
        var requested = original.selectVersion(chosen);
        var originalOnly =
                new PlaylistDuplicates(
                        List.of(
                                new PlaylistDuplicates.Local(
                                        "original.mp3", "歌曲", "歌手", original.track().identity())));
        assertTrue(originalOnly.match(List.of(requested)).isEmpty());
        var matcher =
                new PlaylistDuplicates(
                        List.of(
                                new PlaylistDuplicates.Local(
                                        "live.mp3",
                                        chosen.title(),
                                        chosen.artist(),
                                        chosen.identity())));
        var match = matcher.match(List.of(requested)).get(0);
        assertTrue(match.confirmed());
        var reused = requested.reuseLocal(match.candidates().get(0).location());
        assertTrue(reused.userSelectedVersion());
        assertEquals(chosen, reused.downloadTrack());
        assertEquals("live.mp3", reused.location());
        assertEquals(reused, PlaylistCodec.entry(PlaylistCodec.entry(reused)));
        assertTrue(
                new PlaylistDuplicates(PlaylistDuplicates.associations(reused))
                        .match(List.of(original))
                        .isEmpty());
    }
}
