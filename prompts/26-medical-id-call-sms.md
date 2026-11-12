# Segment 26 — Medical ID, one-tap "Call my guardian", and SMS fallback when there's no internet

FILES: app/src/main/java/com/senseplus/sense/MainActivity.java app/src/main/res/layout/activity_main.xml app/src/main/java/com/senseplus/sense/UserMonitorService.java app/src/main/java/com/senseplus/sense/CloudHelper.java app/src/main/java/com/senseplus/sense/GuardianActivity.java app/src/main/res/layout/activity_guardian.xml app/src/main/java/com/senseplus/sense/HomeActivity.java app/src/main/res/layout/activity_home.xml app/src/main/java/com/senseplus/sense/ModeMapper.java app/src/test/java/com/senseplus/sense/ModeMapperTest.java app/src/main/AndroidManifest.xml
Recommended model: Sonnet 5
Priority: **high value**. Needs 23 (guardian phone numbers and sync).

<!-- PASTE BELOW -->
TASK — Segment 26: Give helpers the medical facts they need, let the user call their guardian in one tap, and send an SMS when the internet is down

Why:
- README.md lists "remote monitoring needs an internet connection" as a limitation. If the user's phone has no data, the guardian is never told. SMS works on plain cellular.
- A bystander or paramedic who finds the user can't see their allergies, conditions or who to call.

## Requirements

### Medical ID (role user)
1. **`MedicalIdActivity`** (a new Home card, `medical`, always shown for role user):
   - fields: name, age, blood group (spinner: A+ … O−, Unknown), allergies, conditions, medications, notes, emergency contact name + phone
   - saved locally (SharedPreferences, JSON) and synced to `users/{uid}.medical` (owner-only write, guardians can read, as in the segment 23 rules)
2. **"Show to a helper"** mode:
   - read-only, very large high-contrast text, plus a **Read aloud** button (TTS)
   - viewable **over the lock screen** (`setShowWhenLocked(true)` on API 27+, `FLAG_SHOW_WHEN_LOCKED` on 26), so a bystander can read it without unlocking. Only this read-only screen does that. This is the same trade-off as the phone's own Medical ID; say so in the answer.
3. The user phone's POSSIBLE EMERGENCY notification gets a **"Medical ID"** action that opens helper mode.
4. **Guardian dashboard:** a collapsible "Medical ID" section from `users/{uid}.medical`.

### Call my guardian (role user)
5. A big **"Call my guardian"** button on the Safety screen (`MainActivity`).
   - Number: the first guardian in `guardianInfo` with a phone, otherwise the Medical ID emergency contact.
   - If `CALL_PHONE` is granted (ask the first time, explaining why), use `ACTION_CALL`. Otherwise use `ACTION_DIAL`.
   - With no number at all: "Add your guardian's phone number in Medical ID".

### SMS fallback (opt-in, default OFF)
6. Add a switch on the Medical ID screen: "Also send an SMS to my guardians in an emergency". Turning it on requests `SEND_SMS`; if that's denied, the switch goes back off.
7. When the level enters POSSIBLE EMERGENCY and the switch is on, send **one** SMS per emergency to every guardian phone and the emergency contact:
   - text: "SENSE+: possible emergency for <name> (<reason>). Location: <maps link> (±N m). Sent automatically."
   - use `SmsManager` (`getSystemService(SmsManager.class)` on API 31+, `SmsManager.getDefault()` below) with `divideMessage` + `sendMultipartTextMessage`
   - it works without internet; that's the point
   - Log the event `SMS_SENT` with the **count only**. Never write phone numbers to logs or Firestore events.
8. Message building and phone-number cleanup go in pure classes:
   - `EmergencyText.sms(name, reason, mapsLink, accuracyM)`
   - `EmergencyText.spokenMedicalId(...)`
   - `PhoneRules.normalize(String)`: strips spaces/dashes, keeps a leading +, returns null if fewer than 7 digits

## Unit tests
- `EmergencyTextTest`: SMS contains name, reason and link; null link → "location unknown"; spoken Medical ID skips empty fields.
- `PhoneRulesTest`: "+91 98765 43210" → "+919876543210"; "98-765-43210" → "9876543210"; "12" → null; null → null.
- `ModeMapperTest`: the `medical` card for users, never for guardians.

## Out of scope
Calling emergency services automatically (112) or medical-data encryption beyond the Firestore rules.

## Acceptance criteria (the reviewer will check these)
- [ ] Medical ID saved locally + synced; helper mode readable over the lock screen; Read aloud works
- [ ] The emergency notification on the user phone has a Medical ID action
- [ ] Call button picks the right number; `CALL_PHONE` → `ACTION_CALL`, else `ACTION_DIAL`
- [ ] SMS is opt-in, sent once per emergency, uses multipart, logs only a count
- [ ] Pure text/phone classes tested; all tests pass

## Phone tests to include in your answer
1. Fill in the Medical ID → the guardian dashboard shows it.
2. Lock the user phone → trigger HELP → Medical ID action → readable without unlocking.
3. Call my guardian → it rings B.
4. SMS switch on → turn off mobile data and Wi-Fi on A → HELP → B gets the SMS with a map link.
5. A second HELP in the same emergency → no second SMS.

COMMIT MESSAGE: "Segment 26: Medical ID with lock-screen helper view, call-my-guardian button, opt-in SMS emergency fallback"
