package com.senseplus.sense;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure formatting for the guardian event timeline. No Android imports; the time
 * zone and "now" are injected so the result is deterministic in tests.
 */
public final class EventFormatter {

    private EventFormatter() {
    }

    public static String emoji(String type) {
        if (EventTypes.ATTENTION.equals(type)
                || EventTypes.LOW_BATTERY.equals(type)) return "🟠";
        if (EventTypes.EMERGENCY.equals(type)
                || EventTypes.MANUAL_HELP.equals(type)
                || EventTypes.VOICE_HELP.equals(type)
                || EventTypes.CODEWORD.equals(type)) return "🔴";
        if (EventTypes.ALL_CLEAR.equals(type)) return "🟢";
        return "⚪";
    }

    public static String label(String type) {
        if (EventTypes.ATTENTION.equals(type)) return "Attention";
        if (EventTypes.EMERGENCY.equals(type)) return "Emergency";
        if (EventTypes.MANUAL_HELP.equals(type)) return "Manual help";
        if (EventTypes.VOICE_HELP.equals(type)) return "Voice help";
        if (EventTypes.CODEWORD.equals(type)) return "Codeword";
        if (EventTypes.ALL_CLEAR.equals(type)) return "All clear";
        if (EventTypes.LOW_BATTERY.equals(type)) return "Low battery";
        return "Event";
    }

    /**
     * Dashboard battery line, e.g. "Battery: 78% · 30 s ago". The age comes from the time the
     * battery was last written ({@code batteryAtMs}); documents from older app versions have no
     * such time, so they show the percentage alone.
     */
    public static String batteryLine(Long percent, Long batteryAtMs, long nowMs) {
        if (percent == null) return "Battery: —";
        String line = "Battery: " + percent + "%";
        if (batteryAtMs != null) line += " · " + age(nowMs - batteryAtMs);
        return line;
    }

    /** "just now", "30 s ago", "5 min ago", "3 h ago", "2 d ago". A negative age counts as zero. */
    public static String age(long ageMs) {
        long seconds = Math.max(0L, ageMs) / 1000L;
        if (seconds < 5) return "just now";
        if (seconds < 60) return seconds + " s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " min ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + " h ago";
        return (hours / 24) + " d ago";
    }

    /**
     * HH:mm:ss when the event happened today in {@code zone}; otherwise a date
     * is included. A null server timestamp falls back to clientTime. If both are
     * null the row shows an em dash.
     */
    public static String timeString(Long serverTimeMs, Long clientTimeMs, long nowMs, TimeZone zone) {
        Long t = serverTimeMs != null ? serverTimeMs : clientTimeMs;
        if (t == null) return "—";
        TimeZone z = zone != null ? zone : TimeZone.getDefault();
        String pattern = sameDay(t, nowMs, z) ? "HH:mm:ss" : "dd MMM HH:mm:ss";
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
        f.setTimeZone(z);
        return f.format(new Date(t));
    }

    /** Complete row text: emoji + label + time on the first line, detail below. */
    public static String row(String type, String detail, Long serverTimeMs,
                             Long clientTimeMs, long nowMs, TimeZone zone) {
        String text = emoji(type) + "  " + label(type) + " · "
                + timeString(serverTimeMs, clientTimeMs, nowMs, zone);
        if (detail != null && !detail.trim().isEmpty()) text += "\n" + detail;
        return text;
    }

    private static boolean sameDay(long a, long b, TimeZone zone) {
        Calendar ca = Calendar.getInstance(zone, Locale.US);
        ca.setTimeInMillis(a);
        Calendar cb = Calendar.getInstance(zone, Locale.US);
        cb.setTimeInMillis(b);
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR)
                && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR);
    }
}
