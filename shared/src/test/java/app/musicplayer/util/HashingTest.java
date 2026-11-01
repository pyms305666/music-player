package app.musicplayer.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Hashing 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：共享 SHA-1 文本散列辅助。为歌词、封面等缓存生成稳定且可作为文件名的键。 */
class HashingTest {
    /** 验证缓存键稳定且适合作为文件名。 */
    @Test
    void cacheKeyIsStableAndFilenameSafe() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Hashing.sha1("abc"));
        assertTrue(Hashing.sha1("中文歌词/歌手").matches("[0-9a-f]{40}"));
        assertNotEquals(Hashing.sha1("中文"), Hashing.sha1("歌词"));
    }

    /** 验证空值和空文字形成相同缓存键。 */
    @Test
    void nullAndEmptyHaveTheSameCacheKey() {
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Hashing.sha1(null));
        assertEquals(Hashing.sha1(""), Hashing.sha1(null));
    }
}
