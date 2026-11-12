package com.senseplus.sense;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;

import java.util.TimeZone;

public class GuardianService extends Service {

    public static final String ONGOING_CHANNEL = "guardian_ongoing";
    /** Everything that is not the alarm: early warning, all clear, battery, contact. */
    public static final String ALERT_CHANNEL = "guardian_alert_v2";
    /**
     * Possible emergency only: alarm sound + strong vibration. Channel settings are immutable
     * once created, so any change to sound/vibration/importance needs a new id (decision 5).
     */
    public static final String ALARM_CHANNEL = "guardian_alarm_v3";

    private static final int ONGOING_ID = 1;
    /** Attention, emergency, all clear and false alarm share this id: each replaces the last. */
    private static final int ALERT_ID = 2;
    private static final int SIGNIN_ID = 4;
    private static final int BATTERY_ID = 5;
    private static final int CONTACT_ID = 6;
    private static final int LINK_ID = 7;

    private static final int REQUEST_OPEN_DASHBOARD = 20;
    private static final int REQUEST_OPEN_MAP = 21;
    private static final int REQUEST_CALL = 22;
    private static final int REQUEST_OPEN_LINK = 23;

    // Milliseconds: buzz, pause, buzz ... Long pulses so it is felt in a pocket or on a table.
    private static final long[] ALARM_VIBRATION = {0, 1000, 400, 1000, 400, 1000, 400, 1000};

    private FirebaseFirestore db;

    // What the previous poll saw. Null until the first poll answers.
    private String lastStatus = null;
    private ConnectivityStatus.State lastContact = null;
    private final BatteryAlertRule batteryRule = new BatteryAlertRule();

