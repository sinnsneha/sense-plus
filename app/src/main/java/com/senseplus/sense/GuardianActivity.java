package com.senseplus.sense;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
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
import java.util.TimeZone;

public class GuardianActivity extends AppCompatActivity {

    private TextView tvGStatus, tvGAddress, tvGCoords, tvGBattery, tvGUpdated, tvNoEvents, tvGConn;
    private Button btnOpenMaps, btnStop;
    private LinearLayout eventsContainer;
    private String currentMapsLink = null;
    private String newestEventId = null;
    private boolean guardianOffline = false;

    // redesign
    private View statusHeroCard;
    private DuckView pipGuardian;
    private TextView tvGStatusCaption, tvGBatteryAge;
    private StatusStyle shownStyle;
    private ValueAnimator liveDotPulse;

    private FirebaseFirestore db;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            db.collection("sense_users").document("demoUser").get(Source.SERVER)
                    .addOnSuccessListener(doc -> {
                        guardianOffline = false;
                        if (doc != null && doc.exists()) showData(doc);
                        else renderConnectivity(null, null);
                    })
                    .addOnFailureListener(e -> {
                        // The guardian's own connection failed; don't blame the user's phone.
                        guardianOffline = true;
                        showGuardianOffline();
                        Log.w("SenseCloud", "Status read failed: " + e.getMessage(), e);
                    });
            pollEvents();
            pollHandler.postDelayed(this, 2000);
        }
    };

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
        Ui.pressFeedback(btnOpenMaps, btnStop);

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
        db.collection("sense_users").document("demoUser")
                .collection("events")
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
        db.collection("sense_users").document("demoUser")
                .collection("events")
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

    /** Icon tile coloured by level: attention amber, help/emergency red, all clear green. */
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
