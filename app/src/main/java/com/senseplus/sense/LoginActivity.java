package com.senseplus.sense;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.senseplus.sense.ui.DuckDrawable;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

/**
 * The launcher. A signed-in user with a cached role goes straight to Home (no flash of this
 * screen). Anonymous sessions from older installs count as signed out. Sign-in persists.
 */
public class LoginActivity extends AppCompatActivity {

    private MaterialButtonToggleGroup tabs;
    private LinearLayout signInContainer, createContainer, retryBox;
    private ProgressBar progress;
    private TextView tvAuthError, tvRetryMessage;
    private TextInputLayout tilSignInEmail, tilSignInPassword, tilName, tilCreateEmail,
            tilCreatePassword, tilConfirm, tilPhone;
    private TextInputEditText etSignInEmail, etSignInPassword, etName, etCreateEmail,
            etCreatePassword, etConfirm, etPhone;
    private CheckBox cbRoleUser, cbRoleGuardian;
    private Button btnSignIn, btnCreate, btnForgot;

    private FirebaseAuth auth;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        FirebaseUser existing = auth.getCurrentUser();
        if (existing != null && existing.isAnonymous()) {
            // Older installs used anonymous sign-in. Treat that as signed out.
            auth.signOut();
            existing = null;
        }
        if (existing != null && AccountStore.hasCachedRole(this)) {
            goHome(); // before setContentView, so the login screen never appears
            return;
        }

