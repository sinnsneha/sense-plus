package com.senseplus.sense;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends the guardian's "I'm on my way" reply (segment 25): the one place that writes
 * users/{linkedUid}.ack. The dashboard button and the notification action both call it.
 *
 * Writes ONLY the ack field (firestore.rules lets a linked guardian touch just 'ack' and 'home'):
 *   ack = { by: this guardian's uid, name: this guardian's name, time: server timestamp }
 */
final class GuardianAck {

    /**
     * How long to wait for the server to confirm (ms). Firestore keeps a write made while offline
     * in a queue and never reports a failure for it, so "no network" would otherwise look like
     * "still sending" forever. After this the caller is told it failed, and may try again.
     * 8 s: longer than a slow mobile round trip, shorter than the ~10 s a broadcast receiver gets.
     */
    static final long CONFIRM_TIMEOUT_MS = 8_000L;

    /** Exactly one of these is called, on the main thread. */
    interface Callback {
        void onSent();
        void onFailed();
    }

    private GuardianAck() {
    }

    static void send(Context context, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        String linkedUid = AccountStore.linkedUid(context);
        if (me == null || me.isAnonymous() || linkedUid == null) {
            main.post(callback::onFailed); // nobody to tell: not signed in, or not linked
            return;
        }

        final AtomicBoolean finished = new AtomicBoolean(false);
        final Runnable timeout = () -> {
            if (finished.compareAndSet(false, true)) callback.onFailed();
        };
        main.postDelayed(timeout, CONFIRM_TIMEOUT_MS);

        Map<String, Object> ack = new HashMap<>();
        ack.put("by", me.getUid());
        ack.put("name", AccountStore.name(context));
        ack.put("time", FieldValue.serverTimestamp());

        // update() with one field path: only 'ack' changes, which is all the rules allow.
        // A write confirmed after the timeout still lands; the dashboard then shows "Sent"
        // because it reads ack.by from the document.
        FirestorePaths.userDoc(linkedUid).update("ack", ack)
                .addOnSuccessListener(unused -> {
                    if (finished.compareAndSet(false, true)) {
                        main.removeCallbacks(timeout);
                        callback.onSent();
                    }
                })
                .addOnFailureListener(e -> {
                    if (finished.compareAndSet(false, true)) {
                        main.removeCallbacks(timeout);
                        callback.onFailed();
                    }
                });
    }
}
