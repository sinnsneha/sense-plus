package com.senseplus.sense;

/**
 * Decides, fix by fix, when the user has LEFT the safe zone around Home and when they have
 * RETURNED (segment 27). Pure Java with no clock and no Android imports.
 *
 * Three protections stop a wobbling GPS from flooding the guardian with alerts:
 * <ul>
 *   <li><b>Accuracy gate:</b> a fix worse than {@link #MAX_ACCURACY_M} says little about where
 *       the phone is, so it is ignored (it neither counts nor resets anything).</li>
 *   <li><b>Hysteresis:</b> leaving needs the user to be clearly outside, {@code distance -
 *       accuracy > radius + 50}; returning needs them to be clearly inside, {@code distance +
 *       accuracy < radius}. Standing on the edge changes nothing.</li>
 *   <li><b>Confirmation:</b> two clearly-outside fixes in a row are needed before LEFT.</li>
 * </ul>
 * One LEFT and one RETURNED per real transition: repeated fixes in the same state say NO_CHANGE.
 */
public final class SafeZoneRule {

    public enum Result { NO_CHANGE, LEFT, RETURNED }

    /** Fixes worse than this are ignored (metres). Indoors a Wi-Fi fix is 15-60 m; 100 m is still usable. */
    public static final double MAX_ACCURACY_M = 100.0;

    /**
     * How far past the radius the user must be, beyond the fix's own error (metres). 50 m means a
     * user pacing along the boundary does not flip the state back and forth.
     */
    public static final double EXIT_MARGIN_M = 50.0;

    /** Clearly-outside fixes in a row needed for LEFT: one stray fix is not a walk away. */
    public static final int EXIT_CONFIRMATIONS = 2;

    private boolean outside = false;
    private int outsideFixes = 0;

    /** True after LEFT and until RETURNED (or reset). */
    public boolean isOutside() {
        return outside;
    }

    /**
     * Starts from a known state, e.g. the "safeZone" the phone last wrote to Firestore, so a
     * restart of monitoring while the user is already outside does not log a second exit.
     */
    public void restore(boolean wasOutside) {
        outside = wasOutside;
        outsideFixes = 0;
    }

    /** Forgets everything: the zone was switched off or Home changed. */
    public void reset() {
        outside = false;
        outsideFixes = 0;
    }

    /**
     * @param distanceM distance from the fix to Home, in metres
     * @param accuracyM the fix's accuracy radius in metres; zero, negative or NaN means unknown and is ignored
     * @param radiusM   the safe zone radius in metres
     * @param enabled   false = the zone is off: always NO_CHANGE, and the state is forgotten
     */
    public Result onFix(double distanceM, double accuracyM, double radiusM, boolean enabled) {
        if (!enabled) {
            reset();
            return Result.NO_CHANGE;
        }
        if (Double.isNaN(accuracyM) || accuracyM <= 0 || accuracyM > MAX_ACCURACY_M) {
            return Result.NO_CHANGE; // too vague (or missing) to say anything either way
        }

        if (!outside) {
            if (distanceM - accuracyM > radiusM + EXIT_MARGIN_M) {
                outsideFixes++;
                if (outsideFixes >= EXIT_CONFIRMATIONS) {
                    outside = true;
                    outsideFixes = 0;
                    return Result.LEFT;
                }
            } else {
                outsideFixes = 0; // not clearly outside: the run of fixes is broken
            }
            return Result.NO_CHANGE;
        }

        if (distanceM + accuracyM < radiusM) {
            outside = false;
            outsideFixes = 0;
            return Result.RETURNED;
        }
        return Result.NO_CHANGE;
    }
}
