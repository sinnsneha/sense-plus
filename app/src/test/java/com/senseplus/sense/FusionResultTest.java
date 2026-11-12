package com.senseplus.sense;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FusionResultTest {

    @Test
    public void summaryWhenBothPass() {
        FusionResult r = new FusionResult(4.2f, 78f, true, true, true);
        assertEquals("accel ✓ · gyro ✓ 4.2 rad/s · posture ✓ 78°", r.summary());
    }

    @Test
    public void gyroMarkFollowsRotationPassedNotAvailability() {
        FusionResult r = new FusionResult(0.4f, 85f, false, true, true);
        assertEquals("accel ✓ · gyro ✗ 0.4 rad/s · posture ✓ 85°", r.summary());
    }

    @Test
    public void summaryWithoutGyroscope() {
        FusionResult r = new FusionResult(0f, 70f, false, true, false);
        assertEquals("accel ✓ · gyro n/a · posture ✓ 70°", r.summary());
    }
}
