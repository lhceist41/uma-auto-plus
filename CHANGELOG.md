# Changelog

All notable changes to **UMA Auto+** are documented in this file.

This project is a fork of [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation). The fork baseline is upstream **v5.4.8**. A summary of all features added on top of that baseline can be found at the bottom of this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Added

- **Objective-aware adaptive skill triggers (Phase 2A, opt-in).** Presets can now declare what a career is for -- `rank` (the default, and exactly the previous behavior), `safe_completion`, `sparks`, or `race_reward` -- and Adaptive mode uses it to arm two new spending triggers. **Critical race**: when a mandatory goal race (read live off the Main screen's goal countdown and text -- never from packaged data, since events can redirect objectives mid-career) or a planned race is 1–2 turns away, the bot spends its skill points before the race instead of waiting for the high-water threshold. **Planned skill affordable**: once a skill from the preset's plan has been seen on the skill screen, the bot buys it as soon as the points cover its observed price, instead of letting it sit unbought. No speculative screen-opening, no fuzzy race matching (garbled OCR simply does nothing), and both triggers are inert for every preset that doesn't declare an objective -- and in Manual mode entirely. Skill-spend records move to `trigger-v3`, adding the objective and the trigger's rationale (which race, which source, which skill at what price); older records stay readable. Only the Copano Rickey URA sash profile is migrated (`race_reward`) in this batch -- recovery-skill injection and sparks-purity behavior remain future work.

- **Adaptive skill spending (V1, opt-in).** A new Skill Spend Mode on the Skill Settings page: Manual (the default, exactly the current behavior -- your configured threshold keeps governing) or Adaptive, which picks the mid-career high-water threshold from an account-strength tier instead. Tiers and thresholds: New 300, Developing 350, Established 600, Endgame 1000; Auto currently uses Developing. The tier labels are a loose guide -- support quality and roster depth matter far more than the Team Rank letter, which the bot never reads. Nothing else changes: finals and career-end buying, the optimizer, presets, and the breakpoint stop all behave exactly as before, and V1 does not learn, inspect the support inventory, or touch presets. Every skill-spend record now states the threshold, tier, and resolution reason that governed it (`trigger-v2`); older records stay readable as-is.

- **Copano Rickey**, the game's third pure dirt trainee, with a URA profile built to farm Kashiwa Kinen Winner's Sashes. Kashiwa is a Senior-May G1 over Dirt Mile at Funabashi, and her career objective there only asks for 3rd or better -- but a sash needs the win, so her URA preset ships a curated dirt agenda that deliberately stops racing after the Classic year and leaves the whole dense Senior half to her goal chain. She arrives at the sash race rested instead of chasing extra fans. Built as a Pace Chaser rather than a Front Runner: her unique and her entire innate kit read the back half of the field, which a front build never triggers. Her Unity Cup and Trackblazer presets ship as caution profiles -- Turf F against two turf-dominant schedules, the same advisory Haru Urara and Smart Falcon carry. Roster is now 217 presets across 71 entries; all three are research-graded until live careers say otherwise.

### Fixed

- **The bundled race calendar was missing the entire new dirt schedule.** The race scraper had been switched off years-of-patches ago on the assumption that races never change, and its filter also treated every race tagged with a historical era as "not on Global yet" -- which silently dropped Kashiwa Kinen, Teio Sho, M.C. Nambu Hai, Tokyo Daishoten and the rest of the dirt calendar the moment Global actually received them. Re-enabled, taught the three new racecourses (Kawasaki, Funabashi, Morioka), and narrowed the filter to only the eras Global genuinely lacks. 26 races added, none changed or removed.

---

## [1.3.8] - 2026-07-13

### Added

- **Every career now gets an estimated overall rank** -- the same kind of grade the game shows (B+, A, S...) -- computed each turn and again from the final stats at career end, then recorded in the run history next to the career's result. To score it, the bot reads the skills your trainee actually owns from her Details screen (including the unique skill's level), so the estimate reflects stats, skills, aptitudes, and the unique together. It mirrors a well-known community calculator, so treat it as an estimate rather than the game's exact number -- but it finally makes runs comparable at a glance. (From the upstream project.)
- **18 more characters, closing the global-roster gaps**: Admire Vega, Agnes Digital, Air Shakur, Bamboo Memory, Curren Chan, Eishin Flash, Fine Motion, Fuji Kiseki, Hishi Akebono, Inari One, Ines Fujin, Mejiro Ardan, Mejiro Bright, Narita Brian, Rice Shower, Sakura Chiyono O, Satono Diamond, and Yaeno Muteki -- 54 presets taking the roster to 210 across 70 entries, every currently-released Global trainee covered. Same research-graded pipeline as the S-tier batches: verified grids, growths, and goal chains, racing plans validated entry-by-entry against the race database, story-event pins verified against the character event data. Inari One joins the dirt roster as an End Closer; Ines Fujin is Front Runner-locked; the thin-Junior sprinters (Curren Chan, Hishi Akebono) carry Trackblazer avoid advisories; goal-sparse or fan-gated bodies (Agnes Digital, Rice Shower, Mejiro Ardan, Hishi Akebono) ship curated racing plans. Bamboo Memory postdates the bundled game data, so her presets ship empty event overrides until the next data sync and her story events fall back to the stat-option heuristic.
- **Legacy Farm presets** for Daiwa Scarlet, El Condor Pasa, and Air Groove (URA only): parent-farming editions of their regular builds that race a generated G1-dense agenda -- 17, 20, and 14 G1 entries per career -- for breeding careers where G1 wins and 1100+ core stats decide the spark quality. The agendas are spaced so the run stays inside the consecutive-race limit, and the picker marks them as farming builds, not badge chasers.
- Refreshed game data: the bot now knows **Bamboo Memory's story events** (it previously fell back to reading her event options off the screen), along with the latest support cards and skills. Behind the scenes the data pipeline was rebuilt so future game additions reach the bot much faster.
- The kept spark set is now recorded in full. The sparks screen only shows 6 rows without scrolling, but the "Keep this set of Sparks?" confirmation lists every spark on one screen -- the bot now reads the complete set from there (a 10-spark career previously recorded only the first 6), so the run history knows exactly which sparks each career kept.
- **The bot now remembers which sparks every career produced.** At the end of a career it reads the sparks screen -- each spark's name, its star count, and whether it's a stat, aptitude, unique, or skill spark -- and saves that alongside the career's result. If the auto-reroll spends, both the original and the redrawn set are kept, so you can see what was rolled and what was kept. This is groundwork for legacy/parent farming: the run history now knows what sparks each career actually ended with, not just its stats.
- **Smart Borrow.** When the queue fills the empty friend slot, the bot now scrolls down through the Borrow Card list and borrows the best card it finds from a curated list of great picks (Kitasan Black first), instead of settling for whatever sits in the top row. Follow trainers with strong cards to give it good options. Cards the game marks "! Duplicate Support" (their character is already in your deck, so the career can't start with them) are skipped during the scan, and if a duplicate lands in the slot anyway the bot swaps it for the next-best pick instead of clicking Start Career into a wall. On by default; turn it off under Run Queue Settings to keep the old top-row behavior.
- **Support-card dating schedule** (from the upstream project, off by default): if you run a Group support card (Team Sirius, Heirs to the Throne), the bot takes its recreation outings on the right career turns to advance the card's outing chain, and holds the final outing so the Pure Passion training buff lands exactly when you want it. It reads the card's "Group Event Progress X/Y" counter from the screen, so it stays on track even after restarts or manual dates, catches up missed outings on the next free turn (can be disabled), and quietly drops the schedule if the window has passed. Career-goal races always come first; when the schedule is active, ordinary mood recovery goes on dates with the trainee instead so it never wastes a scheduled outing. If an outing can't start for any reason, the bot moves on with its turn instead of getting stuck reopening the menu.
- The run history now records whether a URA Finale career actually **won** its finale, not just whether it reached it. Each of the three finale races is checked for a 1st-place result, and every career is labelled accordingly -- won the finale, reached it but lost a race, or the existing completed / force-ended / incomplete. Before this, a triumphant URA win and a run that limped through a lost finale looked identical in the logs. URA Finale only for now; Unity Cup and Trackblazer finales aren't read yet.

### Fixed

- Training failure chances that read as impossible values (over 100%) are rejected and re-read instead of being "corrected" into a plausible-looking wrong number. A real 55% could come through as a trusted 15%, and the bot would train right into the risk. (From the upstream project.)
- The five facilities' failure chances now sanity-check each other and the trainee's energy. Failure chances rise together as energy drops, so one facility reading wildly out of line with its neighbors -- or an impossible 99% at full energy, which could park the bot in needless rest turns -- is recognized as a misread and corrected, while genuine support-card discounts are kept. (From the upstream project.)
- A due fan, trophy, or goal-points requirement now outranks the pre-summer preparation turn -- the forced rest or mood date could eat the exact turn the requirement needed to race. Goal-points requirements also join the main racing gate, which previously only knew fans and trophies. (From the upstream project.)
- The training analysis from a turn that ended in a race or a rest no longer carries over into the next turn. Failure chances move with energy, so the stale numbers could mislead the next turn's training pick -- a finale run was seen reusing three-turn-old analysis.
- **Races your account has never run before no longer stall the bot.** The game only offers the instant "View Results" skip for a race you've already run once; a brand-new race has to be watched live, and the bot used to give up partway through the un-skippable playback and get stuck on the race screen until it stopped the run (this bit the parent-farming builds hard, since their dense race calendars enter G1s the account may never have seen -- especially dirt races). The bot now watches a first-time race through to its results screen, and it also recognizes the race lineup screen when resuming a career that was interrupted mid-race, so a resumed run picks the race back up instead of stalling. After a race has been run once it gains its skip button and is fast-forwarded as before, so this only costs the extra time on genuinely new races.
- The "TROPHY WON!" popup after a first-ever win of a race is closed right away now. Its green celebration banner isn't the standard dialog header the bot looks for, so no dialog was detected there at all and the bot could stare at the trophy for a minute or two until its slower recovery layer got around to closing it. Same story as the first-time races above: the parent-farming agendas are full of races the account has never won, so their careers hit this popup a lot.
- Added a last-resort recovery: if the bot is ever stuck on a screen it can't recognize or advance -- and its usual input-recovery hasn't helped -- it restarts the game and resumes the career in progress, instead of ending the run. Career progress is saved by the game every turn, so nothing is lost. This is a safety net for rare game-side freezes; watch it the first time it fires.
- Rainbow trainings are now detected from the actual rainbow glow around the support cards' portraits instead of being inferred from the orange friendship bars. The old way also counted maxed-friendship supports that weren't actually glowing, which nudged the bot toward trainings that weren't as good as they looked. A new calibration test under Debug Settings shows exactly what the detector sees, in case a future game update shifts the visuals. (From the upstream project.)
- Aptitude letters on the trainee's stat screen are read correctly now. The reader took the first letter that looked close enough, so a D or E grade could be read as B -- and the career-start deck check trusts those letters when it warns about a mismatched build. It now compares every letter and keeps the best match. (From the upstream project.)
- An overnight queue can no longer die on the TP recovery popup. When the emulator silently stops delivering the bot's taps (a known failure the bot already repairs on other screens), the between-run navigator used to click the same dead button 15 times and give up -- and the TP restore would report success without checking, leaving the quantity popup open with nothing spent. The navigator now repairs its tap delivery mid-screen whenever a screen stops responding, a restore only counts once the popup has actually closed, and the quantity popup is recognized as its own screen -- so a restore interrupted halfway (or a queue started right on top of the popup) finishes the refill instead of being mistaken for a results screen.
- Putting a Legacy Farm preset into a run-queue rotation now works. The rotation editor auto-fills the in-game name the bot looks for on the Trainee Select screen, and it treated "(Legacy Farm)" like an outfit -- so it told the bot to find "[Legacy Farm] Air Groove", a trainee that doesn't exist, and the queue stopped with "trainee not found" before the first career. Legacy Farm entries now target the plain character, and they also inherit the same wrong-outfit protection as the regular preset (so El Condor Pasa's farming runs can't grab her Kukulkan Warrior card by mistake). If you already added a Legacy Farm entry to a rotation, remove it and pick it again to refresh the stored name.
- The bot's bulk race information now refreshes with the app. It used to be written once at install and never again, so a future data update could ship newer race data that the race picker silently ignored.
- Stats pushed past the scenario cap by inherited blue sparks are handled correctly. Since the July update, a parent's blue sparks raise the trainee's personal stat cap above the scenario's base cap (up to +96 with strong sparks) -- and the bot used to treat that band as impossible: training into it was refused, and a maxed reading could be recorded below its true value. The reading ceilings and training decisions now allow the spark-raised band, so parent-farmed trainees with big sparks train and record correctly.
- Sprint races were invisible to the distance preference on both sides of the app: the setting stores "Short" but the race data says "Sprint", so the race filter silently dropped every sprint race from standard and smart-racing candidate pools (masked by the fan-emergency and aptitude fallbacks), and the racing-plan browser hid all sprint races even with every distance button selected. Both comparison sites now normalize the token; stored settings and presets keep their existing "Short" value, so nothing needs migrating.
- The career-end spark auto-reroll never actually fired: the bot didn't recognize the "Reroll Sparks" button on the sparks screen, so it clicked Confirm straight past it and silently kept whatever sparks rolled first -- on every career since the feature shipped. The button is now detected reliably. If you enable the reroll, keep an eye on its first use.
- If TP is short when the auto-reroll goes to spend, the game asks "Restore TP?" instead of offering the reroll -- and the bot used to back out and keep the first set. It now restores TP with the same items it already uses at career start (when item restore is enabled) and completes the reroll. A confirmed set can never be rerolled later, and the next career start would have drawn on the same items anyway, so backing out saved nothing. Found on the feature's very first live firing.
- When a rotation gets knocked out of sync (say, by a game update mid-queue) and the bot recovers, the resumed career now actually runs on the right trainee's preset. It used to fix the queue but keep the wrong trainee's stat priorities, racing plan, event picks, and skill plan until the career ended -- at one point Winning Ticket ran a full career on Symboli Rudolf's settings. Everything is reloaded from the correct preset now.
- Every rotation career now states in its log, right at career start, whether it is running the settings its rotation slot intended -- so a wrong-settings launch is visible immediately instead of only being discovered after a bad career.
- Each career's log file now contains exactly that one career. On a long queue session, every file used to also carry the tail end of all the careers before it, which made them confusing to read and share.
- Skill buying no longer counts skills you already own automatically as purchasable, which had been quietly eating into the skill-point budget for skills it could never buy.
- The game's daily reset can dump you back to the main lobby in the middle of a career. The bot used to flail on that screen, eventually recover in the wrong way (skipping ahead in the queue and briefly running the career under the wrong preset), and leave a mess in the logs. It now recognizes the lobby and simply re-enters the career in progress, keeping the same run and preset.
- The run-history report no longer counts a mid-career bot hiccup (a stop on an unrecognized screen) as a failed career. The career usually resumes right afterward, so these are tallied separately and a trainee that hit one transient stop is no longer made to look worse than she is.
- The kept-spark records no longer contain phantom "unreadable" sparks. The keep-set confirmation shrinks to fit the actual set, but the reader always walked eleven fixed slots and recorded the empty space below a short set as unreadable 0-star sparks -- about a third of all kept rows in the history so far are that noise (real sparks were read fine all along; no real spark has zero stars, which is how the reader now knows the list has ended). Old records are easy to discount: ignore their 0-star "unreadable" tail rows. Everything recorded from now on is the true set -- which the upcoming smarter keep-vs-reroll decision depends on.
- A run no longer dies to a brief connection outage. The game's connection-error handling used to give up after three quick retries and end the run -- which cost a 173,000-fan career nine minutes before the daily reset, exactly the window where the servers get flaky. When the retries run out, the bot now holds for three minutes (once per career) and then tries one final round; only a genuinely persistent outage still ends the run. The career is server-saved the whole time, so nothing is lost while it waits.
- Pressing Stop while the bot is navigating between careers is reported as a stop now, not as a navigation failure. The queue banner used to claim the navigation failed and point you at the emulator when you had simply stopped the bot yourself.
- The log written when a bot session ends -- the segment holding the between-career navigation and the sparks screens -- was saved without read permission, so it could not be pulled off the device for review (unlike the per-career logs). Every session start now sweeps the log folder so all files, including older ones, stay readable.
- When the spark reroll needs a TP refill and the item stock has run dry, the Carats top-up retries once when its quantity popup swallows the first Max+OK -- both live failures of that popup were first-attempt no-ops on the Carats option, and the re-drive has always worked. Before this, the refill completed minutes later through the recovery path, after the reroll window had already closed. The reroll also photographs the screen whenever its spend click fails, so any future miss documents the exact dialog it faced instead of leaving us guessing.
- The spark auto-reroll finally makes decisions that match how sparks actually work. The old rule only redrew when the build's core stat finished at 1100+ -- a bar no URA career on this account has ever reached -- so with the toggle on, the reroll silently never fired (six farm careers in one night kept a 1-star stat spark untouched). The decision is now priced from the verified odds: a 2 or 3-star stat spark is always kept, and a 1-star stat spark is redrawn unless every stat finished under 600 (a redraw cannot roll a 3-star there) or the set holds a 3-star aptitude or skill spark worth protecting. Run against that night's six recorded sets, the new rule keeps the five right keeps -- including the 3-star Speed jackpot and the set shielded by a 3-star Long -- and redraws exactly the one weak set. The toggle's description now says what it really does; the first actual spend is still worth watching.
- Ready for the July 14 anniversary update, which reskins the game's home screen: the bot's lobby detection leaned entirely on the button art a reskin is most likely to change. When the usual home-screen anchors stop matching, it now falls back to reading the CAREER button's text off the screen -- text survives a recolor that breaks image matching. This covers starting a queue from the lobby, the daily-reset bounce back to the lobby, the game-restart safety net, and a between-careers navigation that has stopped recognizing screens. If even that misses, every failed home-screen check photographs the unrecognized screen, so the first queue to meet the new skin collects the material for updated button images by itself. Treat the first session after the July 14 update as supervised regardless -- and note the Daily Races / Team Trials modes still use the old detection and may need that update to run.
- A native memory leak in the stat-gain number reader is plugged. Every detection call leaked a few megabytes of memory that Android never reclaims on its own, so the training analysis got measurably slower the longer a career ran (a sibling fork diagnosed the same inherited code and measured its analysis doubling from ~10 to ~20 seconds within a dozen turns). Long overnight sessions should hold their speed from the first career to the last now.
- The reroll's spend button is now cut from the real game. The very first screenshot from the new spend-failure camera showed the Confirm Reroll dialog's button in a different green and a different size than the image the bot was matching against -- it could never have clicked it, which is why priced-positive rerolls kept getting cancelled (three lost overnight, a fourth caught on camera the same evening the camera shipped). The button image is replaced with the live pixels. And since the dialog's own note confirms you choose between the original and rerolled sparks afterwards, a successful spend now photographs the next screen too -- that choice screen has never been seen live, and its first appearance provides the material the chooser will be built from. Until that chooser exists, the first successful spend is worth watching: the screens after it are new territory for the bot.
- Starting a single career (queue off) now launches the trainee whose preset you applied. The game preselects whoever was picked last, and the bot used to accept that stale preselection -- after an interrupted queue it twice came within one click of running El Condor's career under Symboli Rudolf's applied settings. Applying a preset on Home now records which trainee it is for, and a single-run launch verifies the Trainee Select screen against that record: if the wrong trainee is preselected, the bot finds the right one the same way queue rotations do (including the fast position-memory jump), and if she can't be found or the roster was skipped by a detection miss, it stops with a clear message before the career starts instead of ever launching the wrong one. Queue runs are unchanged, hand-tuned setups without an applied preset are unchanged, and the record starts existing the first time a preset is applied on this build.

### Changed

- The parent-farming presets now watch all five stats for the spark rescue, not just the build's core two. The blue spark's stat is picked at random from all five, and a stat that finishes under 600 can never roll a 3-star -- so the existing under-600 training boost now covers Stamina, Guts, and Wit too, lifting stragglers over the line late in a career instead of leaving two of five spark rolls dead. Early career nothing changes (while every stat is under 600 the boost applies evenly). Presets with the identical training block took the same fix. Re-apply the preset (or re-pick it in the rotation editor) for the change to take effect on existing setups.
- Switching trainees between queued careers is much faster. The bot now remembers where each trainee sits in the roster grid and jumps straight to her cell, verifying the name the same way as before advancing -- only a changed roster (a new pull, a re-sort) falls back to the old cell-by-cell scan. A measured switch took ~90 seconds before and ~30 after; staying on the same trainee was already instant and is unchanged.
- The final run of a queue now finishes the career properly instead of stopping on the Complete Career screen. The sparks screen (and its auto-reroll), the results dialogs, and the veteran registration all happen after that screen, so the last career of every queue used to silently skip them -- no reroll chance, and no sparks in the run history. The bot now walks the same career-end flow it uses between runs and parks the game on the home screen. A run that was stopped, skipped, or errored still leaves the screen exactly where it was.
- Internal housekeeping on the skills database so future game-data updates from the upstream project import cleanly. No visible change.

## [1.3.7] - 2026-07-09

A roster and control pass on top of the July-patch adaptation from 1.3.6: the full community S-tier preset list -- 156 presets across 52 entries -- reachable through a new searchable trainee picker with favorites and cross-scenario run queues, a control for when the bot spends skill points, smarter Unity Cup opponent selection, handling for URA Finale's new duel screen, and a set of reliability fixes.

### Added

- A searchable trainee picker replacing both preset dropdowns (Home and the rotation editor): one row per character/outfit with colored avatars, per-scenario recommendation chips, a badge showing whether a build has been proven in real completed runs or is still research-based, and a build summary per scenario card. Applying a preset now sets its scenario together with its settings, and the Home card remembers the applied preset across app restarts.
- Favorites: star a trainee in the picker and she pins to a Favorites section on top. Stored outside the settings database, so favorites survive preset switches and profile changes.
- Cross-scenario run queues. Rotation entries can mix URA Finale, Unity Cup, and Trackblazer; between runs the bot now recognizes the Scenario Select carousel and swipes it to each run's scenario before confirming (it learned the screen's header and all three scenario logos).
- Presets for the six most-requested top-tier cards: Kitasan Black, Nishino Flower, Seiun Sky, Maruzensky (Hot☆Summer Night), Oguri Cap, and Oguri Cap (Ashen Miracle). Built from verified per-card aptitude and objective data with racing plans checked against the race database; research-graded until live careers land, and the picker's badge says so.
- The rest of the community S-tier list: 17 more cards taking the roster to 156 presets across 52 entries. Ten new characters -- Silence Suzuka, Manhattan Cafe, Narita Taishin, Tamamo Cross, Mejiro Dober, Special Week, Smart Falcon, Meisho Doto, Tokai Teio, T.M. Opera O (New Year, Same Radiance!) -- plus alternate outfits for Special Week, Tokai Teio, Seiun Sky, Mayano Top Gun, Gold Ship, King Halo, and Taiki Shuttle. Built the same way as the first batch, with the same research badge. Smart Falcon is the second dirt specialist after Haru Urara, with a hand-built dirt race plan that starts early enough to clear his turn-25 two-win goal. Narita Taishin is the roster's first End Closer. Silence Suzuka runs Front (her unique needs a clear lead) and Medium-first despite her Mile aptitude, because her goals are almost all Medium races. Trackblazer warnings ship for the characters whose early-career race pool there is too thin (Manhattan Cafe, Tamamo Cross, Meisho Doto, both Teio cards) and for Smart Falcon's weak turf grade.
- A control for when the bot buys skills mid-career: preset chips (0, 350, 700, 1200) or a typed threshold, or "Career end" to hold everything for the Pre-Finals and end-of-career buys the way the upstream project does. Under Skill Settings.
- URA Finale's new Happy Meek stat-contest screen (added in the July patch) is handled: the bot reads which stat is being contested, pages to the trainee's strongest one, and confirms. Keep an eye on the first one it plays through.

