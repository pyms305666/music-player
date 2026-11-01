package app.musicplayer.online;

import app.musicplayer.model.OnlineTrackInfo;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 在线搜索协调性能基准。通过可控来源测量搜索预算、慢来源及增量返回行为，输出可比较的结果。 Disposable provider fixtures: measures backend
 * delivery without public-network noise.
 */
public final class OnlineSearchBenchmark {
    private static volatile Object sink;

    /** 创建可控来源或发布夹具，使回归不依赖真实网站和用户文件。 */
    private static OnlineSourceProvider provider(String name, int delay) {
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
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(new OnlineTrackInfo(name, query, "artist", "", null, "id", null));
            }

            /** 准备或解析可实际使用的地址、路径或运行配置。 */
            public String resolve(OnlineTrackInfo track) {
                return null;
            }
        };
    }

    /** 命令行或应用启动入口，准备运行环境并启动本文件的主流程。 */
    public static void main(String[] args) throws Exception {
        boolean incremental = args[0].equals("incremental");
        boolean cached = args[0].equals("cached");
        var memory = ManagementFactory.getMemoryMXBean();
        var pools =
                ManagementFactory.getMemoryPoolMXBeans().stream()
                        .filter(p -> p.getType() == java.lang.management.MemoryType.HEAP)
                        .toList();
        for (int batch = 1; batch <= 3; batch++) {
            try (var crawler =
                            new MusicCrawler(
                                    List.of(provider("fast", 25), provider("slow", 200)), 1000);
                    var service =
                            cached
                                    ? new OnlineMusicSearchService(crawler, System::nanoTime)
                                    : null) {
                if (cached) service.search("song", Runnable::run, ignored -> {}).result().get();
                else crawler.search("warm");
                System.gc();
                pools.forEach(p -> p.resetPeakUsage());
                double firstSum = 0, totalSum = 0;
                for (int i = 0; i < 5; i++) {
                    long start = System.nanoTime();
                    var first = new AtomicLong();
                    if (cached)
                        sink = service.search("song", Runnable::run, ignored -> {}).result().get();
                    else if (incremental) {
                        Consumer<Object> progress =
                                snapshot -> {
                                    try {
                                        List<?> tracks =
                                                (List<?>)
                                                        snapshot.getClass()
                                                                .getMethod("tracks")
                                                                .invoke(snapshot);
                                        if (!tracks.isEmpty())
                                            first.compareAndSet(0, System.nanoTime() - start);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                };
                        sink =
                                crawler.getClass()
                                        .getMethod(
                                                "searchIncrementally", String.class, Consumer.class)
                                        .invoke(crawler, "song", progress);
                    } else sink = crawler.search("song");
                    long elapsed = System.nanoTime() - start;
                    firstSum += (first.get() == 0 ? elapsed : first.get()) / 1e6;
                    totalSum += elapsed / 1e6;
                }
                long peak = pools.stream().mapToLong(p -> p.getPeakUsage().getUsed()).sum();
                System.gc();
                System.out.printf(
                        Locale.ROOT,
                        "mode=%s batch=%d firstResultMs=%.3f totalMs=%.3f peakHeapBytes=%d"
                                + " stableHeapBytes=%d%n",
                        args[0],
                        batch,
                        firstSum / 5,
                        totalSum / 5,
                        peak,
                        memory.getHeapMemoryUsage().getUsed());
            }
        }
    }
}
