package com.senseplus.sense;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Feeds synthetic sensor streams (50 Hz, explicit timestamps starting at 0) to FallDetector.
 * Gravity "before" and "after" a fall are vectors, so posture changes can be simulated.
 */
public class FallDetectorTest {

    private static final long DT = 20; // 50 Hz

    private static final float[] UPRIGHT = { 0f, 9.8f, 0f };  // gravity on y
    private static final float[] FLAT    = { 0f, 0f, 9.8f };  // gravity on z (90° from UPRIGHT)

    /** Drives a detector and records everything it reports. */
    private static class Rig implements FallDetector.Listener {
        final FallDetector detector;
        final boolean feedGyro;
        final List<FusionResult> falls = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        long t = 0;

        Rig(boolean gyroAvailable) {
            feedGyro = gyroAvailable;
            detector = new FallDetector(this, gyroAvailable);
        }

        @Override public void onFallDetected(FusionResult r) { falls.add(r); }
        @Override public void onStateChanged(String info) { messages.add(info); }

        /** One accelerometer (and gyroscope) sample, then time moves on by 20 ms. */
        void sample(float ax, float ay, float az, float rotation) {
            detector.processAccel(ax, ay, az, t);
            if (feedGyro) detector.processGyro(rotation, 0f, 0f, t);
            t += DT;
        }

        void hold(long ms, float[] gravity, float rotation) {
            for (long end = t + ms; t < end; ) sample(gravity[0], gravity[1], gravity[2], rotation);
        }

        /** rest → free-fall → impact (30 m/s²) → settle + stillness. */
        void fall(long freeFallMs, float rotation, float[] before, float[] after, long afterMs) {
            hold(1000, before, 0.1f);
            for (long end = t + freeFallMs; t < end; ) sample(0f, 0f, 1f, rotation);
            sample(0f, 0f, 30f, rotation);
            hold(afterMs, after, 0.1f);
        }

        String lastMessage() { return messages.get(messages.size() - 1); }
    }

    // 1
    @Test
    public void rotationConfirmsFall() {
        Rig rig = new Rig(true);
        rig.fall(300, 5f, UPRIGHT, UPRIGHT, 1600);

        assertEquals(1, rig.falls.size());
        FusionResult r = rig.falls.get(0);
        assertTrue(r.rotationPassed);
        assertFalse(r.posturePassed);
        assertEquals(5f, r.peakRotation, 0.01f);
    }

    // 2
    @Test
    public void noRotationAndSamePostureIsRejected() {
        Rig rig = new Rig(true);
        rig.fall(300, 0f, UPRIGHT, UPRIGHT, 1600);

        assertEquals(0, rig.falls.size());
        // the rejection, with its numbers, is the last thing reported (not overwritten by "ready")
        assertTrue(rig.lastMessage().startsWith("fusion reject"));
        assertTrue(rig.lastMessage().contains("impact without rotation or posture change"));
        assertTrue(rig.lastMessage().contains("rad/s"));
        assertTrue(rig.lastMessage().contains("posture"));
    }

    // 3
    @Test
    public void postureChangeAloneConfirmsFall() {
        Rig rig = new Rig(true);
        rig.fall(300, 0f, UPRIGHT, FLAT, 1600);

        assertEquals(1, rig.falls.size());
        FusionResult r = rig.falls.get(0);
        assertFalse(r.rotationPassed);
        assertTrue(r.posturePassed);
        assertEquals(90f, r.postureChangeDeg, 1f);
    }

    // 4 (DEVLOG bug 2)
    @Test
    public void tooShortFreeFallIsRejected() {
        Rig rig = new Rig(true);
        rig.hold(1000, UPRIGHT, 0.1f);
        for (int i = 0; i < 3; i++) rig.sample(0f, 0f, 1f, 5f); // ~50 ms weightless
        rig.sample(0f, 0f, 30f, 5f);
        assertTrue(rig.lastMessage().startsWith("bump, not a fall"));
        rig.hold(2000, FLAT, 0.1f);

        assertEquals(0, rig.falls.size());
    }

    // 5
    @Test
    public void movementAfterImpactIsRecovery() {
        Rig rig = new Rig(true);
        rig.hold(1000, UPRIGHT, 0.1f);
        for (int i = 0; i < 15; i++) rig.sample(0f, 0f, 1f, 5f);
        rig.sample(0f, 0f, 30f, 5f);
        rig.hold(500, FLAT, 0.1f);        // past the 400 ms settle
        rig.sample(0f, 0f, 20f, 0.1f);    // strong movement: |20 - 9.8| > 4
        assertTrue(rig.lastMessage().startsWith("movement after impact"));
        rig.hold(2000, FLAT, 0.1f);

        assertEquals(0, rig.falls.size());
    }

    // 6 (DEVLOG bug 3)
    @Test
    public void secondFallInsideCooldownIsIgnored() {
        Rig rig = new Rig(true);
        rig.fall(300, 5f, UPRIGHT, UPRIGHT, 1600);
        assertEquals(1, rig.falls.size());

        rig.fall(300, 5f, UPRIGHT, UPRIGHT, 1600); // completes well inside the 5 s cooldown
        assertEquals(1, rig.falls.size());
    }

    // 7
    @Test
    public void withoutGyroscopePostureAloneDecides() {
        Rig rig = new Rig(false);
        rig.fall(300, 0f, UPRIGHT, FLAT, 1600);

        assertEquals(1, rig.falls.size());
        FusionResult r = rig.falls.get(0);
        assertFalse(r.gyroAvailable);
        assertFalse(r.rotationPassed);
        assertTrue(r.posturePassed);
        assertTrue(r.summary().contains("gyro n/a"));
    }

    // --- extras ---

    @Test
    public void withoutGyroscopeAndSamePostureIsRejected() {
        Rig rig = new Rig(false);
        rig.fall(300, 0f, UPRIGHT, UPRIGHT, 1600);

        assertEquals(0, rig.falls.size());
        assertTrue(rig.lastMessage().startsWith("fusion reject"));
        assertTrue(rig.lastMessage().contains("gyro n/a"));
    }

    @Test
    public void preFallGravitySurvivesLongFreeFall() {
        // Free-fall of ~1 s: by the time stillness is confirmed, any 500-700 ms buffer has moved on.
        Rig rig = new Rig(true);
        rig.fall(1000, 0f, UPRIGHT, FLAT, 1600);

        assertEquals(1, rig.falls.size());
        assertEquals(90f, rig.falls.get(0).postureChangeDeg, 1f);
    }

    @Test
    public void fallIsDetectedAgainAfterCooldown() {
        Rig rig = new Rig(true);
        rig.fall(300, 5f, UPRIGHT, UPRIGHT, 1600);
        rig.hold(6000, UPRIGHT, 0.1f); // let the 5 s cooldown pass
        rig.fall(300, 5f, UPRIGHT, UPRIGHT, 1600);

        assertEquals(2, rig.falls.size());
    }

    @Test
    public void restingOnATableNeverTriggers() {
        Rig rig = new Rig(true);
        rig.hold(10000, FLAT, 0.05f);

        assertEquals(0, rig.falls.size());
    }
}