### Changed

- Training gains that land a stat above 1200 are scored at half weight, matching the July rebalance rule that halves stat effectiveness past 1200 -- training points stop chasing soft-capped stats that race calculations discount anyway.
- The center button now says what pressing it does -- Stop while running, Start Queue (N runs) with the queue enabled, Start with the scenario otherwise -- instead of doubling as a scenario label. Daily Races and Team Trials are hidden from its dropdown for now; the career-preset flow owns the Home screen.
- Unity Cup picks its showdown opponent by reading the prediction circles on the confirmation screen -- the best-predicted matchup when none is a confident win, double circles weighted over single -- instead of always taking the middle card.
- Starting a run whose trainee/scenario pairing is a known mismatch (say a Turf-G trainee in a turf-heavy scenario) now asks for confirmation before launching, for the single applied preset or any rotation slot, and the rotation editor flags each mismatched slot with a banner. You can still start it, just not by accident.

### Fixed

- The between-run queue no longer stalls at Scenario Select. The bot now swipes the scenario carousel directly instead of trying to tap the little pulsing arrow, which it couldn't spot reliably enough -- a queue could die one screen short of switching scenarios.
- The between-run queue no longer dies on the post-career Umamusume Details summary card. Its big Close button is hard for the bot to spot, so the card is now recognized by its title text and dismissed.
- The Trackblazer scenario is recognized again on Scenario Select after the game recolored its "Start of the Climax" logo from red to blue. The bot was still looking for the old colors, so Trackblazer rotations could never confirm the scenario.
- A full Veteran Umamusume roster no longer loops the run queue. When the "Veteran Umamusume Max" popup blocks a career start (the roster is at its cap, e.g. 260/260), the queue now stops immediately with a clear reason -- transfer or release a veteran, then restart -- instead of bouncing on the popup for two minutes and bailing with a generic no-progress message.
- Your skill-point spend threshold and on/off choice now stick across preset and rotation switches. Every preset ships a shared 350 threshold that used to silently overwrite whatever you set in Skill Settings; the merge now preserves your value the way it already preserves per-event overrides.
- The bot no longer mistakes the persistent green Skip toggle -- which also sits on the main, skill, and race-day screens -- for an event cutscene, a mix-up that had been nudging playback to a slower speed and wasting time in Unity Cup.
- A Unity Cup Team Showdown no longer times out mid-showdown and kills the career. Since the July patch added result animations, a full showdown -- opponent select, the five-race simulation, then the results and standings -- runs right around thirty seconds even when skipping, but the bot's internal time budget for it was thirty seconds exactly. It gave up one tick before the final Next button appeared and left the career stranded on the standings screen until the bot stopped the run. The budget is now two minutes, so a showdown always plays through to the end.
- Per-career log files keep writing during long unattended queue runs. On a multi-hour session the detailed per-career log could quietly stop being saved; the bot now writes it at the end of every career, so a complete log is always there to look at afterwards.
- The consecutive-race warning is confirmed handled correctly -- the bot declines a third straight race at critically low energy to avoid the stat penalty -- and a misleading internal log line that made the dialog look unhandled no longer fires.
- A career resumed on a race-day screen is recognized as already in-career instead of being misread as a between-run transition.
- The navigation drawer's swipe-to-open gesture works again on Android; the app root was missing its gesture-handler wrapper.
- The Quick Mode Settings popup is handled again, so the bot can pick "Shorten all events" like it's supposed to.

