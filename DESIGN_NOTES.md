# SENSE+ design notes

The UI follows the **SENSE+ Pastel Companion** design system in `sense_plus_design_profile.json` (v1.1.0). This file records where the system lives in the code and every place the build differs from the profile, with the reason. `prompts/00-system.md` rule 8 sends other models here.

## Sources

- **Spec:** `sense_plus_design_profile.json`.
- **Pip the duckling:** `duck_kit.zip`, 33 VectorDrawable layers copied unchanged into `res/drawable/duck_*.xml`.
- **Style reference:** `ducks.jpeg`, the sticker sheet the profile calls `design/reference/duck_style_reference.jpg`.
- **Font:** Lexend (SIL OFL 1.1, licence in `design/licenses/Lexend-OFL.txt`).
- **Icons:** Material Icons Rounded, from Android Studio's bundled set, renamed `ic_*`.

## Where things live

| What | Where |
|---|---|
| Colours (palettes, neutrals, surfaces, status, sound types, gradient stops) | `res/values/colors.xml` |
| Spacing, radius, elevation, touch targets, Pip sizes | `res/values/dimens.xml` |
| Type scale (`TextAppearance.SENSE.*`) | `res/values/styles_text.xml` |
| Components (`Widget.SENSE.*`) | `res/values/styles_widgets.xml` |
| Theme (Material 3 Light, light only) | `res/values/themes.xml`, `values-v27/themes.xml` |
| Fonts | `res/font/lexend*.ttf`, `res/font/lexend.xml` |
| Edge-to-edge, insets, press feedback, reduced motion | `ui/Ui.java` |
| Grain, hero/tile gradients, organic card, scallop badge | `ui/GrainTexture`, `ui/HeroBackgroundDrawable`, `ui/Gradients`, `ui/OrganicShapeDrawable`, `ui/ScallopBadgeDrawable` |
| Pip: looks, renderer, animated view, still drawable | `ui/DuckLooks`, `ui/DuckRenderer`, `ui/DuckView`, `ui/DuckDrawable` |
| State → visuals | `ui/StatusStyle` (SENSE GUARD, Guardian), `ui/SoundStyle` (Hearing) |
| Other views | `ui/WaveLevelIndicatorView`, `ui/PulseRingView`, `ui/FitTextView`, `ui/TileGridLayout` |
| Pip gallery (debug builds) | Long-press the Home greeting → `CharacterGalleryActivity` |

## Decisions and deviations from the profile

