package com.senseplus.sense;

import org.junit.Test;

import static com.senseplus.sense.BootReminderRule.Reminder.GUARDIAN;
import static com.senseplus.sense.BootReminderRule.Reminder.NONE;
import static com.senseplus.sense.BootReminderRule.Reminder.USER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BootReminderRuleTest {

    // ---------- user ----------

    @Test
    public void aSignedInUserWhoWantedMonitoringIsReminded() {
        assertEquals(USER, BootReminderRule.decide(true, Roles.USER, true, false));
    }

    @Test
    public void aUserWhoTurnedMonitoringOffOnPurposeIsLeftAlone() {
        assertEquals(NONE, BootReminderRule.decide(true, Roles.USER, false, false));
    }

    @Test
    public void aUserWhoNeverStartedMonitoringIsLeftAlone() {
        assertEquals(NONE, BootReminderRule.decide(true, Roles.USER, false, true));
    }

    // ---------- guardian ----------

    @Test
    public void aSignedInLinkedGuardianIsReminded() {
        assertEquals(GUARDIAN, BootReminderRule.decide(true, Roles.GUARDIAN, false, true));
        assertEquals(GUARDIAN, BootReminderRule.decide(true, Roles.GUARDIAN, true, true));
    }

    @Test
    public void aGuardianWithNobodyToWatchIsLeftAlone() {
        assertEquals(NONE, BootReminderRule.decide(true, Roles.GUARDIAN, false, false));
        assertEquals(NONE, BootReminderRule.decide(true, Roles.GUARDIAN, true, false));
    }

    // ---------- nobody signed in ----------

    @Test
    public void nobodyIsRemindedWhenSignedOutWhateverElseIsSet() {
        assertEquals(NONE, BootReminderRule.decide(false, Roles.USER, true, true));
        assertEquals(NONE, BootReminderRule.decide(false, Roles.GUARDIAN, true, true));
        assertEquals(NONE, BootReminderRule.decide(false, null, true, true));
    }

    @Test
    public void anUnknownOrMissingRoleGetsNothing() {
        assertEquals(NONE, BootReminderRule.decide(true, null, true, true));
        assertEquals(NONE, BootReminderRule.decide(true, "", true, true));
        assertEquals(NONE, BootReminderRule.decide(true, "admin", true, true));
    }

    // ---------- words ----------

    @Test
    public void theUserReminderSaysWhatTheTaskSays() {
        assertEquals("SENSE+ protection is off after a restart. Tap to turn it back on.",
                BootReminderRule.text(USER));
        assertEquals("SENSE+ protection is off", BootReminderRule.title(USER));
    }

    @Test
    public void theGuardianReminderSaysWhatTheTaskSays() {
        assertEquals("Tap to resume guardian alerts", BootReminderRule.text(GUARDIAN));
        assertTrue(BootReminderRule.title(GUARDIAN).contains("Guardian"));
    }

    @Test
    public void noReminderHasNoText() {
        assertEquals("", BootReminderRule.text(NONE));
    }
}
