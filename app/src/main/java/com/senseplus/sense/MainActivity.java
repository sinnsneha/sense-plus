package com.senseplus.sense;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.ActivityNotFoundException;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.senseplus.sense.ui.DuckDrawable;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.HeroBackgroundDrawable;
import com.senseplus.sense.ui.ScallopBadgeDrawable;
import com.senseplus.sense.ui.StatusStyle;
import com.senseplus.sense.ui.Ui;
import com.senseplus.sense.ui.WaveLevelIndicatorView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String STATUS_ACTION = "com.senseplus.sense.STATUS";
    /** Segment 25: a guardian answered. Extra "message" = the sentence; empty = hide it. */
    public static final String ACK_ACTION = "com.senseplus.sense.ACK";
    /**
     * Segment 28: the "protection is off after a restart" reminder opens this screen with this
     * extra set, and the screen starts monitoring. Starting the service is allowed here because
     * the app is in the foreground; a boot receiver could not do it.
     */
    public static final String EXTRA_AUTO_START = "auto_start";

    /** How long Pip stays happy after "All clear" before going back to idle (ms). */
    private static final long ALL_CLEAR_HAPPY_MS = 2000;

    private TextView tvStatus;
    private View ackCard;
    private TextView tvAck;
    private MaterialButton btnToggle;
    private Button btnCodeword;
    private Button btnLocationSettings;
    private SharedPreferences prefs;

    // redesign
    private View hero;
    private DuckView pipGuard;
    private TextView tvBadge, tvTitle;
    private ImageView imgMedallion;
    private View badgeWrap;
    private WaveLevelIndicatorView levelIndicator;
    private ScallopBadgeDrawable badgeBg;
    private HeroBackgroundDrawable heroBg;
    private StatusStyle currentStyle;
    private ObjectAnimator badgePulse;
    private long happyUntil;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable backToIdle = () -> {
        if (currentStyle == StatusStyle.NORMAL) pipGuard.setExpression(DuckLooks.Expression.IDLE, true);
    };

    private void initCodewordButton() {
        btnCodeword = findViewById(R.id.btnCodeword);
        prefs = getSharedPreferences("senseplus_prefs", MODE_PRIVATE);
        updateCodewordButton();
        btnCodeword.setOnClickListener(v -> {
            final EditText input = new EditText(this);
            input.setInputType(InputType.TYPE_CLASS_TEXT);
            int side = Ui.dp(this, 24);
            FrameLayout wrapper = new FrameLayout(this);
            wrapper.setPadding(side, Ui.dp(this, 4), side, 0);
            wrapper.addView(input, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Set emergency codeword")
                    .setView(wrapper)
                    .setMessage("Use a common English word. Made‑up words can't be recognised by the offline voice model.")
                    .setPositiveButton("Save", (dialog, which) -> {
                        String raw = input.getText().toString();
                        String err = CodewordRules.validate(raw);
                        if (err != null) {
                            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                        } else {
                            String cw = raw.trim().toLowerCase(Locale.US);
                            prefs.edit().putString("codeword", cw).apply();
                            updateCodewordButton();
                            // reload the recognizer only if monitoring is running; sendAction
                            // is a no-op otherwise, so we never start a service just to stop it.
                            sendAction(UserMonitorService.ACTION_RELOAD_VOICE);
                        }
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                    .show();
        });
    }

    private void updateCodewordButton() {
        String cw = prefs.getString("codeword", null);
        if (cw == null) {
            btnCodeword.setText("Set emergency codeword");
        } else {
            // mask after first character
            String masked = cw.charAt(0) + "•••••";
            btnCodeword.setText("Codeword: " + masked);
        }
    }
    private boolean monitoring = false;

    // Segment 26: "Call my guardian". CALL_PHONE is asked for once, with an explanation; a "no"
    // (or a number already asked about) falls back to the dialer, which needs no permission.
    private static final String KEY_CALL_PERMISSION_ASKED = "call_permission_asked";
    private String pendingCallNumber;
    private final ActivityResultLauncher<String> callPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                String number = pendingCallNumber;
                pendingCallNumber = null;
                if (number != null) placeCall(number, granted);
            });

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACK_ACTION.equals(intent.getAction())) {
                showAck(intent.getStringExtra("message"));
                return;
            }
            updateStatusUi(intent.getStringExtra("status"), intent.getStringExtra("info"));
        }
    };

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

        setContentView(R.layout.activity_main);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.guardContent));
        Ui.bindBack(this);

        tvStatus = findViewById(R.id.tvStatus);
        ackCard = findViewById(R.id.ackCard);
        tvAck = findViewById(R.id.tvAck);
        btnToggle = findViewById(R.id.btnToggle);
        btnLocationSettings = findViewById(R.id.btnLocationSettings);
        Button btnHelp = findViewById(R.id.btnHelp);
        Button btnOk = findViewById(R.id.btnOk);

        hero = findViewById(R.id.guardHero);
        pipGuard = findViewById(R.id.pipGuard);
        tvBadge = findViewById(R.id.tvStatusBadge);
        tvTitle = findViewById(R.id.tvGuardTitle);
        imgMedallion = findViewById(R.id.imgBadgeMedallion);
        badgeWrap = findViewById(R.id.badgeWrap);
        levelIndicator = findViewById(R.id.levelIndicator);
        badgeBg = new ScallopBadgeDrawable(Ui.color(this, R.color.tangerine_300),
                Ui.color(this, R.color.tangerine_500));
        tvBadge.setBackground(badgeBg);
        sizeHero();
        applyStyle(StatusStyle.OFF, false); // initial state = monitoring off

        btnLocationSettings.setOnClickListener(v -> openAppSettings());
        refreshLocationPermissionUi();
        requestNeededPermissions();

        btnToggle.setOnClickListener(v -> { if (!monitoring) startMonitoring(); else stopMonitoring(); });
        btnHelp.setOnClickListener(v -> sendAction(UserMonitorService.ACTION_HELP));
        btnOk.setOnClickListener(v -> sendAction(UserMonitorService.ACTION_IM_OK));
        Ui.pressFeedback(btnHelp, btnOk, btnToggle);
        Button btnCall = findViewById(R.id.btnCallGuardian);
        btnCall.setOnClickListener(v -> onCallGuardian());
        Ui.pressFeedback(btnCall);
        // Keep the local copy of the guardians' phones current (one read); it works offline later.
        EmergencyContacts.refresh(this);
        // initialize codeword UI
        initCodewordButton();

        // Segment 28: opened from the restart reminder. Only on a fresh launch, so turning the
        // phone (which recreates the screen with the same intent) does not start it again.
        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_AUTO_START, false)) {
            startMonitoring();
        }
    }

    // ---------- Call my guardian (segment 26) ----------

    /**
     * Number: the first guardian with a phone, otherwise the Medical ID emergency contact. With
     * CALL_PHONE the call starts at once (ACTION_CALL); without it the dialer opens with the
     * number filled in (ACTION_DIAL). The permission is asked for once, with a reason.
     */
    private void onCallGuardian() {
        final String number = EmergencyContacts.callNumber(this);
        if (number == null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.call_no_number_title)
                    .setMessage(R.string.call_no_number_body)
                    .setNegativeButton(R.string.link_cancel, null)
                    .setPositiveButton(R.string.call_open_medical, (d, w) ->
                            startActivity(new Intent(this, MedicalIdActivity.class)))
                    .show();
            return;
        }

        boolean granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            placeCall(number, true);
        } else if (!prefs.getBoolean(KEY_CALL_PERMISSION_ASKED, false)) {
            prefs.edit().putBoolean(KEY_CALL_PERMISSION_ASKED, true).apply();
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.call_permission_title)
                    .setMessage(R.string.call_permission_body)
                    .setNegativeButton(R.string.call_permission_not_now, (d, w) -> placeCall(number, false))
                    .setPositiveButton(R.string.call_permission_allow, (d, w) -> {
                        pendingCallNumber = number;
                        callPermission.launch(Manifest.permission.CALL_PHONE);
                    })
                    .setOnCancelListener(d -> placeCall(number, false))
                    .show();
        } else {
            placeCall(number, false); // asked before and not granted: the dialer still gets them there
        }
    }

    private void placeCall(String number, boolean canCallDirectly) {
        Uri uri = Uri.fromParts("tel", number, null);
        try {
            startActivity(new Intent(canCallDirectly ? Intent.ACTION_CALL : Intent.ACTION_DIAL, uri));
        } catch (SecurityException e) { // permission revoked between the check and the call
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, uri));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, R.string.call_failed, Toast.LENGTH_LONG).show();
            }
        } catch (ActivityNotFoundException e) { // a phone with no dialer, such as a tablet
            Toast.makeText(this, R.string.call_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void startMonitoring() {
        // Segment 28: remember that the person wants protection, so a restart can remind them,
        // and drop the reminder if it is showing: protection is back.
        AccountStore.setMonitoringWanted(this, true);
        ReminderNotifications.cancel(this);
        ContextCompat.startForegroundService(this,
                new Intent(this, UserMonitorService.class).setAction(UserMonitorService.ACTION_START));
        monitoring = true;
        btnToggle.setText(R.string.stop_monitoring);
        btnToggle.setActivated(true); // calm light-lavender "stop" tile while running
        updateStatusUi("NORMAL", "Monitoring (normal)");
    }

    private void stopMonitoring() {
        AccountStore.setMonitoringWanted(this, false); // on purpose: no reminder after a restart
        stopService(new Intent(this, UserMonitorService.class));
        monitoring = false;
        btnToggle.setText(R.string.start_monitoring);
        btnToggle.setActivated(false);
        tvStatus.setText(R.string.status_off);
        applyStyle(StatusStyle.OFF, true);
    }

    private void sendAction(String action) {
        if (!monitoring) return;
        ContextCompat.startForegroundService(this,
                new Intent(this, UserMonitorService.class).setAction(action));
    }

    private void updateStatusUi(String status, String info) {
        if (status == null) status = "";
        StatusStyle style = StatusStyle.from(status);
        applyStyle(style, true);
        // The card already carries a "STATUS" label, so only the message is written.
        tvStatus.setText(info != null ? info : status);
        if (style == StatusStyle.NORMAL && info != null && info.contains("All clear")) allClearFlourish();
    }

    // ---------- presentation (design profile: dynamicStyleHooks.updateStatusUi) ----------

    /** Hero 46% of the screen (300–420dp); Pip at most 68% of the width. */
    private void sizeHero() {
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int min = getResources().getDimensionPixelSize(R.dimen.hero_full_min);
        int max = getResources().getDimensionPixelSize(R.dimen.hero_full_max);
        hero.getLayoutParams().height = Math.max(min, Math.min(max, Math.round(screenH * 0.46f)));
        int pip = Math.min(getResources().getDimensionPixelSize(R.dimen.pip_hero), Math.round(screenW * 0.68f));
        pipGuard.getLayoutParams().width = pip;
        pipGuard.getLayoutParams().height = pip;
    }

    /** The single place that applies a StatusStyle to the screen. */
    private void applyStyle(StatusStyle s, boolean animate) {
        boolean changed = s != currentStyle;
        currentStyle = s;
        boolean motion = animate && !Ui.isReducedMotion();

        if (changed) {
            HeroBackgroundDrawable next = s.hero.hero(this).wave();
            if (motion && heroBg != null) {
                TransitionDrawable fade = new TransitionDrawable(new Drawable[]{heroBg, next});
                fade.setCrossFadeEnabled(true);
                hero.setBackground(fade);
                fade.startTransition(360);
            } else {
                hero.setBackground(next);
            }
            heroBg = next;
        }

        // Pip: keep the 2 s "all clear" smile unless the level has changed.
        boolean keepHappy = s == StatusStyle.NORMAL && SystemClock.uptimeMillis() < happyUntil;
        if (!keepHappy) pipGuard.setLook(s.look, s.expression, motion && changed);
        if (s != StatusStyle.NORMAL) {
            happyUntil = 0;
            handler.removeCallbacks(backToIdle);
        }
        pipGuard.setEffect(s.effect);
        pipGuard.setPulseRings(s == StatusStyle.EMERGENCY);

        // badge
        if (s == StatusStyle.EMERGENCY) {
            badgeBg.setSolid(Ui.color(this, R.color.white));
            tvBadge.setTextColor(Ui.color(this, R.color.status_emergency_deep));
        } else {
            badgeBg.setGradient(Ui.color(this, R.color.tangerine_300), Ui.color(this, R.color.tangerine_500));
            tvBadge.setTextColor(Ui.color(this, R.color.ink_900));
        }
        tvBadge.setText(s.badgeCopy);
        imgMedallion.setImageResource(s.badgeIcon);
        setBadgePulse(s == StatusStyle.ATTENTION);

        levelIndicator.setLevel(s.level, motion);
        tvStatus.setTextColor(Ui.color(this, s.textColor));
        tvStatus.setAccessibilityLiveRegion(s == StatusStyle.EMERGENCY
                ? View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE : View.ACCESSIBILITY_LIVE_REGION_POLITE);
        tvTitle.setTextColor(Ui.color(this, s == StatusStyle.EMERGENCY ? R.color.white : R.color.ink_900));
        Ui.setLightStatusBar(this, s.lightStatusBar);
    }

    /** ATTENTION: the badge gently pulses (1 → 1.04, 1200 ms) unless animations are off. */
    private void setBadgePulse(boolean on) {
        if (on && !Ui.isReducedMotion()) {
            if (badgePulse == null) {
                badgePulse = ObjectAnimator.ofPropertyValuesHolder(badgeWrap,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f));
                badgePulse.setDuration(1200);
                badgePulse.setRepeatCount(ValueAnimator.INFINITE);
                badgePulse.setRepeatMode(ValueAnimator.REVERSE);
                badgePulse.start();
            }
        } else if (badgePulse != null) {
            badgePulse.cancel();
            badgePulse = null;
            badgeWrap.setScaleX(1f);
            badgeWrap.setScaleY(1f);
        }
    }

    /**
     * Segment 25: shows "<name> has seen your alert and is on the way." in large text, or hides
     * the card for an empty message. It is an extra card: the status card, Pip and the level
     * indicator keep showing the alert, because a reply never hides or cancels it.
     */
    private void showAck(String message) {
        if (ackCard == null) return; // redirected to login before the layout was set
        boolean has = message != null && !message.trim().isEmpty();
        ackCard.setVisibility(has ? View.VISIBLE : View.GONE);
        tvAck.setText(has ? message : "");
    }

    /** "All clear": Pip smiles for 2 s, plus a snackbar with Pip's head (celebrate look). */
    private void allClearFlourish() {
        happyUntil = SystemClock.uptimeMillis() + ALL_CLEAR_HAPPY_MS;
        pipGuard.setExpression(DuckLooks.Expression.HAPPY, !Ui.isReducedMotion());
        handler.removeCallbacks(backToIdle);
        handler.postDelayed(backToIdle, ALL_CLEAR_HAPPY_MS);

        Snackbar bar = Snackbar.make(findViewById(android.R.id.content), R.string.all_clear, Snackbar.LENGTH_LONG);
        View v = bar.getView();
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Ui.color(this, R.color.ink_900));
        bg.setCornerRadius(getResources().getDimension(R.dimen.radius_lg));
        v.setBackgroundTintList(null);
        v.setBackground(bg);
        TextView text = v.findViewById(com.google.android.material.R.id.snackbar_text);
        if (text != null) {
            text.setTextAppearance(R.style.TextAppearance_SENSE_BodyM);
            text.setTextColor(Ui.color(this, R.color.white));
            text.setGravity(Gravity.CENTER_VERTICAL);
            int size = getResources().getDimensionPixelSize(R.dimen.pip_snackbar);
            DuckDrawable head = new DuckDrawable(this, DuckLooks.Look.CELEBRATE, DuckLooks.Expression.WINK,
                    size, true).backdrop(Ui.color(this, R.color.lavender_100));
            head.setBounds(0, 0, size, size);
            text.setCompoundDrawablesRelative(head, null, null, null);
            text.setCompoundDrawablePadding(Ui.dp(this, 12));
        }
        bar.show();
    }

    // ---------- permissions & receiver (unchanged) ----------

    private void requestNeededPermissions() {
        List<String> need = new ArrayList<>();
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        // Android 12+ requires FINE to be requested together with COARSE.
        if (!fine) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        } else if (!coarse) {
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 101);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != 101) return;

        refreshLocationPermissionUi();
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && coarse) {
            Toast.makeText(this, "Precise location is off. Navigation needs it.",
                    Toast.LENGTH_LONG).show();
        } else if (!fine && !coarse) {
            Toast.makeText(this, "Location permission is needed for monitoring and navigation.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void refreshLocationPermissionUi() {
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && coarse) {
            btnLocationSettings.setText("Precise location is off. Navigation needs it. Open settings");
            btnLocationSettings.setVisibility(View.VISIBLE);
        } else if (!fine) {
            btnLocationSettings.setText("Location permission is needed. Open settings");
            btnLocationSettings.setVisibility(View.VISIBLE);
        } else {
            btnLocationSettings.setVisibility(View.GONE);
        }
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocationPermissionUi();
        IntentFilter filter = new IntentFilter(STATUS_ACTION);
        filter.addAction(ACK_ACTION);
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        // A reply that arrived while this screen was closed is still shown on return.
        showAck(UserMonitorService.ackMessage);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (badgeWrap != null) setBadgePulse(false); // null when redirected to login
        super.onDestroy();
    }
}
