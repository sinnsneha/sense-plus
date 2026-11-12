package com.senseplus.sense.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.senseplus.sense.R;

/** Small UI helpers shared by every screen (design profile → androidImplementation.Ui.java). */
public final class Ui {

    /** tokens.motion.easing.standard */
    public static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** tokens.motion.easing.decelerate */
    public static final PathInterpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private Ui() {
    }

    public static int dp(Context c, float dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static float dpF(Context c, float dp) {
        return dp * c.getResources().getDisplayMetrics().density;
    }

    public static int color(Context c, @ColorRes int id) {
        return ContextCompat.getColor(c, id);
    }

    /** True when the user turned animations off (Developer options / accessibility). */
    public static boolean isReducedMotion() {
        return !ValueAnimator.areAnimatorsEnabled();
    }

    /**
     * Call before setContentView. Draws behind both system bars with dark icons; our
     * screens are light (v1 is light-only), so the icons must not follow the system's
     * dark mode the way SystemBarStyle.auto() would.
     */
    public static void enableEdgeToEdge(ComponentActivity a) {
        // API 26 can't draw dark navigation icons, so it gets the ink scrim behind light ones.
        EdgeToEdge.enable(a,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, color(a, R.color.surface_scrim)));
    }

    /**
     * Pads {@code top} by the status-bar inset + 12dp and {@code bottom} by the
     * navigation-bar inset + 20dp, keeping each view's own XML padding.
     */
    public static void applyInsets(@Nullable View top, @Nullable View bottom) {
        if (top != null) {
            final int l = top.getPaddingLeft(), t = top.getPaddingTop(),
                    r = top.getPaddingRight(), b = top.getPaddingBottom();
            final int extra = top.getResources().getDimensionPixelSize(R.dimen.top_bar_extra_top);
            ViewCompat.setOnApplyWindowInsetsListener(top, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(l + bars.left, t + bars.top + extra, r + bars.right, b);
                return insets;
            });
        }
        if (bottom != null) {
            final int l = bottom.getPaddingLeft(), t = bottom.getPaddingTop(),
                    r = bottom.getPaddingRight(), b = bottom.getPaddingBottom();
            final int extra = bottom.getResources().getDimensionPixelSize(R.dimen.bottom_safe_extra);
            ViewCompat.setOnApplyWindowInsetsListener(bottom, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(l + bars.left, t, r + bars.right, b + bars.bottom + extra);
                return insets;
            });
        }
    }

    /** Dark status-bar icons on light screens; light icons on the red emergency hero. */
    public static void setLightStatusBar(Activity a, boolean lightBackground) {
        WindowCompat.getInsetsController(a.getWindow(), a.getWindow().getDecorView())
                .setAppearanceLightStatusBars(lightBackground);
    }

    /** Wires a top-bar back button (id btnBack) to the normal back behaviour. */
    public static void bindBack(ComponentActivity a) {
        View back = a.findViewById(R.id.btnBack);
        if (back != null) {
            back.setOnClickListener(v -> a.getOnBackPressedDispatcher().onBackPressed());
        }
    }

    /** Marks a title or section header as a heading for TalkBack (API 26-27 safe). */
    public static void setHeading(View v) {
        ViewCompat.setAccessibilityHeading(v, true);
    }

    /**
     * tokens.motion.patterns.pressFeedback: scale to 0.96 while pressed, pop back on
     * release, light haptic tick. The click itself is still handled by the view.
     */
    @SuppressLint("ClickableViewAccessibility")
    public static void pressFeedback(View v) {
        final OvershootInterpolator pop = new OvershootInterpolator(1.4f);
        v.setOnTouchListener((view, e) -> {
            if (isReducedMotion()) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90)
                            .setInterpolator(STANDARD).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(240)
                            .setInterpolator(pop).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** Applies pressFeedback to several views at once. */
    public static void pressFeedback(View... views) {
        for (View v : views) if (v != null) pressFeedback(v);
    }

    /** Short state text for TalkBack, e.g. "Selected" on a segmented control. */
    public static void setStateDescription(View v, @Nullable CharSequence state) {
        ViewCompat.setStateDescription(v, state);
    }
}
