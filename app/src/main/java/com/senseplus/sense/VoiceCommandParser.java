package com.senseplus.sense;

import java.util.Locale;

/** Pure utility to parse voice command strings from Vosk. */
public class VoiceCommandParser {
    public enum Command { HELP, OKAY, CODEWORD, NONE }

    /**
     * Parses the given lower‑cased text into a {@link Command}.
     * Word boundaries are respected; substrings like "helpful" are NOT treated as HELP.
     * Negations followed by an okay‑type word within two tokens are interpreted as HELP
     * (e.g. "not okay").
     * If a codeword is supplied, it is matched as a whole word and takes precedence over HELP/OKAY.
     */
    public static Command parse(String text) {
        return parse(text, null);
    }

    public static Command parse(String text, String codeword) {
        if (text == null) return Command.NONE;
        // split on whitespace and punctuation
        String[] words = text.toLowerCase(Locale.US).split("[\\s\\p{Punct}]+");
        // codeword check first (whole word match)
        if (codeword != null && !codeword.isEmpty()) {
            for (String w : words) {
                if (codeword.equals(w)) return Command.CODEWORD;
            }
        }
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            if ("help".equals(w)) {
                return Command.HELP;
            }
            // negation + okay word within two tokens
            if (i < words.length - 1) {
                String next = words[i + 1];
                if (("not".equals(w) || "no".equals(w)) && isOkayWord(next)) {
                    return Command.HELP;
                }
                if (i < words.length - 2) {
                    String next2 = words[i + 2];
                    if (("not".equals(w) || "no".equals(w)) && isOkayWord(next2)) {
                        return Command.HELP;
                    }
                }
            }
        }
        // okay words without preceding negation
        for (String w : words) {
            if (isOkayWord(w)) return Command.OKAY;
        }
        return Command.NONE;
    }

    private static boolean isOkayWord(String w) {
        return "okay".equals(w) || "fine".equals(w) || "yes".equals(w) || "alright".equals(w);
    }
}
