# Segment 22 — Login, accounts and a role for each phone (User / Guardian)

FILES: app/src/main/java/com/senseplus/sense/HomeActivity.java app/src/main/res/layout/activity_home.xml app/src/main/AndroidManifest.xml app/src/main/java/com/senseplus/sense/CloudHelper.java app/src/main/java/com/senseplus/sense/GuardianActivity.java app/src/main/java/com/senseplus/sense/GuardianService.java app/src/main/java/com/senseplus/sense/ModeMapper.java app/src/test/java/com/senseplus/sense/ModeMapperTest.java app/src/main/java/com/senseplus/sense/ProfileActivity.java app/src/main/res/values/colors.xml
Recommended model: Sonnet 5
Priority: **must**. Do it before 23.
**Firebase console step (the user does this first):** Authentication → Sign-in method → enable **Email/Password**.

<!-- PASTE BELOW -->
TASK — Segment 22: Add real sign-in, and make each phone either a **User** phone or a **Guardian** phone

Why: today there's no login. Every phone signs in anonymously and shows *both* the user tools and the guardian dashboard, so you can't actually set up a phone as "the guardian's phone". Nothing may be removed: every existing feature stays, under the right role (system prompt, decision 9).

## Requirements

### 1. Sign in / create account / forgot password
- New `LoginActivity` (+ layout), which becomes the **launcher** activity.
  - In `onCreate`, **before** `setContentView`: if a signed-in, **non-anonymous** user exists and the role is cached, go straight to `HomeActivity` and `finish()`, so there's no flash of the login screen.
  - Anonymous users from older installs count as signed out.
- **Sign in:** email + password (Material `TextInputLayout` with the password toggle), plus a "Forgot password?" link that calls `sendPasswordResetEmail`.
- **Create account:**
  - fields: name, email, password, confirm password, phone number (optional; segments 24 and 26 use it to call/SMS)
  - role, chosen from two big cards:
    - **"I need assistance"** (User): "This phone detects falls, reads text, alerts your guardian…"
    - **"I look after someone"** (Guardian): "This phone receives alerts and shows their location…"
