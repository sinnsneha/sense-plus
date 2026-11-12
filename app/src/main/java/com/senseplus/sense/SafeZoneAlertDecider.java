package com.senseplus.sense;

/**
 * What to tell the guardian about a change in the user's "safeZone" field (segment 27). The same
 * pattern as GuardianAlertDecider (segment 24): GuardianService only turns the answer into a
 * notification, and every "should we alert?" question is answered here so it can be unit-tested.
 * No Android imports.
 */
public final class SafeZoneAlertDecider {

    /** Values of users/{uid}.safeZone, written by the user's phone when the state changes. */
    public static final String OUTSIDE = "outside";
    public static final String INSIDE = "inside";

    public enum Alert {
        NONE,
        LEFT,       // 🟠 the user went beyond the safe zone
        RETURNED    // 🟢 the user is back inside it
    }

    private SafeZoneAlertDecider() {
    }

    /**
     * @param previous the value seen on the last poll, or null on the first poll / while unknown
     * @param current  the value just read; null or anything unrecognised means "no information"
     *
     * One alert per transition: the same value twice says NONE. As with segment 24's alerts, an
     * "outside" already in progress on the first poll is announced (the guardian may not have
     * been watching when it happened); an "inside" on the first poll is not.
     */
    public static Alert decide(String previous, String current) {
        if (OUTSIDE.equals(current)) {
            return OUTSIDE.equals(previous) ? Alert.NONE : Alert.LEFT;
        }
        if (INSIDE.equals(current)) {
            return OUTSIDE.equals(previous) ? Alert.RETURNED : Alert.NONE;
        }
        return Alert.NONE;
    }
}
