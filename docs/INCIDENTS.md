# Incident record

Durable engineering lessons from failures that reached production. Each entry is a confirmed
cause with a shipped fix or a standing invariant — not a hypothesis. When an entry names a file,
function, or flag, verify it still exists before acting on it; the code is the source of truth.

Two entries are marked **INVARIANT**. Those are rules that must be obeyed on every change to the
affected subsystem, not merely history.

---

## 1. MessageLog is a process-wide deadlock hazard — INVARIANT

**Invariant:** anything that exists to recover from a hang must perform its recovery action
**before** it logs, and must use `android.util.Log` on its own thread. Never call `MessageLog`
first in a watchdog.

**Symptom.** Two separate watchdogs fired correctly and then froze on their own log line, one
statement before the recovery they were about to perform. Observed live twice on 2026-06-11.

**Confirmed cause.** The foundation library's `MessageLog.log()` holds a single global lock across
both the buffer append and its EventBus post, and EventBus dispatches subscribers synchronously on
the posting thread. A subscriber doing blocking work — the original `onJSEvent` emitted straight
onto the React Native bridge — therefore freezes every thread that ever logs. The queue thread was
parked under that lock during between-run navigation; the stall watchdog fired, called
`MessageLog.e`, and deadlocked one line before `killProcess`. The navigation deadline died the same
way before its interrupt.

**Fix.** `onJSEvent` now enqueues into a bounded queue drained by a daemon worker. `sendEvent`
swallows dead-context `RuntimeException` (otherwise it recurses via `SubscriberExceptionEvent` →
`MessageLog.e` → another post). Both watchdogs act first and log via `android.util.Log`.

**Verification.** Both watchdogs subsequently fired and completed their recovery under load.

**Files.** `StartModule.kt`, `Game.kt`. Commit `0d8c38fc`.

**Status.** Fixed and in force. The invariant applies to any future watchdog or recovery path.

---

## 2. Accessibility service death on MuMu — INVARIANT

**Invariant:** `gestureUtils` accessors are per-access getters on purpose. **Never cache the
reference.** A cached reference keeps dispatching into the dead service object after a rebind.

**Symptom.** Two distinct failure modes, both silent:

1. Every tap and swipe stops registering while screen capture keeps working. Scrollbar thumbs pin
   mid-track, lists appear frozen, taps never land.
2. The bot wedges on an ordinary screen — a normal event, a result screen — while the service still
   reports as bound.

**Confirmed cause.** Mode 1: the emulator wipes `enabled_accessibility_services` outright
(`BatterySaverPolicy: accessibility changed to false` in logcat). Not configuration-dependent —
reproduced with 6 GB / 6 cores, no background cleanup, no battery saver. Mode 2 is nastier: MuMu
kills gesture *dispatch* while leaving `enabled_accessibility_services` intact, so a string-only
check passes and never rebinds while every tap silently no-ops. Proven by an `adb input tap` at the
bot's own recovery coordinate advancing the dialogue where `dispatchGesture` at the identical
coordinate did not — the OS input path was healthy, only dispatch was dead.

**Fix.** `Game.ensureAccessibilityService()` rewrites the secure setting (requires a one-time
`adb shell pm grant com.lhceist41.uma_auto_plus android.permission.WRITE_SECURE_SETTINGS`), wired at
bot start, per campaign tick, per buy pass, and pre-commit. For mode 2, a string check is not
enough: `Game.forceRebindAccessibilityService()` toggles the service off then on — the entry must
actually be *removed*, because the framework ignores a same-value rewrite. It fires from
`Campaign.recoverFromUnknownScreen` at stuck counts 13 and 19 (above the ~12-cycle healthy maximum,
below the 25-cycle stop) and from `CareerLaunchNavigator` once `consecutiveUnknowns >= 2`.

**Verification.** Observed four times before the fix; the rebind has since recovered runs in place.

**Files.** `Game.kt`, `Campaign.kt`, `CareerLaunchNavigator.kt`. The user-facing permission grant is
documented in `TROUBLESHOOTING.md`.

**Status.** Fixed; both modes covered. The tell for mode 2 is screen-agnostic — the bot wedges on
whatever screen was up when dispatch died, so "it stopped on a normal screen" is the signature.

---

## 3. Between-run navigation can wedge below the state machine

**Symptom.** The queue hung indefinitely with zero log lines — over twenty minutes parked on the
career summary (2026-06-11).

**Confirmed cause.** The per-run timeout covers only `Task.start`, and the three-minute stall
watchdog stays calm while navigator wait loops keep ticking the heartbeat. A call wedged *beneath*
`navigate()` was therefore invisible to both guards.

**Fix.** A ten-minute deadline thread interrupts the queue thread; navigator catch blocks
deliberately rethrow `InterruptedException`. After a 60-second grace it escalates to
`queueStopRequested` so the session still ends with a saved log.

**Files.** `StartModule.kt`, `CareerLaunchNavigator.kt`.

**Status.** Fixed.

---

## 4. The skill screen's SP counter is a selection preview, not a balance

**Symptom.** Eight "verified" skill purchases sat uncommitted overnight while the run reported
success (2026-06-09).

