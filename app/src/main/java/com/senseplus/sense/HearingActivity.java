package com.senseplus.sense;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hearing Assist — for deaf and hard-of-hearing users.
 *  Mode 1 SOUND ALERTS : YAMNet (TensorFlow Lite, on-device) classifies the microphone audio
 *                        every 0.5 s. Important sounds become a big coloured card,
 *                        a distinct vibration pattern and a flashlight blink.
 *  Mode 2 LIVE CAPTIONS: Vosk (offline speech recognition) turns nearby speech into large text.
 * Only one mode uses the microphone at a time.
 */
public class HearingActivity extends AppCompatActivity {

    private static final int REQ_MIC = 301;
    private static final float MIN_SCORE = 0.30f;              // ignore weak guesses
    private static final long SAME_SOUND_COOLDOWN_MS = 4000;   // don't repeat the same alert every 0.5 s
    private static final long CARD_RESET_MS = 6000;

    // ---------- the sounds we care about ----------
    private enum SoundType {
        FIRE_ALARM("🔥", "FIRE / SMOKE ALARM", "Smoke or fire alarm is ringing", "#B71C1C", new long[]{0, 800, 200, 800, 200, 800}, true),
        SIREN("🚨", "SIREN", "Emergency vehicle nearby", "#C62828", new long[]{0, 1000, 300, 1000}, true),
        HORN("🚗", "VEHICLE HORN", "A vehicle is honking — be careful", "#E65100", new long[]{0, 300, 100, 300, 100, 300}, true),
        ALARM("⏰", "ALARM", "An alarm is ringing", "#EF6C00", new long[]{0, 600, 200, 600}, true),
        DOORBELL("🚪", "DOORBELL / KNOCK", "Someone may be at the door", "#1565C0", new long[]{0, 200, 150, 200}, true),
        PHONE("📞", "PHONE RINGING", "A phone is ringing", "#2E7D32", new long[]{0, 400, 200, 400}, false),
        BABY("👶", "BABY CRYING", "A baby is crying", "#6A1B9A", new long[]{0, 500, 300, 500}, false),
        GLASS("💥", "GLASS BREAKING", "Something may have broken", "#4E342E", new long[]{0, 700}, false),
        SHOUT("📢", "SHOUTING", "Someone is shouting nearby", "#AD1457", new long[]{0, 300, 200, 300}, false),
        DOG("🐕", "DOG BARKING", "A dog is barking", "#455A64", new long[]{0, 250}, false);

        final String icon, title, detail, color;
        final long[] pattern;
        final boolean flash;

        SoundType(String icon, String title, String detail, String color, long[] pattern, boolean flash) {
            this.icon = icon; this.title = title; this.detail = detail;
            this.color = color; this.pattern = pattern; this.flash = flash;
        }
    }

    private enum Mode { SOUNDS, CAPTIONS }
    private Mode mode = Mode.SOUNDS;

    // UI
    private LinearLayout alertCard;
    private TextView tvAlertIcon, tvAlertTitle, tvAlertDetail, tvListening, tvFeedLabel, tvFeed;
    private ScrollView feedScroll;
    private Button btnSounds, btnCaptions;

    // sound classification (YAMNet)
    private AudioClassifier classifier;
    private TensorAudio tensorAudio;
    private AudioRecord audioRecord;
    private ScheduledExecutorService executor;
    private boolean soundRunning = false;
    private final Map<SoundType, Long> lastAlert = new HashMap<>();
    private final StringBuilder history = new StringBuilder();

    // live captions (Vosk)
    private Model voskModel;
    private SpeechService captionService;
    private final StringBuilder transcript = new StringBuilder();

    private Vibrator vibrator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean askedMic = false;

