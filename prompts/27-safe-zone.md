# Segment 27 — Safe zone: tell the guardian when the user leaves home (wandering alert)

FILES: app/src/main/java/com/senseplus/sense/NavigationActivity.java app/src/main/java/com/senseplus/sense/UserMonitorService.java app/src/main/java/com/senseplus/sense/CloudHelper.java app/src/main/java/com/senseplus/sense/GuardianService.java app/src/main/java/com/senseplus/sense/GuardianActivity.java app/src/main/res/layout/activity_guardian.xml app/src/main/java/com/senseplus/sense/NavMath.java app/src/main/java/com/senseplus/sense/EventTypes.java app/src/main/java/com/senseplus/sense/EventFormatter.java app/src/test/java/com/senseplus/sense/EventFormatterTest.java
Recommended model: Sonnet 5
Priority: nice to have. Needs 21, 23 and 24.

<!-- PASTE BELOW -->
TASK — Segment 27: Use the saved Home as a safe zone. When the user goes beyond it, alert the guardian, and help the user find their way back.

Why: the synopsis targets cognitive disabilities too. Wandering is one of the main risks for those users, and the pieces already exist: Home (21), location updates in the service, events (15), guardian notifications (24).

## Requirements
1. **Home in the cloud:**
   - When Home is saved on the user phone (segment 21: GPS or address), also write `users/{uid}.home = { lat, lon, address, radiusM, enabled }`.
   - The **guardian can set it** from the dashboard: "Set home for <name>" opens an address search (Geocoder) and writes `home`. The segment 23 rules let guardians update only `ack` and `home`.
   - The user phone picks up a guardian-set Home: `UserMonitorService` reads `home` at start and every 10 min, and copies it into the local `home_lat` / `home_lon` prefs so Navigate Home uses it too.
2. **Pure `SafeZoneRule`:**
   - Input: distance to Home, fix accuracy, radius.
   - Ignore fixes with accuracy > 100 m.
   - **Outside** = `distance − accuracy > radius + 50` (hysteresis) on **2 consecutive** fixes.
   - **Back inside** = `distance + accuracy < radius`.
   - Output: `NO_CHANGE`, `LEFT`, `RETURNED`.
3. **User phone** (`UserMonitorService`, using the existing location updates; no new location requests):
   - On LEFT / RETURNED: log `SAFE_ZONE_EXIT` / `SAFE_ZONE_RETURN` events and set `users/{uid}.safeZone = "outside" | "inside"`.
   - Optional spoken help, on by default when the profile includes Cognitive: "You are leaving home. If you need help, open Navigate Home."
4. **Guardian:**
   - 🟠 "<name> has left the safe zone (450 m from home)" with an **Open map** action
   - 🟢 "<name> is back home"
   - a chip on the dashboard
   - Use the same decider pattern as segment 24 (extend it or add `SafeZoneAlertDecider`, pure and tested).
5. **Settings** (on Navigate Home for the user, on the dashboard for the guardian): an enable switch, and a radius of 100 / 250 / 500 / 1000 m (default 250).
6. `EventFormatter`: `SAFE_ZONE_EXIT` → 🟠 "Left safe zone"; `SAFE_ZONE_RETURN` → 🟢 "Back home".

## Unit tests
`SafeZoneRuleTest`:
- one outside fix → NO_CHANGE; two in a row → LEFT
- a fix with accuracy 120 m → ignored
- inside the hysteresis band (radius + 30) → still inside
- after LEFT, distance + accuracy < radius → RETURNED
- disabled → always NO_CHANGE

Also: the decider tests, and `EventFormatter` for the two new types.

## Out of scope
Multiple zones, time-of-day schedules, the Android Geofencing API (the service's existing ~5 s updates are enough).

## Acceptance criteria (the reviewer will check these)
- [ ] Home synced both ways (user saves → cloud; guardian sets → user phone uses it)
- [ ] `SafeZoneRule` pure, with hysteresis + accuracy gate + 2-fix confirmation, tested
- [ ] Exit/return events and guardian notifications, one per transition
- [ ] No new location requests or extra polling beyond the 10-minute home refresh
- [ ] All tests pass

## Phone tests to include in your answer
1. Set Home, radius 100 m, monitoring on → walk 200 m away → guardian 🟠 with the distance.
2. Walk back → 🟢.
3. The guardian sets Home by address → the user's Navigate Home shows it within 10 min (or after restarting monitoring).
4. Standing near the edge doesn't produce repeated alerts.

COMMIT MESSAGE: "Segment 27: safe zone around Home with guardian alerts (exit/return) and guardian-set Home"
