package app.musicplayer.util;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HashingTest {
    @Test void cacheKeyIsStableAndFilenameSafe() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Hashing.sha1("abc"));
        assertTrue(Hashing.sha1("中文歌词/歌手").matches("[0-9a-f]{40}"));
        assertNotEquals(Hashing.sha1("中文"), Hashing.sha1("歌词"));
    }
    @Test void nullAndEmptyHaveTheSameCacheKey() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Hashing.sha1(null));
        assertEquals(Hashing.sha1(""), Hashing.sha1(null));
    }
}
