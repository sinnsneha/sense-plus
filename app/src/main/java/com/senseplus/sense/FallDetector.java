package com.senseplus.sense;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Fall detector: an accelerometer sequence (free-fall, impact, stillness) confirmed by
 * sensor fusion (gyroscope rotation OR a change in phone posture).
 *
 * Pure Java: every input carries its own timestamp in ms and nothing here reads the clock.
 */
public class FallDetector {

    // We tell the service what's happening through these callbacks
    public interface Listener {
        void onFallDetected(FusionResult result);
        void onStateChanged(String info);
    }

    // --- Accelerometer sequence (DEVLOG bugs 1-3: do not change) ---
    private static final float FREE_FALL_MAX = 4f;     // below this = weightless (falling)
    private static final float IMPACT_MIN    = 25f;    // above this = hard impact
    private static final float GRAVITY       = 9.8f;   // resting magnitude
    private static final float STILL_TOLERANCE = 4f;   // how far from gravity still counts as "still"
    private static final long  FREE_FALL_TO_IMPACT_MAX_MS = 1200; // impact must follow free-fall quickly
    private static final long  MIN_FREE_FALL_MS = 100;
    private static final long  SETTLE_MS = 400;        // let the bounce settle after impact
    private static final long  STILLNESS_REQUIRED_MS = 1500; // must stay still this long = a fall
    private static final long  POST_FALL_COOLDOWN_MS = 5000; // ignore motion for 5s after a fall

    // --- Fusion ---
    private static final float ROTATION_MIN_RAD_S = 3.0f;
    private static final float POSTURE_CHANGE_MIN_DEG = 60f;
    private static final long  ROTATION_LEAD_MS = 200;     // look this far before free-fall starts
    private static final long  PRE_FALL_WINDOW_MS = 500;   // gravity is averaged over this long before free-fall
    private static final long  GYRO_RETENTION_MS =
            ROTATION_LEAD_MS + FREE_FALL_TO_IMPACT_MAX_MS + STILLNESS_REQUIRED_MS + 1000;

    private enum State { WAITING, FREE_FALL, IMPACT }

    /** One 3-axis reading with its timestamp. */
    private static final class Sample {
        final float x, y, z;
        final long tMs;
        Sample(float x, float y, float z, long tMs) { this.x = x; this.y = y; this.z = z; this.tMs = tMs; }
    }

    private final Listener listener;
    private final boolean gyroAvailable;

    private State state = State.WAITING;
    private long freeFallTime = 0;
    private long impactTime = 0;
    private boolean hasFallen = false;
    private long lastFallTime = 0;

    // Recent history. Both are pruned by timestamp on every insert.
    private final ArrayDeque<Sample> recentAccel = new ArrayDeque<>(); // last PRE_FALL_WINDOW_MS
    private final ArrayDeque<Sample> recentGyro = new ArrayDeque<>();  // last GYRO_RETENTION_MS

    // Snapshot of the gravity vector just before free-fall, taken on entering FREE_FALL.
    // The ring above has moved on by the time stillness is confirmed, so it must be stored.
    private float[] preFallGravity = null;

    // Running sum of the accelerometer during the stillness window.
    private float stillSumX, stillSumY, stillSumZ;
    private int stillCount;

    public FallDetector(Listener listener, boolean gyroAvailable) {
        this.listener = listener;
        this.gyroAvailable = gyroAvailable;
    }

    /** Called for every accelerometer reading (m/s²) with its timestamp in ms. */
    public void processAccel(float x, float y, float z, long tMs) {
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        // Cool-down after a detected fall. History keeps filling so the next fall has a fresh "before".
        if (hasFallen && tMs - lastFallTime < POST_FALL_COOLDOWN_MS) {
            remember(x, y, z, tMs);
            return;
        }

        switch (state) {

            case WAITING:
                // A fall begins with free-fall (near weightless)
                if (magnitude < FREE_FALL_MAX) {
                    preFallGravity = average(recentAccel);
                    state = State.FREE_FALL;
                    freeFallTime = tMs;
                    listener.onStateChanged("free-fall detected...");
                }
                break;

            case FREE_FALL:
                if (magnitude > IMPACT_MIN) {
                    if (tMs - freeFallTime >= MIN_FREE_FALL_MS) {
                        // genuinely weightless long enough, THEN impact
                        state = State.IMPACT;
                        impactTime = tMs;
                        stillSumX = 0f; stillSumY = 0f; stillSumZ = 0f;
                        stillCount = 0;
                        listener.onStateChanged("impact! checking if still...");
                    } else {
                        // impact came too fast after the dip = a bump / set-down, not a fall
                        reset("bump, not a fall (free-fall too short)");
                    }
                } else if (tMs - freeFallTime > FREE_FALL_TO_IMPACT_MAX_MS) {
                    reset("not a fall (no impact)");
                }
                break;

            case IMPACT:
                long sinceImpact = tMs - impactTime;
                if (sinceImpact < SETTLE_MS) {
                    // ignore the bounce right after impact
                } else if (Math.abs(magnitude - GRAVITY) > STILL_TOLERANCE) {
                    // still moving a lot after impact = person recovering / picked phone up
                    reset("movement after impact (recovery)");
                } else {
                    stillSumX += x; stillSumY += y; stillSumZ += z;
                    stillCount++;
                    if (sinceImpact >= STILLNESS_REQUIRED_MS) {
                        decide(tMs);
                    }
                }
                break;
        }

        remember(x, y, z, tMs);
    }

