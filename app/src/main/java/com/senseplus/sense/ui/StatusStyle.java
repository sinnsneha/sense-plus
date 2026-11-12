package com.senseplus.sense.ui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.senseplus.sense.R;
import com.senseplus.sense.ui.DuckLooks.Effect;
import com.senseplus.sense.ui.DuckLooks.Expression;
import com.senseplus.sense.ui.DuckLooks.Look;
import com.senseplus.sense.ui.Gradients.HeroPreset;

/**
 * The visual meaning of a SENSE GUARD safety level (androidImplementation.StatusStyle).
 * The single place that turns a status string into hero gradient, Pip look/expression/
 * effect, badge copy and icon, text colour and level-indicator position. Emergency
 * overrides aesthetics: saturated red, white status-bar icons, no cheerful faces.
 */
public enum StatusStyle {

    OFF(HeroPreset.OFF, HeroPreset.OFF, R.color.status_off_text, R.string.guard_badge_off,
            R.drawable.ic_bedtime, Look.SLEEP, Expression.SLEEPING, Expression.SLEEPING, Effect.NONE,
            true, WaveLevelIndicatorView.Level.NONE),
    NORMAL(HeroPreset.LAVENDER, HeroPreset.MINT, R.color.status_normal_text, R.string.guard_badge_normal,
            R.drawable.ic_shield_heart, Look.GUARD, Expression.IDLE, Expression.HAPPY, Effect.NONE,
            true, WaveLevelIndicatorView.Level.NORMAL),
    ATTENTION(HeroPreset.BUTTER, HeroPreset.BUTTER, R.color.status_attention_text,
            R.string.guard_badge_attention, R.drawable.ic_question_mark, Look.GUARD, Expression.ALERT,
            Expression.ALERT, Effect.QUESTION, true, WaveLevelIndicatorView.Level.ATTENTION),
    EMERGENCY(HeroPreset.EMERGENCY, HeroPreset.EMERGENCY, R.color.status_emergency_deep,
            R.string.guard_badge_emergency, R.drawable.ic_emergency, Look.GUARD, Expression.CONCERNED,
            Expression.CONCERNED, Effect.EXCLAIM, false, WaveLevelIndicatorView.Level.EMERGENCY);

    /** SENSE GUARD hero gradient. */
    public final HeroPreset hero;
    /** Guardian status card gradient (normal is mint there). */
    public final HeroPreset guardianHero;
    @ColorRes public final int textColor;
    @StringRes public final int badgeCopy;
    @DrawableRes public final int badgeIcon;
    public final Look look;
    public final Expression expression;
    /** Expression of the parent duck in the Guardian look. */
    public final Expression guardianExpression;
    public final Effect effect;
    /** True when the hero is light, so the status bar needs dark icons. */
    public final boolean lightStatusBar;
    public final WaveLevelIndicatorView.Level level;

    StatusStyle(HeroPreset hero, HeroPreset guardianHero, int textColor, int badgeCopy, int badgeIcon,
                Look look, Expression expression, Expression guardianExpression, Effect effect,
                boolean lightStatusBar, WaveLevelIndicatorView.Level level) {
        this.hero = hero;
        this.guardianHero = guardianHero;
        this.textColor = textColor;
        this.badgeCopy = badgeCopy;
        this.badgeIcon = badgeIcon;
        this.look = look;
        this.expression = expression;
        this.guardianExpression = guardianExpression;
        this.effect = effect;
        this.lightStatusBar = lightStatusBar;
        this.level = level;
    }

    public boolean isUrgent() {
        return this == ATTENTION || this == EMERGENCY;
    }

    /**
     * Status broadcast by UserMonitorService ("NORMAL" / "ATTENTION" / "EMERGENCY") or
     * stored in Firestore ("POSSIBLE EMERGENCY"). Unknown text maps to NORMAL.
     */
    public static StatusStyle from(@Nullable String status) {
        if (status == null) return NORMAL;
        if (status.contains("EMERGENCY")) return EMERGENCY;
        if (status.contains("ATTENTION")) return ATTENTION;
        return NORMAL;
    }

    /** Guardian dashboard: no status yet ("UNKNOWN") means the user's phone hasn't reported. */
    public static StatusStyle fromGuardian(@Nullable String status) {
        if (status == null || status.contains("UNKNOWN")) return OFF;
        return from(status);
    }
}
