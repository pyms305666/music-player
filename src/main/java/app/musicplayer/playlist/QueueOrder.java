package app.musicplayer.playlist;

/** Shared circular queue boundaries; filtering the UI never changes this queue. */
public final class QueueOrder {
    private QueueOrder() { }

    public static int relative(int current, int direction, int count) {
        if (count <= 0) throw new IllegalArgumentException("Queue is empty");
        if (current < 0) return direction < 0 ? count - 1 : 0;
        return Math.floorMod(current + direction, count);
    }
}
