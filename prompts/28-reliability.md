# Segment 28 — Reliability: resume protection after a restart, and let Hearing Assist share the microphone

FILES: app/src/main/AndroidManifest.xml app/src/main/java/com/senseplus/sense/HearingActivity.java app/src/main/java/com/senseplus/sense/UserMonitorService.java app/src/main/java/com/senseplus/sense/MainActivity.java app/src/main/java/com/senseplus/sense/HomeActivity.java README.md
Recommended model: Sonnet 5
Priority: nice to have. Needs 22 (roles).

<!-- PASTE BELOW -->
TASK — Segment 28: Two gaps that silently remove protection

1. **After the phone restarts, nothing runs** (no fall detection, no guardian alerts) until someone happens to open the app. Nobody is told.
2. **Hearing Assist and voice keywords fight over the microphone** (decision 8). Today the docs say "stop User monitoring before using Hearing Assist", which also stops fall detection for a deaf user who just wants captions.

## Requirements

### A. Resume after restart
1. Persist intent:
   - `monitoring_wanted = true` when the user starts monitoring, `false` when they stop it on purpose (sign-out also sets it false)
   - guardians: linked + signed in
2. Add a `BootReceiver` (`RECEIVE_BOOT_COMPLETED`, plus `MY_PACKAGE_REPLACED` for app updates), `exported` only as the system requires.
   - Recent Android versions **block starting `dataSync` / `microphone` foreground services from `BOOT_COMPLETED`** for apps targeting new SDKs, so don't try to start the services silently. **Check this against the current Android docs and say what you found.**
   - Instead, post a high-priority notification (new channel `reminder_v1`):
     - user: "SENSE+ protection is off after a restart. Tap to turn it back on." → opens the Safety screen with `auto_start=true`, which starts monitoring (allowed: the app is in the foreground)
     - guardian: "Tap to resume guardian alerts" → opens Home, which starts `GuardianService` (segment 22)
3. Don't post anything if the person isn't signed in or didn't want monitoring.

### B. Microphone sharing
4. A pure `MicArbiter` state class decides, from these events, whether the voice keywords should be listening:
   - `monitoringStarted` / `monitoringStopped`
   - `hearingOpened` / `hearingClosed`
   - Rule: they listen only when monitoring is on **and** Hearing Assist is closed.
5. `UserMonitorService` gets `ACTION_PAUSE_VOICE` / `ACTION_RESUME_VOICE`. Pausing stops only the Vosk listener: **fall detection, location and heartbeat keep running.**
   - Status: "Voice keywords paused while Hearing Assist is open (fall detection still on)"
   - Resume restarts recognition with the current grammar and codeword.
6. `HearingActivity` sends PAUSE in `onResume` and RESUME in `onPause`, **only if monitoring is actually running**. Use a static `UserMonitorService.isRunning()` flag, set in `onStartCommand` and cleared in `onDestroy`, and plain `startService` (the activity is in the foreground).
   - **Do not use `startForegroundService` here.** The service would then have to call `startForeground` even when it isn't running. That exact pattern was a crash bug in segment 16, fixed by routing through `sendAction`.
7. README.md "Known limitations": replace "only one feature can use the microphone at a time" with the new behaviour: Hearing Assist temporarily pauses voice keywords; fall detection keeps working.

## Unit tests
`MicArbiterTest`:
- monitoring on → listen
- Hearing opened → pause
- Hearing closed → resume
- monitoring off while Hearing is open → stays off after Hearing closes
- monitoring starts while Hearing is open → doesn't listen until Hearing closes

## Out of scope
Running Hearing Assist in the background (that's `T-extras` E3), Vosk and YAMNet sharing one audio stream.

## Acceptance criteria (the reviewer will check these)
- [ ] Reboot/update → reminder notification only for signed-in people who wanted protection; tapping restores it
- [ ] Explanation of the boot-time FGS restriction, checked against the docs
- [ ] Pausing voice keeps fall detection, location and heartbeat running
- [ ] `HearingActivity` never starts the service when it isn't running (no `startForegroundService` there)
- [ ] `MicArbiterTest` passes; README limitation updated

## Phone tests to include in your answer
1. Monitoring on → restart the phone → reminder → tap → monitoring back on.
2. Guardian phone restart → reminder → tap → alerts resume.
3. Monitoring on → open Hearing Assist → captions work; drop the phone → the fall is still detected.
4. Close Hearing Assist → "help" works again.
5. Monitoring off → open/close Hearing Assist → no crash, nothing starts.

COMMIT MESSAGE: "Segment 28: resume-after-restart reminders and microphone sharing between Hearing Assist and voice keywords"
