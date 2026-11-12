package com.senseplus.sense;

import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConnectivityStatusTest {

    private static final long NOW = 1_000_000_000L;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void nullLastSeenIsNever() {
        assertEquals(ConnectivityStatus.State.NEVER,
                ConnectivityStatus.classify(NOW, null, true));
        assertEquals(ConnectivityStatus.State.NEVER,
                ConnectivityStatus.classify(NOW, null, false));
        assertEquals("Not connected yet",
                ConnectivityStatus.label(NOW, null, true, UTC));
    }

    @Test
    public void monitoringOffWinsOverRecent() {
        assertEquals(ConnectivityStatus.State.MONITORING_OFF,
                ConnectivityStatus.classify(NOW, NOW - 10_000L, false));
        assertEquals("Monitoring is off",
                ConnectivityStatus.label(NOW, NOW - 10_000L, false, UTC));
    }

    @Test
    public void tenSecondsIsOnline() {
        assertEquals(ConnectivityStatus.State.ONLINE,
                ConnectivityStatus.classify(NOW, NOW - 10_000L, true));
        assertEquals("Online",
                ConnectivityStatus.label(NOW, NOW - 10_000L, true, UTC));
    }

    @Test
    public void oneMinuteBoundary() {
        assertEquals(ConnectivityStatus.State.ONLINE,
                ConnectivityStatus.classify(NOW, NOW - 59_000L, true));
        assertEquals(ConnectivityStatus.State.RECENT,
                ConnectivityStatus.classify(NOW, NOW - 60_000L, true));
    }

    @Test
    public void fourMinutesIsRecentWithMinuteLabel() {
        assertEquals(ConnectivityStatus.State.RECENT,
                ConnectivityStatus.classify(NOW, NOW - 4 * 60_000L, true));
        assertEquals("Last seen 4 min ago",
                ConnectivityStatus.label(NOW, NOW - 4 * 60_000L, true, UTC));
    }

    @Test
    public void sixMinutesIsOffline() {
        assertEquals(ConnectivityStatus.State.OFFLINE,
                ConnectivityStatus.classify(NOW, NOW - 6 * 60_000L, true));
        assertTrue(ConnectivityStatus.label(NOW, NOW - 6 * 60_000L, true, UTC)
                .startsWith("Offline / no signal (last seen "));
    }

    @Test
    public void futureLastSeenIsOnlineNotNegative() {
        assertEquals(ConnectivityStatus.State.ONLINE,
                ConnectivityStatus.classify(NOW, NOW + 5_000L, true));
        assertEquals("Online",
                ConnectivityStatus.label(NOW, NOW + 5_000L, true, UTC));
    }
}