## [1.3.6] - 2026-07-05

The July 2026 game update (the Global 2nd-anniversary rebalance) raised stat caps, added new screens, and changed the training math. This release adapts to all of it and ships a large reliability pass on top: sturdier unattended queues, a run of screen-reading fixes, stamina tuning for the long-distance trainees, and a new results history that records how every career actually ended.

### Added

- Per-scenario stat caps matching the July rebalance: URA 1400, Unity Cup 1300 (Wit 1800), Trackblazer 1200 (Stamina 1900, Wit 1500). The bot now believes legitimate stat values past the old flat 1200 limit, so a real high stat is no longer thrown away as a misread.
- A results history. Every career now saves a compact record of how it ended -- completed, force-ended, or cut short -- together with which settings it ran under, and a bundled tool turns a batch of them into a per-trainee results table: how many full careers versus early exits, and where each preset tends to fail. Preset tuning can now be judged across many runs instead of one run at a time.
- Career-end spark reroll, opt-in and off by default (under Run Queue). When enabled, the bot spends 30 TP once to redraw a career's sparks if the trainee's core stat is high enough for good odds and no 3-star target spark rolled, keeping the better result. Keep an eye on the first use.
- Star Fruit and Carats fallbacks for the TP restore step. When a queue runs out of TP and the primary restore item is gone, it now falls through to Star Fruit, then to a max Carats refill, so a long unattended queue no longer stops on the first depleted item.
- Mandatory races now retry toward 1st place (bounded by the free retry count), and training anticipates rainbows from bars close to maximum friendship, favouring a room that is about to turn rainbow.
- Extra diagnostics behind the Debug Mode setting: the bot can save what it saw and explain each turn's decision in the log, which makes misreads and questionable choices much easier to report and fix.

