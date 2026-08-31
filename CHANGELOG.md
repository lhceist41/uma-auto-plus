# Changelog

All notable changes to **UMA Auto+** are documented in this file.

This project is a fork of [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation). The fork baseline is upstream **v5.4.8**. For a summary of what this fork adds on top of that baseline, see [README.md](README.md).

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### Added

- **Record Decision Data**, a new setting on the Settings page, on by default. The bot keeps a small per-turn record of the decisions it made so a career can be reviewed and improved later. It is stored on your device only and nothing is uploaded, it is independent of Debug Mode (turning it on does not turn on the heavy debug diagnostics), and you can switch it off to keep storage use down.

### Changed

- The preset documentation is split in two: `PRESETS_GUIDE.md` now covers what a preset does, how to pick one, and what to check when a run does not go the way you expected, while building or editing a preset moved to `docs/PRESET_AUTHORING.md`.

## [1.4.0] - 2026-08-28

### Added

- **Grand Concert plays itself, start to finish.** The new career scenario ("Brighter Together Our Grand Concert") is automated end to end: training, races, training events and skill buying work the way they do in the other scenarios, with the scenario's stat caps (Speed 1600, Guts 1500, the rest 1300, plus whatever your inheritance sparks add on top), and all five concerts including the Grand finale run without you. Every tap is still gated on recognising the exact screen it belongs to: if the scenario shows something the bot does not know, it stops safely with the career intact and pressing Start resumes the same career.

- **The Lesson shop is automated, and it buys for the result you want.** The bot opens Lessons on its own and spends performance points on the offered songs and techniques. It secures the three new songs each concert cycle needs for its best result, chases the fuller songbook that unlocks the special version of the finale when the points provably allow it, and buys energy techniques when energy is low so a turn is not lost to resting. While a cycle is still short of its songs, training also leans toward the facilities that earn the point types the next song is missing, without overriding a clearly better training. Points left over at the end of a career are spent through Lessons before the skill screen opens, instead of expiring.

- **Grand Concert works with run queues, trainee rotation and automatic TP restore.** The queue pages the scenario carousel to Grand Concert itself, launches each career, and finishes a career it resumes on before starting the next. Every trainee in the roster has a Grand Concert preset derived from her own proven URA build, which takes the roster to 292 presets.

- **Adaptive skill spending (opt-in).** A new Skill Spend Mode on the Skill Settings page: Manual (the default, exactly the previous behavior, your configured threshold keeps governing) or Adaptive, which picks the mid-career threshold from an account-strength tier instead: New 300, Developing 350, Established 600, Endgame 1000, with Auto currently using Developing. Presets can also declare what a career is for, which arms extra spending: buying before a mandatory or planned race that is one or two turns away, buying a planned skill as soon as it is affordable, buying a recovery skill for a safety-focused build that has none, and holding a farming career to its planned skills mid-career while still spending everything at career end, where the game would otherwise discard it. In Adaptive mode the bot also checks the remaining skill points before finishing a career: a leftover is accepted only when nothing useful, affordable and compatible is left, and otherwise it retries the purchase pass once and then stops safely with the career open so you can spend the points yourself. Every preset that declares no objective, and Manual mode everywhere, is unchanged.

- **The spark auto-reroll now chooses between the original and the rerolled set.** After the 30 TP spend the game shows both sets and asks which to keep, and the bot used to keep whichever page the game showed first. It now reads both sets in full, scores them against the trainee's build, keeps any 3-star worth protecting, and pages over to the winner. A tie or an uncertain read keeps the original, and a final confirmation that does not name the chosen set cancels and stops safely rather than confirming. The run history records both sets, the kept set, and why the choice fell that way.

- **Two new trainees.** **Copano Rickey**, the game's third pure dirt trainee, with a URA profile built to farm Kashiwa Kinen Winner's Sashes: a curated dirt agenda that stops racing after the Classic year so she arrives at the sash race rested, built as a Pace Chaser to match her unique and her innate kit. Her Unity Cup and Trackblazer presets ship with the same Turf F caution advisory Haru Urara and Smart Falcon carry. **Grass Wonder (Saintly Jade Cleric)** joins as a second Grass Wonder outfit with her own presets: same aptitudes and goal chain as the base card, but training that leans Stamina and Wit and skill plans that buy her own recovery line. With two owned outfits of one character the trainee picker shows two Grass Wonder rows, and the bot selects by the exact outfit banner in-game, so neither set of presets can launch the other card.

