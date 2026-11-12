package com.senseplus.sense.ui;

import android.content.Context;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * A TextView for big single values (battery 80%, 14:32, 1234 lux) that shrinks its text
 * just enough to fit the width instead of breaking a number across lines. Height stays
 * wrap_content, so large font scales still grow the view vertically. Never shrinks below
 * 45% of the requested size.
 */
public class FitTextView extends AppCompatTextView {

    private float wantedPx = -1f;
    private boolean fitting;
    private final TextPaint probe = new TextPaint();

    public FitTextView(Context context) {
        super(context);
    }

    public FitTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FitTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setTextSize(int unit, float size) {
        super.setTextSize(unit, size);
        if (!fitting) wantedPx = getTextSize();
    }

    @Override
    public void setTextAppearance(int resId) {
        super.setTextAppearance(resId);
        wantedPx = getTextSize();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int avail = MeasureSpec.getSize(widthMeasureSpec)
                    - getCompoundPaddingLeft() - getCompoundPaddingRight();
            fit(avail);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void fit(int avail) {
        if (wantedPx < 0) wantedPx = getTextSize();
        CharSequence text = getText();
        if (avail <= 0 || text == null || text.length() == 0) return;
        probe.set(getPaint());
        probe.setTextSize(wantedPx);
        float widest = 0f;
        for (String line : text.toString().split("\n")) {
            widest = Math.max(widest, probe.measureText(line));
        }
        float target = wantedPx;
        if (widest > avail) target = Math.max(wantedPx * 0.45f, wantedPx * avail / widest * 0.98f);
        if (Math.abs(getTextSize() - target) > 0.5f) {
            fitting = true;
            super.setTextSize(TypedValue.COMPLEX_UNIT_PX, target);
            fitting = false;
        }
    }
}
