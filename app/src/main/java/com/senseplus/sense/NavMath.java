package com.senseplus.sense;

import java.util.Locale;

/**
 * Pure bearing / distance / direction maths for the Navigate Home screen.
 * No Android imports and no clock access, so it can be unit tested on the JVM.
 */
public final class NavMath {

    /** Within this distance the person is considered home (metres). */
    public static final double ARRIVED_M = 20.0;

    // Sector boundaries in degrees. Chosen so a small heading error still reads
    // as "straight ahead" while a real turn gets its own phrase.
    private static final double AHEAD_MAX_DEG = 15.0;
    private static final double SLIGHTLY_MAX_DEG = 45.0;
    private static final double SIDE_MAX_DEG = 135.0;

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
}