- **Build-Aware Launch (advanced, opt-in, off by default).** A Run Queue option that verifies the live Borrow list, deck and launch screen against what it intended to run before it spends TP, and refuses to start rather than guess when it cannot confirm that state. It ships alongside an optional Windows companion app, also off by default, that can perform one bounded Borrow-list swipe as a last-resort recovery step; the phone always verifies whether the list actually moved, and the companion can never select a card, advance a launch or start a career. The default hands-off launch behavior is unchanged unless you turn this on.

### Fixed

- **Smart Borrow handles long Borrow lists properly.** A swallowed page swipe is no longer mistaken for the bottom of the list and is retried with a stronger drag, both passes over the list now cover the same ground including rows the game greys out, the chosen card is re-checked at its current position immediately before the tap, and a card that has genuinely moved out of reach by the time the list reopens is replaced with the best card currently on offer instead of stopping the launch. Smart Borrow also no longer borrows a support card of the very trainee you are launching, which the game refuses and which used to cost the whole queued run.

- **Career launch is faster and safer.** The roster's page swipe is now verified by comparing the screen before and after, so a swallowed swipe is retried instead of rescanning rows the bot already read: a trainee switch that could take up to 90 seconds is much quicker, and a scan now remembers where every trainee it saw is sitting. Before the irreversible Start Career tap the bot confirms Normal Career mode is selected rather than trusting whichever tab the game had open last. And Start now waits until your selected preset is actually saved to storage before it launches, so the trainee shown on Home is the trainee that starts.

- **Grand Concert careers no longer stop or lose points at the Complete Career screen.** The end-of-career Lessons drain now recognises the greyed-out cards the game shows there, so leftover performance points are spent instead of expiring; the Complete Career screen is recognised the moment it appears rather than losing a timing race; and career-end skill buying re-plans onto skills it can actually buy when the game refuses a listed one, instead of retrying it until the queue stalls. Points that genuinely cannot buy anything are handed back at Finish with the reason recorded.

- **Your training-event picks are applied.** The race-result events (Victory!, Solid Showing, Defeat) let you choose which option to take, and that pick never actually worked: the bot matched a one-option copy of the event and fell back to Option 1 at every race. It now counts the option rows actually on screen before deciding which event it is looking at, so a two-option race result uses the real two-option data and your pick, and the log shows the rewards for the grade of race just run. Per-trainee event picks from a preset now also win over the shared default for the same event, instead of being ignored. And the Acupuncture event no longer bounces between its own two screens: the follow-up screen is recognised and declined once, which ends the event cleanly.

- **Mood Floor is a visible setting, and a strict floor no longer sticks to later trainees.** Mood Floor is the mood the bot keeps a trainee at or above, spending a turn on recreation when she drops below it. A couple of trainees need a strict floor; everyone else runs on the normal Good floor, but the strict value used to be applied by those presets and then never cleared, so every later trainee kept burning turns holding a mood she did not need. Picking any preset now sets the floor that trainee actually calls for, and the floor appears on the Training page as a Normal / Good / Great picker (Good is the default) so you can see and override it.

- **Every career's spark record is now complete.** The sparks screen only shows its first rows, so a career with more sparks than fit recorded a truncated list; the list is now scrolled to its end before recording, whether or not the auto-reroll is enabled. Careers where the reroll spent no longer lose their spark records, and a career that did not reroll no longer stops on the ordinary "Keep this set of Sparks?" dialog waiting for a manual Confirm.

- **The daily reset no longer strands the bot on another app.** When the game restarts at the daily reset or crashes mid-run, the relaunch used to be able to close the game without reopening it, leaving the bot tapping on whatever was behind it, and a queue would then start its next run on that dead screen. The relaunch is gentle now, it retries if the game does not come back, and a game that genuinely cannot be recovered pauses the queue with a clear message. Your career is saved every turn, so nothing is lost.

- **Bundled game data is repaired and refreshed.** The entire new dirt race schedule was missing (Kashiwa Kinen, Teio Sho, M.C. Nambu Hai, Tokyo Daishoten and the rest, 26 races, along with the Kawasaki, Funabashi and Morioka racecourses); skills that a scenario or an event grants rather than sells are now included, so reading a trainee's own skill list is complete; skill tier data is restored after the community source restructured its page; race-result training events now show which finishing position each reward line applies to; and the rest of the bundled data is brought up to date with the current Global release. Separately, settings search now reaches every settings page it points at, including Run Queue, Discord and Scenario Overrides.

