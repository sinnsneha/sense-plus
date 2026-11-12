package com.senseplus.sense;

import org.junit.Test;

import static com.senseplus.sense.SafeZoneRule.Result.LEFT;
import static com.senseplus.sense.SafeZoneRule.Result.NO_CHANGE;
import static com.senseplus.sense.SafeZoneRule.Result.RETURNED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SafeZoneRuleTest {

    private static final double RADIUS = 250;   // the default
    private static final double ACC = 10;       // a good outdoor fix

    /** A fix clearly outside: 400 m away, 10 m accuracy -> 390 > 250 + 50. */
    private static final double FAR = 400;

    private final SafeZoneRule rule = new SafeZoneRule();

    private SafeZoneRule.Result fix(double distance, double accuracy) {
        return rule.onFix(distance, accuracy, RADIUS, true);
    }

    // ---------- leaving: confirmation ----------

    @Test
    public void oneOutsideFixIsNotEnough() {
        assertEquals(NO_CHANGE, fix(FAR, ACC));
        assertFalse(rule.isOutside());
    }

    @Test
    public void twoOutsideFixesInARowIsLeft() {
        assertEquals(NO_CHANGE, fix(FAR, ACC));
        assertEquals(LEFT, fix(FAR, ACC));
        assertTrue(rule.isOutside());
    }

    @Test
    public void aFixInTheBandBetweenTwoOutsideFixesBreaksTheRun() {
        assertEquals(NO_CHANGE, fix(FAR, ACC));
        assertEquals(NO_CHANGE, fix(200, ACC)); // back inside: the run starts again
        assertEquals(NO_CHANGE, fix(FAR, ACC));
        assertEquals(LEFT, fix(FAR, ACC));
    }

    @Test
    public void onlyOneLeftPerTransitionHoweverLongTheyStayOut() {
        fix(FAR, ACC);
        assertEquals(LEFT, fix(FAR, ACC));
        for (int i = 0; i < 20; i++) assertEquals(NO_CHANGE, fix(FAR + i, ACC));
    }

    // ---------- accuracy gate ----------

    @Test
    public void aFixWith120mAccuracyIsIgnored() {
        assertEquals(NO_CHANGE, fix(2000, 120));
        assertEquals(NO_CHANGE, fix(2000, 120));
        assertFalse(rule.isOutside()); // two vague fixes never count as two confirmations
    }

    @Test
    public void anIgnoredFixDoesNotBreakOrAddToTheRun() {
        assertEquals(NO_CHANGE, fix(FAR, ACC));
        assertEquals(NO_CHANGE, fix(2000, 120)); // ignored: neither counts nor resets
        assertEquals(LEFT, fix(FAR, ACC));       // the second GOOD outside fix
    }

    @Test
    public void accuracyOfExactly100mIsStillUsed() {
        // 1000 - 100 = 900 > 300
        assertEquals(NO_CHANGE, rule.onFix(1000, 100, RADIUS, true));
        assertEquals(LEFT, rule.onFix(1000, 100, RADIUS, true));
    }

    @Test
    public void aMissingAccuracyIsIgnored() {
        assertEquals(NO_CHANGE, fix(FAR, 0));
        assertEquals(NO_CHANGE, fix(FAR, 0));
        assertEquals(NO_CHANGE, fix(FAR, Double.NaN));
        assertFalse(rule.isOutside());
    }

    @Test
    public void accuracyIsSubtractedWhenDecidingOutside() {
        // 340 - 60 = 280, not past 250 + 50 = 300: not clearly outside however often it repeats.
        for (int i = 0; i < 5; i++) assertEquals(NO_CHANGE, fix(340, 60));
        assertFalse(rule.isOutside());
    }

    // ---------- hysteresis ----------

    @Test
    public void insideTheBandJustPastTheRadiusIsStillInside() {
        for (int i = 0; i < 10; i++) assertEquals(NO_CHANGE, fix(RADIUS + 30, 5));
        assertFalse(rule.isOutside());
    }

    @Test
    public void exactlyAtTheMarginIsNotOutside() {
        // distance - accuracy == radius + 50 is not "greater than"
        for (int i = 0; i < 5; i++) assertEquals(NO_CHANGE, fix(RADIUS + 50 + ACC, ACC));
        assertFalse(rule.isOutside());
    }

    @Test
    public void justPastTheMarginIsOutside() {
        assertEquals(NO_CHANGE, fix(RADIUS + 50 + ACC + 1, ACC));
        assertEquals(LEFT, fix(RADIUS + 50 + ACC + 1, ACC));
    }

    @Test
    public void afterLeftTheBandNearTheEdgeDoesNotReturn() {
        fix(FAR, ACC);
        fix(FAR, ACC);
        // 280 + 5 = 285 is not < 250: still outside, no alert in either direction
        for (int i = 0; i < 10; i++) assertEquals(NO_CHANGE, fix(RADIUS + 30, 5));
        assertTrue(rule.isOutside());
    }

    @Test
    public void pacingAlongTheEdgeNeverAlertsRepeatedly() {
        int alerts = 0;
        double[] distances = {240, 262, 248, 281, 255, 270, 245, 290, 252, 266, 249, 283};
        for (int round = 0; round < 5; round++) {
            for (double d : distances) {
                if (fix(d, 8) != NO_CHANGE) alerts++;
            }
        }
        assertEquals(0, alerts);
    }

    // ---------- returning ----------

    @Test
    public void afterLeftAFixClearlyInsideIsReturned() {
        fix(FAR, ACC);
        assertEquals(LEFT, fix(FAR, ACC));
        assertEquals(RETURNED, fix(200, ACC)); // 200 + 10 = 210 < 250
        assertFalse(rule.isOutside());
    }

    @Test
    public void returnNeedsDistancePlusAccuracyBelowTheRadius() {
        fix(FAR, ACC);
        fix(FAR, ACC);
        assertEquals(NO_CHANGE, fix(240, 10)); // 250 is not < 250
        assertEquals(RETURNED, fix(239, 10));  // 249 < 250
    }

    @Test
    public void aReturnedFixNeedsNoSecondConfirmation() {
        fix(FAR, ACC);
        fix(FAR, ACC);
        assertEquals(RETURNED, fix(100, ACC));
    }

    @Test
    public void aVagueFixInsideDoesNotReturn() {
        fix(FAR, ACC);
        fix(FAR, ACC);
        assertEquals(NO_CHANGE, fix(100, 150)); // ignored: accuracy over 100 m
        assertTrue(rule.isOutside());
    }

    @Test
    public void canLeaveAgainAfterReturning() {
        fix(FAR, ACC);
        assertEquals(LEFT, fix(FAR, ACC));
        assertEquals(RETURNED, fix(50, ACC));
        assertEquals(NO_CHANGE, fix(FAR, ACC));
        assertEquals(LEFT, fix(FAR, ACC));
    }

    @Test
    public void whileInsideNothingHappens() {
        for (int i = 0; i < 20; i++) assertEquals(NO_CHANGE, fix(30, ACC));
    }

    // ---------- disabled ----------

    @Test
    public void disabledIsAlwaysNoChange() {
        for (int i = 0; i < 10; i++) assertEquals(NO_CHANGE, rule.onFix(5000, ACC, RADIUS, false));
        assertFalse(rule.isOutside());
    }

    @Test
    public void switchingOffForgetsThePartialRunAndTheOutsideState() {
        fix(FAR, ACC);                                     // one confirmation so far
        assertEquals(NO_CHANGE, rule.onFix(FAR, ACC, RADIUS, false));
        assertEquals(NO_CHANGE, fix(FAR, ACC));            // starts from zero again
        assertEquals(LEFT, fix(FAR, ACC));
        assertEquals(NO_CHANGE, rule.onFix(FAR, ACC, RADIUS, false));
        assertFalse(rule.isOutside());
    }

    // ---------- radius and restore ----------

    @Test
    public void theRadiusDecidesWhatIsOutside() {
        // 400 m away: outside a 100 m zone, inside a 500 m zone
        assertEquals(NO_CHANGE, rule.onFix(400, ACC, 100, true));
        assertEquals(LEFT, rule.onFix(400, ACC, 100, true));
        SafeZoneRule other = new SafeZoneRule();
        for (int i = 0; i < 5; i++) assertEquals(NO_CHANGE, other.onFix(400, ACC, 500, true));
    }

    @Test
    public void restoreOutsideMeansNoSecondLeftButAReturnStillWorks() {
        rule.restore(true);
        assertTrue(rule.isOutside());
        for (int i = 0; i < 5; i++) assertEquals(NO_CHANGE, fix(FAR, ACC));
        assertEquals(RETURNED, fix(100, ACC));
    }

    @Test
    public void restoreInsideStartsFromInside() {
        rule.restore(false);
        assertEquals(NO_CHANGE, fix(100, ACC));
        assertFalse(rule.isOutside());
    }

    @Test
    public void theConstantsAreTheSpecifiedOnes() {
        assertEquals(100.0, SafeZoneRule.MAX_ACCURACY_M, 0.0);
        assertEquals(50.0, SafeZoneRule.EXIT_MARGIN_M, 0.0);
        assertEquals(2, SafeZoneRule.EXIT_CONFIRMATIONS);
    }
}