### Changed

- Stamina-first tuning for the long-distance trainees. Mayano Top Gun, Tosen Jordan, and Symboli Rudolf were reaching their Kikuka Sho and Tenno Sho (Spring) goal races short on stamina and force-ending; their URA and Unity Cup presets now train Stamina first. Winning Ticket is no longer recommended for Trackblazer -- her Junior-year aptitudes make the Result-Points checkpoint unreliable.
- Trackblazer no longer rests or camp-trains through a race commitment, and the skill buy pass now stops as soon as its plan is spent instead of scrolling the full list a second time.
- Mood recovery now targets the mood floor configured in settings instead of a fixed one.
- Character, support, and skill event data synced with upstream, and the "Victory!" event text updated for the patch.

### Fixed

- Your settings could be wiped back to defaults if the app was backgrounded during a slow launch; saving now waits until they have finished loading. Presets and internal queue state no longer leak into each other's saves.
- A queued rotation interrupted by a game update or restart now resyncs onto whoever is actually on screen instead of stopping, and rotation name-matching no longer confuses trainees that share a first name or an alternate outfit.
- The Notices dialog that appears at the in-game daily reset (which had killed an overnight run) is now closed with its wide list-style close button.
- When screen capture or the accessibility service dies mid-run, the bot now says so clearly instead of wedging silently -- and it repairs the accessibility service itself and keeps the device awake per run, so long queues survive this far more often.
- The bot now reads race grades in every scenario -- a leftover internal switch had turned that off outside Trackblazer, which left the alarm-clock race policies blind on URA and Unity Cup.
- A run of screen-reading fixes: misread rank letters are recovered, the View Results button, the restyled Legacy Select screen, and a chibi Start Career variant are recognized again, skill upgrade chains are priced at what the screen actually shows, and stubborn reads get a few retries instead of one attempt.
- The per-race retry limit now applies on every retry path, and manual stops and skipped runs are labelled correctly in the end-of-run summary.