1. **ducks.jpeg is used for two stickers only.** The profile says Pip must never be raster. On request, the sheet's ducks are used where they fit a section better than the kit. They appear as two cut-outs in `drawable-nodpi`: `pip_sticker_thanks` (heart hug) on "Thank you" and `pip_sticker_drink` (boba tea) on "I need water", both on the Communication board at 64dp. The sheet is only 736 px wide, so the ducks look soft above about 70dp. Everything larger stays vector.
2. **Two new vector looks** are drawn in the kit's style on its 240-unit canvas. PROFILE is the sheet's orange bow tie + flower pose (`duck_profile_bowtie.xml`, `duck_profile_flower.xml`). COGNITIVE is a checklist notepad + pencil (`duck_cognitive_notebook.xml`).
3. **Light only.** `values-night/themes.xml` was removed; dark mode is future work, as the profile says.
4. **Lexend static weights** (300–800) were generated from Google Fonts' variable `Lexend[wght].ttf` with fontTools.
5. **View type changes**, with the Java updated:
   - `tvAlertIcon`: TextView → ImageView (the profile's recommended migration; `SoundStyle` supplies the icon).
   - `switchAutoSpeak`: Switch → MaterialSwitch.
   - `phraseContainer`: LinearLayout → `ui.TileGridLayout` instead of GridLayout, for equal-height rows, a full-width HELP and re-flow.
   - `cardSensor`: card → 48dp circle ImageButton (id kept).
   - Profile and Login check boxes are the framework `android.widget.CheckBox`, because MaterialCheckBox draws its own box over the custom one.
6. **Home re-flows.** The tool grid is a `TileGridLayout`, so tools hidden by the profile or the role close up, and a lone last tile spans the row. Home is role-based (segment 22): each role sees one full-width hero card; a guardian sees no tools; the greeting uses the account name. Pip appears on every card and tile, as the Home screen spec asks, which is more than the general "one hero + two small" rule.
7. **Mode toggles use a solid fill, not a gradient.** In the Visual Assist toggle the selected segment is solid `butter_300`; on the Login tabs it is solid `lavender_200`. `MaterialButtonToggleGroup` reshapes its buttons' Material background and throws if that background is replaced. Hearing's segmented control is not a toggle group, so it keeps the gradients.
8. **Text fields:** the resting outline is `ink_500` (5.4:1), not `ink_200` (1.2:1), because input boundaries need 3:1. When focused it is 2dp `lavender_600`.
9. **Wording.** The level indicator shows "Emergency" (the profile's copy), but TalkBack says "possible emergency" (project design decision 4).
10. **Guardian.** The status headline uses plain words: All okay, Possible fall, Possible emergency, or "Waiting for the user's phone…". Battery is a big number with its age underneath; segment 24's `batteryLine` text is the TalkBack description. Tiles show values only, because they already carry the labels.
11. **Sensor Info** writes values only. A missing sensor says "Not on this phone" and Pip looks worried.
12. **Hearing.** `SoundType` dropped its emoji and hex colour (tokens are in `SoundStyle`), titles are sentence case, and history lines have no emoji. Material Icons has no siren, so SIREN uses `campaign` and SHOUT uses `record_voice_over`.
13. **Gradients.** The profile's "135°" means top-left light to bottom-right deep. Blob radii are fractions of the width. Navigation (coral) and Profile (lavender + cream) heroes are derived the same way as `grad_hero_sky`, because the profile defines no gradient for them.
14. **No Pip bitmap cache.** Idle breathe and bob animate view properties, so the layers are not redrawn on idle frames; blink, talk, waves and rings redraw only while they run.
15. **Dialogs** use `MaterialAlertDialogBuilder` (white, 32dp corners, `tangerine_700` buttons at 4.8:1).
16. **Login** (segment 22) is not in the profile. It is built from the profile's components: a lavender hero with Pip waving, plus role cards showing Pip's guard and guardian looks.
17. **Buttons are text only (user review, 2026-11-12).** The orange icon-chip CTA read as off-tone and competed with HELP. The changes:
    - Primary CTAs (`Button.Stadium`) have no leading icon circle.
    - SENSE GUARD's start/stop is its own `Button.Guard`: a rounded tile in the hero's lavender. "Start monitoring" is deep lavender with white text; while monitoring runs (`setActivated(true)`) it becomes a calm light-lavender "Stop monitoring", so HELP stays the loudest.
    - HELP is a flat solid-red, letter-spaced tile.
    - I'M OK is a soft mint tile with no border.
    - The codeword setting is a lavender text link.
    - The profile's coloured glows (`glow_tangerine`, `glow_emergency`) are dropped, because they smudged the cream background.
    - The status line no longer repeats "Status:".
    - Material's elevation overlay is off; it tinted the white BACK pill peach.

## Not built yet

- Onboarding slides, the Guardian "Safety this week" donut and the optional bottom app bar (all marked planned or optional in the profile).
- Screen-enter stagger, stat number count-up, and the 3-dot "speaking" indicator on phrase cards.
- A README "Design system" section with screenshots.

## Merge note (2026-11-12)

Other sessions applied segments 21, 22 and 24 to the same files while the redesign was being written. The redesign overwrote their changes in `HomeActivity`, `activity_home.xml`, `MainActivity`, `activity_main.xml`, `GuardianActivity` and `activity_navigation.xml`, and they were restored by hand, in the new style:
- login redirects
- role-based Home, Account dialog and sign out
- no `signInAnonymously()` on the dashboard
- the emergency alarm stops when the dashboard opens
- battery age
- FINE + COARSE location request and the location-settings button
- Navigation's GPS line, settings/retry buttons, typed address and walking directions

These flows compile and the unit tests pass, but they still need a phone check.
