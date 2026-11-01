package app.musicplayer.android.ui;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import android.widget.TextView;

import app.musicplayer.lyrics.LyricTimeline;
import app.musicplayer.model.Lyrics;

/**
 * Android 歌词文本呈现。复用富文本内容，按 LyricTimeline 高亮活动行，使用实际排版位置滚动，支持跳转与重复时间戳。 Main-thread lyric rendering:
 * keep characters stable, replace only two highlight spans.
 */
public final class AndroidLyricsPresenter implements AutoCloseable {
    private final TextView text;
    private final ScrollView scroll;
    private final ForegroundColorSpan color = new ForegroundColorSpan(Color.rgb(240, 90, 60));
    private final StyleSpan bold = new StyleSpan(android.graphics.Typeface.BOLD);
    private final ViewTreeObserver.OnPreDrawListener beforeDraw =
            () -> {
                scrollToActive();
                return true;
            };
    private Lyrics lyrics;
    private LyricTimeline timeline;
    private Spannable buffer;
    private int[] starts = new int[0], ends = new int[0];
    private int active = -1;
    private boolean scrollPending, closed;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public AndroidLyricsPresenter(TextView text, ScrollView scroll) {
        this.text = text;
        this.scroll = scroll;
        text.getViewTreeObserver().addOnPreDrawListener(beforeDraw);
    }

    /** 展示窗口或当前歌单条目，并保留有效选择状态。 */
    public void show(Lyrics value) {
        if (closed || lyrics == value) return;
        lyrics = value;
        timeline = value.timed() ? new LyricTimeline(value.lines()) : null;
        starts = new int[value.timed() ? value.lines().size() : 0];
        ends = new int[starts.length];
        var characters = new SpannableStringBuilder();
        for (int row = 0; row < value.lines().size(); row++) {
            if (row > 0) characters.append('\n');
            if (value.timed()) starts[row] = characters.length();
            characters.append(value.lines().get(row).text());
            if (value.timed()) ends[row] = characters.length();
        }
        text.setText(
                characters,
                value.timed() ? TextView.BufferType.SPANNABLE : TextView.BufferType.NORMAL);
        buffer = value.timed() ? (Spannable) text.getText() : null;
        active = -1;
        scrollPending = false;
    }

    /** 实现测试 ContentProvider 的写接口约定。 */
    public void update(long positionMillis) {
        if (closed || lyrics == null || !lyrics.timed()) return;
        highlight(timeline.advance(positionMillis));
    }

    /** 在播放跳转时重新定位活动歌词行，支持向前或向后跳转。 */
    public void seek(long positionMillis) {
        if (closed || lyrics == null || !lyrics.timed()) return;
        highlight(timeline.seek(positionMillis));
    }

    /** 更新活动歌词的高亮样式，清除旧行高亮。 */
    private void highlight(int next) {
        if (active == next) return;
        buffer.removeSpan(color);
        buffer.removeSpan(bold);
        active = next;
        if (next >= 0 && starts[next] < ends[next]) {
            buffer.setSpan(color, starts[next], ends[next], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            buffer.setSpan(bold, starts[next], ends[next], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Pre-draw runs after text layout, including wrapping caused by bold glyphs.
        scrollPending = next >= 0;
        text.invalidate();
    }

    /** 使用歌词实际排版的位置滚动到活动行。 */
    private void scrollToActive() {
        if (closed || !scrollPending || active < 0 || text.getLayout() == null) return;
        int top = text.getTop();
        ViewParent parent = text.getParent();
        while (parent instanceof View view && view != scroll) {
            top += view.getTop() - view.getScrollY();
            parent = view.getParent();
        }
        if (parent != scroll) return;
        var layout = text.getLayout();
        int row = layout.getLineForOffset(starts[active]);
        int center = (layout.getLineTop(row) + layout.getLineBottom(row)) / 2;
        int target = Math.max(0, top + text.getTotalPaddingTop() + center - scroll.getHeight() / 2);
        scrollPending = false;
        scroll.smoothScrollTo(0, target);
    }

    /** 结束本组件或句柄的使用；具体资源归属见所属类型的说明。 */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        var observer = text.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(beforeDraw);
        lyrics = null;
        timeline = null;
        buffer = null;
        starts = ends = new int[0];
    }
}
