package com.senseplus.sense;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After the phone restarts or the app is updated, nothing runs: not fall detection, not guardian
 * alerts (segment 28). This receiver does NOT try to start the services (Android refuses it, see
 * ReminderNotifications); it tells the person who wanted protection, and the tap restores it.
 *
 * Declared with android:exported="false": the system's own broadcasts (BOOT_COMPLETED and
 * MY_PACKAGE_REPLACED) are still delivered to it, and no other app can send it anything.
 * BOOT_COMPLETED arrives after the user has unlocked the phone, so the preferences are readable.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        Context app = context.getApplicationContext();

        // Everything is read from the phone: no network is needed, and none may be waited for here.
        BootReminderRule.Reminder reminder = BootReminderRule.decide(
                AccountStore.hasCachedRole(app),
                AccountStore.role(app),
                AccountStore.monitoringWanted(app),
                AccountStore.isLinked(app));
        ReminderNotifications.post(app, reminder);
    }
}
