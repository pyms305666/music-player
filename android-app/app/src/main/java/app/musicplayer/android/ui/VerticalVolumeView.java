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

/** A compact vertical volume slider whose drawn track and value range share the same bounds. */
public final class VerticalVolumeView extends View {
    public interface OnVolumeChangedListener {
        void onVolumeChanged(int progress, boolean fromUser);
    }

    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float trackWidth;
    private final float thumbRadius;
    private int progress = 70;
    private OnVolumeChangedListener listener;

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

    public void setOnVolumeChangedListener(OnVolumeChangedListener listener) {
        this.listener = listener;
    }

    public void setProgress(int value) {
        setProgressInternal(value, false);
    }

    public int getProgress() {
        return progress;
    }

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

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

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

    private void updateFromTouch(float y) {
        float top = getPaddingTop() + thumbRadius;
        float bottom = getHeight() - getPaddingBottom() - thumbRadius;
        if (bottom <= top) return;
        float fraction = (bottom - y) / (bottom - top);
        setProgressInternal(Math.round(fraction * 100f), true);
    }

    private void setProgressInternal(int value, boolean fromUser) {
        int clamped = Math.max(0, Math.min(100, value));
        if (clamped == progress) return;
        progress = clamped;
        invalidate();
        updateAccessibilityState();
        if (listener != null) listener.onVolumeChanged(progress, fromUser);
    }

    private void updateAccessibilityState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setStateDescription(progress + "%");
        }
    }
}
