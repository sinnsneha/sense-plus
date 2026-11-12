package com.senseplus.sense;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * User phone (segment 23): "My guardians". Shows a short-lived code for a new guardian, asks the
 * user to approve each request, and lists (and removes) the guardians who get the user's alerts.
 * Nothing about a guardian is trusted until the user taps Approve.
 */
public class GuardiansActivity extends AppCompatActivity {

    /** Attempts at a fresh code before giving up (a taken code is refused by the rules). */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private LinearLayout guardiansContainer;
    private View codeCard;
    private TextView tvCode, tvCountdown, tvPairStatus, tvNoGuardians;
    private DuckView pip;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SecureRandom random = new SecureRandom();
    private TextToSpeech tts;

    private String myUid;
    private String activeCode;
    private long codeExpiresAt;
    // Requests already shown in a dialog, so one request is never asked about twice.
    private final Set<String> promptedRequests = new HashSet<>();

    private final Runnable requestPoll = new Runnable() {
        @Override public void run() {
            pollRequests();
            handler.postDelayed(this, PairingRules.PAIRING_POLL_MS);
        }
    };

    private final Runnable countdownTick = new Runnable() {
        @Override public void run() {
            if (activeCode == null) return;
            long now = System.currentTimeMillis();
            if (PairingRules.isExpired(now, codeExpiresAt)) {
                expireCode();
                return;
            }
            tvCountdown.setText(getString(R.string.guardians_expires_in,
                    PairingRules.countdown(PairingRules.secondsLeft(now, codeExpiresAt))));
            handler.postDelayed(this, 1000);
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
        myUid = user.getUid();

        setContentView(R.layout.activity_guardians);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.bottomBar));
        Ui.bindBack(this);
        findViewById(R.id.guardiansHero).setBackground(Gradients.heroLavender(this).wave());

        guardiansContainer = findViewById(R.id.guardiansContainer);
        codeCard = findViewById(R.id.codeCard);
        tvCode = findViewById(R.id.tvCode);
        tvCountdown = findViewById(R.id.tvCountdown);
        tvPairStatus = findViewById(R.id.tvPairStatus);
        tvNoGuardians = findViewById(R.id.tvNoGuardians);
        pip = findViewById(R.id.pipGuardians);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        findViewById(R.id.btnAddGuardian).setOnClickListener(v -> createCode(1));
        findViewById(R.id.btnSpeakCode).setOnClickListener(v -> speakCode());
        findViewById(R.id.btnCancelCode).setOnClickListener(v -> cancelCode());
        Ui.pressFeedback(findViewById(R.id.btnAddGuardian), findViewById(R.id.btnSpeakCode),
                findViewById(R.id.btnCancelCode));
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadGuardians();
        handler.post(requestPoll);
        if (activeCode != null) handler.post(countdownTick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(requestPoll);
        handler.removeCallbacks(countdownTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        // Leaving the screen for good: don't leave a code lying around.
        if (isFinishing() && activeCode != null) FirestorePaths.pairCode(activeCode).delete();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    // ---------- guardian list ----------

    private void loadGuardians() {
        FirestorePaths.userDoc(myUid).get(Source.SERVER)
                .addOnSuccessListener(this::renderGuardians)
                .addOnFailureListener(e -> showStatus(getString(R.string.guardians_error_network)));
    }

    @SuppressWarnings("unchecked")
    private void renderGuardians(DocumentSnapshot doc) {
        guardiansContainer.removeAllViews();
        List<Object> guardians = doc != null && doc.exists() && doc.get("guardians") instanceof List
                ? (List<Object>) doc.get("guardians") : null;
        Map<String, Object> info = doc != null && doc.exists() && doc.get("guardianInfo") instanceof Map
                ? (Map<String, Object>) doc.get("guardianInfo") : null;

        int count = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        if (guardians != null) {
            for (Object g : guardians) {
                if (!(g instanceof String)) continue;
                final String gid = (String) g;
                Map<String, Object> entry = info != null && info.get(gid) instanceof Map
                        ? (Map<String, Object>) info.get(gid) : null;
                final String name = textOr(entry != null ? entry.get("name") : null,
                        getString(R.string.guardians_unnamed));
                String phone = textOr(entry != null ? entry.get("phone") : null,
                        getString(R.string.guardians_no_phone));

                View row = inflater.inflate(R.layout.item_guardian_row, guardiansContainer, false);
                ((TextView) row.findViewById(R.id.rowName)).setText(name);
                ((TextView) row.findViewById(R.id.rowPhone)).setText(phone);
                row.findViewById(R.id.rowText).setContentDescription(
                        getString(R.string.guardians_row_cd, name, phone));
                View remove = row.findViewById(R.id.rowRemove);
                remove.setContentDescription(getString(R.string.guardians_remove_title, name));
                remove.setOnClickListener(v -> confirmRemove(gid, name));
                Ui.pressFeedback(remove);
                guardiansContainer.addView(row);
                count++;
            }
        }
        tvNoGuardians.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private static String textOr(Object value, String fallback) {
        return value instanceof String && !((String) value).trim().isEmpty()
                ? ((String) value).trim() : fallback;
    }

    private void confirmRemove(String gid, String name) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.guardians_remove_title, name))
                .setMessage(R.string.guardians_remove_body)
                .setNegativeButton(R.string.link_cancel, null)
                .setPositiveButton(R.string.guardians_remove, (d, w) ->
                        FirestorePaths.userDoc(myUid)
                                .update(FieldPath.of("guardians"), FieldValue.arrayRemove(gid),
                                        FieldPath.of("guardianInfo", gid), FieldValue.delete())
                                .addOnSuccessListener(v -> {
                                    Toast.makeText(this, getString(R.string.guardians_removed, name),
                                            Toast.LENGTH_SHORT).show();
                                    loadGuardians();
                                })
                                .addOnFailureListener(e ->
                                        showStatus(getString(R.string.guardians_error_network))))
                .show();
    }

