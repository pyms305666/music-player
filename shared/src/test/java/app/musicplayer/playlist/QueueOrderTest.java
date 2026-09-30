package app.musicplayer.playlist;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueueOrderTest {
    @Test void wrapsInBothDirectionsAndStartsCorrectlyWithoutSelection() {
        assertEquals(0, QueueOrder.relative(2, 1, 3));
        assertEquals(2, QueueOrder.relative(0, -1, 3));
        assertEquals(0, QueueOrder.relative(-1, 1, 3));
        assertEquals(2, QueueOrder.relative(-1, -1, 3));
        assertEquals(0, QueueOrder.relative(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> QueueOrder.relative(-1, 1, 0));
    }
}
