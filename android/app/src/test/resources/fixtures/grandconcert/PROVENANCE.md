# Grand Concert fixture provenance

Launch-night captures of the Global scenario **Brighter Together Our Grand Concert** (community
name "Grand Live"), which was added to Global at 2026-07-22 22:00 UTC. Taken on the maintainer's
own emulator at 1080x1920 within the first half hour of the scenario going live, while starting
one manual reconnaissance career with `[CODE: ICING] Mihono Bourbon`.

These are MuMu screenshots (native RGB, not the bot's own OpenCV camera, which writes BGR and is
unusable for colour probes).

| Fixture | Original file | Shows |
|---|---|---|
| quickmode_dont_use.png | MuMu-20260723-002410-542.png | "Quick Mode Settings" dialog, option 1 ("Don't use Quick Mode") selected, black backdrop, wide green Confirm |
| quickmode_shorten_all.png | MuMu-20260723-002420-043.png | Same dialog with option 2 ("Shorten all events") selected -- the only pair that proves selection is readable |
| career_main_turn1.png | MuMu-20260723-002436-807.png | First career turn: pink stat-table label row, white stat value cells, locked "?" scenario button between Recreation and Races, goal band, energy bar |
| final_confirmation.png | MuMu-20260723-002352-805.png | Final Confirmation with the scenario title rendered as two lines, the Scenario Link badge on the trainee, and the 15 TP cost |

## What these fixtures do and do not prove

They pin the Quick Mode dialog's structure and selection readback, the pink-theme claim about the
career screen (the pink is confined to the stat LABEL row; the value cells stay white, which is
why the shared grayscale stat OCR is unaffected), and the locked state of the scenario button.

They prove nothing about the Lesson shop, the performance point bars, the unlocked scenario
button, or any concert screen -- none of which had appeared yet at turn 1. No probe in
`utils/GrandConcertProbes.kt` claims to recognise those, and the campaign hands the run back to
the player when it meets one.

## Second capture set: a training transition (2026-07-23 02:23)

A matched before-and-after pair spanning one training turn on the same reconnaissance career
(`[CODE: ICING] Mihono Bourbon`), captured to pin the training preview and the turn-transition
model. Native RGB, 1080x1920.

| Fixture | Original file | SHA-256 | Shows |
|---|---|---|---|
| training_guts_before.png | MuMu-20260723-022350-839.png | `e4754c9a0940c3df177305fae1b63591751a88afff4df016611c7adb1f2bcd67` | Training screen, Guts Lvl 1 selected ("Incline" special), Failure 9%, preview Speed +3 / Power +3 / Guts +9 / Skill Pts +8, Da +13 performance gain, Performance Points panel (Da 10, Pa 10, Vo 10, Vi 10, Co 0) with scheduled "22 more" on Da and "2 more" on Vi, two visible support participants, 7 turns to debut, concert in 19 |
| career_after_training.png | MuMu-20260723-022401-212.png | `f659138ab036f78ffb9737fcf1520a4027806ae0a8744d986daca2148c6e4fef` | Career screen the following turn: Da 23 / scheduled "9 more", Mild Hype gauge, stats Speed 184 / Stamina 118 / Power 155 / Guts 182 / Wit 147 / SP 147, 6 turns to debut, concert in 18, and the now-UNLOCKED Lessons button with a "Scheduled" badge |

### What the pair proves

- The performance type a training grants is **per-turn**, not fixed to the facility: Guts
  previewed Dance (+13) this turn, while the documented static primary for Guts is Visual. The
  four unselected facilities showed Speed→Visual, Stamina→Passion, Power→Vocal, Wit→Composure;
  Speed and Guts both differ from the static primary map, which is why the icon must be read.
- The performance arithmetic reconciles exactly: Da 10 + 13 = 23, scheduled Da deficit
  max(32 - 10, 0) = 22 then max(32 - 23, 0) = 9, Vi deficit max(12 - 10, 0) = 2 unchanged, and
  both countdowns dropped by one (debut 7→6, concert 19→18).
- The ordinary stats moved by the preview amounts (Speed +3, Power +3, Guts +9, SP +8, Wit +0)
  **except** Stamina, which rose +10 against a previewed +0. A "minor decline in stamina"
  message is on the after-frame, so a post-training event intervened; the +10 is recorded as an
  unexplained delta rather than treated as a parser failure. This is why the transition verifier
  checks ordinary stats softly while keeping the performance and deficit arithmetic mandatory.

