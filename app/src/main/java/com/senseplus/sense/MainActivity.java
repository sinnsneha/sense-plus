package com.senseplus.sense;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, lightSensor, proximity;
    private TextView tvAccel, tvGyro, tvLight, tvSensorList, tvProx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Connect Java variables to the TextViews in the layout
        tvAccel = findViewById(R.id.tvAccel);
        tvGyro = findViewById(R.id.tvGyro);
        tvLight   = findViewById(R.id.tvLight);
        tvProx    = findViewById(R.id.tvProx);
        tvSensorList = findViewById(R.id.tvSensorList);

        // 1. Get the SensorManager — the system service that controls all sensors
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        // 2. Grab the sensors we want. These return null if the phone lacks one.
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        lightSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximity     = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        showAvailableSensors();
    }

    // List every sensor the phone physically has
    private void showAvailableSensors() {
        List<Sensor> all = sensorManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder("This phone has " + all.size() + " sensors:\n");
        for (Sensor s : all) {
            sb.append("• ").append(s.getName()).append("\n");
        }
        tvSensorList.setText(sb.toString());
    }

    // Start listening when the screen is visible
    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (gyroscope != null)
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        if (lightSensor != null)
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        if (proximity != null)
            sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_UI);
    }

    // Stop listening when the screen is hidden — this saves battery
    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    // Called automatically every time a sensor has a new reading
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                tvAccel.setText(String.format(Locale.US,
                        "Accelerometer (m/s²)\nX: %.2f   Y: %.2f   Z: %.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_GYROSCOPE:
                tvGyro.setText(String.format(Locale.US,
                        "Gyroscope (rad/s)\nX: %.2f   Y: %.2f   Z: %.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_LIGHT:
                tvLight.setText(String.format(Locale.US,
                        "Light (lux): %.1f", event.values[0]));
                break;
            case Sensor.TYPE_PROXIMITY:
                tvProx.setText(String.format(Locale.US,
                        "Proximity (cm): %.1f", event.values[0]));
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed yet
    }
}