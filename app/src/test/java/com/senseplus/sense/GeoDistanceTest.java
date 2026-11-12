package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeoDistanceTest {

    @Test
    public void samePointIsZero() {
        assertEquals(0.0, GeoDistance.metres(28.6139, 77.2090, 28.6139, 77.2090), 1e-6);
    }

    @Test
    public void oneDegreeOfLatitudeIsAbout111Kilometres() {
        assertEquals(111_195.0, GeoDistance.metres(0, 0, 1, 0), 10.0);
        assertEquals(111_195.0, GeoDistance.metres(28, 77, 29, 77), 10.0);
    }

    @Test
    public void oneDegreeOfLongitudeShrinksTowardsThePoles() {
        assertEquals(111_195.0, GeoDistance.metres(0, 10, 0, 11), 10.0);   // at the equator
        assertEquals(55_597.0, GeoDistance.metres(60, 10, 60, 11), 15.0);  // cos 60 = 0.5
    }

    @Test
    public void distanceIsTheSameBothWays() {
        double a = GeoDistance.metres(28.6139, 77.2090, 28.6200, 77.2150);
        double b = GeoDistance.metres(28.6200, 77.2150, 28.6139, 77.2090);
        assertEquals(a, b, 1e-6);
    }

    @Test
    public void aShortWalkIsMeasuredInMetres() {
        // 0.0018 degrees of latitude is about 200 m
        assertEquals(200.0, GeoDistance.metres(28.6139, 77.2090, 28.6157, 77.2090), 2.0);
    }

    @Test
    public void crossingTheDateLineTakesTheShortWay() {
        assertEquals(22_239.0, GeoDistance.metres(0, 179.9, 0, -179.9), 20.0);
    }

    @Test
    public void labelRoundsToTenMetresUnderAKilometre() {
        assertEquals("450 m", GeoDistance.label(450));
        assertEquals("450 m", GeoDistance.label(447.9));
        assertEquals("460 m", GeoDistance.label(455.1));
        assertEquals("120 m", GeoDistance.label(120));
    }

    @Test
    public void labelUsesKilometresFromOneThousand() {
        assertEquals("1.0 km", GeoDistance.label(1000));
        assertEquals("1.2 km", GeoDistance.label(1234));
        assertEquals("12.5 km", GeoDistance.label(12_500));
    }

    @Test
    public void labelUsesADotWhateverTheLocale() {
        assertTrue(GeoDistance.label(2500).contains("."));
    }
}
