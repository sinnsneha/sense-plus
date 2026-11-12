package com.senseplus.sense;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.TileGridLayout;
import com.senseplus.sense.ui.Ui;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommunicationActivity extends AppCompatActivity {

    private static final String PREFS = "senseplus_prefs";
    private static final String KEY_CUSTOM_PHRASES = "custom_phrases";
    private static final String TAG = "Communication";

    /** Phrase-card icon tiles cycle through these families (screens.communication). */
    private static final Gradients.Family[] TILE_FAMILIES = {
            Gradients.Family.MINT, Gradients.Family.BUTTER, Gradients.Family.BLUSH,
            Gradients.Family.SKY, Gradients.Family.LAVENDER};

    private TextToSpeech tts;
    private SharedPreferences prefs;
    private TileGridLayout container;
    private final List<String> customPhrases = new ArrayList<>();

    private int gap;
    private DuckView pip;
    private boolean helpSpeaking;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable backToHappy = () -> {
        helpSpeaking = false;
        pip.setExpression(DuckLooks.Expression.HAPPY, true);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communication);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.commContent));
        Ui.bindBack(this);
        findViewById(R.id.commHero).setBackground(Gradients.heroMint(this).wave());
        pip = findViewById(R.id.pipComm);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadCustomPhrases();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {
                runOnUiThread(() -> {
                    handler.removeCallbacks(backToHappy);
                    if (!helpSpeaking) pip.setTalking(true);
                });
            }

            @Override public void onDone(String utteranceId) {
                runOnUiThread(() -> afterSpeaking(1000));
            }

            @Override public void onError(String utteranceId) {
                runOnUiThread(() -> afterSpeaking(0));
            }
        });

        container = findViewById(R.id.phraseContainer);
        gap = Ui.dp(this, 8);

        renderPhrases();
    }

    /** Pip stops talking and stays happy (or alert after HELP) for a moment. */
    private void afterSpeaking(long holdMs) {
        pip.setTalking(false);
        handler.removeCallbacks(backToHappy);
        handler.postDelayed(backToHappy, holdMs);
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
        int index = 0;
        for (String phrase : PhraseRules.DEFAULT_PHRASES) {
            container.addView(createPhraseView(phrase, false, index++));
        }
        for (String phrase : customPhrases) {
            container.addView(createPhraseView(phrase, true, index++));
        }
        container.addView(createAddView());
    }

    private View createPhraseView(String phrase, boolean custom, int index) {
        LayoutInflater inflater = LayoutInflater.from(this);

        if (!custom && phrase.equalsIgnoreCase("Help")) {
            // HELP: first, full width, solid red (emergency style)
            Button help = (Button) inflater.inflate(R.layout.item_phrase_help, container, false);
            help.setText(phrase.toUpperCase(Locale.US));
            help.setContentDescription(phrase);
            help.setOnClickListener(v -> {
                helpSpeaking = true;
                pip.setTalking(false);
                pip.setExpression(DuckLooks.Expression.ALERT, true);
                speak(phrase);
            });
            Ui.pressFeedback(help);
            return help;
        }

        View card = inflater.inflate(R.layout.item_phrase, container, false);
        ((TextView) card.findViewById(R.id.phraseText)).setText(phrase);
        Gradients.Family family = TILE_FAMILIES[index % TILE_FAMILIES.length];
        FrameLayout tile = card.findViewById(R.id.phraseIconTile);
        ImageView icon = card.findViewById(R.id.phraseIcon);

        @DrawableRes int sticker = custom ? 0 : stickerFor(phrase);
        if (sticker != 0) {
            // Sticker-sheet duck (ducks.jpeg) on a family_100 backdrop: it's on a white card.
            int size = getResources().getDimensionPixelSize(R.dimen.pip_row);
            ViewGroup.LayoutParams lp = tile.getLayoutParams();
            lp.width = size;
            lp.height = size;
            tile.setLayoutParams(lp);
            icon.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), 0);
            icon.setImageResource(sticker);
            icon.setImageTintList(null);
        } else {
            icon.setImageResource(custom ? R.drawable.ic_star : iconFor(phrase));
            icon.setImageTintList(ColorStateList.valueOf(Ui.color(this, family.c700)));
        }
        tile.setBackgroundTintList(ColorStateList.valueOf(Ui.color(this, family.c100)));

        card.setContentDescription(custom ? getString(R.string.comm_custom_cd, phrase) : phrase);
        card.setOnClickListener(v -> speak(phrase));
        if (custom) {
            card.setOnLongClickListener(v -> {
                confirmDelete(phrase);
                return true;
            });
        }
        Ui.pressFeedback(card);
        return card;
    }

    /** Pip stickers cut from the sticker sheet, for the two phrases whose pose they show. */
    @DrawableRes
    private static int stickerFor(String phrase) {
        if (phrase.equalsIgnoreCase("Thank you")) return R.drawable.pip_sticker_thanks;
        if (phrase.equalsIgnoreCase("I need water")) return R.drawable.pip_sticker_drink;
        return 0;
    }

    /** Icon by phrase meaning (family_700 on the family_100 tile). */
    @DrawableRes
    private static int iconFor(String phrase) {
        String p = phrase.toLowerCase(Locale.US);
        if (p.equals("yes")) return R.drawable.ic_thumb_up;
        if (p.equals("no")) return R.drawable.ic_thumb_down;
        if (p.contains("water") || p.contains("drink")) return R.drawable.ic_water_drop;
        if (p.contains("toilet") || p.contains("bathroom")) return R.drawable.ic_restroom;
        if (p.contains("pain") || p.contains("hurt")) return R.drawable.ic_healing;
        if (p.contains("call")) return R.drawable.ic_call;
        if (p.contains("thank")) return R.drawable.ic_favorite;
        if (p.contains("medic") || p.contains("pill")) return R.drawable.ic_pill;
        return R.drawable.ic_chat_bubble;
    }

    private View createAddView() {
        View add = LayoutInflater.from(this).inflate(R.layout.item_phrase_add, container, false);
        add.setOnClickListener(v -> showAddPhraseDialog());
        Ui.pressFeedback(add);
        return add;
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

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
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
        new MaterialAlertDialogBuilder(this)
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
        handler.removeCallbacksAndMessages(null);
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}
