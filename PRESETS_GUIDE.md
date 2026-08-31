# Built-in Character Presets

UMA Auto+ ships a tuned settings profile for every trainee it supports, in every scenario. Pick a
trainee, hit Start, walk away. This guide explains what a preset changes, how to choose one, and
what to check when a run does not go the way you expected.

Building or editing a preset is a separate job: see [Preset authoring](docs/PRESET_AUTHORING.md).

## What a preset is

A preset is a complete, hand-tuned configuration for one trainee in one scenario: stat priorities
and targets, racing preferences and any curated race plan, skill plans for each buying checkpoint,
training-event picks, and the scenario-specific tuning that scenario needs.

Presets are per scenario, not per trainee. The same character trains differently in URA Finale than
she does in Trackblazer, so each scenario gets its own entry. Applying one sets the scenario at the
same time, so you never have to switch scenario separately.

## Applying a preset

Tap the trainee card on the Home page (or pick a scenario first from the center-button dropdown --
both orders work). The trainee picker opens. Expand a trainee, then tap **Apply** on the scenario
card you want.

A preset overwrites the settings it ships and leaves the rest of your configuration alone. In
particular, these survive a preset switch:

- **Debug Mode and your Discord webhook.** Presets never touch either.
- **Your skill-spend timing.** The threshold and on/off switch in Skill Settings are yours, not the
  preset's.
- **Your support-card and scenario event picks.** These depend on your deck, not on the trainee, so
  a preset switch keeps them.
- **Your skill-spend mode and account tier.** Global choices, never preset-owned.

Everything else in a shipped category is replaced, not merged: if a preset ships a racing plan or a
stat target, its value wins.

Settings are saved immediately, so the bot reads the applied values without any extra step. A green
`Preset "<name>" applied` snackbar confirms it. The selection survives an app restart and a scenario
switch, so it does not quietly reset on you.

Two per-trainee values are re-stamped on every apply: the skill-spend objective and the mood floor.
That is deliberate, so a setting a previous trainee needed can never leak onto the next one.

## Choosing a preset

The picker gives you four signals per row.

**Search.** Type any part of a trainee or outfit name.

**Favorites.** Star a row and it pins to the top. Favorites are per outfit, so starring one card
does not star the character's other cards.

**Scenario chips.** Each row shows a chip per scenario the trainee has a preset for, colored by fit:

- Green: a good pick. The trainee's aptitudes suit the scenario and her preset has thorough
  event coverage.
- Yellow: a mismatch, with the reason spelled out. The preset exists and will run, but the
  aptitudes lock her out of races the scenario expects.
- Neutral: supported, no strong opinion either way.

The same advice appears as a banner on the Home page once a trainee is selected, and inline in the
rotation editor. Starting a known mismatch asks for confirmation first, so a doomed pairing never
launches by accident.

**Validated / Research badge.**

- **Validated**: at least one career on that preset has completed its full arc, start to finish.
- **Research**: the build comes from verified game data (aptitude grid, growth rates, goal chain,
  race calendar, event options), matched to the closest proven build for that archetype, but no
  full career has completed on it yet.

Research is not a warning. It means untested end to end, not badly built.

## Which scenario

Any trainee with a green chip for a scenario is a good pick for it. Beyond that:

- **URA Finale** is the most forgiving and the best-covered. Trainees with curated race plans, dirt
  specialists, and anything with an awkward goal chain generally run here.
- **Unity Cup** and **Trackblazer** run turf-dominant schedules. Trainees with poor turf aptitude
  carry a mismatch advisory for both.
- **Trackblazer** additionally scores on result points rather than a long goal chain, so it suits
  different trainees than URA does. Thin fan-gated Junior race pools are the common reason for a
  Trackblazer mismatch.
- **Grand Concert** presets exist for every trainee. They are derived from that trainee's URA Finale
  build (see below), so they inherit her training identity.

## Farm variants

Most rows are one trainee's competitive build. A few are parent-farming variants, built to produce
better inheritance material rather than a better career score. They are deliberately not the right
pick for badge or rating chasing.

| Variant | Scenario | What it changes |
|---|---|---|
| Legacy Farm (Daiwa Scarlet, El Condor Pasa, Air Groove) | URA Finale only | Swaps in a G1-dense mandatory race schedule, spaced so the run never trips the consecutive-race limit. More racing, fewer training turns, more inheritance value per career. |
| Blue Farm (Super Creek) | Unity Cup only | Broadens the blue-spark focus from the competitive build's narrow stat pair to all five stats, and switches skill spending to a farming objective. |

Both farm families train with all five stats in their spark focus and switch skill spending to a
farming objective, so the career-end blue spark never lands on a stat that was left out to rot.

