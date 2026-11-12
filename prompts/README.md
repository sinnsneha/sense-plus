# SENSE+ prompt pack

These prompts are for building the rest of PLAN.md with other models (Sonnet 5, GPT-OSS 120B…). Claude Code (this repo) reviews each segment afterwards.

Done so far: D0 and segments 13–20 (18a only), plus the 15/16 fixes. Their prompts have been deleted; the phone tests they listed are kept below. 18b (pairing) was dropped and replaced by the better design in segment 23.

## Remaining files

| File | What it is | Priority | Model |
|---|---|---|---|
| `00-system.md` | System prompt: project context, design decisions, rules, response format, worked examples | — | read first by every model |
| `21-navigation-fix.md` | **Fix Navigate Home** (7 causes found) + address search + Google Maps walking directions | **must** | Sonnet 5 |
| `22-accounts-roles.md` | **Login** (email/password), a **User / Guardian role per phone**, role-based Home, sign out | **must** | Sonnet 5 |
| `23-pairing-private-data.md` | **Link guardian ↔ user** (code + approval), private per-person data, **Firestore security rules** | **must** | Sonnet 5 |
| `24-guardian-alerts.md` | 🟠 early warning, loud repeating 🔴 alarm, call/map actions, low-battery + lost-contact alerts | high value | Sonnet 5 |
| `25-on-my-way.md` | Guardian taps "I'm on my way" → user's phone says help is coming | high value | Sonnet 5 |
| `26-medical-id-call-sms.md` | Medical ID (lock-screen helper view), "Call my guardian", SMS fallback without internet | high value | Sonnet 5 |
| `27-safe-zone.md` | Alert the guardian when the user leaves home (wandering) | nice | Sonnet 5 |
| `28-reliability.md` | Resume protection after a restart; Hearing Assist shares the mic | nice | Sonnet 5 |
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
