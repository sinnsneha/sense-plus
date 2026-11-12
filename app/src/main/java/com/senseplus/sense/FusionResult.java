package com.senseplus.sense;

import java.util.Locale;

/**
 * Outcome of the sensor-fusion check run when the accelerometer sequence
 * (free-fall, impact, stillness) completes. Immutable and free of Android
 * imports, so it can be unit tested on the JVM.
 */
public class FusionResult {
    public final float peakRotation;      // peak angular speed around the fall, rad/s
    public final float postureChangeDeg;  // angle between gravity before and after, degrees
    public final boolean rotationPassed;
    public final boolean posturePassed;
    public final boolean gyroAvailable;

    public FusionResult(float peakRotation, float postureChangeDeg,
                        boolean rotationPassed, boolean posturePassed,
                        boolean gyroAvailable) {
        this.peakRotation = peakRotation;
        this.postureChangeDeg = postureChangeDeg;
        this.rotationPassed = rotationPassed;
        this.posturePassed = posturePassed;
        this.gyroAvailable = gyroAvailable;
    }

    /** e.g. "accel ✓ · gyro ✓ 4.2 rad/s · posture ✓ 78°" (gyro reads "gyro n/a" without a gyroscope). */
    public String summary() {
        String gyro = gyroAvailable
                ? String.format(Locale.US, "gyro %s %.1f rad/s", mark(rotationPassed), peakRotation)
                : "gyro n/a";
        String posture = String.format(Locale.US, "posture %s %.0f°", mark(posturePassed), postureChangeDeg);
        return "accel ✓ · " + gyro + " · " + posture;
    }

    private static String mark(boolean passed) {
        return passed ? "✓" : "✗";
    }
}
