package com.senseplus.sense;

/**
 * Pure decisions for the guardian's notifications. GuardianService only turns the answer into
 * a notification; every "should we alert?" question is answered here so it can be unit-tested.
 * No Android imports.
 */
public final class GuardianAlertDecider {

    /** What to tell the guardian about a change in the user's status. */
    public enum Alert {
        NONE,
        ATTENTION,             // 🟠 possible fall, the user's 15 s window is running
        EMERGENCY,             // 🔴 possible emergency
        ALL_CLEAR,             // 🟢 emergency ended
        FALSE_ALARM_RESOLVED   // 🟢 attention ended: the user answered in time
    }

    /** What to tell the guardian about a change in contact with the user's phone. */
    public enum Contact { NONE, LOST, RESTORED }

    private enum Level { NORMAL, ATTENTION, EMERGENCY }

    private GuardianAlertDecider() {
    }

    /**
     * @param previousStatus the status seen on the last poll, or null on the first poll
     * @param newStatus      the status just read; null (field missing) means "no information"
     *
     * Status strings are the ones UserMonitorService writes: "NORMAL", "ATTENTION" and
     * "POSSIBLE EMERGENCY". Anything else counts as normal, as the old service did.
     *
     * An alert already in progress on the first poll (previous == null) is announced; a normal
     * status on the first poll is not. An emergency followed directly by a new attention (a
     * poll missed the NORMAL in between) announces the new attention.
     */
    public static Alert decide(String previousStatus, String newStatus) {
        if (newStatus == null) return Alert.NONE;

        Level now = level(newStatus);
        Level before = previousStatus == null ? null : level(previousStatus);

        if (now == Level.EMERGENCY) return before == Level.EMERGENCY ? Alert.NONE : Alert.EMERGENCY;
        if (now == Level.ATTENTION) return before == Level.ATTENTION ? Alert.NONE : Alert.ATTENTION;

        // now is NORMAL: only an alert that was running can end
        if (before == Level.EMERGENCY) return Alert.ALL_CLEAR;
        if (before == Level.ATTENTION) return Alert.FALSE_ALARM_RESOLVED;
        return Alert.NONE;
    }

    /**
     * Lost contact = the phone just became OFFLINE while monitoring is on (ConnectivityStatus
     * already returns MONITORING_OFF, never OFFLINE, when the user switched monitoring off,
     * so a deliberate stop is never reported). Back in contact = it was OFFLINE and fresh data
     * has arrived (ONLINE, or RECENT if the guardian's clock runs a little ahead).
     *
     * @param previous the state on the last poll, or null on the first poll
     */
    public static Contact decideContact(ConnectivityStatus.State previous,
                                        ConnectivityStatus.State current) {
        if (current == null) return Contact.NONE;
        if (current == ConnectivityStatus.State.OFFLINE) {
            return previous == ConnectivityStatus.State.OFFLINE ? Contact.NONE : Contact.LOST;
        }
        if (previous == ConnectivityStatus.State.OFFLINE
                && (current == ConnectivityStatus.State.ONLINE
                || current == ConnectivityStatus.State.RECENT)) {
            return Contact.RESTORED;
        }
        return Contact.NONE;
    }

    private static Level level(String status) {
        if (status.contains("EMERGENCY")) return Level.EMERGENCY;
        if (status.contains("ATTENTION")) return Level.ATTENTION;
        return Level.NORMAL;
    }
}
