package com.senseplus.sense;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guardian phone (segment 23): link to the person you look after.
 *
 * 1. The guardian types the 6-digit code the user's phone shows (pairCodes/{code}).
 * 2. The app writes users/{userUid}/linkRequests/{myUid}; the rules only allow that with a valid,
 *    unexpired code issued by that user.
 * 3. It waits while the user approves on their phone. Until then reading users/{userUid} is
 *    refused (PERMISSION_DENIED); once the user adds this guardian, the read succeeds.
 * 4. The link is saved (prefs + accounts/{me}) and GuardianService starts.
 */
public class LinkActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATIONS = 303;

    private enum State { ENTER, WAITING, LINKED }

    private TextInputLayout tilCode, tilPhone;
    private TextInputEditText etCode, etPhone;
    private View groupEnter, groupWaiting, groupLinked, bottomBar;
    private TextView tvWaiting, tvLinkedTitle, tvLinkError;
    private Button btnLink;
    private DuckView pip;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable approvalPoll;

    private String myUid;
    private String pendingUserUid, pendingUserName;
    private long waitStartedMs;

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
        myUid = user.getUid();

        setContentView(R.layout.activity_link);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.bottomBar));
        Ui.bindBack(this);
        findViewById(R.id.linkHero).setBackground(Gradients.heroMint(this).wave());

        tilCode = findViewById(R.id.tilCode);
        tilPhone = findViewById(R.id.tilPhone);
        etCode = findViewById(R.id.etCode);
        etPhone = findViewById(R.id.etPhone);
        groupEnter = findViewById(R.id.groupEnter);
        groupWaiting = findViewById(R.id.groupWaiting);
        groupLinked = findViewById(R.id.groupLinked);
        bottomBar = findViewById(R.id.bottomBar);
        tvWaiting = findViewById(R.id.tvWaiting);
        tvLinkedTitle = findViewById(R.id.tvLinkedTitle);
        tvLinkError = findViewById(R.id.tvLinkError);
        btnLink = findViewById(R.id.btnLink);
        pip = findViewById(R.id.pipLink);

        String phone = AccountStore.phone(this);
        if (phone != null && !phone.isEmpty()) etPhone.setText(phone);

        btnLink.setOnClickListener(v -> startLink());
        findViewById(R.id.btnCancelWait).setOnClickListener(v -> cancelWaiting());
        findViewById(R.id.btnOpenDashboard).setOnClickListener(v -> {
            startActivity(new Intent(this, GuardianActivity.class));
            finish();
        });
        findViewById(R.id.btnUnlink).setOnClickListener(v -> confirmUnlink());
        Ui.pressFeedback(btnLink, findViewById(R.id.btnCancelWait),
                findViewById(R.id.btnOpenDashboard), findViewById(R.id.btnUnlink));

        render(AccountStore.isLinked(this) ? State.LINKED : State.ENTER);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    // ---------- states ----------

    private void render(State state) {
        groupEnter.setVisibility(state == State.ENTER ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(state == State.ENTER ? View.VISIBLE : View.GONE);
        groupWaiting.setVisibility(state == State.WAITING ? View.VISIBLE : View.GONE);
        groupLinked.setVisibility(state == State.LINKED ? View.VISIBLE : View.GONE);
        setBusy(false);

        switch (state) {
            case WAITING:
                tvWaiting.setText(getString(R.string.link_waiting, displayName(pendingUserName)));
                pip.setExpression(DuckLooks.Expression.IDLE, true);
                break;
            case LINKED:
                tvLinkedTitle.setText(getString(R.string.link_linked_title,
                        displayName(AccountStore.linkedName(this))));
                pip.setExpression(DuckLooks.Expression.HAPPY, true);
                break;
            case ENTER:
            default:
                pip.setExpression(DuckLooks.Expression.IDLE, false);
                break;
        }
    }

    private void setBusy(boolean busy) {
        btnLink.setEnabled(!busy);
        etCode.setEnabled(!busy);
        etPhone.setEnabled(!busy);
    }

    private void showError(int message) {
        tvLinkError.setText(message);
        tvLinkError.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        tvLinkError.setText(message);
        tvLinkError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvLinkError.setVisibility(View.GONE);
        tilCode.setError(null);
        tilPhone.setError(null);
    }

    private String displayName(String name) {
        return name == null || name.trim().isEmpty() ? getString(R.string.link_the_user) : name.trim();
    }

    // ---------- 1 + 2: check the code, then ask to link ----------

    private void startLink() {
        hideError();
        String code = PairCode.normalize(etCode.getText() != null ? etCode.getText().toString() : null);
        if (code == null) {
            tilCode.setError(getString(R.string.link_error_format));
            return;
        }
        final String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String phoneError = AccountRules.phoneError(phone);
        if (phoneError != null) {
            tilPhone.setError(phoneError);
            return;
        }

        setBusy(true);
        FirestorePaths.pairCode(code).get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    String userUid = doc != null ? doc.getString("userUid") : null;
                    Timestamp expires = doc != null ? doc.getTimestamp("expiresAt") : null;
                    if (doc == null || !doc.exists() || userUid == null || expires == null
                            || PairingRules.isExpired(System.currentTimeMillis(),
                                    expires.toDate().getTime())) {
                        setBusy(false);
                        showError(R.string.link_error_code);
                        return;
                    }
                    sendRequest(userUid, doc.getString("userName"), code, phone);
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    // The rules refuse expired or missing codes: same message as a wrong code.
                    showError(isPermissionDenied(e) ? R.string.link_error_code : R.string.link_error_network);
                });
    }

    private void sendRequest(String userUid, String userName, String code, String phone) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", AccountStore.name(this));
        request.put("phone", phone);
        request.put("code", code);
        request.put("createdAt", FieldValue.serverTimestamp());

        FirestorePaths.linkRequest(userUid, myUid).set(request)
                .addOnSuccessListener(v -> {
                    pendingUserUid = userUid;
                    pendingUserName = userName;
                    waitStartedMs = System.currentTimeMillis();
                    render(State.WAITING);
                    startApprovalPoll();
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    showError(isPermissionDenied(e) ? R.string.link_error_code : R.string.link_error_network);
                });
    }

    // ---------- 3: wait for the user's approval ----------

    private void startApprovalPoll() {
        handler.removeCallbacksAndMessages(null);
        approvalPoll = new Runnable() {
            @Override public void run() {
                if (System.currentTimeMillis() - waitStartedMs > PairingRules.CODE_TTL_MS) {
                    stopWaiting();
                    render(State.ENTER);
                    showError(R.string.link_timeout);
                    return;
                }
                checkApproval();
                handler.postDelayed(this, PairingRules.PAIRING_POLL_MS);
            }
        };
        handler.post(approvalPoll);
    }

    /** Readable + lists me = approved. Refused = still waiting, unless the request was removed. */
    private void checkApproval() {
        final String userUid = pendingUserUid;
        if (userUid == null) return;
        FirestorePaths.userDoc(userUid).get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (listsMe(doc != null ? doc.get("guardians") : null)) onApproved();
                    else checkStillRequested(userUid);
                })
                .addOnFailureListener(e -> {
                    if (isPermissionDenied(e)) checkStillRequested(userUid);
                    // Network trouble: try again on the next tick.
                });
    }

    /**
     * The user deletes the request both when approving and when declining. If it is gone, read
     * the user's document once more before calling it a decline, so an approval that is still
     * on its way is not mistaken for one.
     */
    private void checkStillRequested(String userUid) {
        FirestorePaths.linkRequest(userUid, myUid).get(Source.SERVER)
                .addOnSuccessListener(req -> {
                    if (req != null && req.exists()) return; // still pending
                    FirestorePaths.userDoc(userUid).get(Source.SERVER)
                            .addOnSuccessListener(doc -> {
                                if (listsMe(doc != null ? doc.get("guardians") : null)) onApproved();
                                else onDeclined();
                            })
                            .addOnFailureListener(e -> {
                                if (isPermissionDenied(e)) onDeclined();
                            });
                });
    }

    private boolean listsMe(Object guardians) {
        if (!(guardians instanceof List)) return false;
        for (Object g : (List<?>) guardians) {
            if (myUid.equals(g)) return true;
        }
        return false;
    }

    private void onDeclined() {
        if (pendingUserUid == null) return;
        String who = displayName(pendingUserName);
        stopWaiting();
        render(State.ENTER);
        showError(getString(R.string.link_declined, who));
    }

    private void cancelWaiting() {
        if (pendingUserUid != null) FirestorePaths.linkRequest(pendingUserUid, myUid).delete();
        stopWaiting();
        render(State.ENTER);
    }

    private void stopWaiting() {
        handler.removeCallbacksAndMessages(null);
        pendingUserUid = null;
    }

    // ---------- 4: linked ----------

    private void onApproved() {
        if (pendingUserUid == null) return; // already handled
        String userUid = pendingUserUid;
        String name = displayName(pendingUserName);
        stopWaiting();

        AccountStore.saveLink(this, userUid, name);
        startGuardianAlerts();

        String done = getString(R.string.link_done, name);
        tvWaiting.announceForAccessibility(done);
        Toast.makeText(this, done, Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, GuardianActivity.class));
        finish();
    }

    private void startGuardianAlerts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
        ContextCompat.startForegroundService(this, new Intent(this, GuardianService.class));
    }

    private void confirmUnlink() {
        String who = displayName(AccountStore.linkedName(this));
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.link_unlink_title, who))
                .setMessage(getString(R.string.link_unlink_body, who))
                .setNegativeButton(R.string.link_cancel, null)
                .setPositiveButton(R.string.link_unlink, (d, w) -> {
                    AccountStore.clearLink(this);
                    stopService(new Intent(this, GuardianService.class));
                    render(State.ENTER);
                })
                .show();
    }

    private static boolean isPermissionDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }
}
