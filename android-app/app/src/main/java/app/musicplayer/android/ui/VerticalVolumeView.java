package app.musicplayer.android.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import app.musicplayer.android.R;

/**
 * Android 竖向音量控件。支持触摸、键盘和无障碍反馈，把位置换算为 0～100 的音量百分比。 A compact vertical volume slider whose drawn
 * track and value range share the same bounds.
 */
public final class VerticalVolumeView extends View {
    /** 通知使用者音量值及其是否来自用户手势。 */
    public interface OnVolumeChangedListener {
        /** 将播放器或音量控件的变化同步到对应界面。 */
        void onVolumeChanged(int progress, boolean fromUser);
    }

    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float trackWidth;
    private final float thumbRadius;
    private int progress = 70;
    private OnVolumeChangedListener listener;

    /** 初始化本类型的状态和依赖；生命周期与资源归属见类型说明。 */
    public VerticalVolumeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        trackWidth = 4f * density;
        thumbRadius = 8f * density;

        inactivePaint.setColor(ContextCompat.getColor(context, R.color.divider_strong));
        inactivePaint.setStrokeWidth(trackWidth);
        inactivePaint.setStrokeCap(Paint.Cap.ROUND);
        activePaint.setColor(ContextCompat.getColor(context, R.color.accent));
        activePaint.setStrokeWidth(trackWidth);
        activePaint.setStrokeCap(Paint.Cap.ROUND);
        thumbPaint.setColor(ContextCompat.getColor(context, R.color.accent));

        setClickable(true);
        setFocusable(true);
        updateAccessibilityState();
    }

    /** 注册竖向音量控件的变化监听。 */
    public void setOnVolumeChangedListener(OnVolumeChangedListener listener) {
        this.listener = listener;
    }

    /** 设置或读取音量百分比，使用控件范围约束。 */
    public void setProgress(int value) {
        setProgressInternal(value, false);
    }

    /** 设置或读取音量百分比，使用控件范围约束。 */
    public int getProgress() {
        return progress;
    }

    /** 绘制竖向音量轨道和当前刻度。 */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2f;
        float top = getPaddingTop() + thumbRadius;
        float bottom = getHeight() - getPaddingBottom() - thumbRadius;
        if (bottom <= top) return;

        float thumbY = bottom - (bottom - top) * progress / 100f;
        canvas.drawLine(centerX, top, centerX, bottom, inactivePaint);
        canvas.drawLine(centerX, thumbY, centerX, bottom, activePaint);
        canvas.drawCircle(centerX, thumbY, thumbRadius, thumbPaint);
    }

    /** 把触摸位置转换为音量，并维护按下及点击反馈。 */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFromTouch(event.getY());
                return true;
            }
            case MotionEvent.ACTION_UP -> {
                updateFromTouch(event.getY());
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL -> {
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            }
            default -> {
                return super.onTouchEvent(event);
            }
        }
    }

    /** 分发标准点击事件，支持无障碍与触摸交互一致。 */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /** 响应键盘音量调整，保持与触摸相同的进度规则。 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            setProgressInternal(progress + 5, true);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            setProgressInternal(progress - 5, true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 将触摸坐标换算为竖向音量百分比。 */
    private void updateFromTouch(float y) {
        float top = getPaddingTop() + thumbRadius;
        float bottom = getHeight() - getPaddingBottom() - thumbRadius;
        if (bottom <= top) return;
        float fraction = (bottom - y) / (bottom - top);
        setProgressInternal(Math.round(fraction * 100f), true);
    }

    /** 约束音量值并按需要通知监听者及重绘。 */
    private void setProgressInternal(int value, boolean fromUser) {
        int clamped = Math.max(0, Math.min(100, value));
        if (clamped == progress) return;
        progress = clamped;
        invalidate();
        updateAccessibilityState();
        if (listener != null) listener.onVolumeChanged(progress, fromUser);
    }

    /** 更新控件的无障碍说明和当前值。 */
    private void updateAccessibilityState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setStateDescription(progress + "%");
        }
    }
}
