package com.senseplus.sense;

/** Firestore event "type" values. */
public final class EventTypes {
    public static final String ATTENTION = "ATTENTION";
    public static final String EMERGENCY = "EMERGENCY";
    public static final String MANUAL_HELP = "MANUAL_HELP";
    public static final String VOICE_HELP = "VOICE_HELP";
    public static final String ALL_CLEAR = "ALL_CLEAR";
    public static final String CODEWORD = "CODEWORD";
    public static final String LOW_BATTERY = "LOW_BATTERY";

    private EventTypes() {
    }
}
