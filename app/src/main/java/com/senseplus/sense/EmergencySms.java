package com.senseplus.sense;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends the emergency SMS (segment 26). Goes over the phone's cellular network, so it works with
 * no internet, which is the point of the fallback.
 *
 * Phone numbers are never written to the log: only counts and exception class names.
 */
final class EmergencySms {

    private static final String TAG = "SenseSms";

    private EmergencySms() {
    }

    static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Sends {@code text} to every number: divideMessage + sendMultipartTextMessage, so a long text
     * is split by the system instead of being refused.
     *
     * @return how many recipients the system accepted for sending. This is not a delivery
     *         receipt: no sent/delivered intents are requested.
     */
    @SuppressWarnings("deprecation") // SmsManager.getDefault() is the only way below API 31
    static int send(Context c, List<String> numbers, String text) {
        if (numbers == null || numbers.isEmpty() || text == null || text.isEmpty()) return 0;
        if (!hasPermission(c)) {
            Log.w(TAG, "SMS not sent: permission not granted");
            return 0;
        }
        SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? c.getSystemService(SmsManager.class)
                : SmsManager.getDefault();
        if (sms == null) return 0;

        ArrayList<String> parts = sms.divideMessage(text);
        int accepted = 0;
        for (String number : numbers) {
            try {
                sms.sendMultipartTextMessage(number, null, parts, null, null);
                accepted++;
            } catch (Exception e) {
                Log.w(TAG, "SMS could not be queued: " + e.getClass().getSimpleName());
            }
        }
        return accepted;
    }
}
