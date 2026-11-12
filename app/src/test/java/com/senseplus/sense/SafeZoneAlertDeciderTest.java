package com.senseplus.sense;

import org.junit.Test;

import static com.senseplus.sense.SafeZoneAlertDecider.Alert.LEFT;
import static com.senseplus.sense.SafeZoneAlertDecider.Alert.NONE;
import static com.senseplus.sense.SafeZoneAlertDecider.Alert.RETURNED;
import static com.senseplus.sense.SafeZoneAlertDecider.INSIDE;
import static com.senseplus.sense.SafeZoneAlertDecider.OUTSIDE;
import static org.junit.Assert.assertEquals;

public class SafeZoneAlertDeciderTest {

    // ---------- transitions ----------

    @Test
    public void insideToOutsideIsLeft() {
        assertEquals(LEFT, SafeZoneAlertDecider.decide(INSIDE, OUTSIDE));
    }

    @Test
    public void outsideToInsideIsReturned() {
        assertEquals(RETURNED, SafeZoneAlertDecider.decide(OUTSIDE, INSIDE));
    }

    @Test
    public void theSameValueTwiceSaysNothing() {
        assertEquals(NONE, SafeZoneAlertDecider.decide(OUTSIDE, OUTSIDE));
        assertEquals(NONE, SafeZoneAlertDecider.decide(INSIDE, INSIDE));
    }

    @Test
    public void oneAlertPerTransitionAcrossARepeatedPoll() {
        String previous = null;
        int alerts = 0;
        String[] polls = {INSIDE, INSIDE, OUTSIDE, OUTSIDE, OUTSIDE, INSIDE, INSIDE, OUTSIDE};
        for (String poll : polls) {
            if (SafeZoneAlertDecider.decide(previous, poll) != NONE) alerts++;
            previous = poll;
        }
        assertEquals(3, alerts); // left, returned, left: not one per poll
    }

    // ---------- first poll (segment 24's pattern) ----------

    @Test
    public void anOutsideAlreadyInProgressOnTheFirstPollIsAnnounced() {
        assertEquals(LEFT, SafeZoneAlertDecider.decide(null, OUTSIDE));
    }

    @Test
    public void anInsideOnTheFirstPollIsNotAnnounced() {
        assertEquals(NONE, SafeZoneAlertDecider.decide(null, INSIDE));
    }

    // ---------- no information ----------

    @Test
    public void aMissingOrUnknownValueIsNoInformation() {
        assertEquals(NONE, SafeZoneAlertDecider.decide(OUTSIDE, null));
        assertEquals(NONE, SafeZoneAlertDecider.decide(INSIDE, null));
        assertEquals(NONE, SafeZoneAlertDecider.decide(null, null));
        assertEquals(NONE, SafeZoneAlertDecider.decide(OUTSIDE, "somewhere"));
        assertEquals(NONE, SafeZoneAlertDecider.decide(null, ""));
    }

    @Test
    public void theValuesAreTheOnesTheSpecNames() {
        assertEquals("outside", OUTSIDE);
        assertEquals("inside", INSIDE);
    }

    // ---------- dashboard chip ----------

    @Test
    public void chipHasNoHomeWhenThereIsNoHomeWhateverElseIsSet() {
        assertEquals(SafeZoneStatus.State.NO_HOME, SafeZoneStatus.classify(false, true, OUTSIDE));
        assertEquals(SafeZoneStatus.State.NO_HOME, SafeZoneStatus.classify(false, false, null));
    }

    @Test
    public void chipIsOffWhenTheZoneIsSwitchedOffEvenWithAStaleOutside() {
        assertEquals(SafeZoneStatus.State.OFF, SafeZoneStatus.classify(true, false, OUTSIDE));
        assertEquals(SafeZoneStatus.State.OFF, SafeZoneStatus.classify(true, false, null));
    }

    @Test
    public void chipFollowsTheSafeZoneFieldWhenOn() {
        assertEquals(SafeZoneStatus.State.OUTSIDE, SafeZoneStatus.classify(true, true, OUTSIDE));
        assertEquals(SafeZoneStatus.State.INSIDE, SafeZoneStatus.classify(true, true, INSIDE));
    }

    @Test
    public void chipWaitsWhenOnButNothingHasBeenReported() {
        assertEquals(SafeZoneStatus.State.WAITING, SafeZoneStatus.classify(true, true, null));
        assertEquals(SafeZoneStatus.State.WAITING, SafeZoneStatus.classify(true, true, "??"));
    }
}
