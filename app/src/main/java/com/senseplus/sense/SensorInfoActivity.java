package com.senseplus.sense;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public class SensorInfoActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, lightSensor, proximity, rotationVector;
    private TextView tvAccel, tvGyro, tvLight, tvProx, tvCompass, tvSensorList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_info);
        getWindow().setStatusBarColor(Color.parseColor("#0D2B45"));

        tvAccel = findViewById(R.id.tvAccel);
        tvGyro = findViewById(R.id.tvGyro);
        tvLight = findViewById(R.id.tvLight);
        tvProx = findViewById(R.id.tvProx);
        tvCompass = findViewById(R.id.tvCompass);
        tvSensorList = findViewById(R.id.tvSensorList);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope      = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        lightSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximity      = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        showAvailableSensors();
    }

    private void showAvailableSensors() {
        List<Sensor> all = sensorManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder("This phone has " + all.size() + " sensors:\n\n");
        for (Sensor s : all) sb.append("•  ").append(s.getName()).append("\n");
        tvSensorList.setText(sb.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null)  sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (gyroscope != null)      sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        if (lightSensor != null)    sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        if (proximity != null)      sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_UI);
        if (rotationVector != null) sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                tvAccel.setText(String.format(Locale.US, "Accelerometer (m/s²):  X %.2f   Y %.2f   Z %.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_GYROSCOPE:
                tvGyro.setText(String.format(Locale.US, "Gyroscope (rad/s):  X %.2f   Y %.2f   Z %.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_LIGHT:
                tvLight.setText(String.format(Locale.US, "Light:  %.1f lux", event.values[0]));
                break;
            case Sensor.TYPE_PROXIMITY:
                tvProx.setText(String.format(Locale.US, "Proximity:  %.1f cm", event.values[0]));
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                float[] rotMat = new float[9];
                SensorManager.getRotationMatrixFromVector(rotMat, event.values);
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotMat, orientation);
                float azimuth = (float) Math.toDegrees(orientation[0]);
                if (azimuth < 0) azimuth += 360;
                tvCompass.setText(String.format(Locale.US, "Compass:  %.0f°  %s", azimuth, cardinal(azimuth)));
                break;
        }
    }

    private String cardinal(float deg) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[Math.round(deg / 45f) % 8];
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}