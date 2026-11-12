package com.senseplus.sense;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure mapping from the account role and the assistance modes a user chose to the
 * Home-screen cards that should be visible. No Android imports, so it is unit-tested.
 *
 * A User never sees the guardian card; a Guardian sees only the guardian card and
 * Sensor Info. Showing everything for a user with no saved profile is the safe default,
 * so a user is never locked out of a tool.
 */
public class ModeMapper {

    // Mode keys as stored in SharedPreferences ("modes" string set).
    public static final String MODE_VISUAL = "visual";
    public static final String MODE_HEARING = "hearing";
    public static final String MODE_COMMUNICATION = "communication";
    public static final String MODE_MOBILITY = "mobility";
    public static final String MODE_COGNITIVE = "cognitive";

    // Card keys. HomeActivity maps each one to its card view.
    public static final String CARD_USER = "user";
    public static final String CARD_GUARDIAN = "guardian";
    public static final String CARD_COMMUNICATION = "communication";
    public static final String CARD_VISUAL = "visual";
    public static final String CARD_HEARING = "hearing";
    public static final String CARD_NAVIGATION = "navigation";
    public static final String CARD_SENSOR = "sensor";

    /** Safety and Sensor Info are never hidden from a User, whatever the profile says. */
    private static final Set<String> USER_ALWAYS = new LinkedHashSet<>(
            Arrays.asList(CARD_USER, CARD_SENSOR));

    /** A Guardian only needs the dashboard and the sensor screen. */
    private static final Set<String> GUARDIAN_CARDS = new LinkedHashSet<>(
            Arrays.asList(CARD_GUARDIAN, CARD_SENSOR));

    private ModeMapper() { }

    /** @return every card a User can see, in Home order, when the profile says "no preference". */
    public static Set<String> allCards() {
        // The guardian card is deliberately absent: that tool belongs to the guardian role.
        return new LinkedHashSet<>(Arrays.asList(
                CARD_USER, CARD_COMMUNICATION, CARD_VISUAL,
                CARD_HEARING, CARD_NAVIGATION, CARD_SENSOR));
    }

    /** @return every card a Guardian can see. */
    public static Set<String> guardianCards() {
        return new LinkedHashSet<>(GUARDIAN_CARDS);
    }

    /**
     * Old entry point, kept so the existing Home code and tests keep working.
     * It is exactly {@code visibleCards(Roles.USER, modes)}.
     */
    public static Set<String> visibleCards(Set<String> modes) {
        return visibleCards(Roles.USER, modes);
    }

    /**
     * @param role  {@link Roles#USER} or {@link Roles#GUARDIAN}. Anything else is treated as
     *              user so an unread role can never hide the safety tools.
     * @param modes the chosen modes; null, empty, or containing no recognised mode means
     *              "no preference", so every card for that role is shown
     * @return the cards to show. A new set the caller may modify; the input is not changed.
     */
    public static Set<String> visibleCards(String role, Set<String> modes) {
        if (Roles.GUARDIAN.equals(role)) return guardianCards();

        Set<String> cards = new LinkedHashSet<>(USER_ALWAYS);
        boolean anyRecognised = false;

        if (modes != null) {
            for (String mode : modes) {
                if (mode == null) continue;
                switch (mode) {
                    case MODE_VISUAL:
                        cards.add(CARD_VISUAL);
                        cards.add(CARD_NAVIGATION);
                        anyRecognised = true;
                        break;
                    case MODE_HEARING:
                        cards.add(CARD_HEARING);
                        anyRecognised = true;
                        break;
                    case MODE_COMMUNICATION:
                        cards.add(CARD_COMMUNICATION);
                        anyRecognised = true;
                        break;
                    case MODE_MOBILITY:
                        cards.add(CARD_NAVIGATION);
                        anyRecognised = true;
                        break;
                    case MODE_COGNITIVE:
                        cards.add(CARD_COMMUNICATION);
                        cards.add(CARD_NAVIGATION);
                        anyRecognised = true;
                        break;
                    default:
                        break; // unknown key (e.g. from a future version): ignore it
                }
            }
        }
        // Showing everything (except the guardian card) is the safe default for a user.
        return anyRecognised ? cards : allCards();
    }
}
