# Changelog

All notable changes to **UMA Auto+** are documented in this file.

This project is a fork of [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation). The fork baseline is upstream **v5.4.8**. A summary of all features added on top of that baseline can be found at the bottom of this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.3.0] - 2026-06-18

The headline feature: the bot can now grind a whole **rotation of different trainees** unattended -- queue several, and it cycles through them, training each under her own preset. Set it before bed, wake up to a stack of finished careers. On top of that, a near-complete preset overhaul (now 81 builds across 27 characters), a smarter skill-buying strategy, and two solid months of reliability work hardening every loop that was quietly killing overnight runs.

### Highlights

- **Trainee rotation.** Point the run queue at several different trainees and it plays each one in turn under her own settings, switching automatically between careers. The selector finds the right trainee on your roster by name, survives a mid-career app restart without resuming the wrong one, and refuses to start the wrong umamusume rather than guess. Off by default.
- **81 presets across 27 characters** (was 51 across 17). Added the June banner trainees -- Sweep Tosho, Mihono Bourbon, Mejiro Palmer, El Condor Pasa (Kukulkan Warrior), Tosen Jordan, Super Creek, Matikanetannhauser -- and Symboli Rudolf (Emperor's Path), then rebuilt stat priorities, spark targets, stat caps, and character event picks across every preset. Goal-sparse trainees now ship curated racing plans so they don't starve between objectives.
- **Smarter skill buying.** The new Optimize Knapsack strategy works out the best combination of skills your points can buy -- including how upgrade chains exclude each other -- instead of just grabbing the best-looking skill first, and the buy pass now verifies each purchase actually went through instead of trusting the on-screen point counter.
- **Junior fan-gate breakthrough.** The bot reads single-star race predictions and forces fan-goal races when a checkpoint is at risk, so Medium/Long trainees that used to force-end at the 3,000-fan Junior wall now clear it. Trackblazer fights for its Result-Points checkpoints the same way.
- **The unglamorous half: reliability.** Two months of fixes for everything that was quietly killing overnight runs: crashes while reading the screen, emulators silently disabling the bot's taps mid-run (it now heals itself), settings corruption, a rare freeze that disabled the bot's own recovery, career-end screens it could wedge on, and a pre-release audit that closed a batch of rare crashes.
- **x86_64 build.** Added an x86_64 APK split so it runs at native speed on emulators like MuMu instead of under ARM translation.

### Added

- Trainee rotation in the run queue (off by default): per-trainee presets, roster name-matching, and resume-safe career boundaries.
- Symboli Rudolf (Emperor's Path) and seven June-banner trainees, bringing the preset library to 81.
- The Optimize Knapsack skill-buying strategy: the best combination of skills for your points, not just the best-looking skill first.
- An x86_64 version of the APK, so emulators like MuMu run the bot at native speed.
- Single-star race-prediction reading and fan-goal / Result-Point race forcing for at-risk checkpoints.
- Per-career warning when live racing settings drift from the applied preset.
- Support-deck advisory that warns at career start when the deck is spread too thin.

### Fixed

- Crashes when the bot tried to read two parts of the screen at once.
- Emulators silently disabling the bot's taps mid-run -- the bot now notices and re-enables its own accessibility service.
- Saved settings ballooning in size or getting corrupted during long rotation queues.
- A rare freeze that also disabled the very recovery meant to fix freezes.
- Getting stuck leaving the skill screen at career end, and queue stops being reported with the wrong reason.
- A batch of rare crashes found in a pre-release audit.
- Consecutive-race warnings no longer suppress or wrongly abort a race the bot actually wants to enter.

### Changed

- Replaced the binary alarm-clock toggle with a four-way carat-spending policy (Never / G1 only / G1 + Finale / Always).
- The log viewer now only accepts connections from your own computer.
- Updated the underlying engine and refreshed the bundled game data.

---

## [1.2.5] - 2026-04-22

A pile of stability fixes for stuff that was silently sabotaging runs, plus some real speed wins. Fourteen bugs squashed across the bot loop, settings, and UI, and the bot is noticeably faster on the hot path.

### Highlights

- **Recreation dates no longer get permanently turned off mid-run.** One bad screenshot of the recreation icon used to flip a flag that never got reset, so the bot would stop checking for dates for the rest of the career. They're huge mood and energy bombs, so missing them silently was costing entire runs. Re-checked every turn now.
- **Miracle Cure and Rich Hand Cream stockpile actually works.** Was meant to keep up to 5 of each because Trackblazer races so much, but the inventory check bailed out the moment you owned even one. Now actually stockpiles up to 5.
- **Trophy-requirement race selection picks the right G1.** When filtering down to G1-only races for a Trophy goal, the bot was reading the wrong race's grade and fan count whenever the chosen G1 wasn't first on screen. Now picks the actual race.
- **No more 10-second per-turn stalls.** When the bot couldn't cleanly read the stat-table header (any UI flicker), it was burning a full 10 seconds waiting for a thread that would never finish. Fixed at the source. Big win for smooth turn pacing.
- **Mood recovery no longer loops forever.** If the Recreation buttons briefly weren't visible during a mood recovery attempt, the bot would keep retrying the same broken screenshot forever. Now bails cleanly and falls through to other actions.
- **Picking a character preset no longer wipes your custom event picks.** Switching characters used to nuke your 22 default support card picks and 31 Trackblazer scenario picks. They survive preset switches now.
- **Bot is faster on the hot path.** Four per-turn savings: lazy screenshot capture in the decision loop (skipped on race days and a few other fast paths), removed a redundant 1-second wait after skill-hint training, halved the worst-case turn-start stall ceiling from 10s to 5s, and trimmed back-button settling from 1s to 0.5s.

### Reliability fixes

- **Cancelled "already complete" recreation date no longer eats your monthly budget.** The recreation counter was ticking up even on no-op cancels. Now only ticks when you actually use a date.
- **Pre-finals skill purchase has a 3-attempt retry cap.** If the skill list fails to open on day 72, the bot used to click Skills on the wrong screen forever. Now caps at 3 tries and moves on.
- **Trackblazer consecutive-race-warning abort navigates back properly.** Used to leave the bot stranded on the race list with no way to recover. Now backs out cleanly like the other Trackblazer aborts already did.
- **Maiden race dialog-failure abort navigates back too.** Same fix on the race confirmation screen.
- **Race-popup flag no longer forces RACE turn after turn.** When the game showed "goal not reached" or "insufficient fans", the bot would correctly try to race next turn. But if that race attempt found nothing suitable, the flag stayed set and the bot would keep trying to race every single turn after. Fixed.
- **Training analysis no longer mixes up two different evaluation modes.** The bot remembers its training analysis to avoid re-scanning the same turn twice, and that memory now resets when it should.
- **A garbled "Skill points" reward reading no longer crashes the turn.** A bad read on a skill-points event reward used to kill the event handling. Now falls back safely.
- **Default options for "Solid Showing" and "Don't Overdo It!" match the consensus pick.** Defaults said Option 2 (worse: random energy hit, mood damage with no recovery), but every character preset picks Option 1. New users now get Option 1 out of the box.

### Speed wins

- **Fewer screenshots per decision.** The bot was taking a screenshot at the start of every decision even when it didn't need one. Saves a slice of time on most turns, which adds up across a Trackblazer career.
- **Removed redundant 1-second wait after skill-hint training tap.** Pure dead time stacked on top of work the tap function was already doing. About 1 second saved per skill-hint training turn.
- **Halved the worst-case turn-start stall from 10s to 5s.** The turn-start reads finish in under 2 seconds on a healthy device, so the old 10-second safety ceiling was overkill.
- **Back-button settling wait from 1s to 0.5s** in the misc-checks recovery path. Back-navigation is almost always a pure UI transition with nothing to wait for.

---

## [1.2.0] - 2026-04-17

A big reliability + content release. The bot is dramatically more stable on MuMu (no more random mid-queue crashes), handles the new Trackblazer Racing Carnival event end-to-end, no longer gets stuck after the game's recent UI color refresh, and is measurably snappier on every loop tick. Every single one of the 17 baked-in character presets has been overhauled with proper distances, styles, stat targets, skill priorities, and event picks. Fresh installs now ship with skill buying enabled and a strong starting build for every character.

### Highlights

- **Bot survives long queues now.** A whole stack of fixes for the random "the bot just died after a while" problem on MuMu and similar emulators. Combined with auto-resume, you can leave a 10-run queue going overnight and expect to find it on run 8 or 9 the next morning instead of stopped at run 2.
- **Auto-resume after crash.** If the bot does get killed mid-queue (force-stop, OOM, system kill), the next time you start it the queue picks up from where it left off instead of starting over.
- **Racing Carnival event support.** The bot navigates the new Legacy Select screen with its green "Auto-Select" button (replaces the old pink one) and correctly handles the redesigned "Confirm Auto-Select" dialog by ticking both the new "Prioritize Carnival Bonus Sparks" checkbox AND the existing "Include Guests" checkbox before clicking OK. Works whether the carnival event is active or not.
- **Game UI color refresh handled.** A recent game update changed several screen banners from yellow-green to teal/mint (Skill Points header, Start Career! button, Auto-Select button). The bot was still looking for the old colors and missing those screens. It recognizes the new ones now; for the Skill Points label both color variants are accepted so the bot stays compatible if the game changes them again.
- **No more "stuck on the race prep screen" hang.** The bot used to occasionally get stuck looping on the pre-race screen forever when it couldn't find the View Results button. It recognizes the button's current look now, and falls back to the Race button as a safety net.
- **Skill buying is on by default and actually works.** Previously, the skill purchase screen was being misread, so pre-finals and post-career skill buys were silently aborting and your hard-earned skill points went unspent. Now both fire correctly, and fresh installs have skill buying enabled out of the box with a meta-aligned priority list per character.
- **Bot is noticeably faster.** A pile of optimisations cut redundant work the bot was doing every single turn - fewer screenshots, fewer screen checks, less time waiting where it was over-cautious. Most visible between runs, where post-run dialogs now fly by instead of plodding.
- **Smarter energy / item management in Trackblazer.** The bot reserves one low-tier energy item for emergency recovery before consecutive races push energy critically low (instead of being forced to Rest with the items unspent), holds onto a cupcake in case Royal Kale Juice gets bought (so the -1 mood penalty is offset), and ignores the consecutive-race safety limit in Late December where mandatory races force you into long race chains anyway.
- **Every character preset rebuilt.** All 17 characters × 3 scenarios (Trackblazer / Unity Cup / URA Finale) - 51 presets in total - got proper distance/style/surface assignments matching their best aptitudes, full skill priority lists, character-specific event picks (so the bot picks the right option on character story events instead of falling back to defaults), per-character stat targets, and Trackblazer shop blacklists so the bot doesn't waste coins on stat scrolls the character doesn't need.

### Character preset changes

- **Agnes Tachyon** - Fixed wrong unique skill in her priority list (was Mejiro Ardan's skill by mistake). Her real unique `U=ma2` is now top priority. Stamina and Power targets raised to 800 each for Medium reliability. Stat priority adjusted (Power moves up to rank 2) to match Pace Chaser Medium meta.
- **Grass Wonder** - Distance changed from **Medium → Long**. Medium was a B-rank distance for her (efficiency penalty); Long is A-rank. Skill plan and racing preferences updated accordingly.
- **King Halo** - Distance changed from **Mile → Sprint**. Mile was a B-rank distance for her; Sprint is her only A-rank distance. Stat targets adjusted to Sprint shape (Speed 1200, Wit 600, no Stamina focus). Skill plan and racing preferences updated.
- **Gold City (Autumn Cosmos)** - Style changed from **Pace Chaser → Late Surger**. Her unique "Dancing in the Leaves" activates when midpack on the final corner - that's a Late Surger pattern, and her awakening skill pool reinforces it.
- **Air Groove** - Style changed from **Pace Chaser → Late Surger**. Her unique "Empress's Pride" triggers on the final corner when overtaking, favoring Late Surger.
- **Vodka** - Distance changed from **Mile → Medium**. Both are A-rank for her, but her career schedule is heavily Medium-focused (Japan Derby at 2400m, etc.).
- **Matikanefukukitaru, Gold Ship, Mayano Top Gun, Hishi Amazon, etc.** - Aptitudes verified as already optimal; got the same skill plan / event override / stat target overhaul as everyone else.

### Smart-play improvements (cherry-picked from upstream)

- **Late December consecutive-race bypass.** The consecutive-race safety check (which normally caps how many races in a row before the bot rests) is now ignored on Late December turns, since that's the last racing window before mandatory goal races and you want every race to count.
- **Emergency energy item conservation + recovery.** The bot reserves at least one of its lowest-tier energy items (Energy Drink MAX, then Vita 20 → 65 in priority order) so that when consecutive races push energy critically low, it can self-rescue with that reserved item instead of forcing a Rest. Energy Drink MAX/EX also reclassified from quick-use to inventory items so they participate properly in the energy-pool decision.
- **Cupcake reserved for Royal Kale Juice.** The bot will hold onto at least one cupcake (preferring Plain over Berry Sweet) so when Kale Juice gets purchased later, its -1 mood penalty has a +1 offset ready.

### What's new under the hood

- **Self-recovery from freezes.** If the bot genuinely freezes (a rare emulator-level hang), it restarts itself within a few seconds instead of needing you to notice. The 3-minute threshold is generous enough that normal popups and dialog chains never trigger it.
- **Android is told to leave the bot alone.** The app now declares "this process is doing important work, don't kill it", and gets more memory to work with. This is the main reason long queues don't randomly die anymore.
- **Auto-resume queue state.** Queue position is persisted between iterations, so the next bot start can pick up where the previous session was killed.
- **Skill buying on by default.** Fresh installs now buy skills with the Optimize Skills strategy and buy inherited unique skills. The mid-run buy only triggers at 1200 SP, which lands late-Senior - matching the community "Senior April" buy window.
- **Far fewer screenshots per turn.** The bot was capturing the same screen four times over for a single check, and re-checking the same things two or three times per turn. It now shares one capture and remembers what it already checked.
- **Between-run screens recognized about twice as fast.** The bot was checking for eight rare screens before the one it sees most often (the post-run dialogs with Next/OK/Confirm/Close). The common case is now checked first.
- **Popup detection does half the work.** Popup titles always appear in the upper part of the screen, so the bot stopped scanning the bottom half for them.
- **Shop visits ~2 seconds faster.** The bot was waiting 3 seconds before reading the shop's coin counter when 1 is plenty.
- **Five frequently-checked buttons are found faster.** They only ever appear in the bottom half of the screen, so the bot stopped searching the top half for them.

### Bug fixes

- **Race-prep "View Results" hang.** The biggest individual bug fix in this release. The bot would loop forever on the pre-race screen when it couldn't spot the View Results button. It now recognizes the current button, and falls back to clicking Race if View Results isn't found.
- **False race success cascade.** When a race actually failed to complete (e.g. due to the hang above), the bot was still telling itself the race finished and incrementing internal counters. This caused secondary problems on the next turn (wrong consecutive-race count, "I already raced" confusion, queue death). The bot now correctly tracks race success vs. failure across all five race-handling code paths.
- **Skill plans silently aborting.** Pre-finals and career-complete skill purchases were both refusing to run because the bot couldn't recognize the skill purchase screen reliably. Each failed run was leaving 1000+ SP unspent. Both the screen check and the skill-point reading are more tolerant now.
- **Bot stuck on the post-career skill screen.** After the game's UI refresh recolored the "Skill Points" banner, the bot no longer recognized it -- the post-career skill buy silently aborted with leftover SP, and the bot then looped forever clicking Confirm on the still-visible skill screen until the queue gave up. It recognizes the new colors now, and backs out of the screen if it ever lingers.
- **Cupcake mood gain was silently lost.** Using a cupcake at NORMAL mood left the bot still believing the mood was NORMAL - the +1 was thrown away, for both cupcake types. Cupcakes now do what they say.
- **Stray tap into the cinematic after Start Career!** The bot was double-clicking the second "Start Career!" confirmation, and the second click could land on whatever appeared next (the cinematic, a dialog), occasionally leaving the run in a weird state. It only clicks once now.
- **Between-run navigation could waste 3.5 minutes flip-flopping.** Stuck-detection only fired when the bot saw the exact same screen repeatedly; if its reading flickered between two screens it would spin until a long timeout. It now notices "no progress" flip-flopping much sooner.
- **Long queues could slowly run out of memory.** A small amount of memory leaked on every screen check, which added up over multi-hour sessions until Android killed the app. Plugged.
- **Seasonal event banners could trick the bot into a wrong tap.** While looking for the CAREER button by its text, a left-side "Event Underway!" banner could fool the bot into tapping where CAREER usually is - even when the event UI had moved it. The bot now only reads the part of the screen where CAREER actually lives.
- **Crash within seconds of reopening the app after a crash.** The text-reading library could take down the new process before the bot UI even loaded. It starts up safely now.
- **Maiden race retry.** A transient failure on a maiden race attempt was marking "checked today" before the attempt completed, so the bot wouldn't retry that day even when the failure was just bad timing. Now only marks the day done after actual completion.
- **The freeze-recovery no longer misfires on long loading screens.** The old 45-second trigger could fire during legitimately slow loads or popup chains. It now waits 3 minutes and properly counts between-run activity as progress, so it never trips during normal play.
- **Distance preference defaulting wrong on aptitude ties.** Characters with two A-rank distances (like Matikanefukukitaru with Medium=A and Long=A) were trained for Medium even when their preset said Long. All 51 presets now lock in the distance their build intends.

---

## [1.1.0] - 2026-04-14

### Added
- **Configurable per-run time limit.** A new `Max Runtime Per Run` slider in **Run Queue Settings** controls how long a single career may take before the bot gives up on it. Range 30–360 minutes, default **180**. It used to be fixed at 90 minutes, which was too tight for Unity Cup / URA Finale runs on slower devices.
- **The mid-run skill buy gives up gracefully.** If the skill screen fails to open three times in a row, the bot logs a warning and moves on with the career instead of retrying forever.
- **Better maiden race retries.** A hiccup during a maiden race attempt no longer counts as "already checked today" -- the bot tries again next turn. A genuinely completed check still counts.
- **Irregular training fails safe.** If the Training button can't be clicked while evaluating irregular training in Trackblazer, the bot logs it and carries on with its normal decision - instead of looping forever on a button that isn't there.
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
- It is no longer possible to accidentally publish an unsigned or debug-signed release build.

---

## [1.0.1] - 2026-04-13

### Fixed
- **Crash on first launch.** The icon library the app used could crash it the very first time it opened; every icon was swapped to a different library that doesn't.
- Corrected several default values in Run Queue Settings and related pages.

---

## [1.0.0] - 2026-04-13

### Added
- **Rebrand to UMA Auto+** - new app name, package identity (`com.lhceist41.uma_auto_plus` so it can live side-by-side with the upstream app), app icon, and splash.
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
- 81 built-in character profile presets (27 characters × 3 scenarios: Trackblazer, Unity Cup, URA Finale).
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

[1.2.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.2.0
[1.1.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.1.0
[1.0.2]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.2
[1.0.1]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.1
[1.0.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.0.0
[5.5.1]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v5.5.1
[5.5.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v5.5.0
