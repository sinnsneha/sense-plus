# SENSE+

**A Smart Assistive and Safety Companion for People with Disabilities Using Android Sensors**

SENSE+ turns the sensors already inside an ordinary Android phone into one customisable assistive and safety system. It helps a person with a visual, hearing, speech, mobility or cognitive disability understand their surroundings, communicate, and stay safe — and if something goes wrong, it alerts a trusted guardian with the user's live location.

> Course project — *Fundamentals of Sensor Technology and Android Programming* (B.Tech CSE).

---

## What it does

SENSE+ is **one app with two roles**, chosen on the home screen:

- **User mode** — runs on the phone of the person being helped.
- **Guardian mode** — runs on a trusted person's phone and shows the user's live status, protected by biometric login.

### Core features

| Feature | What it does |
|---|---|
| **Fall detection (SENSE GUARD)** | Detects falls using multi-sensor *fusion* (free-fall → impact → stillness), not a single reading — cutting false alarms. |
| **Three-level safety** | `Normal → Attention → Possible Emergency`. On a fall the phone asks "Are you okay?"; no response escalates to a guardian alert. |
| **Recovery & voice response** | Cancels automatically if the user gets up (movement), says "I'm okay", or taps I'M OK. Says "help" to raise an alert hands-free; the "help" / "I'm okay" keywords use offline **Vosk**. |
| **Background monitoring** | A foreground service keeps detecting falls even when the app is closed. |
| **Guardian dashboard** | Real-time status, address, coordinates, battery, and a one-tap **Google Maps** link — updates live over Firebase. |
| **Background alerts** | The guardian's phone buzzes with a colour-coded, self-resolving notification even when its app is closed. |
| **Communication board** | Large buttons that speak phrases aloud for users who cannot talk. |
| **Visual Assist (OCR)** | Point the camera at text and it is read aloud automatically — on-device ML (Edge AI). |
| **Hearing Assist** | YAMNet (TensorFlow Lite) sound alerts — doorbell, fire/smoke alarm, siren and more — with a coloured card, vibration and a flashlight blink, plus live captions from Google `SpeechRecognizer`. |
| **Sensor Info** | Live compass + accelerometer / gyroscope / light / proximity readings and the full device sensor list. |
| **Biometric lock** | Fingerprint / device-PIN protection on the guardian view. |

---

## How the fall engine works

A fall is not a single event — it is a **sequence over time**:

```
free-fall (acceleration → ~0)  →  impact (sharp spike)  →  stillness
```

`FallDetector` is a small state machine that watches for that exact signature, with tuned guards to reject false positives (a shake has no free-fall; a table set-down has too-short a free-fall; handling the phone after a real fall is ignored by a cooldown). This multi-signal approach is the project's core novelty versus typical single-accelerometer apps.

---

## Architecture

```
HomeActivity ──► MainActivity (User)        ──► UserMonitorService  ──┐
             ├─► GuardianActivity (Guardian) ◄── GuardianService    ──┤ (both talk only to Firebase,
             ├─► CommunicationActivity                                │  never to each other)
             ├─► VisualAssistActivity                                 │
             ├─► HearingActivity                                      │
             └─► SensorInfoActivity                      Cloud Firestore
```

- **Sensing / detection** — `FallDetector`, `SensorManager`, Fused Location Provider, rotation-vector compass.
- **Response** — Text-to-Speech, vibration, notifications, offline **Vosk** for keywords, Google `SpeechRecognizer` for captions.
- **Cloud** — `CloudHelper` writes the user's status/location to Cloud Firestore; the guardian reads it (with a 2-second server-poll fallback for networks that drop the real-time stream).
- **Background** — `UserMonitorService` and `GuardianService` are foreground services (Module 5).

## Tech stack

Java · Android SDK · `SensorManager` · Fused Location Provider · CameraX · ML Kit Text Recognition (on-device) · Text-to-Speech / SpeechRecognizer · Vosk (offline speech) · TensorFlow Lite (YAMNet) · Firebase (Auth + Cloud Firestore) · BiometricPrompt · Foreground Services.

---

## Build & run

1. Clone the repo and open it in Android Studio.
2. The app uses Firebase — `google-services.json` is included for the demo project. (For your own Firebase project, replace it and enable **Cloud Firestore** + **Anonymous Authentication**.)
   The Vosk model folder `app/src/main/assets/model-en-us/` must contain the `uuid` file (any text); without it the app reports "model not found".
3. Run on a **physical Android phone** (sensors + camera needed); grant Location, Microphone, Camera and Notification permissions when asked.
4. For the two-phone demo, install on a second phone and pick **Guardian** mode on it.

## Course outcomes covered

- **C331-1.3 (Apply)** — physical sensors through the Android Sensor API.
- **C331-1.4 (Evaluate)** — multi-sensor fusion; communication technologies (Firebase).
- **C331-1.5 (Create)** — a complete, tested real-life sensor-based service.
- **Module 5** — Android services + Firebase real-time monitoring.

## Known limitations

Accuracy of OCR and voice depends on lighting and noise; continuous sensing/listening uses more battery; remote monitoring needs an internet connection (an opt-in SMS fallback can text the guardians without one); Hearing Assist and the voice keywords share the one microphone by taking turns — while Hearing Assist is open the voice keywords ("help", the codeword) are paused, and fall detection, location and the heartbeat keep working; after a phone restart or an app update Android does not let the app resume monitoring by itself, so SENSE+ posts a reminder notification and protection comes back when the person taps it (until then nothing is running); automatic detection supports — it does not replace — professional help.
