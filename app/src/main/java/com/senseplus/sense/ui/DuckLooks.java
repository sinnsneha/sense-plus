package com.senseplus.sense.ui;

import androidx.annotation.DrawableRes;

import com.senseplus.sense.R;

/**
 * Pip's looks: ordered layer stacks (bottom to top) on the shared 240-unit canvas, taken
 * from duck_kit/looks.json (characters.looks). FACE is replaced by the current expression,
 * FX by the current effect; PARENT layers use the parent transform (translate 40,33;
 * scale 0.84). PROFILE and COGNITIVE are new looks drawn for this app in the kit's style.
 */
public final class DuckLooks {

    private DuckLooks() {
    }

    public enum Expression {
        IDLE(R.drawable.duck_face_idle, true),
        HAPPY(R.drawable.duck_face_happy, false),
        WINK(R.drawable.duck_face_wink, false),
        TALK(R.drawable.duck_face_talk, true),
        ALERT(R.drawable.duck_face_alert, true),
        CONCERNED(R.drawable.duck_face_concerned, true),
        SLEEPING(R.drawable.duck_face_sleeping, false);

        @DrawableRes public final int res;
        /** Blink only makes sense when the eyes are open. */
        public final boolean openEyes;

        Expression(int res, boolean openEyes) {
            this.res = res;
            this.openEyes = openEyes;
        }

        /** Never smile, wink or chat during ATTENTION / EMERGENCY (implementationContract). */
        public boolean isCheerful() {
            return this == HAPPY || this == WINK || this == TALK;
        }
    }

    public enum Effect {
        NONE(0), QUESTION(R.drawable.duck_fx_question), EXCLAIM(R.drawable.duck_fx_exclaim),
        SPARKLES(R.drawable.duck_fx_sparkles);

        @DrawableRes public final int res;

        Effect(int res) {
            this.res = res;
        }
    }

    /** One layer of a look. */
    public static final class Layer {
        public static final int FACE = -1;
        public static final int FX = -2;

        @DrawableRes public final int res;
        public final boolean parent;
        /** Optional doodles (hearts, notes, sparkles) are hidden below 64dp. */
        public final boolean doodle;

        Layer(int res, boolean parent, boolean doodle) {
            this.res = res;
            this.parent = parent;
            this.doodle = doodle;
        }

        public boolean isFace() {
            return res == FACE;
        }

        public boolean isFx() {
            return res == FX;
        }
    }

    private static Layer l(int res) {
        return new Layer(res, false, false);
    }

    private static Layer doodle(int res) {
        return new Layer(res, false, true);
    }

    private static Layer parent(int res) {
        return new Layer(res, true, false);
    }

    private static final Layer FACE = l(Layer.FACE);
    private static final Layer FX = l(Layer.FX);
    private static final Layer PARENT_FACE = parent(Layer.FACE);

    public enum Look {
        HOME(Expression.HAPPY,
                doodle(R.drawable.duck_fx_heart), l(R.drawable.duck_shadow), l(R.drawable.duck_base),
                l(R.drawable.duck_pose_wave), FACE),
        GUARD(Expression.IDLE,
                l(R.drawable.duck_guard_cape), l(R.drawable.duck_shadow), l(R.drawable.duck_base),
                l(R.drawable.duck_guard_shield), l(R.drawable.duck_pose_hold), l(R.drawable.duck_guard_knot),
                FACE, FX),
        GUARDIAN(Expression.HAPPY,
                l(R.drawable.duck_guardian_shadow), parent(R.drawable.duck_base),
                parent(R.drawable.duck_pose_down), PARENT_FACE, l(R.drawable.duck_guardian_duckling),
                doodle(R.drawable.duck_guardian_heart), FX),
        COMM(Expression.HAPPY,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_pose_wave), FACE,
                l(R.drawable.duck_comm_bubble)),
        VISUAL(Expression.HAPPY,
                doodle(R.drawable.duck_fx_note), l(R.drawable.duck_shadow), l(R.drawable.duck_base),
                l(R.drawable.duck_visual_book), l(R.drawable.duck_pose_hold), FACE,
                l(R.drawable.duck_visual_glasses)),
        HEARING(Expression.IDLE,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_pose_listen), FACE,
                l(R.drawable.duck_hearing_waves), FX),
        SENSOR(Expression.ALERT,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_pose_down), FACE,
                l(R.drawable.duck_sensor_antenna)),
        NAV(Expression.IDLE,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_pose_down),
                l(R.drawable.duck_nav_satchel), FACE, l(R.drawable.duck_nav_hat)),
        SLEEP(Expression.SLEEPING,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_pose_down), FACE,
                l(R.drawable.duck_sleep_cap), l(R.drawable.duck_fx_zzz)),
        CELEBRATE(Expression.WINK,
                doodle(R.drawable.duck_fx_sparkles), l(R.drawable.duck_shadow), l(R.drawable.duck_base),
                l(R.drawable.duck_pose_down), FACE),
        PLAIN(Expression.IDLE,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_pose_down), FACE),
        /** Orange bow tie + a flower, from the sticker sheet (characters.looks.profile_planned). */
        PROFILE(Expression.HAPPY,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_profile_flower),
                l(R.drawable.duck_pose_down), l(R.drawable.duck_profile_bowtie), FACE),
        /** Hold pose + a checklist notepad and pencil (characters.looks.cognitive_planned). */
        COGNITIVE(Expression.IDLE,
                l(R.drawable.duck_shadow), l(R.drawable.duck_base), l(R.drawable.duck_cognitive_notebook),
                l(R.drawable.duck_pose_hold), FACE);

        public final Expression defaultExpression;
        public final Layer[] layers;

        Look(Expression defaultExpression, Layer... layers) {
            this.defaultExpression = defaultExpression;
            this.layers = layers;
        }

        /** XML enum index (attrs.xml duckLook) → Look. */
        public static Look fromIndex(int i) {
            Look[] all = values();
            return i >= 0 && i < all.length ? all[i] : PLAIN;
        }
    }
}
