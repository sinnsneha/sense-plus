package com.senseplus.sense;

/**
 * What the guardian dashboard's safe-zone chip says (segment 27). Pure: the screen turns the
 * state into a colour and a string. No Android imports.
 */
public final class SafeZoneStatus {

    public enum State {
        NO_HOME,   // nobody has saved a Home yet
        OFF,       // there is a Home but the safe zone is switched off
        WAITING,   // on, but the user's phone has not reported inside / outside yet
        INSIDE,
        OUTSIDE
    }

    private SafeZoneStatus() {
    }

    /**
     * @param hasHome  the document has a usable Home location
     * @param enabled  home.enabled
     * @param safeZone users/{uid}.safeZone ("inside" / "outside"), or null
     */
    public static State classify(boolean hasHome, boolean enabled, String safeZone) {
        if (!hasHome) return State.NO_HOME;
        if (!enabled) return State.OFF;
        if (SafeZoneAlertDecider.OUTSIDE.equals(safeZone)) return State.OUTSIDE;
        if (SafeZoneAlertDecider.INSIDE.equals(safeZone)) return State.INSIDE;
        return State.WAITING;
    }
}
