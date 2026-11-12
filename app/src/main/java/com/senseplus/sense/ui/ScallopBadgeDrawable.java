package com.senseplus.sense.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * components.scallopBadge: a circle with 12 soft bumps (the reference's cookie-edged
 * badge). Gradient + grain normally; a plain white fill for EMERGENCY.
 */
public class ScallopBadgeDrawable extends Drawable {

    private static final int BUMPS = 12;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private Paint grainPaint;
    private final Path path = new Path();
    private int startColor, endColor;
    private boolean solid;
    private int alpha = 255;

    public ScallopBadgeDrawable(int startColor, int endColor) {
        setGradient(startColor, endColor);
    }

    /** Gradient tangerine_300 → tangerine_500 with grain (normal states). */
    public void setGradient(int start, int end) {
        startColor = start;
        endColor = end;
        solid = false;
        rebuild(getBounds());
        invalidateSelf();
    }

    /** Plain fill without grain (white badge in EMERGENCY). */
    public void setSolid(int color) {
        startColor = color;
        endColor = color;
        solid = true;
        rebuild(getBounds());
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        rebuild(bounds);
    }

    private void rebuild(Rect b) {
        if (b.isEmpty()) return;
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float big = Math.min(b.width(), b.height()) / 2f;
        // Bump peaks land at ~0.984 * control radius; valleys at 0.92. Fit the peaks to bounds.
        float r = big / 0.99f;
        float control = r * 1.08f;
        float valley = r * 0.92f;
        path.reset();
        double step = Math.PI * 2 / BUMPS;
        for (int i = 0; i <= BUMPS; i++) {
            double aValley = -Math.PI / 2 + i * step;
            float vx = cx + (float) Math.cos(aValley) * valley;
            float vy = cy + (float) Math.sin(aValley) * valley;
            if (i == 0) {
                path.moveTo(vx, vy);
            } else {
                double aPeak = aValley - step / 2;
                float px = cx + (float) Math.cos(aPeak) * control;
                float py = cy + (float) Math.sin(aPeak) * control;
                path.quadTo(px, py, vx, vy);
            }
        }
        path.close();

        if (solid) {
            fill.setShader(null);
            fill.setColor(startColor);
            grainPaint = null;
        } else {
            fill.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom, startColor, endColor,
                    Shader.TileMode.CLAMP));
            grainPaint = GrainTexture.paint(GrainTexture.HERO);
        }
        applyAlpha();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(path, fill);
        if (grainPaint != null) canvas.drawPath(path, grainPaint);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        outline.setOval(getBounds());
        outline.setAlpha(alpha / 255f);
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        applyAlpha();
        invalidateSelf();
    }

    private void applyAlpha() {
        fill.setAlpha(alpha);
        if (grainPaint != null) grainPaint.setAlpha(Math.round(alpha * GrainTexture.HERO));
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
