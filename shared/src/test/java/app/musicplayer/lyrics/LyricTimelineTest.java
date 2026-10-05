package app.musicplayer.lyrics;

import app.musicplayer.model.LyricLine;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LyricTimelineTest {
    private LyricLine line(long time) { return new LyricLine(Duration.ofMillis(time), "text"); }
    @Test void advancesAndSeeksToLastReachedDuplicateIncludingBeforeFirstLine() {
        var timeline = new LyricTimeline(List.of(line(100), line(500), line(500), line(900)));
        assertEquals(-1, timeline.advance(0)); assertEquals(0, timeline.advance(100));
        assertEquals(2, timeline.advance(500)); assertEquals(2, timeline.advance(800));
        assertEquals(3, timeline.advance(900)); assertEquals(0, timeline.advance(200));
        assertEquals(2, timeline.seek(500)); assertEquals(-1, timeline.seek(-1));
    }
    @Test void matchesReferenceAcrossForwardPlaybackAndRandomJumpsInTenThousandLines() {
        List<LyricLine> lines = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) lines.add(line(i / 2 * 400L));
        var timeline = new LyricTimeline(lines);
        for (int i = 0; i < 5_000; i++) assertEquals(i * 2 + 1, timeline.advance(i * 400L));
        var random = new Random(5);
        for (int i = 0; i < 5_000; i++) {
            long position = random.nextInt(2_000_000);
            int expected = -1;
            for (int row = 0; row < lines.size() && lines.get(row).time().toMillis() <= position; row++) expected = row;
            assertEquals(expected, timeline.advance(position)); assertEquals(expected, timeline.seek(position));
        }
    }
    @Test void capturesImmutableTimestampsAndPreservesRowsWhenUntimedTextExists() {
        var lines = new ArrayList<>(List.of(new LyricLine(null, "heading"), line(0), line(100)));
        var timeline = new LyricTimeline(lines); lines.clear();
        assertEquals(1, timeline.advance(0)); assertEquals(2, timeline.advance(100));
        assertEquals(-1, new LyricTimeline(List.of(new LyricLine(null, "plain"))).advance(500));
        assertEquals(-1, new LyricTimeline(List.of()).seek(500));
    }
    @Test void rejectsUnsortedInputAndLyricsModelOwnsItsList() {
        assertThrows(IllegalArgumentException.class, () -> new LyricTimeline(List.of(line(100), line(0))));
        var lines = new ArrayList<>(List.of(line(0)));
        var lyrics = new app.musicplayer.model.Lyrics("fixture", lines, true, "");
        lines.clear(); assertEquals(1, lyrics.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> lyrics.lines().clear());
    }
}
