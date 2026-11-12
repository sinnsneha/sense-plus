# SENSE+ — Handover Document

This document is for whoever takes over the project. It explains what SENSE+ is, what has been built so far, how to get it running, and the decisions you should know about before changing things. The remaining work is in **PLAN.md**.

Read the documents in this order:

1. **HANDOVER.md** (this file): context and current state
2. **PLAN.md**: what is left to build, in priority order
3. **README.md**: project overview, architecture, tech stack
4. **DEVLOG.md**: segment-by-segment build history (segments 0–12) and bugs 1–13.

---

## 1. The project in one paragraph

SENSE+ is an Android app for people with disabilities, built as the PBL project for *Fundamentals of Sensor Technology and Android Programming* (26B16CS319, B.Tech CSE, JIIT). It turns the sensors already in a phone (accelerometer, gyroscope, light, GPS, compass, microphone, camera) into help the user can perceive: voice, text, vibration and colour. It has two sides. The **User app** is for the person with a disability. The **Guardian dashboard** is for a trusted person, who receives location, battery, safety status and alerts. The core safety feature, **SENSE GUARD**, detects possible falls and uses a three-level response: **Normal → Attention ("Are you okay?") → Possible Emergency**, which alerts the guardian.

| | |
|---|---|
| Repository | https://github.com/sinnsneha/sense-plus |
| Package | `com.senseplus.sense` |
| Language | **Java**. The synopsis says Kotlin; the team switched to Java on purpose. |
| Min / target SDK | 26 / 37 |
| Build | Gradle Kotlin DSL, AGP 9.5.1, Gradle 9.6.0 |
| Backend | Firebase anonymous Auth and Cloud Firestore (`google-services.json` is committed) |
| Test device | Samsung Galaxy A55 (user phone) plus a second Android phone as guardian |

---

## 2. What is built (current state)

Segments 0–12 are done and were tested on a real phone.

| Screen / component | Main files | What it does |
|---|---|---|
| **Home** | `HomeActivity`, `activity_home.xml` | Card-style menu: User, Guardian, Communication, Visual Assist, Hearing Assist, Sensor Info. Guardian access is protected by biometrics. |
| **User (SENSE GUARD)** | `MainActivity`, `UserMonitorService`, `FallDetector` | Starts a foreground service that runs fall detection in the background: accelerometer state machine, free-fall → impact → stillness. Uses the three-level response with TTS, vibration and notifications, recovery-by-movement, manual **HELP** / **I'M OK** buttons and offline voice commands ("help", "I'm okay"). Pushes status, battery and location to Firestore. |
| **Guardian** | `GuardianActivity`, `GuardianService` | Dashboard with status (colour-coded), address, coordinates, Maps link, battery and last update. A background service polls Firestore every 2 s and posts alert notifications (🟠 attention / 🔴 emergency / 🟢 all clear). |
| **Communication board** | `CommunicationActivity` | Large phrase buttons (HELP, YES, NO, I NEED WATER, …) spoken aloud with TTS. |
| **Visual Assist** | `VisualAssistActivity` | CameraX plus ML Kit on-device OCR. Reads text it sees aloud automatically, with a READ ALOUD button. |
| **Hearing Assist** | `HearingActivity`, `activity_hearing.xml`, `ic_ear.xml` | **Sound alerts:** the YAMNet TensorFlow Lite model classifies mic audio every 0.5 s. Doorbell, fire/smoke alarm, siren, horn, alarm, phone, baby crying, glass, shouting and dog each produce a coloured card, a distinct vibration pattern and a flashlight blink for urgent sounds, plus a history list. **Live captions:** Google `SpeechRecognizer` (en-IN) turns speech into large text. |
| **Sensor Info** | `SensorInfoActivity` | Live sensor readings and the list of sensors on the device. |
| Helpers | `CloudHelper`, `LocationHelper` | Firestore reads and writes; Fused Location plus reverse geocoding. |

### Segments added after DEVLOG.md was written

| # | What was built |
|---|---|
| 10 | **Vosk offline voice engine** replaced Android `SpeechRecognizer` in `UserMonitorService` for always-on "help" / "I'm okay" detection. |
| 11 | Voice start-up diagnostics: the User screen's status line shows *Voice ready* / *mic permission denied* / *model not found* instead of failing silently. |
| 12 | **Hearing Assist** module: YAMNet sound alerts and live captions. |

### Bugs found and fixed after DEVLOG.md (continues its numbering)

**10. Porcupine wake-word engine could not be used.** Picovoice sign-up only accepts company email addresses, and it rejected both Gmail and the college email.
*Fix:* switched to **Vosk** (open-source, offline, no account needed).

**11. Microphone permission dialog never appeared.** The mic had been denied in an earlier test. Android then stops showing the dialog, so the service silently skipped voice.
*Fix:* uninstall the app and reinstall it to reset permission state, then grant the mic. The service now shows a status message when the permission is missing.

