package com.senseplus.sense;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModeMapperTest {

    private static final String[] ALL_MODES = {
            ModeMapper.MODE_VISUAL, ModeMapper.MODE_HEARING, ModeMapper.MODE_COMMUNICATION,
            ModeMapper.MODE_MOBILITY, ModeMapper.MODE_COGNITIVE};

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    @Test
    public void emptySetShowsAllCards() {
        assertEquals(ModeMapper.allCards(), ModeMapper.visibleCards(Collections.<String>emptySet()));
    }

    @Test
    public void nullShowsAllCards() {
        assertEquals(ModeMapper.allCards(), ModeMapper.visibleCards(null));
    }

    @Test
    public void onlyUnknownModesShowAllCards() {
        assertEquals(ModeMapper.allCards(), ModeMapper.visibleCards(set("telepathy")));
    }

    @Test
    public void unknownModeIsIgnoredNextToKnownOne() {
        assertEquals(set("user", "sensor", "guardians", "medical", "hearing"),
                ModeMapper.visibleCards(set("hearing", "telepathy")));
    }

    @Test
    public void hearingOnlyAddsHearingToTheAlwaysShownCards() {
        assertEquals(set("user", "sensor", "guardians", "medical", "hearing"),
                ModeMapper.visibleCards(set(ModeMapper.MODE_HEARING)));
    }

    @Test
    public void visualAddsVisualAndNavigation() {
        assertEquals(set("user", "sensor", "guardians", "medical", "visual", "navigation"),
                ModeMapper.visibleCards(set(ModeMapper.MODE_VISUAL)));
    }

    @Test
    public void communicationAddsCommunicationOnly() {
        assertEquals(set("user", "sensor", "guardians", "medical", "communication"),
                ModeMapper.visibleCards(set(ModeMapper.MODE_COMMUNICATION)));
    }

    @Test
    public void mobilityAddsNavigationOnly() {
        assertEquals(set("user", "sensor", "guardians", "medical", "navigation"),
                ModeMapper.visibleCards(set(ModeMapper.MODE_MOBILITY)));
    }

    @Test
    public void cognitiveAddsCommunicationAndNavigation() {
        assertEquals(set("user", "sensor", "guardians", "medical", "communication", "navigation"),
                ModeMapper.visibleCards(set(ModeMapper.MODE_COGNITIVE)));
    }

    @Test
    public void visualAndCognitiveGiveUnionWithoutDuplicates() {
        Set<String> cards = ModeMapper.visibleCards(
                set(ModeMapper.MODE_VISUAL, ModeMapper.MODE_COGNITIVE));
        // navigation comes from both modes but must appear once; hearing stays hidden
        assertEquals(set("user", "sensor", "guardians", "medical", "visual", "communication", "navigation"), cards);
        assertEquals(7, cards.size());
        assertFalse(cards.contains("hearing"));
    }

    @Test
    public void allModesShowAllCards() {
        assertEquals(ModeMapper.allCards(), ModeMapper.visibleCards(set(ALL_MODES)));
    }

    @Test
    public void alwaysShownCardsArePresentForEverySubsetOfModes() {
        for (int mask = 0; mask < (1 << ALL_MODES.length); mask++) {
            Set<String> modes = new HashSet<>();
            for (int i = 0; i < ALL_MODES.length; i++) {
                if ((mask & (1 << i)) != 0) modes.add(ALL_MODES[i]);
            }
            Set<String> cards = ModeMapper.visibleCards(modes);
            assertTrue("user hidden for " + modes, cards.contains(ModeMapper.CARD_USER));
            assertTrue("sensor hidden for " + modes, cards.contains(ModeMapper.CARD_SENSOR));
            assertFalse("guardian shown to a user for " + modes,
                    cards.contains(ModeMapper.CARD_GUARDIAN));
            assertTrue("My guardians hidden for " + modes, cards.contains(ModeMapper.CARD_GUARDIANS));
            assertFalse("link shown to a user for " + modes, cards.contains(ModeMapper.CARD_LINK));
        }
    }

    @Test
    public void inputSetIsNotModified() {
        Set<String> modes = set(ModeMapper.MODE_HEARING);
        ModeMapper.visibleCards(modes);
        assertEquals(set(ModeMapper.MODE_HEARING), modes);
    }

    // ---------- role-aware mapping (segment 22) ----------

    @Test
    public void userRoleNeverContainsTheGuardianCard() {
        for (int mask = 0; mask < (1 << ALL_MODES.length); mask++) {
            Set<String> modes = new HashSet<>();
            for (int i = 0; i < ALL_MODES.length; i++) {
                if ((mask & (1 << i)) != 0) modes.add(ALL_MODES[i]);
            }
            Set<String> cards = ModeMapper.visibleCards(Roles.USER, modes);
            assertFalse("guardian shown to a user for " + modes,
                    cards.contains(ModeMapper.CARD_GUARDIAN));
        }
    }

    @Test
    public void guardianRoleIsExactlyGuardianSensorAndLink() {
        assertEquals(set("guardian", "sensor", "link"),
                ModeMapper.visibleCards(Roles.GUARDIAN, Collections.<String>emptySet()));
        assertEquals(set("guardian", "sensor", "link"),
                ModeMapper.visibleCards(Roles.GUARDIAN, set(ALL_MODES)));
    }

    @Test
    public void userWithNoModesSeesAllCardsExceptGuardian() {
        Set<String> cards = ModeMapper.visibleCards(Roles.USER, Collections.<String>emptySet());
        assertEquals(set("user", "communication", "visual", "hearing", "navigation", "sensor", "guardians", "medical"),
                cards);
        assertFalse(cards.contains(ModeMapper.CARD_GUARDIAN));
    }

    @Test
    public void oneArgumentMethodEqualsTheUserRole() {
        Set<Set<String>> inputs = new HashSet<>();
        inputs.add(Collections.<String>emptySet());
        inputs.add(set(ModeMapper.MODE_VISUAL));
        inputs.add(set(ModeMapper.MODE_HEARING, ModeMapper.MODE_MOBILITY));
        inputs.add(set(ALL_MODES));
        for (Set<String> modes : inputs) {
            assertEquals(ModeMapper.visibleCards(Roles.USER, modes), ModeMapper.visibleCards(modes));
        }
    }

    // ---------- pairing tiles (segment 23) ----------

    @Test
    public void guardianNeverSeesMyGuardiansTile() {
        assertFalse(ModeMapper.visibleCards(Roles.GUARDIAN, set(ALL_MODES))
                .contains(ModeMapper.CARD_GUARDIANS));
    }

    @Test
    public void userAlwaysSeesMyGuardiansButNeverLink() {
        Set<String> cards = ModeMapper.visibleCards(Roles.USER, set(ModeMapper.MODE_HEARING));
        assertTrue(cards.contains(ModeMapper.CARD_GUARDIANS));
        assertFalse(cards.contains(ModeMapper.CARD_LINK));
    }

    // ---------- Medical ID card (segment 26) ----------

    @Test
    public void medicalCardIsTheCardKeyMedical() {
        assertEquals("medical", ModeMapper.CARD_MEDICAL);
    }

    @Test
    public void userAlwaysSeesTheMedicalCardWhateverTheModes() {
        for (int mask = 0; mask < (1 << ALL_MODES.length); mask++) {
            Set<String> modes = new HashSet<>();
            for (int i = 0; i < ALL_MODES.length; i++) {
                if ((mask & (1 << i)) != 0) modes.add(ALL_MODES[i]);
            }
            assertTrue("medical hidden from a user for " + modes,
                    ModeMapper.visibleCards(Roles.USER, modes).contains(ModeMapper.CARD_MEDICAL));
        }
        assertTrue(ModeMapper.visibleCards(Roles.USER, Collections.<String>emptySet())
                .contains(ModeMapper.CARD_MEDICAL));
        assertTrue(ModeMapper.visibleCards((Set<String>) null).contains(ModeMapper.CARD_MEDICAL));
        assertTrue(ModeMapper.allCards().contains(ModeMapper.CARD_MEDICAL));
    }

    @Test
    public void guardianNeverSeesTheMedicalCard() {
        assertFalse(ModeMapper.visibleCards(Roles.GUARDIAN, Collections.<String>emptySet())
                .contains(ModeMapper.CARD_MEDICAL));
        assertFalse(ModeMapper.visibleCards(Roles.GUARDIAN, set(ALL_MODES))
                .contains(ModeMapper.CARD_MEDICAL));
        assertFalse(ModeMapper.guardianCards().contains(ModeMapper.CARD_MEDICAL));
    }

    @Test
    public void anUnreadRoleIsTreatedAsUserSoItStillGetsTheMedicalCard() {
        assertTrue(ModeMapper.visibleCards("someday", Collections.<String>emptySet())
                .contains(ModeMapper.CARD_MEDICAL));
    }
}
