package com.senseplus.sense;

public class FallDetector {

    // We tell MainActivity what's happening through these callbacks
    public interface Listener {
        void onFallDetected();
        void onStateChanged(String info);
    }

    // --- Tuning values (we can adjust these while testing) ---
    private static final float FREE_FALL_MAX = 4f;     // below this = weightless (falling)
    private static final float IMPACT_MIN    = 25f;    // above this = hard impact
    private static final float GRAVITY       = 9.8f;   // resting magnitude
    private static final float STILL_TOLERANCE = 4f;   // how far from gravity still counts as "still"
    private static final long  FREE_FALL_TO_IMPACT_MAX_MS = 1200;// impact must follow free-fall quickly
    private static final long  MIN_FREE_FALL_MS = 100;
    private static final long  SETTLE_MS = 400;        // let the bounce settle after impact
    private static final long  STILLNESS_REQUIRED_MS = 1500; // must stay still this long = a fall
    private static final long POST_FALL_COOLDOWN_MS = 5000; // ignore motion for 5s after a fall
    private long lastFallTime = 0;
    private enum State { WAITING, FREE_FALL, IMPACT }
    private State state = State.WAITING;

    private long freeFallTime = 0;
    private long impactTime = 0;

    private final Listener listener;

    public FallDetector(Listener listener) {
        this.listener = listener;
    }

    // Called for every accelerometer reading
    public void process(double magnitude) {
        long now = System.currentTimeMillis();

        // Just detected a fall? Ignore everything briefly while the phone is handled.
        if (now - lastFallTime < POST_FALL_COOLDOWN_MS) {
            return;
        }

        switch (state) {

            case WAITING:
                // A fall begins with free-fall (near weightless)
                if (magnitude < FREE_FALL_MAX) {
                    state = State.FREE_FALL;
                    freeFallTime = now;
                    listener.onStateChanged("free-fall detected...");
                }
                break;

            case FREE_FALL:
                if (magnitude > IMPACT_MIN) {
                    if (now - freeFallTime >= MIN_FREE_FALL_MS) {
                        // genuinely weightless long enough, THEN impact = real fall
                        state = State.IMPACT;
                        impactTime = now;
                        listener.onStateChanged("impact! checking if still...");
                    } else {
                        // impact came too fast after the dip = a bump / set-down, not a fall
                        reset("bump, not a fall (free-fall too short)");
                    }
                } else if (now - freeFallTime > FREE_FALL_TO_IMPACT_MAX_MS) {
                    reset("not a fall (no impact)");
                }
                break;

            case IMPACT:
                long sinceImpact = now - impactTime;
                if (sinceImpact < SETTLE_MS) {
                    // ignore the bounce right after impact
                } else if (Math.abs(magnitude - GRAVITY) > STILL_TOLERANCE) {
                    // still moving a lot after impact = person recovering / picked phone up
                    reset("movement after impact (recovery)");
                } else if (sinceImpact >= STILLNESS_REQUIRED_MS) {
                    // free-fall -> impact -> stillness = a real fall
                    lastFallTime = now;
                    listener.onFallDetected();
                    reset("ready");
                }
                break;
        }
    }

    private void reset(String why) {
        state = State.WAITING;
        listener.onStateChanged(why);
    }
}