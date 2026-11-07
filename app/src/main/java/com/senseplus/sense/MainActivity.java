package com.senseplus.sense;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String STATUS_ACTION = "com.senseplus.sense.STATUS";

    private TextView tvStatus;
    private Button btnToggle;
    private boolean monitoring = false;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateStatusUi(intent.getStringExtra("status"), intent.getStringExtra("info"));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnToggle = findViewById(R.id.btnToggle);
        Button btnHelp = findViewById(R.id.btnHelp);
        Button btnOk = findViewById(R.id.btnOk);

        requestNeededPermissions();

        btnToggle.setOnClickListener(v -> { if (!monitoring) startMonitoring(); else stopMonitoring(); });
        btnHelp.setOnClickListener(v -> sendAction(UserMonitorService.ACTION_HELP));
        btnOk.setOnClickListener(v -> sendAction(UserMonitorService.ACTION_IM_OK));
    }

    private void startMonitoring() {
        ContextCompat.startForegroundService(this,
                new Intent(this, UserMonitorService.class).setAction(UserMonitorService.ACTION_START));
        monitoring = true;
        btnToggle.setText("STOP MONITORING");
        updateStatusUi("NORMAL", "Monitoring (normal)");
    }

    private void stopMonitoring() {
        stopService(new Intent(this, UserMonitorService.class));
        monitoring = false;
        btnToggle.setText("START MONITORING");
        tvStatus.setText("Status: monitoring OFF");
        tvStatus.setTextColor(Color.parseColor("#555555"));
    }

    private void sendAction(String action) {
        if (!monitoring) return;
        ContextCompat.startForegroundService(this,
                new Intent(this, UserMonitorService.class).setAction(action));
    }

    private void updateStatusUi(String status, String info) {
        if (status == null) status = "";
        int color = Color.parseColor("#1F4E79");
        if (status.contains("EMERGENCY")) color = Color.parseColor("#C62828");
        else if (status.contains("ATTENTION")) color = Color.parseColor("#E65100");
        tvStatus.setTextColor(color);
        tvStatus.setText("Status: " + (info != null ? info : status));
    }

    private void requestNeededPermissions() {
        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 101);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(STATUS_ACTION);
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) { }
    }
}