package com.senseplus.sense;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pure wording for the guardian's notifications, plus the small value helpers they need (map
 * link, dial number). The wording lives here so it is tested and GuardianService stays plumbing.
 * No Android imports; time zones are passed in.
 *
 * Wording is "possible emergency", never "emergency" (design decision 4).
 */
public final class GuardianAlertText {

    /** Used when the user's document has no name yet. */
    static final String FALLBACK_NAME = "the user";

    private GuardianAlertText() {
    }

    // ---------- names ----------

    /** The person inside a sentence: "waiting for Maya", or "the user" if no name is known. */
    public static String who(String name) {
        return hasName(name) ? name.trim() : FALLBACK_NAME;
    }

    /** The person at the start of a sentence: "Maya's phone ..." or "The user's phone ...". */
    private static String whoAtStart(String name) {
        return hasName(name) ? name.trim() : "The user";
    }

    private static boolean hasName(String name) {
        return name != null && !name.trim().isEmpty();
    }

    // ---------- 🟠 attention ----------

    public static String attentionTitle() {
        return "🟠 Possible fall";
    }

    public static String attentionBody(String name, int responseSeconds) {
        return "Possible fall — waiting for " + who(name) + " to respond (" + responseSeconds + " s)";
    }

    // ---------- 🔴 possible emergency ----------

    public static String emergencyTitle(String name) {
        return "🔴 POSSIBLE EMERGENCY" + (hasName(name) ? " — " + name.trim() : "");
    }

    /** One line for the collapsed notification: reason and place. */
    public static String emergencySummary(String reason, String address) {
        List<String> parts = new ArrayList<>();
        if (hasText(reason)) parts.add(reason.trim());
        if (hasText(address)) parts.add(address.trim());
        if (parts.isEmpty()) return "Open the dashboard for details";
        return join(parts, " · ");
    }

    /**
     * The expanded text: reason, place, battery and time, one per line. A missing reason or an
     * unknown battery (negative) leaves that line out; a missing address says so.
     */
    public static String emergencyBody(String reason, String address, int batteryPercent,
                                       long timeMs, TimeZone zone) {
        List<String> lines = new ArrayList<>();
        if (hasText(reason)) lines.add("Reason: " + reason.trim());
        lines.add("Near: " + (hasText(address) ? address.trim() : "location unknown"));
        if (batteryPercent >= 0) lines.add("Battery: " + batteryPercent + "%");
        lines.add("Time: " + clock(timeMs, zone));
        return join(lines, "\n");
    }

    /** Label for the call action: "Call Maya", or just "Call" when no name is known. */
    public static String callLabel(String name) {
        return hasName(name) ? "Call " + name.trim() : "Call";
    }

    // ---------- 🟢 resolved ----------

    public static String allClearTitle() {
        return "🟢 All clear";
    }

    public static String allClearBody(String name) {
        return whoAtStart(name) + " confirmed they are okay — they are safe now.";
    }

    public static String falseAlarmTitle() {
        return "🟢 False alarm";
    }

    public static String falseAlarmBody(String name) {
        return whoAtStart(name) + " responded in time — no emergency.";
    }

    // ---------- 🔵 "I'm on my way" (segment 25) ----------

    /** Label of the action on the 🟠 / 🔴 notifications and of the dashboard button. */
    public static String ackActionLabel() {
        return "I'm on my way";
    }

    public static String ackSentTitle() {
        return "🔵 Reply sent";
    }

    /** The quiet notification that replaces the alarm once the reply is sent. */
    public static String ackSentBody(String name) {
        return "You told " + who(name) + " you're on your way";
    }

    public static String ackFailedTitle() {
        return "🟠 Reply not sent";
    }

    public static String ackFailedBody(String name) {
        return "Couldn't tell " + who(name) + " you're on your way. Check this phone's connection"
                + " and tap I'm on my way again.";
    }

    // ---------- safe zone (segment 27) ----------

    public static String safeZoneExitTitle() {
        return "🟠 Left the safe zone";
    }

    /**
     * "Maya has left the safe zone (450 m from home)". With no distance (a document without a
     * location yet) the parenthesis is left out rather than guessed.
     */
    public static String safeZoneExitBody(String name, Double distanceM) {
        String text = whoAtStart(name) + " has left the safe zone";
        if (distanceM != null && distanceM >= 0) {
            text += " (" + GeoDistance.label(distanceM) + " from home)";
        }
        return text;
    }

    public static String safeZoneReturnTitle() {
        return "🟢 Back home";
    }

    public static String safeZoneReturnBody(String name) {
        return whoAtStart(name) + " is back home";
    }

    // ---------- battery ----------

    public static String lowBatteryTitle() {
        return "🟠 Low battery";
    }

    public static String lowBatteryBody(String name, int percent) {
        return whoAtStart(name) + "'s phone battery is low (" + percent
                + "%). Protection stops if it dies.";
    }

    // ---------- contact ----------

    public static String lostContactTitle() {
        return "🟠 Lost contact";
    }

    public static String lostContactBody(String name, long lastSeenMs, TimeZone zone) {
        return "Lost contact with " + who(name) + "'s phone since " + clock(lastSeenMs, zone);
    }

    public static String backInContactTitle() {
        return "🟢 Back in contact";
    }

    public static String backInContactBody(String name) {
        return "Back in contact with " + who(name);
    }

    // ---------- action values ----------

    /**
     * The link for the "Open map" action: the stored link if there is one, otherwise built from
     * the coordinates, otherwise null (no map action is offered).
     */
    public static String mapsLink(String storedLink, Double lat, Double lon) {
        if (hasText(storedLink)) return storedLink.trim();
        if (lat != null && lon != null) return "https://www.google.com/maps?q=" + lat + "," + lon;
        return null;
    }

    /**
     * The number for the "Call" action: digits and a leading '+' only (spaces, dashes and
     * brackets removed), or null when there is nothing to dial.
     */
    public static String dialNumber(String phone) {
        if (phone == null) return null;
        StringBuilder out = new StringBuilder();
        int digits = 0;
        String trimmed = phone.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
                digits++;
            } else if (c == '+' && out.length() == 0) {
                out.append(c);
            }
        }
        return digits > 0 ? out.toString() : null;
    }

    // ---------- helpers ----------

    private static String clock(long timeMs, TimeZone zone) {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(zone != null ? zone : TimeZone.getDefault());
        return f.format(new Date(timeMs));
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String join(List<String> parts, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
