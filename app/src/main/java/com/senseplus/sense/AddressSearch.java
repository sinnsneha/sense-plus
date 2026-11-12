package com.senseplus.sense;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * One address lookup with the platform Geocoder (segment 27: the guardian types the user's home
 * address). Same approach as Navigate Home's own search: the asynchronous Geocoder on API 33+,
 * a background thread below. Every callback arrives on the main thread, exactly once.
 */
final class AddressSearch {

    interface Callback {
        void onFound(double lat, double lon, String label);
        void onNotFound();
        void onError();
    }

    private AddressSearch() {
    }

    static void search(Context context, final String query, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        if (!Geocoder.isPresent()) {
            main.post(callback::onError);
            return;
        }
        final Geocoder geocoder = new Geocoder(context.getApplicationContext(), Locale.getDefault());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(query, 1, new Geocoder.GeocodeListener() {
                @Override public void onGeocode(List<Address> addresses) {
                    main.post(() -> deliver(addresses, query, callback));
                }

                @Override public void onError(String errorMessage) {
                    main.post(callback::onError);
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
                main.post(() -> {
                    if (error) callback.onError();
                    else deliver(result, query, callback);
                });
            }).start();
        }
    }

    private static void deliver(List<Address> addresses, String query, Callback callback) {
        if (addresses == null || addresses.isEmpty()) {
            callback.onNotFound();
            return;
        }
        Address first = addresses.get(0);
        String label = first.getAddressLine(0);
        if (label == null || label.trim().isEmpty()) label = query;
        callback.onFound(first.getLatitude(), first.getLongitude(), label);
    }
}
