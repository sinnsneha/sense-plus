package com.senseplus.sense;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class LabelAnnouncerTest {

    private static List<LabelAnnouncer.Label> labels(LabelAnnouncer.Label... labels) {
        return Arrays.asList(labels);
    }

    @Test
    public void labelsBelowThresholdAreDropped() {
        LabelAnnouncer announcer = new LabelAnnouncer();
        assertNull(announcer.announce(labels(
                new LabelAnnouncer.Label("door", 0.69f),
                new LabelAnnouncer.Label("chair", 0.20f)), 1000));
    }

    @Test
    public void onlyTopThreeAreKeptInOrder() {
        LabelAnnouncer announcer = new LabelAnnouncer();
        String sentence = announcer.announce(labels(
                new LabelAnnouncer.Label("person", 0.75f),
                new LabelAnnouncer.Label("door", 0.95f),
                new LabelAnnouncer.Label("chair", 0.85f),
                new LabelAnnouncer.Label("car", 0.71f)), 1000);
        assertEquals("I see: door, chair, person", sentence);
    }

    @Test
    public void secondCallWithinFourSecondsIsSilent() {
        LabelAnnouncer announcer = new LabelAnnouncer();
        List<LabelAnnouncer.Label> input = labels(new LabelAnnouncer.Label("door", 0.90f));
        assertNotNull(announcer.announce(input, 1000));
        assertNull(announcer.announce(input, 4500));
    }

    @Test
    public void sameSetAfterSixSecondsIsSuppressed() {
        LabelAnnouncer announcer = new LabelAnnouncer();
        List<LabelAnnouncer.Label> input = labels(new LabelAnnouncer.Label("door", 0.90f));
        assertNotNull(announcer.announce(input, 1000));
        assertNull(announcer.announce(input, 7000));
    }

    @Test
    public void sameSetAfterElevenSecondsIsSpoken() {
        LabelAnnouncer announcer = new LabelAnnouncer();
        List<LabelAnnouncer.Label> input = labels(new LabelAnnouncer.Label("door", 0.90f));
        assertNotNull(announcer.announce(input, 1000));
        assertNotNull(announcer.announce(input, 12000));
    }

    @Test
    public void differentSetAfterFiveSecondsIsSpoken() {
        LabelAnnouncer announcer = new LabelAnnouncer();
        assertNotNull(announcer.announce(
                labels(new LabelAnnouncer.Label("door", 0.90f)), 1000));
        assertNotNull(announcer.announce(
                labels(new LabelAnnouncer.Label("chair", 0.90f)), 6000));
    }

    @Test
    public void emptyInputIsSilent() {
        LabelAnnouncer announcer = new LabelAnnouncer();
        assertNull(announcer.announce(Collections.emptyList(), 1000));
        assertNull(announcer.announce(null, 2000));
    }
}
