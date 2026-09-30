package app.musicplayer.lyrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LrcParserTest {
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

    @Test
    void ignoresMetadataWhenParsingPlainLyrics() {
        var lyrics = LrcParser.parse("test", "[TI:标题]\n[ar:歌手]\n第一行\n第二行");

        assertFalse(lyrics.timed());
        assertEquals(2, lyrics.lines().size());
        assertEquals("第一行", lyrics.lines().get(0).text());
    }
}