    private final Runnable resetCard = this::showIdleCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hearing);
        getWindow().setStatusBarColor(Color.parseColor("#0D2B45"));

        alertCard = findViewById(R.id.alertCard);
        tvAlertIcon = findViewById(R.id.tvAlertIcon);
        tvAlertTitle = findViewById(R.id.tvAlertTitle);
        tvAlertDetail = findViewById(R.id.tvAlertDetail);
        tvListening = findViewById(R.id.tvListening);
        tvFeedLabel = findViewById(R.id.tvFeedLabel);
        tvFeed = findViewById(R.id.tvFeed);
        feedScroll = findViewById(R.id.feedScroll);
        btnSounds = findViewById(R.id.btnSounds);
        btnCaptions = findViewById(R.id.btnCaptions);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        btnSounds.setOnClickListener(v -> { mode = Mode.SOUNDS; startCurrentMode(); });
        btnCaptions.setOnClickListener(v -> { mode = Mode.CAPTIONS; startCurrentMode(); });

        showIdleCard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCurrentMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSoundAlerts();
        stopCaptions();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (classifier != null) { try { classifier.close(); } catch (Exception ignored) { } classifier = null; }
        if (voskModel != null) { try { voskModel.close(); } catch (Exception ignored) { } voskModel = null; }
    }

    // ---------- mode switching ----------
    private void startCurrentMode() {
        if (!hasMic()) {
            if (!askedMic) {
                askedMic = true;
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            } else {
                tvListening.setText("Microphone permission is needed. Turn it on in Settings → Apps → SENSE+ → Permissions.");
            }
            return;
        }
        updateModeButtons();
        if (mode == Mode.SOUNDS) { stopCaptions(); startSoundAlerts(); }
        else { stopSoundAlerts(); startCaptions(); }
    }

    private void updateModeButtons() {
        btnSounds.setAlpha(mode == Mode.SOUNDS ? 1f : 0.45f);
        btnCaptions.setAlpha(mode == Mode.CAPTIONS ? 1f : 0.45f);
        if (mode == Mode.SOUNDS) {
            alertCard.setVisibility(View.VISIBLE);
            tvFeedLabel.setText("RECENT SOUNDS");
            tvFeed.setTextSize(16);
            tvFeed.setText(history.length() == 0 ? "No important sounds yet." : history.toString());
        } else {
            alertCard.setVisibility(View.GONE);
            tvFeedLabel.setText("LIVE CAPTIONS");
            tvFeed.setTextSize(26);
            tvFeed.setText(transcript.length() == 0 ? "Speak near the phone…" : transcript.toString());
        }
    }

    // ---------- MODE 1: sound alerts with YAMNet ----------
    private void startSoundAlerts() {
        if (soundRunning) return;
        try {
            if (classifier == null) classifier = AudioClassifier.createFromFile(this, "yamnet.tflite");
            tensorAudio = classifier.createInputTensorAudio();
            audioRecord = classifier.createAudioRecord();
            audioRecord.startRecording();
        } catch (Exception e) {
            tvListening.setText("Sound model not loaded — is yamnet.tflite in assets? (" + e.getMessage() + ")");
            return;
        }
        soundRunning = true;
        tvListening.setText("Listening for important sounds…");
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::classifyOnce, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void stopSoundAlerts() {
        soundRunning = false;
        if (executor != null) { executor.shutdownNow(); executor = null; }
        if (audioRecord != null) {
            try { audioRecord.stop(); audioRecord.release(); } catch (Exception ignored) { }
            audioRecord = null;
        }
    }

    // runs on the background thread every 500 ms
    private void classifyOnce() {
        try {
            if (!soundRunning || audioRecord == null) return;
            tensorAudio.load(audioRecord);
            List<Classifications> output = classifier.classify(tensorAudio);
            if (output == null || output.isEmpty()) return;

            Category top = null;
            SoundType best = null;
            float bestScore = 0f;
            for (Category c : output.get(0).getCategories()) {
                if (top == null || c.getScore() > top.getScore()) top = c;
                SoundType t = mapLabel(c.getLabel());
                if (t != null && c.getScore() >= MIN_SCORE && c.getScore() > bestScore) {
                    best = t;
                    bestScore = c.getScore();
                }
            }

            final Category topF = top;
            final SoundType bestF = best;
            final float scoreF = bestScore;
            runOnUiThread(() -> {
                if (!soundRunning) return;
                if (topF != null) tvListening.setText(String.format(Locale.US,
                        "Listening… now hearing: %s (%.0f%%)", topF.getLabel(), topF.getScore() * 100));
                if (bestF != null) onSoundDetected(bestF, scoreF);
            });
        } catch (Exception ignored) { }
    }

    // turn one of YAMNet's 521 AudioSet labels into one of our alert types
    private SoundType mapLabel(String label) {
        if (label == null) return null;
        String l = label.toLowerCase(Locale.US);
        if (l.contains("smoke detector") || l.contains("fire alarm")) return SoundType.FIRE_ALARM;
        if (l.contains("siren") || l.contains("emergency vehicle")) return SoundType.SIREN;
        if (l.contains("french horn")) return null;
        if (l.contains("vehicle horn") || l.contains("truck horn") || l.contains("honk") || l.contains("car alarm")) return SoundType.HORN;
        if (l.contains("doorbell") || l.contains("ding-dong") || l.equals("knock")) return SoundType.DOORBELL;
        if (l.contains("alarm") || l.contains("buzzer")) return SoundType.ALARM;
        if (l.contains("telephone") || l.contains("ringtone")) return SoundType.PHONE;
        if (l.contains("baby cry") || l.contains("crying")) return SoundType.BABY;
        if (l.contains("shatter") || l.equals("glass")) return SoundType.GLASS;
        if (l.contains("shout") || l.contains("yell") || l.contains("scream")) return SoundType.SHOUT;
        if (l.equals("dog") || l.contains("bark")) return SoundType.DOG;
        return null;
    }

    private void onSoundDetected(SoundType t, float score) {
        long now = System.currentTimeMillis();
        Long last = lastAlert.get(t);
        if (last != null && now - last < SAME_SOUND_COOLDOWN_MS) return;
        lastAlert.put(t, now);

        // 1) visual: big coloured card
        alertCard.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(t.color)));
        tvAlertIcon.setText(t.icon);
        tvAlertTitle.setText(t.title);
        tvAlertTitle.setTextColor(Color.WHITE);
        tvAlertDetail.setText(String.format(Locale.US, "%s · %.0f%% sure", t.detail, score * 100));
        tvAlertDetail.setTextColor(Color.parseColor("#F1F1F1"));
        alertCard.announceForAccessibility(t.title);

        // 2) touch: a different vibration rhythm for each sound
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createWaveform(t.pattern, -1));

        // 3) light: blink the flashlight for urgent sounds
        if (t.flash) flashTorch();

        // 4) history list
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        history.insert(0, time + "   " + t.icon + "  " + t.title + String.format(Locale.US, "  (%.0f%%)\n", score * 100));
        if (history.length() > 1500) history.setLength(1500);
        if (mode == Mode.SOUNDS) tvFeed.setText(history.toString());

        handler.removeCallbacks(resetCard);
        handler.postDelayed(resetCard, CARD_RESET_MS);
    }

    private void showIdleCard() {
        alertCard.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        tvAlertIcon.setText("👂");
        tvAlertTitle.setText("Listening for sounds");
        tvAlertTitle.setTextColor(ContextCompat.getColor(this, R.color.text_dark));
        tvAlertDetail.setText("Doorbell · alarms · sirens · horns · baby crying");
        tvAlertDetail.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
    }

    private void flashTorch() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String torchId = null;
            for (String id : cm.getCameraIdList()) {
                Boolean hasFlash = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) { torchId = id; break; }
            }
            if (torchId == null) return;
            final String id = torchId;
            for (int i = 0; i < 6; i++) {
                final boolean on = (i % 2 == 0);
                handler.postDelayed(() -> { try { cm.setTorchMode(id, on); } catch (Exception ignored) { } }, i * 250L);
            }
            handler.postDelayed(() -> { try { cm.setTorchMode(id, false); } catch (Exception ignored) { } }, 1600);
        } catch (Exception ignored) { }
    }

    // ---------- MODE 2: live captions with Vosk ----------
    private void startCaptions() {
        if (captionService != null) return;
        if (voskModel != null) { beginCaptions(); return; }
        tvListening.setText("Loading speech model…");
        StorageService.unpack(this, "model-en-us", "model",
                model -> { voskModel = model; beginCaptions(); },
                e -> tvListening.setText("Speech model not found (" + e.getMessage() + ")"));
    }

    private void beginCaptions() {
        if (mode != Mode.CAPTIONS || captionService != null) return;
        try {
            Recognizer rec = new Recognizer(voskModel, 16000.0f);
            captionService = new SpeechService(rec, 16000.0f);
            captionService.startListening(new RecognitionListener() {
                @Override public void onPartialResult(String h) { showCaption(read(h, "partial")); }
                @Override public void onResult(String h) { addFinal(read(h, "text")); }
                @Override public void onFinalResult(String h) { addFinal(read(h, "text")); }
                @Override public void onError(Exception e) { tvListening.setText("Caption error: " + e.getMessage()); }
                @Override public void onTimeout() { }
            });
            tvListening.setText("Live captions ON — speech becomes text on this phone (offline)");
        } catch (Exception e) {
            tvListening.setText("Could not start captions (" + e.getMessage() + ")");
        }
    }

    private void stopCaptions() {
        if (captionService != null) {
            try { captionService.stop(); captionService.shutdown(); } catch (Exception ignored) { }
            captionService = null;
        }
    }

    private String read(String json, String key) {
        try { return new JSONObject(json).optString(key, ""); } catch (Exception e) { return ""; }
    }

    private void addFinal(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String sentence = Character.toUpperCase(text.charAt(0)) + text.substring(1) + ".\n";
        transcript.append(sentence);
        if (transcript.length() > 3000) transcript.delete(0, transcript.length() - 3000);
        showCaption("");
    }

    private void showCaption(String partial) {
        if (mode != Mode.CAPTIONS) return;
        String shown = transcript.toString() + (partial == null || partial.isEmpty() ? "" : partial + " …");
        tvFeed.setText(shown.isEmpty() ? "Speak near the phone…" : shown);
        feedScroll.post(() -> feedScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ---------- permissions ----------
    private boolean hasMic() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCurrentMode();
            else tvListening.setText("Microphone permission is needed. Turn it on in Settings → Apps → SENSE+ → Permissions.");
        }
    }
}