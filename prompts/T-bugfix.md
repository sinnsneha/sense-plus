# T — Bug report template (use when something fails on the phone)

FILES:
Recommended model: whichever built the segment (same chat, if it's still open)

Run `prompts/bundle.sh T-bugfix <file> <file> …`, naming the files the failing code is in. It copies this template plus those files. Paste it into the chat box and fill in the brackets before sending.

<!-- PASTE BELOW -->
BUG REPORT — Segment [N]

**What I did (exact steps):**
1. [...]

**What I expected:** [...]
**What actually happened:** [...]
**How often:** [every time / sometimes / once]
**Phone:** [Galaxy A55 user / guardian phone model], Android [version]

**Logcat** (Android Studio → Logcat, filter `package:com.senseplus.sense`, or a tag like `SenseFusion` / `SenseCloud`; include the stack trace if it crashed):
```
[paste]
```

**Files involved:** [pasted below]

---

How I want you to handle this:
1. **Don't jump to a fix.** List 2–4 ranked hypotheses. For each one, give the evidence for and against it from the log and the code.
2. If the evidence can't separate them, give me **one quick test or log line** that tells them apart, and stop there. I'll run it and come back.
3. Once the cause is clear, give a minimal fix, following the system prompt's code-output rules. Don't refactor anything else.
4. Write the DEVLOG bug entry in the house style, numbered after the last bug (currently bug [last number]): **N. Title.** what went wrong and how it was diagnosed. *Fix:* what changed, and why it works.
5. End with the Segment Report, with STATUS describing the fix.