**Why Blue Farm broadens the stat list.** The career-end blue spark picks its stat at random among
all five, and a stat that finishes below 600 can never roll a 3-star. The training scorer's
spark-rescue boost only fires on the stats you list, so the list is the whole lever: narrowing it to
a trainee's "good" stats silently reintroduces dead rolls. Blue Farm trades a little stat peak for
blue coverage across the board.

Farm variants are scenario-limited on purpose. Legacy Farm's curated schedule is tuned to the URA
goal chain and would not survive being pointed at another scenario; Blue Farm is a Unity Cup build.

## Grand Concert presets are derived

Grand Concert differs from URA Finale in exactly one way a preset cares about: its stat caps. So
nearly every Grand Concert preset is generated from that trainee's URA Finale build rather than
written out a second time (Taiki Shuttle's is an early hand-written exception). For a generated
entry, fixing the URA build fixes its Grand Concert twin automatically, instead of leaving a
hand-copied clone quietly stale.

Three things change in the derivation:

- The scenario is set, so applying the preset switches to Grand Concert with it.
- The Speed target rises for Speed-primary Sprint and Mile builds, to the scenario's higher Speed
  cap. The handful of tempered Sprint/Mile builds, whose URA Speed target already sits below the
  normal baseline, get a smaller raise instead, matching Medium builds. Stayers keep their URA
  weighting and take **no** raised Speed target. A stat target is a weight, not a ceiling: training
  scores a stat by how far behind its target it sits, so raising Speed on a stayer would pull
  training away from the Stamina her longest goal races need.
- Any curated race plan and any declared skill-spend objective are dropped, matching every other
  non-URA preset. A curated plan is tuned to the URA goal chain, and in mandatory-plan mode
  voluntary races happen only on planned turns, so a plan that does not fit the scenario cannot
  recover from a fan shortfall.

The Legacy Farm variants stay URA-only and get no Grand Concert twin.

## Why some presets look unusual

A preset that reads oddly against a trainee's aptitude grid is usually right for a reason.

**A build that ignores the best aptitude letter.** Training follows the goal chain, not the grid.
Silence Suzuka is built Medium-primary despite her Mile A, because her goal chain is almost entirely
Medium. She is also locked to Front Runner, because her unique needs a clear lead to fire at all.

**A build that ignores the obvious running style.** Copano Rickey is a Pace Chaser: her unique and
her whole innate kit read the back half of the field, so a front build never triggers them.

**Stamina before Speed.** Trainees whose goal races are Long (Kikuka Sho, Tenno Sho Spring, and
friends) train Stamina first. Training priority order, not the stat targets, is what governs early
training, and a Long goal race reached short on stamina force-ends the career.

**A curated race schedule instead of smart racing.** Most presets let the smart-racing scheduler
fill fan gaps dynamically. A trainee gets a hand-built schedule instead when her viable race pool is
too sparse for that to work: dirt specialists, trainees with a near-empty Junior year, and trainees
facing a hard fan checkpoint. When a preset runs a mandatory plan, voluntary races happen only on
its planned turns.

**Deliberately buying a negative skill.** A trainee who starts with a built-in stat debuff gets
negative-skill buying enabled, so the buy pass can clear it.

**Skills left out of a plan.** Skills sitting behind Potential levels the build cannot reach are
omitted on purpose. Listing a skill a trainee cannot learn only pads the plan with entries that
never fire. Raise her Potential and those become worth adding.

## When a preset does not behave as expected

**The trainee I picked is not the one that ran.** Start waits until your selection is confirmed
saved before it launches, and blocks the launch with a message if it cannot confirm. If you see that
message, press Start again.

**A setting I changed got overwritten.** Applying a preset replaces the settings that preset ships.
Change it after applying, not before. The exceptions listed under "Applying a preset" are the only
values guaranteed to survive.

**Its race plan did not run.** Check that the racing plan and mandatory-plan switches are still on
in Racing Settings. The bot warns at career start when the live settings have drifted from what the
preset applied.

**Its event picks look wrong.** Support-card and scenario event picks are yours, not the preset's,
because they follow your deck. A preset only ships the character's own event picks. A few trainees
ship those for one scenario and fall back to the automatic option reader for the others.

**It force-ended at a goal race.** Almost always stamina against a Long goal, or a fan or
result-point checkpoint the schedule could not reach in time. Check the trainee's advisory chip for
that scenario first: a yellow chip is the bot telling you in advance that the pairing is a bad fit.

**A preset I expected is not in the picker.** The picker lists one row per trainee and outfit.
Favorites pin to the top and the search box filters everything else; clear the search if a row seems
missing.
