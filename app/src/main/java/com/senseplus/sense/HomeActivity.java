package com.senseplus.sense;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * The role-based home screen. A User sees only user tools; a Guardian sees only the
 * guardian dashboard and Sensor Info. The greeting, role label and account controls
 * follow the role. Nothing is deleted: every feature lives under its role.
 */
public class HomeActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 302;

    private String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous() || !AccountStore.hasCachedRole(this)) {
            openLogin();
            return;
        }
        role = AccountStore.role(this);

        setContentView(R.layout.activity_home);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.homeContent));

        styleCards();
        setUpHeader();
        setUpCardClicks();

        // Debug builds only: long-press the greeting to open Pip's look/expression gallery.
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            findViewById(R.id.greeting).setOnLongClickListener(v -> {
                startActivity(new Intent(this, CharacterGalleryActivity.class));
                return true;
            });
        }

        if (Roles.USER.equals(role)) {
            SharedPreferences prefs = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
            if (savedInstanceState == null
                    && !prefs.getBoolean(ProfileActivity.KEY_PROFILE_DONE, false)) {
                startActivity(new Intent(this, ProfileActivity.class));
            }
        } else {
            ensureGuardianService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (role == null) return; // redirected to login in onCreate
        AccountStore.refresh(this); // background: pick up a role/name change from Firestore
        applyCardVisibility();
    }

    private void setUpHeader() {
        findViewById(R.id.btnEditProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.btnAccount).setOnClickListener(v -> showAccountDialog());
        applyRoleChrome(role);
    }

    /** Role label, greeting name, avatar tint and the controls that only make sense for one role. */
    private void applyRoleChrome(String currentRole) {
        boolean guardian = Roles.GUARDIAN.equals(currentRole);

        ((TextView) findViewById(R.id.tvHomeTitle)).setText(
                guardian ? R.string.home_title_guardian : R.string.app_name);

        String name = AccountStore.name(this);
        if (name == null || name.trim().isEmpty()) name = getString(R.string.home_name_fallback);
        name = name.trim();
        ((TextView) findViewById(R.id.tvGreetingName)).setText(name);
        findViewById(R.id.greeting).setContentDescription(getString(R.string.home_greeting_role_cd,
                name, Roles.label(currentRole)));

        // A guardian's avatar sits on the guardian family colour (mint), a user's on lavender.
        findViewById(R.id.avatarWrap).setBackgroundTintList(ColorStateList.valueOf(
                Ui.color(this, guardian ? R.color.mint_100 : R.color.lavender_100)));

        // The assistance-mode profile is a user feature only.
        findViewById(R.id.btnEditProfile).setVisibility(guardian ? View.GONE : View.VISIBLE);
    }

    private void setUpCardClicks() {
        findViewById(R.id.cardUser).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
        findViewById(R.id.cardComm).setOnClickListener(v ->
                startActivity(new Intent(this, CommunicationActivity.class)));
        findViewById(R.id.cardVisual).setOnClickListener(v ->
                startActivity(new Intent(this, VisualAssistActivity.class)));
        findViewById(R.id.cardHearing).setOnClickListener(v ->
                startActivity(new Intent(this, HearingActivity.class)));
        findViewById(R.id.cardNavigation).setOnClickListener(v ->
                startActivity(new Intent(this, NavigationActivity.class)));
        findViewById(R.id.cardSensor).setOnClickListener(v ->
                startActivity(new Intent(this, SensorInfoActivity.class)));
        findViewById(R.id.cardGuardian).setOnClickListener(v -> authenticateThenOpenGuardian());
    }

    /** Shows or hides each card according to the role and the saved profile. */
    private void applyCardVisibility() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            openLogin();
            return;
        }
        String currentRole = AccountStore.role(this);
        if (!Roles.isValid(currentRole)) {
            openLogin();
            return;
        }
        role = currentRole;
        applyRoleChrome(currentRole);

        SharedPreferences prefs = getSharedPreferences(ProfileActivity.PREFS, MODE_PRIVATE);
        Set<String> modes = Roles.USER.equals(currentRole)
                && prefs.getBoolean(ProfileActivity.KEY_PROFILE_DONE, false)
                ? ProfileActivity.loadModes(prefs)
                : Collections.<String>emptySet();
        Set<String> visible = ModeMapper.visibleCards(currentRole, modes);

        setCardVisible(R.id.cardUser, ModeMapper.CARD_USER, visible);
        setCardVisible(R.id.cardGuardian, ModeMapper.CARD_GUARDIAN, visible);
        setCardVisible(R.id.cardComm, ModeMapper.CARD_COMMUNICATION, visible);
        setCardVisible(R.id.cardVisual, ModeMapper.CARD_VISUAL, visible);
        setCardVisible(R.id.cardHearing, ModeMapper.CARD_HEARING, visible);
        setCardVisible(R.id.cardNavigation, ModeMapper.CARD_NAVIGATION, visible);
        setCardVisible(R.id.cardSensor, ModeMapper.CARD_SENSOR, visible);
        fitLayoutToVisibleCards(visible);
    }

    private void setCardVisible(int viewId, String card, Set<String> visible) {
        findViewById(viewId).setVisibility(visible.contains(card) ? View.VISIBLE : View.GONE);
    }

    // ---------- presentation ----------

    /** Organic hero cards, tool tiles (section family + Pip look), press feedback. */
    private void styleCards() {
        Gradients.cardTangerine(this).applyTo(findViewById(R.id.cardUser));
        Gradients.cardLavender(this).applyTo(findViewById(R.id.cardGuardian));

        styleTile(R.id.cardComm, Gradients.Family.MINT, DuckLooks.Look.COMM,
                R.string.home_tile_comm_title, R.string.home_tile_comm_sub);
        styleTile(R.id.cardVisual, Gradients.Family.BUTTER, DuckLooks.Look.VISUAL,
                R.string.home_tile_visual_title, R.string.home_tile_visual_sub);
        styleTile(R.id.cardHearing, Gradients.Family.BLUSH, DuckLooks.Look.HEARING,
                R.string.home_tile_hearing_title, R.string.home_tile_hearing_sub);
        styleTile(R.id.cardNavigation, Gradients.Family.CORAL, DuckLooks.Look.NAV,
                R.string.home_tile_nav_title, R.string.home_tile_nav_sub);

        Ui.pressFeedback(findViewById(R.id.cardUser), findViewById(R.id.cardGuardian),
                findViewById(R.id.cardSensor), findViewById(R.id.btnAccount),
                findViewById(R.id.btnEditProfile));
    }

    private void styleTile(int id, Gradients.Family family, DuckLooks.Look look,
                           @StringRes int title, @StringRes int sub) {
        View tile = findViewById(id);
        tile.setBackground(Gradients.tile(this, family));
        ((TextView) tile.findViewById(R.id.tileTitle)).setText(title);
        ((TextView) tile.findViewById(R.id.tileSub)).setText(sub);
        ((DuckView) tile.findViewById(R.id.tilePip)).setLook(look);
        tile.setContentDescription(getString(R.string.tile_cd, getString(title), getString(sub)));
        Ui.pressFeedback(tile);
    }

    /**
     * Two hero cards: 300dp each so the second peeks in. One hero card (each role has one):
     * full width. No tools for this role (guardian): hide the tools header too.
     */
    private void fitLayoutToVisibleCards(Set<String> visible) {
        boolean both = visible.contains(ModeMapper.CARD_USER) && visible.contains(ModeMapper.CARD_GUARDIAN);
        int screen = getResources().getDisplayMetrics().widthPixels;
        int width = both
                ? Math.min(getResources().getDimensionPixelSize(R.dimen.organic_card_width), screen - Ui.dp(this, 72))
                : screen - 2 * getResources().getDimensionPixelSize(R.dimen.screen_gutter);
        for (int id : new int[]{R.id.cardUser, R.id.cardGuardian}) {
            View card = findViewById(id);
            ViewGroup.LayoutParams lp = card.getLayoutParams();
            if (lp.width != width) {
                lp.width = width;
                card.setLayoutParams(lp);
            }
        }
        findViewById(R.id.heroCarousel).setPadding(
                getResources().getDimensionPixelSize(R.dimen.screen_gutter), Ui.dp(this, 4),
                both ? Ui.dp(this, 56) : getResources().getDimensionPixelSize(R.dimen.screen_gutter),
                Ui.dp(this, 16));

        boolean anyTool = visible.contains(ModeMapper.CARD_COMMUNICATION) || visible.contains(ModeMapper.CARD_VISUAL)
                || visible.contains(ModeMapper.CARD_HEARING) || visible.contains(ModeMapper.CARD_NAVIGATION);
        findViewById(R.id.toolsHeader).setVisibility(anyTool ? View.VISIBLE : View.GONE);
        findViewById(R.id.toolGrid).setVisibility(anyTool ? View.VISIBLE : View.GONE);
    }

    // ---------- guardian service, login, account (segment 22) ----------

    /** Guardian alerts run from Home, not only while the dashboard is open. */
    private void ensureGuardianService() {
        if (!Roles.GUARDIAN.equals(role)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
        ContextCompat.startForegroundService(this, new Intent(this, GuardianService.class));
    }

    private void openLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showAccountDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String email = user != null && user.getEmail() != null ? user.getEmail() : "—";
        String name = AccountStore.name(this);
        if (name == null || name.trim().isEmpty()) name = "—";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Account")
                .setMessage("Name: " + name + "\nEmail: " + email
                        + "\nRole: " + Roles.label(role))
                .setNegativeButton("Sign out", (dialog, which) -> confirmSignOut())
                .setPositiveButton("Close", null)
                .show();
    }

    private void confirmSignOut() {
        String message = Roles.USER.equals(role)
                ? "Fall detection and alerts will stop. You will need to sign in again."
                : "Guardian alerts will stop on this phone. You will need to sign in again.";

        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign out?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Sign out", (dialog, which) -> signOut())
                .show();
    }

    private void signOut() {
        stopService(new Intent(this, UserMonitorService.class));
        stopService(new Intent(this, GuardianService.class));
        FirebaseAuth.getInstance().signOut();
        AccountStore.clear(this);

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // ---------- biometric guardian lock (unchanged) ----------

    private void authenticateThenOpenGuardian() {
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

        BiometricManager bm = BiometricManager.from(this);
        if (bm.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "No lock set up; opening guardian view", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, GuardianActivity.class));
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        startActivity(new Intent(HomeActivity.this, GuardianActivity.class));
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        Toast.makeText(HomeActivity.this, "Authentication: " + errString, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Guardian access")
                .setSubtitle("Verify it's you to view the dashboard")
                .setAllowedAuthenticators(authenticators)
                .build();
        prompt.authenticate(info);
    }
}
