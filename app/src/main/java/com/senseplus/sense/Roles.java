package com.senseplus.sense;

/**
 * The two account roles. Pure Java so the mapping and validation code can be unit-tested
 * without Android. The strings are also the values stored in Firestore and SharedPreferences.
 */
public final class Roles {

    public static final String USER = "user";
    public static final String GUARDIAN = "guardian";

    private Roles() { }

    public static boolean isValid(String role) {
        return USER.equals(role) || GUARDIAN.equals(role);
    }

    public static String label(String role) {
        return GUARDIAN.equals(role) ? "Guardian" : "User";
    }
}
