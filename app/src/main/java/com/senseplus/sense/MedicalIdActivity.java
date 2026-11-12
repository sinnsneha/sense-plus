package com.senseplus.sense;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

/**
 * The user's Medical ID (segment 26): name, age, blood group, allergies, conditions, medications,
 * notes and an emergency contact, plus the opt-in SMS fallback switch.
 *
 * Saved on the phone and copied to users/{uid}.medical (MedicalStore). This screen is editable,
 * so it is NOT shown over the lock screen; "Show to a helper" opens MedicalHelperActivity, the
 * read-only screen that is.
 */
public class MedicalIdActivity extends AppCompatActivity {

    private TextInputLayout tilContactPhone;
    private TextInputEditText etName, etAge, etAllergies, etConditions, etMedications, etNotes,
            etContactName, etContactPhone;
    private Spinner spBloodGroup;
    private MaterialSwitch swSms;

    /** The form as it was when loaded or last saved, to tell whether there is anything unsaved. */
    private MedicalId baseline = MedicalId.empty();
    private boolean updatingSwitch = false;

    private final ActivityResultLauncher<String> smsPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    MedicalStore.setSmsEnabled(this, true);
                } else {
                    // The switch must never say "on" while the permission is missing.
                    MedicalStore.setSmsEnabled(this, false);
                    setSwitch(false);
                    Toast.makeText(this, R.string.medical_sms_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Intent login = new Intent(this, LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(login);
            finish();
            return;
        }

        setContentView(R.layout.activity_medical_id);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.bottomBar));
        Ui.bindBack(this);
        findViewById(R.id.medicalHero).setBackground(Gradients.heroCoral(this).wave());

        etName = findViewById(R.id.etName);
        etAge = findViewById(R.id.etAge);
        etAllergies = findViewById(R.id.etAllergies);
        etConditions = findViewById(R.id.etConditions);
        etMedications = findViewById(R.id.etMedications);
        etNotes = findViewById(R.id.etNotes);
        etContactName = findViewById(R.id.etContactName);
        etContactPhone = findViewById(R.id.etContactPhone);
        tilContactPhone = findViewById(R.id.tilContactPhone);
        swSms = findViewById(R.id.swSms);

        spBloodGroup = findViewById(R.id.spBloodGroup);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.medical_blood_groups, R.layout.item_spinner);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spBloodGroup.setAdapter(adapter);

        populate(MedicalStore.load(this));

        View showHelper = findViewById(R.id.btnShowHelper);
        View save = findViewById(R.id.btnSave);
        showHelper.setOnClickListener(v -> {
            // The helper view shows what is saved, so save pending edits first.
            if (isDirty() && !save()) return;
            startActivity(new Intent(this, MedicalHelperActivity.class));
        });
        save.setOnClickListener(v -> save());
        Ui.pressFeedback(showHelper, save);

        swSms.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSwitch) return;
            if (!checked) {
                MedicalStore.setSmsEnabled(this, false);
            } else if (EmergencySms.hasPermission(this)) {
                MedicalStore.setSmsEnabled(this, true);
            } else {
                smsPermission.launch(Manifest.permission.SEND_SMS); // the result handler turns it back off if denied
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!isDirty()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    askToSave();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (swSms == null) return; // redirected to login in onCreate
        // The permission can be taken away in Settings: then the switch must read "off".
        boolean saved = MedicalStore.smsEnabled(this);
        boolean on = saved && EmergencySms.hasPermission(this);
        if (saved && !on) MedicalStore.setSmsEnabled(this, false);
        setSwitch(on);
    }

    private void setSwitch(boolean checked) {
        updatingSwitch = true;
        swSms.setChecked(checked);
        updatingSwitch = false;
    }

    // ---------- the form ----------

    private void populate(MedicalId saved) {
        String name = saved.name.isEmpty() ? AccountStore.name(this) : saved.name;
        etName.setText(name);
        etAge.setText(saved.age);
        etAllergies.setText(saved.allergies);
        etConditions.setText(saved.conditions);
        etMedications.setText(saved.medications);
        etNotes.setText(saved.notes);
        etContactName.setText(saved.contactName);
        etContactPhone.setText(saved.contactPhone);

        int index = 0; // "Unknown" is the first entry
        for (int i = 0; i < spBloodGroup.getCount(); i++) {
            if (spBloodGroup.getItemAtPosition(i).toString().equals(saved.bloodGroup)) {
                index = i;
                break;
            }
        }
        spBloodGroup.setSelection(index);
        baseline = readForm(); // includes the name pre-filled from the account
    }

    private MedicalId readForm() {
        Object blood = spBloodGroup.getSelectedItem();
        return new MedicalId(text(etName), text(etAge), blood != null ? blood.toString() : "",
                text(etAllergies), text(etConditions), text(etMedications), text(etNotes),
                text(etContactName), text(etContactPhone));
    }

    private static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private boolean isDirty() {
        return !readForm().equals(baseline);
    }

    /** Validates, saves locally and to Firestore. @return false if the form is not valid. */
    private boolean save() {
        MedicalId form = readForm();
        // The phone is what "Call my guardian" and the SMS use, so a typo must not be saved silently.
        if (!form.contactPhone.isEmpty() && PhoneRules.normalize(form.contactPhone) == null) {
            tilContactPhone.setError(getString(R.string.medical_error_phone));
            etContactPhone.requestFocus();
            return false;
        }
        tilContactPhone.setError(null);
        MedicalStore.save(this, form);
        baseline = form;
        Toast.makeText(this, R.string.medical_saved, Toast.LENGTH_SHORT).show();
        return true;
    }

    private void askToSave() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.medical_unsaved_title)
                .setMessage(R.string.medical_unsaved_body)
                .setNegativeButton(R.string.medical_unsaved_discard, (d, w) -> finish())
                .setPositiveButton(R.string.medical_unsaved_save, (d, w) -> {
                    if (save()) finish();
                })
                .show();
    }
}