### Bonus: two screens this pair also happens to contain

The after-frame is the first capture of the **unlocked** Lessons button (with its "Scheduled"
badge) and of the **Mild Hype** gauge. Neither is automated here (still no Lesson-shop capture),
but both are now available for a later detector.

## Third capture set: the full Lesson and Concert-Info flow (2026-07-23 02:15-02:20)

The complete live lesson flow on the same reconnaissance career, captured screen by screen. Native
RGB, 1080x1920. These replace the earlier "no Lesson-shop capture" gap for everything except
actuation.

| Fixture | Original file | Screen |
|---|---|---|
| technique_list.png | MuMu-20260723-021538-459.png | Lesson list, three Technique cards (Audience Involvement Basics / Stamina +5 / Pa 10 "Learnable!"; Group Lesson Basics / Skill Hint Lvl +1 / Da 15 greyed-unaffordable; Composure Training Basics / Wit +5 / Co 10 "Learnable!"), balances all 10, Full Stats + Concert Info |
| song_list.png | MuMu-20260723-021906-461.png | Lesson list, three Song cards (Run n' Run! Da14/Vi16/Co14; Believe in Miracles! Pa21/Co21; Full Speed Ahead! Umadol Power☆ Da32/Vi12), balances Da/Pa/Vo/Vi 10, Co 0 |
| song_list_scheduled.png | MuMu-20260723-021947-187.png | Same song list after scheduling, third card now carries a pink "Scheduled" badge |
| learn_confirm_technique.png | MuMu-20260723-021557-592.png | "Confirmation" (affordable): Audience Involvement Basics, Stamina +5, Points Left Over Da10/Pa0/Vo10/Vi10/Co10, "won't be able to learn the other 2 options", Cancel / Learn |
| schedule_confirm_technique.png | MuMu-20260723-021600-394.png | "Schedule" (unaffordable technique): Group Lesson Basics, Points Left Over Da -5, "Not enough performance points", "Schedule this concert technique?", Cancel / Schedule |
| schedule_confirm_song.png | MuMu-20260723-021932-552.png | "Schedule" (unaffordable song): Full Speed Ahead! Umadol Power☆, Concert Bonus Friendship +0%->+5%, "HYPE Lv UP!" preview gauge, Points Left Over Da -22/Vi -2, "Schedule this song?", Cancel / Schedule |
| scheduling_complete.png | MuMu-20260723-021938-298.png | "Scheduling Complete": Full Speed Ahead! Umadol Power☆, "Song added to schedule.", Close (over the dimmed list) |
| concert_info.png | MuMu-20260723-021542-175.png | "Concert Info": 1st Concert, Mild Hype, Total Songs Learned 1, Friendship +0%, Specialty Priority +0->+5, Support Chain Lvl 0, Set List "Make debut!", Close |
| career_scheduled.png | MuMu-20260723-022005-459.png | Career screen with a scheduled lesson: Mild Hype gauge, Performance panel Da10 "22 more" / Vi10 "2 more" / Co0, unlocked Lessons button with a pink "Scheduled" badge and a song-note marker |

### What this set proves and what it does not

It pins the Lesson list (technique vs song by header colour, affordability by cost-strip brightness
and the gold "Learnable!" marker), the two confirmation dialogs (the red "Not enough" shortfall
band separates Schedule from Learn), the Scheduling Complete dialog, the Concert Info screen, and
the unlocked/scheduled Lessons-button states. The exact song spellings ("Run n' Run!", "Believe in
Miracles!", "Full Speed Ahead! Umadol Power☆") match the client's master database and differ from
the pre-launch translations.

It does NOT enable actuation: there is no capture of the shop immediately after a successful LEARN
(only after a SCHEDULE), no concert backstage or Great Success result, and no unlocked-but-unscheduled
career screen (the player scheduled a song immediately on unlock, so the UNLOCKED state is proven by
the classifier rather than a fixture). Tutorial-slide captures from the same session
(MuMu-20260723-0215xx) were used only as text/geometry references and are deliberately not committed
as production fixtures.

## Fourth capture: the Complete Career screen (2026-07-24 10:14)

The screen a finished (or failed) Grand Concert career lands on, where the run previously spiraled
through the unknown-screen recovery ladder: none of the shared screen checks recognise it, and the
URA career-end template (`career_end_skills`) scores ~0.55 on it.

| Fixture | Original file | Screen |
|---|---|---|
| career_complete.png | on-device `files/temp/source.png` (the bot's own last capture, pulled 2026-07-24) | "Complete Career": header top-left, Fans 74,473, Attributes/Skills tabs, stat GRADES on a pentagon (no numeric stats), aptitude grid, trainee card `[Jokester ☆ Vibes] Tosen Jordan`, purple "Remaining Performance Points" banner, balance strip Da 45 / Pa 13 / Vo 47 / Vi 60 / Co 55, bottom buttons Skills (Skill Pts 926, "!" badge) / Complete Career / Lessons ("!" badge) |

Unlike the earlier sets this is a bot-saved capture, not a MuMu screenshot. The general warning
about the bot's OpenCV camera writing BGR does not apply to this save path: the channel order was
verified RGB by measurement (the Da balance icon reads blue-dominant, the Pa icon red-dominant,
the Complete Career button red-over-blue), and the fixture test carries an RGB canary on the
Complete Career button so a swapped file cannot slip in silently.

What it proves: the probe anchors for recognising the screen (the flat purple banner fill, the
five balance-type icons between the white strip rows, the pink Complete Career button). What it
does not prove: anything about what lies BEHIND the three buttons; the end-of-career Lessons spend
and the Complete flow still need their own captures before actuation.

## Fifth capture: the concert-pending screen (2026-07-24 14:04)

The screen where a career waits for the player to run a concert ("2nd Concert", Great Hype MAX,
Goal ribbon over the Concert button), captured via adb screencap while the unknown-screen ladder
was churning on it during the first Taiki Shuttle validation career. Verified pixel-identical
anchors against the 3rd Concert MuMu screenshot from 2026-07-23 (`MuMu-20260723-212629-967.png`),
so one probe covers all five concerts.

| Fixture | Original file | Screen |
|---|---|---|
| concert_pending.png | adb screencap 2026-07-24 14:04 | "2nd Concert" pending: purple Hype Level banner (same asset as the Complete Career banner, at y ~500), hype tier text, Goal ribbon (red) over the Concert button, Lessons button, Skip/Quick pills |

## Sixth capture set: the full concert flow (2026-07-24 14:39, MuMu screenshots)

The maintainer played the 3rd Concert screen by screen specifically to enable the concert escort.
Native RGB, 1080x1920. The flow proved linear with no choices: pending -> start confirmation ->
playback -> result banner -> schedule overview -> career (with the next turn's New Year trainee
event following Late Dec concerts, which belongs to the ordinary event handler, not this flow).

| Fixture | Original file | Screen |
|---|---|---|
| concert_confirm.png | MuMu-20260724-143907-012.png | "Confirmation" / "Ready to start the concert?" dialog, Cancel / Start, over the blurred pending screen |
| concert_playback.png | MuMu-20260724-143910-938.png | 3D performance with the white skip disc (brown glyph) bottom right |
| concert_success_banner.png | MuMu-20260724-143914-107.png | GREAT SUCCESS! banner with the green Next button |
| concert_overview.png | MuMu-20260724-143924-027.png | Concert schedule overview (1st-3rd all GREAT SUCCESS, "4th Concert / Turns left: 12"), green Next |
| bonuses_updated.png | adb screencap 2026-07-24 15:18 | Post-concert "Bonuses Updated!" acknowledgment (Close / Confirm over the career screen), captured while the escort's first live run waited on it |
| active_bonuses_panel.png | adb screencap 2026-07-24 17:52 | The "Active Concert Bonuses" panel opened from the career screen: green header, a Bonus Effects row (Friendship Training Effectiveness +10%, Specialty Priority +10, Support Chain Event Frequency Lvl 0) and an Active Songs strip of four jackets, over a single Close. Informational, not an acknowledgment, so it needs a probe of its own: it must never be mistaken for the Close/Confirm dialog above it |
| concert_on_stage.png | adb screencap 2026-07-24 15:47 | The Grand's "ON STAGE!" huddle (Inspiration-style interstitial, vivid pink-purple medallion; one tap proceeds), captured while the escort waited on it at the finale |
| grand_confirm_unchecked.png | MuMu-20260724-183643-965.png | The Grand finale's start confirmation: Hype Level banner (Great Hype) and the "Skip the Grand Concert cutscene" checkbox UNCHECKED (gray glyph), captured while the second validation career's escort waited on it |
| grand_confirm_checked.png | MuMu-20260724-183711-056.png | Same dialog with the cutscene-skip checkbox CHECKED (green glyph), after the maintainer tapped it |

## Seventh capture: the career-end Lesson list with greyed-out cards (2026-07-26 17:10)

Saved automatically by the drain-failure capture in `drainLessonsAtCareerComplete` during the
first Copano Rickey queue career on 2026-07-26 (`gc_drain_no_lessons_20260726_171013.png`), then
channel-swapped into the fixture orientation: the bot's `saveBitmap` PNGs store the opposite R/B
order from these fixture files, so the raw pull is unusable for colour probes until swapped.

| Fixture | Original file | Screen |
|---|---|---|
| technique_list_career_end_dimmed.png | on-device `files/temp/gc_drain_no_lessons_20260726_171013.png` (R/B-swapped) | Career-end Lesson list, balances Da 8 / Pa 33 / Vo 41 / Vi 85 / Co 10: cards 0-1 (Dance Step Advanced Class Da 24, Group Lesson Basics Da 15) greyed out whole because they are unaffordable, card 2 (Acting Intermediate Class, Pa 12 + Vi 12) bright with the gold Learnable! marker |

This frame is the regression lock for the 2026-07-26 incident: the career-end list greys the
ENTIRE unaffordable card (header included), the old presence rule keyed on card 0's bright
header alone, and two career-end drains aborted with "the list did not open" while roughly 177
performance points expired. It pins the dim-tier card-kind thresholds and the any-card presence
rule, and proves dim = unaffordable / bright = Learnable stays readable on this layout.

## Eighth capture set: training-screen Performance panel (2026-07-27, telemetry corpus)

Cut from the `gc_train_*` frames the dev-only `GrandConcertTelemetry.captureTrainingFacility`
instrumentation saved during the 2026-07-27 rotation careers (Sakura Bakushin O endgame and the
Super Creek run), pulled off-device and channel-swapped into the fixture orientation like every
other bot-saved frame. These calibrate the training-screen point reader that feeds the
point-income training bias.

| Fixture | Original file | Screen |
|---|---|---|
| training_panel_vi_gain.png | on-device `files/temp/gc_train_0199_GUTS.png` (R/B-swapped) | Guts training selected on a bright beach backdrop, "+23" beside the Vi row, balances Da 13 / Pa 66 / Vo 14 / Vi 7 / Co 36 with /300 caps (two concerts passed). The "+N" fill here samples on the RED half of the glyph's gold-to-red gradient: the launch-night single-hue detector missed exactly this frame class. |
| training_panel_rainbow.png | on-device `files/temp/gc_train_0004_GUTS.png` (R/B-swapped) | Guts friendship training granting two types at once: gain glyphs beside BOTH the Da and Vi rows. Pins the two-row split-gain case. |
| training_panel_hidden.png | on-device `files/temp/gc_train_0050_WIT.png` (R/B-swapped) | A mid-loop capture with an overlay hiding the panel entirely. Pins that the panel presence gate refuses the frame, because its gain boxes read pure noise. |

Together with training_guts_before.png (whose trainee's red jacket floods the Vi row's gain box
with warm pixels), these four pin the gain-row detector's two-factor rule: warm gradient fill
AND the glyph's thick white outline, at most two rows. Measured separation: art tops out at 37
white samples, the faintest real glyph reads 115+.

## Screens still needed before further automation

1. The lesson list immediately after a successful LEARN (the refresh; we have only after-schedule)
2. An unlocked-but-unscheduled career screen (Lessons lit, no Scheduled badge)
3. Concert countdown and the Hype gauge at a second tier (Great Hype) for gauge calibration
4. Pre-concert backstage screen
5. Great Success result screen and the post-concert point-cap increase
6. Senior scenario-link choice event (the 16-song event)
7. Grand Concert finale and the scenario spark screen
8. The flow behind each Complete Career button: end-of-career Lessons, the Skills screen reached
   from it, and what the Complete Career button itself leads to

Items 1-3 from the earlier list (unlocked Lesson button, performance balances, the three-card
shop) are covered by the capture sets above; the career-completion screen from the old item 10 is
now covered by the fourth capture.
