package com.senseplus.sense;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

public class GuardianService extends Service {

    public static final String ONGOING_CHANNEL = "guardian_ongoing";
    public static final String ALERT_CHANNEL = "guardian_alert_v2";
    private static final int ONGOING_ID = 1;
    private static final int ALERT_ID = 2;
    private static final int CLEAR_ID = 3;

    private FirebaseFirestore db;
    private String lastStatus = "";

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            db.collection("sense_users").document("demoUser").get(Source.SERVER)
                    .addOnSuccessListener(doc -> {
                        if (doc != null && doc.exists())
                            checkStatus(doc.getString("status"), doc.getString("address"));
                    });
            pollHandler.postDelayed(this, 2000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels();
        db = FirebaseFirestore.getInstance();
        FirebaseAuth.getInstance().signInAnonymously();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification ongoing = new NotificationCompat.Builder(this, ONGOING_CHANNEL)
                .setContentTitle("SENSE+ Guardian")
                .setContentText("Monitoring for emergencies")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ONGOING_ID, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(ONGOING_ID, ongoing);
        }

        pollHandler.removeCallbacks(pollTask);
        pollHandler.post(pollTask);
        return START_STICKY;
    }

    private void checkStatus(String status, String address) {
        if (status == null) return;
        boolean isEmergency  = status.contains("EMERGENCY");
        boolean wasEmergency = lastStatus.contains("EMERGENCY");

        if (isEmergency && !wasEmergency) {
            sendAlert(address != null ? address : "location unknown");
        } else if (!isEmergency && wasEmergency) {
            sendAllClear();
        }
        lastStatus = status;
    }

    private void sendAlert(String address) {
        Notification alert = new NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setContentTitle("🔴 POSSIBLE EMERGENCY")
                .setContentText("Your dependent may need help. Near: " + address)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setColor(Color.parseColor("#C62828"))
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ALERT_ID, alert);
    }

    private void sendAllClear() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.cancel(ALERT_ID);
        Notification ok = new NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setContentTitle("🟢 All clear")
                .setContentText("The user pressed I'M OK — they are safe now.")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setColor(Color.parseColor("#2E7D32"))
                .setColorized(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .build();
        nm.notify(CLEAR_ID, ok);
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;

        nm.createNotificationChannel(new NotificationChannel(
                ONGOING_CHANNEL, "Monitoring", NotificationManager.IMPORTANCE_LOW));

        NotificationChannel alert = new NotificationChannel(
                ALERT_CHANNEL, "Emergency alerts", NotificationManager.IMPORTANCE_HIGH);
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        alert.enableLights(true);
        alert.setLightColor(Color.RED);
        nm.createNotificationChannel(alert);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        pollHandler.removeCallbacks(pollTask);
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}