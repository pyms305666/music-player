package app.musicplayer.lyrics;

import app.musicplayer.model.LyricLine;
import java.util.List;

/** Immutable timestamps with a playback cursor; seek uses the last reached duplicate timestamp. */
public final class LyricTimeline {
    private static final long FORWARD_WINDOW_MILLIS = 2_000;
    private final long[] timestamps;
    private final int[] rows;
    private int cursor = -1;
    private long previous;
    private boolean initialized;

    public LyricTimeline(List<LyricLine> lines) {
        int count = (int) lines.stream().filter(LyricLine::timed).count();
        timestamps = new long[count];
        rows = new int[count];
        int offset = 0;
        for (int row = 0; row < lines.size(); row++) {
            var line = lines.get(row);
            if (!line.timed()) continue;
            long time = line.time().toMillis();
            if (offset > 0 && time < timestamps[offset - 1]) throw new IllegalArgumentException("Ordered timestamps required");
            timestamps[offset] = time;
            rows[offset++] = row;
        }
    }
    public int advance(long positionMillis) {
        if (!initialized || positionMillis < previous || positionMillis - previous > FORWARD_WINDOW_MILLIS)
            return seek(positionMillis);
        previous = positionMillis;
        while (cursor + 1 < timestamps.length && timestamps[cursor + 1] <= positionMillis) cursor++;
        return row();
    }
    public int seek(long positionMillis) {
        int low = 0, high = timestamps.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (timestamps[middle] <= positionMillis) low = middle + 1; else high = middle;
        }
        cursor = low - 1;
        previous = positionMillis;
        initialized = true;
        return row();
    }
    private int row() { return cursor < 0 ? -1 : rows[cursor]; }
}
