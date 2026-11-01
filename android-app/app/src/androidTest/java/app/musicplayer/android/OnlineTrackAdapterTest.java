package app.musicplayer.android;

import static org.junit.Assert.*;

import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import app.musicplayer.android.ui.OnlineTrackAdapter;
import app.musicplayer.model.OnlineTrackInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OnlineTrackAdapter 的回归测试。使用可控夹具验证正常流程、边界输入及资源或失败处理。被测职责：Android 在线歌曲 RecyclerView 适配器。用稳定身份及
 * DiffUtil 更新增量结果，并保留用户选择，避免复用行误触其他歌曲。
 */
@RunWith(AndroidJUnit4.class)
public class OnlineTrackAdapterTest {
    /** 将指定测试操作投递到 Android 主线程并等待结果，避免在测试线程直接访问控件。 */
    private void main(Runnable work) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(work);
    }

    /** 提交后台工作并返回结果句柄，完成或失败由句柄通知调用者。 */
    private void submit(OnlineTrackAdapter adapter, List<OnlineTrackInfo> values) throws Exception {
        var complete = new CompletableFuture<Void>();
        main(() -> adapter.submit(values, () -> complete.complete(null)));
        complete.get(5, TimeUnit.SECONDS);
    }

    /** 给测试控件指定可用尺寸并触发布局。 */
    private void layout(RecyclerView view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 1000);
    }

    /** 验证追加来源保留选择，旧列表行不会误点击其他歌曲。 */
    @Test
    public void appendingSourcesPreservesSelectionAndRemovedHolderCannotClickAnotherSong()
            throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var adapter = new AtomicReference<OnlineTrackAdapter>();
        var recycler = new AtomicReference<RecyclerView>();
        var clicked = new AtomicReference<View>();
        var clicks = new java.util.concurrent.atomic.AtomicInteger();
        main(
                () -> {
                    adapter.set(new OnlineTrackAdapter(info -> clicks.incrementAndGet()));
                    recycler.set(new RecyclerView(context));
                    recycler.get().setLayoutManager(new LinearLayoutManager(context));
                    recycler.get().setItemAnimator(null);
                    recycler.get().setAdapter(adapter.get());
                });
        var first = new OnlineTrackInfo("source", "夜曲", "artist", "", null, "first", null);
        var later = new OnlineTrackInfo("later", "song", "artist", "", null, "later", null);
        submit(adapter.get(), List.of(first));
        main(
                () -> {
                    layout(recycler.get());
                    clicked.set(recycler.get().findViewHolderForAdapterPosition(0).itemView);
                    assertTrue(clicked.get().performClick());
                    assertEquals(first, adapter.get().selected());
                });
        var refreshed = first.withAvailability(OnlineTrackInfo.Availability.TENTATIVE, "可尝试下载");
        submit(adapter.get(), List.of(refreshed, later));
        main(
                () -> {
                    layout(recycler.get());
                    assertEquals(refreshed, adapter.get().selected());
                    android.widget.TextView subtitle =
                            recycler.get()
                                    .findViewHolderForAdapterPosition(0)
                                    .itemView
                                    .findViewById(R.id.itemSubtitle);
                    assertTrue(subtitle.getText().toString().contains("可尝试下载"));
                });
        submit(adapter.get(), List.of(later));
        main(
                () -> {
                    layout(recycler.get());
                    assertNull(adapter.get().selected());
                    clicked.get().performClick();
                    assertEquals(1, clicks.get());
                });
        main(() -> adapter.get().clearSelection());
        submit(adapter.get(), List.of(first, later));
        main(() -> assertNull(adapter.get().selected()));
    }
}
