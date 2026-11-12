package com.senseplus.sense;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

import java.util.List;
import java.util.Locale;

public class SensorInfoActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope, lightSensor, proximity, rotationVector;
    private TextView tvAccel, tvGyro, tvLight, tvProx, tvCompass, tvSensorList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor_info);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.sensorContent));
        Ui.bindBack(this);
        findViewById(R.id.sensorHero).setBackground(Gradients.heroSky(this).wave());

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
        showMissingSensors();
    }

    private void showAvailableSensors() {
        List<Sensor> all = sensorManager.getSensorList(Sensor.TYPE_ALL);
        StringBuilder sb = new StringBuilder("This phone has " + all.size() + " sensors:\n\n");
        for (Sensor s : all) sb.append("•  ").append(s.getName()).append("\n");
        tvSensorList.setText(sb.toString());
    }

    /** A sensor this phone lacks says so in its tile, and Pip looks worried (sensorInfo states). */
    private void showMissingSensors() {
        boolean missing = false;
        if (accelerometer == null) { tvAccel.setText(R.string.sensor_missing); missing = true; }
        if (gyroscope == null) { tvGyro.setText(R.string.sensor_missing); missing = true; }
        if (lightSensor == null) { showMissingBig(tvLight); missing = true; }
        if (proximity == null) { showMissingBig(tvProx); missing = true; }
        if (rotationVector == null) { showMissingBig(tvCompass); missing = true; }
        DuckView pip = findViewById(R.id.pipSensor);
        pip.setExpression(missing ? DuckLooks.Expression.CONCERNED : DuckLooks.Expression.ALERT, false);
    }

    private void showMissingBig(TextView tv) {
        tv.setTextAppearance(R.style.TextAppearance_SENSE_BodyL);
        tv.setTextColor(Ui.color(this, R.color.ink_600));
        tv.setText(R.string.sensor_missing);
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

    // The tiles show each sensor's name and unit, so only the values are written here.
    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                tvAccel.setText(String.format(Locale.US, "X  %6.2f\nY  %6.2f\nZ  %6.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_GYROSCOPE:
                tvGyro.setText(String.format(Locale.US, "X  %6.2f\nY  %6.2f\nZ  %6.2f",
                        event.values[0], event.values[1], event.values[2]));
                break;
            case Sensor.TYPE_LIGHT:
                tvLight.setText(withUnit(String.format(Locale.US, "%.0f", event.values[0]), getString(R.string.unit_lux)));
                break;
            case Sensor.TYPE_PROXIMITY:
                tvProx.setText(withUnit(String.format(Locale.US, "%.1f", event.values[0]), getString(R.string.unit_cm)));
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                float[] rotMat = new float[9];
                SensorManager.getRotationMatrixFromVector(rotMat, event.values);
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotMat, orientation);
                float azimuth = (float) Math.toDegrees(orientation[0]);
                if (azimuth < 0) azimuth += 360;
                tvCompass.setText(withUnit(String.format(Locale.US, "%.0f°", azimuth), cardinal(azimuth)));
                break;
        }
    }

    /** Big light number + a smaller unit in ink_600 (components.statTile). */
    private CharSequence withUnit(String value, String unit) {
        SpannableString s = new SpannableString(value + " " + unit);
        int from = value.length() + 1, to = s.length();
        s.setSpan(new RelativeSizeSpan(0.36f), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(Ui.color(this, R.color.ink_600)), from, to,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private String cardinal(float deg) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        return dirs[Math.round(deg / 45f) % 8];
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
