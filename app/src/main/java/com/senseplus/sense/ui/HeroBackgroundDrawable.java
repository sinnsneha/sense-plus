package com.senseplus.sense.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Soft "mesh" gradient: a base colour (or linear gradient) with up to four radial colour
 * blobs, an optional fade into the canvas colour, an optional shaped edge (rounded, wave or
 * organic wobble) and fine grain (tokens.color.gradients + tokens.grain).
 *
 * <p>Every layer is drawn with drawPath on the same shape, so edges stay anti-aliased
 * (clipPath is not anti-aliased on hardware canvases) and grain never spills outside.
 * Shaders and paths are rebuilt only in onBoundsChange.
 */
public class HeroBackgroundDrawable extends Drawable {

    public enum Edge { RECT, ROUNDED, WAVE, ORGANIC }

    /** A radial colour blob; centre is a fraction of the bounds, radius a fraction of the width. */
    public static final class Blob {
        final float cx, cy, r, alpha;
        final int color;

        public Blob(float cx, float cy, float r, int color, float alpha) {
            this.cx = cx;
            this.cy = cy;
            this.r = r;
            this.color = color;
            this.alpha = alpha;
        }
    }

    private final float density;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Blob> blobs = new ArrayList<>();
    private final List<Paint> blobPaints = new ArrayList<>();
    @Nullable private Paint grainPaint;
    protected final Path path = new Path();
    private final RectF rect = new RectF();

    private int baseColor;
    @Nullable private int[] linearColors;
    private boolean linearHorizontal;
    private float grain;
    private Edge edge = Edge.RECT;
    private float cornerPx;
    private float fadeFrom = -1f, fadeTo = -1f;
    private int fadeColor;
    private int alpha = 255;

    public HeroBackgroundDrawable(float density) {
        this.density = density;
    }

    // ---------- configuration (call before the drawable is shown) ----------

    public HeroBackgroundDrawable base(int color) {
        baseColor = color;
        linearColors = null;
        return this;
    }

    /** Linear gradient from the top-left to the bottom-right corner (the profile's "135°"). */
    public HeroBackgroundDrawable diagonal(int... colors) {
        linearColors = colors;
        linearHorizontal = false;
        return this;
    }

    /** Linear gradient from left to right (the profile's "0°"). */
    public HeroBackgroundDrawable horizontal(int... colors) {
        linearColors = colors;
        linearHorizontal = true;
        return this;
    }

    public HeroBackgroundDrawable blob(float cx, float cy, float r, int color, float alpha) {
        blobs.add(new Blob(cx, cy, r, color, alpha));
        return this;
    }

    public HeroBackgroundDrawable grain(float intensity) {
        grain = intensity;
        return this;
    }

    public HeroBackgroundDrawable rounded(float radiusPx) {
        edge = Edge.ROUNDED;
        cornerPx = radiusPx;
        return this;
    }

    /** Wave bottom edge (components.heroSheet.bottomEdge). */
    public HeroBackgroundDrawable wave() {
        edge = Edge.WAVE;
        return this;
    }

    public HeroBackgroundDrawable organic(float radiusPx) {
        edge = Edge.ORGANIC;
        cornerPx = radiusPx;
        return this;
    }

    /** Fades into {@code color} between the two height fractions (Hearing peach sunrise). */
    public HeroBackgroundDrawable fadeTo(float from, float to, int color) {
        fadeFrom = from;
        fadeTo = to;
        fadeColor = color;
        return this;
    }

    public Edge getEdge() {
        return edge;
    }

    public float getCornerPx() {
        return cornerPx;
    }

