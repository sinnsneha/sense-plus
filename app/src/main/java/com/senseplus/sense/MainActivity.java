package com.senseplus.sense;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, lightSensor, proximity;
    private TextView tvStatus, tvAccel, tvGyro, tvLight, tvProx, tvSensorList;

    private TextToSpeech tts;
    private Vibrator vibrator;

    private static final float SHAKE_THRESHOLD = 25f;      // gravity alone is ~9.8
    private static final float FREE_FALL_THRESHOLD = 3f;   // near-weightless = falling
    private static final long  SHAKE_COOLDOWN_MS = 2000;   // ignore repeats for 2s
    private long lastShakeTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvAccel = findViewById(R.id.tvAccel);
        tvGyro = findViewById(R.id.tvGyro);
        tvLight = findViewById(R.id.tvLight);
        tvProx = findViewById(R.id.tvProx);
        tvSensorList = findViewById(R.id.tvSensorList);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        lightSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximity     = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        // The phone's voice
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        // The phone's vibration motor
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        showAvailableSensors();
    }

    private void showAvailableSensors() {
        List<Sensor> all = sensorManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder("This phone has " + all.size() + " sensors:\n");
        for (Sensor s : all) sb.append("• ").append(s.getName()).append("\n");
        tvSensorList.setText(sb.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        if (gyroscope != null)     sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        if (lightSensor != null)   sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        if (proximity != null)     sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_UI);
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
                float x = event.values[0], y = event.values[1], z = event.values[2];
                tvAccel.setText(String.format(Locale.US,
                        "Accelerometer (m/s²)\nX: %.2f   Y: %.2f   Z: %.2f", x, y, z));

                // Combine all 3 axes into one "total strength" number
                double magnitude = Math.sqrt(x*x + y*y + z*z);
                checkForShake(magnitude);
                break;

            case Sensor.TYPE_GYROSCOPE:
                tvGyro.setText(String.format(Locale.US,
                        "Gyroscope (rad/s)\nX: %.2f   Y: %.2f   Z: %.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;

            case Sensor.TYPE_LIGHT:
                tvLight.setText(String.format(Locale.US, "Light (lux): %.1f", event.values[0]));
                break;

            case Sensor.TYPE_PROXIMITY:
                tvProx.setText(String.format(Locale.US, "Proximity (cm): %.1f", event.values[0]));
                break;
        }
    }

    private void checkForShake(double magnitude) {
        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTime > SHAKE_COOLDOWN_MS) {   // debounce: one trigger, not a storm
                lastShakeTime = now;
                tvStatus.setText("Status: SHAKE DETECTED!");
                speak("Shake detected");
                vibrate(500);
            }
        } else if (magnitude < FREE_FALL_THRESHOLD) {
            tvStatus.setText("Status: FREE FALL!");
            speak("Free fall");
            vibrate(500);
        } else {
            tvStatus.setText(String.format(Locale.US, "Status: normal (%.1f)", magnitude));
        }
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "senseId");
    }

    private void vibrate(long ms) {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}