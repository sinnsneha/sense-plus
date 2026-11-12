# SENSE+ — Remaining Work Plan

This plan comes from comparing the synopsis (`sensor_synopsis_final.docx`) with what is built. Items are ordered by **how much leaving them out would cost in marks**, so do them top to bottom. Each one is meant to be a single segment: build it, test it on the phone, commit, then move on.

For what already exists, see HANDOVER.md section 2.

## Synopsis coverage at a glance

| Synopsis feature | Status |
|---|---|
| User app + Guardian dashboard | ✅ Done |
| Three-level safety (Normal / Attention / Possible Emergency) | ✅ Done |
| Manual HELP button | ✅ Done |
| Guardian: location, address, battery, biometric login | ✅ Done |
| OCR text reading with voice | ✅ Done |
| Communication panel (large phrase buttons) | ✅ Done (fixed phrases only) |
| Hearing Assist: sound alerts + speech-to-text | ✅ Done (segment 12) |
| **Sensor fusion** (accelerometer + gyroscope + mic) | ⚠️ Fall detection uses the accelerometer only |
| **Navigation** (GPS + compass, direction and distance) | ❌ Not built |
| **Guardian event history / timeline** | ❌ Not built |
| **Private emergency codeword** set by the user | ❌ Not built (voice listens for "help" only) |
| **Custom phrases** | ❌ Not built |
| **Profile setup / choose assistance modes / link guardian** | ❌ Not built |
| **Guardian connectivity status** | ⚠️ Only "last update" time |
| **Object detection** ("Stairs detected ahead") | ❌ Visual Assist only does OCR |

---

## Priority 1: Gyroscope sensor fusion in fall detection

**Why:** The synopsis problem statement criticises single-sensor fall detection ("a dropped phone can look like a fall"), and fusion is the core topic of the course. Expect "where is the fusion?" in the viva.

**Approach:**
- In `UserMonitorService`, also register `Sensor.TYPE_GYROSCOPE` and pass angular speed (`sqrt(gx²+gy²+gz²)`, rad/s) to `FallDetector`.
- In `FallDetector`, accept an impact as a fall only if there was a **rotation spike** (for example over 3 rad/s) inside the free-fall/impact window. A body falling over rotates; a phone dropped flat often doesn't. Optionally also compare the **gravity direction before and after** (orientation change over 60° means the person ended up lying down).
- Escalation rule from the synopsis methodology: a fall plus "help" or the codeword heard within N seconds goes **straight to Possible Emergency**, skipping Attention.
- Show the gyro value on the Sensor Info screen and log which sensors agreed, so the fusion is visible in the demo.

**Files:** `FallDetector.java`, `UserMonitorService.java`

## Priority 2: Navigation module (GPS + compass)

**Why:** It is a whole module in the synopsis, and the compass covers the magnetometer and rotation-vector topics in syllabus Module 3.

**Approach:**
- New `NavigationActivity` with a Home card.
- "Save this place as Home", stored in SharedPreferences as lat/lon.
- Compass from `TYPE_ROTATION_VECTOR` → `getRotationMatrixFromVector` → `getOrientation` gives the azimuth.
- `Location.distanceBetween()` and `location.bearingTo(home)` give distance and bearing. Draw an arrow that rotates by `bearing − azimuth`.
- Speak guidance with TTS ("Home is 320 metres, turn slightly left") every few seconds or when the user taps. Use a large accessible layout.

**Files:** new `NavigationActivity.java`, `activity_navigation.xml`, arrow drawable; `HomeActivity`, `activity_home.xml`, `AndroidManifest.xml`

## Priority 3: Guardian event history / timeline

**Why:** The synopsis lists it twice (objectives and dashboard). It is quick to build and makes the dashboard look finished.

**Approach:**
- In `CloudHelper`, add `logEvent(type, detail, lat, lon)`, which writes to the sub-collection `sense_users/demoUser/events` with a server timestamp.
- Call it from `UserMonitorService` on every level change (Attention, Emergency, All clear), on manual HELP and on the voice command.
- In `GuardianActivity`, poll the last 20 events ordered by time (same 2 s poll, `Source.SERVER`) and show them as a list with a time, icon and colour.

**Files:** `CloudHelper.java`, `UserMonitorService.java`, `GuardianActivity.java`, `activity_guardian.xml`

## Priority 4: Private emergency codeword + more accurate keywords

**Why:** It is in the objectives, the proposed system and the advantages ("hands-free help option").

