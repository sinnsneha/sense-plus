# SENSE+ prompt pack

These prompts are for building the rest of PLAN.md with other models (Sonnet 5, GPT-OSS 120B…). Claude Code (this repo) reviews each segment afterwards.

**All segments are built: D0 and 13–28** (18a only; 18b was replaced by 23), plus the 15/16 fixes. Their prompts have been deleted; the phone tests they listed are kept below. Next: the phone tests, then D1.

## Remaining files

| File | What it is | Priority | Model |
|---|---|---|---|
| `00-system.md` | System prompt: project context, design decisions, rules, response format, worked examples | — | read first by every model |
| `D1-report-and-viva.md` | Final README/DEVLOG, Word-report text, viva Q&A | last | Sonnet 5 |
| `T-bugfix.md` | Template when something fails on the phone | — | same as the segment |
| `T-extras.md` | Optional extras E1–E3 | — | see file |
| `bundle.sh` | Copies a prompt + its files to the clipboard (only for models that can't see the repo) | — | — |

## How to send a task

**Model that can see the repo** (your current coding chat): start a new chat and send:

```
Read prompts/00-system.md and follow its rules and response format, but since you can
edit the repo, change the files directly instead of printing them. Then run
./gradlew :app:testDebugUnitTest. Your task is in prompts/21-navigation-fix.md.
```

Change only the filename for each task.

**Model that can't see the repo** (e.g. GPT-OSS in a plain chat window): `prompts/bundle.sh 00` → paste, then `prompts/bundle.sh 21` → paste, then copy the returned files into the project.

Then:
1. Run the phone tests the model lists.
2. If something fails, use `T-bugfix.md` in the same chat.
3. Commit.
4. Ask Claude Code: **"review segment N"**.

## Order

**One task at a time, and commit before starting the next.** If two chats work on the same file at once, the last one to save silently overwrites the other.

**0. Commit everything now.** Segments 13, 15, 16, 18a and 19 are still uncommitted, and the new segments edit the same files.

**21 → 22 → 23 → 24 → 25 → 26 → 27 → 28 → D1**

- **21** depends on nothing new. Do it first: it repairs a feature that's already in the app.
- **22 → 23 → 24 → 25** must go in this order (each builds on the one before).
- **26** needs 23. **27** needs 21, 23 and 24. **28** needs 22. Stop after any segment if time runs out: 21–23 are the must-haves.
- **Run the phone tests below before 23.** 23 rewires where all data lives, so you want to know what already worked before it.
- **D1 last**, after the phone tests: it may only describe features as working if their phone tests passed. Tell it which ones did.
- `T-extras.md` (E1–E3) is optional polish, any time after 24.

**Firebase console steps (you do these; the code can't):**
1. Before 22: Authentication → Sign-in method → enable **Email/Password**.
2. Now: Firestore → Rules. If they say `allow read, write: if request.time < timestamp.date(...)`, they're **test-mode rules with an expiry date**, and the whole app silently stops syncing on that date.
3. After 23: paste `firestore.rules` from the repo into Firestore → Rules → **Publish**.

## Phone tests still to run (from the deleted prompts)

**First:** in the Firebase console, open Firestore → Rules and check that `sense_users/{id}/events/{eventId}` is allowed (rules don't carry over to subcollections). If it isn't, every event write fails and the guardian timeline stays empty.

**Segment 13 — fall detection + voice**
- [ ] Drop flat onto a bed from ~20 cm → no alert; the screen shows "Fall check: fusion reject …" with the numbers
- [ ] Drop from ~1 m with a tumble → "Possible fall", with the fusion summary
- [ ] Phone in a trouser pocket, controlled sit-fall onto a mattress → alert
- [ ] Put the phone down on a table normally → nothing
- [ ] During "Are you okay?" say "I'm not okay" → possible emergency
- [ ] During "Are you okay?" say "help" → possible emergency
- [ ] During "Are you okay?" say "I'm okay" → cancelled

**Segment 14 — navigation**
- [ ] Save Home outdoors, walk ~100 m → the arrow points back, and the distance roughly matches Google Maps
- [ ] Turn on the spot → the arrow counter-rotates and the spoken left/right is correct
- [ ] Walk back → "You have arrived home"
- [ ] Indoors with weak GPS → saving is refused, with the accuracy shown

**Segment 15 — guardian event history**
- [ ] Fall → Attention → I'M OK → the guardian timeline shows 🟠 Attention then 🟢 All clear, newest first
- [ ] HELP button → 🔴 Manual help; voice "help" → 🔴 Voice help
- [ ] Press HELP twice → only one event
- [ ] Close and reopen the guardian app → the history is still there
- [ ] Logcat (`SenseCloud`) shows no "Failed to log event" warnings
- [ ] Guardian dashboard open for 5 min with no events → Firebase console → Usage shows ~150 reads for events, not ~3,000

**Segment 16 — codeword**
- [ ] Monitoring OFF → set a codeword → no crash
- [ ] Set "banana", say it during normal monitoring → possible emergency, and the timeline shows 🔴 Codeword
- [ ] Say it during "Are you okay?" → possible emergency
- [ ] Talk normally near the phone for 2 min → count the false alarms (put the number in DEVLOG)
- [ ] Change the codeword → the old one no longer triggers anything

**Segment 17 — custom phrases**
- [ ] Add "Please call my sister" → tap it → spoken
- [ ] Restart the app → it's still there
- [ ] Long-press it → delete → still gone after a restart
- [ ] Try adding "help" (a default) → refused
- [ ] Long-press a default phrase → nothing happens

**Segment 18a — profile + assistance modes**
- [ ] Clear the app data and launch → the profile opens. Press Back → Home shows all 7 cards
- [ ] Tick Hearing → Save → Home shows only Safety, Guardian, Sensor Info and Hearing
- [ ] Edit profile → Hearing is still ticked. Add Visual → Save → Visual Assist and Navigate Home appear
- [ ] Untick everything → Save → all cards are back
- [ ] Kill the app and relaunch → the profile stays closed, and the filtering persists
- [ ] TalkBack reads each row's label and explanation; at the largest font size nothing is clipped

**Segment 19 — guardian connectivity**
- [ ] User monitoring on → the guardian shows "Online"
- [ ] User phone in airplane mode → "Last seen n min ago" after 1 min, then "Offline" after 5 min
- [ ] Stop monitoring → "Monitoring is off"
- [ ] Guardian phone in airplane mode → "This phone is offline: can't check"

**Segment 20 — What's around me**
- [ ] Point at a chair or door → spoken within ~1 s
- [ ] Hold still → not repeated for 10 s
- [ ] Switch back to Read text → OCR works as before
- [ ] Leave it running for 2 min → the camera never freezes

**Segment 21 — Navigate Home fixes**
- [ ] Clear app data, open Navigate Home first → one dialog asking precise/approximate location
- [ ] Choose Approximate → message + Open settings; location off → the system "turn on location" dialog
- [ ] Indoors → Save → "Measuring…" → saved, or "GPS is rough, save anyway?"
- [ ] Type your address → resolved → saved
- [ ] Hold the phone upright → the arrow stays steady; Walking directions → Google Maps in walking mode

**Segment 22 — login and roles**
- [ ] Phone A: create a User account; phone B: create a Guardian account
- [ ] Kill and reopen → still signed in, straight to the right Home; wrong password → "Email or password is wrong"
- [ ] Forgot password → the email arrives
- [ ] B's Home has no user tools; A's Home has no guardian card; sign out → login, Back doesn't return

**Segment 23 — pairing (publish `firestore.rules` once both phones run this build)**
- [ ] A: My guardians → Add a guardian → code shown and spoken
- [ ] B: Link to a person → enter the code → "Waiting…" → A taps Approve → B's dashboard shows A
- [ ] A declines instead → B says it was declined
- [ ] A removes B → within ~15 s B says "No longer linked" and alerts stop (needs the new rules)

**Segment 24 — guardian alerts**
- [ ] Drop A's phone → B gets the orange "possible fall" notice within the 15 s window
- [ ] No answer → B's red alarm keeps ringing until opened; tap → dashboard; Call / Open map work
- [ ] A's battery below 15 % → both phones warn once; A in airplane mode 6 min → B gets "Lost contact"

**Segment 25 — I'm on my way**
- [ ] Possible emergency on A → B taps "I'm on my way" (notification or dashboard) → within ~5 s A says help is coming
- [ ] The alarm on B stops after tapping; A's alert is NOT cancelled by it

**Segment 26 — Medical ID, call, SMS**
- [ ] Fill in Medical ID on A → B's dashboard shows it
- [ ] Lock A → HELP → "Medical ID" on the notification opens it without unlocking; Read aloud works
- [ ] Call my guardian → it rings B
- [ ] SMS switch on, A offline (no data, no Wi-Fi) → HELP → B gets one SMS with a map link; a second HELP sends no second SMS

**Segment 27 — safe zone**
- [ ] Home set, radius 100 m, monitoring on → walk 200 m away → B gets "left the safe zone" with the distance; walk back → "back home"
- [ ] B sets Home by address → A's Navigate Home shows it within 10 min
- [ ] Standing near the edge does not repeat the alert

**Segment 28 — restart and microphone**
- [ ] Monitoring on → restart A → reminder notification → tap → monitoring back on (same for B's guardian alerts)
- [ ] Monitoring on → open Hearing Assist → captions work and a drop is still detected → close it → "help" works again

## What a review checks

When you say "review segment N", Claude Code will:
- diff against the last commit
- tick off that prompt's **Acceptance criteria**
- run the debug build and `./gradlew :app:testDebugUnitTest`
- check that the design decisions in `00-system.md` §3 weren't broken
- give a verdict (**on plan / on plan with fixes / off plan**), plus a fix prompt if needed

A review can't run the app on your phone. Tell me which phone tests passed.

## Review log

| Date | Segment | Verdict | Notes |
|---|---|---|---|
| 2026-11-12 | 14 | **on plan**, committed `7a9de15` | NavMathTest 5/5. Minor: with no Home saved, the status stays "Finding your location…" after a fix arrives. |
| 2026-11-12 | D0 | **done, not committed** | README + DEVLOG have segments 10–12 and bugs 10–13. DEVLOG has no rows for 14, 15, 16 yet (D1 can add them). |
| 2026-11-12 | 13 | **on plan, not committed** | First attempt didn't compile (96 errors from hand-merged snippets); the redo is correct. Pre-fall snapshot, rejection message, `hasFallen` fix. |
| 2026-11-12 | 15 | **on plan after fix, not committed** | Polling only, events logged once. Fixed: event poll now costs 1 read per poll instead of 20; CODEWORD shows 🔴. Firestore rules not verifiable from the repo. |
| 2026-11-12 | 16 | **on plan after fix, not committed** | JSONArray grammar with not/no/[unk], codeword never leaked. Fixed: codeword reload goes through `sendAction`, so there's no crash when monitoring is off. No vocabulary check (allowed). |
| 2026-11-12 | fixes 15/16 | **verified** | All 3 acceptance items met. assembleDebug OK, 45/45 tests. |
| 2026-11-12 | 17 | **on plan**, committed `cbfeeb1` | Defaults unchanged (same 8 phrases), JSON stays in the Activity, `PhraseRules` 8 tests, a11y labels on buttons. |
| 2026-11-12 | 20 | **on plan**, committed `fc7105c` | `imageProxy.close()` exactly once on every path, 500 ms throttle, `LabelAnnouncer` 7 tests, no stair claim. That commit's DEVLOG also carries the uncommitted segment 13's row. |
| 2026-11-12 | 18a | **on plan, not committed** | Mapping exactly as specified, all cards when nothing is chosen, applied in `onResume`, manifest OK, 13 tests. Material3 button style is safe (theme is Material3). The profile reappears on each cold start until saved (acceptable). Added `styles.xml` (declared deviation). |
| 2026-11-12 | 19 | **on plan, not committed** | Server `lastSeen` on every write, 30 s heartbeat, `monitoring=false` in `onDestroy`, guardian distinguishes its own failure, `ConnectivityStatus` 7 tests incl. future timestamp. Mixed into the same files as 13/15/16. |
| 2026-11-12 | whole tree | **builds** | assembleDebug + 80/80 tests. The last commit (`fc7105c`) also builds and passes on its own. DEVLOG has rows 10–13 and 20 only (14–19 missing; D1 can add them). |
| 2026-11-12 | 13–19, 21, 22, 24 + redesign | **committed** `ed78ec0` | Build + 148 tests green on the clean commit. 24 was built before 23, on demoUser. |
| 2026-11-12 | 21 / 22 / 24 | **on plan** | All acceptance items found in the code; phone tests pending. |
| 2026-11-12 | 23 | **done by Claude Code**, committed `d9f3959` | demoUser gone; FirestorePaths; code + approval pairing; firestore.rules; 5 s / 2 s guardian polling; 24's alerts kept. Clean-commit build + 161 tests green. Phone tests pending; publish firestore.rules only after both phones run this build. |
| 2026-11-12 | 25–28 | **not done** | No code in the repo. The Antigravity run for 28 wrote nothing (0-byte patch, no out/ folder) despite reporting success. |
| 2026-11-12 | 25 | **on plan** | Guardian writes only `ack`; alarm cancelled on ack; user polls own doc every 5 s only during alerts (15 s after a first ack, to announce a second guardian); ack never cancels the alert; AckRule tested. |
| 2026-11-12 | 26 | **on plan** | Medical ID synced (users/{uid}.medical); lock-screen helper view + read aloud; Medical ID action on the emergency notification; call with CALL_PHONE else dialer; opt-in multipart SMS once per emergency, event logs only a count. |
| 2026-11-12 | 27 | **on plan** | Guardian writes only `home` (incl. dotted sub-fields); SafeZoneRule 100 m accuracy gate, +50 m margin, 2 fixes; no new location requests; 10-min home refresh; one alert per transition. |
| 2026-11-12 | 28 | **on plan** | HearingActivity talks to the service only if running, via startService; pause stops only the keywords; boot/update reminder (Android 15 FGS restriction explained); README limitation rewritten. |
| 2026-11-12 | whole tree | **builds** | assembleDebug + 313/313 tests. All Firestore access via FirestorePaths (same 5 paths), so firestore.rules covers everything. Not committed yet. |
