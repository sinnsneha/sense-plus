package com.senseplus.sense;

import java.util.regex.Pattern;

/**
 * Pure validation for the sign-in and create-account forms. No Android imports, so it is
 * unit-tested. Each method returns null when the value is acceptable, or a short message
 * suitable for a TextInputLayout error.
 */
public final class AccountRules {

    public static final int MIN_PASSWORD = 6;  // Firebase Auth's minimum password length
    public static final int MAX_NAME = 40;     // characters: keeps the header greeting readable

    // Local part @ domain . tld, no spaces. Deliberately simple; Firebase does the real check.
    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    // Optional phone: digits, + and spaces only, up to 20 characters.
    private static final Pattern PHONE =
            Pattern.compile("^[0-9+ ]{0,20}$");

    private AccountRules() { }

    public static String emailError(String email) {
        String value = email == null ? "" : email.trim();
        return EMAIL.matcher(value).matches() ? null : "Enter a valid email address.";
    }

    public static String passwordError(String password) {
        return password != null && password.length() >= MIN_PASSWORD
                ? null : "Password must be at least 6 characters.";
    }

    public static String confirmError(String password, String confirm) {
        return password != null && password.equals(confirm)
                ? null : "Passwords do not match.";
    }

    public static String nameError(String name) {
        String value = name == null ? "" : name.trim();
        return !value.isEmpty() && value.length() <= MAX_NAME
                ? null : "Enter your name (1 to 40 characters).";
    }

    public static String phoneError(String phone) {
        String value = phone == null ? "" : phone.trim();
        return PHONE.matcher(value).matches() ? null : "Use only digits, + and spaces.";
    }
}
