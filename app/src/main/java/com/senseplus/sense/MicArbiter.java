package com.senseplus.sense;

/**
 * Decides whether the voice keywords (the Vosk listener in UserMonitorService) may use the
 * microphone (segment 28). Pure Java with no Android imports.
 *
 * Hearing Assist needs the microphone for sound alerts and live captions, and two recorders
 * fighting over it was the old limitation ("stop User monitoring before using Hearing Assist"),
 * which also switched off fall detection. Now only the voice keywords step aside.
 *
 * Rule: the keywords listen <b>only when monitoring is on AND Hearing Assist is closed</b>.
 * The state is just two flags, so the order of events does not matter, and an event repeated
 * (a second "opened" from a screen that paused and resumed) changes nothing.
 */
public final class MicArbiter {

    private boolean monitoring = false;
    private boolean hearingOpen = false;

    /** Monitoring began. @return whether the voice keywords should now be listening */
    public boolean monitoringStarted() {
        monitoring = true;
        return shouldListen();
    }

    /** Monitoring ended on purpose. Hearing closing later must not turn the keywords back on. */
    public boolean monitoringStopped() {
        monitoring = false;
        return shouldListen();
    }

    /** Hearing Assist came to the front and wants the microphone. */
    public boolean hearingOpened() {
        hearingOpen = true;
        return shouldListen();
    }

    /** Hearing Assist left the front and released the microphone. */
    public boolean hearingClosed() {
        hearingOpen = false;
        return shouldListen();
    }

    /** True when the voice keywords should be listening right now. */
    public boolean shouldListen() {
        return monitoring && !hearingOpen;
    }

    /** True while Hearing Assist is holding the microphone (the keywords are paused for it). */
    public boolean isHearingOpen() {
        return hearingOpen;
    }
}
