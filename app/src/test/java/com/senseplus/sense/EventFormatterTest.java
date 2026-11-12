package com.senseplus.sense;

import org.junit.Test;

import java.time.Instant;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventFormatterTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long NOW = Instant.parse("2026-11-12T14:30:00Z").toEpochMilli();

    @Test
    public void emojiByType() {
        assertEquals("🟠", EventFormatter.emoji(EventTypes.ATTENTION));
        assertEquals("🔴", EventFormatter.emoji(EventTypes.EMERGENCY));
        assertEquals("🔴", EventFormatter.emoji(EventTypes.MANUAL_HELP));
        assertEquals("🔴", EventFormatter.emoji(EventTypes.VOICE_HELP));
        assertEquals("🔴", EventFormatter.emoji(EventTypes.CODEWORD));
        assertEquals("🟢", EventFormatter.emoji(EventTypes.ALL_CLEAR));
        assertEquals("🟠", EventFormatter.emoji(EventTypes.LOW_BATTERY));
        assertEquals("🔵", EventFormatter.emoji(EventTypes.GUARDIAN_ACK));
        assertEquals("🟠", EventFormatter.emoji(EventTypes.SAFE_ZONE_EXIT));
        assertEquals("🟢", EventFormatter.emoji(EventTypes.SAFE_ZONE_RETURN));
        assertEquals("⚪", EventFormatter.emoji("SOMETHING_ELSE"));
    }

    @Test
    public void labelByType() {
        assertEquals("Attention", EventFormatter.label(EventTypes.ATTENTION));
        assertEquals("Emergency", EventFormatter.label(EventTypes.EMERGENCY));
        assertEquals("Manual help", EventFormatter.label(EventTypes.MANUAL_HELP));
        assertEquals("Voice help", EventFormatter.label(EventTypes.VOICE_HELP));
        assertEquals("Codeword", EventFormatter.label(EventTypes.CODEWORD));
        assertEquals("All clear", EventFormatter.label(EventTypes.ALL_CLEAR));
        assertEquals("Low battery", EventFormatter.label(EventTypes.LOW_BATTERY));
        assertEquals("Guardian responding", EventFormatter.label(EventTypes.GUARDIAN_ACK));
        assertEquals("SMS sent", EventFormatter.label(EventTypes.SMS_SENT));
        assertEquals("Left safe zone", EventFormatter.label(EventTypes.SAFE_ZONE_EXIT));
        assertEquals("Back home", EventFormatter.label(EventTypes.SAFE_ZONE_RETURN));
        assertEquals("Event", EventFormatter.label(null));
    }

    @Test
    public void safeZoneRowsShowTheOrangeExitAndTheGreenReturn() {
        long t = Instant.parse("2026-11-12T14:32:07Z").toEpochMilli();
        assertEquals("🟠  Left safe zone · 14:32:07\nLeft the safe zone (450 m from home)",
                EventFormatter.row(EventTypes.SAFE_ZONE_EXIT, "Left the safe zone (450 m from home)",
                        t, null, NOW, UTC));
        assertEquals("🟢  Back home · 14:40:00",
                EventFormatter.row(EventTypes.SAFE_ZONE_RETURN, null,
                        Instant.parse("2026-11-12T14:40:00Z").toEpochMilli(), null, NOW, UTC));
    }

    @Test
    public void guardianAckRowShowsBlueLabelTimeAndDetail() {
        long t = Instant.parse("2026-11-12T14:32:07Z").toEpochMilli();
        String row = EventFormatter.row(EventTypes.GUARDIAN_ACK, "Ravi is on the way", t, null, NOW, UTC);
        assertEquals("🔵  Guardian responding · 14:32:07\nRavi is on the way", row);
    }

    @Test
    public void lowBatteryRowShowsOrangeLabelTimeAndDetail() {
        long t = Instant.parse("2026-11-12T14:32:07Z").toEpochMilli();
        String row = EventFormatter.row(EventTypes.LOW_BATTERY, "Battery low (12%)", t, null, NOW, UTC);
        assertEquals("🟠  Low battery · 14:32:07\nBattery low (12%)", row);
    }

    @Test
    public void batteryLineShowsThePercentageAndItsAge() {
        assertEquals("Battery: 78% · 30 s ago",
                EventFormatter.batteryLine(78L, NOW - 30_000L, NOW));
        assertEquals("Battery: 78% · 4 min ago",
                EventFormatter.batteryLine(78L, NOW - 4 * 60_000L, NOW));
    }

    @Test
    public void batteryLineWithoutATimeShowsThePercentageOnly() {
        assertEquals("Battery: 78%", EventFormatter.batteryLine(78L, null, NOW));
    }

    @Test
    public void batteryLineWithoutAReadingShowsADash() {
        assertEquals("Battery: —", EventFormatter.batteryLine(null, NOW, NOW));
    }

    @Test
    public void ageIsReadableAtEveryScale() {
        assertEquals("just now", EventFormatter.age(0));
        assertEquals("just now", EventFormatter.age(4_999));
        assertEquals("5 s ago", EventFormatter.age(5_000));
        assertEquals("59 s ago", EventFormatter.age(59_999));
        assertEquals("1 min ago", EventFormatter.age(60_000));
        assertEquals("59 min ago", EventFormatter.age(59 * 60_000L + 59_000L));
        assertEquals("1 h ago", EventFormatter.age(3_600_000L));
        assertEquals("23 h ago", EventFormatter.age(23 * 3_600_000L + 59 * 60_000L));
        assertEquals("1 d ago", EventFormatter.age(24 * 3_600_000L));
    }

    @Test
    public void aNegativeAgeFromClockSkewCountsAsJustNow() {
        assertEquals("just now", EventFormatter.age(-20_000L));
        assertEquals("Battery: 50% · just now",
                EventFormatter.batteryLine(50L, NOW + 20_000L, NOW));
    }

    @Test
    public void todayShowsTimeOnly() {
        long t = Instant.parse("2026-11-12T14:32:07Z").toEpochMilli();
        assertEquals("14:32:07", EventFormatter.timeString(t, null, NOW, UTC));
    }

    @Test
    public void olderEventIncludesDate() {
        long t = Instant.parse("2026-11-11T14:32:07Z").toEpochMilli();
        assertEquals("11 Nov 14:32:07", EventFormatter.timeString(t, null, NOW, UTC));
    }

    @Test
    public void nullServerTimeFallsBackToClientTime() {
        long client = Instant.parse("2026-11-12T09:05:00Z").toEpochMilli();
        assertEquals("09:05:00", EventFormatter.timeString(null, client, NOW, UTC));
    }

    @Test
    public void missingBothTimesShowsDash() {
        assertEquals("—", EventFormatter.timeString(null, null, NOW, UTC));
    }

    @Test
    public void rowContainsEmojiLabelTimeAndDetail() {
        long t = Instant.parse("2026-11-12T14:32:07Z").toEpochMilli();
        String row = EventFormatter.row(EventTypes.ATTENTION,
                "Possible fall (accel ✓ · gyro ✓ 4.2 rad/s · posture ✓ 78°)",
                t, null, NOW, UTC);
        assertTrue(row.startsWith("🟠  Attention · 14:32:07"));
        assertTrue(row.endsWith("\nPossible fall (accel ✓ · gyro ✓ 4.2 rad/s · posture ✓ 78°)"));
    }
}
