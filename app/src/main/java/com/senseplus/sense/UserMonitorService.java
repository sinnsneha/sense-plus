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
import android.os.CountDownTimer;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

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

    // Vosk voice (offline, continuous keyword listening)
    private Model voskModel;
    private SpeechService speechService;

    private enum Level { NORMAL, ATTENTION, EMERGENCY }
    private Level level = Level.NORMAL;

    private CountDownTimer attentionTimer;
    private long attentionStart = 0;
    private static final int ATTENTION_SECONDS = 15;
    private static final long RECOVERY_GRACE_MS = 2500;
    private static final float RECOVERY_MOVEMENT = 4f;
    private static final float GRAVITY = 9.8f;

    private boolean started = false;

    // guard so the mic doesn't act on the phone's own speech
    private boolean ttsSpeaking = false;
    private long lastSpokeEnd = 0;

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
            try { locationHelper.start(); } catch (Exception ignored) { }
            broadcast("NORMAL", "Monitoring (normal)");
            cloudHelper.updateStatus("NORMAL", "Monitoring", battery());
            startVoice();
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

    // ---------- Vosk voice (offline keyword listening) ----------
    private void startVoice() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) return;
        // Unpack the model from assets/model-en-us into internal storage, then start listening
        StorageService.unpack(this, "model-en-us", "model",
            (model) -> { voskModel = model; startRecognition(); },
            (exception) -> { /* model missing or failed to load — voice simply stays off */ });
    }

    private void startRecognition() {
        try {
            Recognizer rec = new Recognizer(voskModel, 16000.0f);
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(new RecognitionListener() {
                @Override public void onPartialResult(String hypothesis) { handleVosk(hypothesis); }
                @Override public void onResult(String hypothesis) { handleVosk(hypothesis); }
                @Override public void onFinalResult(String hypothesis) { handleVosk(hypothesis); }
                @Override public void onError(Exception e) { }
                @Override public void onTimeout() { }
            });
        } catch (Exception ignored) { }
    }

    private void handleVosk(String json) {
        if (json == null) return;
        // ignore while (or just after) the phone is speaking, so it doesn't hear itself
        if (ttsSpeaking || System.currentTimeMillis() - lastSpokeEnd < 700) return;

        String text;
        try {
            JSONObject o = new JSONObject(json);
            text = o.has("partial") ? o.optString("partial") : o.optString("text");
        } catch (Exception e) { return; }
        if (text == null || text.trim().isEmpty()) return;
        text = text.toLowerCase(Locale.US);

        if (level == Level.NORMAL) {
            if (text.contains("help")) triggerEmergency("Voice command: HELP");
        } else {
            if (text.contains("okay") || text.contains("fine") || text.contains("yes") || text.contains("alright")) {
                cancelToNormal("you said you're okay");
            }
        }
    }

    private void stopVoice() {
        if (speechService != null) {
            try { speechService.stop(); speechService.shutdown(); } catch (Exception ignored) { }
            speechService = null;
        }
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
            try { startForeground(ONGOING_ID, n); } catch (Exception ignored) { }
        }
    }

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
        if (voskModel != null) { try { voskModel.close(); } catch (Exception ignored) { } voskModel = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        cancelAlert();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
