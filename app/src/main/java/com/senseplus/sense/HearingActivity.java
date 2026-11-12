package com.senseplus.sense;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.PulseRingView;
import com.senseplus.sense.ui.SoundStyle;
import com.senseplus.sense.ui.Ui;

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
 *
 * Segment 28: while this screen is open, the voice keywords of SENSE GUARD step aside (the
 * activity sends PAUSE / RESUME to UserMonitorService); fall detection, location and the
 * heartbeat keep running, so using captions does not switch off protection.
 */
public class HearingActivity extends AppCompatActivity {

    /**
     * True while this screen is in the foreground (onResume to onPause). UserMonitorService reads
     * it when monitoring starts, so keywords started while Hearing Assist is open wait for it to close.
     */
    private static volatile boolean open = false;

    public static boolean isOpen() {
        return open;
    }

    private static final int REQ_MIC = 301;
    private static final float MIN_SCORE = 0.30f;              // ignore weak guesses
    private static final long SAME_SOUND_COOLDOWN_MS = 4000;   // don't repeat the same alert every 0.5 s
    private static final long CARD_RESET_MS = 6000;
    private static final long CAPTION_ALERT_MS = 2000;         // Pip stays alert this long after speech

    // ---------- the sounds we care about ----------
    // Colours and icons come from ui.SoundStyle (design tokens); the name must match.
    private enum SoundType {
        FIRE_ALARM("Fire / smoke alarm", "Smoke or fire alarm is ringing", new long[]{0, 800, 200, 800, 200, 800}, true),
        SIREN("Siren", "Emergency vehicle nearby", new long[]{0, 1000, 300, 1000}, true),
        HORN("Vehicle horn", "A vehicle is honking — be careful", new long[]{0, 300, 100, 300, 100, 300}, true),
        ALARM("Alarm", "An alarm is ringing", new long[]{0, 600, 200, 600}, true),
        DOORBELL("Doorbell / knock", "Someone may be at the door", new long[]{0, 200, 150, 200}, true),
        PHONE("Phone ringing", "A phone is ringing", new long[]{0, 400, 200, 400}, false),
        BABY("Baby crying", "A baby is crying", new long[]{0, 500, 300, 500}, false),
        GLASS("Glass breaking", "Something may have broken", new long[]{0, 700}, false),
        SHOUT("Shouting", "Someone is shouting nearby", new long[]{0, 300, 200, 300}, false),
        DOG("Dog barking", "A dog is barking", new long[]{0, 250}, false);

        final String title, detail;
        final long[] pattern;
        final boolean flash;

        SoundType(String title, String detail, long[] pattern, boolean flash) {
            this.title = title; this.detail = detail;
            this.pattern = pattern; this.flash = flash;
        }
    }

    private enum Mode { SOUNDS, CAPTIONS }
    private Mode mode = Mode.SOUNDS;

    // UI
    private LinearLayout alertCard;
    private ImageView tvAlertIcon;
    private TextView tvAlertTitle, tvAlertDetail, tvListening, tvFeedLabel, tvFeed;
    private ScrollView feedScroll;
    private Button btnSounds, btnCaptions;

