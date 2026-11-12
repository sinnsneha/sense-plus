package com.senseplus.sense.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.senseplus.sense.R;

/**
 * components.waveLevelIndicator: a gradient pill track (mint → butter → coral) with a white
 * grip thumb over the active safety level, and three labels underneath. The active label
 * is large, in its status colour, with a dot above and "now" below. One accessibility node:
 * "Safety level: …", announced politely when it changes.
 */
public class WaveLevelIndicatorView extends View {

    public enum Level { NONE, NORMAL, ATTENTION, EMERGENCY }

    private static final int[] LABELS = {R.string.level_normal, R.string.level_attention, R.string.level_emergency};
    private static final int[] COLORS = {R.color.status_normal_text, R.color.status_attention_text,
            R.color.status_emergency_text};

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final String[] labels = new String[3];
    private final String now;

    private final float trackH, thumbW, thumbH, dotD, density;
    private final float activeSize, inactiveSize, captionSize;
    private Level level = Level.NONE;
    private float thumbIndex = 0f;
    private float thumbAlpha = 0f;
    @Nullable private ValueAnimator anim;

    public WaveLevelIndicatorView(Context c) {
        this(c, null);
    }

    public WaveLevelIndicatorView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        density = getResources().getDisplayMetrics().density;
        trackH = 18 * density;
        thumbW = 28 * density;
        thumbH = 34 * density;
        dotD = 8 * density;
        activeSize = sp(24);
        inactiveSize = sp(16);
        captionSize = sp(13);

        for (int i = 0; i < 3; i++) labels[i] = c.getString(LABELS[i]);
        now = c.getString(R.string.level_now);

        Typeface bold = ResourcesCompat.getFont(c, R.font.lexend_bold);
        Typeface semi = ResourcesCompat.getFont(c, R.font.lexend_semibold);
        Typeface regular = ResourcesCompat.getFont(c, R.font.lexend_regular);
        active.setTypeface(bold);
        active.setTextAlign(Paint.Align.CENTER);
        inactive.setTypeface(semi);
        inactive.setTextAlign(Paint.Align.CENTER);
        inactive.setColor(Ui.color(c, R.color.ink_500));
        caption.setTypeface(regular);
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setColor(Ui.color(c, R.color.ink_500));

