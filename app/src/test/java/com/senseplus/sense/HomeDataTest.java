package com.senseplus.sense;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HomeDataTest {

    private static HomeData home(long updatedAt) {
        return new HomeData(28.6139, 77.2090, "12 Park Road", 250, true, updatedAt);
    }

    // ---------- radius ----------

    @Test
    public void onlyTheFourOfferedRadiiAreKept() {
        assertEquals(100, HomeData.normalizeRadius(100));
        assertEquals(250, HomeData.normalizeRadius(250));
        assertEquals(500, HomeData.normalizeRadius(500));
        assertEquals(1000, HomeData.normalizeRadius(1000));
    }

    @Test
    public void anythingElseBecomesTheDefault250() {
        assertEquals(250, HomeData.DEFAULT_RADIUS_M);
        assertEquals(250, HomeData.normalizeRadius(0));
        assertEquals(250, HomeData.normalizeRadius(-5));
        assertEquals(250, HomeData.normalizeRadius(300));
        assertEquals(250, HomeData.normalizeRadius(Long.MAX_VALUE));
    }

    @Test
    public void radiusIndexMatchesTheSpinnerOrder() {
        assertEquals(0, HomeData.radiusIndex(100));
        assertEquals(1, HomeData.radiusIndex(250));
        assertEquals(2, HomeData.radiusIndex(500));
        assertEquals(3, HomeData.radiusIndex(1000));
        assertEquals(1, HomeData.radiusIndex(999)); // unknown value: the default's position
    }

    // ---------- location ----------

    @Test
    public void hasLocationNeedsRealCoordinates() {
        assertTrue(home(1).hasLocation());
        assertFalse(new HomeData(Double.NaN, 77, "", 250, false, 0).hasLocation());
        assertFalse(new HomeData(95, 77, "", 250, false, 0).hasLocation());
        assertFalse(new HomeData(28, 181, "", 250, false, 0).hasLocation());
    }

    @Test
    public void nullIslandIsAnUnsetHomeNotAHome() {
        assertFalse(new HomeData(0, 0, "", 250, true, 0).hasLocation());
    }

    // ---------- map round trip ----------

    @Test
    public void mapRoundTripKeepsEveryField() {
        HomeData back = HomeData.fromMap(home(1234L).toMap());
        assertEquals(28.6139, back.lat, 0.0);
        assertEquals(77.2090, back.lon, 0.0);
        assertEquals("12 Park Road", back.address);
        assertEquals(250, back.radiusM);
        assertTrue(back.enabled);
        assertEquals(1234L, back.updatedAt);
    }

    @Test
    public void theMapUsesTheFieldNamesOfTheSpec() {
        Map<String, Object> m = home(1).toMap();
        assertTrue(m.containsKey("lat"));
        assertTrue(m.containsKey("lon"));
        assertTrue(m.containsKey("address"));
        assertTrue(m.containsKey("radiusM"));
        assertTrue(m.containsKey("enabled"));
    }

    @Test
    public void fromMapAcceptsWholeNumbersAndLongsFromFirestore() {
        Map<String, Object> m = new HashMap<>();
        m.put("lat", 28L);       // a whole-number latitude arrives as Long
        m.put("lon", 77.5);
        m.put("radiusM", 500L);
        m.put("enabled", Boolean.TRUE);
        m.put("updatedAt", 99L);
        HomeData h = HomeData.fromMap(m);
        assertEquals(28.0, h.lat, 0.0);
        assertEquals(500, h.radiusM);
        assertEquals("", h.address);
        assertEquals(99L, h.updatedAt);
        assertTrue(h.hasLocation());
    }

    @Test
    public void fromMapOfNothingOrJunkHasNoLocationAndIsOff() {
        HomeData none = HomeData.fromMap(null);
        assertFalse(none.hasLocation());
        assertFalse(none.enabled);
        assertEquals(250, none.radiusM);

        Map<String, Object> junk = new HashMap<>();
        junk.put("lat", "north");
        junk.put("enabled", "yes");
        HomeData h = HomeData.fromMap(junk);
        assertFalse(h.hasLocation());
        assertFalse(h.enabled);
    }

    // ---------- who wins ----------

    @Test
    public void aNewerCloudHomeReplacesThePhone() {
        assertTrue(HomeData.cloudReplacesLocal(home(2000), true, 1000));
    }

    @Test
    public void anOlderOrEqualCloudHomeNeverUndoesTheUsersLastChange() {
        assertFalse(HomeData.cloudReplacesLocal(home(1000), true, 2000));
        assertFalse(HomeData.cloudReplacesLocal(home(1000), true, 1000));
        assertFalse(HomeData.cloudReplacesLocal(home(0), true, 5));
    }

    @Test
    public void aPhoneWithNoHomeTakesTheCloudHomeWhateverItsAge() {
        assertTrue(HomeData.cloudReplacesLocal(home(0), false, 999999));
    }

    @Test
    public void aCloudWithoutARealHomeNeverReplacesAnything() {
        assertFalse(HomeData.cloudReplacesLocal(null, false, 0));
        assertFalse(HomeData.cloudReplacesLocal(HomeData.fromMap(null), false, 0));
        assertFalse(HomeData.cloudReplacesLocal(HomeData.fromMap(null), true, 0));
    }

    @Test
    public void aHomeSavedBeforeThisSegmentIsUploadedOnce() {
        assertTrue(HomeData.localShouldBePushed(null, true));
        assertTrue(HomeData.localShouldBePushed(HomeData.fromMap(null), true));
        assertFalse(HomeData.localShouldBePushed(home(1), true));  // the cloud already has one
        assertFalse(HomeData.localShouldBePushed(null, false));    // nothing to upload
    }
}
