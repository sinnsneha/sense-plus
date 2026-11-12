package com.senseplus.sense;

import java.util.Collection;

/**
 * Pure rules for the guardian's "I'm on my way" reply (segment 25). A guardian writes
 * users/{uid}.ack = { by, name, time }; the user's phone reads it during an alert and decides
 * here whether it is a new answer to the CURRENT alert, and what to say about it.
 *
 * No Android imports and no clock: every time is a parameter, so the rules are unit-tested.
 */
public final class AckRule {

    /**
     * Clock-skew allowance (ms). {@code ack.time} is the server's clock, the alert start is the
     * user phone's clock. Phone clocks are network-synced, so a few seconds of difference is the
     * realistic worst case; an ack up to this long BEFORE the alert start still counts.
     */
    public static final long CLOCK_SKEW_TOLERANCE_MS = 5_000L;

    /** Read the user's own document this often (ms) while an alert runs and nobody has answered. */
    public static final long POLL_MS = 5_000L;

    /**
     * After the first answer, keep reading this often (ms) so a SECOND guardian's reply can still
     * be announced. 0 stops polling at the first answer. 15 s: a second "also coming" is not
     * urgent, and it cuts the reads to a third of the 5 s rate.
     */
    public static final long SECOND_GUARDIAN_POLL_MS = 15_000L;

    /** The first announcement is repeated once after this long (ms) if the alert is still on. */
    public static final long REPEAT_AFTER_MS = 30_000L;

    /** What a polled ack means for this alert. */
    public enum Outcome {
        NONE,   // stale, malformed, or a guardian already announced
        FIRST,  // the first guardian to answer this alert
        ALSO    // another guardian answering after the first
    }

    static final String FIRST_FALLBACK = "Your guardian";
    static final String ALSO_FALLBACK = "Another guardian";

    private AckRule() {
    }

    /**
     * An ack counts only if it was written after the current alert started (minus the skew
     * allowance). Null, or no alert started (alertStartMs &lt;= 0), is never fresh, so an old
     * ack from a previous alert can never be announced.
     */
    public static boolean isFresh(Long ackTimeMs, long alertStartMs) {
        if (ackTimeMs == null || alertStartMs <= 0) return false;
        return ackTimeMs >= alertStartMs - CLOCK_SKEW_TOLERANCE_MS;
    }

    /**
     * @param ackBy            uid of the guardian who wrote the ack (null/empty = malformed)
     * @param alreadyAnnounced uids already announced for this alert (null = none)
     *
     * A guardian is announced once per alert: tapping the button again, or a poll that sees the
     * same ack twice, gives NONE.
     */
    public static Outcome classify(Long ackTimeMs, String ackBy, long alertStartMs,
                                   Collection<String> alreadyAnnounced) {
        if (!isFresh(ackTimeMs, alertStartMs)) return Outcome.NONE;
        if (ackBy == null || ackBy.isEmpty()) return Outcome.NONE;
        boolean anyoneYet = alreadyAnnounced != null && !alreadyAnnounced.isEmpty();
        if (anyoneYet && alreadyAnnounced.contains(ackBy)) return Outcome.NONE;
        return anyoneYet ? Outcome.ALSO : Outcome.FIRST;
    }

    /** Delay (ms) before the next read of the user's own document; 0 means stop reading. */
    public static long nextPollDelayMs(boolean someoneAnswered) {
        return someoneAnswered ? SECOND_GUARDIAN_POLL_MS : POLL_MS;
    }

    /** What the user's phone says aloud and shows in large text. Empty for NONE. */
    public static String spoken(Outcome outcome, String guardianName) {
        switch (outcome) {
            case FIRST:
                return name(guardianName, FIRST_FALLBACK) + " has seen your alert and is on the way.";
            case ALSO:
                return name(guardianName, ALSO_FALLBACK) + " is also coming.";
            case NONE:
            default:
                return "";
        }
    }

    /**
     * The same news worded for someone else reading it (the event timeline and the ongoing
     * notification), so it never says "your". Empty for NONE.
     */
    public static String eventDetail(Outcome outcome, String guardianName) {
        switch (outcome) {
            case FIRST:
                return name(guardianName, "A guardian") + " is on the way";
            case ALSO:
                return name(guardianName, ALSO_FALLBACK) + " is also coming";
            case NONE:
            default:
                return "";
        }
    }

    private static String name(String guardianName, String fallback) {
        return guardianName != null && !guardianName.trim().isEmpty() ? guardianName.trim() : fallback;
    }
}
