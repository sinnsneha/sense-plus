package com.senseplus.sense;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

/**
 * "Protection is off after a restart" (segment 28). Posted by BootReceiver; the tap is what
 * brings protection back, because Android does not let a boot receiver start these services.
 *
 * Why a notification and not a silent restart (checked against the Android developer docs):
 * on Android 15 (targeting API 35+) a BOOT_COMPLETED receiver may not start a foreground service
 * of type dataSync or microphone (microphone since Android 14), and a service that needs a
 * while-in-use permission (location, microphone, camera) cannot be created from the background
 * at all, with BOOT_COMPLETED not among the exemptions. UserMonitorService is
 * location|microphone|dataSync and GuardianService is dataSync, so both would be refused. A tap
 * on a notification opens an activity, and the app may start a foreground service from there.
 */
final class ReminderNotifications {

    /** High importance so it is seen; a new id because channel settings are immutable (decision 5). */
    static final String CHANNEL = "reminder_v1";

    private static final int NOTIFICATION_ID = 31;
    private static final int REQUEST_TAP = 31;

    private ReminderNotifications() {
    }

    static void post(Context context, BootReminderRule.Reminder reminder) {
        if (reminder == BootReminderRule.Reminder.NONE) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Protection reminders", NotificationManager.IMPORTANCE_HIGH);
        channel.enableVibration(true);
        nm.createNotificationChannel(channel);

        String text = BootReminderRule.text(reminder);
        Notification n = new NotificationCompat.Builder(context, CHANNEL)
                .setContentTitle(BootReminderRule.title(reminder))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setColor(Color.parseColor("#E65100"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(tapIntent(context, reminder))
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIFICATION_ID, n);
    }

    /** Removes the reminder: protection is back (or the person signed out), so it is stale. */
    static void cancel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    /**
     * User: Home underneath, the Safety screen on top with auto_start=true, which starts monitoring
     * (the app is then in the foreground, so starting the service is allowed). Back from there
     * goes to Home instead of leaving the app. Guardian: just Home, which starts GuardianService.
     */
    private static PendingIntent tapIntent(Context context, BootReminderRule.Reminder reminder) {
        Intent home = new Intent(context, HomeActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        if (reminder == BootReminderRule.Reminder.GUARDIAN) {
            return TaskStackBuilder.create(context).addNextIntent(home)
                    .getPendingIntent(REQUEST_TAP, flags);
        }
        Intent safety = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_AUTO_START, true);
        return TaskStackBuilder.create(context).addNextIntent(home).addNextIntent(safety)
                .getPendingIntent(REQUEST_TAP, flags);
    }
}
