package com.senseplus.sense.ui;

import android.content.Context;

import androidx.annotation.ColorRes;

import com.senseplus.sense.R;

/**
 * Factories for the gradient tokens in tokens.color.gradients. Every colour comes from
 * colors.xml; a new drawable is returned on each call (drawables hold per-view state).
 */
public final class Gradients {

    /** A palette family: its 50 / 100 / 200 / 300 / 700 shades. */
    public enum Family {
        TANGERINE(R.color.tangerine_50, R.color.tangerine_100, R.color.tangerine_200, R.color.tangerine_300, R.color.tangerine_700),
        LAVENDER(R.color.lavender_50, R.color.lavender_100, R.color.lavender_200, R.color.lavender_300, R.color.lavender_700),
        BUTTER(R.color.butter_50, R.color.butter_100, R.color.butter_200, R.color.butter_300, R.color.butter_700),
        BLUSH(R.color.blush_50, R.color.blush_100, R.color.blush_200, R.color.blush_300, R.color.blush_700),
        MINT(R.color.mint_50, R.color.mint_100, R.color.mint_200, R.color.mint_300, R.color.mint_700),
        SKY(R.color.sky_50, R.color.sky_100, R.color.sky_200, R.color.sky_300, R.color.sky_700),
        CORAL(R.color.coral_50, R.color.coral_100, R.color.coral_200, R.color.coral_300, R.color.coral_700);

        @ColorRes public final int c50, c100, c200, c300, c700;

        Family(int c50, int c100, int c200, int c300, int c700) {
            this.c50 = c50;
            this.c100 = c100;
            this.c200 = c200;
            this.c300 = c300;
            this.c700 = c700;
        }
    }

    private Gradients() {
    }

    private static HeroBackgroundDrawable hero(Context c) {
        return new HeroBackgroundDrawable(c.getResources().getDisplayMetrics().density);
    }

    private static int col(Context c, @ColorRes int id) {
        return Ui.color(c, id);
    }

    // ---------- hero gradients ----------

    public static HeroBackgroundDrawable heroLavender(Context c) {
        return hero(c).base(col(c, R.color.grad_lavender_base))
                .blob(0.15f, 0.10f, 1.10f, col(c, R.color.lavender_50), 1.0f)
                .blob(0.85f, 0.55f, 0.75f, col(c, R.color.lavender_300), 0.9f)
                .blob(0.95f, 0.95f, 0.60f, col(c, R.color.tangerine_100), 0.55f)
                .grain(GrainTexture.HERO);
    }

    public static HeroBackgroundDrawable heroPeachSunrise(Context c) {
        return hero(c).base(col(c, R.color.tangerine_100))
                .blob(0.05f, 0.02f, 0.95f, col(c, R.color.grad_peach_blob_cream), 1.0f)
                .blob(0.55f, 0.35f, 0.80f, col(c, R.color.grad_peach_blob_peach), 0.9f)
                .blob(1.00f, 0.22f, 0.70f, col(c, R.color.tangerine_400), 0.85f)
                .blob(0.10f, 0.45f, 0.55f, col(c, R.color.blush_200), 0.6f)
                .fadeTo(0.48f, 0.62f, col(c, R.color.surface_canvas))
                .grain(GrainTexture.HERO);
    }

    public static HeroBackgroundDrawable heroButter(Context c) {
        return hero(c).base(col(c, R.color.butter_100))
                .blob(0.10f, 0.10f, 1.0f, col(c, R.color.butter_50), 1.0f)
                .blob(0.90f, 0.70f, 0.7f, col(c, R.color.butter_300), 0.8f)
                .grain(GrainTexture.HERO);
    }

    public static HeroBackgroundDrawable heroMint(Context c) {
        return hero(c).base(col(c, R.color.mint_100))
                .blob(0.10f, 0.10f, 1.0f, col(c, R.color.mint_50), 1.0f)
                .blob(0.90f, 0.80f, 0.7f, col(c, R.color.mint_300), 0.7f)
                .grain(GrainTexture.HERO);
    }

    public static HeroBackgroundDrawable heroSky(Context c) {
        return hero(c).base(col(c, R.color.sky_100))
                .blob(0.10f, 0.10f, 1.0f, col(c, R.color.sky_50), 1.0f)
                .blob(0.90f, 0.80f, 0.7f, col(c, R.color.sky_300), 0.7f)
                .grain(GrainTexture.HERO);
    }

    /** Navigation (coral family). Derived from grad_hero_sky/mint: 50 top-left, 300 bottom-right. */
    public static HeroBackgroundDrawable heroCoral(Context c) {
        return hero(c).base(col(c, R.color.coral_100))
                .blob(0.10f, 0.10f, 1.0f, col(c, R.color.coral_50), 1.0f)
                .blob(0.90f, 0.80f, 0.7f, col(c, R.color.coral_300), 0.7f)
                .grain(GrainTexture.HERO);
    }

