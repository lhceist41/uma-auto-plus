# Changelog

All notable changes to **UMA Auto+** are documented in this file.

This project is a fork of [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation). The fork baseline is upstream **v5.4.8**. A summary of all features added on top of that baseline can be found at the bottom of this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.1.0] - 2026-04-14

### Added
- **Configurable per-run time limit.** A new `Max Runtime Per Run` slider in **Run Queue Settings** controls how long a single career may take before the bot gives up on it. Range 30–360 minutes, default **180**. It used to be fixed at 90 minutes, which was too tight for Unity Cup / URA Finale runs on slower devices.
- **The mid-run skill buy gives up gracefully.** If the skill screen fails to open three times in a row, the bot logs a warning and moves on with the career instead of retrying forever.
- **Better maiden race retries.** A hiccup during a maiden race attempt no longer counts as "already checked today" -- the bot tries again next turn. A genuinely completed check still counts.
- **Irregular Training safe-skip.** In `Trackblazer.decideNextAction`, if `ButtonTraining.click()` fails during irregular-training evaluation, the bot logs a warning, marks the check done for this turn, and falls through to the normal decision logic — instead of looping forever trying to click a button that isn't there.
- The between-run navigation learned to recognize two more screens (the Auto-Fill button and the support deck screen).

### Changed
- **Queues keep going past a failed run by default.** Fresh installs now continue the queue past a failed or timed-out run instead of aborting everything on the first problem. Existing installs keep their saved choice.
- **Between-run navigation hardened.** The CAREER button is found three different ways, the Skip Off button is tapped at its measured position, the deck screen is identified more reliably, and Auto-Fill can no longer be clicked in a loop.
- The CAREER button on the home screen is recognized more reliably.

### Fixed
- **Mood-recovery deadlock at career start.** When the trainee needed mood recovery on the very first turn, the bot could loop forever wanting to recover but never being allowed to. It now trains once first, then recovers mood on the next turn.
- The skill-buy retry loop described above.
- Maiden Race never retrying a transient failure within the same day.
- Trackblazer looping when the Training button failed to click.

### Documentation
- Added this CHANGELOG.md with full history from the fork baseline forward.
- Release notes on GitHub are now generated from this changelog automatically, so the two always match.

---

## [1.0.2] - 2026-04-14

### Changed
- **Release builds hardened for public distribution.** Releases are now always properly signed (a build that can't be signed fails instead of shipping wrong), and only release-appropriate network rules ship in the release APK.

### Fixed
- Release APK signing configuration is now strict about missing signing secrets — prevents accidentally shipping an unsigned or debug-signed release build.

---

## [1.0.1] - 2026-04-13

### Fixed
- **Crash on first launch.** The icon library the app used could crash it the very first time it opened; every icon was swapped to a different library that doesn't.
- Corrected several default values in Run Queue Settings and related pages.

---

## [1.0.0] - 2026-04-13

### Added
- **Rebrand to UMA Auto+** — new app name, package identity (`com.lhceist41.uma_auto_plus` so it can live side-by-side with the upstream app), app icon, and splash.
- Version numbering reset to `1.0.0` for the first proper branded release.
- In-app update checker that polls the GitHub releases feed and surfaces new versions inside the app.

### Changed
- Queue defaults: `totalRuns` 2 → 5, `autoFillSupports` false → true, to reflect the common multi-run workflow.
- Built-in character presets: 51 presets (17 characters × 3 scenarios), picked from a filter-by-scenario menu on the Home page.

---

## [5.5.1] - 2026-04-13

### Added
- **Crash resilience for the multi-run queue.** Queue progress is saved continuously, so if the app crashes mid-queue you're told about it on the next launch and can pick up where it left off. Logging load during long runs was also reduced.

### Fixed
- Long automation runs no longer overload the app's UI, which in testing was the root cause of the mid-run crashes.

---

## [5.5.0] - 2026-04-13

### Added
- **First public GitHub release** of the fork.
- **Multi-run queue.** Queue 2–20 consecutive career runs of the same scenario. Between runs, the bot walks itself from the career-complete screen back through home, scenario select, deck setup, and confirmation into the next career -- no input needed.
- **Queue progress UI.** Home page shows the current run number, total runs, and a skip-current-run button while a queued session is active. Interrupted queues are detected on next launch and the user is offered to resume.
- **Start the bot from anywhere.** If you start it from the home screen, scenario select, or deck setup instead of the training menu, it finds its own way to the training menu first.
- **Run Queue Settings page** with controls for: total runs, delay between runs, stop-on-error behaviour, reuse-last-launch-setup, and auto-fill support deck.
- **Automatic releases.** Every release is built, signed, and published to GitHub automatically.

### Changed
- README updated to clearly credit the upstream project and link back to steve1316/uma-android-automation.

---

## Custom features vs. upstream [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) (baseline: v5.4.8)

The fork already differed substantially from upstream v5.4.8 on day one. The list below is not exhaustive, but captures the major differences you'd notice compared to running the original:

### Architecture / app shell
- Rebranded to **UMA Auto+** (new name, icon, splash, package id `com.lhceist41.uma_auto_plus`).
- Swapped the icon library to fix a crash on first launch.
- Reduced logging load so long runs don't crash the app.
- Automatic, signed APK builds published to GitHub on every release.
- In-app update checker polling the GitHub releases feed.

### Multi-run queue (entirely new)
- Queue 2–20 consecutive career runs of the same scenario unattended.
- The bot navigates itself between runs: career summary → home → scenario select → deck setup → confirmation → cinematic → training menu.
- Queue progress is saved continuously for crash recovery and resumption.
- Queue progress UI on the Home page with skip-run button.
- Configurable per-run max runtime with a safety timeout (default 180 min).
- Configurable `stopOnError` (default: continue past errors), reuse-last-launch-setup, auto-fill support deck.

### Character presets (entirely new)
- 51 built-in character profile presets (17 characters × 3 scenarios: Trackblazer, Unity Cup, URA Finale).
- Scenario-filtered preset picker on the Home page that deep-merges preset settings into the active profile.

### Bot behaviour improvements
- Career flow: the mood-recovery deadlock fix, the skill-buy retry cap, and cleaner per-day state resets across scenarios.
- Racing: maiden race attempts retry after a hiccup instead of counting as done for the day, and "no maiden available today" is distinguished from "no race fits her aptitudes".
- Trackblazer: irregular training fails safe when a button can't be clicked, and the consecutive-race count that guards the -30 stat penalty is tracked correctly across days.

### Screen recognition
- The bot recognizes several screens and buttons the original doesn't, mostly for the between-run navigation.
- The home-screen CAREER button is detected more reliably.

### Android release hardening
- Releases are always properly signed.
- Release builds only allow secure network connections.
- Build configuration fixes so release builds work reliably.
- The app identifies itself differently from the original, so both can be installed side by side.

---

[1.1.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.1.0
[1.0.2]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.2
[1.0.1]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.1
[1.0.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.0
[5.5.1]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v5.5.1
[5.5.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v5.5.0