    /** Called for every gyroscope reading (rad/s per axis) with its timestamp in ms. */
    public void processGyro(float wx, float wy, float wz, long tMs) {
        if (!gyroAvailable) return;
        recentGyro.addLast(new Sample(wx, wy, wz, tMs));
        prune(recentGyro, tMs - GYRO_RETENTION_MS);
    }

    // ---------- fusion ----------

    /** The accelerometer sequence is complete: fuse it with rotation and posture. */
    private void decide(long tMs) {
        FusionResult r = evaluate();
        if (r.rotationPassed || r.posturePassed) {
            hasFallen = true;
            lastFallTime = tMs;
            listener.onFallDetected(r);
            reset("ready");
        } else {
            // Drop back to WAITING without calling reset(): the rejection must stay the last message.
            state = State.WAITING;
            String gyro = gyroAvailable
                    ? String.format(Locale.US, "rotation %.1f rad/s", r.peakRotation)
                    : "gyro n/a";
            listener.onStateChanged(String.format(Locale.US,
                    "fusion reject: impact without rotation or posture change (%s, posture %.0f°)",
                    gyro, r.postureChangeDeg));
        }
    }

    private FusionResult evaluate() {
        // Rotation: peak angular speed from just before free-fall until the bounce settles.
        long from = freeFallTime - ROTATION_LEAD_MS;
        long to = impactTime + SETTLE_MS;
        float peak = 0f;
        for (Sample g : recentGyro) {
            if (g.tMs < from || g.tMs > to) continue;
            float speed = (float) Math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z);
            if (speed > peak) peak = speed;
        }
        boolean rotationPassed = gyroAvailable && peak >= ROTATION_MIN_RAD_S;

        // Posture: how far gravity moved between "before the fall" and "lying still".
        float postureDeg = 0f;
        if (preFallGravity != null && stillCount > 0) {
            float[] still = { stillSumX / stillCount, stillSumY / stillCount, stillSumZ / stillCount };
            postureDeg = angleDeg(preFallGravity, still);
        }
        boolean posturePassed = postureDeg >= POSTURE_CHANGE_MIN_DEG;

        return new FusionResult(peak, postureDeg, rotationPassed, posturePassed, gyroAvailable);
    }

    /** Angle in degrees between two vectors; 0 if either has no length. */
    private static float angleDeg(float[] a, float[] b) {
        double magA = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        double magB = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        if (magA == 0 || magB == 0) return 0f;
        double cos = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (magA * magB);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    // ---------- helpers ----------

    private void remember(float x, float y, float z, long tMs) {
        recentAccel.addLast(new Sample(x, y, z, tMs));
        prune(recentAccel, tMs - PRE_FALL_WINDOW_MS);
    }

    private static void prune(ArrayDeque<Sample> samples, long cutoffMs) {
        while (!samples.isEmpty() && samples.peekFirst().tMs < cutoffMs) {
            samples.pollFirst();
        }
    }

    /** Mean vector of the samples, or null when there are none. */
    private static float[] average(ArrayDeque<Sample> samples) {
        if (samples.isEmpty()) return null;
        float sx = 0f, sy = 0f, sz = 0f;
        for (Sample s : samples) { sx += s.x; sy += s.y; sz += s.z; }
        int n = samples.size();
        return new float[] { sx / n, sy / n, sz / n };
    }

    private void reset(String why) {
        state = State.WAITING;
        listener.onStateChanged(why);
    }
}
