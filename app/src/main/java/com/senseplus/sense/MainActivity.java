package com.senseplus.sense;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, lightSensor, proximity;
    private TextView tvStatus, tvAccel, tvGyro, tvLight, tvProx, tvSensorList;
    private Button btnHelp, btnOk;

    private TextToSpeech tts;
    private Vibrator vibrator;
    private FallDetector fallDetector;

    // Three-level safety state
    private enum SafetyLevel { NORMAL, ATTENTION, EMERGENCY }
    private SafetyLevel level = SafetyLevel.NORMAL;

    private CountDownTimer attentionTimer;
    private long attentionStartTime = 0;
    private static final int   ATTENTION_SECONDS   = 15;   // time to respond before emergency
    private static final long  RECOVERY_GRACE_MS   = 2000; // ignore movement right after fall (our own buzz)
    private static final float RECOVERY_MOVEMENT   = 4f;   // movement beyond this during countdown = recovered
    private static final float GRAVITY = 9.8f;

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
        btnHelp = findViewById(R.id.btnHelp);
        btnOk = findViewById(R.id.btnOk);

        btnHelp.setOnClickListener(v -> triggerEmergency("Manual HELP pressed"));
        btnOk.setOnClickListener(v -> cancelToNormal("you pressed I'm OK"));

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        lightSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        proximity     = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        fallDetector = new FallDetector(new FallDetector.Listener() {
            @Override public void onFallDetected() { enterAttention(); }
            @Override public void onStateChanged(String info) {
                if (level == SafetyLevel.NORMAL) tvStatus.setText("Status: " + info);
            }
        });

        setStatusNormal();
        showAvailableSensors();
    }

    // ---------- Three-level safety state machine ----------

    private void enterAttention() {
        level = SafetyLevel.ATTENTION;
        attentionStartTime = System.currentTimeMillis();
        tvStatus.setTextColor(Color.parseColor("#E65100")); // orange
        speak("Possible fall detected. Are you okay? Press I am okay, or I will alert your guardian.");
        vibrate(800);

        if (attentionTimer != null) attentionTimer.cancel();
        attentionTimer = new CountDownTimer(ATTENTION_SECONDS * 1000L, 1000) {
            @Override public void onTick(long ms) {
                tvStatus.setText("POSSIBLE FALL — Are you okay? (" + (ms / 1000) + "s)\nPress I'M OK to cancel");
            }
            @Override public void onFinish() {
                triggerEmergency("No response after fall");
            }
        }.start();
    }

    private void triggerEmergency(String reason) {
        if (attentionTimer != null) attentionTimer.cancel();
        level = SafetyLevel.EMERGENCY;
        tvStatus.setTextColor(Color.parseColor("#C62828")); // red
        tvStatus.setText("POSSIBLE EMERGENCY\n" + reason +
                "\n(Guardian would receive location + time)\nPress I'M OK to cancel");
        speak("Possible emergency. Alerting your guardian.");
        vibrate(1200);
        // Segment 5: this is where we actually send the alert to the guardian via Firebase.
    }

    private void cancelToNormal(String reason) {
        if (attentionTimer != null) attentionTimer.cancel();
        boolean wasActive = (level != SafetyLevel.NORMAL);
        setStatusNormal();
        if (wasActive) {
            tvStatus.setText("Status: all clear (" + reason + ")");
            speak("Okay. Alert canceled.");
        }
    }

    private void setStatusNormal() {
        level = SafetyLevel.NORMAL;
        tvStatus.setTextColor(Color.parseColor("#1F4E79"));
        tvStatus.setText("Status: monitoring (normal)");
    }

    // ---------- Sensors ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                float x = event.values[0], y = event.values[1], z = event.values[2];
                tvAccel.setText(String.format(Locale.US,
                        "Accelerometer (m/s²)\nX: %.2f   Y: %.2f   Z: %.2f", x, y, z));
                double magnitude = Math.sqrt(x*x + y*y + z*z);

                if (level == SafetyLevel.NORMAL) {
                    fallDetector.process(magnitude);          // only hunt for falls when calm
                } else if (level == SafetyLevel.ATTENTION) {
                    long sinceAttention = System.currentTimeMillis() - attentionStartTime;
                    // after the grace period, strong movement = the person got up = recovered
                    if (sinceAttention > RECOVERY_GRACE_MS
                            && Math.abs(magnitude - GRAVITY) > RECOVERY_MOVEMENT) {
                        cancelToNormal("movement detected — you're up");
                    }
                }
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

    private void showAvailableSensors() {
        List<Sensor> all = sensorManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder("This phone has " + all.size() + " sensors:\n");
        for (Sensor s : all) sb.append("• ").append(s.getName()).append("\n");
        tvSensorList.setText(sb.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (gyroscope != null)     sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_UI);
        if (lightSensor != null)   sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
        if (proximity != null)     sensorManager.registerListener(this, proximity, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
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
        if (attentionTimer != null) attentionTimer.cancel();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}