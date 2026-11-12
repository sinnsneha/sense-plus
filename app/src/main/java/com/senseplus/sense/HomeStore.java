package com.senseplus.sense;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Home and the safe zone around it, on the phone and in users/{uid}.home (segment 27).
 *
 * The user's phone keeps Home in SharedPreferences, exactly where Navigate Home has always read
 * it (home_lat / home_lon / home_address), plus the safe zone settings. Every change here is
 * also written to Firestore, and a Home set by the guardian is copied back to the phone by
 * UserMonitorService (applyCloud). HomeData.cloudReplacesLocal decides who wins.
 */
final class HomeStore {

    private static final String TAG = "SenseHome";

    static final String KEY_ENABLED = "safe_zone_enabled";    // boolean, default off
    static final String KEY_RADIUS = "safe_zone_radius_m";    // int, one of HomeData.RADII_M
    static final String KEY_SPEAK = "safe_zone_speak";        // boolean, default from the profile
    /** When the phone's copy last changed (ms): the user's save, or a newer cloud copy applied. */
    static final String KEY_CHANGED_AT = "home_changed_at";

    private HomeStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(NavigationActivity.PREFS, Context.MODE_PRIVATE);
    }

    // ---------- the phone's copy ----------

    /** Home as saved on this phone. {@link HomeData#hasLocation()} is false when none is saved. */
    static HomeData load(Context c) {
        SharedPreferences p = prefs(c);
        double lat = Double.NaN;
        double lon = Double.NaN;
        if (p.contains(NavigationActivity.KEY_HOME_LAT) && p.contains(NavigationActivity.KEY_HOME_LON)) {
            try {
                // Stored as text so the full precision survives (see NavigationActivity).
                lat = Double.parseDouble(p.getString(NavigationActivity.KEY_HOME_LAT, "NaN"));
                lon = Double.parseDouble(p.getString(NavigationActivity.KEY_HOME_LON, "NaN"));
            } catch (NumberFormatException e) {
                lat = Double.NaN;
                lon = Double.NaN;
            }
        }
        long changedAt = p.getLong(KEY_CHANGED_AT, p.getLong(NavigationActivity.KEY_HOME_SAVED_AT, 0L));
        return new HomeData(lat, lon, p.getString(NavigationActivity.KEY_HOME_ADDRESS, ""),
                p.getInt(KEY_RADIUS, HomeData.DEFAULT_RADIUS_M), p.getBoolean(KEY_ENABLED, false), changedAt);
    }

    /**
     * Spoken help when the user leaves home. Off for everyone until switched on, except that it
     * is ON by default when the profile includes the Cognitive mode (the people wandering puts at
     * risk). Once the user chooses, their choice stands.
     */
    static boolean speakHelp(Context c) {
        SharedPreferences p = prefs(c);
        if (p.contains(KEY_SPEAK)) return p.getBoolean(KEY_SPEAK, false);
        Set<String> modes = ProfileActivity.loadModes(p);
        return modes.contains(ModeMapper.MODE_COGNITIVE);
    }

    static void setSpeakHelp(Context c, boolean speak) {
        prefs(c).edit().putBoolean(KEY_SPEAK, speak).apply();
    }

    // ---------- the user changes something on this phone ----------

    /** Navigate Home just saved a Home (GPS or address) into the prefs: stamp it and send it. */
    static void onHomeSaved(Context c) {
        prefs(c).edit().putLong(KEY_CHANGED_AT, System.currentTimeMillis()).apply();
        push(c);
    }

    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean(KEY_ENABLED, enabled)
                .putLong(KEY_CHANGED_AT, System.currentTimeMillis()).apply();
        push(c);
    }

    static void setRadius(Context c, int radiusM) {
        prefs(c).edit().putInt(KEY_RADIUS, HomeData.normalizeRadius(radiusM))
                .putLong(KEY_CHANGED_AT, System.currentTimeMillis()).apply();
        push(c);
    }

    /**
     * Writes the phone's whole Home block to users/{uid}.home. Merge, so the status, guardians
     * and the rest of the document are untouched. Queued by the SDK if there is no network.
     */
    static void push(Context c) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        HomeData home = load(c);
        if (!home.hasLocation()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("home", home.toMap());
        FirestorePaths.userDoc(user.getUid()).set(data, SetOptions.merge())
                .addOnFailureListener(e ->
                        Log.w(TAG, "Home not written: " + e.getClass().getSimpleName()));
    }

    // ---------- the cloud changed it (a guardian) ----------

    /**
     * Copies a guardian-set Home into the prefs Navigate Home reads, when HomeData says the cloud
     * copy is the newer one.
     *
     * @return true if the phone's copy was replaced
     */
    static boolean applyCloud(Context c, HomeData cloud) {
        HomeData local = load(c);
        if (!HomeData.cloudReplacesLocal(cloud, local.hasLocation(), local.updatedAt)) return false;

        SharedPreferences.Editor e = prefs(c).edit()
                .putString(NavigationActivity.KEY_HOME_LAT, Double.toString(cloud.lat))
                .putString(NavigationActivity.KEY_HOME_LON, Double.toString(cloud.lon))
                .putInt(KEY_RADIUS, cloud.radiusM)
                .putBoolean(KEY_ENABLED, cloud.enabled)
                .putLong(KEY_CHANGED_AT, cloud.updatedAt);
        if (cloud.address.isEmpty()) e.remove(NavigationActivity.KEY_HOME_ADDRESS);
        else e.putString(NavigationActivity.KEY_HOME_ADDRESS, cloud.address);
        e.apply();
        return true;
    }

    // ---------- the guardian writes (firestore.rules: a linked guardian may change only 'home') ----------

    /** Sets the whole Home (a typed address). update() with one field path: only 'home' changes. */
    static Task<Void> writeAsGuardian(String userUid, HomeData home) {
        return FirestorePaths.userDoc(userUid).update("home", home.toMap());
    }

    /** Changes only the switch and the radius of an existing Home, leaving its location alone. */
    static Task<Void> writeSettingsAsGuardian(String userUid, boolean enabled, int radiusM) {
        return FirestorePaths.userDoc(userUid).update(
                "home.enabled", enabled,
                "home.radiusM", (long) HomeData.normalizeRadius(radiusM),
                "home.updatedAt", System.currentTimeMillis());
    }
}
