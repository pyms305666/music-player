package app.musicplayer.online;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.musicplayer.model.OnlineTrackInfo;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * MusicCrawler
 * 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：共享在线来源协调器。并行搜索、维护来源健康和解析缓存，组织下载候选及跨来源回退，委托独立组件传输和匹配。
 */
class MusicCrawlerTest {
    /** 验证同名歌曲的不同来源仍被保留供下载换源。 */
    @Test
    void keepsSameSongFromDifferentSourcesForDownloadFallback() {
        OnlineTrackInfo kuwo = track("酷我音乐", "kuwo-id");
        OnlineTrackInfo netease = track("网易云音乐", "netease-id");

        assertEquals(List.of(kuwo, netease), MusicCrawler.deduplicate(List.of(kuwo, netease)));
    }

    /** 验证同一来源的重复歌曲结果仅保留一次。 */
    @Test
    void removesRepeatedResultFromTheSameSource() {
        OnlineTrackInfo first = track("QQ音乐", "same-id");
        OnlineTrackInfo duplicate = track("QQ音乐", "same-id");

        assertEquals(List.of(first), MusicCrawler.deduplicate(List.of(first, duplicate)));
    }

    /** 验证无点号的地址信息也可识别 FLAC。 */
    @Test
    void detectsFlacExtensionWithoutDot() {
        OnlineTrackInfo kuwo = track("酷我音乐", "228908");
        assertEquals(
                ".flac",
                MusicCrawler.guessExtension(
                        kuwo,
                        "http://kw-er.kuwo.cn/abc/6aa4f2e8/resource/30106/trackmedia/F000000bYDlc2XxKLsflac?bitrate$2000"));
        assertEquals(
                ".mp3",
                MusicCrawler.guessExtension(
                        kuwo,
                        "http://kw-er.kuwo.cn/abc/6aa4f2e8/resource/30106/trackmedia/M800000bYDlc2XxKLsmp3?bitrate$320"));
    }

    /** 验证既有按来源和地址推测扩展名的规则。 */
    @Test
    void keepsLegacyExtensionRules() {
        OnlineTrackInfo qq = track("QQ音乐", "mid");
        OnlineTrackInfo netease = track("网易云音乐", "id");
        assertEquals(
                ".m4a",
                MusicCrawler.guessExtension(
                        qq, "http://aqqmusic.tc.qq.com/amobile.music.tc.qq.com/M8000.m4a"));
        assertEquals(
                ".mp3",
                MusicCrawler.guessExtension(
                        netease, "https://music.163.com/song/media/outer/url?id=1.mp3"));
    }

    /** 按测试参数创建歌曲模型或持久化记录。 */
    private static OnlineTrackInfo track(String source, String id) {
        return new OnlineTrackInfo(source, "天黑黑", "孙燕姿", "", null, id, null);
    }
}
