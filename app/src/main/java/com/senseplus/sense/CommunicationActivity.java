package com.senseplus.sense;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class CommunicationActivity extends AppCompatActivity {

    private TextToSpeech tts;

    private final String[] phrases = {
            "Help", "Yes", "No", "Thank you",
            "I need water", "I need the toilet",
            "I am in pain", "Please call my guardian"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_communication);
        getWindow().setStatusBarColor(Color.parseColor("#0D2B45"));

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        LinearLayout container = findViewById(R.id.phraseContainer);
        float density = getResources().getDisplayMetrics().density;
        int gap = (int) (8 * density);
        int minH = (int) (84 * density);

        for (String phrase : phrases) {
            Button b = new Button(this);
            b.setText(phrase);
            b.setTextSize(21);
            b.setAllCaps(false);
            b.setTextColor(0xFFFFFFFF);
            int color = phrase.equalsIgnoreCase("Help") ? 0xFFC62828 : 0xFF1565C0;
            b.setBackgroundTintList(ColorStateList.valueOf(color));
            b.setMinimumHeight(minH);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, gap, 0, gap);
            b.setLayoutParams(lp);

            b.setOnClickListener(v -> speak(phrase));
            container.addView(b);
        }
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