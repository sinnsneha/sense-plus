package com.senseplus.sense;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GuardianActivity extends AppCompatActivity {

    private TextView tvGStatus, tvGAddress, tvGCoords, tvGBattery, tvGUpdated;
    private Button btnOpenMaps, btnStop;
    private String currentMapsLink = null;

    private FirebaseFirestore db;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            db.collection("sense_users").document("demoUser").get(Source.SERVER)
                    .addOnSuccessListener(doc -> { if (doc != null && doc.exists()) showData(doc); });
            pollHandler.postDelayed(this, 2000);   // dashboard refresh every 2s
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        tvGStatus = findViewById(R.id.tvGStatus);
        tvGAddress = findViewById(R.id.tvGAddress);
        tvGCoords = findViewById(R.id.tvGCoords);
        tvGBattery = findViewById(R.id.tvGBattery);
        tvGUpdated = findViewById(R.id.tvGUpdated);
        btnOpenMaps = findViewById(R.id.btnOpenMaps);
        btnStop = findViewById(R.id.btnStop);

        btnOpenMaps.setOnClickListener(v -> {
            if (currentMapsLink != null)
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentMapsLink)));
        });

        // Ask for notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
        }

        // Start the background monitoring service (keeps listening when app is off-screen)
        ContextCompat.startForegroundService(this, new Intent(this, GuardianService.class));

        // Stop button
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, GuardianService.class));
            btnStop.setText("MONITORING STOPPED");
        });

        db = FirebaseFirestore.getInstance();
        FirebaseAuth.getInstance().signInAnonymously();
    }

    @Override
    protected void onStart() {
        super.onStart();
        pollHandler.post(pollTask);   // live dashboard polling (reliable on any network)
    }

    @Override
    protected void onStop() {
        super.onStop();
        pollHandler.removeCallbacks(pollTask);
    }

    private void showData(DocumentSnapshot doc) {
        String status = doc.getString("status");
        String address = doc.getString("address");
        Double lat = doc.getDouble("latitude");
        Double lon = doc.getDouble("longitude");
        Long battery = doc.getLong("battery");
        Long updatedAt = doc.getLong("updatedAt");
        currentMapsLink = doc.getString("mapsLink");

        if (status == null) status = "UNKNOWN";
        tvGStatus.setText(status);
        tvGStatus.setTextColor(colorFor(status));

        tvGAddress.setText("Address: " + (address != null ? address : "—"));
        if (lat != null && lon != null)
            tvGCoords.setText(String.format(Locale.US, "Coordinates: %.5f, %.5f", lat, lon));
        tvGBattery.setText("Battery: " + (battery != null ? battery + "%" : "—"));
        if (updatedAt != null) {
            String time = new SimpleDateFormat("dd MMM, hh:mm:ss a", Locale.US).format(new Date(updatedAt));
            tvGUpdated.setText("Last update: " + time);
        }
    }

    private int colorFor(String status) {
        if (status.contains("EMERGENCY")) return Color.parseColor("#C62828");
        if (status.contains("ATTENTION")) return Color.parseColor("#E65100");
        return Color.parseColor("#2E7D32");
    }
}