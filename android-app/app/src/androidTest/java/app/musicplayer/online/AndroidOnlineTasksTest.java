package app.musicplayer.online;

import static org.junit.Assert.*;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import app.musicplayer.android.R;
import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.android.ui.AndroidOnlineTasks;
import app.musicplayer.android.ui.OnlineTrackAdapter;
import app.musicplayer.model.OnlineTrackInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 验证 Android 在线列表选择、搜索状态、下载发布及离开页面后的清理。 Offline controls and private temporary files only; never
 * opens the personal database.
 */
@RunWith(AndroidJUnit4.class)
public class AndroidOnlineTasksTest {
    /** 将指定测试操作投递到 Android 主线程并等待结果，避免在测试线程直接访问控件。 */
    private void main(Runnable work) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(work);
    }

    /** 等待测试条件满足；到达期限仍未满足时报告失败。 */
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var ready = new java.util.concurrent.atomic.AtomicBoolean();
            main(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(20);
        }
        fail("UI condition did not become true");
    }

    /** 验证最低支持 API 上发布拒绝后清理暂存并报告失败。 */
    @Test
    public void rejectedPublicationCleansStagingAndReportsFailureOnMinimumSupportedApi()
            throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path directory =
                Files.createTempDirectory(context.getCacheDir().toPath(), "online-rejection-test-");
        Path staging = directory.resolve("owned.mp3");
        var controller = new AtomicReference<DownloadController<String>>();
        var notices = new AtomicReference<DownloadController.Notice>();
        var track = new OnlineTrackInfo("source", "fixture", "artist", "", null, "owned", null);
        try (var service =
                new OnlineMusicSearchService(
                        new MusicCrawler(List.of(), 1000),
                        System::nanoTime,
                        (info, target, token, progress) -> Files.write(staging, new byte[32]))) {
            main(
                    () -> {
                        var handler = new Handler(Looper.getMainLooper());
                        controller.set(
                                new DownloadController<>(
                                        service,
                                        command -> handler.post(command),
                                        notices::set,
                                        path -> {
                                            try {
                                                Files.deleteIfExists(path);
                                            } catch (java.io.IOException error) {
                                                throw new java.io.UncheckedIOException(error);
                                            }
                                        }));
                        controller
                                .get()
                                .start(
                                        track,
                                        directory,
                                        path -> {
                                            throw new java.util.concurrent
                                                    .RejectedExecutionException(
                                                    "Fixture rejection");
                                        },
                                        ignored -> fail("Rejected publication completed"));
                    });
            await(() -> controller.get().retry(track));
            assertFalse(Files.exists(staging));
            assertEquals(DownloadEvent.Stage.FAILED, notices.get().event().stage());
            assertTrue(
                    notices.get().error()
                            instanceof java.util.concurrent.RejectedExecutionException);
        } finally {
            if (controller.get() != null) main(() -> controller.get().close());
            Files.deleteIfExists(staging);
            Files.deleteIfExists(directory);
        }
    }

    /** 验证已有歌单控件处理分批、选择、进度、取消和重试。 */
    @Test
    public void batchesSelectionProgressCancellationAndRetryUseExistingControls() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Path directory =
                Files.createTempDirectory(context.getCacheDir().toPath(), "online-controls-test-");
        var sourceRelease = new CountDownLatch(1);
        var transferRelease = new CountDownLatch(1);
        var transferStarted = new CountDownLatch(1);
        var sourceCalls = new AtomicInteger();
        var published = new AtomicInteger();
        var first = new OnlineTrackInfo("fast", "夜曲", "歌手", "", null, "first", null);
        var second = new OnlineTrackInfo("slow", "song", "artist", "", null, "second", null);
        var fast =
                provider(
                        "fast",
                        () -> {
                            sourceCalls.incrementAndGet();
                            return List.of(first);
                        });
        var slow =
                provider(
                        "slow",
                        () -> {
                            sourceCalls.incrementAndGet();
                            try {
                                if (!sourceRelease.await(5, TimeUnit.SECONDS))
                                    throw new IllegalStateException("Source gate");
                            } catch (InterruptedException stopped) {
                                Thread.currentThread().interrupt();
                            }
                            return List.of(second);
                        });
        var tasks = new AtomicReference<AndroidOnlineTasks>();
        var adapter = new AtomicReference<OnlineTrackAdapter>();
        var recycler = new AtomicReference<RecyclerView>();
        var query = new AtomicReference<EditText>();
        var search = new AtomicReference<ImageButton>();
        var download = new AtomicReference<Button>();
        var status = new AtomicReference<String>("");
        try (var service =
                new OnlineMusicSearchService(
                        new MusicCrawler(List.of(slow, fast), 5000),
                        System::nanoTime,
                        (track, target, token, progress) -> {
                            Path temporary = Files.createTempFile(target, "owned-", ".part");
                            try {
                                Files.write(temporary, new byte[2048]);
                                progress.accept(
                                        new DownloadEvent(
                                                DownloadEvent.Stage.TRANSFERRING,
                                                2048,
                                                OptionalLong.empty()));
                                transferStarted.countDown();
                                try (var detached = token.onCancel(transferRelease::countDown)) {
                                    if (!transferRelease.await(5, TimeUnit.SECONDS))
                                        throw new IllegalStateException("Transfer gate");
                                    token.check();
                                    return Files.move(temporary, target.resolve("fixture.wav"));
                                }
                            } finally {
                                Files.deleteIfExists(temporary);
                            }
                        })) {
            main(
                    () -> {
                        var themed =
                                new androidx.appcompat.view.ContextThemeWrapper(
                                        context, R.style.Theme_SimpleMusicPlayer);
                        View layout =
                                LayoutInflater.from(themed).inflate(R.layout.activity_main, null);
                        query.set(layout.findViewById(R.id.onlineSearch));
                        query.get().setText("\u00a0 ARTIST　夜曲\u0085 ");
                        assertEquals(
                                "artist 夜曲",
                                SearchResultCache.key(query.get().getText().toString()));
                        search.set(layout.findViewById(R.id.onlineSearchButton));
                        download.set(layout.findViewById(R.id.downloadButton));
                        adapter.set(new OnlineTrackAdapter(info -> tasks.get().selectionChanged()));
                        recycler.set(layout.findViewById(R.id.onlineResults));
                        recycler.get().setLayoutManager(new LinearLayoutManager(context));
                        recycler.get().setItemAnimator(null);
                        recycler.get().setAdapter(adapter.get());
                        tasks.set(
                                new AndroidOnlineTasks(
                                        service,
                                        command ->
                                                new Handler(Looper.getMainLooper()).post(command),
                                        new AndroidOnlineTasks.Controls(
                                                query.get(),
                                                search.get(),
                                                download.get(),
                                                adapter.get()),
                                        status::set,
                                        () -> {}));
                        tasks.get().search();
                    });
            await(() -> adapter.get().getItemCount() == 1);
            main(
                    () -> {
                        recycler.get()
                                .measure(
                                        View.MeasureSpec.makeMeasureSpec(
                                                1080, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(
                                                1000, View.MeasureSpec.EXACTLY));
                        recycler.get().layout(0, 0, 1080, 1000);
                        recycler.get().findViewHolderForAdapterPosition(0).itemView.performClick();
                        assertEquals(first.identity(), adapter.get().selected().identity());
                        assertEquals("取消搜索", search.get().getContentDescription().toString());
                    });
            sourceRelease.countDown();
            await(
                    () ->
                            adapter.get().getItemCount() == 2
                                    && search.get()
                                            .getContentDescription()
                                            .toString()
                                            .equals("搜索"));
            main(
                    () -> {
                        assertEquals(first.identity(), adapter.get().selected().identity());
                        tasks.get()
                                .download(
                                        first,
                                        directory,
                                        path -> {
                                            published.incrementAndGet();
                                            return CompletableFuture.completedFuture(
                                                    (TrackEntry) null);
                                        },
                                        ignored -> {});
                    });
            assertTrue(transferStarted.await(5, TimeUnit.SECONDS));
            await(() -> status.get().contains("2.0 KiB"));
            main(
                    () -> {
                        assertFalse(status.get().contains("%"));
                        assertEquals("取消下载", download.get().getText().toString());
                        assertTrue(tasks.get().cancelSelectedDownload());
                    });
            await(() -> status.get().contains("已取消下载"));
            assertEquals(0, published.get());
            main(
                    () ->
                            tasks.get()
                                    .download(
                                            first,
                                            directory,
                                            path -> {
                                                published.incrementAndGet();
                                                return CompletableFuture.completedFuture(
                                                        (TrackEntry) null);
                                            },
                                            ignored -> {}));
            await(() -> status.get().contains("下载完成"));
            assertEquals(1, published.get());
            main(() -> tasks.get().search());
            await(() -> status.get().contains("缓存"));
            assertEquals(2, sourceCalls.get());
            main(
                    () -> {
                        query.get().setText("");
                        tasks.get().search();
                    });
            await(() -> adapter.get().getItemCount() == 0 && status.get().contains("没有找到"));
            try (var files = Files.list(directory)) {
                assertFalse(files.anyMatch(path -> path.toString().endsWith(".part")));
            }
        } finally {
            sourceRelease.countDown();
            transferRelease.countDown();
            if (tasks.get() != null) main(() -> tasks.get().close());
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
            Files.deleteIfExists(directory);
        }
    }

    /** 创建可控来源或发布夹具，使回归不依赖真实网站和用户文件。 */
    private OnlineSourceProvider provider(
            String name, java.util.function.Supplier<List<OnlineTrackInfo>> results) {
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
                return results.get();
            }

            /** 准备或解析可实际使用的地址、路径或运行配置。 */
            public String resolve(OnlineTrackInfo track) {
                return null;
            }
        };
    }
}
