package app.musicplayer.android;

import android.content.Context;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.musicplayer.android.ui.AndroidLyricsPresenter;
import app.musicplayer.model.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AndroidLyricsPresenterTest {
    @Test public void reusesTextHandlesDuplicateTimestampsAndSeeksBackwards() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            TextView view = new TextView(context); ScrollView scroll = new ScrollView(context); scroll.addView(view);
            Lyrics lyrics = new Lyrics("fixture", List.of(new LyricLine(Duration.ZERO, "第一行"),
                    new LyricLine(Duration.ZERO, "同时间最后行"), new LyricLine(Duration.ofSeconds(1), "第二行")), true, "");
            try (var presenter = new AndroidLyricsPresenter(view, scroll)) {
                presenter.show(lyrics); Spannable original = (Spannable)view.getText();
                presenter.update(0); assertEquals("同时间最后行", highlighted(original));
                presenter.seek(1000); assertEquals("第二行", highlighted(original));
                presenter.update(0); assertEquals("同时间最后行", highlighted(original));
                presenter.update(-1); assertEquals("", highlighted(original));
                presenter.show(lyrics); assertSame(original, view.getText());
                presenter.close(); presenter.update(1000); assertSame(original, view.getText());
            }
        });
    }
    @Test public void scrollUsesWrappedLayoutInsteadOfMultiplyingRowHeight() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            ScrollView scroll = new ScrollView(context); LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
            TextView text = new TextView(context); text.setTextSize(18); text.setPadding(0, 13, 0, 0);
            content.addView(text); scroll.addView(content);
            List<LyricLine> lines = new ArrayList<>();
            for (int i = 0; i < 100; i++) lines.add(new LyricLine(Duration.ofSeconds(i), String.join("", Collections.nCopies(8, "中文长歌词换行验证")) + i));
            try (var presenter = new AndroidLyricsPresenter(text, scroll)) {
                presenter.show(new Lyrics("fixture", lines, true, "")); layout(scroll);
                presenter.update(0); text.getViewTreeObserver().dispatchOnPreDraw();
                presenter.update(30000); layout(scroll); text.getViewTreeObserver().dispatchOnPreDraw();
                Spannable buffer = (Spannable)text.getText();
                int start = buffer.getSpanStart(buffer.getSpans(0, buffer.length(), ForegroundColorSpan.class)[0]);
                int row = text.getLayout().getLineForOffset(start);
                int center = (text.getLayout().getLineTop(row) + text.getLayout().getLineBottom(row)) / 2;
                assertTrue(row > 30); assertEquals(center + text.getTotalPaddingTop() - 60, scroll.getScrollY());
            }
        });
    }
    private static void layout(ScrollView scroll) {
        scroll.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 240, 120);
    }
    private static String highlighted(Spannable buffer) {
        var spans = buffer.getSpans(0, buffer.length(), ForegroundColorSpan.class);
        return spans.length == 0 ? "" : buffer.subSequence(buffer.getSpanStart(spans[0]), buffer.getSpanEnd(spans[0])).toString();
    }
}
