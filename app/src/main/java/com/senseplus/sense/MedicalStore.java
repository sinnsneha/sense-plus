package com.senseplus.sense;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Where the Medical ID and the SMS switch live (segment 26).
 *
 * The Medical ID is saved on the phone (SharedPreferences, JSON) so the lock-screen helper view
 * works with no network and no sign-in round trip, and is copied to users/{uid}.medical so the
 * guardians can read it (firestore.rules: only the owner writes it).
 *
 * Both values are stored per account id, so signing out and signing in as someone else on the
 * same phone never shows the first person's medical details or sends their SMS.
 */
final class MedicalStore {

    private static final String TAG = "SenseMedical";
    private static final String KEY_MEDICAL = "medical_id_json";
    private static final String KEY_SMS = "sms_fallback_enabled";

    private MedicalStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(AccountStore.PREFS, Context.MODE_PRIVATE);
    }

    /** The key for this account, or null when nobody is signed in (nothing is read or written then). */
    private static String key(Context c, String base) {
        String uid = AccountStore.uid(c);
        return uid == null || uid.isEmpty() ? null : base + ":" + uid;
    }

    /** The saved Medical ID, or an empty one. Local only: safe to call on the lock screen. */
    static MedicalId load(Context c) {
        String key = key(c, KEY_MEDICAL);
        if (key == null) return MedicalId.empty();
        String json = prefs(c).getString(key, null);
        if (json == null) return MedicalId.empty();
        try {
            JSONObject o = new JSONObject(json);
            Map<String, String> map = new HashMap<>();
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, o.optString(k, ""));
            }
            return MedicalId.fromMap(map);
        } catch (Exception e) {
            Log.w(TAG, "Saved Medical ID could not be read: " + e.getClass().getSimpleName());
            return MedicalId.empty();
        }
    }

    /** Saves on the phone, then copies to Firestore (queued by the SDK if there is no network). */
    static void save(Context c, MedicalId id) {
        String key = key(c, KEY_MEDICAL);
        if (key == null) return;
        prefs(c).edit().putString(key, new JSONObject(id.toMap()).toString()).apply();
        syncToCloud(id);
    }

    /**
     * users/{uid}.medical = the whole map. Merge, so the status, guardians and the rest of the
     * document are untouched; every key is always present, so a cleared field overwrites the old text.
     */
    static void syncToCloud(MedicalId id) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        Map<String, Object> data = new HashMap<>();
        data.put("medical", id.toMap());
        FirestorePaths.userDoc(user.getUid()).set(data, SetOptions.merge())
                .addOnFailureListener(e ->
                        Log.w(TAG, "Medical ID sync failed: " + e.getClass().getSimpleName()));
    }

    /** The opt-in SMS fallback. Off until the user turns it on. */
    static boolean smsEnabled(Context c) {
        String key = key(c, KEY_SMS);
        return key != null && prefs(c).getBoolean(key, false);
    }

    static void setSmsEnabled(Context c, boolean enabled) {
        String key = key(c, KEY_SMS);
        if (key != null) prefs(c).edit().putBoolean(key, enabled).apply();
    }
}
