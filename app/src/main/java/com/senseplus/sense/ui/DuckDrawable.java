package com.senseplus.sense.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.senseplus.sense.ui.DuckLooks.Expression;
import com.senseplus.sense.ui.DuckLooks.Look;

/**
 * A still Pip for places where a View can't go: compound drawables (profile rows),
 * the snackbar head, list empty states. Optional backdrop circle (placementRules.backdrop:
 * white Pip on a white card always sits on a family_100 circle) and head-only crop.
 */
public class DuckDrawable extends Drawable {

    private final DuckRenderer renderer;
    private final int sizePx;
    private final boolean headOnly;
    private final Paint backdrop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path circle = new Path();
    private boolean hasBackdrop;

    public DuckDrawable(Context c, Look look, @Nullable Expression expression, int sizePx, boolean headOnly) {
        renderer = new DuckRenderer(c);
        renderer.setLook(look);
        renderer.setExpression(expression != null ? expression : look.defaultExpression);
        renderer.setHideDoodles(headOnly || sizePx < Ui.dp(c, 64));
        renderer.preload();
        this.sizePx = sizePx;
        this.headOnly = headOnly;
    }

    public DuckDrawable backdrop(int color) {
        hasBackdrop = true;
        backdrop.setColor(color);
        return this;
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        circle.reset();
        circle.addCircle(bounds.exactCenterX(), bounds.exactCenterY(),
                Math.min(bounds.width(), bounds.height()) / 2f, Path.Direction.CW);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        if (hasBackdrop) canvas.drawPath(circle, backdrop);
        if (headOnly) {
            int save = canvas.save();
            canvas.clipPath(circle);
            float size = b.height() / 0.55f;
            renderer.draw(canvas, b.exactCenterX() - size / 2f, b.top, size);
            canvas.restoreToCount(save);
        } else {
            float size = Math.min(b.width(), b.height());
            // Inset a little inside a backdrop circle so the feet don't touch its edge.
            float inset = hasBackdrop ? size * 0.06f : 0f;
            float s = size - inset * 2;
            renderer.draw(canvas, b.exactCenterX() - s / 2f, b.bottom - inset - s, s);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        backdrop.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // Pip keeps its own colours.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
