package com.senseplus.sense;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.location.SettingsClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.senseplus.sense.ui.DuckLooks;
import com.senseplus.sense.ui.DuckView;
import com.senseplus.sense.ui.Gradients;
import com.senseplus.sense.ui.Ui;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * "Navigate Home": saves the current GPS fix (or a typed address) as Home,
 * shows a compass arrow that points back to it, and speaks distance + turn
 * instructions. It also explains permission / location / GPS problems instead
 * of silently showing "Finding your location…".
 */
public class NavigationActivity extends AppCompatActivity implements SensorEventListener {

    static final String PREFS = "senseplus_prefs";
    static final String KEY_HOME_LAT = "home_lat";
    static final String KEY_HOME_LON = "home_lon";
    static final String KEY_HOME_ADDRESS = "home_address";
    static final String KEY_HOME_SAVED_AT = "home_saved_at";

    // Metres. Saving stops collecting fixes once it has one this good.
    static final float MEASURE_STOP_ACCURACY_M = 20f;
    // Millis. Long enough for a Wi-Fi fix indoors, short enough to not feel stuck.
    static final long MEASURE_TIMEOUT_MS = 10_000L;

    private static final long AUTO_SPEAK_INTERVAL_MS = 10_000L;
    private static final int LOCATION_PERMISSION_REQUEST = 202;

    // Low-pass weight for the compass. 0.15 keeps the needle steady without
    // making it lag noticeably when the phone is turned.
    private static final float AZIMUTH_SMOOTHING = 0.15f;

    private final float[] rotationMatrix = new float[9];
    private final float[] uprightMatrix = new float[9];
    private final float[] orientation = new float[3];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable measureTimeout = this::finishHomeMeasure;

    private SharedPreferences prefs;
    private SensorManager sensorManager;
    private Sensor rotationVector;
    private TextToSpeech tts;
    private LocationHelper locationHelper;
    private Geocoder geocoder;
    private ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher;

    private ImageView imgArrow;
    private TextView tvStatus, tvGps, tvHomeInfo, tvCalibration, tvNoHome;
    private Button btnSaveHome, btnSpeak, btnTypeAddress, btnWalkingDirections,
            btnOpenSettings, btnRetryLocation;
    private MaterialSwitch switchAutoSpeak;
    private TextView tvDistance;
    private DuckView pip;
    private double lastDistanceM = -1;
    private View navCard;

    // Segment 27: safe zone settings (shown once a Home is saved)
    private View safeZoneCard;
    private MaterialSwitch swSafeZone, swSafeZoneSpeak;
    private Spinner spSafeZoneRadius;
    private boolean bindingSafeZone = false; // true while the controls are being filled in, not changed by the user

    private boolean hasHome;
    private double homeLat, homeLon;
    private String homeAddress;

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

    private boolean fineGranted;
    private boolean coarseGranted;
    private boolean locationOff;
    private boolean settingsCheckInFlight;

    private boolean measuringHome;
    private boolean hasMeasureFix;
    private double measureLat, measureLon;
    private float measureAccuracy;

    private String lastStatusKey = "";
    private String lastSentence;

    private final LocationHelper.Listener locationListener = this::handleFix;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Ui.enableEdgeToEdge(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);
        Ui.applyInsets(findViewById(R.id.topBar), findViewById(R.id.navContent));
        Ui.bindBack(this);
        findViewById(R.id.navHero).setBackground(Gradients.heroCoral(this).wave());

        locationSettingsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartIntentSenderForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        locationOff = false;
                        if (resumed) startLocationNow();
                    } else {
                        locationOff = true;
                    }
                    updateStatus();
                });

        imgArrow = findViewById(R.id.imgArrow);
        tvStatus = findViewById(R.id.tvNavStatus);
        tvGps = findViewById(R.id.tvGps);
        tvHomeInfo = findViewById(R.id.tvHomeInfo);
        tvCalibration = findViewById(R.id.tvCalibration);
        tvNoHome = findViewById(R.id.tvNoHome);
        btnSaveHome = findViewById(R.id.btnSaveHome);
        btnSpeak = findViewById(R.id.btnSpeak);
        btnTypeAddress = findViewById(R.id.btnTypeAddress);
        btnWalkingDirections = findViewById(R.id.btnWalkingDirections);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        btnRetryLocation = findViewById(R.id.btnRetryLocation);
        switchAutoSpeak = findViewById(R.id.switchAutoSpeak);
        navCard = findViewById(R.id.navCard);
        tvDistance = findViewById(R.id.tvDistance);
        pip = findViewById(R.id.pipNav);
        safeZoneCard = findViewById(R.id.safeZoneCard);
        swSafeZone = findViewById(R.id.swSafeZone);
        swSafeZoneSpeak = findViewById(R.id.swSafeZoneSpeak);
        spSafeZoneRadius = findViewById(R.id.spSafeZoneRadius);
        Ui.pressFeedback(btnSaveHome, btnSpeak, btnTypeAddress, btnWalkingDirections,
                btnOpenSettings, btnRetryLocation);

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

        geocoder = new Geocoder(this, Locale.getDefault());
        locationHelper = new LocationHelper(this, locationListener);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(Locale.US);
        });

        btnSaveHome.setOnClickListener(v -> onSaveHomeClicked());
        btnTypeAddress.setOnClickListener(v -> promptAddress());
        btnWalkingDirections.setOnClickListener(v -> openWalkingDirections());
        btnSpeak.setOnClickListener(v -> speakDirections());
        btnOpenSettings.setOnClickListener(v -> openAppSettings());
        btnRetryLocation.setOnClickListener(v -> retryLocationSettings());
        switchAutoSpeak.setOnCheckedChangeListener((button, checked) -> {
            autoSpeak = checked;
            if (checked) lastAutoSpeakMs = System.currentTimeMillis();
        });
        setUpSafeZoneControls();

        refreshPermissions();
        ensureLocationPermission();
        updateHomeState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        refreshPermissions();
        if (sensorManager != null && rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        }
        if (fineGranted || coarseGranted) {
            startWhenReady();
        } else {
            updateStatus();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        if (measuringHome) {
            measuringHome = false;
            btnSaveHome.setEnabled(true);
        }
        handler.removeCallbacks(measureTimeout);
        stopLocation();
        settingsCheckInFlight = false;
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
        homeAddress = prefs.getString(KEY_HOME_ADDRESS, null);
        if (!hasHome) return;
        try {
            // Stored as String so the full double precision survives; putFloat would lose it.
            homeLat = Double.parseDouble(prefs.getString(KEY_HOME_LAT, "0"));
            homeLon = Double.parseDouble(prefs.getString(KEY_HOME_LON, "0"));
        } catch (NumberFormatException e) {
            hasHome = false;
        }
    }

    private void commitHome(double lat, double lon, String address) {
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_HOME_LAT, Double.toString(lat))
                .putString(KEY_HOME_LON, Double.toString(lon))
                .putLong(KEY_HOME_SAVED_AT, System.currentTimeMillis());
        if (address != null && !address.trim().isEmpty()) {
            editor.putString(KEY_HOME_ADDRESS, address);
            homeAddress = address;
        } else {
            // A GPS save replaces the point, so an address from an earlier typed save
            // would now describe a different place.
            editor.remove(KEY_HOME_ADDRESS);
            homeAddress = null;
        }
        editor.apply();
        HomeStore.onHomeSaved(this); // segment 27: stamp it and write it to users/{uid}.home

        homeLat = lat;
        homeLon = lon;
        hasHome = true;
        updateDeclination();
        updateHomeState();
        tvStatus.setText("Home saved");
        speak("Home saved.");
    }

    // ---------- Safe zone settings (segment 27) ----------

    /** The adapter and listeners, set up once. Every change goes through HomeStore (phone + cloud). */
    private void setUpSafeZoneControls() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.safezone_radii, R.layout.item_spinner);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spSafeZoneRadius.setAdapter(adapter);

        swSafeZone.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingSafeZone) HomeStore.setEnabled(this, checked);
        });
        spSafeZoneRadius.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Setting the selection from the saved value also arrives here: only a real change counts.
                int radius = HomeData.RADII_M[position];
                if (!bindingSafeZone && radius != HomeStore.load(NavigationActivity.this).radiusM) {
                    HomeStore.setRadius(NavigationActivity.this, radius);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        swSafeZoneSpeak.setOnCheckedChangeListener((button, checked) -> {
            if (!bindingSafeZone) HomeStore.setSpeakHelp(this, checked);
        });
    }

    /** Fills the controls from the saved settings (and the profile, for the spoken-help default). */
    private void bindSafeZone() {
        HomeData saved = HomeStore.load(this);
        bindingSafeZone = true;
        swSafeZone.setChecked(saved.enabled);
        spSafeZoneRadius.setSelection(HomeData.radiusIndex(saved.radiusM));
        swSafeZoneSpeak.setChecked(HomeStore.speakHelp(this));
        bindingSafeZone = false;
    }

    // ---------- Saving Home ----------

    private void onSaveHomeClicked() {
        if (measuringHome) return;
        if (!fineGranted && !coarseGranted) {
            String msg = "Location permission is needed to save Home.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        if (!fineGranted) {
            String msg = "Precise location is off. Navigation needs it.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        if (locationOff) {
            String msg = "Location is off. Turn it on to save Home.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        startHomeMeasure();
    }

    private void startHomeMeasure() {
        measuringHome = true;
        hasMeasureFix = false;
        measureAccuracy = Float.MAX_VALUE;
        btnSaveHome.setEnabled(false);

        // Use the fix already on screen while we wait for a possibly better one.
        if (hasFix) {
            hasMeasureFix = true;
            measureLat = currentLat;
            measureLon = currentLon;
            measureAccuracy = currentAccuracyM;
        }

        updateStatus();
        speak("Measuring your location. Up to 10 seconds.");
        handler.postDelayed(measureTimeout, MEASURE_TIMEOUT_MS);

        if (hasMeasureFix && measureAccuracy <= MEASURE_STOP_ACCURACY_M) {
            finishHomeMeasure();
        }
    }

    private void onMeasureFix(double lat, double lon, float accuracy) {
        if (!measuringHome) return;
        if (!hasMeasureFix || accuracy < measureAccuracy) {
            hasMeasureFix = true;
            measureLat = lat;
            measureLon = lon;
            measureAccuracy = accuracy;
        }
        if (measureAccuracy <= MEASURE_STOP_ACCURACY_M) finishHomeMeasure();
    }

    private void finishHomeMeasure() {
        if (!measuringHome) return;
        measuringHome = false;
        handler.removeCallbacks(measureTimeout);
        btnSaveHome.setEnabled(true);

        if (!hasMeasureFix) {
            String msg = "Couldn't get your location. Move near a window or go outdoors and try again.";
            updateStatus();
            tvStatus.setText(msg);
            speak(msg);
            return;
        }

        NavMath.HomeSaveDecision decision = NavMath.homeSaveDecision(measureAccuracy);
        if (decision == NavMath.HomeSaveDecision.SAVE) {
            commitHome(measureLat, measureLon, null);
        } else if (decision == NavMath.HomeSaveDecision.CONFIRM) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("GPS is rough")
                    .setMessage(String.format(Locale.US,
                            "GPS is rough, ±%d m. Save anyway?", Math.round(measureAccuracy)))
                    .setPositiveButton("Save anyway", (dialog, which) ->
                            commitHome(measureLat, measureLon, null))
                    .setNegativeButton("Try again", (dialog, which) -> startHomeMeasure())
                    .show();
        } else {
            String msg = String.format(Locale.US,
                    "GPS is too rough, ±%d m. Move near a window or go outdoors and try again.",
                    Math.round(measureAccuracy));
            updateStatus();
            tvStatus.setText(msg);
            speak(msg);
        }
    }

    // ---------- Home by typed address ----------

    private void promptAddress() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("House number, street, city");
        new MaterialAlertDialogBuilder(this)
                .setTitle("Type Home address")
                .setMessage("Enter your full home address, then tap Search.")
                .setView(input)
                .setPositiveButton("Search", (dialog, which) -> {
                    String query = input.getText().toString().trim();
                    if (query.isEmpty()) {
                        Toast.makeText(this, "Type an address first", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    searchAddress(query);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void searchAddress(final String query) {
        if (!Geocoder.isPresent()) {
            String msg = "Address search isn't available on this phone.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        String msg = "Searching for your address…";
        tvStatus.setText(msg);
        speak(msg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(query, 1, new Geocoder.GeocodeListener() {
                @Override
                public void onGeocode(List<Address> addresses) {
                    runOnUiThread(() -> showAddressResult(addresses, query));
                }

                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(NavigationActivity.this::addressSearchFailed);
                }
            });
        } else {
            new Thread(() -> {
                List<Address> addresses = null;
                boolean failed = false;
                try {
                    addresses = geocoder.getFromLocationName(query, 1);
                } catch (IOException e) {
                    failed = true;
                }
                final List<Address> result = addresses;
                final boolean error = failed;
                runOnUiThread(() -> {
                    if (error) addressSearchFailed();
                    else showAddressResult(result, query);
                });
            }).start();
        }
    }

    private void showAddressResult(List<Address> addresses, String query) {
        if (addresses == null || addresses.isEmpty()) {
            String msg = "No address found for \"" + query + "\". Try adding the city or PIN code.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        Address address = addresses.get(0);
        final double lat = address.getLatitude();
        final double lon = address.getLongitude();
        String label = address.getAddressLine(0);
        if (label == null || label.trim().isEmpty()) label = query;
        final String addressText = label;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Is this your Home?")
                .setMessage(addressText + "\n\n"
                        + String.format(Locale.US, "%.6f, %.6f", lat, lon))
                .setPositiveButton("Save this Home", (dialog, which) ->
                        commitHome(lat, lon, addressText))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
        speak("Found " + addressText + ". Tap Save this Home to use it.");
    }

    private void addressSearchFailed() {
        String msg = "Couldn't search the address. Check your internet connection and try again.";
        tvStatus.setText(msg);
        speak(msg);
    }

    // ---------- Google Maps walking directions ----------

    private void openWalkingDirections() {
        if (!hasHome) {
            String msg = "No Home saved yet. Save this place first.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        Uri navigationUri = Uri.parse("google.navigation:q=" + homeLat + "," + homeLon + "&mode=w");
        Intent mapsIntent = new Intent(Intent.ACTION_VIEW, navigationUri);
        mapsIntent.setPackage("com.google.android.apps.maps");
        try {
            startActivity(mapsIntent);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Maps is not installed; fall back to the browser.
        }

        Uri webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination="
                + homeLat + "," + homeLon + "&travelmode=walking");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, webUri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open Google Maps directions.",
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Compass ----------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) return;

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        SensorManager.getOrientation(rotationMatrix, orientation);
        double pitchRad = orientation[1];

        // Flat: the raw matrix is already correct. Upright: remap so the heading
        // is the direction the back camera faces, not the direction the top points.
        float[] activeMatrix = rotationMatrix;
        if (NavMath.isUpright(pitchRad)) {
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X,
                    SensorManager.AXIS_Z, uprightMatrix);
            activeMatrix = uprightMatrix;
        }
        SensorManager.getOrientation(activeMatrix, orientation);
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

    /** Recomputes distance + bearing and updates the arrow; returns the spoken sentence. */
    private String currentNavigationSentence() {
        if (!hasHome || !hasFix || !hasAzimuth) return null;

        float[] results = new float[2];
        Location.distanceBetween(currentLat, currentLon, homeLat, homeLon, results);
        double distanceM = results[0];
        double bearingDeg = results[1];

        double relativeDeg = NavMath.normalizeDeg(bearingDeg - azimuthDeg);
        String sentence = NavMath.sentence(distanceM, relativeDeg, true);
        showDistance(distanceM);

        imgArrow.setRotation((float) relativeDeg);
        imgArrow.setContentDescription(sentence);
        return sentence;
    }

    private void updateNavigation() {
        updateStatus();
        if (!autoSpeak || lastSentence == null) return;

        long now = System.currentTimeMillis();
        if (now - lastAutoSpeakMs >= AUTO_SPEAK_INTERVAL_MS) {
            lastAutoSpeakMs = now;
            speak(lastSentence);
        }
    }

    private void speakDirections() {
        if (!hasHome) {
            String msg = "No Home saved yet. Save this place first.";
            tvStatus.setText(msg);
            speak(msg);
            return;
        }
        updateStatus();
        if (lastSentence == null) {
            speak(tvStatus.getText().toString());
            return;
        }
        lastAutoSpeakMs = System.currentTimeMillis();
        speak(lastSentence);
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav");
    }

    // ---------- Screen state ----------

    private void updateHomeState() {
        if (hasHome) {
            safeZoneCard.setVisibility(View.VISIBLE);
            bindSafeZone();
            tvNoHome.setVisibility(View.GONE);
            navCard.setVisibility(View.VISIBLE);
            btnSpeak.setVisibility(View.VISIBLE);
            btnWalkingDirections.setVisibility(View.VISIBLE);
            switchAutoSpeak.setVisibility(View.VISIBLE);
            if (homeAddress != null && !homeAddress.trim().isEmpty()) {
                tvHomeInfo.setText("Home: " + homeAddress);
            } else {
                tvHomeInfo.setText(String.format(Locale.US,
                        "Home saved at %.6f, %.6f", homeLat, homeLon));
            }
        } else {
            safeZoneCard.setVisibility(View.GONE);
            tvNoHome.setVisibility(View.VISIBLE);
            navCard.setVisibility(View.GONE);
            btnSpeak.setVisibility(View.GONE);
            btnWalkingDirections.setVisibility(View.GONE);
            switchAutoSpeak.setVisibility(View.GONE);
            tvHomeInfo.setText("");
            lastDistanceM = -1;
            tvDistance.setText(R.string.dash);
        }
        updateNavigation();
    }

    /**
     * One status line that always tells the truth, in priority order:
     * permission missing / approximate only / location off / waiting for a fix /
     * ready to save Home / navigating.
     */
    private void updateStatus() {
        lastSentence = null;

        if (hasFix) {
            tvGps.setText(String.format(Locale.US, "GPS ±%d m", Math.round(currentAccuracyM)));
        } else {
            tvGps.setText("No GPS fix yet");
        }

        String key;
        String text;

        if (!fineGranted && !coarseGranted) {
            key = "permission";
            text = "Location permission is needed to save Home and measure distance.";
        } else if (!fineGranted) {
            key = "approximate";
            text = "Precise location is off. Navigation needs it.";
        } else if (locationOff) {
            key = "locationOff";
            text = "Location is off. Turn it on to use navigation.";
        } else if (measuringHome) {
            key = "measuring";
            text = "Measuring your location… (up to 10 s)";
        } else if (!hasFix) {
            key = "waiting";
            text = "Finding your location…";
        } else if (!hasHome) {
            key = "ready";
            text = "Ready to save Home";
        } else if (!hasAzimuth) {
            key = "direction";
            text = "Finding your direction…";
        } else {
            key = "navigating";
            lastSentence = currentNavigationSentence();
            text = lastSentence != null ? lastSentence : "Finding your direction…";
        }

        tvStatus.setText(text);
        updateStatusButtons(key);
        updatePip(key);

        if (!key.equals(lastStatusKey)) {
            lastStatusKey = key;
            tvStatus.announceForAccessibility(text);
        }
    }

    private void updateStatusButtons(String key) {
        boolean showSettings = "permission".equals(key) || "approximate".equals(key);
        btnOpenSettings.setVisibility(showSettings ? View.VISIBLE : View.GONE);
        btnRetryLocation.setVisibility("locationOff".equals(key) ? View.VISIBLE : View.GONE);
    }

    // ---------- presentation (Pip + distance readout) ----------

    /** Big distance number: nearest 10 m under 1 km, else km with one decimal (unit smaller). */
    private void showDistance(double metres) {
        lastDistanceM = metres;
        String value, unit;
        if (metres < 1000.0) {
            value = String.valueOf(Math.round(metres / 10.0) * 10L);
            unit = "m";
        } else {
            value = String.format(Locale.US, "%.1f", metres / 1000.0);
            unit = "km";
        }
        SpannableString text = new SpannableString(value + " " + unit);
        text.setSpan(new RelativeSizeSpan(0.36f), value.length() + 1, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvDistance.setText(text);
    }

    /** Pip (nav look) follows the status line: worried when location can't work, looking
     *  while it waits, happy at Home, calm while navigating. */
    private void updatePip(String key) {
        DuckLooks.Expression e;
        switch (key) {
            case "permission":
            case "approximate":
            case "locationOff":
                e = DuckLooks.Expression.CONCERNED;
                break;
            case "measuring":
            case "waiting":
            case "direction":
                e = DuckLooks.Expression.ALERT;
                break;
            case "navigating":
                e = lastDistanceM >= 0 && lastDistanceM < NavMath.ARRIVED_M
                        ? DuckLooks.Expression.HAPPY : DuckLooks.Expression.IDLE;
                break;
            default:
                e = DuckLooks.Expression.IDLE;
                break;
        }
        pip.setExpression(e, true);
    }

    // ---------- Location permission / settings ----------

    private void refreshPermissions() {
        fineGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        coarseGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureLocationPermission() {
        refreshPermissions();
        if (fineGranted) return;
        // Android 12+ ignores FINE on its own; it must be requested with COARSE.
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION},
                LOCATION_PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) return;

        refreshPermissions();
        if (fineGranted || coarseGranted) {
            startWhenReady();
        } else {
            String msg = "Location permission is needed to save Home and measure distance.";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            updateStatus();
        }
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private void retryLocationSettings() {
        locationOff = false;
        stopLocation();
        settingsCheckInFlight = false;
        startWhenReady();
        updateStatus();
    }

    private void startWhenReady() {
        if (!resumed) return;
        if (!fineGranted && !coarseGranted) {
            updateStatus();
            return;
        }
        if (locationRunning || settingsCheckInFlight) return;

        settingsCheckInFlight = true;
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();
        LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
                .addLocationRequest(request)
                .build();

        SettingsClient client = LocationServices.getSettingsClient(this);
        client.checkLocationSettings(settingsRequest)
                .addOnSuccessListener(this, response -> {
                    settingsCheckInFlight = false;
                    locationOff = false;
                    if (resumed) startLocationNow();
                    updateStatus();
                })
                .addOnFailureListener(this, e -> {
                    settingsCheckInFlight = false;
                    if (e instanceof ResolvableApiException) {
                        try {
                            IntentSenderRequest requestIntent =
                                    new IntentSenderRequest.Builder(
                                            ((ResolvableApiException) e)
                                                    .getResolution().getIntentSender())
                                            .build();
                            locationSettingsLauncher.launch(requestIntent);
                        } catch (Exception fallback) {
                            locationOff = true;
                            updateStatus();
                        }
                    } else {
                        locationOff = true;
                        updateStatus();
                    }
                });
    }

    private void startLocationNow() {
        if (!resumed || locationRunning) return;
        if (!fineGranted && !coarseGranted) return;
        locationRunning = true;
        locationHelper.start();
        locationHelper.getCurrentLocation(locationListener);
        updateStatus();
    }

    private void stopLocation() {
        if (!locationRunning) return;
        locationRunning = false;
        locationHelper.stop();
    }

    private void handleFix(double lat, double lon, float accuracy) {
        if (accuracy <= 0f) accuracy = 9999f;
        currentLat = lat;
        currentLon = lon;
        currentAccuracyM = accuracy;
        hasFix = true;
        updateDeclination();
        if (measuringHome) onMeasureFix(lat, lon, accuracy);
        updateNavigation();
    }
}