---

## [1.3.8] - 2026-07-13

### Added

- **Career records are much richer.** Every career now gets an estimated overall rank, the same kind of grade the game shows (B+, A, S...), computed from the trainee's actual stats, skills, aptitudes and unique skill level and recorded next to the career's result. It mirrors a well-known community calculator, so treat it as an estimate rather than the game's exact number. The run history also records whether a URA Finale career actually won its finale rather than only reaching it, and the full set of sparks each career kept. (The rank estimate comes from the upstream project.)
- **18 more trainees, closing the Global roster gaps**: Admire Vega, Agnes Digital, Air Shakur, Bamboo Memory, Curren Chan, Eishin Flash, Fine Motion, Fuji Kiseki, Hishi Akebono, Inari One, Ines Fujin, Mejiro Ardan, Mejiro Bright, Narita Brian, Rice Shower, Sakura Chiyono O, Satono Diamond and Yaeno Muteki, taking the roster to 210 presets across 70 entries and covering every trainee released on Global. Inari One joins the dirt roster as an End Closer, Ines Fujin is Front Runner-locked, the thin-Junior sprinters carry Trackblazer avoid advisories, and the goal-sparse or fan-gated bodies ship curated racing plans.
- **Legacy Farm presets** for Daiwa Scarlet, El Condor Pasa and Air Groove (URA Finale only): parent-farming editions of their regular builds that race a G1-dense agenda, for breeding careers where G1 wins and high core stats decide the spark quality. The agendas are spaced to stay inside the consecutive-race limit, and the picker marks them as farming builds rather than badge chasers. If you add one to a run-queue rotation, pick it from the rotation editor as usual.
- **Smart Borrow.** When the queue fills the empty friend slot it now scrolls the Borrow Card list and borrows the best card it finds from a curated list of strong picks (Kitasan Black first), instead of settling for whatever sits in the top row. Follow trainers with strong cards to give it good options. Cards the game marks as duplicates are skipped, and one that lands in the slot anyway is swapped for the next-best pick instead of walking into a disabled Start Career. On by default; turn it off under Run Queue Settings to keep the old top-row behavior.
- **Support-card dating schedule** (from the upstream project, off by default). With a Group support card equipped (Team Sirius, Heirs to the Throne), the bot takes its recreation outings on the career turns that advance the card's outing chain and holds the final outing so the training buff lands where you want it. It reads the card's progress counter off the screen, so it stays on track after restarts and manual dates, catches up missed outings on the next free turn, and drops the schedule if the window has passed. Career-goal races always come first, and ordinary mood recovery goes on dates with the trainee while the schedule is active so a scheduled outing is never wasted.

### Changed

- Switching trainees between queued careers is much faster. The bot remembers where each trainee sits in the roster grid and jumps straight to her cell, verifying the name before advancing exactly as before; only a changed roster falls back to the old cell-by-cell scan. A measured switch went from about 90 seconds to about 30.
- The final career of a queue now finishes properly instead of stopping on the Complete Career screen. The sparks screen and its auto-reroll, the results dialogs and the veteran registration all happen after that screen, so the last career of every queue used to skip them silently.
- The parent-farming presets now watch all five stats for the late-career spark rescue, not just the build's core two, because the blue spark's stat is drawn at random from all five and a stat that finishes too low can never roll a 3-star. Re-apply the preset, or re-pick it in the rotation editor, for this to take effect on an existing setup.

### Fixed

