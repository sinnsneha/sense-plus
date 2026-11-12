package com.senseplus.sense.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;

import java.util.Random;

/**
 * Fine monochrome speckle that gives coloured surfaces the reference's risograph grain
 * (tokens.grain). One 128x128 tile (64 KB) is built once and shared; 1 texel = 1 device
 * pixel so the grain stays fine on every density. Never used on Pip, text, icons, white
 * cards or the camera preview.
 */
public final class GrainTexture {

    public static final float HERO = 0.8f;
    public static final float TILE = 0.55f;

    private static Bitmap tile;

    private GrainTexture() {
    }

    public static synchronized Bitmap bitmap() {
        if (tile == null) tile = build();
        return tile;
    }

    static Bitmap build() {
        int s = 128;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Random r = new Random(7);
        int[] px = new int[s * s];
        for (int i = 0; i < px.length; i++) {
            float p = r.nextFloat();
            if (p < 0.20f) {
                px[i] = Color.argb((int) (255 * (0.10f + r.nextFloat() * 0.12f)), 255, 255, 255);
            } else if (p < 0.40f) {
                px[i] = Color.argb((int) (255 * (0.05f + r.nextFloat() * 0.07f)), 40, 20, 60);
            } else {
                px[i] = Color.TRANSPARENT;
            }
        }
        b.setPixels(px, 0, s, 0, 0, s, s);
        return b;
    }

    /** A paint that repeats the grain tile at the given intensity (1.0 = full alpha). */
    public static Paint paint(float intensity) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(bitmap(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        p.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, intensity))));
        return p;
    }
}