    // redesign
    private DuckView pip;
    private ImageView soundTypeBadge;
    private View alertIconTile, ghostCard1, ghostCard2;
    private TextView tvAlertMeta;
    private PulseRingView alertRings;
    private float cardRotation;

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
    private final Runnable pipCalm = () -> {
        if (pip != null) pip.setExpression(DuckLooks.Expression.IDLE, true);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hearing);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.hearingContent));
        Ui.bindBack(this);
        findViewById(R.id.hearingRoot).setBackground(Gradients.heroPeachSunrise(this));

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

        pip = findViewById(R.id.pipHearing);
        soundTypeBadge = findViewById(R.id.soundTypeBadge);
        alertIconTile = findViewById(R.id.alertIconTile);
        ghostCard1 = findViewById(R.id.ghostCard1);
        ghostCard2 = findViewById(R.id.ghostCard2);
        tvAlertMeta = findViewById(R.id.tvAlertMeta);
        alertRings = findViewById(R.id.alertRings);

        // components.cardDeck: at large font sizes the main card stands straight for readability.
        cardRotation = getResources().getConfiguration().fontScale >= 1.3f ? 0f : -2.5f;
        alertCard.setRotation(cardRotation);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        btnSounds.setOnClickListener(v -> { mode = Mode.SOUNDS; startCurrentMode(); });
        btnCaptions.setOnClickListener(v -> { mode = Mode.CAPTIONS; startCurrentMode(); });
        Ui.pressFeedback(btnSounds, btnCaptions);

        showIdleCard();
    }

    @Override
    protected void onResume() {
        super.onResume();
        open = true;
        // Ask the voice keywords to let go of the microphone BEFORE this screen takes it.
        sendVoiceAction(UserMonitorService.ACTION_PAUSE_VOICE);
        startCurrentMode();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopSoundAlerts();
        stopCaptions();
        open = false;
        // The microphone is free again: the keywords may listen.
        sendVoiceAction(UserMonitorService.ACTION_RESUME_VOICE);
    }

    /**
     * Tells the monitoring service to pause or resume its voice keywords, but only if it is
     * running. Plain startService, not the foreground-service start: this screen is in the
     * foreground, so it is allowed, and a service started the foreground way must call
     * startForeground even when there is nothing to do, which crashed in segment 16. With
     * monitoring off, nothing is started and nothing happens.
     */
    private void sendVoiceAction(String action) {
        if (!UserMonitorService.isRunning()) return;
        try {
            startService(new Intent(this, UserMonitorService.class).setAction(action));
        } catch (RuntimeException e) {
            // The service was stopped between the check and the call; there is nothing to pause.
        }
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
                setListening("Microphone permission is needed. Turn it on in Settings → Apps → SENSE+ → Permissions.", true);
            }
            return;
        }
        updateModeButtons();
        if (mode == Mode.SOUNDS) { stopCaptions(); startSoundAlerts(); }
        else { stopSoundAlerts(); startCaptions(); }
    }

    private void updateModeButtons() {
        boolean sounds = mode == Mode.SOUNDS;
        btnSounds.setSelected(sounds);
        btnCaptions.setSelected(!sounds);
        Ui.setStateDescription(btnSounds, getString(sounds ? R.string.selected : R.string.not_selected));
        Ui.setStateDescription(btnCaptions, getString(sounds ? R.string.not_selected : R.string.selected));
        int deck = sounds ? View.VISIBLE : View.GONE;
        ghostCard1.setVisibility(deck);
        ghostCard2.setVisibility(deck);
        if (sounds) {
            alertCard.setVisibility(View.VISIBLE);
            tvFeedLabel.setText(R.string.hearing_recent);
            tvFeed.setTextSize(16);
            tvFeed.setTextColor(Ui.color(this, R.color.ink_800));
            tvFeed.setText(history.length() == 0 ? getString(R.string.hearing_no_sounds) : history.toString());
        } else {
            alertCard.setVisibility(View.GONE);
            alertRings.setActive(false);
            soundTypeBadge.setVisibility(View.GONE);
            tvFeedLabel.setText(R.string.hearing_live_captions);
            tvFeed.setTextSize(26);
            tvFeed.setTextColor(Ui.color(this, R.color.ink_900));
            tvFeed.setText(transcript.length() == 0 ? getString(R.string.hearing_speak_near) : transcript.toString());
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
            setListening("Sound model not loaded — is yamnet.tflite in assets? (" + e.getMessage() + ")", true);
            return;
        }
        soundRunning = true;
        setListening("Listening for important sounds…", false);
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
                if (topF != null) setListening(String.format(Locale.US,
                        "Listening… now hearing: %s (%.0f%%)", topF.getLabel(), topF.getScore() * 100), false);
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

        // 1) visual: big coloured card (urgent = solid colour + white text; soft = white card)
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        showSoundCard(t, score, time);
        alertCard.announceForAccessibility(t.title);

        // 2) touch: a different vibration rhythm for each sound
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createWaveform(t.pattern, -1));

        // 3) light: blink the flashlight for urgent sounds
        if (t.flash) flashTorch();

        // 4) history list
        history.insert(0, time + "  ·  " + t.title + String.format(Locale.US, "  ·  %.0f%%\n", score * 100));
        if (history.length() > 1500) history.setLength(1500);
        if (mode == Mode.SOUNDS) tvFeed.setText(history.toString());

        handler.removeCallbacks(resetCard);
        handler.postDelayed(resetCard, CARD_RESET_MS);
    }

    // ---------- presentation (SoundStyle + Pip) ----------

    /** Applies tokens.color.soundTypes to the deck, then pops the card and wakes Pip up. */
    private void showSoundCard(SoundType t, float score, String time) {
        SoundStyle s = SoundStyle.of(t.name());
        alertCard.setBackgroundTintList(ColorStateList.valueOf(Ui.color(this, s.cardColor())));
        alertIconTile.setBackgroundTintList(ColorStateList.valueOf(Ui.color(this, s.tileColor())));
        tvAlertIcon.setImageResource(s.icon);
        tvAlertIcon.setImageTintList(ColorStateList.valueOf(Ui.color(this, s.strong)));
        tvAlertTitle.setText(t.title);
        tvAlertTitle.setTextColor(Ui.color(this, s.titleColor()));
        tvAlertDetail.setText(t.detail);
        tvAlertDetail.setTextColor(Ui.color(this, s.detailColor()));
        tvAlertMeta.setText(String.format(Locale.US, "%s  ——  %.0f%% sure", time, score * 100));
        tintMeta(s.metaColor());
        tvAlertMeta.setVisibility(View.VISIBLE);

        alertRings.setRingColor(Ui.color(this, s.strong));
        alertRings.setActive(s.urgent);

        soundTypeBadge.setImageResource(s.icon);
        soundTypeBadge.setImageTintList(ColorStateList.valueOf(Ui.color(this, s.strong)));
        soundTypeBadge.setVisibility(View.VISIBLE);

        handler.removeCallbacks(pipCalm);
        pip.setLayerVisible(R.drawable.duck_hearing_waves, true);
        pip.setWavesPulsing(false); // waves at full opacity while a sound is shown
        pip.setExpression(DuckLooks.Expression.ALERT, true);
        alertPop();
    }

    /** motion.patterns.alertPop: scale 0.9 → 1.04 → 1, rotation −2.5° → −4° → −2.5°. */
    private void alertPop() {
        if (Ui.isReducedMotion()) return;
        alertCard.setScaleX(0.9f);
        alertCard.setScaleY(0.9f);
        alertCard.animate().scaleX(1f).scaleY(1f).setDuration(360)
                .setInterpolator(new OvershootInterpolator(1.4f)).start();
        if (cardRotation != 0f) {
            ObjectAnimator.ofFloat(alertCard, View.ROTATION, cardRotation, -4f, cardRotation)
                    .setDuration(360).start();
        }
    }

    private void tintMeta(@ColorRes int color) {
        int c = Ui.color(this, color);
        tvAlertMeta.setTextColor(c);
        for (Drawable d : tvAlertMeta.getCompoundDrawablesRelative()) {
            if (d != null) d.mutate().setTintList(ColorStateList.valueOf(c));
        }
    }

    /** Status chip under the deck; errors get the emergency tint and a worried Pip. */
    private void setListening(String text, boolean error) {
        tvListening.setText(text);
        tvListening.setBackgroundResource(error ? R.drawable.bg_listening_chip_error : R.drawable.bg_listening_chip);
        tvListening.setTextColor(Ui.color(this, error ? R.color.status_emergency_text : R.color.ink_700));
        Drawable dot = tvListening.getCompoundDrawablesRelative()[0];
        if (dot != null) {
            dot.mutate().setTintList(ColorStateList.valueOf(
                    Ui.color(this, error ? R.color.live_dot_error : R.color.live_dot_on)));
        }
        if (error) {
            handler.removeCallbacks(pipCalm);
            pip.setWavesPulsing(false);
            pip.setLayerVisible(R.drawable.duck_hearing_waves, false);
            pip.setExpression(DuckLooks.Expression.CONCERNED, true);
        } else if (pip.getExpression() == DuckLooks.Expression.CONCERNED) {
            pip.setLayerVisible(R.drawable.duck_hearing_waves, true);
            pip.setWavesPulsing(true);
            pip.setExpression(DuckLooks.Expression.IDLE, true);
        }
    }

    private void showIdleCard() {
        alertCard.setBackgroundTintList(ColorStateList.valueOf(Ui.color(this, R.color.surface_card)));
        alertIconTile.setBackgroundTintList(ColorStateList.valueOf(Ui.color(this, R.color.blush_100)));
        tvAlertIcon.setImageResource(R.drawable.ic_hearing);
        tvAlertIcon.setImageTintList(ColorStateList.valueOf(Ui.color(this, R.color.blush_700)));
        tvAlertTitle.setText(R.string.hearing_idle_title);
        tvAlertTitle.setTextColor(ContextCompat.getColor(this, R.color.ink_900));
        tvAlertDetail.setText(R.string.hearing_idle_detail);
        tvAlertDetail.setTextColor(ContextCompat.getColor(this, R.color.ink_700));
        tvAlertMeta.setVisibility(View.GONE);
        alertRings.setActive(false);
        soundTypeBadge.setVisibility(View.GONE);
        if (pip.getExpression() != DuckLooks.Expression.CONCERNED) {
            pip.setLayerVisible(R.drawable.duck_hearing_waves, true);
            pip.setWavesPulsing(true);
            pip.setExpression(DuckLooks.Expression.IDLE, true);
        }
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
        setListening("Loading speech model…", false);
        StorageService.unpack(this, "model-en-us", "model",
                model -> { voskModel = model; beginCaptions(); },
                e -> setListening("Speech model not found (" + e.getMessage() + ")", true));
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
                @Override public void onError(Exception e) { setListening("Caption error: " + e.getMessage(), true); }
                @Override public void onTimeout() { }
            });
            setListening("Live captions ON — speech becomes text on this phone (offline)", false);
        } catch (Exception e) {
            setListening("Could not start captions (" + e.getMessage() + ")", true);
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
        tvFeed.setText(shown.isEmpty() ? getString(R.string.hearing_speak_near) : shown);
        feedScroll.post(() -> feedScroll.fullScroll(View.FOCUS_DOWN));
        if (partial != null && !partial.isEmpty() && pip.getExpression() != DuckLooks.Expression.CONCERNED) {
            // speech arriving: Pip listens hard, then relaxes
            pip.setExpression(DuckLooks.Expression.ALERT, true);
            handler.removeCallbacks(pipCalm);
            handler.postDelayed(pipCalm, CAPTION_ALERT_MS);
        }
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
            else setListening("Microphone permission is needed. Turn it on in Settings → Apps → SENSE+ → Permissions.", true);
        }
    }
}