    // ---------- pairing code ----------

    /** A fresh 6-digit code. The rules refuse overwriting someone else's code: then try another. */
    private void createCode(int attempt) {
        hideStatus();
        if (activeCode != null) FirestorePaths.pairCode(activeCode).delete(); // one code at a time
        activeCode = null;

        final String code = PairCode.generate(random);
        final long expiresAt = System.currentTimeMillis() + PairingRules.CODE_TTL_MS;
        Map<String, Object> data = new HashMap<>();
        data.put("userUid", myUid);
        data.put("userName", AccountStore.name(this));
        data.put("expiresAt", new Timestamp(new Date(expiresAt)));

        FirestorePaths.pairCode(code).set(data)
                .addOnSuccessListener(v -> {
                    activeCode = code;
                    codeExpiresAt = expiresAt;
                    showCode(code);
                })
                .addOnFailureListener(e -> {
                    if (isPermissionDenied(e) && attempt < MAX_CODE_ATTEMPTS) createCode(attempt + 1);
                    else showStatus(getString(isPermissionDenied(e)
                            ? R.string.guardians_error_code : R.string.guardians_error_network));
                });
    }

    private void showCode(String code) {
        tvCode.setText(PairCode.display(code));
        tvCode.setContentDescription(getString(R.string.guardians_code_cd, PairCode.spoken(code)));
        codeCard.setVisibility(View.VISIBLE);
        pip.setExpression(DuckLooks.Expression.HAPPY, true);
        handler.removeCallbacks(countdownTick);
        handler.post(countdownTick);
        speakCode();
    }

    private void speakCode() {
        if (activeCode == null || tts == null) return;
        tts.speak(getString(R.string.guardians_code_spoken, PairCode.spoken(activeCode)),
                TextToSpeech.QUEUE_FLUSH, null, "pair-code");
    }

    private void cancelCode() {
        if (activeCode != null) FirestorePaths.pairCode(activeCode).delete();
        clearCode();
    }

    private void expireCode() {
        if (activeCode != null) FirestorePaths.pairCode(activeCode).delete();
        clearCode();
        showStatus(getString(R.string.guardians_code_expired));
    }

    private void clearCode() {
        activeCode = null;
        handler.removeCallbacks(countdownTick);
        codeCard.setVisibility(View.GONE);
        pip.setExpression(DuckLooks.Expression.IDLE, false);
    }

    // ---------- requests from guardians ----------

    private void pollRequests() {
        FirestorePaths.linkRequests(myUid).get(Source.SERVER)
                .addOnSuccessListener(snap -> {
                    if (snap == null) return;
                    for (DocumentSnapshot req : snap.getDocuments()) {
                        String gid = req.getId();
                        if (promptedRequests.add(gid)) {
                            askToApprove(gid, textOr(req.get("name"), getString(R.string.guardians_unnamed)),
                                    textOr(req.get("phone"), ""));
                        }
                    }
                });
    }

    private void askToApprove(String gid, String name, String phone) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.guardians_approve_title, name))
                .setMessage(phone.isEmpty() ? getString(R.string.guardians_approve_body)
                        : getString(R.string.guardians_approve_body_phone, phone))
                .setCancelable(false)
                .setNegativeButton(R.string.guardians_decline, (d, w) ->
                        FirestorePaths.linkRequest(myUid, gid).delete())
                .setPositiveButton(R.string.guardians_approve, (d, w) -> approve(gid, name, phone))
                .show();
    }

    /** Adds the guardian to users/{me}: from now on the rules let them read my status. */
    private void approve(String gid, String name, String phone) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("name", name);
        entry.put("phone", phone);
        Map<String, Object> info = new HashMap<>();
        info.put(gid, entry);

        Map<String, Object> data = new HashMap<>();
        data.put("guardians", FieldValue.arrayUnion(gid));
        data.put("guardianInfo", info);
        // My own name and phone, so the guardian sees who this is even before monitoring starts.
        data.put("name", AccountStore.name(this));
        data.put("phone", AccountStore.phone(this));

        FirestorePaths.userDoc(myUid).set(data, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    FirestorePaths.linkRequest(myUid, gid).delete();
                    if (activeCode != null) FirestorePaths.pairCode(activeCode).delete();
                    clearCode();
                    String done = getString(R.string.guardians_now_guardian, name);
                    showStatus(done);
                    if (tts != null) tts.speak(done, TextToSpeech.QUEUE_FLUSH, null, "pair-done");
                    loadGuardians();
                })
                .addOnFailureListener(e -> {
                    promptedRequests.remove(gid); // ask again on the next poll
                    showStatus(getString(R.string.guardians_error_network));
                });
    }

    // ---------- helpers ----------

    private void showStatus(String text) {
        tvPairStatus.setText(text);
        tvPairStatus.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        tvPairStatus.setVisibility(View.GONE);
    }

    private static boolean isPermissionDenied(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
    }
}
