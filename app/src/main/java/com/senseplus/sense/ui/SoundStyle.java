package com.senseplus.sense.ui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import com.senseplus.sense.R;

/**
 * tokens.color.soundTypes: how each Hearing Assist sound looks. Names match
 * HearingActivity.SoundType. Urgent sounds turn the alert card into a solid colour with
 * white text (all ≥ 4.5:1); soft sounds keep a white card with a tinted icon tile.
 */
public enum SoundStyle {
    FIRE_ALARM(true, R.color.sound_fire_solid, R.color.sound_fire_tile, R.drawable.ic_sound_fire),
    SIREN(true, R.color.sound_siren_solid, R.color.sound_siren_tile, R.drawable.ic_sound_siren),
    HORN(true, R.color.sound_horn_solid, R.color.sound_horn_tile, R.drawable.ic_sound_horn),
    ALARM(true, R.color.sound_alarm_solid, R.color.sound_alarm_tile, R.drawable.ic_sound_alarm),
    DOORBELL(true, R.color.sound_doorbell_solid, R.color.sound_doorbell_tile, R.drawable.ic_sound_doorbell),
    PHONE(false, R.color.sound_phone_icon, R.color.sound_phone_tint, R.drawable.ic_sound_phone),
    BABY(false, R.color.sound_baby_icon, R.color.sound_baby_tint, R.drawable.ic_sound_baby),
    GLASS(false, R.color.sound_glass_icon, R.color.sound_glass_tint, R.drawable.ic_sound_glass),
    SHOUT(false, R.color.sound_shout_icon, R.color.sound_shout_tint, R.drawable.ic_sound_shout),
    DOG(false, R.color.sound_dog_icon, R.color.sound_dog_tint, R.drawable.ic_sound_dog);

    public final boolean urgent;
    /** Urgent: the card's solid colour (also the icon colour on the white tile). Soft: icon colour. */
    @ColorRes public final int strong;
    /** Light tint: the icon tile behind a soft sound's icon. */
    @ColorRes public final int tint;
    @DrawableRes public final int icon;

    SoundStyle(boolean urgent, int strong, int tint, int icon) {
        this.urgent = urgent;
        this.strong = strong;
        this.tint = tint;
        this.icon = icon;
    }

    /** Card background colour. */
    @ColorRes
    public int cardColor() {
        return urgent ? strong : R.color.surface_card;
    }

    /** Icon tile colour: white on an urgent card, the tint on a white card. */
    @ColorRes
    public int tileColor() {
        return urgent ? R.color.white : tint;
    }

    @ColorRes
    public int titleColor() {
        return urgent ? R.color.white : R.color.ink_900;
    }

    @ColorRes
    public int detailColor() {
        return urgent ? R.color.white : R.color.ink_700;
    }

    @ColorRes
    public int metaColor() {
        return urgent ? R.color.white : R.color.ink_500;
    }

    public static SoundStyle of(String soundTypeName) {
        return valueOf(soundTypeName);
    }
}
