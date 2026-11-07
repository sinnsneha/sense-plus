package com.senseplus.sense;

import android.content.res.ColorStateList;
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

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        LinearLayout container = findViewById(R.id.phraseContainer);
        int gap = (int) (12 * getResources().getDisplayMetrics().density);
        int minH = (int) (80 * getResources().getDisplayMetrics().density);

        for (String phrase : phrases) {
            Button b = new Button(this);
            b.setText(phrase);
            b.setTextSize(22);
            b.setAllCaps(false);
            b.setTextColor(0xFFFFFFFF);
            b.setBackgroundTintList(ColorStateList.valueOf(0xFF1F4E79));
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