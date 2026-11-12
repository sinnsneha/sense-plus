package com.senseplus.sense;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * One place for the signed-in account: the role and name cached in SharedPreferences (so the
 * app can start offline) and the accounts/{uid} document in Firestore. The cache lives in the
 * same file as the assistance profile, so signing out does not touch the profile.
 */
public final class AccountStore {

    static final String PREFS = "senseplus_prefs";
    static final String KEY_UID = "account_uid";
    static final String KEY_ROLE = "account_role";
    static final String KEY_NAME = "account_name";
    static final String KEY_EMAIL = "account_email";

    private static final String COLLECTION = "accounts";

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

    /** Clears the cached account but keeps the assistance profile in the same file. */
    public static void clear(Context c) {
        prefs(c).edit()
                .remove(KEY_UID)
                .remove(KEY_ROLE)
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
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
        return FirebaseFirestore.getInstance().collection(COLLECTION).document(uid).set(data);
    }

    /** Reads accounts/{uid}. Default source, so a locally cached copy still works offline. */
    public static void loadAccount(String uid, AccountCallback callback) {
        if (uid == null || uid.isEmpty()) {
            callback.onMissing();
            return;
        }
        FirebaseFirestore.getInstance().collection(COLLECTION).document(uid).get()
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

    /** Background refresh of the cached role and name. Failures are ignored: the cache stays. */
    public static void refresh(Context c) {
        final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;
        loadAccount(user.getUid(), new AccountCallback() {
            @Override public void onLoaded(String name, String role, String email) {
                if (Roles.isValid(role)) cache(c, user.getUid(), name, role, email);
            }
            @Override public void onMissing() { }
            @Override public void onError(Exception e) { }
        });
    }
}
