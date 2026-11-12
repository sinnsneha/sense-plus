# Segment 21 — Make Navigate Home work, and turn it into usable navigation

FILES: app/src/main/java/com/senseplus/sense/NavigationActivity.java app/src/main/res/layout/activity_navigation.xml app/src/main/java/com/senseplus/sense/LocationHelper.java app/src/main/java/com/senseplus/sense/NavMath.java app/src/test/java/com/senseplus/sense/NavMathTest.java app/src/main/java/com/senseplus/sense/MainActivity.java app/src/main/AndroidManifest.xml
Recommended model: Sonnet 5
Priority: **must**. It doesn't depend on any other new segment.

<!-- PASTE BELOW -->
TASK — Segment 21: Navigate Home doesn't work on the phone. Fix every cause below, then add real walking directions.

## Causes found in review (fix all of them)

**Certain (visible in the code):**
- **B1. Status is stuck.** With no Home saved, the status line says "Finding your location…" forever, even after a fix arrives. `updateDirections()` returns early when `!hasHome` without updating the text, so the screen looks frozen.
- **B2. Save is refused indoors.** Saving Home requires accuracy ≤ 30 m. People set "Home" while *at* home, indoors, where Wi-Fi fixes are usually 15–60 m.
- **B3. Location off is silent.** If location is switched off on the phone, no fix ever arrives and nothing says why.
- **B4. Approximate location is silent.** If the user granted only *Approximate* location, accuracy is ~1–3 km, so saving is always refused and distances are meaningless. Nothing explains this.
- **B5. Heading is wrong when the phone is upright.** The heading comes from `getOrientation()` on the raw rotation matrix. That's correct when the phone is flat, but unstable when it's held upright in front of the body, which is how people naturally point a phone. The arrow swings.
- **B7. No fast first fix.** Only `requestLocationUpdates` is used.

**Likely (fix anyway):**
- **B6.** `NavigationActivity` and `MainActivity` both request `ACCESS_FINE_LOCATION` *alone*. For apps targeting Android 12+ (this one targets 37), Android's documentation says FINE must be requested together with `ACCESS_COARSE_LOCATION`. A FINE-only request can be ignored with no dialog (Logcat: "ACCESS_FINE_LOCATION must be requested with ACCESS_COARSE_LOCATION").

## Requirements

1. **Permissions** (`NavigationActivity` **and** `MainActivity`): request FINE + COARSE in the same request. Then:
   - FINE granted → normal.
   - Only COARSE granted → "Precise location is off. Navigation needs it.", with an **Open settings** button (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, `package:` URI).
   - Denied → explain, plus the same button.
   - The screen is never silent.
2. **Location switched off:** before starting updates, run `LocationServices.getSettingsClient(this).checkLocationSettings(...)` with the same high-accuracy request. On `ResolvableApiException`, show the system "Turn on location" dialog with `ActivityResultContracts.StartIntentSenderForResult`. If the user declines, show a message and a retry button.
3. **Fast first fix:** add `LocationHelper.getCurrentLocation(listener)` using `getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource)`. Call it once when the screen starts, alongside the normal updates. `UserMonitorService` uses `LocationHelper` too, so its existing behaviour must not change.
4. **Truthful status (fixes B1):**
   - A small always-visible line: "GPS ±12 m" (or "No GPS fix yet").
   - The main status reflects the real state, in this priority: permission missing / approximate only / location off / waiting for first fix / **"Ready to save Home"** / navigating sentence.
   - Announce a state *change* to TalkBack with `announceForAccessibility`, but not on every sensor tick.
