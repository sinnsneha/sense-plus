package com.senseplus.sense;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

public class LocationHelper {

    public interface Listener {
        void onLocation(double latitude, double longitude, float accuracyMeters);
    }

    private final FusedLocationProviderClient client;
    private final Listener listener;
    private LocationCallback callback;

    public LocationHelper(Context context, Listener listener) {
        this.client = LocationServices.getFusedLocationProviderClient(context);
        this.listener = listener;
    }

    // Call ONLY after location permission has been granted
    @SuppressLint("MissingPermission")
    public void start() {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 5000)   // update about every 5 seconds
                .setMinUpdateIntervalMillis(2000)
                .build();

        callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) {
                    listener.onLocation(loc.getLatitude(), loc.getLongitude(), loc.getAccuracy());
                }
            }
        };

        client.requestLocationUpdates(request, callback, Looper.getMainLooper());
    }

    /**
     * One-shot high-accuracy fix, faster than waiting for the first update.
     * The result may be a recent cached location. Call ONLY after permission
     * has been granted. The passed listener is called on the main thread.
     */
    @SuppressLint("MissingPermission")
    public void getCurrentLocation(Listener listener) {
        CancellationTokenSource tokenSource = new CancellationTokenSource();
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        listener.onLocation(location.getLatitude(), location.getLongitude(),
                                location.getAccuracy());
                    }
                });
    }

    public void stop() {
        if (callback != null) client.removeLocationUpdates(callback);
    }
}
