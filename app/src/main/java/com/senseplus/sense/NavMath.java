package com.senseplus.sense;

import java.util.Locale;

/**
 * Pure bearing / distance / direction maths for the Navigate Home screen.
 * No Android imports and no clock access, so it can be unit tested on the JVM.
 */
public final class NavMath {

    /** Within this distance the person is considered home (metres). */
    public static final double ARRIVED_M = 20.0;

    /**
     * Accuracy at or below this can be saved without asking (metres).
     * Indoors a Wi-Fi fix is often 15-60 m, so 30 m was too strict.
     */
    public static final float HOME_GOOD_ACCURACY_M = 50f;

    /**
     * Above this the fix is too rough to be a useful Home point (metres).
     * 150 m is still the right building in a dense area but is never silent.
     */
    public static final float HOME_MAX_ACCURACY_M = 150f;

    /** Beyond this distance walking directions are more useful than an arrow (metres). */
    public static final double MAPS_SUGGEST_M = 300.0;

    /** Pitch (radians) beyond which the phone counts as held upright. 45 degrees. */
    private static final double UPRIGHT_THRESHOLD_RAD = Math.toRadians(45.0);

    private static final String MAPS_HINT =
            "For step-by-step walking directions, tap Walking directions.";

    // Sector boundaries in degrees. Chosen so a small heading error still reads
    // as "straight ahead" while a real turn gets its own phrase.
    private static final double AHEAD_MAX_DEG = 15.0;
    private static final double SLIGHTLY_MAX_DEG = 45.0;
    private static final double SIDE_MAX_DEG = 135.0;

    /** What to do with a measured Home fix. */
    public enum HomeSaveDecision {
        SAVE,
        CONFIRM,
        REFUSE
    }

    private NavMath() {
    }

    /**
     * Wraps an angle into (-180, 180].
     * 190 becomes -170, -190 becomes 170, and both 180 and -180 become 180.
     */
    public static double normalizeDeg(double deg) {
        double r = deg % 360.0;
        if (r > 180.0) r -= 360.0;
        if (r <= -180.0) r += 360.0;
        return r;
    }

    /**
     * Human phrase for a bearing relative to where the phone is pointing.
     * Negative values mean the target is to the left.
     */
    public static String direction(double relativeDeg) {
        double r = normalizeDeg(relativeDeg);
        double abs = Math.abs(r);
        if (abs < AHEAD_MAX_DEG) return "straight ahead";
        String side = r < 0 ? "left" : "right";
        if (abs < SLIGHTLY_MAX_DEG) return "slightly " + side;
        if (abs <= SIDE_MAX_DEG) return side;
        return "behind you";
    }

    /**
     * Distance in words: under 1 km rounded to the nearest 10 m, otherwise
     * kilometres to one decimal place.
     */
    public static String distancePhrase(double metres) {
        if (metres < 1000.0) {
            long rounded = Math.round(metres / 10.0) * 10L;
            return rounded + " metres";
        }
        return String.format(Locale.US, "%.1f kilometres", metres / 1000.0);
    }

    /**
     * Full spoken sentence, e.g. "Home is 320 metres, turn slightly left".
     * Arrival wins over any direction phrase.
     */
    public static String sentence(double metres, double relativeDeg) {
        if (metres < ARRIVED_M) return "You have arrived home";
        String dir = direction(relativeDeg);
        if ("straight ahead".equals(dir)) {
            return "Home is " + distancePhrase(metres) + ", straight ahead";
        }
        if ("behind you".equals(dir)) {
            return "Home is " + distancePhrase(metres) + ", turn around";
        }
        return "Home is " + distancePhrase(metres) + ", turn " + dir;
    }

    /**
     * Same sentence as {@link #sentence(double, double)}, with an optional hint
     * that Google Maps can give step-by-step walking directions. The hint is
     * only added past {@link #MAPS_SUGGEST_M} so short walks stay concise.
     */
    public static String sentence(double metres, double relativeDeg, boolean walkingDirectionsHint) {
        String base = sentence(metres, relativeDeg);
        if (walkingDirectionsHint && suggestMaps(metres)) {
            return base + ". " + MAPS_HINT;
        }
        return base;
    }

    /**
     * Decides whether a measured fix is good enough for Home.
     * 50 m or better saves silently; up to 150 m asks first; worse is refused.
     */
    public static HomeSaveDecision homeSaveDecision(float accuracyM) {
        if (accuracyM <= HOME_GOOD_ACCURACY_M) return HomeSaveDecision.SAVE;
        if (accuracyM <= HOME_MAX_ACCURACY_M) return HomeSaveDecision.CONFIRM;
        return HomeSaveDecision.REFUSE;
    }

    /**
     * True when the phone is held upright (pitch beyond 45 degrees either way),
     * where the raw rotation-vector heading is unstable.
     */
    public static boolean isUpright(double pitchRad) {
        return Math.abs(pitchRad) > UPRIGHT_THRESHOLD_RAD;
    }

    /** True when the walk is long enough that Google Maps walking directions help. */
    public static boolean suggestMaps(double distanceM) {
        return distanceM > MAPS_SUGGEST_M;
    }
}
