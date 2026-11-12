package com.senseplus.sense;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * One place for the signed-in account: the role and name cached in SharedPreferences (so the
 * app can start offline) and the accounts/{uid} document in Firestore. The cache lives in the
 * same file as the assistance profile, so signing out does not touch the profile.
 *
 * Segment 23 adds the phone number and, for guardians, the person they are linked to.
 */
public final class AccountStore {

    static final String PREFS = "senseplus_prefs";
    static final String KEY_UID = "account_uid";
    static final String KEY_ROLE = "account_role";
    static final String KEY_NAME = "account_name";
    static final String KEY_EMAIL = "account_email";
    static final String KEY_PHONE = "account_phone";
    static final String KEY_LINKED_UID = "linked_user_uid";     // guardians only
    static final String KEY_LINKED_NAME = "linked_user_name";   // guardians only

    // Field names inside accounts/{uid}.
    static final String FIELD_LINKED_USER = "linkedUser";
    static final String FIELD_LINKED_USER_NAME = "linkedUserName";

    public interface AccountCallback {
        void onLoaded(String name, String role, String email);
        void onMissing();
        void onError(Exception e);
    }

    private AccountStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String uid(Context c) { return prefs(c).getString(KEY_UID, null); }
    public static String role(Context c) { return prefs(c).getString(KEY_ROLE, null); }
    public static String name(Context c) { return prefs(c).getString(KEY_NAME, ""); }
    public static String email(Context c) { return prefs(c).getString(KEY_EMAIL, ""); }
    public static String phone(Context c) { return prefs(c).getString(KEY_PHONE, ""); }

    /** The user this guardian is linked to, or null. */
    public static String linkedUid(Context c) {
        String v = prefs(c).getString(KEY_LINKED_UID, null);
        return v == null || v.isEmpty() ? null : v;
    }

    public static String linkedName(Context c) { return prefs(c).getString(KEY_LINKED_NAME, ""); }

    public static boolean isLinked(Context c) { return linkedUid(c) != null; }

    /**
     * True only when the cache belongs to the currently signed-in, non-anonymous Firebase user
     * and contains a role we understand. Anonymous users from older installs fail this test.
     */
    public static boolean hasCachedRole(Context c) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return false;
        return user.getUid().equals(uid(c)) && Roles.isValid(role(c));
    }

    public static void cache(Context c, String uid, String name, String role, String email) {
        prefs(c).edit()
                .putString(KEY_UID, uid)
                .putString(KEY_ROLE, role)
                .putString(KEY_NAME, name == null ? "" : name)
                .putString(KEY_EMAIL, email == null ? "" : email)
                .apply();
    }

    /** Clears the cached account and link but keeps the assistance profile in the same file. */
    public static void clear(Context c) {
        prefs(c).edit()
                .remove(KEY_UID)
                .remove(KEY_ROLE)
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_PHONE)
                .remove(KEY_LINKED_UID)
                .remove(KEY_LINKED_NAME)
                .apply();
    }

    /** Writes accounts/{uid}. Used at sign-up; also repairs a missing document from the cache. */
    public static Task<Void> writeAccount(String uid, String name, String role,
                                          String email, String phone) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name == null ? "" : name);
        data.put("role", role);
        data.put("email", email == null ? "" : email);
        data.put("phone", phone == null ? "" : phone);
        data.put("createdAt", FieldValue.serverTimestamp());
        // Merge, so repairing the document never wipes a guardian's linkedUser.
        return FirestorePaths.account(uid).set(data, SetOptions.merge());
    }

    /** Reads accounts/{uid}. Default source, so a locally cached copy still works offline. */
    public static void loadAccount(String uid, AccountCallback callback) {
        if (uid == null || uid.isEmpty()) {
            callback.onMissing();
            return;
        }
        FirestorePaths.account(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        callback.onLoaded(doc.getString("name"), doc.getString("role"),
                                doc.getString("email"));
                    } else {
                        callback.onMissing();
                    }
                })
                .addOnFailureListener(callback::onError);
    }

    /** Background refresh of the cached account. Failures are ignored: the cache stays. */
    public static void refresh(Context c) {
        refresh(c, null);
    }

    /**
     * Background refresh of the cached role, name, phone and (for guardians) the linked person.
     * {@code onDone} runs on the main thread after a successful read, so Home can re-render and
     * start guardian alerts on a phone that only just learned its link.
     */
    public static void refresh(Context c, Runnable onDone) {
        final Context app = c.getApplicationContext();
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        FirestorePaths.account(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) return;
                    String role = doc.getString("role");
                    if (!Roles.isValid(role)) return;
                    cache(app, user.getUid(), doc.getString("name"), role, doc.getString("email"));
                    SharedPreferences.Editor e = prefs(app).edit();
                    String phone = doc.getString("phone");
                    e.putString(KEY_PHONE, phone == null ? "" : phone);
                    if (Roles.GUARDIAN.equals(role)) {
                        String linked = doc.getString(FIELD_LINKED_USER);
                        String linkedName = doc.getString(FIELD_LINKED_USER_NAME);
                        if (linked == null || linked.isEmpty()) {
                            e.remove(KEY_LINKED_UID).remove(KEY_LINKED_NAME);
                        } else {
                            e.putString(KEY_LINKED_UID, linked)
                                    .putString(KEY_LINKED_NAME, linkedName == null ? "" : linkedName);
                        }
                    }
                    e.apply();
                    if (onDone != null) onDone.run();
                });
    }

    /** A guardian is now linked: remember it here and in accounts/{me} (for other phones). */
    public static void saveLink(Context c, String linkedUid, String linkedName) {
        prefs(c).edit()
                .putString(KEY_LINKED_UID, linkedUid)
                .putString(KEY_LINKED_NAME, linkedName == null ? "" : linkedName)
                .apply();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_LINKED_USER, linkedUid);
        data.put(FIELD_LINKED_USER_NAME, linkedName == null ? "" : linkedName);
        FirestorePaths.account(user.getUid()).set(data, SetOptions.merge());
    }

    /** Forgets the link here and in accounts/{me}. The user removes the guardian on their side. */
    public static void clearLink(Context c) {
        prefs(c).edit().remove(KEY_LINKED_UID).remove(KEY_LINKED_NAME).apply();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put(FIELD_LINKED_USER, FieldValue.delete());
        data.put(FIELD_LINKED_USER_NAME, FieldValue.delete());
        FirestorePaths.account(user.getUid()).set(data, SetOptions.merge());
    }
}
