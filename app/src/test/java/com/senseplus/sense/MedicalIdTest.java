package com.senseplus.sense;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class MedicalIdTest {

    private static MedicalId sample() {
        return new MedicalId("Maya", "34", "A+", "penicillin", "asthma", "inhaler", "none",
                "Ravi", "+919876543210");
    }

    @Test
    public void fieldsAreTrimmedAndNeverNull() {
        MedicalId m = new MedicalId("  Maya ", null, " ", null, null, null, null, null, null);
        assertEquals("Maya", m.name);
        assertEquals("", m.age);
        assertEquals("", m.bloodGroup);
        assertEquals("", m.contactPhone);
    }

    @Test
    public void emptyOnlyWhenNothingIsFilledIn() {
        assertTrue(MedicalId.empty().isEmpty());
        assertTrue(new MedicalId("", "", "Unknown", "", "", "", "", "", "").isEmpty());
        assertFalse(new MedicalId("", "", "", "", "", "", "x", "", "").isEmpty());
        assertFalse(new MedicalId("", "", "O−", "", "", "", "", "", "").isEmpty());
    }

    @Test
    public void unknownBloodGroupCountsAsNone() {
        assertFalse(new MedicalId("", "", "Unknown", "", "", "", "", "", "").hasBloodGroup());
        assertFalse(new MedicalId("", "", "", "", "", "", "", "", "").hasBloodGroup());
        assertTrue(new MedicalId("", "", "AB+", "", "", "", "", "", "").hasBloodGroup());
    }

    @Test
    public void mapRoundTripKeepsEveryField() {
        MedicalId m = sample();
        assertEquals(m, MedicalId.fromMap(m.toMap()));
    }

    @Test
    public void toMapAlwaysWritesEveryKeySoAClearedFieldOverwritesTheOldValue() {
        Map<String, Object> map = new MedicalId("Maya", "", "", "", "", "", "", "", "").toMap();
        assertEquals(9, map.size());
        assertEquals("", map.get("allergies"));
        assertEquals("Maya", map.get("name"));
        assertEquals("", map.get("contactPhone"));
    }

    @Test
    public void fromMapToleratesMissingKeysNullAndNumbers() {
        assertTrue(MedicalId.fromMap(null).isEmpty());
        Map<String, Object> m = new HashMap<>();
        m.put("age", 34L);       // a number from Firestore, not text
        m.put("notes", null);
        MedicalId id = MedicalId.fromMap(m);
        assertEquals("34", id.age);
        assertEquals("", id.notes);
        assertEquals("", id.name);
    }

    @Test
    public void equalityLooksAtEveryField() {
        assertEquals(sample(), sample());
        assertEquals(sample().hashCode(), sample().hashCode());
        assertNotEquals(sample(), new MedicalId("Maya", "35", "A+", "penicillin", "asthma",
                "inhaler", "none", "Ravi", "+919876543210"));
    }
}