        setContentView(R.layout.activity_login);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.loginContent));
        findViewById(R.id.loginHero).setBackground(Gradients.heroLavender(this).wave());

        bindViews();
        setUpTabs();
        setUpRoleCards();
        setUpClickListeners();

        if (existing != null) {
            // Signed in, but this phone has no cached role (e.g. app data was partly cleared).
            loadRoleAndContinue(existing);
        }
    }

    private void bindViews() {
        tabs = findViewById(R.id.tabs);
        signInContainer = findViewById(R.id.signInContainer);
        createContainer = findViewById(R.id.createContainer);
        retryBox = findViewById(R.id.retryBox);
        progress = findViewById(R.id.loginProgress);
        tvAuthError = findViewById(R.id.tvAuthError);
        tvRetryMessage = findViewById(R.id.tvRetryMessage);

        tilSignInEmail = findViewById(R.id.tilSignInEmail);
        tilSignInPassword = findViewById(R.id.tilSignInPassword);
        tilName = findViewById(R.id.tilName);
        tilCreateEmail = findViewById(R.id.tilCreateEmail);
        tilCreatePassword = findViewById(R.id.tilCreatePassword);
        tilConfirm = findViewById(R.id.tilConfirm);
        tilPhone = findViewById(R.id.tilPhone);

        etSignInEmail = findViewById(R.id.etSignInEmail);
        etSignInPassword = findViewById(R.id.etSignInPassword);
        etName = findViewById(R.id.etName);
        etCreateEmail = findViewById(R.id.etCreateEmail);
        etCreatePassword = findViewById(R.id.etCreatePassword);
        etConfirm = findViewById(R.id.etConfirm);
        etPhone = findViewById(R.id.etPhone);

        cbRoleUser = findViewById(R.id.cbRoleUser);
        cbRoleGuardian = findViewById(R.id.cbRoleGuardian);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnCreate = findViewById(R.id.btnCreate);
        btnForgot = findViewById(R.id.btnForgot);
    }

    private void setUpTabs() {
        tabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean create = checkedId == R.id.btnTabCreate;
            signInContainer.setVisibility(create ? View.GONE : View.VISIBLE);
            createContainer.setVisibility(create ? View.VISIBLE : View.GONE);
            tvAuthError.setVisibility(View.GONE);
        });
        tabs.check(R.id.btnTabSignIn);
    }

    private void setUpRoleCards() {
        // Presentation: Pip shows each role (guard look = protected, guardian look = looks after),
        // label bold, explanation smaller and muted. The text itself is unchanged for TalkBack.
        int pipSize = getResources().getDimensionPixelSize(R.dimen.pip_row);
        dressRoleCard(cbRoleUser, DuckLooks.Look.GUARD, R.color.tangerine_100, pipSize);
        dressRoleCard(cbRoleGuardian, DuckLooks.Look.GUARDIAN, R.color.mint_100, pipSize);

        // Only one role can be chosen; there is no default, so the person states it explicitly.
        cbRoleUser.setOnCheckedChangeListener((button, checked) -> {
            if (checked) cbRoleGuardian.setChecked(false);
        });
        cbRoleGuardian.setOnCheckedChangeListener((button, checked) -> {
            if (checked) cbRoleUser.setChecked(false);
        });
        cbRoleUser.setChecked(false);
        cbRoleGuardian.setChecked(false);
    }

    private void dressRoleCard(CheckBox box, DuckLooks.Look look, int backdropColor, int pipSize) {
        CharSequence text = box.getText();
        int newline = text.toString().indexOf('\n');
        if (newline > 0) {
            SpannableString styled = new SpannableString(text);
            styled.setSpan(new StyleSpan(Typeface.BOLD), 0, newline, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new RelativeSizeSpan(0.8f), newline + 1, text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new ForegroundColorSpan(Ui.color(this, R.color.ink_600)), newline + 1,
                    text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            box.setText(styled);
        }
        Drawable[] d = box.getCompoundDrawablesRelative(); // keep the tick box at the end
        box.setCompoundDrawablesRelativeWithIntrinsicBounds(
                new DuckDrawable(this, look, null, pipSize, false).backdrop(Ui.color(this, backdropColor)),
                d[1], d[2], d[3]);
    }

    private void setUpClickListeners() {
        btnSignIn.setOnClickListener(v -> signIn());
        btnCreate.setOnClickListener(v -> createAccount());
        btnForgot.setOnClickListener(v -> forgotPassword());
        findViewById(R.id.btnRetryRole).setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null || user.isAnonymous()) {
                recreate();
            } else {
                loadRoleAndContinue(user);
            }
        });
        findViewById(R.id.btnUseAnotherAccount).setOnClickListener(v -> {
            auth.signOut();
            AccountStore.clear(this);
            recreate();
        });
    }

    // ---------- sign in ----------

    private void signIn() {
        if (busy) return;
        clearErrors();
        String email = value(etSignInEmail).trim();
        String password = value(etSignInPassword);

        String error = AccountRules.emailError(email);
        if (error != null) { tilSignInEmail.setError(error); return; }
        error = AccountRules.passwordError(password);
        if (error != null) { tilSignInPassword.setError(error); return; }

        setBusy(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        setBusy(false);
                        showError("Sign in failed. Please try again.");
                        return;
                    }
                    loadRoleAndContinue(user);
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    showError(friendly(e));
                });
    }

    // ---------- create account ----------

    private void createAccount() {
        if (busy) return;
        clearErrors();
        String name = value(etName).trim();
        String email = value(etCreateEmail).trim();
        String password = value(etCreatePassword);
        String confirm = value(etConfirm);
        String phone = value(etPhone).trim();
        String role = selectedRole();

        String error = AccountRules.nameError(name);
        if (error != null) { tilName.setError(error); return; }
        error = AccountRules.emailError(email);
        if (error != null) { tilCreateEmail.setError(error); return; }
        error = AccountRules.passwordError(password);
        if (error != null) { tilCreatePassword.setError(error); return; }
        error = AccountRules.confirmError(password, confirm);
        if (error != null) { tilConfirm.setError(error); return; }
        error = AccountRules.phoneError(phone);
        if (error != null) { tilPhone.setError(error); return; }
        if (role == null) {
            showError("Choose how you will use SENSE+: assistance or guardian.");
            return;
        }

        final String finalName = name;
        final String finalPhone = phone;
        final String finalRole = role;
        final String finalEmail = email;

        setBusy(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        setBusy(false);
                        showError("Account creation failed. Please try again.");
                        return;
                    }
                    // Cache first, so this phone can start even if the server write is slow.
                    AccountStore.cache(this, user.getUid(), finalName, finalRole, finalEmail);
                    AccountStore.writeAccount(user.getUid(), finalName, finalRole,
                            finalEmail, finalPhone)
                            .addOnSuccessListener(unused -> {
                                setBusy(false);
                                goHome();
                            })
                            .addOnFailureListener(e -> {
                                setBusy(false);
                                Toast.makeText(this,
                                        "Signed in, but your profile could not be saved online. "
                                                + "Check your internet connection.",
                                        Toast.LENGTH_LONG).show();
                                goHome();
                            });
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    showError(friendly(e));
                });
    }

    private String selectedRole() {
        if (cbRoleGuardian.isChecked()) return Roles.GUARDIAN;
        if (cbRoleUser.isChecked()) return Roles.USER;
        return null;
    }

    // ---------- forgot password ----------

    private void forgotPassword() {
        String email = value(etSignInEmail).trim();
        String error = AccountRules.emailError(email);
        if (error != null) {
            tilSignInEmail.setError(error);
            return;
        }
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> Toast.makeText(this,
                        "If an account exists for that email, a reset link is on its way.",
                        Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> showError(friendly(e)));
    }

    // ---------- role loading / retry ----------

    private void loadRoleAndContinue(FirebaseUser user) {
        setBusy(true);
        final String uid = user.getUid();
        AccountStore.loadAccount(uid, new AccountStore.AccountCallback() {
            @Override public void onLoaded(String name, String role, String email) {
                if (!Roles.isValid(role)) {
                    setBusy(false);
                    showRetry("This account has no valid role yet. Try again, or use another account.");
                    return;
                }
                AccountStore.cache(LoginActivity.this, uid, name, role,
                        email != null ? email : user.getEmail());
                setBusy(false);
                goHome();
            }

            @Override public void onMissing() {
                if (repairFromCache(uid)) return;
                setBusy(false);
                showRetry("We could not find your SENSE+ profile. Check your internet and retry.");
            }

            @Override public void onError(Exception e) {
                if (repairFromCache(uid)) return;
                setBusy(false);
                showRetry("Could not load your profile. Check your internet and retry.");
            }
        });
    }

    /**
     * Offline start: a cached role that belongs to this uid is enough. The role is never
     * guessed. The account document is re-created from the cache as a best effort.
     */
    private boolean repairFromCache(String uid) {
        if (!uid.equals(AccountStore.uid(this)) || !Roles.isValid(AccountStore.role(this))) {
            return false;
        }
        AccountStore.writeAccount(uid, AccountStore.name(this), AccountStore.role(this),
                AccountStore.email(this), "");
        goHome();
        return true;
    }

    private void showRetry(String message) {
        tabs.setVisibility(View.GONE);
        signInContainer.setVisibility(View.GONE);
        createContainer.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        retryBox.setVisibility(View.VISIBLE);
        tvRetryMessage.setText(message);
        tvAuthError.setVisibility(View.GONE);
    }

    // ---------- helpers ----------

    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        btnSignIn.setEnabled(!value);
        btnCreate.setEnabled(!value);
        btnForgot.setEnabled(!value);
    }

    private void showError(String message) {
        tvAuthError.setText(message);
        tvAuthError.setVisibility(View.VISIBLE);
    }

    private void clearErrors() {
        tvAuthError.setText("");
        tvAuthError.setVisibility(View.GONE);
        tilSignInEmail.setError(null);
        tilSignInPassword.setError(null);
        tilName.setError(null);
        tilCreateEmail.setError(null);
        tilCreatePassword.setError(null);
        tilConfirm.setError(null);
        tilPhone.setError(null);
    }

    private static String value(TextInputEditText field) {
        CharSequence text = field.getText();
        return text == null ? "" : text.toString();
    }

    /**
     * Firebase returns one generic invalid-credentials error when email enumeration
     * protection is on, so a wrong email and a wrong password must read the same.
     */
    static String friendly(Throwable error) {
        if (error instanceof FirebaseNetworkException)
            return "No internet connection. Check your connection and try again.";
        if (error instanceof FirebaseAuthInvalidCredentialsException)
            return "Email or password is wrong.";
        if (error instanceof FirebaseAuthInvalidUserException)
            return "Email or password is wrong.";
        if (error instanceof FirebaseAuthUserCollisionException)
            return "An account already exists for this email.";
        if (error instanceof FirebaseAuthWeakPasswordException)
            return "Password must be at least 6 characters.";
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if ("ERROR_TOO_MANY_REQUESTS".equals(code))
                return "Too many attempts. Wait a minute and try again.";
            if ("ERROR_NETWORK_REQUEST_FAILED".equals(code))
                return "No internet connection. Check your connection and try again.";
        }
        return "Something went wrong. Please try again.";
    }
}
