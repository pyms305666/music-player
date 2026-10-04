package app.musicplayer.android;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
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
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OnlineTrackAdapterTest {
    private void main(Runnable work) { InstrumentationRegistry.getInstrumentation().runOnMainSync(work); }
    private void submit(OnlineTrackAdapter adapter, List<OnlineTrackInfo> values) throws Exception {
        var complete = new CompletableFuture<Void>();
        main(() -> adapter.submit(values, () -> complete.complete(null)));
        complete.get(5, TimeUnit.SECONDS);
    }
    private void layout(RecyclerView view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 1080, 1000);
    }
    @Test public void appendingSourcesPreservesSelectionAndRemovedHolderCannotClickAnotherSong() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var adapter = new AtomicReference<OnlineTrackAdapter>(); var recycler = new AtomicReference<RecyclerView>();
        var clicked = new AtomicReference<View>(); var clicks = new java.util.concurrent.atomic.AtomicInteger();
        main(() -> {
            adapter.set(new OnlineTrackAdapter(info -> clicks.incrementAndGet()));
            recycler.set(new RecyclerView(context)); recycler.get().setLayoutManager(new LinearLayoutManager(context));
            recycler.get().setItemAnimator(null); recycler.get().setAdapter(adapter.get());
        });
        var first = new OnlineTrackInfo("source", "夜曲", "artist", "", null, "first", null);
        var later = new OnlineTrackInfo("later", "song", "artist", "", null, "later", null);
        submit(adapter.get(), List.of(first));
        main(() -> {
            layout(recycler.get()); clicked.set(recycler.get().findViewHolderForAdapterPosition(0).itemView);
            assertTrue(clicked.get().performClick()); assertEquals(first, adapter.get().selected());
        });
        var refreshed = first.withAvailability(OnlineTrackInfo.Availability.TENTATIVE, "可尝试下载");
        submit(adapter.get(), List.of(refreshed, later));
        main(() -> {
            layout(recycler.get()); assertEquals(refreshed, adapter.get().selected());
            android.widget.TextView subtitle = recycler.get().findViewHolderForAdapterPosition(0).itemView.findViewById(R.id.itemSubtitle);
            assertTrue(subtitle.getText().toString().contains("可尝试下载"));
        });
        submit(adapter.get(), List.of(later));
        main(() -> { layout(recycler.get()); assertNull(adapter.get().selected()); clicked.get().performClick(); assertEquals(1, clicks.get()); });
        main(() -> adapter.get().clearSelection());
        submit(adapter.get(), List.of(first, later));
        main(() -> assertNull(adapter.get().selected()));
    }
}