5. **Saving Home indoors (fixes B2).** Tapping Save shows "Measuring your location… (up to 10 s)" and collects fixes for up to 10 s, stopping early at ≤ 20 m. It keeps the most accurate fix, then decides with a new pure `NavMath.homeSaveDecision(float accuracyM)` → `SAVE` (≤ 50 m), `CONFIRM` (≤ 150 m: "GPS is rough, ±N m. Save anyway?" with Save anyway / Try again) or `REFUSE` (> 150 m). Constants: `HOME_GOOD_ACCURACY_M = 50f`, `HOME_MAX_ACCURACY_M = 150f`.
6. **New: set Home by typing an address.** A "Type Home address" button opens a dialog. Run `Geocoder.getFromLocationName(...)` on a background thread (the listener variant on API 33+ is fine), show the resolved address and ask for confirmation, then save. Handle `Geocoder.isPresent() == false`, no results and no network with clear messages. Store the address text under `home_address`.
7. **Heading when upright (fixes B5).** From the orientation's pitch, decide whether the phone is upright (pure helper `NavMath.isUpright(pitchRad)`, threshold 45°). If upright, run `SensorManager.remapCoordinateSystem(R, AXIS_X, AXIS_Z, outR)` before `getOrientation`, so the heading is the direction the back camera faces. Keep the existing declination correction and sin/cos smoothing. Hint text: "Hold the phone flat, or point it like a camera".
8. **New: real walking directions.**
   - Add a big **"Walking directions (Google Maps)"** button. It opens `google.navigation:q=<lat>,<lon>&mode=w` with package `com.google.android.apps.maps`. If Maps isn't installed, fall back to `https://www.google.com/maps/dir/?api=1&destination=<lat>,<lon>&travelmode=walking`.
   - When Home is more than 300 m away (pure `NavMath.suggestMaps(distanceM)`), the spoken sentence adds: "For step-by-step walking directions, tap Walking directions."
   - The compass arrow stays for short distances and orientation.
9. **Keep everything that works:**
   - arrow, spoken directions, auto-speak, declination, smoothing, portrait lock
   - double-precision storage, with the **existing pref keys `home_lat` / `home_lon`** (segment 27 reads them)
   - Add `home_address` and `home_saved_at` keys.
10. **Accessibility:** new buttons ≥ 64 dp, each with a `contentDescription`.

## Unit tests (`NavMathTest`, keep the existing 5)
- `homeSaveDecision`: 20 → SAVE · 50 → SAVE · 51 → CONFIRM · 150 → CONFIRM · 151 → REFUSE
- `isUpright`: flat (0) false · 30° false · 60° true · −60° true
- `suggestMaps`: 299 → false · 301 → true
- `sentence` still has the same existing outputs, with the Maps hint only beyond 300 m

## Out of scope
Turn-by-turn routing inside the app, several saved places, and syncing Home to the cloud (segment 27).

## Acceptance criteria (the reviewer will check these)
- [ ] FINE + COARSE requested together in **both** activities; approximate-only and denied states have a message + settings button
- [ ] Location-off detected via `SettingsClient`, and the system dialog is offered
- [ ] No code path leaves "Finding your location…" on screen after a fix has arrived
- [ ] Save uses the best fix over ≤ 10 s with the SAVE / CONFIRM / REFUSE thresholds; save-by-address works and handles Geocoder failure
- [ ] Upright heading uses `remapCoordinateSystem`; flat behaviour unchanged
- [ ] Walking-directions intent with the web fallback
- [ ] `home_lat` / `home_lon` keys unchanged; `UserMonitorService` location behaviour unchanged
- [ ] All new and old tests pass

## Phone tests to include in your answer
1. Clear app data, then open Navigate Home first → one permission dialog with precise/approximate.
2. Choose Approximate → message + Open settings.
3. Turn location off → system dialog.
4. Indoors → Save → Measuring → Save or "rough, save anyway?".
5. Type your address → resolved → saved.
6. Outdoors, walk ~100 m → the arrow points home, both flat and held upright.
7. Walking directions → Google Maps opens in walking mode.
8. Far away (> 300 m) → speech suggests Walking directions.

COMMIT MESSAGE: "Segment 21: fix Navigate Home (permissions, location off, indoor save, upright heading) + address search and walking directions"