- **Races your account has never run before no longer stall the bot.** The game only offers the instant "View Results" skip for a race you have already run once; a brand-new race has to be watched live, and the bot used to give up partway through and get stuck. It now watches a first-time race through to its results screen, recognises the race lineup screen when resuming a career interrupted mid-race, and closes the "TROPHY WON!" popup straight away instead of staring at it. This bit the parent-farming builds hardest, since their dense calendars enter races the account has never seen.
- **Sprint races are visible to the race picker again.** The distance setting stores "Short" while the race data used "Sprint", so the race filter and the racing-plan browser silently dropped every Sprint race from their pools. Both sides now normalize the token; stored settings and presets keep their existing "Short" value, so nothing needs migrating.
- **The spark auto-reroll finally works.** The bot did not recognise the "Reroll Sparks" button, so it clicked past it on every career since the feature shipped. The button is detected now, the reroll's spend button is cut from the live game so the confirmation can actually be clicked, the decision is priced from the real spark odds instead of a threshold no career ever reached, and a short TP balance is restored with the same items used at career start rather than silently keeping the first set. The first actual spend is still worth watching.
- **Overnight queues survive a lot more.** A brief connection outage no longer ends a run: after the quick retries the bot holds for three minutes and tries once more, and the career is server-saved the whole time. The TP recovery popup can no longer wedge the between-run navigator, and a restore only counts once the popup has actually closed. A native memory leak in the stat-gain reader that made training analysis slower the longer a session ran is plugged. And if the bot is ever stuck on a screen it cannot recognise or advance, it now restarts the game and resumes the career in progress instead of ending the run.
- **A career now always runs on the right trainee's settings.** A rotation knocked out of sync used to be repaired while the wrong trainee's stat priorities, racing plan, event picks and skill plan stayed in force for the rest of the career; everything is reloaded from the correct preset now, and every rotation career states at career start whether it is running the settings its slot intended. Starting a single career with the queue off also verifies the Trainee Select screen against the preset you applied instead of accepting whoever the game preselected, and stops with a clear message rather than ever launching the wrong trainee. Separately, the game's daily reset dumping you back to the main lobby mid-career is recognised: the bot re-enters the career in progress instead of flailing and skipping ahead in the queue.
- **Training reads are sturdier.** Failure chances that read as impossible values are rejected and re-read instead of being "corrected" into a plausible wrong number, the five facilities sanity-check each other against the trainee's energy, aptitude letters on the stat screen are compared in full instead of taken on the first close match, and rainbow trainings are detected from the actual glow around the support portraits instead of inferred from friendship bars. Stale analysis from a turn that ended in a race or a rest no longer carries into the next turn. Stats pushed past the scenario cap by inherited blue sparks are trained into and recorded correctly. (Several of these come from the upstream project.)

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
- Career-end spark reroll, opt-in and off by default (under Run Queue). When enabled, the bot spends 30 TP once to redraw a career's sparks if the trainee's core stat is high enough for good odds and no 3-star target spark rolled. (As shipped here the redraw was kept without comparing the two sets; choosing the better set arrived with the spark selection support in a later update.) Keep an eye on the first use.
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

## 1.2.5 - 2026-04-22

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

## 1.2.0 - 2026-04-17

A reliability and content release. The bot survives long queues on MuMu, handles the Trackblazer Racing Carnival end to end, is no longer blinded by the game's UI colour refresh, and is measurably faster on every loop tick. Every built-in character preset was rebuilt, and fresh installs now ship with skill buying enabled and a strong starting build for every character.

### Added

- **Racing Carnival support.** The bot navigates the new Legacy Select screen with its green Auto-Select button and handles the redesigned Confirm Auto-Select dialog, ticking both the new "Prioritize Carnival Bonus Sparks" checkbox and the existing "Include Guests" checkbox before confirming. It works whether the carnival event is running or not.
- **Auto-resume after a crash.** If the bot is killed mid-queue by a force-stop, an out-of-memory kill or the system, the next time you start it the queue picks up where it left off instead of starting over.
- **Skill buying is on by default.** Fresh installs buy skills with the Optimize Skills strategy and buy inherited unique skills, with a meta-aligned priority list per character. The mid-career buy triggers at 1200 skill points, which lands late in the Senior year.
- **Smarter energy and item handling in Trackblazer.** The bot reserves one of its lowest-tier energy items so it can rescue itself when consecutive races push energy critically low, instead of being forced to Rest with the items unspent; it holds a cupcake to offset Royal Kale Juice's mood penalty if that gets bought; and it ignores the consecutive-race safety limit in Late December, where mandatory races force long chains anyway. (From the upstream project.)

### Changed

- **Every character preset rebuilt** -- all 17 characters across Trackblazer, Unity Cup and URA Finale, 51 presets in total. Each got distances, styles and surfaces matched to its real aptitudes, per-character stat targets, a full skill priority list, character-specific event picks so story events no longer fall back to defaults, and Trackblazer shop blacklists so coins are not spent on stat scrolls the character does not need. The notable corrections: Grass Wonder moves from Medium to Long (Medium was a B-rank distance for her), King Halo from Mile to Sprint (her only A-rank distance), Vodka from Mile to Medium to match her Medium-heavy career schedule, Gold City (Autumn Cosmos) and Air Groove from Pace Chaser to Late Surger to match what their uniques actually trigger on, and Agnes Tachyon's plan now carries her own unique instead of another trainee's.
- **The bot is noticeably faster.** It shares one screen capture across checks that used to take four, looks for the common between-run screens before the rare ones, stops scanning the bottom half of the screen for popup titles and the top half for buttons that only ever appear at the bottom, and no longer over-waits before reading the shop's coin counter. Most visible between runs, where the post-run dialogs now fly by.
- **The game's UI colour refresh is handled.** Several screen banners changed from yellow-green to teal/mint, including the Skill Points header, the Start Career button and the Auto-Select button. The bot recognises the new colours, and accepts both variants of the Skill Points label so a future recolour does not break it again.

