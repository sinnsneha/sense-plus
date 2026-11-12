package com.senseplus.sense;

import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.senseplus.sense.ui.Ui;

import java.util.Locale;

/**
 * Medical ID, "Show to a helper" (segment 26): a READ-ONLY screen with very large, high-contrast
 * text and a Read aloud button, for a bystander or paramedic who finds the user.
 *
 * It is shown over the lock screen, so it can be read without unlocking the phone. That is the
 * same trade-off as the phone's own Medical ID: anyone holding a locked phone, who can open this
 * screen, can read what is saved here. For that reason it is the ONLY screen that does this, it
 * has nothing to edit or send, and it reads the copy saved on the phone (no network, no sign-in
 * round trip). The editable form is MedicalIdActivity, which is not shown over the lock screen.
 *
 * It is opened from the "Medical ID" button on the possible-emergency notification, and from
 * "Show to a helper" on the Medical ID screen.
 */
public class MedicalHelperActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private MedicalId medical = MedicalId.empty();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        showOverLockScreen();

        setContentView(R.layout.activity_medical_helper);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.bottomBar));
        Ui.bindBack(this);

        medical = MedicalStore.load(this);
        LinearLayout rows = findViewById(R.id.helperRows);
        int shown = MedicalViews.fill(this, rows, medical, true);
        TextView empty = findViewById(R.id.tvHelperEmpty);
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);

        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            // The words are English, so use an English voice whatever the phone's language is.
            tts.setLanguage(Locale.US);
            ttsReady = true;
        });

        View readAloud = findViewById(R.id.btnReadAloud);
        readAloud.setOnClickListener(v -> readAloud());
        Ui.pressFeedback(readAloud);
    }

    /** setShowWhenLocked on API 27+, the window flag on API 26 (the project's minSdk). */
    @SuppressWarnings("deprecation")
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    private void readAloud() {
        if (!ttsReady || tts == null) {
            Toast.makeText(this, R.string.medical_tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        // Flush: pressing the button again starts over instead of queueing a second reading.
        tts.speak(EmergencyText.spokenMedicalId(medical), TextToSpeech.QUEUE_FLUSH, null, "medical-id");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
