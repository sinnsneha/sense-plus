package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PairingRulesTest {

    @Test
    public void expiryBoundary() {
        long expires = 1_000_000L;
        assertFalse(PairingRules.isExpired(expires - 1, expires));
        assertTrue(PairingRules.isExpired(expires, expires));
        assertTrue(PairingRules.isExpired(expires + 1, expires));
    }

    @Test
    public void codeLivesTenMinutes() {
        long created = 5_000L;
        long expires = created + PairingRules.CODE_TTL_MS;
        assertFalse(PairingRules.isExpired(created + 9 * 60_000L, expires));
        assertTrue(PairingRules.isExpired(created + 10 * 60_000L, expires));
    }

    @Test
    public void secondsLeftRoundsUpAndNeverGoesNegative() {
        assertEquals(600, PairingRules.secondsLeft(0, 600_000));
        assertEquals(1, PairingRules.secondsLeft(599_001, 600_000));
        assertEquals(0, PairingRules.secondsLeft(600_000, 600_000));
        assertEquals(0, PairingRules.secondsLeft(700_000, 600_000));
    }

    @Test
    public void countdownText() {
        assertEquals("10:00", PairingRules.countdown(600));
        assertEquals("9:05", PairingRules.countdown(545));
        assertEquals("0:00", PairingRules.countdown(-3));
    }

    @Test
    public void unlinkOnlyAfterThreeRefusalsInARow() {
        assertFalse(PairingRules.shouldUnlink(0));
        assertFalse(PairingRules.shouldUnlink(2));
        assertTrue(PairingRules.shouldUnlink(3));
        assertTrue(PairingRules.shouldUnlink(4));
    }

    @Test
    public void backgroundPollIsSlowWhenCalmAndFastDuringAlerts() {
        assertEquals(5_000L, PairingRules.backgroundPollMs(null));
        assertEquals(5_000L, PairingRules.backgroundPollMs("NORMAL"));
        assertEquals(2_000L, PairingRules.backgroundPollMs("ATTENTION"));
        assertEquals(2_000L, PairingRules.backgroundPollMs("POSSIBLE EMERGENCY"));
    }
}
