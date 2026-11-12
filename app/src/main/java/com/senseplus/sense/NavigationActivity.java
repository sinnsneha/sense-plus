package com.senseplus.sense;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * "Navigate Home": saves the current GPS fix as Home, shows a compass arrow that
 * points back to it, and speaks distance + turn instructions.
 */
public class NavigationActivity extends AppCompatActivity implements SensorEventListener {

    static final String PREFS = "senseplus_prefs";
    static final String KEY_HOME_LAT = "home_lat";
    static final String KEY_HOME_LON = "home_lon";

    // Metres. Above this the fix is too rough to be a useful Home point.
    static final float HOME_MAX_ACCURACY_M = 30f;

    private static final long AUTO_SPEAK_INTERVAL_MS = 10_000L;
    private static final int LOCATION_PERMISSION_REQUEST = 202;

    // Low-pass weight for the compass. 0.15 keeps the needle steady without
    // making it lag noticeably when the phone is turned.
    private static final float AZIMUTH_SMOOTHING = 0.15f;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private Sensor rotationVector;
    private TextToSpeech tts;
    private LocationHelper locationHelper;

    private ImageView imgArrow;
    private TextView tvStatus, tvHomeInfo, tvCalibration, tvNoHome;
    private Button btnSaveHome, btnSpeak;
    private Switch switchAutoSpeak;
    private View navCard;

    private boolean hasHome;
    private double homeLat, homeLon;

    private boolean hasFix;
    private double currentLat, currentLon;
    private float currentAccuracyM;
    private double declinationDeg;

    private boolean hasAzimuth;
    private double azimuthDeg;
    private double azimuthSin, azimuthCos;

    private boolean autoSpeak;
    private long lastAutoSpeakMs;
    private boolean locationRunning;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        getWindow().setStatusBarColor(Color.parseColor("#0D2B45"));

        imgArrow = findViewById(R.id.imgArrow);
        tvStatus = findViewById(R.id.tvNavStatus);
        tvHomeInfo = findViewById(R.id.tvHomeInfo);
        tvCalibration = findViewById(R.id.tvCalibration);
        tvNoHome = findViewById(R.id.tvNoHome);
        btnSaveHome = findViewById(R.id.btnSaveHome);
        btnSpeak = findViewById(R.id.btnSpeak);
        switchAutoSpeak = findViewById(R.id.switchAutoSpeak);
        navCard = findViewById(R.id.navCard);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadHome();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        if (rotationVector == null) {
            tvCalibration.setText("This phone has no compass sensor.");
            tvCalibration.setVisibility(View.VISIBLE);
        }

