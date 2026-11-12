# T — Optional extras (only after segments 13–20 are done and committed)

FILES: app/src/main/java/com/senseplus/sense/GuardianService.java app/src/main/java/com/senseplus/sense/GuardianActivity.java
Recommended model: GPT-OSS 120B (E1), Sonnet 5 (E2, E3)

Pick ONE extra per chat. `prompts/bundle.sh T-extras` attaches E1's files. For E2 or E3, name the extra files on the command line:
- E2: `prompts/bundle.sh T-extras app/src/main/java/com/senseplus/sense/HearingActivity.java app/src/main/java/com/senseplus/sense/CloudHelper.java app/src/main/java/com/senseplus/sense/EventTypes.java`
- E3: `prompts/bundle.sh T-extras app/src/main/java/com/senseplus/sense/HearingActivity.java app/src/main/AndroidManifest.xml app/src/main/java/com/senseplus/sense/UserMonitorService.java`

Then delete the two extras you aren't doing from the pasted text.

<!-- PASTE BELOW -->
TASK — Optional extra: [E1 / E2 / E3]

**E1 — Guardian reads alerts aloud.** When `GuardianService` posts an ATTENTION or EMERGENCY notification, the guardian phone also speaks it, e.g. "Possible fall detected. Location: <address>." Speak once per transition, not on every poll. Respect the phone's silent/DND mode: if the ringer mode is silent, don't speak. Add a "Speak alerts" toggle on the Guardian screen (SharedPreferences, default on).
Acceptance: one utterance per transition; silent mode respected; TTS shut down in `onDestroy`.

**E2 — Smoke/fire alarm logs a guardian event.** When Hearing Assist detects a fire/smoke alarm (or siren), call `CloudHelper.logEvent(EventTypes.SOUND_ALERT, "Smoke alarm heard")`. Debounce to at most one event per 60 s per sound type. Don't change the alert levels: this is information, not an emergency.
Acceptance: the debounce is a pure tested class; there is a new event type; the guardian list shows it with its own emoji.

**E3 — Hearing Assist runs in the background.** Add a "Run in background" option that moves YAMNet classification into a foreground service with `foregroundServiceType="microphone"`. Sound alerts (vibration + notification) keep working with the screen off. Respect design decision 8: if User monitoring holds the mic, refuse to start, explain why on screen, and don't fight over the mic.
Acceptance: manifest service type + permission; mic-conflict handling; the service stops cleanly; battery cost noted in Known Gaps.

Same rules and response format as always. End with the Segment Report; use `SEGMENT: E1`, `E2` or `E3`.
