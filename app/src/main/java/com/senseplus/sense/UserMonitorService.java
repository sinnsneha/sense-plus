package com.senseplus.sense;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserMonitorService extends Service implements SensorEventListener {

    public static final String ACTION_START = "START";
    public static final String ACTION_HELP = "HELP";
    public static final String ACTION_IM_OK = "IM_OK";

    private static final String CHANNEL_ONGOING = "user_monitor";
    private static final String CHANNEL_ALERT = "user_alert_v2";
    private static final int ONGOING_ID = 10;
    private static final int ALERT_ID = 11;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private FallDetector fallDetector;
    private TextToSpeech tts;
    private Vibrator vibrator;
    private LocationHelper locationHelper;
    private CloudHelper cloudHelper;
    private SpeechRecognizer speech;

    private enum Level { NORMAL, ATTENTION, EMERGENCY }
    private Level level = Level.NORMAL;

    private CountDownTimer attentionTimer;
    private long attentionStart = 0;
    private static final int ATTENTION_SECONDS = 15;
    private static final long RECOVERY_GRACE_MS = 2500;
    private static final float RECOVERY_MOVEMENT = 4f;
    private static final float GRAVITY = 9.8f;

    private boolean started = false;

    // Voice
    private boolean voiceActive = false;
    private boolean ttsSpeaking = false;
    private long lastSpokeEnd = 0;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { ttsSpeaking = true; }
                    @Override public void onDone(String id) { ttsSpeaking = false; lastSpokeEnd = System.currentTimeMillis(); }
                    @Override public void onError(String id) { ttsSpeaking = false; lastSpokeEnd = System.currentTimeMillis(); }
                });
            }
        });

        cloudHelper = new CloudHelper();
        cloudHelper.connect();

        locationHelper = new LocationHelper(this, (lat, lon, acc) -> handleLocation(lat, lon));

        fallDetector = new FallDetector(new FallDetector.Listener() {
            @Override public void onFallDetected() { enterAttention(); }
            @Override public void onStateChanged(String info) { }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForeground("Monitoring (normal)");
        if (!started) {
            started = true;
            if (accelerometer != null)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            try { locationHelper.start(); } catch (Exception ignored) {}
            broadcast("NORMAL", "Monitoring (normal)");
            cloudHelper.updateStatus("NORMAL", "Monitoring", battery());
            startVoice();   // begin continuous listening (for "help", and later "okay")
        }
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_HELP.equals(action)) triggerEmergency("Manual HELP pressed");
        else if (ACTION_IM_OK.equals(action)) cancelToNormal("You pressed I'm OK");
        return START_STICKY;
    }

    // ---------- state machine ----------
    private void enterAttention() {
        if (level != Level.NORMAL) return;
        level = Level.ATTENTION;
        attentionStart = System.currentTimeMillis();
        speak("Possible fall detected. Are you okay? Say I am okay, or I will alert your guardian.");
        vibrate(800);
        broadcast("ATTENTION", "Possible fall — are you okay?");
        cloudHelper.updateStatus("ATTENTION", "Possible fall — awaiting response", battery());
        showAlert("Possible fall — Are you okay?", "Say \"I'm okay\" or tap I'M OK if you are fine.", false);

        if (attentionTimer != null) attentionTimer.cancel();
        attentionTimer = new CountDownTimer(ATTENTION_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) { ensureForeground("Are you okay? (" + (ms / 1000) + "s)"); }
            @Override public void onFinish() { triggerEmergency("No response after fall"); }
        }.start();
    }

    private void triggerEmergency(String reason) {
        if (attentionTimer != null) attentionTimer.cancel();
        level = Level.EMERGENCY;
        speak("Possible emergency. Alerting your guardian.");
        vibrate(1200);
        broadcast("EMERGENCY", "POSSIBLE EMERGENCY — " + reason);
        cloudHelper.updateStatus("POSSIBLE EMERGENCY", reason, battery());
        showAlert("POSSIBLE EMERGENCY", "Guardian notified. Tap I'M OK to cancel.", true);
        ensureForeground("EMERGENCY — guardian notified");
    }

    private void cancelToNormal(String reason) {
        if (attentionTimer != null) attentionTimer.cancel();
        boolean wasActive = level != Level.NORMAL;
        level = Level.NORMAL;
        if (wasActive) speak("Okay. Alert canceled.");
        broadcast("NORMAL", "All clear (" + reason + ")");
        cloudHelper.updateStatus("NORMAL", "All clear (" + reason + ")", battery());
        cancelAlert();
        ensureForeground("Monitoring (normal)");
    }

    // ---------- sensors ----------
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];
        double mag = Math.sqrt(x * x + y * y + z * z);
        if (level == Level.NORMAL) {
            fallDetector.process(mag);
        } else if (level == Level.ATTENTION) {
            long since = System.currentTimeMillis() - attentionStart;
            if (since > RECOVERY_GRACE_MS && Math.abs(mag - GRAVITY) > RECOVERY_MOVEMENT) {
                cancelToNormal("movement detected — you're up");
            }
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // ---------- continuous voice (keyword listening) ----------
    private void startVoice() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        voiceActive = true;
        ensureRecognizer();
        listenOnce();
    }

    private void ensureRecognizer() {
        if (speech != null) return;
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(new RecognitionListener() {
            @Override public void onPartialResults(Bundle partial) {
                handleSpeech(partial.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
            }
            @Override public void onResults(Bundle results) {
                handleSpeech(results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION));
                listenAgain();
            }
            @Override public void onError(int error) { listenAgain(); }
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    private void listenOnce() {
        if (!voiceActive) return;
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString());
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            speech.startListening(i);
        } catch (Exception ignored) { }
    }

    private void listenAgain() {
        if (!voiceActive) return;
        try { speech.cancel(); } catch (Exception ignored) { }
        main.postDelayed(this::listenOnce, 100);   // small gap so it doesn't thrash
    }

    private void handleSpeech(ArrayList<String> list) {
        if (list == null) return;
        // Ignore audio while (or just after) the phone is speaking, so it doesn't hear itself
        if (ttsSpeaking || System.currentTimeMillis() - lastSpokeEnd < 700) return;

        for (String s : list) {
            String t = s.toLowerCase(Locale.US);
            if (level == Level.NORMAL) {
                if (t.contains("help")) { triggerEmergency("Voice command: HELP"); return; }
            } else {
                if (t.contains("okay") || t.contains("fine") || t.contains("yes")
                        || t.contains("alright") || t.contains("i am ok") || t.contains("i'm ok")) {
                    cancelToNormal("you said you're okay");
                    return;
                }
            }
        }
    }

    private void stopVoice() {
        voiceActive = false;
        try { if (speech != null) speech.cancel(); } catch (Exception ignored) { }
    }

    // ---------- location -> cloud ----------
    private void handleLocation(double lat, double lon) {
        String maps = "https://www.google.com/maps?q=" + lat + "," + lon;
        new Thread(() -> {
            String addr = reverseGeocode(lat, lon);
            cloudHelper.updateLocation(lat, lon, maps, addr);
        }).start();
    }
    private String reverseGeocode(double lat, double lon) {
        try {
            Geocoder g = new Geocoder(this, Locale.getDefault());
            List<Address> l = g.getFromLocation(lat, lon, 1);
            if (l != null && !l.isEmpty()) return l.get(0).getAddressLine(0);
        } catch (Exception ignored) { }
        return "Address unavailable";
    }

    // ---------- helpers ----------
    private void speak(String t) { if (tts != null) tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u"); }
    private void vibrate(long ms) {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
    }
    private int battery() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
    }
    private boolean hasPermission(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }
    private void broadcast(String status, String info) {
        Intent i = new Intent(MainActivity.STATUS_ACTION).setPackage(getPackageName());
        i.putExtra("status", status);
        i.putExtra("info", info);
        sendBroadcast(i);
    }

    // ---------- notifications ----------
    private void ensureForeground(String text) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ONGOING)
                .setContentTitle("SENSE+ is protecting you")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int types = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION))
                    types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && hasPermission(Manifest.permission.RECORD_AUDIO))
                    types |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
                startForeground(ONGOING_ID, n, types);
            } else {
                startForeground(ONGOING_ID, n);
            }
        } catch (Exception e) {
            try { startForeground(ONGOID(), n); } catch (Exception ignored) { }
        }
    }
    private int ONGOID() { return ONGOING_ID; }

    private void showAlert(String title, String text, boolean emergency) {
        PendingIntent okPi = PendingIntent.getService(this, 1,
                new Intent(this, UserMonitorService.class).setAction(ACTION_IM_OK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ALERT)
                .setContentTitle((emergency ? "🔴 " : "🟠 ") + title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setColor(Color.parseColor(emergency ? "#C62828" : "#E65100"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "I'M OK", okPi)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ALERT_ID, notif);
    }
    private void cancelAlert() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ALERT_ID);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ONGOING, "Monitoring", NotificationManager.IMPORTANCE_LOW));
        NotificationChannel alert = new NotificationChannel(
                CHANNEL_ALERT, "Fall alerts", NotificationManager.IMPORTANCE_HIGH);
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 500, 200, 500});
        nm.createNotificationChannel(alert);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (attentionTimer != null) attentionTimer.cancel();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (locationHelper != null) locationHelper.stop();
        stopVoice();
        if (speech != null) { speech.destroy(); speech = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        cancelAlert();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}