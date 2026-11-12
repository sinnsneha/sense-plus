package com.senseplus.sense;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AckRuleTest {

    private static final long START = 1_800_000_000_000L; // the moment the alert began

    // ---------- isFresh ----------

    @Test
    public void ackBeforeTheAlertIsStale() {
        assertFalse(AckRule.isFresh(START - 60_000L, START));        // a minute earlier
        assertFalse(AckRule.isFresh(START - 24 * 3_600_000L, START)); // yesterday's alert
    }

    @Test
    public void ackAfterTheAlertIsFresh() {
        assertTrue(AckRule.isFresh(START + 1L, START));
        assertTrue(AckRule.isFresh(START + 12_000L, START));
        assertTrue(AckRule.isFresh(START, START));
    }

    @Test
    public void ackWithinTheSkewToleranceIsFresh() {
        assertTrue(AckRule.isFresh(START - 3_000L, START));
        assertTrue(AckRule.isFresh(START - AckRule.CLOCK_SKEW_TOLERANCE_MS, START)); // exactly 5 s
    }

    @Test
    public void ackJustOutsideTheToleranceIsStale() {
        assertFalse(AckRule.isFresh(START - AckRule.CLOCK_SKEW_TOLERANCE_MS - 1L, START));
    }

    @Test
    public void toleranceIsFiveSeconds() {
        assertEquals(5_000L, AckRule.CLOCK_SKEW_TOLERANCE_MS);
    }

    @Test
    public void nullAckIsStale() {
        assertFalse(AckRule.isFresh(null, START));
    }

    @Test
    public void noAlertStartedMeansNothingIsFresh() {
        assertFalse(AckRule.isFresh(START, 0L));
        assertFalse(AckRule.isFresh(START, -1L));
    }

    // ---------- classify: first / also / none ----------

    @Test
    public void firstFreshAckIsFirst() {
        assertEquals(AckRule.Outcome.FIRST,
                AckRule.classify(START + 4_000L, "guardianA", START, Collections.<String>emptySet()));
        assertEquals(AckRule.Outcome.FIRST,
                AckRule.classify(START + 4_000L, "guardianA", START, null));
    }

    @Test
    public void aStaleAckIsNeverAnnounced() {
        assertEquals(AckRule.Outcome.NONE,
                AckRule.classify(START - 3_600_000L, "guardianA", START, new HashSet<String>()));
        assertEquals(AckRule.Outcome.NONE,
                AckRule.classify(null, "guardianA", START, new HashSet<String>()));
    }

    @Test
    public void aMalformedAckWithoutAnAuthorIsIgnored() {
        assertEquals(AckRule.Outcome.NONE, AckRule.classify(START + 1L, null, START, null));
        assertEquals(AckRule.Outcome.NONE, AckRule.classify(START + 1L, "", START, null));
    }

    @Test
    public void theSameGuardianIsAnnouncedOnlyOnce() {
        HashSet<String> announced = new HashSet<>(Collections.singleton("guardianA"));
        assertEquals(AckRule.Outcome.NONE,
                AckRule.classify(START + 20_000L, "guardianA", START, announced)); // tapped again
    }

    @Test
    public void aDifferentGuardianAfterTheFirstIsAlso() {
        HashSet<String> announced = new HashSet<>(Collections.singleton("guardianA"));
        assertEquals(AckRule.Outcome.ALSO,
                AckRule.classify(START + 20_000L, "guardianB", START, announced));
    }

    @Test
    public void everyGuardianAlreadyAnnouncedStaysQuiet() {
        HashSet<String> announced = new HashSet<>(Arrays.asList("guardianA", "guardianB"));
        assertEquals(AckRule.Outcome.NONE,
                AckRule.classify(START + 30_000L, "guardianB", START, announced));
        assertEquals(AckRule.Outcome.ALSO,
                AckRule.classify(START + 30_000L, "guardianC", START, announced));
    }

    // ---------- polling cadence ----------

    @Test
    public void pollsEveryFiveSecondsUntilSomeoneAnswers() {
        assertEquals(5_000L, AckRule.POLL_MS);
        assertEquals(AckRule.POLL_MS, AckRule.nextPollDelayMs(false));
    }

    @Test
    public void afterTheFirstAnswerItOnlyWatchesSlowlyForASecondGuardian() {
        assertEquals(AckRule.SECOND_GUARDIAN_POLL_MS, AckRule.nextPollDelayMs(true));
        assertTrue(AckRule.SECOND_GUARDIAN_POLL_MS == 0
                || AckRule.SECOND_GUARDIAN_POLL_MS > AckRule.POLL_MS);
    }

    // ---------- wording ----------

    @Test
    public void firstAnnouncementNamesTheGuardian() {
        assertEquals("Ravi has seen your alert and is on the way.",
                AckRule.spoken(AckRule.Outcome.FIRST, "Ravi"));
        assertEquals("Ravi has seen your alert and is on the way.",
                AckRule.spoken(AckRule.Outcome.FIRST, "  Ravi "));
    }

    @Test
    public void secondAnnouncementSaysAlsoComing() {
        assertEquals("Maya is also coming.", AckRule.spoken(AckRule.Outcome.ALSO, "Maya"));
    }

    @Test
    public void aMissingNameStillMakesASensibleSentence() {
        assertEquals("Your guardian has seen your alert and is on the way.",
                AckRule.spoken(AckRule.Outcome.FIRST, null));
        assertEquals("Another guardian is also coming.", AckRule.spoken(AckRule.Outcome.ALSO, ""));
    }

    @Test
    public void nothingToSayForNone() {
        assertEquals("", AckRule.spoken(AckRule.Outcome.NONE, "Ravi"));
        assertEquals("", AckRule.eventDetail(AckRule.Outcome.NONE, "Ravi"));
    }

    @Test
    public void eventDetailNeverSaysYour() {
        assertEquals("Ravi is on the way", AckRule.eventDetail(AckRule.Outcome.FIRST, "Ravi"));
        assertEquals("Maya is also coming", AckRule.eventDetail(AckRule.Outcome.ALSO, "Maya"));
        assertEquals("A guardian is on the way", AckRule.eventDetail(AckRule.Outcome.FIRST, null));
    }
}
