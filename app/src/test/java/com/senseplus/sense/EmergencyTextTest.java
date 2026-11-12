package com.senseplus.sense;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EmergencyTextTest {

    private static final String LINK = "https://www.google.com/maps?q=28.6139,77.209";

    // ---------- sms ----------

    @Test
    public void smsHasTheNameTheReasonAndTheLink() {
        String sms = EmergencyText.sms("Maya", "No response after fall", LINK, 12.4);
        assertEquals("SENSE+: possible emergency for Maya (No response after fall). "
                + "Location: " + LINK + " (+/-12 m). Sent automatically.", sms);
        assertTrue(sms.contains("Maya"));
        assertTrue(sms.contains("No response after fall"));
        assertTrue(sms.contains(LINK));
    }

    @Test
    public void smsWithoutALinkSaysTheLocationIsUnknown() {
        String sms = EmergencyText.sms("Maya", "Manual HELP pressed", null, 12);
        assertTrue(sms, sms.contains("location unknown"));
        assertFalse(sms.contains("http"));
        assertFalse(sms.contains("+/-"));
        assertTrue(EmergencyText.sms("Maya", "x", "  ", 0).contains("location unknown"));
    }

    @Test
    public void smsLeavesOutAnUnknownAccuracy() {
        assertEquals("SENSE+: possible emergency for Maya (x). Location: " + LINK + ". Sent automatically.",
                EmergencyText.sms("Maya", "x", LINK, 0));
        assertFalse(EmergencyText.sms("Maya", "x", LINK, Double.NaN).contains("+/-"));
        assertFalse(EmergencyText.sms("Maya", "x", LINK, -5).contains("+/-"));
    }

    @Test
    public void smsRoundsTheAccuracyToWholeMetres() {
        assertTrue(EmergencyText.sms("Maya", "x", LINK, 7.6).contains("(+/-8 m)"));
    }

    @Test
    public void smsFallsBackWhenTheNameOrReasonIsMissing() {
        assertEquals("SENSE+: possible emergency for the user. Location: " + LINK + ". Sent automatically.",
                EmergencyText.sms(null, "", LINK, 0));
        assertTrue(EmergencyText.sms("  ", "x", LINK, 0).contains("for the user (x)"));
    }

    @Test
    public void aTypicalSmsIsPlainAsciiAndFitsOneSegment() {
        // One non-GSM character (such as the plus-minus sign) would make it UCS-2: 70 per part.
        String sms = EmergencyText.sms("Maya", "No response after fall",
                EmergencyText.mapsLink(28.613912345678, 77.209012345678), 12.4);
        for (int i = 0; i < sms.length(); i++) {
            assertTrue("non-ASCII character at " + i, sms.charAt(i) < 128);
        }
        assertTrue("length " + sms.length(), sms.length() <= 160);
    }

    @Test
    public void mapsLinkIsShortAndUsesADotWhateverTheLocale() {
        assertEquals("https://www.google.com/maps?q=28.61391,77.20901",
                EmergencyText.mapsLink(28.613912345678, 77.209012345678));
        assertEquals("https://www.google.com/maps?q=-33.86882,151.20929",
                EmergencyText.mapsLink(-33.868820, 151.209290));
    }

    @Test
    public void smsSaysPossibleEmergencyNeverJustEmergency() {
        assertTrue(EmergencyText.sms("Maya", "x", LINK, 0).contains("possible emergency"));
    }

    // ---------- spoken Medical ID ----------

    private static MedicalId id(String name, String age, String blood, String allergies,
                                String conditions, String medications, String notes,
                                String contactName, String contactPhone) {
        return new MedicalId(name, age, blood, allergies, conditions, medications, notes,
                contactName, contactPhone);
    }

    @Test
    public void spokenMedicalIdReadsEveryFilledField() {
        MedicalId m = id("Maya", "34", "A+", "penicillin", "asthma", "inhaler", "wears hearing aids",
                "Ravi", "+91 98765 43210");
        assertEquals("Medical ID for Maya. Age 34. Blood group A positive. Allergies: penicillin. "
                + "Medical conditions: asthma. Medications: inhaler. Notes: wears hearing aids. "
                + "Emergency contact: Ravi, phone plus 9 1 9 8 7 6 5 4 3 2 1 0.",
                EmergencyText.spokenMedicalId(m));
    }

    @Test
    public void spokenMedicalIdSkipsEmptyFields() {
        String spoken = EmergencyText.spokenMedicalId(
                id("Maya", "", "Unknown", "", "asthma", "  ", "", "", ""));
        assertEquals("Medical ID for Maya. Medical conditions: asthma.", spoken);
        assertFalse(spoken.contains("Age"));
        assertFalse(spoken.contains("Blood"));
        assertFalse(spoken.contains("Allergies"));
        assertFalse(spoken.contains("Medications"));
        assertFalse(spoken.contains("Emergency contact"));
    }

    @Test
    public void spokenMedicalIdWithoutANameStillWorks() {
        assertEquals("Medical ID. Allergies: nuts.",
                EmergencyText.spokenMedicalId(id("", "", "", "nuts", "", "", "", "", "")));
    }

    @Test
    public void spokenMedicalIdSpeaksNegativeBloodGroups() {
        assertTrue(EmergencyText.spokenMedicalId(id("", "", "O−", "", "", "", "", "", ""))
                .contains("Blood group O negative."));
        assertTrue(EmergencyText.spokenMedicalId(id("", "", "AB-", "", "", "", "", "", ""))
                .contains("Blood group AB negative."));
    }

    @Test
    public void spokenMedicalIdDoesNotDoubleTheFullStop() {
        assertTrue(EmergencyText.spokenMedicalId(id("", "", "", "nuts.", "", "", "", "", ""))
                .endsWith("Allergies: nuts."));
    }

    @Test
    public void spokenMedicalIdAcceptsAContactWithOnlyOneOfNameAndPhone() {
        assertTrue(EmergencyText.spokenMedicalId(id("", "", "", "x", "", "", "", "Ravi", ""))
                .endsWith("Emergency contact: Ravi."));
        assertTrue(EmergencyText.spokenMedicalId(id("", "", "", "x", "", "", "", "", "555 555 5555"))
                .endsWith("Emergency contact: phone 5 5 5 5 5 5 5 5 5 5."));
    }

    @Test
    public void emptyMedicalIdSaysSo() {
        assertEquals("No medical information has been saved on this phone.",
                EmergencyText.spokenMedicalId(MedicalId.empty()));
        assertEquals("No medical information has been saved on this phone.",
                EmergencyText.spokenMedicalId(null));
    }
}