**Confirmed cause.** Pressing `+` decrements the displayed SP immediately, but nothing is owned
until Confirm commits. A fire-and-forget confirm — ignored click result followed by a blind Back
press — cancelled the purchase dialog while the SP drop made it look like it had worked.

**Fix.** `confirmAndExit` verifies the skill-list screen is actually gone and returns a Boolean.
Never treat an SP drop alone as a completed purchase. Related: parsed prices drift from actual
charges as discount tiers shift, so the budget tracker reconciles against the verified post-buy
screen value — without that, a 45-point skill was refused while 55 points sat on screen.

**Files.** `SkillPlan.kt`.

**Status.** Fixed.

---

## 5. Prediction icons gate what the bot can even see

**Symptom.** A trainee force-ended on the fan checkpoint having entered zero extra races, despite a
race pool that looked adequate on paper.

**Confirmed cause.** Race-list rows are located positionally from their prediction icon. Rows with
**no prediction icon do not appear to the bot at all.** Predictions are stat-dependent at runtime,
so a race pool derived from static data does not predict what the bot will actually see: an
early-Junior Medium specialist draws only single-star predictions, which originally had no template
asset.

**Fix.** Single-star rows are detected since 2026-06-10 (`IconRaceListPredictionSingleStar`,
`PredictionTier`), but tier still outranks fans: single-star races are entered only under the
fan-emergency policy or the tiered maiden fallback (last turn always; ≤2 turns of slack requires
≥30% energy; otherwise ≥50%). A fan-emergency entry policy triggers on goal-text OCR
(`getGoalText().contains("fans")`) — the previous `race_criteria_*` template family died in a game
update, which had silently disabled fan-requirement forcing for every trainee.

**Files.** `Racing.kt`, `CustomImageUtils.kt`, `components/Icon.kt`.

**Status.** Fixed. A row with no prediction icon remains invisible by design.

---

## 6. Fan-checkpoint failures are a visibility problem, not an aptitude wall

**Symptom.** A Medium/Long specialist repeatedly force-ended at the 3,000-fan Junior checkpoint. The
long-standing explanation — that she needed a Mile aptitude bump — was wrong.

**Confirmed cause.** Cross-checked against `races.json`: Junior year contains eight Medium-2000m
Turf races, two of which clear the checkpoint alone. Aptitude was never the constraint. The real
constraint was prediction visibility (entry 5).

**Fix.** Single-star detection plus the fan-emergency policy. What finally completed the full career
was a Pal-type borrowed friend card (34 training turns versus 17 the run before) combined with the
350-SP mid-career skill threshold — earlier attempts fought the stat wall with zero skills because
the old 1,200-SP threshold never fired before the run died.

**Note on deck validation.** `runDeckValidation()` no longer uses the old "Junior fan-farm impossible
if Sprint and Mile are both below B" heuristic, which false-positived on Medium specialists. It now
warns on preferred-distance and preferred-style aptitude below the floor, then runs a
prediction-visibility warning when the best distance aptitude is below B.

**Files.** `Campaign.kt` (`runDeckValidation`), `Racing.kt`, `src/data/races.json`.

**Status.** Resolved. The lesson generalizes: diagnose entry starvation as a perception problem
before assuming an aptitude problem.

---

## 7. Goal-sparse trainees starve under mandatory-plan-only racing

**Symptom.** A trainee trained roughly 90% of turns and reached her first hard goal hundreds of
stats short, force-ending early (2026-06-11).

**Confirmed cause.** With `enableMandatoryRacingPlan: true` and `enableFarmingFans: false`,
voluntary races happen **only** on planned turns — the smart-racing branch in
`checkEligibilityToStartExtraRacingProcess` is structurally unreachable in that configuration. A
trainee with a thin goal chain then has almost no races at all.

**Fix.** Curated racing plans are life support, not garnish. Dense-goal trainees ride their goal
chains; sparse ones need roughly 6–10 plan entries covering the Junior post-debut stretch and the
Classic gaps. The previously silent eligibility fall-through now logs
`[RACE] Extra racing skipped this turn: <reason>`.

**Files.** `Racing.kt`, `src/data/characterPresets.ts`.

**Status.** Fixed; the logging makes the fall-through visible.

---

## 8. The training-settings cascade must be tuned as a set

`enableFarmingFans`, `minFansThreshold`, `daysToRunExtraRaces`, and `ignoreConsecutiveRaceWarning`
jointly govern how aggressively the bot enters extra races to clear the 3,000-fan Junior checkpoint.
Changing one without the others is usually wrong and produces either starvation (entry 7) or
runaway racing.

**Files.** `Training.kt`, `Racing.kt`, `src/context/BotStateContext.tsx`.

---

## 9. The borrowed friend card never persists between careers

**Symptom.** Start Career renders as enabled but is silently ignored while the friend slot is empty.
Verified live: 26 clicks produced zero transitions, and an `adb` tap was ignored identically.

**Confirmed cause.** The game clears the borrowed card at career end and refuses to start with an
incomplete deck, without surfacing an error.

