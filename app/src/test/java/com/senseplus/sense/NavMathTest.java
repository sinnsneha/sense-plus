package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NavMathTest {

    @Test
    public void normalizeWrapsIntoMinus180ExclusiveTo180Inclusive() {
        assertEquals(-170.0, NavMath.normalizeDeg(190.0), 1e-9);
        assertEquals(170.0, NavMath.normalizeDeg(-190.0), 1e-9);
        assertEquals(180.0, NavMath.normalizeDeg(180.0), 1e-9);
        assertEquals(180.0, NavMath.normalizeDeg(-180.0), 1e-9);
        assertEquals(-1.0, NavMath.normalizeDeg(359.0), 1e-9);
        assertEquals(0.0, NavMath.normalizeDeg(360.0), 1e-9);
    }

    @Test
    public void directionAtSectorBoundaries() {
        assertEquals("straight ahead", NavMath.direction(14));
        assertEquals("slightly right", NavMath.direction(15));
        assertEquals("right", NavMath.direction(45));
        assertEquals("right", NavMath.direction(46));
        assertEquals("right", NavMath.direction(135));
        assertEquals("behind you", NavMath.direction(136));

        assertEquals("straight ahead", NavMath.direction(-14));
        assertEquals("slightly left", NavMath.direction(-15));
        assertEquals("left", NavMath.direction(-45));
        assertEquals("left", NavMath.direction(-135));
        assertEquals("behind you", NavMath.direction(-136));
    }

    @Test
    public void distancePhrases() {
        assertEquals("10 metres", NavMath.distancePhrase(8));
        assertEquals("320 metres", NavMath.distancePhrase(324));
        assertEquals("1.5 kilometres", NavMath.distancePhrase(1450));
    }

    @Test
    public void arrivedSentenceUnderTwentyMetres() {
        assertEquals("You have arrived home", NavMath.sentence(19.9, 90));
        assertEquals("You have arrived home", NavMath.sentence(0, 0));
    }

    @Test
    public void sentenceIncludesDistanceAndTurn() {
        assertEquals("Home is 320 metres, turn slightly left", NavMath.sentence(324, -30));
        assertEquals("Home is 1.5 kilometres, turn right", NavMath.sentence(1450, 90));
        assertEquals("Home is 320 metres, straight ahead", NavMath.sentence(324, 5));
        assertEquals("Home is 320 metres, turn around", NavMath.sentence(324, 170));
    }
}