    // ---------- Drawable ----------

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rebuild(bounds);
    }

    private void rebuild(Rect b) {
        int w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        rect.set(b);

        if (linearColors != null) {
            basePaint.setShader(linearHorizontal
                    ? new LinearGradient(b.left, 0, b.right, 0, linearColors, null, Shader.TileMode.CLAMP)
                    : new LinearGradient(b.left, b.top, b.right, b.bottom, linearColors, null,
                    Shader.TileMode.CLAMP));
        } else {
            basePaint.setShader(null);
            basePaint.setColor(baseColor);
        }

        blobPaints.clear();
        for (Blob blob : blobs) {
            float cx = b.left + blob.cx * w;
            float cy = b.top + blob.cy * h;
            float r = Math.max(1f, blob.r * w);
            int a0 = Math.round(255 * blob.alpha);
            int[] colors = {
                    ColorUtils.setAlphaComponent(blob.color, a0),
                    ColorUtils.setAlphaComponent(blob.color, Math.round(a0 * 0.45f)),
                    ColorUtils.setAlphaComponent(blob.color, 0)};
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(cx, cy, r, colors, new float[]{0f, 0.6f, 1f},
                    Shader.TileMode.CLAMP));
            blobPaints.add(p);
        }

        if (fadeFrom >= 0f) {
            float y0 = b.top + fadeFrom * h, y1 = b.top + fadeTo * h;
            fadePaint.setShader(new LinearGradient(0, y0, 0, y1,
                    ColorUtils.setAlphaComponent(fadeColor, 0), fadeColor, Shader.TileMode.CLAMP));
        } else {
            fadePaint.setShader(null);
        }

        if (grain > 0f) {
            grainPaint = GrainTexture.paint(grain);
            if (fadeFrom >= 0f) {
                // Grain fades out with the colour so the canvas part stays clean.
                float y0 = b.top + fadeFrom * h, y1 = b.top + fadeTo * h;
                Shader mask = new LinearGradient(0, y0, 0, y1, 0xFF000000, 0x00000000,
                        Shader.TileMode.CLAMP);
                grainPaint.setShader(new ComposeShader(grainPaint.getShader(), mask,
                        PorterDuff.Mode.DST_IN));
            }
        } else {
            grainPaint = null;
        }

        buildPath(b);
        applyAlpha();
    }

    protected void buildPath(Rect b) {
        path.reset();
        float w = b.width(), h = b.height();
        switch (edge) {
            case ROUNDED:
                path.addRoundRect(rect, cornerPx, cornerPx, Path.Direction.CW);
                break;
            case WAVE: {
                // from (0, h-28dp) cubic to (w, h-20dp), controls (0.25w, h-4dp) and (0.55w, h-52dp)
                float l = b.left, t = b.top, r = b.right, btm = b.bottom;
                path.moveTo(l, t);
                path.lineTo(r, t);
                path.lineTo(r, btm - 20 * density);
                path.cubicTo(l + 0.55f * w, btm - 52 * density,
                        l + 0.25f * w, btm - 4 * density,
                        l, btm - 28 * density);
                path.close();
                break;
            }
            case ORGANIC:
                buildOrganic(b, w, h);
                break;
            case RECT:
            default:
                path.addRect(rect, Path.Direction.CW);
                break;
        }
    }

    /**
     * components.organicCard.shape: rounded rect (radius_hero) whose top edge sits 6dp
     * higher at the left than at the right and whose bottom edge bulges 6dp down at ~70%.
     */
    private void buildOrganic(Rect b, float w, float h) {
        float l = b.left, t = b.top, r = b.right, btm = b.bottom;
        float wb = 6 * density;
        float rad = Math.min(cornerPx, Math.min(w, h) / 2f - wb);
        float k = 0.5523f * rad;
        float bottom = btm - wb;          // bottom baseline; the bulge reaches btm
        float topRight = t + wb;          // top edge drops 6dp from left to right

        path.moveTo(l + rad, t);
        path.cubicTo(l + w * 0.40f, t, l + w * 0.62f, topRight, r - rad, topRight);
        path.cubicTo(r - rad + k, topRight, r, topRight + rad - k, r, topRight + rad);
        path.lineTo(r, bottom - rad);
        path.cubicTo(r, bottom - rad + k, r - rad + k, bottom, r - rad, bottom);
        path.cubicTo(l + w * 0.86f, bottom + wb * 1.33f, l + w * 0.52f, bottom + wb * 1.33f,
                l + rad, bottom);
        path.cubicTo(l + rad - k, bottom, l, bottom - rad + k, l, bottom - rad);
        path.lineTo(l, t + rad);
        path.cubicTo(l, t + rad - k, l + rad - k, t, l + rad, t);
        path.close();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (getBounds().isEmpty()) return;
        canvas.drawPath(path, basePaint);
        for (Paint p : blobPaints) canvas.drawPath(path, p);
        if (fadePaint.getShader() != null) canvas.drawPath(path, fadePaint);
        if (grainPaint != null) canvas.drawPath(path, grainPaint);
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        Rect b = getBounds();
        switch (edge) {
            case ROUNDED:
                outline.setRoundRect(b, cornerPx);
                break;
            case ORGANIC:
                if (Build.VERSION.SDK_INT >= 33) {
                    outline.setPath(path); // clips and casts shadows from API 33
                } else {
                    outline.setRoundRect(b, cornerPx);
                }
                break;
            default:
                outline.setRect(b);
                break;
        }
        outline.setAlpha(alpha / 255f);
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        applyAlpha();
        invalidateSelf();
    }

    private void applyAlpha() {
        basePaint.setAlpha(linearColors != null ? alpha : Math.round(alpha * (baseColor >>> 24) / 255f));
        fadePaint.setAlpha(alpha);
        for (Paint p : blobPaints) p.setAlpha(alpha);
        if (grainPaint != null) grainPaint.setAlpha(Math.round(alpha * grain));
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        basePaint.setColorFilter(colorFilter);
        fadePaint.setColorFilter(colorFilter);
        for (Paint p : blobPaints) p.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