---

## [1.3.5] - 2026-06-24

A Trackblazer tuning knob plus a skill-matching fix. Megaphone use can now be gated per tier so the strong megaphones are saved for high-gain turns, and inherited unique skills are no longer skipped at the end-of-career buy.

### Added

- Per-tier megaphone thresholds for Trackblazer (under Scenario Overrides). Each megaphone tier -- Empowering (+60%), Motivating (+40%), Coaching (+20%) -- now has its own minimum main-stat gain below which the bot holds it, and a blocked tier falls through to a weaker one. Off by default (all thresholds 0), so existing runs are unchanged; raise the Empowering and Motivating floors to reserve those tiers for the highest-value turns such as summer camp.

### Fixed

- Inherited unique skills are no longer skipped when buying at career end. Skill names ending in the ★ marker sometimes failed to match their database entry on a shortcut path, so the bot never bought them; the shortcut now matches names the same way as everywhere else.

---

## [1.3.4] - 2026-06-24

Two accuracy fixes plus an opt-in Trackblazer training mode. The end-of-career summary now records the real final stats, a corrupted stat reading can no longer stick, and an experimental irregular-training option is available for Trackblazer (off by default).

### Added

- An opt-in irregular-training mode for Trackblazer (off by default, under Scenario Overrides). On a free Classic or Senior turn it can train instead of running a voluntary race when the training is clearly strong, when its gain also feeds a secondary goal (a rainbow, a skill hint, an unfinished bond, or a side-stat breakpoint), or when a critical stat needs rescuing. It never touches mandatory or scheduled races. Experimental: its benefit over plain racing is not yet proven, so it stays off by default.

