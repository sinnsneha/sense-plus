package com.senseplus.sense;

/**
 * After a restart or an app update, who is told that protection is off, and with what words
 * (segment 28). Pure Java with no Android imports; BootReceiver only gathers the facts, asks
 * here, and posts the notification.
 *
 * Nothing is started silently: from Android 15 a BOOT_COMPLETED receiver may not start a
 * dataSync or microphone foreground service (and from Android 14 not a microphone one), and a
 * location one needs a visible app. So the person is reminded, and tapping the reminder opens
 * the app, which may start the service because it is then in the foreground.
 */
public final class BootReminderRule {

    public enum Reminder {
        NONE,
        USER,      // "protection is off": tapping opens the Safety screen and starts monitoring
        GUARDIAN   // "resume guardian alerts": tapping opens Home, which starts GuardianService
    }

    private BootReminderRule() {
    }

    /**
     * @param signedIn          a real (not anonymous) account is signed in with a known role
     * @param role              {@link Roles#USER} or {@link Roles#GUARDIAN}
     * @param monitoringWanted  the user turned monitoring on and has not turned it off since
     * @param guardianLinked    this guardian is linked to a person
     *
     * Nobody who is signed out, nobody who did not ask for protection, and no unlinked guardian
     * gets a notification: a reminder for something the person never wanted is just noise.
     */
    public static Reminder decide(boolean signedIn, String role, boolean monitoringWanted,
                                  boolean guardianLinked) {
        if (!signedIn) return Reminder.NONE;
        if (Roles.USER.equals(role)) return monitoringWanted ? Reminder.USER : Reminder.NONE;
        if (Roles.GUARDIAN.equals(role)) return guardianLinked ? Reminder.GUARDIAN : Reminder.NONE;
        return Reminder.NONE;
    }

    public static String title(Reminder reminder) {
        return reminder == Reminder.GUARDIAN ? "Guardian alerts are off" : "SENSE+ protection is off";
    }

    public static String text(Reminder reminder) {
        switch (reminder) {
            case USER:
                return "SENSE+ protection is off after a restart. Tap to turn it back on.";
            case GUARDIAN:
                return "Tap to resume guardian alerts";
            case NONE:
            default:
                return "";
        }
    }
}
