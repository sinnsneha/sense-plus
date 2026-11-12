package com.senseplus.sense;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The phone numbers "Call my guardian" and the SMS fallback use (segment 26).
 *
 * The guardians' numbers live in users/{uid}.guardianInfo, but the SMS exists for the moment
 * there is NO internet, so it cannot read Firestore when it is needed. They are therefore copied
 * to this phone whenever they are known (the My guardians screen, the start of monitoring, the
 * Safety screen) and read from the copy.
 */
final class EmergencyContacts {

    private static final String TAG = "SenseContacts";
    private static final String KEY_GUARDIAN_PHONES = "guardian_phones";

    private EmergencyContacts() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(AccountStore.PREFS, Context.MODE_PRIVATE);
    }

    /** Per account, like the Medical ID, so a different sign-in never reuses someone else's list. */
    private static String key(Context c) {
        String uid = AccountStore.uid(c);
        return uid == null || uid.isEmpty() ? null : KEY_GUARDIAN_PHONES + ":" + uid;
    }

    /** The guardians' phones in the order they were added, from the local copy. */
    static List<String> guardianPhones(Context c) {
        List<String> out = new ArrayList<>();
        String key = key(c);
        if (key == null) return out;
        String json = prefs(c).getString(key, null);
        if (json == null) return out;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) out.add(array.optString(i, ""));
        } catch (Exception e) {
            Log.w(TAG, "Saved guardian phones could not be read");
        }
        return out;
    }

    /** Replaces the local copy with the guardians listed in this users/{uid} document. */
    static void cacheFrom(Context c, DocumentSnapshot doc) {
        String key = key(c);
        if (key == null) return;
        List<String> phones = new ArrayList<>();
        if (doc != null && doc.exists()) {
            Object guardians = doc.get("guardians");
            Object info = doc.get("guardianInfo");
            if (guardians instanceof List && info instanceof Map) {
                phones = PhoneRules.guardianPhonesInOrder((List<?>) guardians, (Map<?, ?>) info);
            }
        }
        prefs(c).edit().putString(key, new JSONArray(phones).toString()).apply();
    }

    /**
     * Reads the signed-in user's own document once and refreshes the local copy. The default
     * source, so with no network the call simply fails (or answers from cache) and the previous
     * copy stays. One read per call.
     */
    static void refresh(Context c) {
        final Context app = c.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        FirestorePaths.userDoc(user.getUid()).get()
                .addOnSuccessListener(doc -> cacheFrom(app, doc))
                .addOnFailureListener(e ->
                        Log.w(TAG, "Guardian phones not refreshed: " + e.getClass().getSimpleName()));
    }

    /** The number "Call my guardian" dials, or null when no guardian and no emergency contact has one. */
    static String callNumber(Context c) {
        return PhoneRules.callNumber(guardianPhones(c), MedicalStore.load(c).contactPhone);
    }

    /** Who gets the emergency SMS: every guardian phone and the emergency contact, each once. */
    static List<String> smsRecipients(Context c) {
        return PhoneRules.smsRecipients(guardianPhones(c), MedicalStore.load(c).contactPhone);
    }
}
