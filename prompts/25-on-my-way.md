# Segment 25 — "I'm on my way": the guardian answers, and the user hears that help is coming

FILES: app/src/main/java/com/senseplus/sense/GuardianService.java app/src/main/java/com/senseplus/sense/GuardianActivity.java app/src/main/res/layout/activity_guardian.xml app/src/main/java/com/senseplus/sense/UserMonitorService.java app/src/main/java/com/senseplus/sense/MainActivity.java app/src/main/java/com/senseplus/sense/CloudHelper.java app/src/main/java/com/senseplus/sense/FirestorePaths.java app/src/main/java/com/senseplus/sense/EventTypes.java app/src/main/java/com/senseplus/sense/EventFormatter.java app/src/test/java/com/senseplus/sense/EventFormatterTest.java app/src/main/AndroidManifest.xml
Recommended model: Sonnet 5
Priority: **high value**. Needs 23 and 24.

<!-- PASTE BELOW -->
TASK — Segment 25: Close the loop. Today an alert goes one way: the person who fell never knows whether anyone saw it. Let the guardian answer "I'm on my way", and have the user's phone say so out loud.

## Requirements

### Guardian side
1. An **"I'm on my way"** button on the dashboard, visible only while the status is ATTENTION or POSSIBLE EMERGENCY. Add the same action to the 🟠 / 🔴 notifications from segment 24, handled by a new `AckReceiver` (`BroadcastReceiver`, `exported="false"`).
2. Acknowledging writes `users/{uid}.ack = { by: <guardian uid>, name: <guardian name>, time: FieldValue.serverTimestamp() }`. The segment 23 rules already let guardians update only `ack`.
3. After acknowledging:
   - the insistent alarm stops: cancel it, and re-post a quiet notification "You told <name> you're on your way"
   - the button shows "Sent: <name> has been told"
   - if the write fails (no network), show it clearly and keep the button enabled

### User side
4. While the level is ATTENTION or EMERGENCY, `UserMonitorService` reads its own `users/{uid}` doc every **5 s** (`Source.SERVER`). Stop as soon as an acknowledgement is seen, or the level returns to NORMAL. These reads happen only during alerts. State the cost in the answer.
5. An ack counts only if `ack.time` is after the moment the current alert started: pure `AckRule.isFresh(ackTimeMs, alertStartMs)`, with a 5 s tolerance for clock skew.
6. When a fresh ack arrives:
   - speak "**<name> has seen your alert and is on the way.**" (and repeat it once after 30 s if still in an alert)
   - one long vibration
   - update the ongoing notification
   - broadcast it to the Safety screen, which shows it in large text
   - log the event `GUARDIAN_ACK`
   - A second guardian's ack: "<name2> is also coming."
7. During ATTENTION, an ack doesn't cancel the alert. The user still answers "I'm okay" or the timer escalates. Never hide the alert because of an ack.

### Timeline
8. `EventFormatter`: `GUARDIAN_ACK` → 🔵 "Guardian responding".

## Unit tests
- `AckRuleTest`: ack before the alert → stale; ack after → fresh; within the 5 s tolerance → fresh; null → stale.
- `EventFormatterTest`: `GUARDIAN_ACK`.

## Out of scope
Chat or messages between the phones, and live tracking of the guardian's location.

## Acceptance criteria (the reviewer will check these)
- [ ] Guardian ack from the dashboard **and** from the notification action; writes only the `ack` field
- [ ] The insistent alarm stops after acking
- [ ] The user phone polls its own doc only during alerts, every 5 s, and stops after a fresh ack or on NORMAL
- [ ] Freshness rule tested; an old ack from a previous alert is never announced
- [ ] The ack never cancels or hides the user's alert
- [ ] All tests pass

## Phone tests to include in your answer
1. Possible emergency on A → B taps "I'm on my way" in the notification → within ~5 s, A says "<B> has seen your alert and is on the way".
2. Same from the dashboard button.
3. A second guardian acks → "also coming".
4. The next day's alert doesn't replay yesterday's ack.
5. B offline when tapping → a clear failure message.

COMMIT MESSAGE: "Segment 25: guardian 'I'm on my way' acknowledgement spoken on the user's phone"
