package com.senseplus.sense;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class CloudHelper {

    private static final String COLLECTION = "sense_users";
    private static final String USER_ID = "demoUser";   // shared id so the guardian phone can find it

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public CloudHelper() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // Sign in anonymously — no login screen needed
    public void connect() {
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously();
        }
    }

    public void updateLocation(double lat, double lon, String mapsLink, String address) {
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
        data.put("battery", battery);
        data.put("updatedAt", System.currentTimeMillis());
        push(data);
    }

    // merge = update only these fields, keep the rest of the document
    private void push(Map<String, Object> data) {
        db.collection(COLLECTION).document(USER_ID).set(data, SetOptions.merge());
    }
}