**Approach:**
- A small settings screen (or a dialog on the User screen) where the user types a codeword, stored in SharedPreferences.
- In `UserMonitorService.startRecognition()`, create the Vosk recognizer with a **restricted grammar**:
  `new Recognizer(model, 16000f, "[\"help\", \"okay\", \"i'm okay\", \"fine\", \"<codeword>\", \"[unk]\"]")`.
  Restricting the vocabulary makes keyword spotting much more accurate, because Vosk only has to choose between those words.
- Codeword → `triggerEmergency("Codeword heard")`.
- Note: the codeword must be a word in the Vosk model's vocabulary (ordinary English words work; invented words do not).

**Files:** `UserMonitorService.java`, a new settings activity or dialog

## Priority 5: Custom phrases in the Communication board

**Approach:** "+ Add phrase" button → text input → saved to SharedPreferences (a JSON array) → shown after the default phrases. Long-press a custom phrase to delete it.

**Files:** `CommunicationActivity.java`, `activity_communication.xml`

## Priority 6: First-launch profile and assistance modes

**Why:** This backs up the "one customisable app" claim.

**Approach:**
- On first launch, show a screen with checkboxes for Visual, Hearing, Communication, Mobility and Cognitive, saved to SharedPreferences.
- `HomeActivity` shows only the cards for the chosen modes, with an "Edit profile" option. Safety and Guardian are always shown.
- Guardian linking: replace the fixed `USER_ID = "demoUser"` in `CloudHelper` with a generated 6-digit pairing code. The user's phone shows it and the guardian types it once. Optional; keep `demoUser` as the fallback for the demo.

## Priority 7: Guardian connectivity status

**Approach:** The user phone writes `lastSeen` on every update (it already writes the time). The guardian computes how long ago that was: **Online** (under 1 min), **Last seen X min ago**, or **Offline / no signal** (over 5 min) in grey. If monitoring is meant to be on, a long silence is itself worth showing.

**Files:** `GuardianActivity.java`

## Priority 8: Object detection in Visual Assist

**Approach:** Add a mode toggle in `VisualAssistActivity` for **Read text** or **What's around me**. Use ML Kit **Image Labeling** (`com.google.mlkit:image-labeling`) or **Object Detection** on the same CameraX frames, and speak the top labels above about 70% confidence, with a cooldown. Be honest in the report: general models recognise common objects (door, chair, person, car), and dependable stair detection would need a custom-trained model.

## Optional extras

- Guardian phone reads alerts aloud with TTS ("Possible fall detected. Location: …").
- Hearing Assist: a smoke or fire alarm also logs an event to the guardian (it can reuse Priority 3).
- Hearing Assist: a "Run in background" option, so sound alerts keep working with the screen off (needs a foreground service with a microphone type).

---

## Documentation tasks (needed for marks)

1. **Update the Word report** (`SENSE_Plus_Project_Log.docx`) with segments 10–12, bugs 10–13 (see HANDOVER.md) and each new segment from this plan as it is built.
2. **Explain where the build differs from the synopsis**, so the examiner doesn't read it as a mismatch:
   - Java instead of Kotlin; no MVVM or Coroutines (Activities plus foreground services, with background threads where needed).
   - Polling plus a foreground service instead of Firebase Cloud Messaging, and why (HANDOVER.md decision 7).
   - Vosk (offline) for keywords and Google SpeechRecognizer for captions, and why (HANDOVER.md decision 2).
   - YAMNet (TensorFlow Lite) is the "sound classification" model.
3. **Update README.md and DEVLOG.md** in the repo with the new segments and bugs.
4. **Viva prep:** be ready to explain the fall state machine, the three-level response, the polling decision, sensor fusion, and what on-device ML models run (ML Kit OCR, YAMNet, Vosk).

## Testing checklist before submission

- [ ] Drop the phone onto a bed: Attention → say "I'm okay" or move → back to Normal
- [ ] Fall with no response: Emergency after 15 s → guardian notification with location
- [ ] HELP button and the voice word "help" → guardian alert
- [ ] Guardian dashboard updates within about 2 s; "All clear" after I'M OK
- [ ] Visual Assist reads printed text aloud
- [ ] Hearing Assist: doorbell, siren or horn from a video → card, vibration, flashlight; captions show speech
- [ ] Communication phrases speak aloud
- [ ] Biometric prompt before the Guardian screen
- [ ] Each new segment from this plan
