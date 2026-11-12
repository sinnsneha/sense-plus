package com.senseplus.sense;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure phone-number rules for "Call my guardian" and the SMS fallback (segment 26). No Android
 * imports, so they are unit-tested.
 */
public final class PhoneRules {

    /** Fewer digits than this cannot be a dialable number (the shortest real ones have 7). */
    static final int MIN_DIGITS = 7;

    /** E.164 allows at most 15 digits including the country code; more is a typing mistake. */
    static final int MAX_DIGITS = 15;

    private PhoneRules() {
    }

    /**
     * Strips spaces, dashes, dots and brackets; keeps digits and a leading '+'.
     *
     * @return the clean number, or null if it is null or has fewer than 7 (or more than 15) digits
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder();
        int digits = 0;
        String s = raw.trim();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                out.append(c);
                digits++;
            } else if (c == '+' && out.length() == 0) {
                out.append(c); // only a '+' before the first digit counts, e.g. "(+91) 98765 43210"
            }
        }
        return digits >= MIN_DIGITS && digits <= MAX_DIGITS ? out.toString() : null;
    }

    /**
     * The number "Call my guardian" dials: the first guardian with a usable phone, otherwise the
     * Medical ID emergency contact, otherwise null (the screen then asks for a number).
     *
     * @param guardianPhones guardians' phones in the order they were added (may be null)
     */
    public static String callNumber(List<String> guardianPhones, String emergencyContactPhone) {
        if (guardianPhones != null) {
            for (String phone : guardianPhones) {
                String n = normalize(phone);
                if (n != null) return n;
            }
        }
        return normalize(emergencyContactPhone);
    }

    /**
     * Everyone who gets the emergency SMS: every guardian phone, then the emergency contact.
     * Cleaned, and each person once: the same number written with and without a country code
     * ("+919876543210" and "9876543210") counts as one, so nobody gets two texts.
     */
    public static List<String> smsRecipients(List<String> guardianPhones, String emergencyContactPhone) {
        List<String> out = new ArrayList<>();
        if (guardianPhones != null) {
            for (String phone : guardianPhones) addIfNew(out, normalize(phone));
        }
        addIfNew(out, normalize(emergencyContactPhone));
        return out;
    }

    /**
     * The phones of a user's guardians, in the order of users/{uid}.guardians, read from
     * users/{uid}.guardianInfo ({ guardianUid: { name, phone } }). Guardians without a usable
     * phone are left out.
     */
    public static List<String> guardianPhonesInOrder(List<?> guardianIds, Map<?, ?> guardianInfo) {
        List<String> out = new ArrayList<>();
        if (guardianIds == null || guardianInfo == null) return out;
        for (Object id : guardianIds) {
            Object entry = guardianInfo.get(id);
            if (!(entry instanceof Map)) continue;
            Object phone = ((Map<?, ?>) entry).get("phone");
            String n = phone instanceof String ? normalize((String) phone) : null;
            if (n != null) out.add(n);
        }
        return out;
    }

    /** True when two cleaned numbers are the same line: equal digits, or one is the other with a country code. */
    static boolean sameNumber(String a, String b) {
        String da = digitsOnly(a);
        String db = digitsOnly(b);
        if (da.equals(db)) return true;
        String shorter = da.length() <= db.length() ? da : db;
        String longer = da.length() <= db.length() ? db : da;
        return shorter.length() >= MIN_DIGITS && longer.endsWith(shorter);
    }

    private static void addIfNew(List<String> list, String number) {
        if (number == null) return;
        for (String existing : list) {
            if (sameNumber(existing, number)) return;
        }
        list.add(number);
    }

    private static String digitsOnly(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }
}
