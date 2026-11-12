package com.senseplus.sense;

import android.util.Log;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Writes the signed-in user's live status to their own document, users/{uid} (segment 23).
 * Before segment 23 every phone shared one document; now only this user and the guardians they
 * approved can read it (firestore.rules).
 */
public class CloudHelper {

    private static final String TAG = "SenseCloud";

    /** Null when nobody is signed in: every write is then skipped instead of crashing. */
    private final String uid;
    private final DocumentReference userDoc;

    // Last known location, cached from updateLocation so an event can carry it.
    // Written from the location thread, read from the main thread.
    private volatile Double lastLat;
    private volatile Double lastLon;

    public CloudHelper(String uid) {
        this.uid = uid;
        this.userDoc = uid != null ? FirestorePaths.userDoc(uid) : null;
    }

    /** The user's name and phone, so a guardian sees who this is and can call them. */
    public void updateProfile(String name, String phone) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name == null ? "" : name);
        data.put("phone", phone == null ? "" : phone);
        push(data);
    }

    public void updateLocation(double lat, double lon, String mapsLink, String address) {
        lastLat = lat;
        lastLon = lon;
        Map<String, Object> data = new HashMap<>();
        data.put("latitude", lat);
        data.put("longitude", lon);
        data.put("mapsLink", mapsLink);
        data.put("address", address);
        data.put("updatedAt", System.currentTimeMillis());
        push(data);
    }

    public void updateStatus(String status, String lastEvent, int battery) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("lastEvent", lastEvent);
        putBattery(data, battery);
        data.put("updatedAt", System.currentTimeMillis());
        push(data);
    }

    /**
     * Appends one safety event to users/{uid}/events.
     * lat/lon are the last fix cached by updateLocation, or null if unknown.
     */
    public void logEvent(String type, String detail) {
        if (uid == null) return;
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("detail", detail);
        data.put("lat", lastLat);
        data.put("lon", lastLon);
        data.put("time", FieldValue.serverTimestamp());
        data.put("clientTime", System.currentTimeMillis());

        FirestorePaths.events(uid).add(data)
                .addOnFailureListener(e ->
                        Log.w(TAG, "Failed to log event " + type + ": " + e.getMessage(), e));
    }

    /** Writes the monitoring flag plus a fresh server-side lastSeen (heartbeat). */
    public void updateMonitoring(boolean monitoring) {
        updateMonitoring(monitoring, -1);
    }

    /**
     * Heartbeat that also carries the battery level, so the guardian's figure is never older
     * than one heartbeat. A negative level means "unknown" and is not written.
     */
    public void updateMonitoring(boolean monitoring, int battery) {
        Map<String, Object> data = new HashMap<>();
        data.put("monitoring", monitoring);
        putBattery(data, battery);
        push(data);
    }

    /**
     * Battery plus the server time it was written. lastSeen cannot tell the guardian how old the
     * battery figure is, because location writes refresh lastSeen without a battery value.
     */
    private void putBattery(Map<String, Object> data, int battery) {
        if (battery < 0) return;
        data.put("battery", battery);
        data.put("batteryAt", FieldValue.serverTimestamp());
    }

    // merge = update only these fields, keep the rest of the document (guardians, home, ack…)
    private void push(Map<String, Object> data) {
        if (userDoc == null) return;
        // Server time, so the guardian compares its own clock with the server's.
        data.put("lastSeen", FieldValue.serverTimestamp());
        userDoc.set(data, SetOptions.merge())
                .addOnFailureListener(e -> Log.w(TAG, "Status write failed: " + e.getMessage(), e));
    }
}
