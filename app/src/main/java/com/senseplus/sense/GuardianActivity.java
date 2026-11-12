package com.senseplus.sense;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.senseplus.sense.ui.DuckDrawable;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.StatusStyle;
import com.senseplus.sense.ui.Ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class GuardianActivity extends AppCompatActivity {

    private TextView tvGStatus, tvGAddress, tvGCoords, tvGBattery, tvGUpdated, tvNoEvents, tvGConn;
    private Button btnOpenMaps, btnStop;
    private LinearLayout eventsContainer;
    private String currentMapsLink = null;
    private String newestEventId = null;
    private boolean guardianOffline = false;

    // Segment 25: "I'm on my way". The button exists only while the status is an alert.
    private Button btnAck;
    private TextView tvGAckError;
    private String myUid;
    private String ackUserName = "";     // who the reply tells: the user's name, as last read
    private boolean ackSending = false;  // a write is waiting for the server
    private boolean ackSent = false;     // this guardian has answered the CURRENT alert

    // Segment 26: the collapsible Medical ID section.
    private View medicalHeader, medicalBody, medicalChevron;
    private LinearLayout medicalRows;
    private TextView tvMedicalEmpty;
    private boolean medicalOpen = false;
    private MedicalId medicalShown = null; // what the rows show now, so a poll that changes nothing rebuilds nothing

    // Segment 27: the safe zone card (chip, switch, radius, "Set home for <name>").
    /** After the guardian changes a setting, the next polls must not put the old value back before the write lands (ms). */
    private static final long SAFE_ZONE_EDIT_HOLD_MS = 6_000L;
    private TextView tvSafeZoneChip, tvSafeZoneHome, tvSafeZoneHint;
    private MaterialSwitch swGSafeZone;
    private Spinner spGRadius;
    private Button btnSetHome;
    private HomeData safeZoneHome = null;       // the Home block as last read (null = none)
    private String safeZoneName = "";           // who it is about, for the labels
    private boolean bindingSafeZone = false;    // true while the controls are filled in, not changed by the guardian
    private long safeZoneEditUntilMs = 0;

    // redesign
    private View statusHeroCard;
    private DuckView pipGuardian;
    private TextView tvGStatusCaption, tvGBatteryAge;
    private StatusStyle shownStyle;
    private ValueAnimator liveDotPulse;

    private FirebaseFirestore db;

    // Segment 23: the person this guardian is linked to (users/{linkedUid}).
    private String linkedUid;
    // Refused reads in a row; a few mean the user removed this guardian.
    private int deniedStreak = 0;
    private boolean unlinkHandled = false;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            FirestorePaths.userDoc(linkedUid).get(Source.SERVER)
                    .addOnSuccessListener(doc -> {
                        guardianOffline = false;
                        deniedStreak = 0;
                        if (doc != null && doc.exists()) showData(doc);
                        else renderConnectivity(null, null);
                    })
                    .addOnFailureListener(e -> {
                        if (isPermissionDenied(e)) {
                            // Not the network: the rules refused us. Three in a row = removed.
                            deniedStreak++;
                            if (PairingRules.shouldUnlink(deniedStreak)) handleUnlinked();
                            return;
                        }
                        // The guardian's own connection failed; don't blame the user's phone.
                        guardianOffline = true;
                        showGuardianOffline();
                        Log.w("SenseCloud", "Status read failed: " + e.getMessage(), e);
                    });
            pollEvents();
            pollHandler.postDelayed(this, 2000);
        }
    };

    private static boolean isPermissionDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }

    /** The user removed this guardian: stop, forget the link, explain, and offer to link again. */
    private void handleUnlinked() {
        if (unlinkHandled || isFinishing()) return;
        unlinkHandled = true;
        pollHandler.removeCallbacks(pollTask);
        String who = AccountStore.linkedName(this);
        AccountStore.clearLink(this);
        stopService(new Intent(this, GuardianService.class));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.link_removed_title)
                .setMessage(getString(R.string.link_removed_body,
                        who == null || who.trim().isEmpty() ? getString(R.string.link_someone) : who.trim()))
                .setCancelable(false)
                .setPositiveButton(R.string.link_again, (d, w) -> {
                    startActivity(new Intent(this, LinkActivity.class));
                    finish();
                })
                .setNegativeButton(R.string.link_close, (d, w) -> finish())
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);

        // The dashboard needs a real account; anonymous sessions from old installs do not count.
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            Intent login = new Intent(this, LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(login);
            finish();
            return;
        }

        myUid = user.getUid();

        // Segment 23: the dashboard shows one linked person. Not linked yet → link first.
        linkedUid = AccountStore.linkedUid(this);
        if (linkedUid == null) {
            startActivity(new Intent(this, LinkActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_guardian);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.guardianContent));
        Ui.bindBack(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tvGStatus = findViewById(R.id.tvGStatus);
        tvGAddress = findViewById(R.id.tvGAddress);
        tvGCoords = findViewById(R.id.tvGCoords);
        tvGBattery = findViewById(R.id.tvGBattery);
        tvGUpdated = findViewById(R.id.tvGUpdated);
        tvGConn = findViewById(R.id.tvGConn);
        tvNoEvents = findViewById(R.id.tvNoEvents);
        eventsContainer = findViewById(R.id.eventsContainer);
        btnOpenMaps = findViewById(R.id.btnOpenMaps);
        btnStop = findViewById(R.id.btnStop);
        btnAck = findViewById(R.id.btnAck);
        tvGAckError = findViewById(R.id.tvGAckError);

        statusHeroCard = findViewById(R.id.statusHeroCard);
        pipGuardian = findViewById(R.id.pipGuardian);
        tvGStatusCaption = findViewById(R.id.tvGStatusCaption);
        tvGBatteryAge = findViewById(R.id.tvGBatteryAge);
        renderStatus(StatusStyle.OFF, false); // nothing heard from the user's phone yet

        // Empty timeline: Pip celebrates "no alerts yet" on a butter backdrop.
        int empty = Ui.dp(this, 96);
        tvNoEvents.setCompoundDrawablesRelativeWithIntrinsicBounds(null,
                new DuckDrawable(this, DuckLooks.Look.CELEBRATE, null, empty, false)
                        .backdrop(Ui.color(this, R.color.butter_100)), null, null);

        btnOpenMaps.setOnClickListener(v -> {
            if (currentMapsLink != null)
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentMapsLink)));
        });
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, GuardianService.class));
            btnStop.setText("Monitoring stopped");
        });
        btnAck.setOnClickListener(v -> sendAck());
        Ui.pressFeedback(btnOpenMaps, btnStop, btnAck);

        medicalHeader = findViewById(R.id.medicalHeader);
        medicalBody = findViewById(R.id.medicalBody);
        medicalChevron = findViewById(R.id.medicalChevron);
        medicalRows = findViewById(R.id.medicalRows);
        tvMedicalEmpty = findViewById(R.id.tvMedicalEmpty);
        medicalHeader.setOnClickListener(v -> setMedicalOpen(!medicalOpen));
        setMedicalOpen(false);

        setUpSafeZoneCard();

        db = FirebaseFirestore.getInstance();

        // keep the background monitoring service running
        androidx.core.content.ContextCompat.startForegroundService(this, new Intent(this, GuardianService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Opening the dashboard counts as seeing the alert: stop a ringing emergency alarm.
        GuardianService.dismissAlert(this);
        pollHandler.post(pollTask);
    }

    @Override
    protected void onStop() {
        super.onStop();
        pollHandler.removeCallbacks(pollTask);
        setLiveDotPulse(false);
    }

    private void showData(DocumentSnapshot doc) {
        String status = doc.getString("status");
        String address = doc.getString("address");
        Double lat = doc.getDouble("latitude");
        Double lon = doc.getDouble("longitude");
        Long battery = doc.getLong("battery");
        Long updatedAt = doc.getLong("updatedAt");
        Timestamp batteryAt = doc.getTimestamp("batteryAt"); // when the user phone last wrote the battery
        currentMapsLink = doc.getString("mapsLink");

        Timestamp lastSeen = doc.getTimestamp("lastSeen");
        Long lastSeenMs = lastSeen != null ? lastSeen.toDate().getTime() : null;
        Boolean monitoring = doc.getBoolean("monitoring");
        renderConnectivity(lastSeenMs, monitoring);

        if (status == null) status = "UNKNOWN";
        renderStatus(StatusStyle.fromGuardian(status), true);
        renderAck(StatusStyle.fromGuardian(status), doc);
        renderMedical(doc);
        renderSafeZone(doc);

        // The tiles already show the labels, so only the values are written.
        tvGAddress.setText(address != null ? address : getString(R.string.dash));
        if (lat != null && lon != null)
            tvGCoords.setText(String.format(Locale.US, "%.5f, %.5f", lat, lon));
        tvGBattery.setText(battery != null ? withSmallUnit(battery + "%", 1) : getString(R.string.dash));
        // Segment 24: how old the battery reading is (EventFormatter.batteryLine's "· 5 min ago").
        tvGBatteryAge.setText(battery != null && batteryAt != null
                ? EventFormatter.age(System.currentTimeMillis() - batteryAt.toDate().getTime()) : "");
        tvGBatteryAge.setVisibility(tvGBatteryAge.length() > 0 ? View.VISIBLE : View.GONE);
        tvGBattery.setContentDescription(EventFormatter.batteryLine(battery,
                batteryAt != null ? batteryAt.toDate().getTime() : null, System.currentTimeMillis()));
        if (updatedAt != null) {
            String pattern = android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm:ss" : "h:mm:ss a";
            tvGUpdated.setText(new SimpleDateFormat(pattern, Locale.US).format(new Date(updatedAt)));
        }
    }

    // ---------- safe zone (segment 27) ----------

    private void setUpSafeZoneCard() {
        tvSafeZoneChip = findViewById(R.id.tvSafeZoneChip);
        tvSafeZoneHome = findViewById(R.id.tvSafeZoneHome);
        tvSafeZoneHint = findViewById(R.id.tvSafeZoneHint);
        swGSafeZone = findViewById(R.id.swGSafeZone);
        spGRadius = findViewById(R.id.spGRadius);
        btnSetHome = findViewById(R.id.btnSetHome);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.safezone_radii, R.layout.item_spinner);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spGRadius.setAdapter(adapter);
        spGRadius.setSelection(HomeData.radiusIndex(HomeData.DEFAULT_RADIUS_M));

        swGSafeZone.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingSafeZone) saveSafeZoneSettings(checked, selectedRadius());
        });
        spGRadius.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Filling the spinner from the document also arrives here: only a real change counts.
                if (bindingSafeZone || safeZoneHome == null || !safeZoneHome.hasLocation()) return;
                int radius = HomeData.RADII_M[position];
                if (radius != safeZoneHome.radiusM) saveSafeZoneSettings(swGSafeZone.isChecked(), radius);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        btnSetHome.setOnClickListener(v -> promptHomeAddress());
        Ui.pressFeedback(btnSetHome);
        safeZoneName = GuardianAlertText.who(AccountStore.linkedName(this)); // until the first poll names them
        renderSafeZone(null);
    }

    private int selectedRadius() {
        int p = spGRadius.getSelectedItemPosition();
        return p >= 0 && p < HomeData.RADII_M.length ? HomeData.RADII_M[p] : HomeData.DEFAULT_RADIUS_M;
    }

    /** Draws the card from the polled document (null = nothing known yet). */
    private void renderSafeZone(DocumentSnapshot doc) {
        if (doc != null) {
            String name = doc.getString("name");
            if (name == null || name.trim().isEmpty()) name = AccountStore.linkedName(this);
            safeZoneName = GuardianAlertText.who(name);
            Object raw = doc.get("home");
            safeZoneHome = raw instanceof Map ? HomeData.fromMap((Map<?, ?>) raw) : null;
        }
        HomeData home = safeZoneHome;
        boolean hasHome = home != null && home.hasLocation();
        boolean enabled = hasHome && home.enabled;
        String zone = doc != null ? doc.getString("safeZone") : null;
        SafeZoneStatus.State state = SafeZoneStatus.classify(hasHome, enabled, zone);

        // Chip: where the user is, in words and a coloured dot.
        String chip;
        @ColorRes int dot;
        switch (state) {
            case INSIDE:
                chip = getString(R.string.safezone_chip_inside);
                dot = R.color.live_dot_on;
                break;
            case OUTSIDE:
                Double lat = doc.getDouble("latitude");
                Double lon = doc.getDouble("longitude");
                chip = lat != null && lon != null
                        ? getString(R.string.safezone_chip_outside,
                                GeoDistance.label(GeoDistance.metres(lat, lon, home.lat, home.lon)))
                        : getString(R.string.safezone_chip_outside_unknown);
                dot = R.color.status_attention_solid;
                break;
            case WAITING:
                chip = getString(R.string.safezone_chip_on);
                dot = R.color.sky_500;
                break;
            case OFF:
                chip = getString(R.string.safezone_chip_off);
                dot = R.color.ink_400;
                break;
            case NO_HOME:
            default:
                chip = getString(R.string.safezone_chip_no_home);
                dot = R.color.ink_400;
                break;
        }
        setTextIfChanged(tvSafeZoneChip, chip);
        Drawable d = tvSafeZoneChip.getCompoundDrawablesRelative()[0];
        if (d != null) d.mutate().setTintList(ColorStateList.valueOf(Ui.color(this, dot)));

        String homeLine = !hasHome ? getString(R.string.safezone_g_no_home)
                : getString(R.string.safezone_g_home, !home.address.isEmpty() ? home.address
                        : String.format(Locale.US, "%.5f, %.5f", home.lat, home.lon));
        setTextIfChanged(tvSafeZoneHome, homeLine);
        setTextIfChanged(swGSafeZone, getString(R.string.safezone_g_switch, safeZoneName));
        setTextIfChanged(btnSetHome, getString(R.string.safezone_g_set_home, safeZoneName));
        btnSetHome.setContentDescription(getString(R.string.safezone_g_set_home_cd, safeZoneName));

        swGSafeZone.setEnabled(hasHome);
        spGRadius.setEnabled(hasHome);
        tvSafeZoneHint.setVisibility(hasHome ? View.GONE : View.VISIBLE);

        // Show the saved values, unless the guardian has just changed one and the write is still landing.
        if (System.currentTimeMillis() >= safeZoneEditUntilMs) {
            bindingSafeZone = true;
            swGSafeZone.setChecked(enabled);
            spGRadius.setSelection(HomeData.radiusIndex(hasHome ? home.radiusM : HomeData.DEFAULT_RADIUS_M));
            bindingSafeZone = false;
        }
    }

    private static void setTextIfChanged(TextView view, String text) {
        if (!text.contentEquals(view.getText())) view.setText(text);
    }

    /** The switch or the radius changed: write only those fields (rules: a guardian may touch only 'home'). */
    private void saveSafeZoneSettings(boolean enabled, int radiusM) {
        if (linkedUid == null) return;
        safeZoneEditUntilMs = System.currentTimeMillis() + SAFE_ZONE_EDIT_HOLD_MS;
        HomeStore.writeSettingsAsGuardian(linkedUid, enabled, radiusM)
                .addOnFailureListener(e -> {
                    safeZoneEditUntilMs = 0; // let the next poll show what is really saved
                    Toast.makeText(this, R.string.safezone_g_settings_failed, Toast.LENGTH_LONG).show();
                });
    }

    /** "Set home for <name>": type an address, look it up (Geocoder), confirm, write users/{uid}.home. */
    private void promptHomeAddress() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.safezone_g_search_hint);
        int side = Ui.dp(this, 24);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(side, Ui.dp(this, 4), side, 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.safezone_g_search_title, safeZoneName))
                .setMessage(R.string.safezone_g_search_body)
                .setView(wrapper)
                .setNegativeButton(R.string.safezone_g_cancel, null)
                .setPositiveButton(R.string.safezone_g_search, (dialog, which) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) {
                        Toast.makeText(this, R.string.safezone_g_type_first, Toast.LENGTH_SHORT).show();
                    } else {
                        searchHome(query);
                    }
                })
                .show();
    }

    private void searchHome(String query) {
        Toast.makeText(this, R.string.safezone_g_searching, Toast.LENGTH_SHORT).show();
        AddressSearch.search(this, query, new AddressSearch.Callback() {
            @Override public void onFound(double lat, double lon, String label) {
                if (isFinishing() || isDestroyed()) return;
                confirmHome(lat, lon, label);
            }

            @Override public void onNotFound() {
                if (!isFinishing() && !isDestroyed())
                    Toast.makeText(GuardianActivity.this, R.string.safezone_g_not_found, Toast.LENGTH_LONG).show();
            }

            @Override public void onError() {
                if (!isFinishing() && !isDestroyed())
                    Toast.makeText(GuardianActivity.this, R.string.safezone_g_search_error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmHome(double lat, double lon, String label) {
        final String who = safeZoneName;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.safezone_g_confirm_title, who))
                .setMessage(label + "\n\n" + String.format(Locale.US, "%.6f, %.6f", lat, lon))
                .setNegativeButton(R.string.safezone_g_cancel, null)
                .setPositiveButton(R.string.safezone_g_confirm_yes, (dialog, which) -> {
                    // Keep the radius and the switch as they are; a first Home starts off at 250 m.
                    HomeData old = safeZoneHome;
                    HomeData home = new HomeData(lat, lon, label,
                            old != null ? old.radiusM : HomeData.DEFAULT_RADIUS_M,
                            old != null && old.enabled, System.currentTimeMillis());
                    safeZoneEditUntilMs = System.currentTimeMillis() + SAFE_ZONE_EDIT_HOLD_MS;
                    HomeStore.writeAsGuardian(linkedUid, home)
                            .addOnSuccessListener(v -> Toast.makeText(this,
                                    getString(R.string.safezone_g_saved, who), Toast.LENGTH_LONG).show())
                            .addOnFailureListener(e -> {
                                safeZoneEditUntilMs = 0;
                                Toast.makeText(this, R.string.safezone_g_save_failed, Toast.LENGTH_LONG).show();
                            });
                })
                .show();
    }

    // ---------- Medical ID (segment 26) ----------

    private void setMedicalOpen(boolean open) {
        medicalOpen = open;
        medicalBody.setVisibility(open ? View.VISIBLE : View.GONE);
        medicalChevron.setRotation(open ? -90f : 90f); // the forward arrow turned: up = open, down = closed
        Ui.setStateDescription(medicalHeader, getString(open
                ? R.string.guardian_medical_expanded : R.string.guardian_medical_collapsed));
    }

    /** users/{uid}.medical rides in the document this poll already read: no extra reads. */
    private void renderMedical(DocumentSnapshot doc) {
        Object raw = doc.get("medical");
        MedicalId id = MedicalId.fromMap(raw instanceof Map ? (Map<?, ?>) raw : null);
        if (id.equals(medicalShown)) return;
        medicalShown = id;
        int rows = MedicalViews.fill(this, medicalRows, id, false);
        tvMedicalEmpty.setVisibility(rows == 0 ? View.VISIBLE : View.GONE);
    }

    // ---------- "I'm on my way" (segment 25) ----------

    /**
     * Shows the button only while the user is in a possible fall or possible emergency. Whether
     * this guardian has already answered comes from the document itself (ack.by is me), so the
     * button is right after reopening the dashboard or answering from the notification. The
     * user's phone clears the old ack when a new alert begins, so "Sent" never carries over.
     */
    private void renderAck(StatusStyle style, DocumentSnapshot doc) {
        String name = doc.getString("name");
        if (name == null || name.trim().isEmpty()) name = AccountStore.linkedName(this);
        ackUserName = GuardianAlertText.who(name);

        if (!style.isUrgent()) { // calm again (or no status yet): hide it and forget this alert
            ackSent = false;
            ackSending = false;
            btnAck.setVisibility(View.GONE);
            tvGAckError.setVisibility(View.GONE);
            return;
        }

        btnAck.setVisibility(View.VISIBLE);
        if (myUid != null && myUid.equals(doc.getString("ack.by"))) ackSent = true;

        // Not setEnabled(false): the Stadium button's disabled look (ink_400 on ink_100) would make
        // the confirmation hard to read. It stays readable and simply stops taking taps.
        if (ackSent) {
            tvGAckError.setVisibility(View.GONE);
            btnAck.setClickable(false);
            btnAck.setText(getString(R.string.guardian_ack_sent, ackUserName));
        } else if (ackSending) {
            btnAck.setClickable(false);
            btnAck.setText(R.string.guardian_ack_sending);
        } else {
            btnAck.setClickable(true);
            btnAck.setText(R.string.guardian_ack_button);
        }
    }

    private void sendAck() {
        if (ackSending || ackSent) return;
        ackSending = true;
        tvGAckError.setVisibility(View.GONE);
        btnAck.setClickable(false);
        btnAck.setText(R.string.guardian_ack_sending);

        final String who = ackUserName;
        GuardianAck.send(this, new GuardianAck.Callback() {
            @Override public void onSent() {
                ackSending = false;
                // Stops a ringing alarm and leaves "You told <name> you're on your way".
                AckNotifications.showSent(getApplicationContext(), who);
                if (isFinishing() || isDestroyed()) return;
                // A reply confirmed after the alert already ended must not mark the next one.
                if (shownStyle == null || !shownStyle.isUrgent()) return;
                ackSent = true;
                btnAck.setClickable(false);
                btnAck.setText(getString(R.string.guardian_ack_sent, who));
            }

            @Override public void onFailed() {
                ackSending = false;
                if (isFinishing() || isDestroyed()) return;
                btnAck.setClickable(true); // keep it usable: the guardian must be able to retry
                btnAck.setText(R.string.guardian_ack_button);
                tvGAckError.setVisibility(View.VISIBLE);
            }
        });
    }

    // ---------- presentation (replaces colorFor) ----------

    /** Status card gradient, Pip (guardian look), headline + caption and their colours. */
    private void renderStatus(StatusStyle s, boolean animate) {
        boolean changed = s != shownStyle;
        shownStyle = s;
        if (changed) Gradients.organicFrom(this, s.guardianHero).applyTo(statusHeroCard);

        pipGuardian.setLook(DuckLooks.Look.GUARDIAN, s.guardianExpression,
                animate && changed && !Ui.isReducedMotion());
        pipGuardian.setEffect(s.effect);
        pipGuardian.setPulseRings(s == StatusStyle.EMERGENCY);

        int title, caption;
        switch (s) {
            case EMERGENCY:
                title = R.string.guardian_status_emergency;
                caption = R.string.guardian_caption_emergency;
                break;
            case ATTENTION:
                title = R.string.guardian_status_attention;
                caption = R.string.guardian_caption_attention;
                break;
            case NORMAL:
                title = R.string.guardian_status_normal;
                caption = R.string.guardian_caption_normal;
                break;
            case OFF:
            default:
                title = R.string.guardian_waiting;
                caption = R.string.guardian_caption_waiting;
                break;
        }
        tvGStatus.setText(title);
        tvGStatusCaption.setText(caption);
        // White only on the red card (#D92D20, 4.8:1); ink on every pastel.
        boolean onRed = s == StatusStyle.EMERGENCY;
        tvGStatus.setTextColor(Ui.color(this, onRed ? R.color.white : R.color.ink_900));
        tvGStatusCaption.setTextColor(Ui.color(this, onRed ? R.color.white : R.color.ink_800));
        tvGStatus.setAccessibilityLiveRegion(onRed
                ? View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE : View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    /** "80%" with the unit smaller, baseline-aligned (components.statTile). */
    private CharSequence withSmallUnit(String text, int unitChars) {
        SpannableString s = new SpannableString(text);
        s.setSpan(new RelativeSizeSpan(0.36f), text.length() - unitChars, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private void setConnChip(String text, @ColorRes int dot, boolean live) {
        tvGConn.setText(text);
        Drawable d = tvGConn.getCompoundDrawablesRelative()[0];
        if (d != null) {
            d.mutate().setTintList(ColorStateList.valueOf(Ui.color(this, dot)));
            d.setAlpha(255);
        }
        setLiveDotPulse(live);
    }

    /** components.liveDot: the green dot breathes (alpha 1 → 0.4, 1200 ms) while online. */
    private void setLiveDotPulse(boolean on) {
        if (on && !Ui.isReducedMotion()) {
            if (liveDotPulse != null) return;
            liveDotPulse = ValueAnimator.ofFloat(1f, 0.4f);
            liveDotPulse.setDuration(1200);
            liveDotPulse.setRepeatCount(ValueAnimator.INFINITE);
            liveDotPulse.setRepeatMode(ValueAnimator.REVERSE);
            liveDotPulse.addUpdateListener(a -> {
                Drawable d = tvGConn.getCompoundDrawablesRelative()[0];
                if (d != null) d.setAlpha(Math.round(255 * (float) a.getAnimatedValue()));
            });
            liveDotPulse.start();
        } else if (liveDotPulse != null) {
            liveDotPulse.cancel();
            liveDotPulse = null;
            Drawable d = tvGConn.getCompoundDrawablesRelative()[0];
            if (d != null) d.setAlpha(255);
        }
    }

    private void renderConnectivity(Long lastSeenMs, Boolean monitoring) {
        if (guardianOffline) {
            showGuardianOffline();
            return;
        }
        long now = System.currentTimeMillis();
        ConnectivityStatus.State state = ConnectivityStatus.classify(now, lastSeenMs, monitoring);
        String label = ConnectivityStatus.label(now, lastSeenMs, monitoring);

        if (state == ConnectivityStatus.State.ONLINE) {
            setConnChip(label, R.color.live_dot_on, true);
        } else if (state == ConnectivityStatus.State.RECENT) {
            setConnChip(label, R.color.status_attention_solid, false);
        } else {
            setConnChip(label, R.color.ink_400, false);
        }
    }

    private void showGuardianOffline() {
        setConnChip("This phone is offline: can't check", R.color.live_dot_error, false);
    }

    // ---------- event history (same 2 s poll, no snapshot listener) ----------
    // Every poll reads only the newest event (1 read). The full 20-document query
    // runs only when that document changes, keeping the free read quota usable.
    private void pollEvents() {
        FirestorePaths.events(linkedUid)
                .orderBy("time", Query.Direction.DESCENDING)
                .limit(1)
                .get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        showEvents(snap); // keeps the "No events yet" empty state
                        return;
                    }
                    String newest = snap.getDocuments().get(0).getId();
                    if (!newest.equals(newestEventId)) loadEvents();
                })
                .addOnFailureListener(e ->
                        Log.w("SenseCloud", "Event read failed: " + e.getMessage(), e));
    }

    private void loadEvents() {
        FirestorePaths.events(linkedUid)
                .orderBy("time", Query.Direction.DESCENDING)
                .limit(20)
                .get(Source.SERVER)
                .addOnSuccessListener(this::showEvents)
                .addOnFailureListener(e ->
                        Log.w("SenseCloud", "Event read failed: " + e.getMessage(), e));
    }

    private void showEvents(QuerySnapshot snap) {
        if (snap == null || snap.isEmpty()) {
            if (!"EMPTY".equals(newestEventId)) {
                newestEventId = "EMPTY";
                eventsContainer.removeAllViews();
                eventsContainer.setVisibility(View.GONE);
                tvNoEvents.setVisibility(View.VISIBLE);
            }
            return;
        }

        // Rebuild only when a new event arrives, so the list does not flicker every 2 s.
        String newest = snap.getDocuments().get(0).getId();
        if (newest.equals(newestEventId)) return;
        newestEventId = newest;

        eventsContainer.removeAllViews();
        eventsContainer.setVisibility(View.VISIBLE);
        tvNoEvents.setVisibility(View.GONE);

        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        LayoutInflater inflater = LayoutInflater.from(this);
        int count = snap.getDocuments().size();

        for (int i = 0; i < count; i++) {
            DocumentSnapshot d = snap.getDocuments().get(i);
            Timestamp ts = d.getTimestamp("time");
            Long serverTime = ts != null ? ts.toDate().getTime() : null;
            Long clientTime = d.getLong("clientTime");
            String type = d.getString("type");
            String detail = d.getString("detail");

            View row = inflater.inflate(R.layout.item_event, eventsContainer, false);
            String title = EventFormatter.label(type);
            String time = EventFormatter.timeString(serverTime, clientTime, now, zone);
            ((TextView) row.findViewById(R.id.eventTitle)).setText(title);
            ((TextView) row.findViewById(R.id.eventTime)).setText(time);
            TextView detailView = row.findViewById(R.id.eventDetail);
            boolean hasDetail = detail != null && !detail.trim().isEmpty();
            detailView.setText(hasDetail ? detail : "");
            detailView.setVisibility(hasDetail ? View.VISIBLE : View.GONE);
            styleEventIcon(row, type);
            row.findViewById(R.id.eventDivider).setVisibility(i == count - 1 ? View.GONE : View.VISIBLE);
            row.setContentDescription(title + ", " + time + (hasDetail ? ". " + detail : ""));
            eventsContainer.addView(row);
        }
    }

    /** Icon tile coloured by level: attention amber, help/emergency red, all clear green, guardian blue. */
    private void styleEventIcon(View row, String type) {
        @ColorRes int tint, iconColor;
        @DrawableRes int icon;
        if (EventTypes.ATTENTION.equals(type)) {
            tint = R.color.status_attention_tint;
            iconColor = R.color.status_attention_text;
            icon = R.drawable.ic_warning;
        } else if (EventTypes.LOW_BATTERY.equals(type)) {
            tint = R.color.butter_100;
            iconColor = R.color.butter_800;
            icon = R.drawable.ic_battery;
        } else if (EventTypes.ALL_CLEAR.equals(type)) {
            tint = R.color.status_normal_tint;
            iconColor = R.color.status_normal_text;
            icon = R.drawable.ic_check_circle;
        } else if (EventTypes.SAFE_ZONE_EXIT.equals(type)) { // amber: left the safe zone
            tint = R.color.status_attention_tint;
            iconColor = R.color.status_attention_text;
            icon = R.drawable.ic_home;
        } else if (EventTypes.SAFE_ZONE_RETURN.equals(type)) { // green: back home
            tint = R.color.status_normal_tint;
            iconColor = R.color.status_normal_text;
            icon = R.drawable.ic_home;
        } else if (EventTypes.GUARDIAN_ACK.equals(type)) { // blue: a guardian is on the way
            tint = R.color.sky_100;
            iconColor = R.color.sky_700;
            icon = R.drawable.ic_person;
        } else if (EventTypes.EMERGENCY.equals(type) || EventTypes.MANUAL_HELP.equals(type)
                || EventTypes.VOICE_HELP.equals(type) || EventTypes.CODEWORD.equals(type)) {
            tint = R.color.status_emergency_tint;
            iconColor = R.color.status_emergency_text;
            icon = R.drawable.ic_emergency;
        } else {
            tint = R.color.ink_100;
            iconColor = R.color.ink_600;
            icon = R.drawable.ic_history;
        }
        row.findViewById(R.id.eventIconTile).setBackgroundTintList(
                ColorStateList.valueOf(Ui.color(this, tint)));
        ImageView iv = row.findViewById(R.id.eventIcon);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(Ui.color(this, iconColor)));
    }
}