**12. "Voice model not found" even though the model folder was correct.** Vosk's `StorageService.unpack()` needs a version file named `uuid` inside the model folder. The downloaded model does not include one; Vosk's own demo generates it during the build.
*Fix:* added `app/src/main/assets/model-en-us/uuid` containing any text (`senseplus-1`).

**13. Live captions were inaccurate with Vosk.** The small `en-us` model has about a 10% word error rate even on clean American speech, and much worse with an Indian accent and room noise.
*Fix:* captions use Google `SpeechRecognizer` with `en-IN`. Emergency keywords stay on Vosk (see decision 2 below).

---

## 3. Getting it running on a new machine

1. Install the **latest Android Studio**. Older versions cannot open an AGP 9.5.1 project.
2. Use **File → New → Project from Version Control** and paste the repo URL. Trust the project and let the first Gradle sync finish (10–15 min). Accept any "install missing SDK platform" prompts.
3. Check that the **model files** are present. They are binary assets that must be in the repo:
   - `app/src/main/assets/model-en-us/` should contain `am/ conf/ graph/ ivector/ README` **and `uuid`**. This is the Vosk model `vosk-model-small-en-us-0.15`, and its contents sit directly in this folder with no extra nested folder.
   - `app/src/main/assets/yamnet.tflite` is the YAMNet classification model with metadata.
4. **Uninstall SENSE+ from both phones before the first Run.** Each computer signs debug builds with its own key, so installing over an app built on another machine fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
5. Enable USB debugging, accept the "Allow USB debugging" prompt and select the **physical phone** in the device dropdown. The emulator's microphone is not reliable for voice.
6. Grant **mic, location, notifications and camera** when asked.

**Known limitation while testing:** only one feature can use the microphone at a time. Stop User monitoring before testing Hearing Assist.

---

## 4. Design decisions (keep them, or know why you're changing them)

1. **Guardian uses 2-second polling, not a Firestore real-time listener.** The listener's persistent connection was silently dropped by the test Wi-Fi, which made alerts lag about 60 s. Polling with `get(Source.SERVER)` is fast and has no stream to drop. A plain `get()` returned a stale cache, so `Source.SERVER` is deliberate. See DEVLOG bug 5.
2. **Two speech engines on purpose.** Always-on emergency keywords use **Vosk**: offline, continuous, and the audio never leaves the phone, as the synopsis says ("microphone processed on the device"). **Captions** use **Google SpeechRecognizer**: whole sentences need accuracy, and captions are short sessions the user starts. Google's recognizer was already tried for keywords and missed words between restarts, which is why it was dropped there.
3. **Fall detection is a state machine, not a threshold.** Free-fall (minimum duration) → impact → stillness, with a post-fall cooldown, detection paused during a response and a grace period before movement counts as recovery. Bugs 1–4 in DEVLOG explain each piece.
4. **Alerts say "possible emergency", never "emergency".** This follows the synopsis. Automatic detection supports human help; it does not replace it.
5. **Notification channel IDs are versioned** (`user_alert_v2`, etc.). Channel settings cannot change after creation, so change the ID if you change vibration or importance.
6. **Single user/guardian pair.** `CloudHelper` writes to a fixed Firestore document `sense_users/demoUser`. This is fine for the demo; real pairing is in PLAN.md.
7. **Polling plus a foreground service instead of Firebase Cloud Messaging.** FCM push needs a server (Cloud Functions on the paid Blaze plan). The report should say this was a deliberate substitution.
10. **Tapping a guardian alert opens the dashboard without the biometric lock.** The fingerprint/PIN check protects the Guardian *card on Home* (so someone who picks up the phone can't browse a person's location). The notification is different: it fires in an emergency, where seconds matter, and the guardian is already being shown the alert. `GuardianService` therefore sends the tap straight to `GuardianActivity`. The notification text itself shows the user's address, so the phone's lock screen settings (hide sensitive content) remain the privacy control there. Introduced in segment 24.
11. **The emergency alarm is its own channel (`guardian_alarm_v3`).** Alarm audio attributes and `FLAG_INSISTENT` make it ring until the guardian opens or dismisses it. "Bypass Do Not Disturb" is requested but best effort: Android honours it only after the user allows it for the app. Early warning, all-clear, battery and lost-contact notifications stay on `guardian_alert_v2` so only a real emergency is loud. Introduced in segment 24.

---

## 5. Documents outside the repo

- **Project report (Word):** `SENSE_Plus_Project_Log.docx` was produced during development and covers segments 0–9 plus the bug summary. It needs segments 10–12 and bugs 10–13 added, plus the decisions in section 4.
- **Synopsis:** `sensor_synopsis_final.docx`. This is what the project is marked against. PLAN.md lists every synopsis feature that is not built yet.
