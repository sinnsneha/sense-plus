package com.senseplus.sense;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

/**
 * Builds the label-over-value rows of a Medical ID (segment 26), for the read-only helper view
 * (large text) and the guardian dashboard (normal text). Empty facts get no row.
 */
final class MedicalViews {

    private MedicalViews() {
    }

    /**
     * Replaces the contents of {@code container} with one row per filled-in fact.
     *
     * @param large true for the helper view's very large, high-contrast text
     * @return how many rows were added (0 = nothing to show)
     */
    static int fill(Context context, LinearLayout container, MedicalId id, boolean large) {
        container.removeAllViews();
        if (id == null) return 0;
        LayoutInflater inflater = LayoutInflater.from(context);
        int count = 0;
        count += row(context, inflater, container, R.string.medical_row_name, id.name, large);
        count += row(context, inflater, container, R.string.medical_row_age, id.age, large);
        count += row(context, inflater, container, R.string.medical_row_blood,
                id.hasBloodGroup() ? id.bloodGroup : "", large);
        count += row(context, inflater, container, R.string.medical_row_allergies, id.allergies, large);
        count += row(context, inflater, container, R.string.medical_row_conditions, id.conditions, large);
        count += row(context, inflater, container, R.string.medical_row_medications, id.medications, large);
        count += row(context, inflater, container, R.string.medical_row_notes, id.notes, large);
        count += row(context, inflater, container, R.string.medical_row_contact, contactLine(id), large);
        return count;
    }

    /** "Ravi · +91 98765 43210", or whichever half exists. */
    static String contactLine(MedicalId id) {
        if (!id.contactName.isEmpty() && !id.contactPhone.isEmpty()) {
            return id.contactName + " · " + id.contactPhone;
        }
        return id.contactName.isEmpty() ? id.contactPhone : id.contactName;
    }

    private static int row(Context context, LayoutInflater inflater, LinearLayout container,
                           @StringRes int label, String value, boolean large) {
        if (value == null || value.trim().isEmpty()) return 0;
        View row = inflater.inflate(R.layout.item_medical_row, container, false);
        String labelText = context.getString(label);
        TextView tvLabel = row.findViewById(R.id.rowLabel);
        TextView tvValue = row.findViewById(R.id.rowValue);
        tvLabel.setText(labelText);
        tvValue.setText(value);
        if (large) {
            tvLabel.setTextAppearance(R.style.TextAppearance_SENSE_HelperLabel);
            tvValue.setTextAppearance(R.style.TextAppearance_SENSE_HelperValue);
        }
        row.setContentDescription(context.getString(R.string.medical_row_cd, labelText, value));
        container.addView(row);
        return 1;
    }
}
