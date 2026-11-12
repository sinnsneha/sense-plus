package com.senseplus.sense;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.core.app.NotificationCompat;

/**
 * The notification side of "I'm on my way" (segment 25): the action button on the guardian's
 * 🟠 / 🔴 notifications, the quiet confirmation that replaces the alarm, and the failure notice.
 * Static helpers taking a Context, so the service, the dashboard and the receiver share them.
 */
final class AckNotifications {

    /** Intent action and extra of the broadcast the notification button sends to AckReceiver. */
    static final String ACTION_ACK = "com.senseplus.sense.GUARDIAN_ACK";
    static final String EXTRA_USER_NAME = "user_name";

    /**
     * Silent replies: importance LOW means no sound, no vibration, no heads-up. A new id because
     * channel settings are immutable once created (design decision 5).
     */
    static final String QUIET_CHANNEL = "guardian_quiet_v1";

    private static final int REQUEST_ACK = 24;
    /** Separate from the shared alert id, so a failure notice never replaces a ringing alarm. */
    private static final int ACK_FAILED_ID = 8;

    private AckNotifications() {
    }

    /** The "I'm on my way" button. {@code userName} is only used for the confirmation text. */
    static NotificationCompat.Action ackAction(Context context, String userName) {
        Intent intent = new Intent(context, AckReceiver.class)
                .setAction(ACTION_ACK)
                .putExtra(EXTRA_USER_NAME, userName == null ? "" : userName);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST_ACK, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_directions,
                GuardianAlertText.ackActionLabel(), pi).build();
    }

    /**
     * The reply went through: cancel the status notification (this is what stops an insistent
     * alarm) and post a quiet one in its place. It shares the alert id, so the next alert,
     * all clear or false alarm replaces it like any other status notice.
     */
    static void showSent(Context context, String userName) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureQuietChannel(nm);

        Notification n = new NotificationCompat.Builder(context, QUIET_CHANNEL)
                .setContentTitle(GuardianAlertText.ackSentTitle())
                .setContentText(GuardianAlertText.ackSentBody(userName))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setColor(Color.parseColor("#245E9C"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(GuardianService.openDashboardIntent(context))
                .setAutoCancel(true)
                .build();
        nm.cancel(GuardianService.ALERT_ID);
        nm.notify(GuardianService.ALERT_ID, n);
    }

    /**
     * The reply did not reach the server. The alarm is left ringing (the guardian has not told
     * anyone anything yet) and a separate notice says so, with the same button to try again.
     */
    static void showFailed(Context context, String userName) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        Notification n = new NotificationCompat.Builder(context, GuardianService.ALERT_CHANNEL)
                .setContentTitle(GuardianAlertText.ackFailedTitle())
                .setContentText(GuardianAlertText.ackFailedBody(userName))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(GuardianAlertText.ackFailedBody(userName)))
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setColor(Color.parseColor("#E65100"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(GuardianService.openDashboardIntent(context))
                .addAction(ackAction(context, userName))
                .setAutoCancel(true)
                .build();
        nm.notify(ACK_FAILED_ID, n);
    }

    private static void ensureQuietChannel(NotificationManager nm) {
        nm.createNotificationChannel(new NotificationChannel(
                QUIET_CHANNEL, "Your replies", NotificationManager.IMPORTANCE_LOW));
    }
}
