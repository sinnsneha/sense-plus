package com.senseplus.sense.ui;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;

/**
 * components.organicCard: rounded rect (radius_hero) with a gentle wobble, filled with a
 * diagonal gradient + grain. {@link #clipChildren(View)} clips the card's children (Pip
 * peeking out of the bottom-left) to the shape: a true path on API 33+, the rounded rect
 * before that (path outlines only clip from API 33).
 */
public class OrganicShapeDrawable extends HeroBackgroundDrawable {

    public OrganicShapeDrawable(float density, float radiusPx) {
        super(density);
        organic(radiusPx);
    }

    /** Sets this drawable as the view's background and clips the view's children to it. */
    public void applyTo(View card) {
        card.setBackground(this);
        clipChildren(card);
    }

    public void clipChildren(View card) {
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, @NonNull Outline outline) {
                OrganicShapeDrawable.this.setBounds(0, 0, view.getWidth(), view.getHeight());
                OrganicShapeDrawable.this.getOutline(outline);
            }
        });
        card.setClipToOutline(true);
    }
}
