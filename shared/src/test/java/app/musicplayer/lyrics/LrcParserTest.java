package app.musicplayer.lyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** LrcParser 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：LRC 歌词解析器。处理时间标签、偏移、同一行多个时间点及无时间文本，输出共享歌词模型。 */
class LrcParserTest {
    /** 验证带时间的歌词解析与排序。 */
    @Test
    void parsesAndSortsTimedLyrics() {
        var lyrics = LrcParser.parse("test", "[00:02.50]第二行\n[00:01.5][00:03.005]第一行");

        assertTrue(lyrics.timed());
        assertEquals(3, lyrics.lines().size());
        assertEquals(1_500, lyrics.lines().get(0).time().toMillis());
        assertEquals("第一行", lyrics.lines().get(0).text());
        assertEquals(2_500, lyrics.lines().get(1).time().toMillis());
        assertEquals(3_005, lyrics.lines().get(2).time().toMillis());
    }

    /** 验证纯文本歌词解析不把元数据标签作为歌词行。 */
    @Test
    void ignoresMetadataWhenParsingPlainLyrics() {
        var lyrics = LrcParser.parse("test", "[TI:标题]\n[ar:歌手]\n第一行\n第二行");

        assertFalse(lyrics.timed());
        assertEquals(2, lyrics.lines().size());
        assertEquals("第一行", lyrics.lines().get(0).text());
    }
}
