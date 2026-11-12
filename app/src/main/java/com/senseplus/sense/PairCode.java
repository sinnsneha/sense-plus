package com.senseplus.sense;

import java.util.Locale;
import java.util.Random;

/**
 * The 6-digit code a user shows their guardian to link the two accounts (segment 23).
 * Pure Java: the caller passes the random source (SecureRandom in the app, a fixed one in tests).
 */
public final class PairCode {

    /** Digits in a code. Six digits = one million codes, and each lives only 10 minutes. */
    public static final int LENGTH = 6;
    private static final int RANGE = 1_000_000;

    private PairCode() { }

    /** A new code, zero-padded so it is always exactly six digits ("000042"). */
    public static String generate(Random random) {
        return String.format(Locale.US, "%06d", random.nextInt(RANGE));
    }

    /** True only for exactly six ASCII digits. */
    public static boolean isValidFormat(String code) {
        if (code == null || code.length() != LENGTH) return false;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    /**
     * What the guardian typed, cleaned up: spaces and dashes are dropped, so "123 456" and
     * "123-456" work. Returns null when the rest is not a valid code.
     */
    public static String normalize(String input) {
        if (input == null) return null;
        String digits = input.replace(" ", "").replace("-", "");
        return isValidFormat(digits) ? digits : null;
    }

    /** "123456" → "123 456": easier to read out and copy. */
    public static String display(String code) {
        if (!isValidFormat(code)) return code;
        return code.substring(0, 3) + " " + code.substring(3);
    }

    /** "123456" → "1, 2, 3, 4, 5, 6", so text-to-speech reads digits, not "one hundred…". */
    public static String spoken(String code) {
        if (!isValidFormat(code)) return code;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }
}
