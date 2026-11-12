package com.senseplus.sense;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Every Firestore path the app uses, in one place (segment 23). The data model and the rules
 * that protect it are in firestore.rules at the repo root; keep the two in step.
 *
 * <pre>
 * accounts/{uid}                              the account: name, role, phone (+ linkedUser for guardians)
 * users/{uid}                                 a user's live status, location, battery, guardians
 * users/{uid}/events/{id}                     the user's safety timeline
 * users/{uid}/linkRequests/{guardianUid}      a guardian asking to link (needs a valid code)
 * pairCodes/{code}                            a short-lived 6-digit code issued by a user
 * </pre>
 */
public final class FirestorePaths {

    public static final String ACCOUNTS = "accounts";
    public static final String USERS = "users";
    public static final String EVENTS = "events";
    public static final String LINK_REQUESTS = "linkRequests";
    public static final String PAIR_CODES = "pairCodes";

    private FirestorePaths() { }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    public static DocumentReference account(String uid) {
        return db().collection(ACCOUNTS).document(uid);
    }

    public static DocumentReference userDoc(String uid) {
        return db().collection(USERS).document(uid);
    }

    public static CollectionReference events(String uid) {
        return userDoc(uid).collection(EVENTS);
    }

    public static CollectionReference linkRequests(String userUid) {
        return userDoc(userUid).collection(LINK_REQUESTS);
    }

    public static DocumentReference linkRequest(String userUid, String guardianUid) {
        return linkRequests(userUid).document(guardianUid);
    }

    public static DocumentReference pairCode(String code) {
        return db().collection(PAIR_CODES).document(code);
    }
}
