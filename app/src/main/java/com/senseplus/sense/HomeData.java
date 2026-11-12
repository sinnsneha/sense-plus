package com.senseplus.sense;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared Home block, users/{uid}.home (segment 27): where Home is and how the safe zone
 * around it is set. The user's phone and the guardian both write it, so who wins when they
 * differ is decided here by {@link #updatedAt}. No Android imports.
 *
 * Fields: lat, lon, address (may be empty), radiusM, enabled, and updatedAt (ms, the writer's
 * clock) which the task's field list does not name but the conflict rule needs.
 */
public final class HomeData {

    /** The radii the screens offer, in metres. */
    public static final int[] RADII_M = {100, 250, 500, 1000};

    /** Default radius: 250 m is a street or two, wide enough that a garden or a driveway is inside. */
    public static final int DEFAULT_RADIUS_M = 250;

    public static final String KEY_LAT = "lat";
    public static final String KEY_LON = "lon";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_RADIUS = "radiusM";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_UPDATED_AT = "updatedAt";

    public final double lat;
    public final double lon;
    public final String address;
    public final int radiusM;
    public final boolean enabled;
    public final long updatedAt;

    public HomeData(double lat, double lon, String address, int radiusM, boolean enabled, long updatedAt) {
        this.lat = lat;
        this.lon = lon;
        this.address = address == null ? "" : address.trim();
        this.radiusM = normalizeRadius(radiusM);
        this.enabled = enabled;
        this.updatedAt = updatedAt;
    }

    /** True when lat / lon are real numbers on Earth. (0, 0) is "null island", an unset value, not a home. */
    public boolean hasLocation() {
        return !Double.isNaN(lat) && !Double.isNaN(lon)
                && Math.abs(lat) <= 90.0 && Math.abs(lon) <= 180.0
                && !(lat == 0.0 && lon == 0.0);
    }

    /** One of {@link #RADII_M}; anything else (a typo, a future value) becomes the default. */
    public static int normalizeRadius(long radiusM) {
        for (int r : RADII_M) {
            if (r == radiusM) return r;
        }
        return DEFAULT_RADIUS_M;
    }

    /** The index of {@code radiusM} in {@link #RADII_M} (the spinner position). */
    public static int radiusIndex(int radiusM) {
        int normalized = normalizeRadius(radiusM);
        for (int i = 0; i < RADII_M.length; i++) {
            if (RADII_M[i] == normalized) return i;
        }
        return 1;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(KEY_LAT, lat);
        m.put(KEY_LON, lon);
        m.put(KEY_ADDRESS, address);
        m.put(KEY_RADIUS, (long) radiusM);
        m.put(KEY_ENABLED, enabled);
        m.put(KEY_UPDATED_AT, updatedAt);
        return m;
    }

    /**
     * Reads the map written by {@link #toMap()}. Never null: a missing or malformed map gives a
     * value whose {@link #hasLocation()} is false. Numbers may arrive as Long or Double.
     */
    public static HomeData fromMap(Map<?, ?> m) {
        if (m == null) return new HomeData(Double.NaN, Double.NaN, "", DEFAULT_RADIUS_M, false, 0L);
        double lat = number(m.get(KEY_LAT), Double.NaN);
        double lon = number(m.get(KEY_LON), Double.NaN);
        Object address = m.get(KEY_ADDRESS);
        long radius = (long) number(m.get(KEY_RADIUS), DEFAULT_RADIUS_M);
        boolean enabled = Boolean.TRUE.equals(m.get(KEY_ENABLED));
        long updatedAt = (long) number(m.get(KEY_UPDATED_AT), 0);
        return new HomeData(lat, lon, address instanceof String ? (String) address : "",
                normalizeRadius(radius), enabled, updatedAt);
    }

    /**
     * Should the cloud copy replace this phone's Home? Only when the cloud has a real Home AND
     * this phone has none, or the cloud copy is newer than the phone's last change. A guardian's
     * newer Home wins; a stale cloud copy never undoes something the user just saved.
     */
    public static boolean cloudReplacesLocal(HomeData cloud, boolean localHasHome, long localChangedAt) {
        if (cloud == null || !cloud.hasLocation()) return false;
        return !localHasHome || cloud.updatedAt > localChangedAt;
    }

    /**
     * Should this phone upload its Home? When it has one and the cloud has none (a Home saved
     * before segment 27, or a first sync).
     */
    public static boolean localShouldBePushed(HomeData cloud, boolean localHasHome) {
        return localHasHome && (cloud == null || !cloud.hasLocation());
    }

    private static double number(Object o, double fallback) {
        return o instanceof Number ? ((Number) o).doubleValue() : fallback;
    }
}
