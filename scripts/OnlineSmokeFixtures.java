package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线烟雾测试的可控数据夹具。模拟来源、封面、歌词和传输响应，使桌面 UI 检查可重复执行。 Deterministic UI fixtures, compiled only by the
 * smoke harness.
 */
public final class OnlineSmokeFixtures implements AutoCloseable {
    public final CountDownLatch releaseSource = new CountDownLatch(1);
    public final CountDownLatch transferStarted = new CountDownLatch(1);
    public final CountDownLatch releaseTransfer = new CountDownLatch(1);
    public final AtomicInteger transfers = new AtomicInteger();
    public final AtomicInteger sourceCalls = new AtomicInteger();
    public final OnlineMusicSearchService service;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public OnlineSmokeFixtures() {
        var fast = provider("fast", false);
        var slow = provider("slow", true);
        service =
                new OnlineMusicSearchService(
                        new MusicCrawler(List.of(slow, fast), 5000),
                        System::nanoTime,
                        (track, directory, cancellation, progress) -> {
                            transfers.incrementAndGet();
                            Files.createDirectories(directory);
                            Path temporary =
                                    Files.createTempFile(directory, "ui-fixture-", ".part");
                            try {
                                Files.write(temporary, new byte[2048]);
                                progress.accept(
                                        new DownloadEvent(
                                                DownloadEvent.Stage.TRANSFERRING,
                                                2048,
                                                OptionalLong.empty()));
                                transferStarted.countDown();
                                try (var detach =
                                        cancellation.onCancel(releaseTransfer::countDown)) {
                                    if (!releaseTransfer.await(5, TimeUnit.SECONDS))
                                        throw new IllegalStateException("Fixture was not released");
                                    cancellation.check();
                                    Path target = directory.resolve(track.primaryId() + ".wav");
                                    return Files.move(temporary, target);
                                }
                            } finally {
                                Files.deleteIfExists(temporary);
                            }
                        });
    }

    /** 创建可控来源或发布夹具，使回归不依赖真实网站和用户文件。 */
    private OnlineSourceProvider provider(String name, boolean slow) {
        return new OnlineSourceProvider() {
            /** 返回来源的稳定名称，用于结果标记、去重与渠道选择。 */
            public String sourceName() {
                return name;
            }

            /** 取得来源要求的 Referer 请求头，供 HTTP 请求使用。 */
            public String referer() {
                return "http://localhost/";
            }

            /** 按查询文字搜索歌曲，返回本来源或当前搜索组件的结果。 */
            public List<OnlineTrackInfo> search(String query) {
                sourceCalls.incrementAndGet();
                if (slow)
                    try {
                        if (!releaseSource.await(5, TimeUnit.SECONDS))
                            throw new IllegalStateException("Source was not released");
                    } catch (InterruptedException cancelled) {
                        Thread.currentThread().interrupt();
                    }
                return slow ? List.of(track(name)) : List.of(track(name), track(name + "2"));
            }

            /** 准备或解析可实际使用的地址、路径或运行配置。 */
            public String resolve(OnlineTrackInfo track) {
                return null;
            }
        };
    }

    /** 按测试参数创建歌曲模型或持久化记录。 */
    private static OnlineTrackInfo track(String name) {
        return new OnlineTrackInfo(name, "song " + name, "artist", "", null, name, null);
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        releaseSource.countDown();
        releaseTransfer.countDown();
        service.close();
    }
}
