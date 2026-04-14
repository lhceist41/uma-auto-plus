# Changelog

All notable changes to **UMA Auto+** are documented in this file.

This project is a fork of [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation). The fork baseline is upstream **v5.4.8**. A summary of all features added on top of that baseline can be found at the bottom of this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.1.0] - 2026-04-14

### Added
- **Configurable per-run max runtime.** A new `Max Runtime Per Run` slider in **Run Queue Settings** controls how long a single career run is allowed to take before the bot bails out with a timeout result. Range 30–360 minutes, step 15, default **180 minutes**. Previously this was hardcoded to 90 minutes, which was too tight for Unity Cup / URA Finale runs on slower devices.
- **Skill Point Check attempt cap.** `performGlobalChecks` now tracks consecutive failures of `handleSkillListScreen` and, after 3 failed attempts, marks the check as handled for the current run with a warning log. Prevents an infinite retry loop if the skill list screen ever gets into a state the bot cannot recover from.
- **Better Maiden Race retry semantics.** Transient failures (dialog handler errors, aptitude miss) now leave `bHasCheckedForMaidenRaceToday` unset so the bot can retry on the next turn, while genuine "work complete" states still mark the check as done.
- **Irregular Training safe-skip.** In `Trackblazer.decideNextAction`, if `ButtonTraining.click()` fails during irregular-training evaluation, the bot logs a warning, marks the check done for this turn, and falls through to the normal decision logic — instead of looping forever trying to click a button that isn't there.
- `ButtonAutoFill` and `LabelSupportFormation` component templates (used by the CareerLaunchNavigator between runs).

### Changed
- **Queue `stopOnError` default flipped to `false`.** Fresh installs now continue the queue past a failed / timed-out run instead of aborting the whole queue on the first problem. Existing installs keep whatever value was previously saved in SQLite.
- **CareerLaunchNavigator state machine hardened.** Multi-detector strategy for the CAREER button (full template + text crop + OCR fallback on "Event"), measured pixel coordinates for the Skip Off button (35.7% width × 96.2% height), deck-screen discrimination via `LabelSupportFormation`, and an `autoFillAlreadyDone` flag to prevent Auto-Fill click loops.
- Updated `components/button/career_home.png` and `components/button/career_home_text.png` templates for improved reliability.

### Fixed
- **Mood-recovery deadlock with `firstTrainingCheck`.** When the horse needed mood recovery but `firstTrainingCheck` was still active, the bot would loop forever wanting to recover but being refused. It now does a training first to clear the flag, allowing mood recovery on the next turn.
- Skill Point Check loop described above (functional, not crash-level).
- Maiden Race never retrying a transient failure within the same day.
- Trackblazer irregular-training tight loop on click failure.

### Documentation
- Added this CHANGELOG.md with full history from the fork baseline forward.
- CI/CD workflow now extracts the current version's section of CHANGELOG.md into the GitHub release body automatically, so release notes stay in sync with the repo.

---

## [1.0.2] - 2026-04-14

### Changed
- **Hardened Android release configuration for public distribution.** Enforced release signing (build fails if signing env vars are missing rather than silently falling back to the debug keystore), split network security config by build type so cleartext traffic is debug-only, and cleaned up ProGuard/R8 rules to handle transitive `javax.management.*` and twitter4j classes from Kord.

### Fixed
- Release APK signing configuration is now strict about missing signing secrets — prevents accidentally shipping an unsigned or debug-signed release build.

---

## [1.0.1] - 2026-04-13

### Fixed
- **Icon rendering crash.** Replaced all `Ionicons` usages with `lucide-react-native` equivalents across DrawerContent, PageHeader, SelectButton, and the Home page, resolving a `displayName` error that crashed the app on first launch.
- Corrected several default values in Run Queue Settings and related pages.

---

## [1.0.0] - 2026-04-13

### Added
- **Rebrand to UMA Auto+** — new app name, package identity (`com.lhceist41.uma_auto_plus` so it can live side-by-side with the upstream app), app icon, and splash.
- Version numbering reset to `1.0.0` for the first proper branded release.
- In-app update checker that polls the GitHub releases feed and surfaces new versions inside the app.

### Changed
- Queue defaults: `totalRuns` 2 → 5, `autoFillSupports` false → true, to reflect the common multi-run workflow.
- Built-in character preset system now ships 51 presets (17 characters × 3 scenarios) with `enablePopupCheck: false`, surfaced via a filter-by-scenario picker on the Home page.

---

## [5.5.1] - 2026-04-13

