package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MicArbiterTest {

    private final MicArbiter mic = new MicArbiter();

    @Test
    public void nothingListensBeforeMonitoringStarts() {
        assertFalse(mic.shouldListen());
    }

    @Test
    public void monitoringOnMeansListen() {
        assertTrue(mic.monitoringStarted());
        assertTrue(mic.shouldListen());
    }

    @Test
    public void hearingOpenedPausesTheKeywords() {
        mic.monitoringStarted();
        assertFalse(mic.hearingOpened());
        assertFalse(mic.shouldListen());
        assertTrue(mic.isHearingOpen());
    }

    @Test
    public void hearingClosedResumesTheKeywords() {
        mic.monitoringStarted();
        mic.hearingOpened();
        assertTrue(mic.hearingClosed());
        assertTrue(mic.shouldListen());
        assertFalse(mic.isHearingOpen());
    }

    @Test
    public void monitoringOffWhileHearingIsOpenStaysOffAfterHearingCloses() {
        mic.monitoringStarted();
        mic.hearingOpened();
        mic.monitoringStopped();
        assertFalse(mic.shouldListen());
        assertFalse(mic.hearingClosed()); // Hearing leaving must not switch the keywords back on
        assertFalse(mic.shouldListen());
    }

    @Test
    public void monitoringStartingWhileHearingIsOpenDoesNotListenUntilHearingCloses() {
        mic.hearingOpened();
        assertFalse(mic.monitoringStarted());
        assertFalse(mic.shouldListen());
        assertTrue(mic.hearingClosed());
        assertTrue(mic.shouldListen());
    }

    @Test
    public void monitoringOffMeansNoListeningWhateverHearingDoes() {
        assertFalse(mic.hearingClosed());
        assertFalse(mic.hearingOpened());
        assertFalse(mic.hearingClosed());
        mic.monitoringStarted();
        mic.monitoringStopped();
        assertFalse(mic.shouldListen());
    }

    @Test
    public void repeatedEventsChangeNothing() {
        mic.monitoringStarted();
        mic.monitoringStarted();
        assertTrue(mic.shouldListen());
        mic.hearingOpened();
        mic.hearingOpened(); // a screen that paused and resumed twice
        assertFalse(mic.shouldListen());
        mic.hearingClosed();
        mic.hearingClosed();
        assertTrue(mic.shouldListen());
    }

    @Test
    public void everyOrderOfTheFourEventsAgreesWithTheRule() {
        // The state is two flags, so after any sequence the answer is monitoring && !hearing.
        boolean[] monitoringSteps = {true, false};
        boolean[] hearingSteps = {true, false};
        for (boolean firstMonitoring : monitoringSteps) {
            for (boolean secondMonitoring : monitoringSteps) {
                for (boolean firstHearing : hearingSteps) {
                    for (boolean secondHearing : hearingSteps) {
                        MicArbiter a = new MicArbiter();
                        apply(a, firstMonitoring, true);
                        apply(a, firstHearing, false);
                        apply(a, secondMonitoring, true);
                        apply(a, secondHearing, false);
                        assertTrue(a.shouldListen() == (secondMonitoring && !secondHearing));
                    }
                }
            }
        }
    }

    /** on = monitoring started / hearing opened; off = monitoring stopped / hearing closed. */
    private static void apply(MicArbiter a, boolean on, boolean isMonitoring) {
        if (isMonitoring) {
            if (on) a.monitoringStarted(); else a.monitoringStopped();
        } else {
            if (on) a.hearingOpened(); else a.hearingClosed();
        }
    }
}
