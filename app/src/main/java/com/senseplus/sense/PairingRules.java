package com.senseplus.sense;

import java.util.Locale;

/**
 * Timing rules for guardian pairing and for the guardian's background poll (segment 23).
 * Pure Java: every method takes the current time as a parameter.
 */
public final class PairingRules {

    /** A pairing code is valid for 10 minutes: long enough to read it out, short enough to be safe. */
    public static final long CODE_TTL_MS = 10 * 60_000L;

    /** How often each phone checks during pairing (user: new requests; guardian: approval). */
    public static final long PAIRING_POLL_MS = 2_000L;

    /**
     * Polls in a row that may be refused before the guardian treats itself as unlinked.
     * One refusal can be a hiccup; three in a row (6–15 s) means the user removed this guardian.
     */
    public static final int DENIED_POLLS_TO_UNLINK = 3;

    /** Background poll while the user is NORMAL: 5 s keeps one guardian at ~17k reads/day. */
    public static final long POLL_NORMAL_MS = 5_000L;

    /** Background poll during a possible fall or possible emergency: as fast as the dashboard. */
    public static final long POLL_ALERT_MS = 2_000L;

    private PairingRules() { }

    public static boolean isExpired(long nowMs, long expiresAtMs) {
        return nowMs >= expiresAtMs;
    }

    /** Whole seconds left, rounded up, never negative. */
    public static long secondsLeft(long nowMs, long expiresAtMs) {
        long ms = expiresAtMs - nowMs;
        if (ms <= 0) return 0;
        return (ms + 999) / 1000;
    }

    /** 545 → "9:05". */
    public static String countdown(long secondsLeft) {
        long s = Math.max(0, secondsLeft);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    public static boolean shouldUnlink(int consecutiveDenied) {
        return consecutiveDenied >= DENIED_POLLS_TO_UNLINK;
    }

    /**
     * Interval for GuardianService's next poll, from the last status it saw (design decision 1).
     * Anything that is not clearly calm (ATTENTION, POSSIBLE EMERGENCY) gets the fast interval.
     */
    public static long backgroundPollMs(String lastStatus) {
        if (lastStatus == null) return POLL_NORMAL_MS;
        if (lastStatus.contains("ATTENTION") || lastStatus.contains("EMERGENCY")) return POLL_ALERT_MS;
        return POLL_NORMAL_MS;
    }
}
