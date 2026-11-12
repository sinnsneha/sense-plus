package com.senseplus.sense.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.senseplus.sense.R;
import com.senseplus.sense.ui.DuckLooks.Effect;
import com.senseplus.sense.ui.DuckLooks.Expression;
import com.senseplus.sense.ui.DuckLooks.Look;

import java.util.Random;

/**
 * Pip the duckling (characters.implementation.DuckView). Draws the layered VectorDrawable
 * kit for a look, bottom-centred in a square, and brings it to life:
 * <ul>
 * <li>idle breathe (scale 1 → 1.025, 3200 ms) and bob (−3dp, 2400 ms), pivot bottom-centre;</li>
 * <li>a 120 ms blink every 3–6 s while the eyes are open;</li>
 * <li>talk (talk/idle every 150 ms), pulsing sound waves, pulse rings;</li>
 * <li>a squash-and-pop reaction when the look or expression changes.</li>
 * </ul>
 * Idle motion uses view properties (RenderNode), so the stack is not redrawn on idle
 * frames. Every loop stops when the view is hidden or animations are turned off.
 * Pip is decorative: not focusable and not announced by TalkBack.
 */
public class DuckView extends View {

    public enum Crop { FULL, HEAD }

    private final DuckRenderer renderer;
    private Crop crop = Crop.FULL;
    private boolean idleMotion = true;
    private boolean talking, wavesPulsing, pulseRings;
    private boolean visibleToUser;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float ringPhase;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean talkFrame;

    @Nullable private ValueAnimator breathe, bob, waves, rings, reactAnim;
    private float breatheScale = 1f, reactX = 1f, reactY = 1f;

    private final Runnable unblink = new Runnable() {
        @Override public void run() {
            renderer.setFaceOverride(null);
            invalidate();
        }
    };

    private final Runnable blinkTask = new Runnable() {
        @Override public void run() {
            if (!talking && renderer.getExpression().openEyes) {
                renderer.setFaceOverride(Expression.SLEEPING);
                invalidate();
                handler.postDelayed(unblink, 120);
            }
            scheduleBlink();
        }
    };

    private final Runnable talkTask = new Runnable() {
        @Override public void run() {
            talkFrame = !talkFrame;
            renderer.setFaceOverride(talkFrame ? Expression.TALK : Expression.IDLE);
            invalidate();
            handler.postDelayed(this, 150);
        }
    };

    public DuckView(Context context) {
        this(context, null);
    }

