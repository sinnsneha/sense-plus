package com.senseplus.sense.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.senseplus.sense.ui.DuckLooks.Effect;
import com.senseplus.sense.ui.DuckLooks.Expression;
import com.senseplus.sense.ui.DuckLooks.Layer;
import com.senseplus.sense.ui.DuckLooks.Look;

/**
 * Draws one Pip look (layer stack) at a given size. Drawables are resolved once per
 * renderer and mutated, so per-layer alpha (e.g. pulsing sound waves) never leaks into
 * other views. Shared by {@link DuckView} (animated) and {@link DuckDrawable} (static).
 */
public final class DuckRenderer {

    private static final float UNITS = 240f;

    private final Context context;
    private final SparseArray<Drawable> cache = new SparseArray<>();
    private final SparseArray<Float> layerAlpha = new SparseArray<>();
    private final SparseArray<Boolean> layerHidden = new SparseArray<>();

    private Look look = Look.PLAIN;
    private Expression expression = Expression.IDLE;
    @Nullable private Expression faceOverride;
    private Effect effect = Effect.NONE;
    private boolean hideDoodles;

    public DuckRenderer(Context context) {
        this.context = context;
    }

    public void setLook(Look look) {
        this.look = look;
    }

    public Look getLook() {
        return look;
    }

    public void setExpression(Expression e) {
        expression = e;
    }

    public Expression getExpression() {
        return expression;
    }

    /** Temporary face (blink frame, talk frame); null to show the real expression. */
    public void setFaceOverride(@Nullable Expression e) {
        faceOverride = e;
    }

    public void setEffect(Effect e) {
        effect = e;
    }

    public Effect getEffect() {
        return effect;
    }

    public void setHideDoodles(boolean hide) {
        hideDoodles = hide;
    }

    public void setLayerAlpha(@DrawableRes int res, float alpha) {
        layerAlpha.put(res, alpha);
    }

    public void setLayerVisible(@DrawableRes int res, boolean visible) {
        layerHidden.put(res, !visible);
    }

    /** Resolves every drawable the current look can need, off the draw path. */
    public void preload() {
        for (Layer layer : look.layers) {
            if (layer.isFace()) {
                get(expression.res);
                get(Expression.SLEEPING.res);
                get(Expression.TALK.res);
                get(Expression.IDLE.res);
            } else if (layer.isFx()) {
                if (effect.res != 0) get(effect.res);
            } else {
                get(layer.res);
            }
        }
    }

    @Nullable
    private Drawable get(int res) {
        if (res == 0) return null;
        Drawable d = cache.get(res);
        if (d == null) {
            Drawable raw = AppCompatResources.getDrawable(context, res);
            if (raw == null) return null;
            d = raw.mutate();
            cache.put(res, d);
        }
        return d;
    }

    /**
     * Draws the look into a square of {@code size} px whose top-left is at (left, top).
     * k = size / 240 canvas units.
     */
    public void draw(Canvas canvas, float left, float top, float size) {
        int s = Math.round(size);
        if (s <= 0) return;
        float k = size / UNITS;
        int save = canvas.save();
        canvas.translate(left, top);
        for (Layer layer : look.layers) {
            int res;
            if (layer.isFace()) {
                res = (faceOverride != null ? faceOverride : expression).res;
            } else if (layer.isFx()) {
                res = effect.res;
            } else {
                res = layer.res;
            }
            if (res == 0) continue;
            if (layer.doodle && hideDoodles) continue;
            Boolean hidden = layerHidden.get(res);
            if (hidden != null && hidden) continue;
            Drawable d = get(res);
            if (d == null) continue;
            Float a = layerAlpha.get(res);
            d.setAlpha(a == null ? 255 : Math.round(255 * Math.max(0f, Math.min(1f, a))));
            d.setBounds(0, 0, s, s);
            if (layer.parent) {
                int p = canvas.save();
                canvas.translate(40 * k, 33 * k);
                canvas.scale(0.84f, 0.84f);
                d.draw(canvas);
                canvas.restoreToCount(p);
            } else {
                d.draw(canvas);
            }
        }
        canvas.restoreToCount(save);
    }
}