        thumb.setColor(Ui.color(c, R.color.white));
        thumb.setShadowLayer(4 * density, 0, 1.5f * density, Ui.color(c, R.color.ripple_ink));
        thumbEdge.setStyle(Paint.Style.STROKE);
        thumbEdge.setStrokeWidth(density);
        thumbEdge.setColor(Ui.color(c, R.color.ink_200));
        grip.setStyle(Paint.Style.STROKE);
        grip.setStrokeWidth(2 * density);
        grip.setStrokeCap(Paint.Cap.ROUND);
        grip.setColor(Ui.color(c, R.color.ink_300));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        updateDescription();
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics());
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level l, boolean animate) {
        if (l == level) return;
        Level old = level;
        level = l;
        updateDescription();
        if (anim != null) anim.cancel();
        if (l == Level.NONE) {
            thumbAlpha = 0f;
            invalidate();
            return;
        }
        float target = l.ordinal() - 1;
        if (!animate || Ui.isReducedMotion() || old == Level.NONE) {
            thumbIndex = target;
            thumbAlpha = 1f;
            invalidate();
            return;
        }
        float from = thumbIndex;
        anim = ValueAnimator.ofFloat(from, target);
        anim.setDuration(360);
        anim.setInterpolator(Ui.STANDARD);
        anim.addUpdateListener(a -> {
            thumbIndex = (float) a.getAnimatedValue();
            invalidate();
        });
        thumbAlpha = 1f;
        anim.start();
    }

    private void updateDescription() {
        Context c = getContext();
        if (level == Level.NONE) {
            setContentDescription(c.getString(R.string.level_cd_off));
        } else if (level == Level.EMERGENCY) {
            // Spoken wording stays "possible emergency" (project design decision 4).
            setContentDescription(c.getString(R.string.level_cd, c.getString(R.string.level_cd_emergency)));
        } else {
            setContentDescription(c.getString(R.string.level_cd, labels[level.ordinal() - 1]));
        }
    }

    // ---------- layout ----------

    private float labelsTop() {
        return thumbH + 12 * density;
    }

    private float lineHeight(Paint p, float size) {
        p.setTextSize(size);
        Paint.FontMetrics fm = p.getFontMetrics();
        return fm.descent - fm.ascent;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        float h = labelsTop() + dotD + 4 * density + lineHeight(active, activeSize)
                + 2 * density + lineHeight(caption, captionSize) + 4 * density;
        setMeasuredDimension(w, resolveSize(Math.round(h) + getPaddingTop() + getPaddingBottom(),
                heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float left = getPaddingLeft(), right = w - getPaddingRight();
        Context c = getContext();
        track.setShader(new LinearGradient(left, 0, right, 0,
                new int[]{Ui.color(c, R.color.grad_track_mint), Ui.color(c, R.color.grad_track_butter),
                        Ui.color(c, R.color.grad_track_coral)}, null, Shader.TileMode.CLAMP));
    }

    private float colCenter(float index) {
        float left = getPaddingLeft();
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        return left + w * (2 * index + 1) / 6f;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float left = getPaddingLeft(), right = getWidth() - getPaddingRight();
        float top = getPaddingTop();
        float colW = (right - left) / 3f;

        // track
        float ty = top + (thumbH - trackH) / 2f;
        r.set(left, ty, right, ty + trackH);
        canvas.drawRoundRect(r, trackH / 2f, trackH / 2f, track);

        // thumb
        if (thumbAlpha > 0f) {
            float cx = colCenter(thumbIndex);
            r.set(cx - thumbW / 2f, top, cx + thumbW / 2f, top + thumbH);
            thumb.setAlpha(Math.round(255 * thumbAlpha));
            canvas.drawRoundRect(r, thumbW / 2f, thumbW / 2f, thumb);
            canvas.drawRoundRect(r, thumbW / 2f, thumbW / 2f, thumbEdge);
            float gy0 = top + thumbH * 0.32f, gy1 = top + thumbH * 0.68f;
            canvas.drawLine(cx - 3 * density, gy0, cx - 3 * density, gy1, grip);
            canvas.drawLine(cx + 3 * density, gy0, cx + 3 * density, gy1, grip);
        }

        // labels
        float lt = top + labelsTop();
        float labelLineH = lineHeight(active, activeSize);
        float lineTop = lt + dotD + 4 * density;
        for (int i = 0; i < 3; i++) {
            float cx = colCenter(i);
            boolean isActive = level != Level.NONE && level.ordinal() - 1 == i;
            Paint p = isActive ? active : inactive;
            float size = isActive ? activeSize : inactiveSize;
            p.setTextSize(size);
            float maxW = colW - 6 * density;
            float tw = p.measureText(labels[i]);
            if (tw > maxW) p.setTextSize(size * maxW / tw);
            if (isActive) {
                int color = Ui.color(getContext(), COLORS[i]);
                p.setColor(color);
                dot.setColor(color);
                canvas.drawCircle(cx, lt + dotD / 2f, dotD / 2f, dot);
            }
            // centre every label vertically in the (active-size) line box
            Paint.FontMetrics fm = p.getFontMetrics();
            float lh = fm.descent - fm.ascent;
            canvas.drawText(labels[i], cx, lineTop + (labelLineH - lh) / 2f - fm.ascent, p);
            if (isActive) {
                caption.setTextSize(captionSize);
                Paint.FontMetrics cm = caption.getFontMetrics();
                canvas.drawText(now, cx, lineTop + labelLineH + 2 * density - cm.ascent, caption);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
