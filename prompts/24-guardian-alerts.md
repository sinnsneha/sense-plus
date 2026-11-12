# Segment 24 — Guardian alerts that can't be missed: early warning, loud alarm, call/map actions, low-battery and lost-contact alerts

FILES: app/src/main/java/com/senseplus/sense/GuardianService.java app/src/main/java/com/senseplus/sense/GuardianActivity.java app/src/main/res/layout/activity_guardian.xml app/src/main/java/com/senseplus/sense/CloudHelper.java app/src/main/java/com/senseplus/sense/UserMonitorService.java app/src/main/java/com/senseplus/sense/ConnectivityStatus.java app/src/main/java/com/senseplus/sense/EventTypes.java app/src/main/java/com/senseplus/sense/EventFormatter.java app/src/test/java/com/senseplus/sense/EventFormatterTest.java app/src/main/AndroidManifest.xml
Recommended model: Sonnet 5
Priority: **high value**. Needs 23.

<!-- PASTE BELOW -->
TASK — Segment 24: Make every guardian alert noticeable and useful, and warn the guardian *before* protection silently stops

Problems found in review:
- `GuardianService` only notifies on possible emergency and all-clear. There's **no** notification when a possible fall starts (ATTENTION), although older docs claim one, so the guardian learns nothing during the user's 15 s "Are you okay?" window.
- The emergency notification:
  - plays the default sound once, which is easy to miss
  - can't be tapped to open anything
  - has no "call" or "map" actions
- `battery` only reaches Firestore on status changes, so the dashboard's battery can be hours old, and nobody is warned before the user's phone dies. A dead phone silently ends all protection.
- The dashboard shows "Offline" (segment 19), but a guardian who isn't looking at the dashboard is never told the user's phone went silent.

## Requirements

1. **A pure decision class** `GuardianAlertDecider`:
   - Input: previous status, new status.
   - Output: `NONE`, `ATTENTION` (🟠), `EMERGENCY` (🔴), `ALL_CLEAR` (🟢), `FALSE_ALARM_RESOLVED` (🟢, for ATTENTION → NORMAL).
   - `GuardianService` uses it. One notification per transition, and each new one replaces the previous one (same notification ID).
2. **🟠 Attention:** "Possible fall — waiting for <name> to respond (15 s)". Normal priority alert, vibration.
3. **🔴 Possible emergency:**
   - **New channel `guardian_alarm_v3`** (decision 5): IMPORTANCE_HIGH, alarm sound `RingtoneManager.getDefaultUri(TYPE_ALARM)` with `AudioAttributes.USAGE_ALARM`, and a strong vibration pattern. `setBypassDnd(true)` is best effort (Android only honours it with notification-policy access); say so.
   - Set `Notification.FLAG_INSISTENT` on the built notification, so the alarm repeats until it's opened or cancelled.
   - Tapping it opens `GuardianActivity` (`PendingIntent` with `FLAG_IMMUTABLE`). It skips the biometric lock, because speed matters in an emergency. Write this down as a decision.
   - Actions: **Open map** (the user's maps link) and **Call <name>** (`ACTION_DIAL tel:` with `users/{uid}.phone`, only shown if a phone number exists).
   - Text: name, reason (`lastEvent`), address, battery, time.
4. **Battery:**
   - The user phone's 30 s heartbeat also writes `battery` (one line in `CloudHelper.updateMonitoring` / the heartbeat).
   - New pure `BatteryAlertRule` (hysteresis: alert once below 15 %, re-arm above 20 %, ignore −1).
   - Guardian: 🟠 "<name>'s phone battery is low (12 %). Protection stops if it dies."
   - User phone, using the same rule class: speak "Battery low, please charge the phone" once, and log a `LOW_BATTERY` event.
5. **Lost contact:**
   - Using `ConnectivityStatus`: when `monitoring == true` and the state becomes `OFFLINE`, notify 🟠 "Lost contact with <name>'s phone since 14:32" once.
   - When it's back to ONLINE: 🟢 "Back in contact with <name>".
   - Don't notify for `MONITORING_OFF` (the user turned it off on purpose).
6. **Dashboard:** the battery shows its age ("78 % · 30 s ago"). `EventFormatter` maps `LOW_BATTERY` (🟠 "Low battery").
7. Keep the existing 🟢 all-clear behaviour and the existing `guardian_alert_v2` channel for non-alarm notifications.

## Unit tests
- `GuardianAlertDeciderTest`: NORMAL→ATTENTION, ATTENTION→POSSIBLE EMERGENCY, NORMAL→POSSIBLE EMERGENCY (manual HELP), EMERGENCY→NORMAL, ATTENTION→NORMAL, NORMAL→NORMAL, null previous status on first poll (no alert for NORMAL; alert for an emergency already in progress).
- `BatteryAlertRuleTest`: once below 15; not again at 14 or 16; re-arms above 20; −1 ignored.
- `EventFormatterTest`: `LOW_BATTERY`.

## Out of scope
The "I'm on my way" acknowledgement (segment 25), SMS (segment 26), safe zone (segment 27).

## Acceptance criteria (the reviewer will check these)
- [ ] All notification decisions go through the pure `GuardianAlertDecider` (tested)
- [ ] The emergency uses the new `guardian_alarm_v3` channel with alarm audio attributes + `FLAG_INSISTENT`; tap opens the dashboard; Open map / Call actions
- [ ] The heartbeat carries `battery`; low-battery alert once per discharge on both phones
- [ ] Lost-contact and back-in-contact notifications, none for MONITORING_OFF
- [ ] No extra Firestore reads added (everything comes from the doc already being polled)
- [ ] All tests pass

## Phone tests to include in your answer
1. Drop the user phone → guardian gets 🟠 within the 15 s window.
2. No answer → 🔴 alarm keeps ringing until opened.
3. Tap → dashboard. Call → dialer with the number.
4. User says "I'm okay" during 🟠 → 🟢.
5. User phone below 15 % (or temporarily raise the threshold) → both phones warn once.
6. User phone in airplane mode for 6 min → guardian gets "Lost contact".
7. Back online → "Back in contact".

COMMIT MESSAGE: "Segment 24: guardian early warning, insistent emergency alarm with call/map actions, low-battery and lost-contact alerts"
