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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import android.content.SharedPreferences;
import org.json.JSONArray;
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
    public static final String ACTION_RELOAD_VOICE = "RELOAD_VOICE"; // used by the codeword screen (segment 16)

    private static final String CHANNEL_ONGOING = "user_monitor";
    private static final String CHANNEL_ALERT = "user_alert_v2";
    private static final int ONGOING_ID = 10;
    private static final int ALERT_ID = 11;
    private static final int SIGNIN_ID = 12;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private boolean gyroAvailable = false;
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
    // Package-private: GuardianService quotes this window in its early-warning text.
    static final int ATTENTION_SECONDS = 15;
    private static final long RECOVERY_GRACE_MS = 2500;
    private static final float RECOVERY_MOVEMENT = 4f;
    private static final float GRAVITY = 9.8f;

    private boolean started = false;

    // Server-side heartbeat so the guardian can tell a live phone from a lost signal.
    private static final long HEARTBEAT_MS = 30_000L;
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeatTask = new Runnable() {
        @Override public void run() {
            sendHeartbeat();
            heartbeatHandler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    // Warns once per discharge (same rule class the guardian phone uses for this phone's level).
    private final BatteryAlertRule batteryRule = new BatteryAlertRule();

    // guard so the mic doesn't act on the phone's own speech
    private boolean ttsSpeaking = false;
    private long lastSpokeEnd = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        gyroAvailable = gyroscope != null;
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

        locationHelper = new LocationHelper(this, (lat, lon, acc) -> handleLocation(lat, lon));

        fallDetector = new FallDetector(new FallDetector.Listener() {
            @Override public void onFallDetected(FusionResult result) { enterAttention(result); }
            @Override public void onStateChanged(String info) {
                if (!isRejection(info)) return;
                String msg = "Fall check: " + info;
                Log.d("SenseFusion", msg);
                broadcast("NORMAL", msg);
            }
        }, gyroAvailable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            showSignInRequired();
            return START_NOT_STICKY;
        }

        String action = intent != null ? intent.getAction() : null;

        // A codeword change only reloads the recognizer; if monitoring is not
        // running, do nothing (and don't leave a foreground service behind).
        if (ACTION_RELOAD_VOICE.equals(action) && !started) {
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForeground("Monitoring (normal)");
        if (!started) {
            started = true;
            if (accelerometer != null)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            if (gyroscope != null)
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            try { locationHelper.start(); } catch (Exception ignored) { }
            broadcast("NORMAL", "Monitoring (normal)");
            cloudHelper.updateStatus("NORMAL", "Monitoring", battery());
            startHeartbeat();
            startVoice();
        }
        if (ACTION_HELP.equals(action)) triggerEmergency(EventTypes.MANUAL_HELP, "Manual HELP pressed");
        else if (ACTION_IM_OK.equals(action)) cancelToNormal("You pressed I'm OK");
        else if (ACTION_RELOAD_VOICE.equals(action)) {
            stopVoice();
            if (voskModel != null) startRecognition();
        }
        return START_STICKY;
    }

    // ---------- state machine ----------
    private void enterAttention(FusionResult result) {
        if (level != Level.NORMAL) return;
        level = Level.ATTENTION;
        attentionStart = System.currentTimeMillis();

        String summary = result != null ? result.summary() : "fusion data missing";
        String detail = "Possible fall (" + summary + ")";

        speak("Possible fall detected. Are you okay? Say I am okay, or I will alert your guardian.");
        vibrate(800);
        broadcast("ATTENTION", detail);
        cloudHelper.updateStatus("ATTENTION", detail, battery());
        cloudHelper.logEvent(EventTypes.ATTENTION, detail);
        showAlert("Possible fall — Are you okay?", "Say \"I'm okay\" or tap I'M OK if you are fine.", false);

        if (attentionTimer != null) attentionTimer.cancel();
        attentionTimer = new CountDownTimer(ATTENTION_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) { ensureForeground("Are you okay? (" + (ms / 1000) + "s)"); }
            @Override public void onFinish() { triggerEmergency(EventTypes.EMERGENCY, "No response after fall"); }
        }.start();
    }

    private void triggerEmergency(String eventType, String reason) {
        // Already an emergency: do not re-announce, re-notify or duplicate the event.
        if (level == Level.EMERGENCY) return;

        if (attentionTimer != null) attentionTimer.cancel();
        level = Level.EMERGENCY;
        speak("Possible emergency. Alerting your guardian.");
        vibrate(1200);
        broadcast("EMERGENCY", "POSSIBLE EMERGENCY — " + reason);
        cloudHelper.updateStatus("POSSIBLE EMERGENCY", reason, battery());
        cloudHelper.logEvent(eventType, reason);
        showAlert("POSSIBLE EMERGENCY", "Guardian notified. Tap I'M OK to cancel.", true);
        ensureForeground("EMERGENCY — guardian notified");
    }

    private void cancelToNormal(String reason) {
        if (attentionTimer != null) attentionTimer.cancel();
        boolean wasActive = level != Level.NORMAL;
        level = Level.NORMAL;
        if (wasActive) {
            speak("Okay. Alert canceled.");
            cloudHelper.logEvent(EventTypes.ALL_CLEAR, "All clear (" + reason + ")");
        }
        broadcast("NORMAL", "All clear (" + reason + ")");
        cloudHelper.updateStatus("NORMAL", "All clear (" + reason + ")", battery());
        cancelAlert();
        ensureForeground("Monitoring (normal)");
    }

    // ---------- sensors ----------
    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type != Sensor.TYPE_ACCELEROMETER && type != Sensor.TYPE_GYROSCOPE) return;

        long tMs = event.timestamp / 1_000_000L; // same time base for both sensors

        if (type == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0], y = event.values[1], z = event.values[2];
            double mag = Math.sqrt(x * x + y * y + z * z);
            if (level == Level.NORMAL) {
                fallDetector.processAccel(x, y, z, tMs);
            } else if (level == Level.ATTENTION) {
                long since = System.currentTimeMillis() - attentionStart;
                if (since > RECOVERY_GRACE_MS && Math.abs(mag - GRAVITY) > RECOVERY_MOVEMENT) {
                    cancelToNormal("movement detected — you're up");
                }
            }
        } else {
            fallDetector.processGyro(event.values[0], event.values[1], event.values[2], tMs);
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    /** Only rejection messages are forwarded to the screen; ordinary state noise is not. */
    private boolean isRejection(String info) {
        if (info == null) return false;
        return info.startsWith("fusion reject")
                || info.startsWith("bump, not a fall")
                || info.startsWith("not a fall")
                || info.startsWith("movement after impact");
    }

    // ---------- Vosk voice (offline keyword listening) ----------
    private void startVoice() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            broadcast("NORMAL", "VOICE OFF — mic permission denied");
            speak("Microphone permission is off.");
            return;
        }
        broadcast("NORMAL", "Loading voice model...");
        // Unpack the model from assets/model-en-us into internal storage, then start listening
        StorageService.unpack(this, "model-en-us", "model",
            (model) -> { voskModel = model; startRecognition(); },
            (exception) -> {
                String msg = exception != null ? exception.getMessage() : "unknown";
                broadcast("NORMAL", "VOICE OFF — model not found (" + msg + ")");
                speak("Voice model not found.");
            });
    }

    /** Reads the private codeword from SharedPreferences, or null when unset. */
    private String codeword() {
        SharedPreferences p = getSharedPreferences("senseplus_prefs", MODE_PRIVATE);
        String cw = p.getString("codeword", null);
        if (cw == null) return null;
        cw = cw.trim().toLowerCase(Locale.US);
        return cw.isEmpty() ? null : cw;
    }

    private void startRecognition() {
        try {
            // Restricted grammar keeps Vosk on the keywords we care about.
            // org.json escapes any codeword safely; "not"/"no" must stay so
            // segment 13's negation rule can see "I'm not okay".
            JSONArray grammar = new JSONArray();
            grammar.put("help");
            grammar.put("okay");
            grammar.put("i'm okay");
            grammar.put("i am okay");
            grammar.put("fine");
            grammar.put("alright");
            grammar.put("yes");
            grammar.put("no");
            grammar.put("not");
            grammar.put("i'm not okay");
            String cw = codeword();
            if (cw != null) grammar.put(cw);
            grammar.put("[unk]");

            Recognizer rec = new Recognizer(voskModel, 16000.0f, grammar.toString());
            speechService = new SpeechService(rec, 16000.0f);
            speechService.startListening(new RecognitionListener() {
                @Override public void onPartialResult(String hypothesis) { handleVosk(hypothesis); }
                @Override public void onResult(String hypothesis) { handleVosk(hypothesis); }
                @Override public void onFinalResult(String hypothesis) { handleVosk(hypothesis); }
                @Override public void onError(Exception e) {
                    broadcast("NORMAL", "VOICE ERROR — " + (e != null ? e.getMessage() : ""));
                }
                @Override public void onTimeout() { }
            });
            broadcast("NORMAL", "Voice ready: say help or your codeword");
            speak("Voice ready.");
        } catch (Exception e) {
            broadcast("NORMAL", "VOICE OFF — recognizer failed (" + e.getMessage() + ")");
        }
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

        VoiceCommandParser.Command cmd = VoiceCommandParser.parse(text, codeword());
        if (level == Level.NORMAL) {
            if (cmd == VoiceCommandParser.Command.HELP)
                triggerEmergency(EventTypes.VOICE_HELP, "Voice command: HELP");
            else if (cmd == VoiceCommandParser.Command.CODEWORD)
                triggerEmergency(EventTypes.CODEWORD, "Codeword heard");
            // OKAY means nothing when no alert is active.
        } else if (level == Level.ATTENTION) {
            if (cmd == VoiceCommandParser.Command.HELP)
                triggerEmergency(EventTypes.VOICE_HELP, "Voice: HELP after fall");
            else if (cmd == VoiceCommandParser.Command.CODEWORD)
                triggerEmergency(EventTypes.CODEWORD, "Codeword heard");
            else if (cmd == VoiceCommandParser.Command.OKAY)
                cancelToNormal("you said you're okay");
        } else { // EMERGENCY
            if (cmd == VoiceCommandParser.Command.OKAY)
                cancelToNormal("you said you're okay");
            // HELP/CODEWORD are already an emergency: don't duplicate the event.
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
    /** No account: leave a notification, satisfy the foreground-service contract, then stop. */
    private void showSignInRequired() {
        Notification n = signInNotification();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(SIGNIN_ID, n);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ONGOING_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(ONGOING_ID, n);
            }
        } catch (Exception ignored) { }
        stopSelf();
    }

    private Notification signInNotification() {
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, login,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ONGOING)
                .setContentTitle("Sign in to SENSE+")
                .setContentText("Sign in to SENSE+ to resume protection.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
    }

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

    /** Writes a heartbeat immediately, then every 30 s until onDestroy. */
    private void startHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatTask);
        sendHeartbeat();
        heartbeatHandler.postDelayed(heartbeatTask, HEARTBEAT_MS);
    }

    /** One heartbeat: monitoring flag, fresh lastSeen and the battery level, then the battery check. */
    private void sendHeartbeat() {
        int pct = battery();
        cloudHelper.updateMonitoring(true, pct);
        checkBattery(pct);
    }

    /**
     * Once per discharge: log a LOW_BATTERY event for the guardian's timeline and say it aloud.
     * The rule is consulted in every state so it stays in step with the real level, but speech
     * is skipped during a fall prompt or emergency because speak() would cut that off.
     */
    private void checkBattery(int pct) {
        if (!batteryRule.shouldAlert(pct)) return;
        cloudHelper.logEvent(EventTypes.LOW_BATTERY, "Battery low (" + pct + "%)");
        if (level == Level.NORMAL) speak("Battery low, please charge the phone");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Best effort: tell the guardian monitoring was turned off rather than lost.
        heartbeatHandler.removeCallbacks(heartbeatTask);
        if (cloudHelper != null) cloudHelper.updateMonitoring(false);
        if (attentionTimer != null) attentionTimer.cancel();
        if (sensorManager != null) sensorManager.unregisterListener(this); // removes accel + gyro
        if (locationHelper != null) locationHelper.stop();
        stopVoice();
        if (voskModel != null) { try { voskModel.close(); } catch (Exception ignored) { } voskModel = null; }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        cancelAlert();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) { return null; }
}
