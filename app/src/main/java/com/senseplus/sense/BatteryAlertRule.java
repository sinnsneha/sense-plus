package com.senseplus.sense;

/**
 * Low-battery rule with hysteresis, used by BOTH phones: the user phone speaks a warning, the
 * guardian phone shows a notification. Each phone keeps its own instance. No Android imports.
 *
 * Without hysteresis a level hovering around the threshold (14, 15, 14 ...) would alert again
 * on every dip.
 */
public final class BatteryAlertRule {

    // Percent. Below 15 there is roughly an hour of use left; early enough to find a charger.
    public static final int ALERT_BELOW = 15;
    // Percent. The rule re-arms only once the phone has charged well past the threshold.
    public static final int REARM_ABOVE = 20;

    private boolean armed = true;

    /**
     * @param percent battery level; any negative value means "unknown" and is ignored
     * @return true exactly once each time the level drops below {@link #ALERT_BELOW}
     */
    public boolean shouldAlert(int percent) {
        if (percent < 0) return false;
        if (percent > REARM_ABOVE) {
            armed = true;
            return false;
        }
        if (armed && percent < ALERT_BELOW) {
            armed = false;
            return true;
        }
        return false;
    }
}
