# SENSE+ — Development Log

A record of how the project was built segment by segment, and — importantly — the **bugs we hit and how we fixed them**. The debugging story is part of the engineering, not a footnote.

## Build segments

| # | What was built |
|---|---|
| 0 | Android Studio project, GitHub repo, runs on a physical phone |
| 1 | Sensor Diagnostics — live accelerometer / gyroscope / light / proximity via `SensorManager` |
| 2 | Motion magnitude, Text-to-Speech, vibration |
| 3 | **SENSE GUARD** — fall detection engine + three-level safety response |
| 4 | Location (Fused Provider) + compass (rotation vector) |
| 5 | Firebase — anonymous auth + push status/location to Cloud Firestore |
| 6 | Guardian dashboard (real-time) + two-phone demo |
| 6.5 | Guardian background monitoring (foreground service) + alert notifications |
| 6.6 | User background monitoring (foreground service) + voice ("help" / "I'm okay") |
| 7 | Communication board (TTS) + biometric lock |
| 8 | Visual Assist — on-device OCR (ML Kit) that reads text aloud |
| 9 | Full UI/UX redesign (card layout, gradient headers, custom icon) + Sensor Info screen |

## Bugs found and fixed

**1. Shake vs. fall flip-flop.** Classifying each sensor reading on its own made a drop register as *shake → free-fall → impact* in turn, and the voice stuttered over each.
*Fix:* a fall cannot be read from one instant — it is a sequence. Built `FallDetector` as a state machine that looks for free-fall → impact → stillness over time.

**2. Setting the phone on a table triggered a "fall".** A brief dip + tap + rest mimicked the fall signature.
*Fix:* required a **minimum free-fall duration** — a real fall is weightless far longer than a set-down.

**3. A false fall right after a real one.** Picking the phone up and moving it after a fall re-triggered detection.
*Fix:* a **post-fall cooldown**, and structurally, detection is paused while a response is already in progress.

**4. Recovery vs. the app's own vibration.** The alert's own buzz was briefly read as the user "moving".
*Fix:* a short **grace period** ignores movement right after the alert fires.

**5. Guardian alert lagged ~60 seconds.** Firebase's real-time listener held a persistent connection that the test Wi-Fi silently dropped; the SDK only noticed on its ~60 s keepalive, and the stuck stream even blocked direct reads.
*Fix:* isolated it (writes were instant, the console proved the server was fine, and the lag followed the *listener*, not the phone). Replaced the listener with a **2-second `get(Source.SERVER)` poll** — fast independent reads with no persistent stream to drop. Diagnosed `Source.SERVER` specifically because a plain `get()` returned the dead listener's **stale cache**.

**6. Stale guardian notification.** An emergency notification lingered after the user pressed I'M OK.
*Fix:* notification lifecycle — the emergency notice is cancelled and an "All clear" is posted when status returns to normal.

**7. Notification not noticeable / wrong colour.** Vibration wouldn't apply, and the heads-up showed the wrong accent.
*Fix:* notification-channel settings are **immutable after creation**, so the channel ID was versioned to apply vibration; colour-coded accents + emoji title indicators (🔴/🟠/🟢) were added because the OEM shows the launcher icon in the pop-up.

**8. Voice recognition misses.** Android's `SpeechRecognizer` listens in bursts with gaps and is not a true always-on wake-word engine.
*Fix (partial):* partial results + ignoring the mic while the phone is speaking (so it doesn't hear its own prompt). Reliable always-on keyword spotting would need a dedicated engine (Porcupine/Vosk) — noted as future work; meanwhile movement-recovery and the on-screen/notification buttons are robust fallbacks.

**9. Sensor Info crash.** Tapping the new screen crashed.
*Fix:* `SensorInfoActivity` was missing from `AndroidManifest.xml` — registered it.

## Design principle

The app never delivers information through a channel the person cannot use: the same event becomes **voice + vibration** for a blind user, **screen + vibration** for a deaf user, **spoken phrases** for a non-speaking user. Blind users navigate via the OS screen reader (TalkBack), which the labelled controls support.