### Fixed

- **Long queues no longer die on MuMu.** The app now tells Android it is doing important work and is given more memory, it restarts itself within seconds if it genuinely freezes, and a small per-screen-check memory leak that added up over multi-hour sessions is plugged. Combined with auto-resume, a 10-run queue left going overnight should be found near its end rather than stopped on run 2.
- **The race-prep screen no longer hangs forever.** The bot used to loop on the pre-race screen when it could not spot the View Results button; it recognises the button's current look and falls back to the Race button. A race that failed to complete is also no longer counted as a finished race, which used to cascade into a wrong consecutive-race count and eventually kill the queue.
- **Skill purchases no longer abort silently.** Pre-finals and career-complete buying both refused to run because the skill purchase screen was misread, leaving 1000+ points unspent each time. The screen check and the skill-point reading are more tolerant now, and the bot backs out of the skill screen instead of looping on Confirm when it lingers.
- **A pile of smaller stalls and misfires.** The freeze recovery no longer trips on legitimately slow loading screens or popup chains; between-run navigation no longer spends minutes flip-flopping between two readings before noticing it is stuck; a seasonal "Event Underway!" banner can no longer trick the CAREER tap into the wrong place; the stray second tap after Start Career, which could land on the cinematic, is gone; a cupcake used at Normal mood no longer throws its mood gain away; a maiden race is retried the same day after a transient failure instead of being marked done; and the app no longer crashes within seconds of being reopened after a crash.
- **Distance preference no longer defaults wrong on aptitude ties.** Characters with two A-rank distances were trained for the first one even when their preset asked for the other. All 51 presets now lock in the distance their build intends.

---

## 1.1.0 - 2026-04-14

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

## 1.0.2 - 2026-04-14

### Changed
- **Release builds hardened for public distribution.** Releases are now always properly signed (a build that can't be signed fails instead of shipping wrong), and only release-appropriate network rules ship in the release APK.

### Fixed
- It is no longer possible to accidentally publish an unsigned or debug-signed release build.

---

## 1.0.1 - 2026-04-13

### Fixed
- **Crash on first launch.** The icon library the app used could crash it the very first time it opened; every icon was swapped to a different library that doesn't.
- Corrected several default values in Run Queue Settings and related pages.

---

## 1.0.0 - 2026-04-13

### Added
- **Rebrand to UMA Auto+** - new app name, package identity (`com.lhceist41.uma_auto_plus` so it can live side-by-side with the upstream app), app icon, and splash.
- Version numbering reset to `1.0.0` for the first proper branded release.
- In-app update checker that polls the GitHub releases feed and surfaces new versions inside the app.

### Changed
- Queue defaults: `totalRuns` 2 → 5, `autoFillSupports` false → true, to reflect the common multi-run workflow.
- Built-in character presets: 51 presets (17 characters × 3 scenarios), picked from a filter-by-scenario menu on the Home page.

---

## 5.5.1 - 2026-04-13

### Added
- **Crash resilience for the multi-run queue.** Queue progress is saved continuously, so if the app crashes mid-queue you're told about it on the next launch and can pick up where it left off. Logging load during long runs was also reduced.

### Fixed
- Long automation runs no longer overload the app's UI, which in testing was the root cause of the mid-run crashes.

---

## 5.5.0 - 2026-04-13

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

[1.4.0]: https://github.com/lhceist41/uma-auto-plus/compare/v1.3.8...v1.4.0
[1.3.8]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.8
[1.3.7]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.7
[1.3.6]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.6
[1.3.5]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.5
[1.3.4]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.4
[1.3.3]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.3
[1.3.2]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.2
[1.3.1]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.1
[1.3.0]: https://github.com/lhceist41/uma-auto-plus/releases/tag/v1.3.0
[Unreleased]: https://github.com/lhceist41/uma-auto-plus/compare/v1.4.0...main