        locationHelper = new LocationHelper(this, (lat, lon, accuracy) -> {
            currentLat = lat;
            currentLon = lon;
            currentAccuracyM = accuracy;
            hasFix = true;
            updateDeclination();
            updateNavigation();
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        btnSaveHome.setOnClickListener(v -> saveHome());
        btnSpeak.setOnClickListener(v -> speakDirections());
        switchAutoSpeak.setOnCheckedChangeListener((button, checked) -> {
            autoSpeak = checked;
            if (checked) lastAutoSpeakMs = System.currentTimeMillis();
        });

        ensureLocationPermission();
        updateHomeState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (sensorManager != null && rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        }
        startLocation();
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        stopLocation();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    // ---------- Home storage ----------

    private void loadHome() {
        hasHome = prefs.contains(KEY_HOME_LAT) && prefs.contains(KEY_HOME_LON);
        if (!hasHome) return;
        try {
            // Stored as String so the full double precision survives; putFloat would lose it.
            homeLat = Double.parseDouble(prefs.getString(KEY_HOME_LAT, "0"));
            homeLon = Double.parseDouble(prefs.getString(KEY_HOME_LON, "0"));
        } catch (NumberFormatException e) {
            hasHome = false;
        }
    }

    private void saveHome() {
        if (!hasFix) {
            String msg = "Finding your location…";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        if (currentAccuracyM > HOME_MAX_ACCURACY_M) {
            String msg = String.format(Locale.US,
                    "GPS is weak, plus or minus %d metres. Move near a window or outdoors.",
                    Math.round(currentAccuracyM));
            tvStatus.setText(msg);
            speak(msg);
            return;
        }

        prefs.edit()
                .putString(KEY_HOME_LAT, Double.toString(currentLat))
                .putString(KEY_HOME_LON, Double.toString(currentLon))
                .apply();
        homeLat = currentLat;
        homeLon = currentLon;
        hasHome = true;
        updateDeclination();
        updateHomeState();
        String msg = "Home saved";
        tvStatus.setText(msg);
        speak("Home saved at this place.");
    }

    // ---------- Compass ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        SensorManager.getOrientation(rotationMatrix, orientation);
        double magneticDeg = Math.toDegrees(orientation[0]);
        double rad = Math.toRadians(magneticDeg);

        // Smooth sin and cos separately so passing through 0°/360° never jumps.
        if (!hasAzimuth) {
            azimuthSin = Math.sin(rad);
            azimuthCos = Math.cos(rad);
            hasAzimuth = true;
        } else {
            azimuthSin += AZIMUTH_SMOOTHING * (Math.sin(rad) - azimuthSin);
            azimuthCos += AZIMUTH_SMOOTHING * (Math.cos(rad) - azimuthCos);
        }

        double smoothedMagnetic = Math.toDegrees(Math.atan2(azimuthSin, azimuthCos));
        if (smoothedMagnetic < 0) smoothedMagnetic += 360;
        double trueAzimuth = smoothedMagnetic + declinationDeg;
        trueAzimuth = ((trueAzimuth % 360) + 360) % 360;
        azimuthDeg = trueAzimuth;

        updateNavigation();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;
        boolean bad = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                || accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW;
        runOnUiThread(() -> tvCalibration.setVisibility(bad ? View.VISIBLE : View.GONE));
    }

    /** Declination from magnetic north to true north for the current fix. */
    private void updateDeclination() {
        if (!hasFix) {
            declinationDeg = 0;
            return;
        }
        // Altitude barely affects declination; 0 m is close enough for walking directions.
        GeomagneticField field = new GeomagneticField(
                (float) currentLat, (float) currentLon, 0f, System.currentTimeMillis());
        declinationDeg = field.getDeclination();
    }

    // ---------- Distance / direction ----------

    /** Recomputes distance + bearing, updates the arrow, and returns the spoken sentence. */
    private String updateDirections() {
        if (!hasHome) return null;
        if (!hasFix) {
            tvStatus.setText("Finding your location…");
            return null;
        }
        if (!hasAzimuth) {
            tvStatus.setText("Finding your direction…");
            return null;
        }

        float[] results = new float[2];
        Location.distanceBetween(currentLat, currentLon, homeLat, homeLon, results);
        double distanceM = results[0];
        double bearingDeg = results[1];

        double relativeDeg = NavMath.normalizeDeg(bearingDeg - azimuthDeg);
        String sentence = NavMath.sentence(distanceM, relativeDeg);

        imgArrow.setRotation((float) relativeDeg);
        imgArrow.setContentDescription(sentence);
        tvStatus.setText(sentence);
        return sentence;
    }

    private void updateNavigation() {
        String sentence = updateDirections();
        if (sentence == null || !autoSpeak) return;

        long now = System.currentTimeMillis();
        if (now - lastAutoSpeakMs >= AUTO_SPEAK_INTERVAL_MS) {
            lastAutoSpeakMs = now;
            speak(sentence);
        }
    }

    private void speakDirections() {
        if (!hasHome) {
            String msg = "No Home saved yet. Save this place first.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        String sentence = updateDirections();
        if (sentence == null) {
            speak(tvStatus.getText().toString());
            return;
        }
        lastAutoSpeakMs = System.currentTimeMillis();
        speak(sentence);
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav");
    }

    // ---------- Screen state ----------

    private void updateHomeState() {
        if (hasHome) {
            tvNoHome.setVisibility(View.GONE);
            navCard.setVisibility(View.VISIBLE);
            btnSpeak.setVisibility(View.VISIBLE);
            switchAutoSpeak.setVisibility(View.VISIBLE);
            tvHomeInfo.setText(String.format(Locale.US,
                    "Home saved at %.6f, %.6f", homeLat, homeLon));
            if (!hasFix) tvStatus.setText("Finding your location…");
        } else {
            tvNoHome.setVisibility(View.VISIBLE);
            navCard.setVisibility(View.GONE);
            btnSpeak.setVisibility(View.GONE);
            switchAutoSpeak.setVisibility(View.GONE);
            tvHomeInfo.setText("");
            tvStatus.setText(hasFix ? "No Home saved yet" : "Finding your location…");
        }
        updateNavigation();
    }

    // ---------- Location permission ----------

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureLocationPermission() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) return;
        if (hasLocationPermission()) {
            startLocation();
        } else {
            tvStatus.setText("Location permission is needed to save Home and measure distance.");
            Toast.makeText(this, "Location permission is needed for navigation",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void startLocation() {
        if (!resumed || !hasLocationPermission() || locationRunning) return;
        locationRunning = true;
        locationHelper.start();
    }

    private void stopLocation() {
        if (!locationRunning) return;
        locationRunning = false;
        locationHelper.stop();
    }
}
