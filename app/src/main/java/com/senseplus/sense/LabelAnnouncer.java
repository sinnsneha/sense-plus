package com.senseplus.sense;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Pure filtering and de-duplication for spoken image labels. */
public class LabelAnnouncer {

    public static final float MIN_CONFIDENCE = 0.70f;
    public static final int MAX_LABELS = 3;
    public static final long SPEAK_COOLDOWN_MS = 4000;
    public static final long REPEAT_SUPPRESS_MS = 10000;

    public static final class Label {
        public final String text;
        public final float confidence;

        public Label(String text, float confidence) {
            this.text = text;
            this.confidence = confidence;
        }
    }

    private boolean hasSpoken = false;
    private long lastSpokenMs = 0;
    private String lastSignature = "";

    /**
     * Returns the sentence to speak, or null when there is nothing new to say.
     */
    public String announce(List<Label> labels, long nowMs) {
        List<Label> top = topLabels(labels);
        if (top.isEmpty()) return null;

        if (hasSpoken && nowMs - lastSpokenMs < SPEAK_COOLDOWN_MS) return null;

        String signature = signatureOf(top);
        if (hasSpoken
                && signature.equals(lastSignature)
                && nowMs - lastSpokenMs < REPEAT_SUPPRESS_MS) {
            return null;
        }

        hasSpoken = true;
        lastSpokenMs = nowMs;
        lastSignature = signature;

        StringBuilder sentence = new StringBuilder("I see: ");
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) sentence.append(", ");
            sentence.append(top.get(i).text);
        }
        return sentence.toString();
    }

    /** Keeps labels at or above {@link #MIN_CONFIDENCE}, strongest first, at most three. */
    public static List<Label> topLabels(List<Label> labels) {
        List<Label> valid = new ArrayList<>();
        if (labels != null) {
            for (Label label : labels) {
                if (label == null || label.text == null) continue;
                String text = label.text.trim();
                if (text.isEmpty() || label.confidence < MIN_CONFIDENCE) continue;
                valid.add(new Label(text, label.confidence));
            }
        }

        Collections.sort(valid, (a, b) -> Float.compare(b.confidence, a.confidence));
        if (valid.size() > MAX_LABELS) {
            return new ArrayList<>(valid.subList(0, MAX_LABELS));
        }
        return valid;
    }

    private static String signatureOf(List<Label> labels) {
        TreeSet<String> unique = new TreeSet<>();
        for (Label label : labels) {
            unique.add(label.text.toLowerCase(Locale.US));
        }
        return String.join("|", unique);
    }
}