### Fixed

- The end-of-career summary now records the true final stats and fan count. The bot was silently failing to open the post-finale Details screen on every run, so it had been logging the pre-finale values -- roughly 40 per stat and tens of thousands of fans short of the real result.
- A garbled stat reading can no longer lock a bad value in -- the bot keeps the last good value instead -- and impossible stat values are rejected even on a fresh install.

---

## [1.3.3] - 2026-06-22

A reliability patch plus two new trainees. The bot now skips through the result and event screens it used to churn on, the post-Auto-Fill "Follow Trainer" prompt no longer stalls a queue, a short scrollable list can no longer crash the tap picker, and "Prioritize Skill Hints" now plays by the same rules as every other training.

### New

- Presets for Biwa Hayahide and Mejiro Ryan (the roster is now 29 trainees across 87 presets).

### Fixed

- The bot now advances result and event screens -- goal-complete, race results, hint and achievement popups, and day-end event dialogue -- by pressing their Skip or Next button, instead of churning through several unknown-screen recovery cycles before stumbling past.
- The "Follow Trainer" prompt that appears when Auto-Fill borrows a card from someone you have not raced is dismissed by default, so it no longer stalls a queued run.
- A short scrollable list (such as the post-purchase "choose how many to use" screen) could crash the bot; it cannot any more.
- "Prioritize Skill Hints" no longer trains a hinted stat at any failure chance or with no energy, and no longer overrides the training blacklist. A skill hint now only wins among trainings that already pass the same failure-rate, energy, and blacklist gates as every other training, and that prioritization now applies in every year.