    // Refused polls in a row. A few mean the user removed this guardian (segment 23).
    private int deniedStreak = 0;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            final String linked = AccountStore.linkedUid(GuardianService.this);
            if (linked == null) { // unlinked from another screen while running
                stopForNoLink();
                return;
            }
            FirestorePaths.userDoc(linked).get(Source.SERVER)
                    .addOnSuccessListener(doc -> {
                        deniedStreak = 0;
                        if (doc != null && doc.exists()) onPolled(doc);
                    })
                    .addOnFailureListener(e -> {
                        if (isPermissionDenied(e)) {
                            deniedStreak++;
                            if (PairingRules.shouldUnlink(deniedStreak)) onUnlinked();
                        }
                        // Any other failure is this phone's own network: keep polling.
                    });
            // Decision 1: 5 s while the user is NORMAL, 2 s during a possible fall/emergency.
            pollHandler.postDelayed(this, PairingRules.backgroundPollMs(lastStatus));
        }
    };

    private static boolean isPermissionDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }

    /** Removes the status notification (and so stops a ringing alarm). Called when the dashboard opens. */
    static void dismissAlert(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(ALERT_ID);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            // No account: leave a clear notification and stop instead of reading as nobody.
            Notification signIn = signInNotification();
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(SIGNIN_ID, signIn);
            startForegroundSafely(signIn);
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!AccountStore.isLinked(this)) {
            // Not linked to anyone yet: nothing to watch. Say so, and stop.
            startForegroundSafely(linkNotification("Link to a person",
                    "Link to the person you look after to receive their alerts."));
            stopForNoLink();
            return START_NOT_STICKY;
        }

        String who = AccountStore.linkedName(this);
        Notification ongoing = new NotificationCompat.Builder(this, ONGOING_CHANNEL)
                .setContentTitle("SENSE+ Guardian")
                .setContentText(who == null || who.trim().isEmpty()
                        ? "Monitoring for emergencies" : "Watching over " + who.trim())
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();

        startForegroundSafely(ongoing);

        deniedStreak = 0;
        pollHandler.removeCallbacks(pollTask);
        pollHandler.post(pollTask);
        return START_STICKY;
    }

    /** No linked person: post the "link" notice (tap → LinkActivity) and stop the service. */
    private void stopForNoLink() {
        pollHandler.removeCallbacks(pollTask);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(LINK_ID, linkNotification("Link to a person",
                "Link to the person you look after to receive their alerts."));
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    /** The user removed this guardian (polls refused several times): forget the link and stop. */
    private void onUnlinked() {
        String who = AccountStore.linkedName(this);
        AccountStore.clearLink(this);
        pollHandler.removeCallbacks(pollTask);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(ALERT_ID);
            nm.notify(LINK_ID, linkNotification("No longer linked",
                    (who == null || who.trim().isEmpty() ? "The person you looked after" : who.trim())
                            + " removed you as a guardian. Alerts have stopped on this phone."));
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private Notification linkNotification(String title, String text) {
        Intent link = new Intent(this, LinkActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, REQUEST_OPEN_LINK, link, pendingFlags());
        return new NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
    }

    private void startForegroundSafely(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ONGOING_ID, notification);
        }
    }

    private Notification signInNotification() {
        Intent login = new Intent(this, LoginActivity.class);
        login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, login,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, ONGOING_CHANNEL)
                .setContentTitle("Sign in to SENSE+")
                .setContentText("Sign in to SENSE+ to resume protection.")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
    }

    /**
     * Everything the guardian is told comes from this one document, which the poll has already
     * read: no extra Firestore reads. The questions "should we alert?" are answered by the pure
     * GuardianAlertDecider and BatteryAlertRule; this class only builds and posts notifications.
     *
     * "name" and "phone" are the user's own profile fields (written by the user phone from
     * segment 23). While they are absent the texts say "the user" and no Call action is offered.
     */
    private void onPolled(DocumentSnapshot doc) {
        long now = System.currentTimeMillis();
        String name = doc.getString("name");

        handleStatus(doc, name, now);
        handleBattery(doc, name);
        handleContact(doc, name, now);
    }

    // ---------- status: 🟠 attention, 🔴 emergency, 🟢 all clear ----------

    private void handleStatus(DocumentSnapshot doc, String name, long now) {
        String status = doc.getString("status");
        if (status == null) return; // no information: keep what we knew

        GuardianAlertDecider.Alert alert = GuardianAlertDecider.decide(lastStatus, status);
        lastStatus = status;

        switch (alert) {
            case ATTENTION:
                postStatusAlert(simpleNotification(ALERT_CHANNEL,
                        GuardianAlertText.attentionTitle(),
                        GuardianAlertText.attentionBody(name, UserMonitorService.ATTENTION_SECONDS),
                        "#E65100", NotificationCompat.CATEGORY_STATUS));
                break;
            case EMERGENCY:
                postStatusAlert(emergencyNotification(doc, name, now));
                break;
            case ALL_CLEAR:
                postStatusAlert(simpleNotification(ALERT_CHANNEL,
                        GuardianAlertText.allClearTitle(),
                        GuardianAlertText.allClearBody(name),
                        "#2E7D32", NotificationCompat.CATEGORY_STATUS));
                break;
            case FALSE_ALARM_RESOLVED:
                postStatusAlert(simpleNotification(ALERT_CHANNEL,
                        GuardianAlertText.falseAlarmTitle(),
                        GuardianAlertText.falseAlarmBody(name),
                        "#2E7D32", NotificationCompat.CATEGORY_STATUS));
                break;
            case NONE:
            default:
                break;
        }
    }

    /**
     * Posts under the shared ALERT_ID. The old notification is cancelled first on purpose:
     * replacing it in place would not reliably stop an insistent alarm that is still ringing.
     */
    private void postStatusAlert(Notification notification) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(ALERT_ID);
        nm.notify(ALERT_ID, notification);
    }

    /**
     * The loud one. Alarm channel (alarm audio attributes) + FLAG_INSISTENT so it keeps ringing
     * until the guardian opens or dismisses it. Tapping opens the dashboard directly, without
     * the biometric lock (design decision 10). Open map and Call are only offered when the
     * document has a location / phone number.
     */
    private Notification emergencyNotification(DocumentSnapshot doc, String name, long now) {
        String reason = doc.getString("lastEvent");
        String address = doc.getString("address");
        Long battery = doc.getLong("battery");
        int batteryPercent = battery != null ? battery.intValue() : -1;

        NotificationCompat.Builder b = new NotificationCompat.Builder(this, ALARM_CHANNEL)
                .setContentTitle(GuardianAlertText.emergencyTitle(name))
                .setContentText(GuardianAlertText.emergencySummary(reason, address))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(
                        GuardianAlertText.emergencyBody(reason, address, batteryPercent, now,
                                TimeZone.getDefault())))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setColor(Color.parseColor("#C62828"))
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setWhen(now)
                .setShowWhen(true)
                .setContentIntent(openDashboardIntent())
                .setAutoCancel(true);

        String mapsLink = GuardianAlertText.mapsLink(
                doc.getString("mapsLink"), doc.getDouble("latitude"), doc.getDouble("longitude"));
        if (mapsLink != null) {
            Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse(mapsLink))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            b.addAction(android.R.drawable.ic_menu_mapmode, "Open map",
                    PendingIntent.getActivity(this, REQUEST_OPEN_MAP, map, pendingFlags()));
        }

        String number = GuardianAlertText.dialNumber(doc.getString("phone"));
        if (number != null) {
            // ACTION_DIAL only opens the dialer with the number filled in: no CALL_PHONE permission.
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            b.addAction(android.R.drawable.ic_menu_call, GuardianAlertText.callLabel(name),
                    PendingIntent.getActivity(this, REQUEST_CALL, dial, pendingFlags()));
        }

        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT; // sound and vibration repeat until opened or cancelled
        return n;
    }

    // ---------- battery: 🟠 once per discharge ----------

    private void handleBattery(DocumentSnapshot doc, String name) {
        Long battery = doc.getLong("battery");
        int percent = battery != null ? battery.intValue() : -1;
        if (!batteryRule.shouldAlert(percent)) return;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.notify(BATTERY_ID, simpleNotification(ALERT_CHANNEL,
                GuardianAlertText.lowBatteryTitle(),
                GuardianAlertText.lowBatteryBody(name, percent),
                "#E65100", NotificationCompat.CATEGORY_STATUS));
    }

    // ---------- contact: 🟠 lost, 🟢 back ----------

    private void handleContact(DocumentSnapshot doc, String name, long now) {
        Timestamp seen = doc.getTimestamp("lastSeen");
        Long lastSeenMs = seen != null ? seen.toDate().getTime() : null;
        ConnectivityStatus.State state =
                ConnectivityStatus.classify(now, lastSeenMs, doc.getBoolean("monitoring"));

        GuardianAlertDecider.Contact contact = GuardianAlertDecider.decideContact(lastContact, state);
        lastContact = state;

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (contact == GuardianAlertDecider.Contact.LOST && lastSeenMs != null) {
            nm.notify(CONTACT_ID, simpleNotification(ALERT_CHANNEL,
                    GuardianAlertText.lostContactTitle(),
                    GuardianAlertText.lostContactBody(name, lastSeenMs, TimeZone.getDefault()),
                    "#E65100", NotificationCompat.CATEGORY_STATUS));
        } else if (contact == GuardianAlertDecider.Contact.RESTORED) {
            nm.notify(CONTACT_ID, simpleNotification(ALERT_CHANNEL,
                    GuardianAlertText.backInContactTitle(),
                    GuardianAlertText.backInContactBody(name),
                    "#2E7D32", NotificationCompat.CATEGORY_STATUS));
        }
    }

    // ---------- building blocks ----------

    /** A tappable notification (opens the dashboard) with a one-line text and a colour. */
    private Notification simpleNotification(String channel, String title, String text,
                                            String colorHex, String category) {
        return new NotificationCompat.Builder(this, channel)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setColor(Color.parseColor(colorHex))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT) // the channel sets the real behaviour
                .setCategory(category)
                .setContentIntent(openDashboardIntent())
                .setAutoCancel(true)
                .build();
    }

    /** Opens the guardian dashboard directly. The biometric check belongs to the Home card only. */
    private PendingIntent openDashboardIntent() {
        Intent open = new Intent(this, GuardianActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, REQUEST_OPEN_DASHBOARD, open, pendingFlags());
    }

    private static int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        nm.createNotificationChannel(new NotificationChannel(
                ONGOING_CHANNEL, "Monitoring", NotificationManager.IMPORTANCE_LOW));

        NotificationChannel alert = new NotificationChannel(
                ALERT_CHANNEL, "Emergency alerts", NotificationManager.IMPORTANCE_HIGH);
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        alert.enableLights(true);
        alert.setLightColor(Color.RED);
        nm.createNotificationChannel(alert);

        // The loud channel for POSSIBLE EMERGENCY. USAGE_ALARM plays on the alarm volume, so it is
        // heard even when the ringer is silent; with FLAG_INSISTENT on the notification it repeats.
        NotificationChannel alarm = new NotificationChannel(
                ALARM_CHANNEL, "Emergency alarm", NotificationManager.IMPORTANCE_HIGH);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        alarm.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs);
        alarm.enableVibration(true);
        alarm.setVibrationPattern(ALARM_VIBRATION);
        alarm.enableLights(true);
        alarm.setLightColor(Color.RED);
        // Best effort only: Android honours "bypass Do Not Disturb" for an app only after the user
        // allows it (channel settings > Override Do Not Disturb, or Do Not Disturb access). In the
        // default "priority only" mode an alarm-category notification with alarm audio is allowed
        // through anyway; "total silence" blocks it unless the user has granted the override.
        alarm.setBypassDnd(true);
        nm.createNotificationChannel(alarm);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(pollTask);
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}