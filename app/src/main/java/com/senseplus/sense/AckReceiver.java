package com.senseplus.sense;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Handles the "I'm on my way" button on the guardian's 🟠 / 🔴 notifications (segment 25).
 * Not exported: only this app's own PendingIntent can reach it.
 *
 * goAsync() keeps the receiver alive until the write is confirmed or GuardianAck times out
 * (8 s), instead of returning at once and leaving the answer to chance.
 */
public class AckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !AckNotifications.ACTION_ACK.equals(intent.getAction())) return;

        final Context app = context.getApplicationContext();
        final String userName = intent.getStringExtra(AckNotifications.EXTRA_USER_NAME);
        final PendingResult pending = goAsync();

        GuardianAck.send(app, new GuardianAck.Callback() {
            @Override public void onSent() {
                AckNotifications.showSent(app, userName);
                pending.finish();
            }

            @Override public void onFailed() {
                AckNotifications.showFailed(app, userName);
                pending.finish();
            }
        });
    }
}
