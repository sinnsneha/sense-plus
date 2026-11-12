package com.senseplus.sense;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The user's Medical ID (segment 26): the facts a helper or paramedic needs. A plain value
 * object with no Android imports; every field is trimmed and never null. Stored locally as JSON
 * (MedicalStore) and in Firestore as users/{uid}.medical (toMap / fromMap).
 */
public final class MedicalId {

    /** The spinner's "no answer". It is shown nowhere and spoken nowhere. */
    public static final String UNKNOWN_BLOOD_GROUP = "Unknown";

    // Keys of the Firestore map users/{uid}.medical (and of the local JSON).
    public static final String KEY_NAME = "name";
    public static final String KEY_AGE = "age";
    public static final String KEY_BLOOD_GROUP = "bloodGroup";
    public static final String KEY_ALLERGIES = "allergies";
    public static final String KEY_CONDITIONS = "conditions";
    public static final String KEY_MEDICATIONS = "medications";
    public static final String KEY_NOTES = "notes";
    public static final String KEY_CONTACT_NAME = "contactName";
    public static final String KEY_CONTACT_PHONE = "contactPhone";

    public final String name;
    public final String age;
    public final String bloodGroup;
    public final String allergies;
    public final String conditions;
    public final String medications;
    public final String notes;
    public final String contactName;
    public final String contactPhone;

    public MedicalId(String name, String age, String bloodGroup, String allergies,
                     String conditions, String medications, String notes,
                     String contactName, String contactPhone) {
        this.name = clean(name);
        this.age = clean(age);
        this.bloodGroup = clean(bloodGroup);
        this.allergies = clean(allergies);
        this.conditions = clean(conditions);
        this.medications = clean(medications);
        this.notes = clean(notes);
        this.contactName = clean(contactName);
        this.contactPhone = clean(contactPhone);
    }

    public static MedicalId empty() {
        return new MedicalId("", "", "", "", "", "", "", "", "");
    }

    /** True when the blood group was chosen (it is not blank and not "Unknown"). */
    public boolean hasBloodGroup() {
        return !bloodGroup.isEmpty() && !UNKNOWN_BLOOD_GROUP.equalsIgnoreCase(bloodGroup);
    }

    /** True when nothing at all has been filled in. */
    public boolean isEmpty() {
        return name.isEmpty() && age.isEmpty() && !hasBloodGroup() && allergies.isEmpty()
                && conditions.isEmpty() && medications.isEmpty() && notes.isEmpty()
                && contactName.isEmpty() && contactPhone.isEmpty();
    }

    /** Every key is always present (empty text when blank), so a merge write overwrites old values. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(KEY_NAME, name);
        m.put(KEY_AGE, age);
        m.put(KEY_BLOOD_GROUP, bloodGroup);
        m.put(KEY_ALLERGIES, allergies);
        m.put(KEY_CONDITIONS, conditions);
        m.put(KEY_MEDICATIONS, medications);
        m.put(KEY_NOTES, notes);
        m.put(KEY_CONTACT_NAME, contactName);
        m.put(KEY_CONTACT_PHONE, contactPhone);
        return m;
    }

    /** Reads the map written by {@link #toMap()}. Null, missing keys and non-text values are tolerated. */
    public static MedicalId fromMap(Map<?, ?> m) {
        if (m == null) return empty();
        return new MedicalId(text(m, KEY_NAME), text(m, KEY_AGE), text(m, KEY_BLOOD_GROUP),
                text(m, KEY_ALLERGIES), text(m, KEY_CONDITIONS), text(m, KEY_MEDICATIONS),
                text(m, KEY_NOTES), text(m, KEY_CONTACT_NAME), text(m, KEY_CONTACT_PHONE));
    }

    private static String text(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? "" : v.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalId)) return false;
        return toMap().equals(((MedicalId) o).toMap());
    }

    @Override
    public int hashCode() {
        return toMap().hashCode();
    }
}
