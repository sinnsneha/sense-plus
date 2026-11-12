package com.senseplus.sense;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommunicationActivity extends AppCompatActivity {

    private static final String PREFS = "senseplus_prefs";
    private static final String KEY_CUSTOM_PHRASES = "custom_phrases";
    private static final String TAG = "Communication";

    private TextToSpeech tts;
    private SharedPreferences prefs;
    private LinearLayout container;
    private final List<String> customPhrases = new ArrayList<>();

    private int gap;
    private int minH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communication);
        getWindow().setStatusBarColor(Color.parseColor("#0D2B45"));

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadCustomPhrases();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        container = findViewById(R.id.phraseContainer);
        float density = getResources().getDisplayMetrics().density;
        gap = (int) (8 * density);
        minH = (int) (84 * density);

        renderPhrases();
    }

    private void loadCustomPhrases() {
        customPhrases.clear();
        String json = prefs.getString(KEY_CUSTOM_PHRASES, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String phrase = array.optString(i, "").trim();
                if (!phrase.isEmpty()) customPhrases.add(phrase);
            }
        } catch (JSONException e) {
            Log.w(TAG, "Ignoring malformed custom phrases", e);
        }
    }

    private void saveCustomPhrases() {
        JSONArray array = new JSONArray();
        for (String phrase : customPhrases) array.put(phrase);
        prefs.edit().putString(KEY_CUSTOM_PHRASES, array.toString()).apply();
    }

    private void renderPhrases() {
        container.removeAllViews();
        for (String phrase : PhraseRules.DEFAULT_PHRASES) {
            container.addView(createPhraseButton(phrase, false));
        }
        for (String phrase : customPhrases) {
            container.addView(createPhraseButton(phrase, true));
        }
        container.addView(createAddButton());
    }

    private Button createPhraseButton(String phrase, boolean custom) {
        Button button = new Button(this);
        button.setText(custom ? "★ " + phrase : phrase);
        button.setTextSize(21);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        int color;
        if (custom) {
            color = 0xFF00695C;
        } else {
            color = phrase.equalsIgnoreCase("Help") ? 0xFFC62828 : 0xFF1565C0;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setMinimumHeight(minH);
        button.setContentDescription(custom ? phrase + ", custom phrase" : phrase);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, gap, 0, gap);
        button.setLayoutParams(lp);

        button.setOnClickListener(v -> speak(phrase));
        if (custom) {
            button.setOnLongClickListener(v -> {
                confirmDelete(phrase);
                return true;
            });
        }
        return button;
    }

    private Button createAddButton() {
        Button button = new Button(this);
        button.setText("+ Add phrase");
        button.setTextSize(21);
        button.setAllCaps(false);
        button.setTextColor(0xFFFFFFFF);
        button.setBackgroundTintList(ColorStateList.valueOf(0xFF2E7D32));
        button.setMinimumHeight(minH);
        button.setContentDescription("Add a custom phrase");

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, gap, 0, gap);
        button.setLayoutParams(lp);

        button.setOnClickListener(v -> showAddPhraseDialog());
        return button;
    }

    private void showAddPhraseDialog() {
        EditText input = new EditText(this);
        input.setHint("Type a phrase to speak");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        int side = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(side, gap, side, 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add phrase")
                .setView(wrapper)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", (d, which) -> d.cancel())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String raw = input.getText().toString();
                    String error = PhraseRules.validate(raw, customPhrases);
                    if (error != null) {
                        input.setError(error);
                        return;
                    }
                    customPhrases.add(raw.trim());
                    saveCustomPhrases();
                    renderPhrases();
                    dialog.dismiss();
                    announce("Phrase added");
                }));
        dialog.show();
    }

    private void confirmDelete(String phrase) {
        new AlertDialog.Builder(this)
                .setTitle("Delete phrase")
                .setMessage("Delete '" + phrase + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    customPhrases.remove(phrase);
                    saveCustomPhrases();
                    renderPhrases();
                    announce("Phrase deleted");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void announce(String message) {
        container.announceForAccessibility(message);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "commId");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
