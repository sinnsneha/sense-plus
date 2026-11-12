package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BatteryAlertRuleTest {

    @Test
    public void alertsOnceWhenTheLevelDropsBelowTheThreshold() {
        BatteryAlertRule rule = new BatteryAlertRule();
        assertFalse(rule.shouldAlert(50));
        assertFalse(rule.shouldAlert(15));   // 15 is not below 15
        assertTrue(rule.shouldAlert(14));
    }

    @Test
    public void doesNotAlertAgainAt14Or16() {
        BatteryAlertRule rule = new BatteryAlertRule();
        assertTrue(rule.shouldAlert(12));
        assertFalse(rule.shouldAlert(14));
        assertFalse(rule.shouldAlert(16));
        assertFalse(rule.shouldAlert(12));
    }

    @Test
    public void staysQuietBetweenTheThresholdAndTheRearmLevel() {
        BatteryAlertRule rule = new BatteryAlertRule();
        assertTrue(rule.shouldAlert(10));
        assertFalse(rule.shouldAlert(20));   // 20 is not above 20: still not re-armed
        assertFalse(rule.shouldAlert(13));
    }

    @Test
    public void rearmsAboveTwentyPercent() {
        BatteryAlertRule rule = new BatteryAlertRule();
        assertTrue(rule.shouldAlert(10));
        assertFalse(rule.shouldAlert(21));   // charged past 20: re-armed
        assertTrue(rule.shouldAlert(14));
    }

    @Test
    public void ignoresAnUnknownLevel() {
        BatteryAlertRule rule = new BatteryAlertRule();
        assertFalse(rule.shouldAlert(-1));
        assertTrue(rule.shouldAlert(10));    // the -1 did not use up the alert
        assertFalse(rule.shouldAlert(-1));
        assertFalse(rule.shouldAlert(30));   // and a -1 does not re-arm either
        assertTrue(rule.shouldAlert(10));    // re-armed by the real 30, not the -1
    }

    @Test
    public void unknownLevelDoesNotRearm() {
        BatteryAlertRule rule = new BatteryAlertRule();
        assertTrue(rule.shouldAlert(5));
        assertFalse(rule.shouldAlert(-1));
        assertFalse(rule.shouldAlert(5));    // still disarmed
    }
}
