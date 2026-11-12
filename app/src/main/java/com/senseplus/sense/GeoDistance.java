package com.senseplus.sense;

import java.util.Locale;

/**
 * Pure distance maths for the safe zone (segment 27). No Android imports, so it is unit-tested
 * on the JVM and gives the same answer on the user's phone and on the guardian's.
 */
public final class GeoDistance {

    /** Mean Earth radius in metres (IUGG). The haversine error is under 0.5 %, far finer than a GPS fix. */
    static final double EARTH_RADIUS_M = 6_371_008.8;

    private GeoDistance() {
    }

    /** Great-circle distance in metres between two latitude / longitude points in degrees. */
    public static double metres(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** "450 m" (nearest 10 m) under a kilometre, otherwise "1.2 km". */
    public static String label(double metres) {
        if (metres < 1000.0) return Math.round(metres / 10.0) * 10L + " m";
        return String.format(Locale.US, "%.1f km", metres / 1000.0);
    }
}
