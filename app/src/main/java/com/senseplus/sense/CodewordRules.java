package com.senseplus.sense;

/** Pure validation helper for the private emergency codeword. */
public class CodewordRules {
    /**
     * Validates a proposed codeword.
     * @param raw user input (may contain whitespace, mixed case, etc.)
     * @return null if valid, otherwise an error message suitable for a Toast.
     */
    public static String validate(String raw) {
        if (raw == null) return "Codeword cannot be empty";
        String cw = raw.trim().toLowerCase(java.util.Locale.US);
        if (cw.length() < 3 || cw.length() > 15) {
            return "Codeword must be 3‑15 letters";
        }
        if (!cw.matches("[a-z]+")) {
            return "Codeword must contain letters only";
        }
        // reserved words list
        String[] reserved = {"help", "okay", "fine", "alright", "yes", "no", "not"};
        for (String r : reserved) {
            if (cw.equals(r)) return "Codeword cannot be a reserved word";
        }
        return null; // valid
    }
}

