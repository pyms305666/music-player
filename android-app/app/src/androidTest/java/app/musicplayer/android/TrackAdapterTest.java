package app.musicplayer.android;

import static org.junit.Assert.*;

import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import app.musicplayer.android.data.TrackEntry;
import app.musicplayer.android.ui.TrackAdapter;
import app.musicplayer.android.ui.TrackRow;
import app.musicplayer.model.Track;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TrackAdapter 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：Android 本地歌曲 RecyclerView 适配器。基于 TrackRow
 * 的不可变内容比较更新列表，保存选择并分发点击与长按操作。 Adapter-only fixtures: no files or personal database rows are
 * created.
 */
@RunWith(AndroidJUnit4.class)
public class TrackAdapterTest {
    /** 将指定测试操作投递到 Android 主线程并等待结果，避免在测试线程直接访问控件。 */
    private void main(Runnable work) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(work);
    }

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    private void submit(TrackAdapter adapter, List<TrackRow> rows) throws Exception {
        var complete = new CompletableFuture<Void>();
        main(() -> adapter.submit(rows, () -> complete.complete(null)));
        complete.get(5, TimeUnit.SECONDS);
    }

    /** 给测试控件指定可用尺寸并触发布局。 */
    private void layout(RecyclerView view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 1000);
    }

    /** 验证前方行变化后的差异更新保留可见行及滚动偏移。 */
    @Test
    public void diffPreservesVisibleRowAndOffsetWhenEarlierRowsChange() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var adapter = new AtomicReference<TrackAdapter>();
        var recycler = new AtomicReference<RecyclerView>();
        var manager = new AtomicReference<LinearLayoutManager>();
        main(
                () -> {
                    adapter.set(new TrackAdapter(entry -> {}));
                    recycler.set(new RecyclerView(context));
                    manager.set(new LinearLayoutManager(context));
                    recycler.get().setLayoutManager(manager.get());
                    recycler.get().setItemAnimator(null);
                    recycler.get().setAdapter(adapter.get());
                });
        List<TrackRow> rows =
                java.util.stream.IntStream.range(0, 100)
                        .mapToObj(
                                index ->
                                        TrackRow.of(
                                                new TrackEntry(
                                                        new Track(
                                                                Paths.get(
                                                                        "/fixture/scroll-"
                                                                                + index
                                                                                + ".mp3")),
                                                        index)))
                        .toList();
        submit(adapter.get(), rows);
        var anchorKey = new AtomicReference<String>();
        var anchorTop = new java.util.concurrent.atomic.AtomicInteger();
        main(
                () -> {
                    manager.get().scrollToPositionWithOffset(50, -20);
                    layout(recycler.get());
                    int position = manager.get().findFirstVisibleItemPosition();
                    anchorKey.set(adapter.get().getCurrentList().get(position).key());
                    anchorTop.set(manager.get().findViewByPosition(position).getTop());
                });
        var inserted = new java.util.ArrayList<TrackRow>();
        inserted.add(
                TrackRow.of(new TrackEntry(new Track(Paths.get("/fixture/inserted.mp3")), -1)));
        inserted.addAll(rows.subList(10, rows.size()));
        submit(adapter.get(), inserted);
        main(
                () -> {
                    layout(recycler.get());
                    int position = manager.get().findFirstVisibleItemPosition();
                    assertEquals(
                            anchorKey.get(), adapter.get().getCurrentList().get(position).key());
                    assertEquals(
                            anchorTop.get(), manager.get().findViewByPosition(position).getTop());
                });
    }

    /** 验证差异更新、排序、筛选和元数据变化后保留有效选择。 */
    @Test
    public void selectionSurvivesDiffSortFilterAndMutableMetadataChanges() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var adapter = new AtomicReference<TrackAdapter>();
        var recycler = new AtomicReference<RecyclerView>();
        var selected = new AtomicReference<TrackEntry>();
        var clickCount = new java.util.concurrent.atomic.AtomicInteger();
        var clickedView = new AtomicReference<View>();
        main(
                () -> {
                    adapter.set(
                            new TrackAdapter(
                                    entry -> {
                                        selected.set(entry);
                                        clickCount.incrementAndGet();
                                    }));
                    RecyclerView view = new RecyclerView(context);
                    view.setLayoutManager(new LinearLayoutManager(context));
                    view.setAdapter(adapter.get());
                    recycler.set(view);
                });
        TrackEntry first = new TrackEntry(new Track(Paths.get("/fixture/Artist - 夜曲.mp3")), 1);
        TrackEntry second = new TrackEntry(new Track(Paths.get("/fixture/Another.mp3")), 2);
        TrackRow captured = TrackRow.of(first);
        submit(adapter.get(), List.of(captured, TrackRow.of(second)));
        main(
                () -> {
                    int width = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY);
                    int height = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY);
                    recycler.get().measure(width, height);
                    recycler.get().layout(0, 0, 1080, 1000);
                    clickedView.set(recycler.get().findViewHolderForAdapterPosition(0).itemView);
                    assertTrue(clickedView.get().performClick());
                    assertEquals(first.key(), adapter.get().selected().key());
                });
        assertEquals(first, selected.get());
        first.track().updateMetadata("新标题", "新歌手");
        assertEquals("夜曲", captured.title());
        submit(adapter.get(), List.of(TrackRow.of(second), TrackRow.of(first)));
        main(
                () -> {
                    assertEquals(first.key(), adapter.get().selected().key());
                    assertEquals("新标题", adapter.get().getCurrentList().get(1).title());
                    recycler.get()
                            .measure(
                                    View.MeasureSpec.makeMeasureSpec(
                                            1080, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(
                                            1000, View.MeasureSpec.EXACTLY));
                    recycler.get().layout(0, 0, 1080, 1000);
                    android.widget.TextView title =
                            recycler.get()
                                    .findViewHolderForAdapterPosition(1)
                                    .itemView
                                    .findViewById(R.id.itemTitle);
                    assertEquals("新标题", title.getText().toString());
                });
        submit(adapter.get(), List.of(TrackRow.of(second)));
        main(
                () -> {
                    assertNull(adapter.get().selected());
                    clickedView.get().performClick();
                    assertEquals(1, clickCount.get());
                });
        submit(adapter.get(), List.of(TrackRow.of(second), TrackRow.of(first)));
        main(() -> assertEquals(first.key(), adapter.get().selected().key()));
        submit(adapter.get(), List.of());
        main(() -> assertNull(adapter.get().selected()));
    }
}
