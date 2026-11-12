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
    public static final String GUARDIAN_ACK = "GUARDIAN_ACK"; // a guardian answered "I'm on my way"
    public static final String SMS_SENT = "SMS_SENT";         // the emergency SMS fallback was sent (count only)
    public static final String SAFE_ZONE_EXIT = "SAFE_ZONE_EXIT";     // went beyond the safe zone around Home
    public static final String SAFE_ZONE_RETURN = "SAFE_ZONE_RETURN"; // came back inside it

    private EventTypes() {
    }
}
