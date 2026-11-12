package com.senseplus.sense;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Pure validation helper for the communication board's custom phrases. */
public final class PhraseRules {

    public static final int MAX_LENGTH = 60;
    public static final int MAX_CUSTOM = 20;

    public static final List<String> DEFAULT_PHRASES = Collections.unmodifiableList(Arrays.asList(
            "Help", "Yes", "No", "Thank you",
            "I need water", "I need the toilet",
            "I am in pain", "Please call my guardian"));

    private PhraseRules() {
    }

    /**
     * Validates a proposed custom phrase.
     *
     * @param phrase   user input (surrounding whitespace is ignored)
     * @param existing custom phrases already saved (may also contain the defaults)
     * @return null if valid, otherwise an error message suitable for display
     */
    public static String validate(String phrase, List<String> existing) {
        if (phrase == null) return "Phrase cannot be empty";
        String trimmed = phrase.trim();
        if (trimmed.isEmpty()) return "Phrase cannot be empty";
        if (trimmed.length() > MAX_LENGTH) {
            return "Phrase must be " + MAX_LENGTH + " characters or fewer";
        }

        for (String defaultPhrase : DEFAULT_PHRASES) {
            if (defaultPhrase.equalsIgnoreCase(trimmed)) {
                return "Phrase already exists";
            }
        }

        int customCount = 0;
        if (existing != null) {
            for (String e : existing) {
                if (e == null) continue;
                String other = e.trim();
                if (other.equalsIgnoreCase(trimmed)) {
                    return "Phrase already exists";
                }
                if (!isDefault(other)) customCount++;
            }
        }

        if (customCount >= MAX_CUSTOM) {
            return "You can add at most " + MAX_CUSTOM + " custom phrases";
        }
        return null;
    }

    private static boolean isDefault(String phrase) {
        for (String defaultPhrase : DEFAULT_PHRASES) {
            if (defaultPhrase.equalsIgnoreCase(phrase)) return true;
        }
        return false;
    }
}