**Fix.** The navigator fills the slot from the Borrow Card picker. Both deck-screen paths carry
bounded retries (2 fill attempts, 5 Start Career clicks) before failing with structured
diagnostics rather than looping silently.

**Related trap — duplicate support cards.** The picker *commits* a pick on the first tap even when
the chosen card's character is already in the deck; the game only blocks later, at Start Career,
while marking both clashing cards with a persistent "Duplicate Support" tag. The duplicate rule is
per **character**, not per card. The bot therefore skips tagged rows during the scan and, if a
duplicate lands anyway, replaces it before attempting to start.

**Files.** `CareerLaunchNavigator.kt`, `SmartBorrowList.kt`, `components/Label.kt`.

**Status.** Fixed. Memory topic: `smart-borrow-duplicate-handling`.

---

## 10. Run-queue navigation state machine

Careers are chained by `CareerLaunchNavigator.kt` through an explicit FSM:
`CAREER_SUMMARY → COMPLETE_CAREER_CONFIRMATION → POST_RUN_RESULTS → CAREER_COMPLETE_DIALOG →
HOME_SCREEN → CAREER_ENTRY → SCENARIO_SELECT → DECK_SETUP → CONFIRMATION → CINEMATIC →
TRAINING_MENU`.

States whose template assets are missing fail with structured `NavigationResult` diagnostics rather
than looping silently. Preserve that property when adding states.

**Files.** `CareerLaunchNavigator.kt`.

---

## 11. Alarm Clock retry policy — preserve the bridge call

`Campaign.markAlarmClockPolicySkipped()` sets a per-race flag (`bAlarmClockPolicySkippedThisRace`).
Trackblazer's `shouldRetryRace` early-returns on that flag so subsequent retry prompts on the same
race are auto-dismissed. **If you touch the dialog handler chain, preserve this bridge call** — it is
easy to drop during a refactor and the failure is a silent retry loop.

**Files.** `Campaign.kt`, `Trackblazer.kt`, `DialogHandler.kt`.

---

## 12. Deck validation fires exactly once per career

The scenario-validation flow runs when the Umamusume Details dialog opens (the `umamusume_details`
handler). The `bDeckValidationChecked` flag ensures it fires only once per career. It is
informational — it warns, it does not block.

**Files.** `Campaign.kt`.

---

## 13. Mood floor is an opt-in redirect guard

`Mood.GREAT` is opt-in via the `moodFloor` setting. It exists for a narrow reason: certain trainees
have a story event that redirects the career objective to an unsuitable race when mood is allowed to
drop. Raising the floor prevents the event from firing at the wrong time. Applied only in the presets
that need it — it costs turns elsewhere.

**Files.** `Campaign.kt`, `src/data/characterPresets.ts`.

---

## 14. Some negative statuses are scripted and not curable

**Symptom.** A status rode along for a dozen turns while the Infirmary button appeared disabled, and
the bot repeatedly declined to act.

**Confirmed cause.** Certain story-locked statuses genuinely cannot be cured until a specific race
completes. The Infirmary button is legitimately disabled — this is not a misread.

**Fix.** `Campaign.checkInjury` force-attempts the infirmary once per persistent-status episode.
This cures the button-misread case, no-ops on the scripted case, and logs the contradiction instead
of silently carrying the status.

**Files.** `Campaign.kt`.

**Status.** Fixed.

---

## 15. Preset edits do not reach a configured rotation until it is re-picked

**Symptom.** A preset fix was shipped and installed, yet queued careers kept running the old values.

**Confirmed cause.** Two layers of staleness. Presets live in the JS bundle, so an edit to
`characterPresets.ts` requires a rebundle and install before it exists on-device at all. Separately,
the rotation editor snapshots settings into per-run rows at setup time, so even an installed preset
change stays invisible to an already-configured queue.

**Fix / procedure.** After shipping a preset change: rebundle, install, then **re-pick the affected
trainees in the rotation editor** (or re-apply on Home for single runs).
`Game.warnOnRacingConfigDrift()` logs `[CONFIG_DRIFT]` at career start when live flags deviate from
the applied snapshot — that log line is the tell.

**Files.** `src/data/characterPresets.ts`, `src/pages/Home/index.tsx`, `StartModule.kt`, `Game.kt`.

**Status.** Understood and instrumented; the rebundle-and-re-pick step is mandatory, not optional.

---

## 16. Trainee Select remembers grid positions — do not "optimize" it

Stored positions let a rotation switch skip the slow roster scan. The switch anchors the grid to the
top, swipes to the stored page, taps the one cell, and **accepts only on the same preview-OCR name
check the full scan uses**. A wrong trainee is therefore impossible; a stale entry merely costs one
slow fallback scan, which re-saves every position it passes.

Roster changes (a new pull, a sort shift) self-heal lazily at one slow switch per stale trainee, by
design. An eager whole-map refresh would key rows off OCR-derived names — the bug class that broke a
previous rotation match. The per-cell settle wait in the scan is load-bearing: without it the preview
banner is misread.

**Files.** `CareerLaunchNavigator.kt`, `StartModule.kt`.

**Status.** Working as designed. Memory topic: `trainee-position-store`.
