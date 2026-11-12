package com.senseplus.sense;

import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.senseplus.sense.ui.DuckDrawable;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

/**
 * Debug-only Pip gallery (characters.implementation.debugGallery): every look at 240dp on its
 * section gradient (animated), 96dp and 48dp on white with a backdrop, and every expression.
 * Opened by long-pressing the Home greeting in debuggable builds.
 */
public class CharacterGalleryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.color(this, R.color.surface_canvas));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int gutter = Ui.dp(this, 20);
        column.setPadding(gutter, gutter, gutter, gutter);
        scroll.addView(column);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setTextAppearance(R.style.TextAppearance_SENSE_HeadlineL);
        title.setText(R.string.gallery_title);
        Ui.setHeading(title);
        column.addView(title);
        Ui.applyInsets(title, column);

        for (DuckLooks.Look look : DuckLooks.Look.values()) {
            TextView label = new TextView(this);
            label.setTextAppearance(R.style.TextAppearance_SENSE_TitleM);
            label.setText(look.name().toLowerCase(java.util.Locale.US) + " look");
            label.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 8));
            column.addView(label);

            HorizontalScrollView hs = new HorizontalScrollView(this);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.BOTTOM);
            hs.addView(row);
            column.addView(hs);

            // 240dp on the section gradient, animated
            FrameLayout big = new FrameLayout(this);
            big.setBackground(Gradients.tile(this, familyFor(look)));
            DuckView duck = new DuckView(this);
            duck.setLook(look);
            big.addView(duck, new FrameLayout.LayoutParams(Ui.dp(this, 240), Ui.dp(this, 240)));
            row.addView(big);

            // 96dp and 48dp on white with a family_100 backdrop
            row.addView(still(look, look.defaultExpression, 96, true));
            row.addView(still(look, look.defaultExpression, 48, true));

            // every expression at 96dp
            for (DuckLooks.Expression e : DuckLooks.Expression.values()) {
                row.addView(still(look, e, 96, false));
            }
        }
    }

    private ImageView still(DuckLooks.Look look, DuckLooks.Expression e, int dp, boolean backdrop) {
        int px = Ui.dp(this, dp);
        DuckDrawable d = new DuckDrawable(this, look, e, px, false);
        if (backdrop) d.backdrop(Ui.color(this, familyFor(look).c100));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(d);
        iv.setContentDescription(look.name() + " " + e.name());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px, px);
        lp.setMarginStart(Ui.dp(this, 12));
        iv.setLayoutParams(lp);
        return iv;
    }

    private static Gradients.Family familyFor(DuckLooks.Look look) {
        switch (look) {
            case GUARD:
                return Gradients.Family.TANGERINE;
            case GUARDIAN:
            case COMM:
                return Gradients.Family.MINT;
            case VISUAL:
            case CELEBRATE:
            case COGNITIVE:
                return Gradients.Family.BUTTER;
            case HEARING:
                return Gradients.Family.BLUSH;
            case SENSOR:
            case SLEEP:
                return Gradients.Family.SKY;
            case NAV:
                return Gradients.Family.CORAL;
            case HOME:
            case PROFILE:
            case PLAIN:
            default:
                return Gradients.Family.LAVENDER;
        }
    }
}