---

## [1.3.2] - 2026-06-20

A reliability and diagnostics patch on top of 1.3.1. Overnight queues that crossed midnight could stall on the real-world date-rollover popup; the shop and long scrollable lists each had an edge case that could mislead the bot; and the end of a career now writes a single structured outcome line so a run's result is legible at a glance. Plus a new skill-buying option and a log-viewer fix.

Validated with a 3-career unattended Trackblazer queue that completed end to end.

### Added

- A single summary line written to the log at the end of every career, recording the result, scenario, the turn it ended on, fans, final stats, and skill points. The game shows the same end screen for a clean finish and an early force-end, so the turn number is the tell -- a full career ends near the scenario's last turn, a force-end ends early. Makes a run's outcome readable at a glance and easy to share in a bug report.
- An option to skip double-circle (◎) skill upgrades, spreading skill points across more single-circle (○) skills instead of spending them topping a few up to ◎. Off by default.

### Fixed

- Overnight runs that cross midnight no longer stall on the real-world date-rollover popup; its OK button is now dismissed and the run continues.
- The shop is no longer reported open until the items screen is confirmed visible, so an intercepting unlock or discount dialog can't make the bot buy against the wrong screen.
- An edge case in list scrolling that could cut off part of a long list (shop, skills, races) is handled cleanly now.
- A popup the bot can't clear now ends the run cleanly after a few retries instead of spinning until the whole queue gets killed.
- The Remote Log Viewer's Compact toggle now re-renders the lines already on screen instead of only affecting new ones.