    public DuckView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DuckView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        renderer = new DuckRenderer(context);
        Look look = Look.PLAIN;
        Expression expression = null;
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DuckView, defStyleAttr, 0);
            look = Look.fromIndex(a.getInt(R.styleable.DuckView_duckLook, Look.PLAIN.ordinal()));
            int e = a.getInt(R.styleable.DuckView_duckExpression, -1);
            if (e >= 0 && e < Expression.values().length) expression = Expression.values()[e];
            idleMotion = a.getBoolean(R.styleable.DuckView_idleMotion, true);
            crop = a.getInt(R.styleable.DuckView_duckCrop, 0) == 1 ? Crop.HEAD : Crop.FULL;
            a.recycle();
        }
        renderer.setLook(look);
        renderer.setExpression(expression != null ? expression : look.defaultExpression);
        renderer.preload();

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(Color.WHITE);
        ringPaint.setStrokeWidth(Ui.dpF(context, 3));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        if (crop == Crop.HEAD) setupHeadClip();
    }

    // ---------- public API ----------

    public Look getLook() {
        return renderer.getLook();
    }

    public Expression getExpression() {
        return renderer.getExpression();
    }

    public void setLook(Look look) {
        setLook(look, look.defaultExpression, false);
    }

    public void setLook(Look look, Expression expression, boolean animate) {
        if (look == renderer.getLook() && expression == renderer.getExpression()) return;
        Runnable swap = () -> {
            renderer.setLook(look);
            renderer.setExpression(expression);
            renderer.preload();
            invalidate();
        };
        if (animate) react(swap); else swap.run();
    }

    public void setExpression(Expression expression, boolean animate) {
        if (expression == renderer.getExpression()) return;
        Runnable swap = () -> {
            renderer.setExpression(expression);
            renderer.preload();
            invalidate();
        };
        if (animate) react(swap); else swap.run();
    }

    public void setEffect(Effect effect) {
        if (effect == renderer.getEffect()) return;
        renderer.setEffect(effect);
        renderer.preload();
        invalidate();
    }

    public void setCrop(Crop c) {
        crop = c;
        if (c == Crop.HEAD) setupHeadClip();
        invalidate();
    }

    /** Talk/idle alternate every 150 ms while text-to-speech is speaking. */
    public void setTalking(boolean on) {
        if (talking == on) return;
        talking = on;
        handler.removeCallbacks(talkTask);
        if (on) {
            if (loopsAllowed()) {
                handler.post(talkTask);
            } else {
                renderer.setFaceOverride(Expression.TALK);
            }
        } else {
            renderer.setFaceOverride(null);
        }
        invalidate();
    }

    /** Hearing look: sound waves pulse 0.35 ↔ 1.0 while listening; full opacity otherwise. */
    public void setWavesPulsing(boolean on) {
        wavesPulsing = on;
        updateLoops();
        if (!on) {
            renderer.setLayerAlpha(R.drawable.duck_hearing_waves, 1f);
            invalidate();
        }
    }

    public void setLayerVisible(@DrawableRes int res, boolean visible) {
        renderer.setLayerVisible(res, visible);
        invalidate();
    }

    public void setLayerAlpha(@DrawableRes int res, float alpha) {
        renderer.setLayerAlpha(res, alpha);
        invalidate();
    }

    /** Emergency emphasis: two rings expanding behind Pip (a static ring with motion off). */
    public void setPulseRings(boolean on) {
        if (pulseRings == on) return;
        pulseRings = on;
        updateLoops();
        invalidate();
    }

    public void setPulseRingColor(int color) {
        ringPaint.setColor(color);
        invalidate();
    }

    public void setIdleMotionEnabled(boolean enabled) {
        idleMotion = enabled;
        updateLoops();
    }

    // ---------- layout & drawing ----------

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int def = Ui.dp(getContext(), 96);
        setMeasuredDimension(resolveSize(def, widthMeasureSpec), resolveSize(def, heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setPivotX(w / 2f);
        setPivotY(h);
        float size = Math.min(w - getPaddingLeft() - getPaddingRight(), h - getPaddingTop() - getPaddingBottom());
        renderer.setHideDoodles(crop == Crop.HEAD || size < Ui.dpF(getContext(), 64));
    }

    private void setupHeadClip() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, @NonNull Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        setClipToOutline(true);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int pl = getPaddingLeft(), pt = getPaddingTop();
        float cw = getWidth() - pl - getPaddingRight();
        float ch = getHeight() - pt - getPaddingBottom();
        if (cw <= 0 || ch <= 0) return;
        float size, left, top;
        if (crop == Crop.HEAD) {
            // Head only: the top 55% of the canvas fills the view (placementRules.avatar).
            size = ch / 0.55f;
            left = pl + (cw - size) / 2f;
            top = pt;
        } else {
            size = Math.min(cw, ch);
            left = pl + (cw - size) / 2f;
            top = pt + ch - size;
        }
        if (pulseRings) drawRings(canvas, left, top, size);
        renderer.draw(canvas, left, top, size);
    }

    private void drawRings(Canvas canvas, float left, float top, float size) {
        float k = size / 240f;
        float cx = left + 122 * k, cy = top + 130 * k, base = 92 * k;
        if (!loopsAllowed()) {
            ringPaint.setAlpha(150);
            canvas.drawCircle(cx, cy, base * 1.15f, ringPaint);
            return;
        }
        for (int i = 0; i < 2; i++) {
            float ph = (ringPhase + i * 0.5f) % 1f;
            ringPaint.setAlpha(Math.round(255 * 0.5f * (1f - ph)));
            canvas.drawCircle(cx, cy, base * (1f + 0.6f * ph), ringPaint);
        }
    }

    // ---------- motion ----------

    private boolean loopsAllowed() {
        return visibleToUser && !Ui.isReducedMotion();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visibleToUser = isVisible;
        updateLoops();
    }

    @Override
    protected void onDetachedFromWindow() {
        visibleToUser = false;
        updateLoops();
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private void updateLoops() {
        boolean run = loopsAllowed();

        // breathe + bob
        if (run && idleMotion) {
            if (breathe == null) {
                breathe = ValueAnimator.ofFloat(1f, 1.025f);
                breathe.setDuration(3200);
                breathe.setRepeatMode(ValueAnimator.REVERSE);
                breathe.setRepeatCount(ValueAnimator.INFINITE);
                breathe.setInterpolator(new AccelerateDecelerateInterpolator());
                breathe.addUpdateListener(a -> {
                    breatheScale = (float) a.getAnimatedValue();
                    applyScale();
                });
                breathe.start();
            }
            if (bob == null) {
                bob = ValueAnimator.ofFloat(0f, -Ui.dpF(getContext(), 3));
                bob.setDuration(2400);
                bob.setRepeatMode(ValueAnimator.REVERSE);
                bob.setRepeatCount(ValueAnimator.INFINITE);
                bob.setInterpolator(new AccelerateDecelerateInterpolator());
                bob.addUpdateListener(a -> setTranslationY((float) a.getAnimatedValue()));
                bob.start();
            }
            handler.removeCallbacks(blinkTask);
            scheduleBlink();
        } else {
            if (breathe != null) { breathe.cancel(); breathe = null; }
            if (bob != null) { bob.cancel(); bob = null; }
            breatheScale = 1f;
            applyScale();
            setTranslationY(0f);
            handler.removeCallbacks(blinkTask);
        }

        // talking loop follows visibility too
        handler.removeCallbacks(talkTask);
        if (talking) {
            if (run) handler.post(talkTask); else renderer.setFaceOverride(Expression.TALK);
        }

        // sound waves
        if (run && wavesPulsing) {
            if (waves == null) {
                waves = ValueAnimator.ofFloat(0.35f, 1f);
                waves.setDuration(1200);
                waves.setRepeatMode(ValueAnimator.REVERSE);
                waves.setRepeatCount(ValueAnimator.INFINITE);
                waves.addUpdateListener(a -> {
                    renderer.setLayerAlpha(R.drawable.duck_hearing_waves, (float) a.getAnimatedValue());
                    invalidate();
                });
                waves.start();
            }
        } else if (waves != null) {
            waves.cancel();
            waves = null;
            renderer.setLayerAlpha(R.drawable.duck_hearing_waves, 1f);
            invalidate();
        }

        // pulse rings (period 1200 ms ≥ 800 ms: photosensitivity-safe)
        if (run && pulseRings) {
            if (rings == null) {
                rings = ValueAnimator.ofFloat(0f, 1f);
                rings.setDuration(1200);
                rings.setRepeatCount(ValueAnimator.INFINITE);
                rings.setInterpolator(new LinearInterpolator());
                rings.addUpdateListener(a -> {
                    ringPhase = (float) a.getAnimatedValue();
                    invalidate();
                });
                rings.start();
            }
        } else if (rings != null) {
            rings.cancel();
            rings = null;
            invalidate();
        }
    }

    private void scheduleBlink() {
        if (!loopsAllowed() || !idleMotion) return;
        handler.postDelayed(blinkTask, 3000 + random.nextInt(3000));
    }

    private void applyScale() {
        setScaleX(breatheScale * reactX);
        setScaleY(breatheScale * reactY);
    }

    /** characters: squash 90 ms → swap layers at the peak → pop back with overshoot 360 ms. */
    private void react(Runnable swap) {
        if (reactAnim != null) reactAnim.cancel();
        if (Ui.isReducedMotion() || !isLaidOut() || !visibleToUser) {
            swap.run();
            return;
        }
        ValueAnimator squash = ValueAnimator.ofFloat(0f, 1f);
        squash.setDuration(90);
        squash.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            reactX = 1f + 0.04f * f;
            reactY = 1f - 0.06f * f;
            applyScale();
        });
        squash.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override public void onAnimationCancel(Animator animation) {
                cancelled = true;
                swap.run();
                reactX = reactY = 1f;
                applyScale();
            }

            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                swap.run();
                ValueAnimator pop = ValueAnimator.ofFloat(1f, 0f);
                pop.setDuration(360);
                pop.setInterpolator(new OvershootInterpolator(1.4f));
                pop.addUpdateListener(a -> {
                    float f = (float) a.getAnimatedValue();
                    reactX = 1f + 0.04f * f;
                    reactY = 1f - 0.06f * f;
                    applyScale();
                });
                reactAnim = pop;
                pop.start();
            }
        });
        reactAnim = squash;
        squash.start();
    }
}
