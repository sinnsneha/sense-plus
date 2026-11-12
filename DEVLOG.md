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
| 10 | **Vosk offline voice engine** replaced Android `SpeechRecognizer` in `UserMonitorService` for always-on "help" / "I'm okay" detection. |
| 11 | Voice start-up diagnostics: the User screen's status line shows *Voice ready* / *mic permission denied* / *model not found* instead of failing silently. |
| 12 | **Hearing Assist** module: YAMNet sound alerts and live captions. |
| 13 | **Gyroscope + posture sensor fusion** in `FallDetector`. The accelerometer sequence (free-fall → impact → stillness) is unchanged; a fall is now confirmed only if the **gyroscope saw rotation (≥ 3 rad/s) OR the gravity vector moved ≥ 60°** between before and after. Without a gyroscope, posture alone decides. The result (`FusionResult`) is shown on the User screen and stored in Firestore `lastEvent`, e.g. `accel ✓ · gyro ✓ 4.2 rad/s · posture ✓ 78°`; rejected drops show their numbers as "Fall check: …" and in Logcat (`SenseFusion`). The detector is pure Java (timestamps passed in) so it is unit-tested with synthetic sensor streams. **Limit, stated honestly:** fusion reliably rejects flat drops and set-downs, but a phone that tumbles and lands in a new orientation can still pass. That is why the 15 s "Are you okay?" stage exists. |
| 20 | **Visual Assist "What's around me"** — a mode toggle switches the CameraX analyzer between the existing on-device OCR path and ML Kit Image Labeling (`com.google.mlkit:image-labeling:17.0.9`, bundled base model). Label mode analyses at most one frame every 500 ms, keeps the top three labels at ≥ 70% confidence, shows them with their confidence percentages, and speaks "I see: …" at most every 4 s without repeating the same set within 10 s. The filtering and de-duplication live in the pure, unit-tested `LabelAnnouncer`. The general-purpose model recognises common objects such as door, chair, person and car; dependable stair or obstacle detection would need a custom-trained model, so the app makes no such claim. |
| 25 | **Guardian "I'm on my way"** closes the alert loop. A button on the guardian dashboard and an action on the 🟠 / 🔴 notifications (handled by the non-exported `AckReceiver`) write `users/{uid}.ack = {by, name, time: serverTimestamp}`, the only field the segment 23 rules let a guardian change. A confirmed reply cancels the insistent alarm and leaves a quiet "You told <name> you're on your way"; a reply that is not confirmed within 8 s is shown as not sent and can be retried. While the user is in ATTENTION or EMERGENCY, `UserMonitorService` reads its own document every 5 s (`Source.SERVER`) and nothing in NORMAL; the pure, unit-tested `AckRule` accepts an ack only if it is newer than the alert start (5 s clock-skew allowance), and the new alert's first status write also deletes the old ack. A fresh ack is spoken ("<name> has seen your alert and is on the way."), repeated once after 30 s, buzzed, shown in large text on the Safety screen and in the ongoing notification, and logged as `GUARDIAN_ACK` (🔵 "Guardian responding"). It never cancels or hides the alert. After the first reply the read slows to every 15 s so a second guardian can still be announced ("<name> is also coming."). |
| 26 | **Medical ID, Call my guardian, SMS fallback.** `MedicalIdActivity` (new Home card `medical`, user only) saves name, age, blood group, allergies, conditions, medications, notes and an emergency contact on the phone (per account) and copies them to `users/{uid}.medical`, which the guardian dashboard shows in a collapsible section from the document it already polls. "Show to a helper" opens `MedicalHelperActivity`: read-only, very large text, Read aloud (TTS), shown over the lock screen (`setShowWhenLocked`, plus `android:showWhenLocked` so the notification button can open it without the unlock prompt). It is the only screen that does this, the same trade-off as a phone's own Medical ID; the possible-emergency notification gets a "Medical ID" button that opens it. The Safety screen has a "Call my guardian" button: the first guardian phone, else the emergency contact; `ACTION_CALL` if `CALL_PHONE` is granted (asked once, with a reason), otherwise `ACTION_DIAL`. An opt-in switch (default off; asks for `SEND_SMS`, turns itself back off if refused) sends one `SmsManager` multipart SMS per emergency to every guardian phone and the emergency contact over the cellular network, so it works with no internet. The guardians' phones are read from a local copy refreshed while online, because the SMS cannot read Firestore when it matters. The `SMS_SENT` event stores the count only, never a number. The wording and number rules are pure and unit-tested (`EmergencyText`, `PhoneRules`, `MedicalId`). The SMS is plain ASCII with a 5-decimal map link so a typical message fits one 160-character SMS. Delivery is not confirmed (no sent/delivered receipts). |
| 27 | **Safe zone around Home.** Home now lives in `users/{uid}.home = {lat, lon, address, radiusM, enabled, updatedAt}` and is synced both ways: saving Home on Navigate Home writes it (`HomeStore`), and the guardian can set it from the dashboard ("Set home for <name>", Geocoder address search; the segment 23 rules already let a guardian change only `ack` and `home`). `UserMonitorService` reads its own document at start and every 10 minutes and copies a newer guardian-set Home into the `home_lat` / `home_lon` prefs Navigate Home reads (`HomeData.cloudReplacesLocal`: the newer `updatedAt` wins, so a stale cloud copy never undoes a fresh local save). The decision is the pure, unit-tested `SafeZoneRule`, fed by the location updates the service already gets (no new location request): fixes worse than 100 m are ignored, outside = `distance − accuracy > radius + 50` on two fixes in a row, back inside = `distance + accuracy < radius`, and each state change gives one `LEFT` / `RETURNED`. Each transition logs `SAFE_ZONE_EXIT` / `SAFE_ZONE_RETURN` and writes `safeZone = "outside" | "inside"`. The guardian's phone turns that into one 🟠 "<name> has left the safe zone (450 m from home)" notification with Open map, or one 🟢 "is back home" (`SafeZoneAlertDecider`, the same pattern as segment 24), and the dashboard shows a chip plus the switch, a 100 / 250 / 500 / 1000 m radius and the set-home button; the user has the same switch and radius on Navigate Home, plus spoken help ("You are leaving home. If you need help, open Navigate Home."), on by default when the profile includes Cognitive. The safe zone is OFF until someone switches it on, so ordinary trips out do not alert. The 10-minute read also keeps segment 26's copy of the guardians' phones fresh, replacing its separate read at start: the only extra polling is 144 reads a day. |
| 28 | **Resume-after-restart reminders and microphone sharing.** (A) After a restart or an app update nothing ran and nobody was told. `monitoring_wanted` is now saved when the user starts monitoring and cleared when they stop it or sign out. A `BootReceiver` (`BOOT_COMPLETED` + `MY_PACKAGE_REPLACED`, `exported=false`) asks the pure `BootReminderRule` and, for a signed-in user who wanted monitoring or a signed-in linked guardian, posts a high-priority notification on the new `reminder_v1` channel; nobody else gets one. It starts no service, because the Android docs say a `BOOT_COMPLETED` receiver may not start `dataSync` or `microphone` foreground services (Android 15 for apps targeting 15+, `microphone` since 14) and that location/camera/microphone services cannot be created from the background at all. The tap opens Home → the Safety screen with `auto_start=true` (user), which starts monitoring, or Home (guardian), which starts `GuardianService`; both are allowed because the app is then in the foreground. (B) Hearing Assist no longer forces the user to stop monitoring. The pure `MicArbiter` says the voice keywords listen only while monitoring is on and Hearing Assist is closed; `HearingActivity` sends `ACTION_PAUSE_VOICE` in `onResume` and `ACTION_RESUME_VOICE` in `onPause` only if `UserMonitorService.isRunning()`, using plain `startService` (never `startForegroundService`, the segment 16 crash pattern). Pausing stops only the Vosk listener; fall detection, location and the heartbeat keep running, and resuming restarts recognition with the current grammar and codeword. |

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
*Fix (partial):* partial results + ignoring the mic while the phone is speaking (so it doesn't hear its own prompt). Reliable always-on keyword spotting would need a dedicated engine (Porcupine/Vosk) — noted as future work; meanwhile movement-recovery and the on-screen/notification buttons are robust fallbacks. This was resolved in segment 10, when Vosk replaced `SpeechRecognizer` for keywords (see bug 10).

**9. Sensor Info crash.** Tapping the new screen crashed.
*Fix:* `SensorInfoActivity` was missing from `AndroidManifest.xml` — registered it.

**10. Porcupine wake-word engine could not be used.** Picovoice sign-up only accepts company email addresses, and it rejected both Gmail and the college email.
*Fix:* switched to **Vosk** (open-source, offline, no account needed).

**11. Microphone permission dialog never appeared.** The mic had been denied in an earlier test. Android then stops showing the dialog, so the service silently skipped voice.
*Fix:* uninstall the app and reinstall it to reset permission state, then grant the mic. The service now shows a status message when the permission is missing.

**12. "Voice model not found" even though the model folder was correct.** Vosk's `StorageService.unpack()` needs a version file named `uuid` inside the model folder. The downloaded model does not include one; Vosk's own demo generates it during the build.
*Fix:* added `app/src/main/assets/model-en-us/uuid` containing any text (`senseplus-1`).

**13. Live captions were inaccurate with Vosk.** The small `en-us` model has about a 10% word error rate even on clean American speech, and much worse with an Indian accent and room noise.
*Fix:* captions use Google `SpeechRecognizer` with `en-IN`. Emergency keywords stay on Vosk (design decision 2 in HANDOVER.md).

**14. "I'm not okay" cancelled the alert, and "help" during a fall alert was ignored.** While an alert was active the service cancelled it whenever the recognised text *contained* "okay", so "I'm not okay" counted as "I'm okay". During the 15 s "Are you okay?" stage only "okay" was listened for, so "help" did nothing.
*Fix:* a pure `VoiceCommandParser` splits the text into whole words (so "helpful" is not "help") and checks HELP first: the word "help", or "not"/"no" followed within two words by okay / fine / alright. Only then OKAY. During ATTENTION, HELP now goes straight to Possible Emergency and OKAY cancels; during EMERGENCY, HELP is ignored (no re-announcing). Covered by `VoiceCommandParserTest`.

## Design principle

The app never delivers information through a channel the person cannot use: the same event becomes **voice + vibration** for a blind user, **screen + vibration** for a deaf user, **spoken phrases** for a non-speaking user. Blind users navigate via the OS screen reader (TalkBack), which the labelled controls support.