---

## [1.3.1] - 2026-06-20

A focused stability patch. The bot was stalling on in-career story and support-card event cutscenes -- the "tap to continue" screens with a Skip pill that play before an event's choices appear. Both the between-run launcher and the in-career loop mistook these for other screens and gave up, ending the run (sometimes seconds after Start). They are now recognized and tapped through to the choices, so events that used to stop an unattended queue just resolve and the career continues.

Validated with a 6-career unattended overnight queue that completed end to end.

### Fixed

- In-career story and support-card event intro cutscenes are now tapped through to their choices instead of being misread as an unknown screen and stalling the run until the bot gave up.
- The between-run launcher no longer mistakes an in-career "tap to continue" cutscene for the Quick Mode prompt and loops on it until it times out; these screens are simply tapped through, while the real Quick Mode prompt is still handled as before.

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
- Queue 2–20 consecutive career runs unattended, mixing scenarios freely — the between-run navigator pages the Scenario Select carousel to each run's scenario.
- The bot navigates itself between runs: career summary → home → scenario select → deck setup → confirmation → cinematic → training menu.
- Queue progress is saved continuously for crash recovery and resumption.
- Queue progress UI on the Home page with skip-run button.
- Configurable per-run max runtime with a safety timeout (default 180 min).
- Configurable `stopOnError` (default: continue past errors), reuse-last-launch-setup, auto-fill support deck.

### Character presets (entirely new)
- 210 built-in character presets (70 characters and outfits × 3 scenarios: Trackblazer, Unity Cup, URA Finale).
- Searchable trainee picker on the Home page — character-grouped with per-scenario advisory chips, validation badges, and starred favorites — that deep-merges preset settings into the active profile and sets the scenario together with the preset.

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
