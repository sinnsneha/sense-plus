package com.senseplus.sense.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * components.pulseRing for cards: two rounded-rect rings that start at the target's edge
 * (this view's bounds inset by {@link #setInset}) and grow out to this view's edge while
 * fading 0.5 → 0, period 1200 ms, offset by half a period. Size this view to the target plus
 * the inset on every side and put it behind the target. With animations off a static 2dp ring
 * is shown. The period never goes under 800 ms (photosensitivity: ≤ 3 flashes a second).
 */
public class PulseRingView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    @Nullable private ValueAnimator anim;
    private float phase;
    private boolean active, visibleToUser;
    private float cornerPx, insetPx;

    public PulseRingView(Context c) {
        this(c, null);
    }

    public PulseRingView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        paint.setStyle(Paint.Style.STROKE);
        cornerPx = Ui.dpF(c, 32);
        insetPx = Ui.dpF(c, 20);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setRingColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    /** Distance between this view's edge and the target's edge. */
    public void setInset(float px) {
        insetPx = px;
        invalidate();
    }

    public void setCornerRadius(float px) {
        cornerPx = px;
        invalidate();
    }

    public void setActive(boolean on) {
        active = on;
        update();
        invalidate();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visibleToUser = isVisible;
        update();
    }

    private void update() {
        boolean run = active && visibleToUser && !Ui.isReducedMotion();
        if (run && anim == null) {
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(1200);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setInterpolator(new LinearInterpolator());
            anim.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            anim.start();
        } else if (!run && anim != null) {
            anim.cancel();
            anim = null;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!active) return;
        float w = getWidth(), h = getHeight();
        if (anim == null) {
            paint.setAlpha(170);
            paint.setStrokeWidth(Ui.dpF(getContext(), 2));
            float g = insetPx * 0.4f;
            r.set(insetPx - g, insetPx - g, w - insetPx + g, h - insetPx + g);
            canvas.drawRoundRect(r, cornerPx + g, cornerPx + g, paint);
            return;
        }
        paint.setStrokeWidth(Ui.dpF(getContext(), 3));
        float sw = paint.getStrokeWidth() / 2f;
        for (int i = 0; i < 2; i++) {
            float ph = (phase + i * 0.5f) % 1f;
            float g = (insetPx - sw) * ph;
            paint.setAlpha(Math.round(255 * 0.5f * (1f - ph)));
            r.set(insetPx - g, insetPx - g, w - insetPx + g, h - insetPx + g);
            canvas.drawRoundRect(r, cornerPx + g, cornerPx + g, paint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
        super.onDetachedFromWindow();
    }
}
