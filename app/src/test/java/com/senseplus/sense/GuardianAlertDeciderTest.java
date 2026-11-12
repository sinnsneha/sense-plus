package com.senseplus.sense;

import org.junit.Test;

import static com.senseplus.sense.ConnectivityStatus.State;
import static com.senseplus.sense.GuardianAlertDecider.Alert;
import static com.senseplus.sense.GuardianAlertDecider.Contact;
import static org.junit.Assert.assertEquals;

public class GuardianAlertDeciderTest {

    private static final String NORMAL = "NORMAL";
    private static final String ATTENTION = "ATTENTION";
    private static final String EMERGENCY = "POSSIBLE EMERGENCY";

    // ---------- status transitions ----------

    @Test
    public void normalToAttentionAlertsAttention() {
        assertEquals(Alert.ATTENTION, GuardianAlertDecider.decide(NORMAL, ATTENTION));
    }

    @Test
    public void attentionToEmergencyAlertsEmergency() {
        assertEquals(Alert.EMERGENCY, GuardianAlertDecider.decide(ATTENTION, EMERGENCY));
    }

    @Test
    public void normalStraightToEmergencyAlertsEmergency() {
        // manual HELP, voice help or codeword skips the 15 s window
        assertEquals(Alert.EMERGENCY, GuardianAlertDecider.decide(NORMAL, EMERGENCY));
    }

    @Test
    public void emergencyToNormalIsAllClear() {
        assertEquals(Alert.ALL_CLEAR, GuardianAlertDecider.decide(EMERGENCY, NORMAL));
    }

    @Test
    public void attentionToNormalIsFalseAlarmResolved() {
        assertEquals(Alert.FALSE_ALARM_RESOLVED, GuardianAlertDecider.decide(ATTENTION, NORMAL));
    }

    @Test
    public void unchangedStatusAlertsNothing() {
        assertEquals(Alert.NONE, GuardianAlertDecider.decide(NORMAL, NORMAL));
        assertEquals(Alert.NONE, GuardianAlertDecider.decide(ATTENTION, ATTENTION));
        assertEquals(Alert.NONE, GuardianAlertDecider.decide(EMERGENCY, EMERGENCY));
    }

    @Test
    public void firstPollNormalAlertsNothing() {
        assertEquals(Alert.NONE, GuardianAlertDecider.decide(null, NORMAL));
    }

    @Test
    public void firstPollWithEmergencyAlreadyRunningAlerts() {
        assertEquals(Alert.EMERGENCY, GuardianAlertDecider.decide(null, EMERGENCY));
    }

    @Test
    public void firstPollWithAttentionAlreadyRunningAlerts() {
        assertEquals(Alert.ATTENTION, GuardianAlertDecider.decide(null, ATTENTION));
    }

    @Test
    public void missingNewStatusAlertsNothing() {
        assertEquals(Alert.NONE, GuardianAlertDecider.decide(EMERGENCY, null));
        assertEquals(Alert.NONE, GuardianAlertDecider.decide(null, null));
    }

    @Test
    public void emergencyThenNewAttentionAnnouncesTheAttention() {
        // a poll missed the NORMAL in between
        assertEquals(Alert.ATTENTION, GuardianAlertDecider.decide(EMERGENCY, ATTENTION));
    }

    @Test
    public void unrecognisedStatusCountsAsNormal() {
        assertEquals(Alert.ALL_CLEAR, GuardianAlertDecider.decide(EMERGENCY, "UNKNOWN"));
        assertEquals(Alert.NONE, GuardianAlertDecider.decide(null, "UNKNOWN"));
    }

    // ---------- contact transitions ----------

    @Test
    public void onlineToOfflineIsLost() {
        assertEquals(Contact.LOST, GuardianAlertDecider.decideContact(State.ONLINE, State.OFFLINE));
        assertEquals(Contact.LOST, GuardianAlertDecider.decideContact(State.RECENT, State.OFFLINE));
    }

    @Test
    public void lostIsReportedOnlyOnce() {
        assertEquals(Contact.NONE, GuardianAlertDecider.decideContact(State.OFFLINE, State.OFFLINE));
    }

    @Test
    public void offlineOnFirstPollIsLost() {
        assertEquals(Contact.LOST, GuardianAlertDecider.decideContact(null, State.OFFLINE));
    }

    @Test
    public void onlineOnFirstPollIsSilent() {
        assertEquals(Contact.NONE, GuardianAlertDecider.decideContact(null, State.ONLINE));
    }

    @Test
    public void offlineToOnlineIsRestored() {
        assertEquals(Contact.RESTORED, GuardianAlertDecider.decideContact(State.OFFLINE, State.ONLINE));
        assertEquals(Contact.RESTORED, GuardianAlertDecider.decideContact(State.OFFLINE, State.RECENT));
    }

    @Test
    public void monitoringOffNeverNotifies() {
        for (State previous : new State[]{null, State.NEVER, State.ONLINE, State.RECENT,
                State.OFFLINE, State.MONITORING_OFF}) {
            assertEquals("from " + previous, Contact.NONE,
                    GuardianAlertDecider.decideContact(previous, State.MONITORING_OFF));
        }
    }

    @Test
    public void monitoringSwitchedBackOnAndThenSilentIsLostAgain() {
        // off on purpose (silent) -> on again but the data goes stale: that is a real loss
        assertEquals(Contact.LOST,
                GuardianAlertDecider.decideContact(State.MONITORING_OFF, State.OFFLINE));
    }

    @Test
    public void ordinaryDriftBetweenOnlineAndRecentIsSilent() {
        assertEquals(Contact.NONE, GuardianAlertDecider.decideContact(State.ONLINE, State.RECENT));
        assertEquals(Contact.NONE, GuardianAlertDecider.decideContact(State.RECENT, State.ONLINE));
    }

    @Test
    public void stillOfflineAfterGoingMonitoringOffAndBackIsReportedAgain() {
        // OFFLINE -> MONITORING_OFF is silent, and a later OFFLINE is a new loss
        assertEquals(Contact.NONE,
                GuardianAlertDecider.decideContact(State.OFFLINE, State.MONITORING_OFF));
        assertEquals(Contact.LOST,
                GuardianAlertDecider.decideContact(State.MONITORING_OFF, State.OFFLINE));
    }

    @Test
    public void nullCurrentStateAlertsNothing() {
        assertEquals(Contact.NONE, GuardianAlertDecider.decideContact(State.OFFLINE, null));
    }
}
