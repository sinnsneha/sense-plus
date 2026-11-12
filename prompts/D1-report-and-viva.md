# D1 — Final docs, report text and viva prep (do this last)

FILES: README.md DEVLOG.md HANDOVER.md PLAN.md
Recommended model: Sonnet 5
Also paste: every saved Segment Report (prompts/reports/*.md), and a list of which phone tests actually passed.

<!-- PASTE BELOW -->
TASK D1 — Final documentation pass, Word-report sections and viva preparation

I'm pasting README.md, DEVLOG.md, HANDOVER.md, PLAN.md, the Segment Reports for the segments that were built, and a list of the phone tests that actually passed.

**Ground rule:** only describe a feature as built if its Segment Report says complete **and** its phone tests are in my "passed" list. Anything else is "partial" or "planned". If something I've given you contradicts itself, ask me instead of guessing.

## Deliverables
1. **README.md (full):** features table, architecture diagram (new Activities and classes), tech stack (new dependencies), known limitations. Same tone as now.
2. **DEVLOG.md (full):** segment rows and bug entries for everything built, numbered continuously after what's there.
3. **PLAN.md:** update only the "Synopsis coverage at a glance" table (✅ / ⚠️ / ❌ + a short note). Leave the rest alone.
4. **Report text for the Word document** (`SENSE_Plus_Project_Log.docx`). Plain text I can paste, with headings:
   - Segments 10–N: one paragraph each (what, which sensors/APIs, how it was tested)
   - Bugs 10–N: problem → diagnosis → fix
   - **Differences from the synopsis, and why:** Java instead of Kotlin; Activities + foreground services instead of MVVM/Coroutines; 2 s polling + foreground service instead of FCM (FCM needs Cloud Functions on the paid Blaze plan, and the listener dropped on test Wi-Fi); Vosk for keywords + Google SpeechRecognizer for captions; YAMNet (TFLite) as the sound-classification model; image labelling instead of stair detection (if segment 20 was built)
   - Design decisions (HANDOVER.md §4 + any added since)
   - Limitations and future work: honest
5. **Viva prep:** 20 likely examiner questions with crisp answers (3–6 sentences each), covering at least:
   - the fall state machine and each guard
   - the three-level response
   - what the sensor fusion is exactly, and what it can't tell apart
   - why polling instead of a listener, and why `Source.SERVER`
   - foreground service types (dataSync, location, microphone)
   - which on-device ML models run and where (ML Kit OCR, ML Kit image labelling, YAMNet, Vosk)
   - why Java
   - Firestore structure and rules
   - privacy (on-device audio, the codeword never leaves the phone)
   - battery cost
   - accessibility per disability type
   - what you'd do with more time
   Add 5 "hard" questions an examiner might use to probe weaknesses, with honest answers.

## Acceptance criteria (the reviewer will check these)
- [ ] No feature is claimed beyond what the reports + passed tests support
- [ ] The DEVLOG numbering is continuous and matches the commit history
- [ ] The synopsis-differences section covers every item listed above
- [ ] Viva answers match the actual code (thresholds, intervals, model names)

COMMIT MESSAGE: "Docs: final README, DEVLOG and PLAN coverage update"
