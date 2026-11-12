package com.senseplus.sense;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PhoneRulesTest {

    // ---------- normalize ----------

    @Test
    public void keepsALeadingPlusAndDropsSpaces() {
        assertEquals("+919876543210", PhoneRules.normalize("+91 98765 43210"));
    }

    @Test
    public void dropsDashes() {
        assertEquals("9876543210", PhoneRules.normalize("98-765-43210"));
    }

    @Test
    public void dropsBracketsAndDotsAndKeepsThePlusInsideBrackets() {
        assertEquals("+919876543210", PhoneRules.normalize("(+91) 98765.43210"));
    }

    @Test
    public void aPlusInTheMiddleIsDropped() {
        assertEquals("+4412345678", PhoneRules.normalize("+44 1234+5678"));
    }

    @Test
    public void tooFewDigitsIsNull() {
        assertNull(PhoneRules.normalize("12"));
        assertNull(PhoneRules.normalize("123456"));      // 6 digits
        assertNull(PhoneRules.normalize("+1 23 45 6"));  // a '+' is not a digit
    }

    @Test
    public void sevenDigitsIsTheSmallestAccepted() {
        assertEquals("1234567", PhoneRules.normalize("123-4567"));
    }

    @Test
    public void tooManyDigitsIsNull() {
        assertNull(PhoneRules.normalize("1234567890123456")); // 16
        assertEquals("123456789012345", PhoneRules.normalize("123456789012345")); // 15 is fine
    }

    @Test
    public void nullAndBlankAreNull() {
        assertNull(PhoneRules.normalize(null));
        assertNull(PhoneRules.normalize(""));
        assertNull(PhoneRules.normalize("   "));
        assertNull(PhoneRules.normalize("n/a"));
    }

    // ---------- callNumber ----------

    @Test
    public void callsTheFirstGuardianWithAPhone() {
        assertEquals("9876543210", PhoneRules.callNumber(
                Arrays.asList("", "98765 43210", "+44 20 7946 0958"), "55555 55555"));
    }

    @Test
    public void skipsGuardiansWhoseNumberIsUnusable() {
        assertEquals("+442079460958", PhoneRules.callNumber(
                Arrays.asList(null, "12", "+44 20 7946 0958"), null));
    }

    @Test
    public void fallsBackToTheEmergencyContact() {
        assertEquals("5555555555", PhoneRules.callNumber(Collections.<String>emptyList(), "555-555-5555"));
        assertEquals("5555555555", PhoneRules.callNumber(null, "555-555-5555"));
    }

    @Test
    public void noNumberAtAllIsNull() {
        assertNull(PhoneRules.callNumber(null, null));
        assertNull(PhoneRules.callNumber(Arrays.asList("x", "1"), "  "));
    }

    // ---------- smsRecipients ----------

    @Test
    public void everyGuardianThenTheEmergencyContact() {
        assertEquals(Arrays.asList("9876543210", "+442079460958", "5555555555"),
                PhoneRules.smsRecipients(Arrays.asList("98765 43210", "+44 20 7946 0958"), "555 555 5555"));
    }

    @Test
    public void theSameNumberIsTextedOnce() {
        assertEquals(Arrays.asList("9876543210"),
                PhoneRules.smsRecipients(Arrays.asList("98765-43210", "9876543210"), "98765 43210"));
    }

    @Test
    public void theSameLineWithAndWithoutACountryCodeIsOnePerson() {
        assertEquals(Arrays.asList("+919876543210"),
                PhoneRules.smsRecipients(Arrays.asList("+91 98765 43210"), "98765 43210"));
        assertEquals(1, PhoneRules.smsRecipients(Arrays.asList("98765 43210"), "+91 98765 43210").size());
    }

    @Test
    public void unusableNumbersAreNeverTexted() {
        assertTrue(PhoneRules.smsRecipients(Arrays.asList("", "12", null), "abc").isEmpty());
        assertTrue(PhoneRules.smsRecipients(null, null).isEmpty());
    }

    @Test
    public void differentNumbersAreNotMerged() {
        assertEquals(2, PhoneRules.smsRecipients(Arrays.asList("9876543210", "9876543211"), null).size());
        assertFalse(PhoneRules.sameNumber("9876543210", "9876543211"));
    }

    // ---------- guardianPhonesInOrder ----------

    private static Map<String, Object> entry(String name, String phone) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("phone", phone);
        return m;
    }

    @Test
    public void readsGuardianPhonesInTheOrderOfTheGuardianList() {
        Map<String, Object> info = new HashMap<>();
        info.put("g2", entry("Ravi", "+91 98765 43210"));
        info.put("g1", entry("Maya", "555 555 5555"));
        assertEquals(Arrays.asList("5555555555", "+919876543210"),
                PhoneRules.guardianPhonesInOrder(Arrays.asList("g1", "g2"), info));
    }

    @Test
    public void guardiansWithoutAUsablePhoneOrEntryAreSkipped() {
        Map<String, Object> info = new HashMap<>();
        info.put("g1", entry("Maya", ""));
        info.put("g3", entry("Ravi", "555 555 5555"));
        info.put("g4", "not a map");
        assertEquals(Arrays.asList("5555555555"),
                PhoneRules.guardianPhonesInOrder(Arrays.asList("g1", "g2", "g3", "g4"), info));
    }

    @Test
    public void missingGuardianDataGivesAnEmptyList() {
        assertTrue(PhoneRules.guardianPhonesInOrder(null, null).isEmpty());
        assertTrue(PhoneRules.guardianPhonesInOrder(Arrays.asList("g1"), null).isEmpty());
    }
}