    /** Profile (lavender 50-200 + cream, per sectionAccents.profileSettings). */
    public static HeroBackgroundDrawable heroProfile(Context c) {
        return hero(c).base(col(c, R.color.lavender_100))
                .blob(0.10f, 0.10f, 1.0f, col(c, R.color.lavender_50), 1.0f)
                .blob(0.90f, 0.80f, 0.7f, col(c, R.color.lavender_200), 0.8f)
                .blob(0.05f, 0.95f, 0.5f, col(c, R.color.surface_cream), 0.7f)
                .grain(GrainTexture.HERO);
    }

    /** Text sits on the #D92D20 base; the lighter blob stays behind Pip only. */
    public static HeroBackgroundDrawable heroEmergency(Context c) {
        return hero(c).base(col(c, R.color.status_emergency_solid))
                .blob(0.85f, 0.35f, 0.55f, col(c, R.color.grad_emergency_blob), 0.7f)
                .grain(0.5f);
    }

    public static HeroBackgroundDrawable heroOff(Context c) {
        return hero(c).base(col(c, R.color.ink_100))
                .blob(0.20f, 0.10f, 1.0f, col(c, R.color.ink_50), 1.0f)
                .blob(0.90f, 0.80f, 0.7f, col(c, R.color.lavender_200), 0.6f)
                .grain(0.6f);
    }

    // ---------- cards and tiles ----------

    /** grad_card_tangerine (Home SENSE GUARD card). */
    public static OrganicShapeDrawable cardTangerine(Context c) {
        OrganicShapeDrawable d = organic(c);
        d.diagonal(col(c, R.color.grad_tangerine_light), col(c, R.color.grad_tangerine_mid),
                col(c, R.color.tangerine_500)).grain(GrainTexture.HERO);
        return d;
    }

    /** grad_card_lavender (Home Guardian card). */
    public static OrganicShapeDrawable cardLavender(Context c) {
        OrganicShapeDrawable d = organic(c);
        d.diagonal(col(c, R.color.lavender_100), col(c, R.color.lavender_300),
                col(c, R.color.lavender_400)).grain(GrainTexture.HERO);
        return d;
    }

    /** Organic card filled with a hero gradient (Guardian status card). */
    public static OrganicShapeDrawable organicFrom(Context c, HeroPreset preset) {
        OrganicShapeDrawable d = organic(c);
        preset.fill(c, d);
        return d;
    }

    private static OrganicShapeDrawable organic(Context c) {
        return new OrganicShapeDrawable(c.getResources().getDisplayMetrics().density,
                c.getResources().getDimension(R.dimen.radius_hero));
    }

    /** grad_tile_family: {family}_50 → {family}_100 + a {family}_200 blob, grain 0.55, radius 28. */
    public static HeroBackgroundDrawable tile(Context c, Family f) {
        return hero(c).diagonal(col(c, f.c50), col(c, f.c100))
                .blob(0.95f, 0.95f, 0.7f, col(c, f.c200), 0.6f)
                .grain(GrainTexture.TILE)
                .rounded(c.getResources().getDimension(R.dimen.radius_2xl));
    }

    /** Hero gradients that can also fill an organic card. */
    public enum HeroPreset {
        LAVENDER, MINT, BUTTER, EMERGENCY, OFF;

        void fill(Context c, HeroBackgroundDrawable d) {
            switch (this) {
                case MINT:
                    d.base(col(c, R.color.mint_100))
                            .blob(0.10f, 0.10f, 1.0f, col(c, R.color.mint_50), 1.0f)
                            .blob(0.90f, 0.80f, 0.7f, col(c, R.color.mint_300), 0.7f)
                            .grain(GrainTexture.HERO);
                    break;
                case BUTTER:
                    d.base(col(c, R.color.butter_100))
                            .blob(0.10f, 0.10f, 1.0f, col(c, R.color.butter_50), 1.0f)
                            .blob(0.90f, 0.70f, 0.7f, col(c, R.color.butter_300), 0.8f)
                            .grain(GrainTexture.HERO);
                    break;
                case EMERGENCY:
                    d.base(col(c, R.color.status_emergency_solid))
                            .blob(0.15f, 0.75f, 0.45f, col(c, R.color.grad_emergency_blob), 0.7f)
                            .grain(0.5f);
                    break;
                case OFF:
                    d.base(col(c, R.color.ink_100))
                            .blob(0.20f, 0.10f, 1.0f, col(c, R.color.ink_50), 1.0f)
                            .blob(0.90f, 0.80f, 0.7f, col(c, R.color.lavender_200), 0.6f)
                            .grain(0.6f);
                    break;
                case LAVENDER:
                default:
                    d.base(col(c, R.color.grad_lavender_base))
                            .blob(0.15f, 0.10f, 1.10f, col(c, R.color.lavender_50), 1.0f)
                            .blob(0.85f, 0.55f, 0.75f, col(c, R.color.lavender_300), 0.9f)
                            .blob(0.95f, 0.95f, 0.60f, col(c, R.color.tangerine_100), 0.55f)
                            .grain(GrainTexture.HERO);
                    break;
            }
        }

        public HeroBackgroundDrawable hero(Context c) {
            HeroBackgroundDrawable d = Gradients.hero(c);
            fill(c, d);
            return d;
        }
    }
}
