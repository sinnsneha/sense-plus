package com.senseplus.sense;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.senseplus.sense.ui.DuckDrawable;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Lets the user say which kinds of difficulty apply to them. The choices are saved as
 * "assistance modes"; HomeActivity then shows only the cards that {@link ModeMapper} maps to them.
 * Opened automatically on first launch and from "Edit profile" on Home. Pressing Back without
 * saving changes nothing, so the user is never locked out of any tool.
 */
public class ProfileActivity extends AppCompatActivity {

    static final String PREFS = "senseplus_prefs";
    static final String KEY_PROFILE_DONE = "profile_done"; // boolean: the user pressed Save once
    static final String KEY_MODES = "modes";               // string set of ModeMapper.MODE_* keys

    // Row i in the layout stores mode i. Both arrays must stay in the same order.
    private static final int[] ROW_IDS = {
            R.id.cbVisual, R.id.cbHearing, R.id.cbCommunication, R.id.cbMobility, R.id.cbCognitive};
    private static final String[] ROW_MODES = {
            ModeMapper.MODE_VISUAL, ModeMapper.MODE_HEARING, ModeMapper.MODE_COMMUNICATION,
            ModeMapper.MODE_MOBILITY, ModeMapper.MODE_COGNITIVE};

    // The explanation under each label is drawn at this fraction of the label's size.
    private static final float EXPLANATION_SIZE = 0.8f;

    /**
     * The modes saved so far (empty if none). Always a copy: the set SharedPreferences
     * returns must not be modified.
     */
    static Set<String> loadModes(SharedPreferences prefs) {
        return new HashSet<>(prefs.getStringSet(KEY_MODES, Collections.<String>emptySet()));
    }

    private SharedPreferences prefs;

    // Pip's look for the tool each row adds, on that tool's family_100 backdrop (white card).
    private static final DuckLooks.Look[] ROW_LOOKS = {
            DuckLooks.Look.VISUAL, DuckLooks.Look.HEARING, DuckLooks.Look.COMM,
            DuckLooks.Look.NAV, DuckLooks.Look.COGNITIVE};
    private static final int[] ROW_BACKDROPS = {
            R.color.butter_100, R.color.blush_100, R.color.mint_100, R.color.coral_100, R.color.lavender_100};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.bottomBar));
        Ui.bindBack(this);
        findViewById(R.id.profileHero).setBackground(Gradients.heroProfile(this).wave());

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> saved = loadModes(prefs);

        int pipSize = getResources().getDimensionPixelSize(R.dimen.pip_row);
        for (int i = 0; i < ROW_IDS.length; i++) {
            CheckBox row = findViewById(ROW_IDS[i]);
            styleTwoLineText(row);
            Drawable[] d = row.getCompoundDrawablesRelative(); // keep the tick box at the end
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    new DuckDrawable(this, ROW_LOOKS[i], null, pipSize, false)
                            .backdrop(Ui.color(this, ROW_BACKDROPS[i])),
                    d[1], d[2], d[3]);
            // Set before the system restores view state, so a rotation keeps unsaved ticks.
            row.setChecked(saved.contains(ROW_MODES[i]));
        }

        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        Ui.pressFeedback(findViewById(R.id.btnSave));
    }

    private void save() {
        Set<String> chosen = new HashSet<>(); // a fresh set: putStringSet keeps the reference
        for (int i = 0; i < ROW_IDS.length; i++) {
            CheckBox row = findViewById(ROW_IDS[i]);
            if (row.isChecked()) chosen.add(ROW_MODES[i]);
        }
        prefs.edit()
                .putStringSet(KEY_MODES, chosen)
                .putBoolean(KEY_PROFILE_DONE, true)
                .apply();
        Toast.makeText(this, "Profile saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * The layout text is "label\nexplanation". Make the label bold and the explanation
     * smaller and muted. Spans scale with the user's font size; the text itself is unchanged,
     * so TalkBack reads label then explanation as the checkbox's name.
     */
    private void styleTwoLineText(CheckBox box) {
        CharSequence text = box.getText();
        int newline = text.toString().indexOf('\n');
        if (newline < 0) return;

        SpannableString styled = new SpannableString(text);
        styled.setSpan(new StyleSpan(Typeface.BOLD), 0, newline, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new RelativeSizeSpan(EXPLANATION_SIZE), newline + 1, text.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        styled.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.ink_600)),
                newline + 1, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        box.setText(styled);
    }
}
