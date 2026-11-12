package com.senseplus.sense;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure classification of the user phone's connectivity from the server-side
 * lastSeen timestamp and the monitoring flag. No Android imports.
 */
public final class ConnectivityStatus {

    public enum State { NEVER, MONITORING_OFF, ONLINE, RECENT, OFFLINE }

    private static final long ONLINE_MS = 60_000L;       // under a minute
    private static final long RECENT_MS = 5 * 60_000L;   // up to five minutes

    private ConnectivityStatus() {
    }

    public static State classify(long nowMs, Long lastSeenMs, Boolean monitoring) {
        if (lastSeenMs == null) return State.NEVER;
        if (Boolean.FALSE.equals(monitoring)) return State.MONITORING_OFF;

        // Clock skew can put lastSeen slightly ahead of "now"; treat as fresh.
        long age = Math.max(0L, nowMs - lastSeenMs);
        if (age < ONLINE_MS) return State.ONLINE;
        if (age <= RECENT_MS) return State.RECENT;
        return State.OFFLINE;
    }

    public static String label(long nowMs, Long lastSeenMs, Boolean monitoring) {
        return label(nowMs, lastSeenMs, monitoring, TimeZone.getDefault());
    }

    public static String label(long nowMs, Long lastSeenMs, Boolean monitoring, TimeZone zone) {
        State state = classify(nowMs, lastSeenMs, monitoring);
        switch (state) {
            case NEVER:
                return "Not connected yet";
            case MONITORING_OFF:
                return "Monitoring is off";
            case ONLINE:
                return "Online";
            case RECENT:
                long age = Math.max(0L, nowMs - lastSeenMs);
                long minutes = Math.max(1L, age / 60_000L);
                return "Last seen " + minutes + " min ago";
            case OFFLINE:
            default:
                TimeZone z = zone != null ? zone : TimeZone.getDefault();
                SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
                f.setTimeZone(z);
                String time = f.format(new Date(lastSeenMs));
                return "Offline / no signal (last seen " + time + ")";
        }
    }
}
