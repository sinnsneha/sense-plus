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

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Source;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class UserMonitorService extends Service implements SensorEventListener {

    public static final String ACTION_START = "START";
    public static final String ACTION_HELP = "HELP";
    public static final String ACTION_IM_OK = "IM_OK";
    public static final String ACTION_RELOAD_VOICE = "RELOAD_VOICE"; // used by the codeword screen (segment 16)
    /**
     * Segment 28, sent by Hearing Assist: give it the microphone / take it back. Only the Vosk
     * keyword listener stops and restarts; fall detection, location and the heartbeat carry on.
     */
    public static final String ACTION_PAUSE_VOICE = "PAUSE_VOICE";
    public static final String ACTION_RESUME_VOICE = "RESUME_VOICE";

    private static final String VOICE_PAUSED_TEXT =
            "Voice keywords paused while Hearing Assist is open (fall detection still on)";

    /**
     * True from the moment monitoring is running until the service is destroyed. Hearing Assist
     * checks it before sending PAUSE / RESUME, so it never starts this service just to pause
     * something that is not there (that pattern crashed in segment 16: a foreground service
     * started with startForegroundService must call startForeground, even when there is
     * nothing to do). Static: both live in the same process.
     */
    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

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
    private boolean voiceLoading = false;                 // the model is being unpacked (segment 28)
    private final MicArbiter mic = new MicArbiter();      // may the keywords use the microphone right now?

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

    // Last location fix, for the emergency SMS (segment 26). Updated on the main thread.
    private long lastFixMs = 0;            // when the fix arrived; 0 = none yet
    private double lastLat, lastLon;
    private float lastAccuracyM = 0f;

    /**
     * A fix older than this is not put in the SMS (ms). Updates arrive about every 5 s, so a gap
     * of 10 minutes means GPS has been lost for a long time; a guardian sent to an old spot
     * would be misled, so the text says the location is unknown instead.
     */
    private static final long SMS_FIX_MAX_AGE_MS = 10 * 60_000L;

    // ---------- safe zone around Home (segment 27) ----------
    // The checks ride on the location updates the service already receives (about every 5 s):
    // there is no extra location request. The only extra network use is ONE read of this user's
    // own document at start and then every HOME_REFRESH_MS, to pick up a Home or settings the
    // guardian changed (and to keep segment 26's copy of the guardians' phones fresh).

    /** How often the user's own document is read for a guardian-set Home (ms): 10 min = 144 reads a day. */
    private static final long HOME_REFRESH_MS = 10 * 60_000L;

    private final SafeZoneRule safeZoneRule = new SafeZoneRule();
    private boolean safeZoneSeeded = false; // the rule starts from the cloud's last "safeZone" once
    private final Handler homeHandler = new Handler(Looper.getMainLooper());
    private final Runnable homeTask = new Runnable() {
        @Override public void run() {
            refreshHome();
            homeHandler.postDelayed(this, HOME_REFRESH_MS);
        }
    };

    // ---------- guardian reply: "I'm on my way" (segment 25) ----------
    // While the level is ATTENTION or EMERGENCY the service reads its own users/{uid} document
    // every AckRule.POLL_MS (Source.SERVER) to see whether a guardian answered. Nothing is read
    // while the level is NORMAL. AckRule decides what counts; this block only does the I/O.

    /** One long buzz when a guardian answers (ms): clearly longer than the alert's 800 / 1200. */
    private static final long ACK_VIBRATION_MS = 1500;

    /**
     * The latest reply shown on the Safety screen, so it is there when the screen is opened after
     * the reply arrived (the broadcast only reaches a screen that is open). Null when none.
     */
    static volatile String ackMessage = null;

    private String myUid;                  // this user's account id; null when signed out
    private long alertStartMs = 0;         // when the current alert began (phone clock); 0 = none
    private final Set<String> acknowledgedBy = new HashSet<>(); // guardians already announced
    private String ackNote = null;         // "Ravi is on the way", shown in the ongoing notification
    private String ongoingText = "Monitoring (normal)"; // the ongoing notification without the note
    private String repeatMessage = null;   // the one announcement to repeat after 30 s
    private final Handler ackHandler = new Handler(Looper.getMainLooper());
    private final Runnable ackPollTask = this::pollAck;
    private final Runnable ackRepeatTask = () -> {
        String message = repeatMessage;
        repeatMessage = null;
        if (message != null && level != Level.NORMAL) speak(message); // still in an alert
    };

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

        // Segment 23: write to this user's own document. With no signed-in account the helper
        // skips every write; onStartCommand then stops the service with a sign-in notice.
        FirebaseUser signedIn = FirebaseAuth.getInstance().getCurrentUser();
        myUid = signedIn != null && !signedIn.isAnonymous() ? signedIn.getUid() : null;
        cloudHelper = new CloudHelper(myUid);

        locationHelper = new LocationHelper(this, (lat, lon, acc) -> handleLocation(lat, lon, acc));

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

        // Segment 28: Hearing Assist pauses / resumes the voice keywords. It sends these with
        // plain startService and only while isRunning(), so monitoring is already on. They return
        // before ensureForeground() so the ongoing notification text is left as it is.
        if (ACTION_PAUSE_VOICE.equals(action) || ACTION_RESUME_VOICE.equals(action)) {
            if (!started) { // should not happen; do not leave a service behind
                stopSelf();
                return START_NOT_STICKY;
            }
            if (ACTION_PAUSE_VOICE.equals(action)) pauseVoice(); else resumeVoice();
            return START_STICKY;
        }

        running = true;
        ensureForeground("Monitoring (normal)");
        if (!started) {
            started = true;
            if (accelerometer != null)
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            if (gyroscope != null)
                sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);
            try { locationHelper.start(); } catch (Exception ignored) { }
            broadcast("NORMAL", "Monitoring (normal)");
            // Name and phone first, so a linked guardian sees who this is and can call them.
            cloudHelper.updateProfile(AccountStore.name(this), AccountStore.phone(this));
            cloudHelper.updateStatus("NORMAL", "Monitoring", battery());
            // Segments 26 + 27: one read of this user's document now, then every 10 minutes. It
            // copies the guardians' phones to this phone (the SMS fallback needs them with no
            // network) and picks up a Home the guardian set.
            homeHandler.removeCallbacks(homeTask);
            homeHandler.post(homeTask);
            startHeartbeat();
            // Segment 28: if Hearing Assist is already open (monitoring was started from the restart
            // reminder, say), the keywords wait until it closes.
            mic.monitoringStarted();
            if (HearingActivity.isOpen()) mic.hearingOpened();
            startVoice();
        }
        if (ACTION_HELP.equals(action)) triggerEmergency(EventTypes.MANUAL_HELP, "Manual HELP pressed");
        else if (ACTION_IM_OK.equals(action)) cancelToNormal("You pressed I'm OK");
        else if (ACTION_RELOAD_VOICE.equals(action)) {
            stopVoice();
            if (voskModel != null && mic.shouldListen()) startRecognition(true);
        }
        return START_STICKY;
    }

    // ---------- state machine ----------
    private void enterAttention(FusionResult result) {
        if (level != Level.NORMAL) return;
        level = Level.ATTENTION;
        attentionStart = System.currentTimeMillis();
        beginAlertWatch();

        String summary = result != null ? result.summary() : "fusion data missing";
        String detail = "Possible fall (" + summary + ")";

        speak("Possible fall detected. Are you okay? Say I am okay, or I will alert your guardian.");
        vibrate(800);
        broadcast("ATTENTION", detail);
        cloudHelper.startAlert("ATTENTION", detail, battery()); // also clears any old guardian reply
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

        // From NORMAL this is a new alert; from ATTENTION it is the same alert escalating, which
        // keeps its start time and any guardian reply it already has.
        boolean newAlert = level == Level.NORMAL;
        if (attentionTimer != null) attentionTimer.cancel();
        level = Level.EMERGENCY;
        if (newAlert) beginAlertWatch();
        speak("Possible emergency. Alerting your guardian.");
        vibrate(1200);
        broadcast("EMERGENCY", "POSSIBLE EMERGENCY — " + reason);
        if (newAlert) cloudHelper.startAlert("POSSIBLE EMERGENCY", reason, battery());
        else cloudHelper.updateStatus("POSSIBLE EMERGENCY", reason, battery());
        cloudHelper.logEvent(eventType, reason);
        showAlert("POSSIBLE EMERGENCY", "Guardian notified. Tap I'M OK to cancel.", true);
        ensureForeground("EMERGENCY — guardian notified");
        // Only reached when the level was not already EMERGENCY (see the return above), so a
        // second HELP in the same emergency sends nothing.
        sendEmergencySms(reason);
    }

    /**
     * Segment 26, opt-in: texts every guardian's phone and the Medical ID emergency contact once
     * per emergency. Goes over the cellular network, so it works with no internet. The numbers
     * come from the local copy (EmergencyContacts), never from Firestore. The event records only
     * HOW MANY were texted, never who.
     */
    private void sendEmergencySms(String reason) {
        if (!MedicalStore.smsEnabled(this)) return;
        List<String> recipients = EmergencyContacts.smsRecipients(this);
        if (recipients.isEmpty()) return;

        String name = AccountStore.name(this);
        if (name == null || name.trim().isEmpty()) name = MedicalStore.load(this).name;
        boolean fixIsRecent = lastFixMs > 0 && System.currentTimeMillis() - lastFixMs <= SMS_FIX_MAX_AGE_MS;
        String link = fixIsRecent ? EmergencyText.mapsLink(lastLat, lastLon) : null;
        String text = EmergencyText.sms(name, reason, link, lastAccuracyM);

        int sent = EmergencySms.send(this, recipients, text);
        if (sent > 0) {
            cloudHelper.logEvent(EventTypes.SMS_SENT,
                    "SMS sent to " + sent + (sent == 1 ? " contact" : " contacts"));
        }
    }

    private void cancelToNormal(String reason) {
        if (attentionTimer != null) attentionTimer.cancel();
        boolean wasActive = level != Level.NORMAL;
        level = Level.NORMAL;
        endAlertWatch(); // back to NORMAL: stop reading the document, drop the reply note
        if (wasActive) {
            speak("Okay. Alert canceled.");
            cloudHelper.logEvent(EventTypes.ALL_CLEAR, "All clear (" + reason + ")");
        }
        broadcast("NORMAL", "All clear (" + reason + ")");
        cloudHelper.updateStatus("NORMAL", "All clear (" + reason + ")", battery());
        cancelAlert();
        ensureForeground("Monitoring (normal)");
    }

    // ---------- safe zone (segment 27) ----------

    /**
     * Feeds one location fix to the rule and acts on the transition. Home and the settings are
     * read from the phone's own copy (HomeStore) every time, so a change made on Navigate Home
     * takes effect at once, with no extra network use. Exactly one event and one "safeZone"
     * write per transition, because SafeZoneRule reports each LEFT / RETURNED only once.
     */
    private void checkSafeZone(double lat, double lon, float accuracyM) {
        HomeData home = HomeStore.load(this);
        boolean on = home.enabled && home.hasLocation();
        double distanceM = on ? GeoDistance.metres(lat, lon, home.lat, home.lon) : 0;

        boolean wasOutside = safeZoneRule.isOutside();
        SafeZoneRule.Result result = safeZoneRule.onFix(distanceM, accuracyM, home.radiusM, on);
        if (result != SafeZoneRule.Result.NO_CHANGE) safeZoneSeeded = true; // a late seed must not undo this
        if (!on && wasOutside) cloudHelper.updateSafeZone(null); // switched off while outside: clear it now
        if (result == SafeZoneRule.Result.LEFT) {
            cloudHelper.updateSafeZone(SafeZoneAlertDecider.OUTSIDE);
            cloudHelper.logEvent(EventTypes.SAFE_ZONE_EXIT,
                    "Left the safe zone (" + GeoDistance.label(distanceM) + " from home)");
            // Spoken help, but never over a fall prompt or an emergency announcement.
            if (HomeStore.speakHelp(this) && level == Level.NORMAL) {
                speak("You are leaving home. If you need help, open Navigate Home.");
            }
        } else if (result == SafeZoneRule.Result.RETURNED) {
            cloudHelper.updateSafeZone(SafeZoneAlertDecider.INSIDE);
            cloudHelper.logEvent(EventTypes.SAFE_ZONE_RETURN, "Back inside the safe zone");
        }
    }

    /** One read of this user's own document: guardian-set Home, safe zone state, guardians' phones. */
    private void refreshHome() {
        if (myUid == null) return;
        FirestorePaths.userDoc(myUid).get(Source.SERVER)
                .addOnSuccessListener(this::onHomeDoc)
                .addOnFailureListener(e ->
                        Log.w("SenseCloud", "Home read failed: " + e.getClass().getSimpleName()));
    }

    private void onHomeDoc(DocumentSnapshot doc) {
        HomeData local = HomeStore.load(this);
        if (doc == null || !doc.exists()) {
            if (local.hasLocation()) HomeStore.push(this); // a Home saved before segment 27
            return;
        }
        EmergencyContacts.cacheFrom(this, doc); // segment 26: same read, no extra one

        Object raw = doc.get("home");
        HomeData cloud = raw instanceof Map ? HomeData.fromMap((Map<?, ?>) raw) : null;
        if (HomeData.localShouldBePushed(cloud, local.hasLocation())) {
            HomeStore.push(this);
        } else {
            HomeStore.applyCloud(this, cloud); // only if the cloud copy is newer (HomeData decides)
        }

        // Start the rule from what the guardian was last told, so restarting monitoring while the
        // user is already outside does not log a second exit.
        String cloudZone = doc.getString("safeZone");
        if (!safeZoneSeeded) {
            safeZoneRule.restore(SafeZoneAlertDecider.OUTSIDE.equals(cloudZone));
            safeZoneSeeded = true;
        }
        // The zone is off (or there is no Home): remove a stale "outside" so the guardian's chip
        // and alerts do not carry on from before.
        HomeData now = HomeStore.load(this);
        if (cloudZone != null && !(now.enabled && now.hasLocation())) {
            cloudHelper.updateSafeZone(null);
        }
    }

    // ---------- guardian reply (segment 25) ----------

    /** A new alert began (NORMAL to ATTENTION / EMERGENCY): remember when, start reading. */
    private void beginAlertWatch() {
        ackHandler.removeCallbacksAndMessages(null);
        alertStartMs = System.currentTimeMillis();
        acknowledgedBy.clear();
        ackNote = null;
        repeatMessage = null;
        if (myUid != null) ackHandler.postDelayed(ackPollTask, AckRule.POLL_MS);
    }

    /** The level is back to NORMAL: no more reads, and the reply note goes away. */
    private void endAlertWatch() {
        ackHandler.removeCallbacksAndMessages(null);
        alertStartMs = 0;
        acknowledgedBy.clear();
        repeatMessage = null;
        boolean hadReply = ackNote != null;
        ackNote = null;
        if (hadReply) {
            ackMessage = null;
            broadcastAck(""); // an empty message tells the Safety screen to hide the reply
        }
    }

    /**
     * One read of this user's own document. Runs only while the level is ATTENTION or EMERGENCY;
     * the next read is scheduled here, so there is no read at all once the level is NORMAL.
     */
    private void pollAck() {
        if (level == Level.NORMAL || myUid == null) return;
        FirestorePaths.userDoc(myUid).get(Source.SERVER)
                .addOnSuccessListener(this::handleAckDoc)
                .addOnFailureListener(e ->
                        Log.w("SenseCloud", "Ack read failed: " + e.getMessage()));
        long next = AckRule.nextPollDelayMs(!acknowledgedBy.isEmpty());
        if (next > 0) ackHandler.postDelayed(ackPollTask, next);
    }

    private void handleAckDoc(DocumentSnapshot doc) {
        if (level == Level.NORMAL || doc == null || !doc.exists()) return; // the alert ended meanwhile
        Timestamp time = doc.getTimestamp("ack.time");
        String by = doc.getString("ack.by");
        AckRule.Outcome outcome = AckRule.classify(
                time != null ? time.toDate().getTime() : null, by, alertStartMs, acknowledgedBy);
        if (outcome == AckRule.Outcome.NONE) return; // no ack, an old one, or already announced
        acknowledgedBy.add(by);
        onGuardianAck(outcome, doc.getString("ack.name"));
    }

    /**
     * A guardian answered. Tell the user in every channel they might use (voice, a long buzz, the
     * ongoing notification, large text on the Safety screen) and log it for the timeline. The
     * alert itself is untouched: during ATTENTION the user must still answer or the timer runs
     * out, and a reply never hides or cancels anything.
     */
    private void onGuardianAck(AckRule.Outcome outcome, String guardianName) {
        String message = AckRule.spoken(outcome, guardianName);
        String detail = AckRule.eventDetail(outcome, guardianName);

        speak(message);
        vibrate(ACK_VIBRATION_MS);

        ackNote = detail;
        ensureForeground(ongoingText); // rebuilds the ongoing notification with the note in front
        ackMessage = message;
        broadcastAck(message);
        cloudHelper.logEvent(EventTypes.GUARDIAN_ACK, detail);

        if (outcome == AckRule.Outcome.FIRST) { // say it once more in case the first was missed
            repeatMessage = message;
            ackHandler.removeCallbacks(ackRepeatTask);
            ackHandler.postDelayed(ackRepeatTask, AckRule.REPEAT_AFTER_MS);
        }
    }

    private void broadcastAck(String message) {
        Intent i = new Intent(MainActivity.ACK_ACTION).setPackage(getPackageName());
        i.putExtra("message", message);
        sendBroadcast(i);
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
        voiceLoading = true;
        // Unpack the model from assets/model-en-us into internal storage, then start listening
        // (unless Hearing Assist opened meanwhile: then the keywords wait for it to close).
        StorageService.unpack(this, "model-en-us", "model",
            (model) -> {
                voiceLoading = false;
                voskModel = model;
                if (mic.shouldListen()) startRecognition(true);
                else showVoicePaused();
            },
            (exception) -> {
                voiceLoading = false;
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

    // ---------- microphone sharing with Hearing Assist (segment 28) ----------

    /**
     * Hearing Assist opened and wants the microphone: stop ONLY the Vosk keyword listener. The
     * sensors, the fall detector, location, the heartbeat and every alert are not touched, so a
     * deaf user can use captions without losing fall detection. The loaded model is kept, so
     * resuming is quick.
     */
    private void pauseVoice() {
        mic.hearingOpened();
        stopVoice();
        showVoicePaused();
    }

    /**
     * Hearing Assist closed. Restarts recognition with the CURRENT grammar and codeword
     * (startRecognition reads the codeword each time), if monitoring is on.
     */
    private void resumeVoice() {
        mic.hearingClosed();
        if (!mic.shouldListen()) return;
        if (voskModel != null) startRecognition(level == Level.NORMAL);
        else if (!voiceLoading) startVoice(); // no model yet (an earlier load failed): try again
        // else the model is still unpacking: its callback starts listening now that the arbiter allows it
    }

    /** Tells the Safety screen why the keywords are quiet. Not while an alert is showing: that must not be repainted. */
    private void showVoicePaused() {
        if (level == Level.NORMAL) broadcast("NORMAL", VOICE_PAUSED_TEXT);
    }

    /**
     * @param announce say "Voice ready" and update the status line. False when resuming during an
     *                 alert, so the announcement does not cut across the alert's own speech.
     */
    private void startRecognition(boolean announce) {
        if (voskModel == null) return;
        stopVoice(); // never leave a second listener behind if this is called twice
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
            if (announce) {
                broadcast("NORMAL", "Voice ready: say help or your codeword");
                speak("Voice ready.");
            }
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
    private void handleLocation(double lat, double lon, float accuracyMeters) {
        // Kept for the emergency SMS, which must not wait for a fix or for the network.
        lastLat = lat;
        lastLon = lon;
        lastAccuracyM = accuracyMeters;
        lastFixMs = System.currentTimeMillis();
        checkSafeZone(lat, lon, accuracyMeters);
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
        ongoingText = text;
        // A guardian's reply goes in front of the state, so the state is never hidden by it.
        String shown = ackNote != null ? ackNote + " · " + text : text;
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setContentTitle("SENSE+ is protecting you")
            .setContentText(shown)
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle((emergency ? "🔴 " : "🟠 ") + title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setColor(Color.parseColor(emergency ? "#C62828" : "#E65100"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "I'M OK", okPi);

        if (emergency) {
            // Segment 26: "Medical ID" opens the read-only helper view, which is shown over the
            // lock screen. PUBLIC visibility so the notification and its buttons are not hidden
            // on a locked screen set to "hide sensitive content": a bystander has to see it.
            PendingIntent medicalPi = PendingIntent.getActivity(this, 2,
                new Intent(this, MedicalHelperActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_info_details, "Medical ID", medicalPi)
                   .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        }
        Notification notif = builder.build();
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
        running = false;            // Hearing Assist must not send anything to a service that is gone
        mic.monitoringStopped();
        // Best effort: tell the guardian monitoring was turned off rather than lost.
        heartbeatHandler.removeCallbacks(heartbeatTask);
        ackHandler.removeCallbacksAndMessages(null);
        homeHandler.removeCallbacksAndMessages(null);
        ackMessage = null;
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