- **Validation** goes in a pure class `AccountRules` (no Android imports): email shape, password ≥ 6 (Firebase's minimum), passwords match, name 1–40 characters, phone optional (digits, +, spaces).
- **Friendly errors:** map Firebase exceptions to plain messages. Note that new Firebase projects have *email enumeration protection* on by default, so a wrong email and a wrong password both come back as one generic invalid-credentials error. Show "Email or password is wrong" for both. Also cover: account already exists, weak password, no internet (`FirebaseNetworkException`).
- **Accessibility:**
  - autofill hints (`emailAddress`, `password`, `newPassword`, `personName`, `phone`)
  - fields and buttons ≥ 56 dp
  - every field labelled for TalkBack
  - sign-in persists, so the user (or their guardian, helping) only does this once

### 2. Account document
- `accounts/{uid}` = `{ name, role: "user" | "guardian", email, phone, createdAt: serverTimestamp }`. Write it at sign-up; read it at sign-in to get the role.
- New class `AccountStore`:
  - caches `account_uid`, `account_role`, `account_name` in SharedPreferences `senseplus_prefs`, so the app can start offline
  - refreshes from Firestore in the background
  - if the role can't be loaded at all, shows a retry screen; it never guesses
- Role constants live in a small `Roles` class.

### 3. Remove anonymous sign-in everywhere (important)
`CloudHelper.connect()`, `GuardianActivity.onCreate` and `GuardianService.onCreate` all call `signInAnonymously()`. **Firebase signs out the current user whenever `signInAnonymously()` is called**, so leaving any of these would silently log the guardian out every time the dashboard opens.
- Remove all three.
- Anything that needs a user (the services, the dashboard) checks `FirebaseAuth.getInstance().getCurrentUser()`. If it's null, a service stops itself with a notification "Sign in to SENSE+ to resume protection", and an activity routes to `LoginActivity`.
- **Data paths are unchanged in this segment:** still `sense_users/demoUser`, so the two-phone flow keeps working end to end. Segment 23 changes them.

### 4. Role-based Home (the "distinction")
- `ModeMapper.visibleCards(String role, Set<String> modes)`:
  - **user** → today's behaviour **minus** the `guardian` card: `user` and `sensor`, plus the mode cards, all cards if no modes chosen.
  - **guardian** → `guardian` and `sensor` only.
  - Keep the old one-argument method delegating to `role = user`, so existing tests stay valid.
- **Visual distinction:**
  - header title "SENSE+" for users, "SENSE+ Guardian" for guardians
  - a different header gradient for guardians (new drawable, e.g. teal or purple)
  - subtitle "Signed in as <name>"
- The assistance-mode profile (segment 18a) is **for users only**. The first-launch profile only opens for role `user`, and "Edit profile" is hidden for guardians.
- The guardian dashboard keeps its **biometric lock**.

### 5. Guardian alerts start automatically
For role `guardian`, start `GuardianService` when Home opens after sign-in, not only when the dashboard is opened. Request `POST_NOTIFICATIONS` on Android 13+ first. Never start it for role `user`.

### 6. Account / sign out
- An **Account** button in the Home header opens a dialog with name, email, role and **Sign out**.
- Sign out:
  1. Ask for confirmation. For users the warning is "Fall detection and alerts will stop."
  2. Stop `UserMonitorService` and `GuardianService`.
  3. `FirebaseAuth.signOut()`.
  4. Clear the cached role.
  5. Open `LoginActivity` with `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK`.
- To use the other role, the person signs out and signs in with the other account. That keeps the single-phone demo possible without mixing roles.

### 7. Firestore rules (transitional)
Tell the user to **add** this rule next to their existing ones (segment 23 replaces the whole file):
```
match /accounts/{uid} { allow read, write: if request.auth != null && request.auth.uid == uid; }
```
Also tell them: if their current rules look like `allow read, write: if request.time < timestamp.date(...)`, they are **test-mode rules that expire**, and the whole app silently stops syncing on that date. Check the date.

## Unit tests
- `AccountRulesTest`: valid email/password; bad email; 5-character password; mismatched confirm; empty name; 41-character name; optional phone empty OK; phone with letters rejected.
- `ModeMapperTest` (existing 13 stay):
  - role user never contains `guardian`
  - role guardian = exactly {guardian, sensor}
  - role user with no modes = all cards except `guardian`
  - one-argument method = role user

## Out of scope
Pairing and per-account data (segment 23), Google sign-in, email verification, profile photos.

## Acceptance criteria (the reviewer will check these)
- [ ] `grep signInAnonymously` finds nothing
- [ ] Launcher gate: signed out → login; signed in → role Home with no flash; anonymous counts as signed out
- [ ] `accounts/{uid}` written at sign-up with role; role cached and refreshed
- [ ] User Home has no guardian card; Guardian Home has no user tools; no feature deleted (each is reachable under its role)
- [ ] `GuardianService` auto-starts for guardians only; services stop on sign-out; back stack cleared
- [ ] Friendly error messages; `AccountRulesTest` and `ModeMapperTest` pass
- [ ] Data path still `sense_users/demoUser`, so fall → alert still works between two phones

## Phone tests to include in your answer
1. Phone A: create a **User** account; phone B: create a **Guardian** account.
2. Fall or HELP on A → alert on B, even if B's dashboard was never opened.
3. Kill and reopen the app → still signed in, straight to the right Home.
4. Wrong password → "Email or password is wrong".
5. Forgot password → the email arrives.
6. B's Home shows no user tools; A's Home shows no guardian card.
7. Sign out on A → monitoring stops, login screen shown, Back doesn't return to Home.
8. TalkBack reads every login field.

COMMIT MESSAGE: "Segment 22: email/password accounts, user/guardian roles, role-based home, sign out"
