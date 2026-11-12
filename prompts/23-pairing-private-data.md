# Segment 23 — Link a guardian to a user (pairing code + approval), private per-person data, real security rules

FILES: app/src/main/java/com/senseplus/sense/CloudHelper.java app/src/main/java/com/senseplus/sense/GuardianActivity.java app/src/main/java/com/senseplus/sense/GuardianService.java app/src/main/java/com/senseplus/sense/UserMonitorService.java app/src/main/java/com/senseplus/sense/HomeActivity.java app/src/main/res/layout/activity_home.xml app/src/main/AndroidManifest.xml app/src/main/java/com/senseplus/sense/AccountStore.java app/src/main/java/com/senseplus/sense/ModeMapper.java app/src/test/java/com/senseplus/sense/ModeMapperTest.java
Recommended model: Sonnet 5 (security-sensitive)
Priority: **must**. Needs 22.
**Firebase console step (the user does this after the code is in):** paste `firestore.rules` into Firestore → Rules → **Publish**.

<!-- PASTE BELOW -->
TASK — Segment 23: Replace the shared `demoUser` document with per-person data, let a guardian link to a user with a code that the user approves, and lock the database down with real rules

> **Segment 24 is already in the code**, built on the old `demoUser` path: `GuardianAlertDecider`, `BatteryAlertRule`, the `guardian_alarm_v3` insistent alarm, call/map actions, battery in the heartbeat, and low-battery and lost-contact alerts. **Keep all of it working.** In `GuardianService` and `GuardianActivity`, change only *where* they read from (the linked user's `users/{uid}`) and the background polling interval. 24's phone tests must still pass after this segment.
> New screens (`GuardiansActivity`, `LinkActivity`) follow the design system in the system prompt (rule 8: tokens only, Pip on the screen).

Why: today every install of the app reads and writes the **same** document (`sense_users/demoUser`). So any phone with the app sees everyone's location, and two users would overwrite each other. A guardian also has no way to choose whose alerts they receive.

## Data model (use exactly these names)
| Path | Written by | Fields |
|---|---|---|
| `accounts/{uid}` | that account | from 22, plus for guardians: `linkedUser` (uid), `linkedUserName` |
| `users/{uid}` | the user (guardians: only `ack`, `home`) | every field that used to go to `sense_users/demoUser`, plus `name`, `phone`, `guardians` (array of guardian uids), `guardianInfo` (map uid → `{name, phone}`) |
| `users/{uid}/events/{id}` | the user | unchanged from segment 15 |
| `users/{uid}/linkRequests/{guardianUid}` | the guardian | `{name, phone, code, createdAt}` |
| `pairCodes/{code}` | the user | `{userUid, userName, expiresAt}` (Timestamp, now + 10 min) |

**One place for paths:** a new `FirestorePaths` class (`userDoc(uid)`, `events(uid)`, `linkRequests(uid)`, `pairCode(code)`, `account(uid)`). After this segment, `grep -r "demoUser\|sense_users" app/src` must find **nothing**. Old `sense_users/demoUser` data is simply ignored (not deleted).

## Requirements

### User phone
1. `CloudHelper` takes the user's uid. `UserMonitorService` gets it from `FirebaseAuth`; if there is none, it stops with the "Sign in to resume protection" notification. At service start, write `name` and `phone` (from `AccountStore`) into `users/{uid}`.
2. **"My guardians"** screen (`GuardiansActivity`, a new Home card for role `user`):
   - **Add a guardian:**
     - Generate a 6-digit code with `SecureRandom` (pure `PairCode.generate(Random)`, keeping leading zeros) and `create` `pairCodes/{code}`. If the code is taken, the write is denied: generate another, up to 5 tries.
     - Show the code in huge digits with a 10-minute countdown, plus a **"Speak code"** button that reads the digits one by one, for blind users.
   - While the screen is open, poll `users/{me}/linkRequests` every 2 s (`Source.SERVER`). For each request show **"Approve <name> as your guardian?"**:
     - **Approve:**
       1. Update your own doc: `guardians` arrayUnion(gid), `guardianInfo.<gid>` = {name, phone}.
       2. Delete the request.
       3. Delete the code.
       4. Speak "<name> is now your guardian".
     - **Decline:** delete the request.
   - **List current guardians**, each with **Remove** (confirm first; arrayRemove + remove the map entry).

### Guardian phone
3. **"Link to a person"** screen (`LinkActivity`; a Home card for role `guardian`, and opened automatically on first sign-in if not linked):
   - Enter the 6-digit code → `get` `pairCodes/{code}`. *Not found*, *expired* and `PERMISSION_DENIED` all mean "That code is wrong or has expired".
   - Write `users/{userUid}/linkRequests/{myUid}` with `{name, phone, code, createdAt}`.
   - Show "Waiting for <userName> to approve on their phone…". Poll `get users/{userUid}` (`Source.SERVER`) every 2 s. `PERMISSION_DENIED` means not approved yet. Success means linked:
     1. Save `linkedUser` / `linkedUserName` to `accounts/{me}` and to prefs.
     2. Start `GuardianService`.
     3. Open the dashboard.
   - Give up after 10 min, with a message.
   - **Unlink** (on this screen): clears the local link and stops the service.
4. `GuardianActivity` and `GuardianService` read `users/{linkedUser}` and its `events`.
   - Not linked → the dashboard shows "Not linked to anyone yet" with a button to `LinkActivity`, and the service stops itself.
   - `PERMISSION_DENIED` **3 polls in a row** (the user removed this guardian) → "You are no longer linked to <name>". Clear the local link and stop the service.
5. **Background polling (decision 1, quota):** `GuardianService` polls every **5 s while the status is NORMAL**, every **2 s otherwise**. The open dashboard stays at 2 s.
6. Home header subtitle for guardians: "Looking after <linkedUserName>" or "Not linked yet".

### Security rules
7. Create **`firestore.rules`** at the repo root with exactly this content. If a field name has to differ, change both sides and say so under DEVIATIONS.
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() { return request.auth != null; }
    function isUid(uid) { return signedIn() && request.auth.uid == uid; }
    function userDoc(uid) { return get(/databases/$(database)/documents/users/$(uid)); }
    function codeDoc(code) { return get(/databases/$(database)/documents/pairCodes/$(code)); }

    match /accounts/{uid} {
      allow read, write: if isUid(uid);
    }

    match /users/{uid} {
      allow read: if isUid(uid)
                  || (signedIn() && request.auth.uid in resource.data.get('guardians', []));
      allow create, delete: if isUid(uid);
      // The owner may change anything; a linked guardian may only touch 'ack' and 'home'.
      allow update: if isUid(uid)
                    || (signedIn()
                        && request.auth.uid in resource.data.get('guardians', [])
                        && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['ack', 'home']));

      match /events/{eventId} {
        allow read: if isUid(uid)
                    || (signedIn() && request.auth.uid in userDoc(uid).data.get('guardians', []));
        allow create: if isUid(uid);
      }

      // A guardian may ask to link only with a valid, unexpired code issued by this user.
      match /linkRequests/{guardianUid} {
        allow create: if isUid(guardianUid)
                      && exists(/databases/$(database)/documents/pairCodes/$(request.resource.data.code))
                      && codeDoc(request.resource.data.code).data.userUid == uid
                      && codeDoc(request.resource.data.code).data.expiresAt > request.time;
        allow read, delete: if isUid(uid) || isUid(guardianUid);
      }
    }

    // Short-lived codes: anyone signed in may look up ONE code (never list them).
    match /pairCodes/{code} {
      allow get: if signedIn() && resource.data.expiresAt > request.time;
      allow create: if signedIn() && request.resource.data.userUid == request.auth.uid;
      allow delete: if signedIn() && resource.data.userUid == request.auth.uid;
    }
  }
}
```
8. In the answer, tell the user to publish it and explain what it guarantees: a guardian can see a person only after that person approves them; only the owner can write their data; guardians can only acknowledge alerts (`ack`) and set Home (`home`), which segments 25 and 27 use.

### Home cards
9. `ModeMapper` gains `guardians` (always shown for role user) and `link` (always shown for role guardian). Update the tests.

## Unit tests
- `PairCodeTest`: always 6 characters, digits only; leading zeros kept (a seeded `Random` that yields 42 → "000042"); `isValidFormat` rejects "12345", "1234567", "12a456", null.
- `PairingRulesTest`: `isExpired(now, expiresAt)` boundaries; `deniedStreakUnlinks(3)` → true only after 3 in a row.
- `ModeMapperTest`: the new cards per role.

## Out of scope
A guardian following several people (one linked person per guardian; a user **may** have several guardians), FCM push, email verification.

## Acceptance criteria (the reviewer will check these)
- [ ] `grep -r "demoUser\|sense_users" app/src` finds nothing; all paths go through `FirestorePaths`
- [ ] Linking needs the code **and** the user's approval; codes expire after 10 min and are deleted after approval
- [ ] `firestore.rules` present and matching the data model; guardian-side `PERMISSION_DENIED` handled (waiting vs removed)
- [ ] `GuardianService` polls at 5 s when NORMAL and 2 s otherwise; dashboard 2 s
- [ ] Removing a guardian on the user phone unlinks the guardian phone within ~3 polls
- [ ] "Speak code" reads the digits one by one
- [ ] All tests pass; fall → alert still works end to end after linking

## Phone tests to include in your answer
1. Publish the rules.
2. A (user): My guardians → Add → code shown.
3. B (guardian): Link → enter code → "Waiting…".
4. A: "Approve <B>?" → Approve → B's dashboard shows A's live status.
5. Fall or HELP on A → alert on B.
6. Third account C: enter an expired or wrong code → error; C never sees A.
7. A removes B → B shows "no longer linked" and alerts stop.
8. Firebase console → Usage: reads stay low while A is NORMAL.

COMMIT MESSAGE: "Segment 23: guardian pairing (code + approval), per-person Firestore data, security rules, adaptive guardian polling"
