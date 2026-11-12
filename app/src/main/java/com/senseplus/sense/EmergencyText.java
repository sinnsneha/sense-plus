package com.senseplus.sense;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure wording for the emergency SMS and for reading the Medical ID aloud (segment 26).
 * No Android imports.
 *
 * Wording is "possible emergency", never "emergency" (design decision 4).
 */
public final class EmergencyText {

    /** Used when the user has no name on file. */
    static final String FALLBACK_NAME = "the user";

    private EmergencyText() {
    }

    /**
     * The emergency SMS:
     * "SENSE+: possible emergency for Maya (No response after fall). Location: https://... (+/-12 m). Sent automatically."
     *
     * Plain ASCII on purpose. One character outside the GSM 7-bit alphabet (such as "±") makes the
     * phone send the whole text as UCS-2, which fits 70 characters per part instead of 160, so a
     * typical message would cost three SMS per recipient instead of one.
     *
     * @param mapsLink  the last known location as a link, or null/blank when there is no fix
     * @param accuracyM the fix's accuracy in metres; zero, negative or NaN means unknown
     */
    public static String sms(String name, String reason, String mapsLink, double accuracyM) {
        StringBuilder sb = new StringBuilder("SENSE+: possible emergency for ");
        sb.append(hasText(name) ? name.trim() : FALLBACK_NAME);
        if (hasText(reason)) sb.append(" (").append(reason.trim()).append(')');
        sb.append(". ");
        if (hasText(mapsLink)) {
            sb.append("Location: ").append(mapsLink.trim());
            if (accuracyM > 0 && !Double.isNaN(accuracyM) && !Double.isInfinite(accuracyM)) {
                sb.append(" (+/-").append(Math.round(accuracyM)).append(" m)");
            }
            sb.append(". ");
        } else {
            sb.append("Phone location unknown. ");
        }
        sb.append("Sent automatically.");
        return sb.toString();
    }

    /**
     * A Maps link for the SMS, with the coordinates cut to 5 decimals (about 1 m, far finer than
     * a phone's fix) so the whole text stays inside one 160-character SMS. The full-precision
     * link in Firestore is longer and is not used here.
     */
    public static String mapsLink(double latitude, double longitude) {
        return String.format(Locale.US, "https://www.google.com/maps?q=%.5f,%.5f", latitude, longitude);
    }

    /**
     * What "Read aloud" says. Empty fields are skipped; the blood group is spoken as
     * "A positive"; the phone number is spoken digit by digit so it can be written down.
     */
    public static String spokenMedicalId(MedicalId id) {
        if (id == null || id.isEmpty()) {
            return "No medical information has been saved on this phone.";
        }
        List<String> parts = new ArrayList<>();
        parts.add("Medical ID" + (hasText(id.name) ? " for " + id.name : "") + ".");
        if (hasText(id.age)) parts.add("Age " + id.age + ".");
        if (id.hasBloodGroup()) parts.add("Blood group " + spokenBloodGroup(id.bloodGroup) + ".");
        addSentence(parts, "Allergies: ", id.allergies);
        addSentence(parts, "Medical conditions: ", id.conditions);
        addSentence(parts, "Medications: ", id.medications);
        addSentence(parts, "Notes: ", id.notes);

        boolean hasContactName = hasText(id.contactName);
        boolean hasContactPhone = hasText(id.contactPhone);
        if (hasContactName || hasContactPhone) {
            StringBuilder c = new StringBuilder("Emergency contact: ");
            if (hasContactName) c.append(id.contactName);
            if (hasContactName && hasContactPhone) c.append(", phone ");
            if (!hasContactName && hasContactPhone) c.append("phone ");
            if (hasContactPhone) c.append(spokenPhone(id.contactPhone));
            parts.add(c.append('.').toString());
        }
        return join(parts, " ");
    }

    /** "A+" becomes "A positive", "O-" (or the typographic minus) "O negative". */
    static String spokenBloodGroup(String group) {
        String g = group.trim();
        if (g.endsWith("+")) return g.substring(0, g.length() - 1) + " positive";
        if (g.endsWith("-") || g.endsWith("−")) return g.substring(0, g.length() - 1) + " negative";
        return g;
    }

    /** "+91 98765 43210" becomes "plus 9 1 9 8 7 6 5 4 3 2 1 0", so a speech engine reads each digit. */
    static String spokenPhone(String phone) {
        StringBuilder sb = new StringBuilder();
        String p = phone.trim();
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '+' && sb.length() == 0) {
                sb.append("plus");
            } else if (c >= '0' && c <= '9') {
                if (sb.length() > 0) sb.append(' ');
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void addSentence(List<String> parts, String label, String value) {
        if (!hasText(value)) return;
        String v = value.trim();
        boolean ends = v.endsWith(".") || v.endsWith("!") || v.endsWith("?");
        parts.add(label + v + (ends ? "" : "."));
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
