package com.senseplus.sense;

import org.junit.Test;

import java.time.Instant;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GuardianAlertTextTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final long T = Instant.parse("2026-11-12T14:32:07Z").toEpochMilli();

    @Test
    public void attentionNamesThePersonAndTheWindow() {
        assertEquals("Possible fall — waiting for Maya to respond (15 s)",
                GuardianAlertText.attentionBody("Maya", 15));
    }

    @Test
    public void missingNameFallsBackToTheUser() {
        assertEquals("Possible fall — waiting for the user to respond (15 s)",
                GuardianAlertText.attentionBody(null, 15));
        assertEquals("Possible fall — waiting for the user to respond (15 s)",
                GuardianAlertText.attentionBody("   ", 15));
        assertEquals("The user's phone battery is low (12%). Protection stops if it dies.",
                GuardianAlertText.lowBatteryBody("", 12));
    }

    @Test
    public void emergencyTitleHasNameOnlyWhenKnown() {
        assertEquals("🔴 POSSIBLE EMERGENCY — Maya", GuardianAlertText.emergencyTitle(" Maya "));
        assertEquals("🔴 POSSIBLE EMERGENCY", GuardianAlertText.emergencyTitle(null));
    }

    @Test
    public void emergencyBodyHasReasonAddressBatteryAndTime() {
        assertEquals("Reason: No response after fall\nNear: 12 Park Road\nBattery: 12%\nTime: 14:32",
                GuardianAlertText.emergencyBody("No response after fall", "12 Park Road", 12, T, UTC));
    }

    @Test
    public void emergencyBodyHandlesMissingPieces() {
        assertEquals("Near: location unknown\nTime: 14:32",
                GuardianAlertText.emergencyBody(null, " ", -1, T, UTC));
    }

    @Test
    public void emergencyBodyUsesTheGivenTimeZone() {
        TimeZone ist = TimeZone.getTimeZone("Asia/Kolkata");
        assertTrue(GuardianAlertText.emergencyBody("x", "y", 50, T, ist).endsWith("Time: 20:02"));
    }

    @Test
    public void emergencySummaryJoinsReasonAndPlace() {
        assertEquals("Manual HELP pressed · 12 Park Road",
                GuardianAlertText.emergencySummary("Manual HELP pressed", "12 Park Road"));
        assertEquals("12 Park Road", GuardianAlertText.emergencySummary(null, "12 Park Road"));
        assertEquals("Open the dashboard for details", GuardianAlertText.emergencySummary("", null));
    }

    @Test
    public void callLabelNamesThePersonWhenKnown() {
        assertEquals("Call Maya", GuardianAlertText.callLabel("Maya"));
        assertEquals("Call", GuardianAlertText.callLabel(null));
    }

    @Test
    public void lowBatteryMessageMatchesTheSpec() {
        assertEquals("Maya's phone battery is low (12%). Protection stops if it dies.",
                GuardianAlertText.lowBatteryBody("Maya", 12));
    }

    @Test
    public void lostContactShowsWhenTheLastSignalWas() {
        assertEquals("Lost contact with Maya's phone since 14:32",
                GuardianAlertText.lostContactBody("Maya", T, UTC));
        assertEquals("Lost contact with the user's phone since 14:32",
                GuardianAlertText.lostContactBody(null, T, UTC));
    }

    @Test
    public void backInContactNamesThePerson() {
        assertEquals("Back in contact with Maya", GuardianAlertText.backInContactBody("Maya"));
    }

    @Test
    public void resolvedMessagesNameThePerson() {
        assertEquals("Maya confirmed they are okay — they are safe now.",
                GuardianAlertText.allClearBody("Maya"));
        assertEquals("Maya responded in time — no emergency.",
                GuardianAlertText.falseAlarmBody("Maya"));
    }

    @Test
    public void ackNotificationsNameThePersonYouTold() {
        assertEquals("I'm on my way", GuardianAlertText.ackActionLabel());
        assertEquals("You told Maya you're on your way", GuardianAlertText.ackSentBody("Maya"));
        assertEquals("You told the user you're on your way", GuardianAlertText.ackSentBody(null));
        assertTrue(GuardianAlertText.ackFailedBody("Maya").startsWith("Couldn't tell Maya you're on your way."));
    }

    @Test
    public void safeZoneExitNamesThePersonAndTheDistance() {
        assertEquals("Maya has left the safe zone (450 m from home)",
                GuardianAlertText.safeZoneExitBody("Maya", 450.0));
        assertEquals("Maya has left the safe zone (1.2 km from home)",
                GuardianAlertText.safeZoneExitBody("Maya", 1234.0));
        assertEquals("🟠 Left the safe zone", GuardianAlertText.safeZoneExitTitle());
    }

    @Test
    public void safeZoneExitWithoutADistanceLeavesTheBracketOut() {
        assertEquals("Maya has left the safe zone", GuardianAlertText.safeZoneExitBody("Maya", null));
        assertEquals("The user has left the safe zone", GuardianAlertText.safeZoneExitBody(" ", null));
    }

    @Test
    public void safeZoneReturnNamesThePerson() {
        assertEquals("Maya is back home", GuardianAlertText.safeZoneReturnBody("Maya"));
        assertEquals("The user is back home", GuardianAlertText.safeZoneReturnBody(null));
        assertEquals("🟢 Back home", GuardianAlertText.safeZoneReturnTitle());
    }

    @Test
    public void mapsLinkPrefersTheStoredLink() {
        assertEquals("https://www.google.com/maps?q=1.5,2.5",
                GuardianAlertText.mapsLink("https://www.google.com/maps?q=1.5,2.5", 9.0, 9.0));
    }

    @Test
    public void mapsLinkIsBuiltFromCoordinatesWhenNoLinkIsStored() {
        assertEquals("https://www.google.com/maps?q=28.61,77.23",
                GuardianAlertText.mapsLink(null, 28.61, 77.23));
        assertEquals("https://www.google.com/maps?q=28.61,77.23",
                GuardianAlertText.mapsLink("  ", 28.61, 77.23));
    }

    @Test
    public void mapsLinkIsNullWithNoLocationAtAll() {
        assertNull(GuardianAlertText.mapsLink(null, null, null));
        assertNull(GuardianAlertText.mapsLink("", 28.61, null));
    }

    @Test
    public void dialNumberKeepsDigitsAndALeadingPlus() {
        assertEquals("+919876543210", GuardianAlertText.dialNumber(" +91 98765-43210 "));
        assertEquals("9876543210", GuardianAlertText.dialNumber("(987) 654 3210"));
        assertEquals("+4412345", GuardianAlertText.dialNumber("+44 12+345"));
    }

    @Test
    public void dialNumberIsNullWhenThereIsNothingToDial() {
        assertNull(GuardianAlertText.dialNumber(null));
        assertNull(GuardianAlertText.dialNumber(""));
        assertNull(GuardianAlertText.dialNumber("   "));
        assertNull(GuardianAlertText.dialNumber("+"));
        assertNull(GuardianAlertText.dialNumber("n/a"));
    }
}