### Added
- **Crash resilience for multi-run queue.** Queue progress is persisted to SQLite on every state transition so an unexpected crash (Hermes SIGSEGV, OOM, etc.) can be detected and reported to the user on next launch. MessageLog writes are throttled to 300 ms batches with a 500-entry cap to keep the React bridge from overloading the JS thread during long runs.

### Fixed
- Reduced pressure on the JS thread during long automation runs, which in testing was the root cause of mid-run Hermes crashes.

---

## [5.5.0] - 2026-04-13

### Added
- **First public GitHub release** of the fork.
- **Multi-run queue feature.** Queue 2–20 consecutive career runs of the same scenario. Between-run navigation is handled by a finite-state machine (`CareerLaunchNavigator`) that walks the career-complete → home → scenario-select → deck-setup → confirmation → cinematic → training-menu flow without user input.
- **Queue progress UI.** Home page shows the current run number, total runs, and a skip-current-run button while a queued session is active. Interrupted queues are detected on next launch and the user is offered to resume.
- **Auto-navigation to training menu at bot start.** If the bot is started from anywhere in the career launch flow (home screen, scenario select, deck setup, etc.), the navigator finds its way to the active training menu before handing off to the scenario task.
- **Run Queue Settings page** with controls for: total runs, delay between runs, stop-on-error behaviour, reuse-last-launch-setup, and auto-fill support deck.
- **CI/CD pipeline** (`.github/workflows/build-apk.yml`) that builds release-signed APKs and publishes a GitHub release on every `v*` tag.

### Changed
- README updated to clearly credit the upstream project and link back to steve1316/uma-android-automation.

---

## Custom features vs. upstream [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) (baseline: v5.4.8)

The initial fork commit already contained substantial customisation beyond the upstream v5.4.8 baseline. The list below is not exhaustive, but captures the major behavioural / feature differences compared to running upstream:

### Architecture / app shell
- Rebranded to **UMA Auto+** (new name, icon, splash, package id `com.lhceist41.uma_auto_plus`).
- Replaced all Ionicons with `lucide-react-native` to avoid the React-Native-Vector-Icons `displayName` crash on first launch.
- Throttled `MessageLog` bridge updates (300 ms batches, 500-entry cap) for crash resilience during long runs.
- GitHub Actions CI/CD with release signing (env-based keystore), automatic APK builds on tag push, and GitHub releases.
- In-app update checker polling the GitHub releases feed.

### Multi-run queue (entirely new)
- Queue 2–20 consecutive career runs of the same scenario unattended.
- `CareerLaunchNavigator` finite-state machine handles between-run navigation (career summary → home → scenario select → deck setup → pre-run confirmation → cinematic → training menu).
- Queue progress persisted to SQLite for crash recovery and resumption.
- Queue progress UI on the Home page with skip-run button.
- Configurable per-run max runtime with a safety timeout (default 180 min).
- Configurable `stopOnError` (default: continue past errors), reuse-last-launch-setup, auto-fill support deck.

### Character presets (entirely new)
- 51 built-in character profile presets (17 characters × 3 scenarios: Trackblazer, Unity Cup, URA Finale).
- Scenario-filtered preset picker on the Home page that deep-merges preset settings into the active profile.

### Bot behaviour improvements
- **Campaign.kt:** mood-recovery deadlock fix, skill-point-check attempt cap, shared date-change reset hook that fans out to scenario-specific `resetDailyFlags()` overrides.
- **Racing.kt:** maiden race flag moved to success paths only (transient failures retry next turn), clearer separation between "no maiden available today" and "could not find good aptitudes".
- **Trackblazer:** irregular-training safe-skip on `ButtonTraining.click()` failure, correctly preserves the cross-day `consecutiveRaceCount` / `counterUpdatedByOCR` state used for the -30 stat penalty rule.

### Templates & components
- New component templates: `ButtonAutoFill`, `ButtonCareerHome`, `ButtonCareerHomeText`, `LabelSupportFormation`, and several more used by the career launch navigator.
- Updated `components/button/career_home.png` and `career_home_text.png` for improved detection reliability.

### Android release hardening
- Strict release signing (build fails if signing env vars are missing).
- Cleartext-traffic network security config restricted to the debug build type only.
- Expanded ProGuard/R8 keep rules for `javax.management.*` and `twitter4j.*` (transitive Kord dependencies).
- `applicationId` changed to avoid conflicting with the upstream app so both can be installed side-by-side.

---

[1.1.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.1.0
[1.0.2]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.2
[1.0.1]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.1
[1.0.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.0
[5.5.1]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v5.5.1
[5.5.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v5.5.0
