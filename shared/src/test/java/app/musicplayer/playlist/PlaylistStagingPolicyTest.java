package app.musicplayer.playlist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/** 共享暂存策略保持函数式接口及原平台行为，桌面扩展不改变 Android 默认路径。 */
class PlaylistStagingPolicyTest {
    /** 原有 lambda 发布器继续使用调用方提供的暂存目录。 */
    @Test
    void legacyPublisherKeepsFallbackDirectory() {
        PlaylistDownloads.Publisher publisher = (file, entry, destination) -> destination;
        Path fallback = Path.of("private-cache", "entry-id");
        assertEquals(fallback, publisher.stagingDirectory("user-selected-folder", fallback));
    }
}
