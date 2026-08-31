# Built-in Character Presets Guide

This document explains how built-in character presets work in UMA Auto+, how they are structured, and how to create new presets for additional characters.

---

## Overview

UMA Auto+ ships with **292 built-in character presets** (72 character entries x 4 scenarios, plus three URA-only Legacy Farm variants and one Unity Cup Blue Farm variant). Every character entry has a preset for every scenario, Grand Concert included. Trainees are picked in the searchable trainee picker on the Home page -- one row per character/outfit with per-scenario advisory chips and a validation badge -- and applying a preset sets its scenario together with its settings.

The 2026-06-11 batch added seven entries from the June banner research pass: Sweep Tosho, Mihono Bourbon, Mejiro Palmer, El Condor Pasa (Kukulkan Warrior), Tosen Jordan, Super Creek, and Matikanetannhauser. Their URA presets ship curated racing plans (see "Curated racing plans in presets" below); Tosen Jordan's presets enable negative-skill buying so the buy pass clears her 3D Nail Art speed debuff. Symboli Rudolf (Emperor's Path) was added 2026-06-18 — a Late Surger Medium built around her late-overtake unique; URA and Trackblazer are her recommended scenarios. Biwa Hayahide (Pace Chaser, built as a Long stayer) and Mejiro Ryan (Late Surger Medium) were added 2026-06-21 to make two owned-but-unpresetted Medium/Long-A turf bodies farmable hands-off; both fill the Medium/Long Team-Trials slots and are URA + Trackblazer recommended.

The 2026-07-05 tuning pass put Stamina first in the URA Finale and Unity Cup presets for Mayano Top Gun, Tosen Jordan, and Symboli Rudolf: all three were reaching their Long goal races (Kikuka Sho, Tenno Sho Spring) short on stamina and force-ending, and the training-priority order — not the stat targets — is what governs early training. Their Trackblazer presets are unchanged (its goals are result-point-based, not a Long chain). Winning Ticket (Get to Winning!) kept her Trackblazer preset but picked up an avoid advisory for it: her Sprint=G/Mile=F aptitudes make the Junior Result-Points checkpoint unreliable, so the "Yes" below means the preset ships, not that it is recommended — the Home advisory flags it.

The 2026-07-06 batch added the six top-tier cards the community asked for: Kitasan Black, Nishino Flower, Seiun Sky, Maruzensky (Hot☆Summer Night), Oguri Cap, and Oguri Cap (Ashen Miracle). All six are research-graded — built from verified per-card aptitude/objective data (career objective chains are shared across a character's outfits, so the two Oguri cards share one racing plan) and cloned from the nearest validated archetype, but none has completed a live career on the maintainer's account yet; the picker shows them with the Research badge. Kitasan and Seiun train Stamina first (Long goal chains), Nishino is a Sprint/Mile speed build with a participation-only Oaks gate, and both recovery-gated uniques (summer Maruzensky, Ashen Miracle Oguri) ship skill plans that front-load the recovery skills their uniques need.

The second 2026-07-06 batch completed the community S-tier list with 17 more cards: ten new characters (Silence Suzuka, Manhattan Cafe, Narita Taishin, Tamamo Cross, Mejiro Dober, Special Week, Smart Falcon, Meisho Doto, Tokai Teio, T.M. Opera O in her New Year outfit) plus alternate outfits for Special Week, Tokai Teio, Seiun Sky, Mayano Top Gun, Gold Ship, King Halo, and Taiki Shuttle. All research-graded, same pipeline as the first batch: cloned from the nearest validated archetype, per-card grids and growths verified, racing plans checked against the race database and each card's goal turns. Notables: Smart Falcon is the second dirt specialist after Haru Urara -- his URA preset ships a curated dirt agenda whose three Junior entries exist to satisfy the turn-25 two-win goal (mandatory plans race planned turns only, so a plan that starts too late force-ends the career). Narita Taishin runs End Closer (joining Gold Ship, Hishi Amazon, and Sweep Tosho), with his archetype's Late-Surger skills swapped to End counterparts. Silence Suzuka is Front-forced (her unique needs a clear lead) and Medium-primary despite the Mile-A grid, because her goal chain is almost entirely Medium. Trackblazer avoid advisories ship for the narrow-Junior-pool bodies (Manhattan Cafe, Tamamo Cross, Meisho Doto, both Teio cards) and for Smart Falcon's Turf E.

The 2026-07-10 batch closed the remaining global-roster gaps with 18 characters: Admire Vega, Agnes Digital, Air Shakur, Bamboo Memory, Curren Chan, Eishin Flash, Fine Motion, Fuji Kiseki, Hishi Akebono, Inari One, Ines Fujin, Mejiro Ardan, Mejiro Bright, Narita Brian, Rice Shower, Sakura Chiyono O, Satono Diamond, and Yaeno Muteki. Same research-graded pipeline: verified grids, growths, and goal chains; racing plans validated entry-by-entry against the race database; event pins verified against the character event data. Notables: Inari One joins the dirt roster as an End Closer whose skill plan carries a surface-agnostic recovery for her dirt-to-turf Arima pivot; Ines Fujin is Front Runner-locked like Silence Suzuka; the sprinters with thin fan-gated Junior pools (Curren Chan, Hishi Akebono) carry Trackblazer avoid advisories; goal-sparse or gate-heavy bodies (Agnes Digital's unscheduled turn-47-60 G1 window, Rice Shower's near-empty Junior, Mejiro Ardan's 6,000-fan wall at turn 30, Hishi Akebono's early fan checkpoints) all ship curated racing plans. Bamboo Memory is in the bundled character data, and her URA Finale preset ships explicit character event overrides; her Unity Cup and Trackblazer presets leave the override map empty, so those two fall back to the OCR stat-option heuristic for story events.

The 2026-07-11 additions are the first **Legacy Farm** variants: Daiwa Scarlet, El Condor Pasa, and Air Groove parent-farming editions (URA only). Each clones the character's regular URA preset and swaps in a generated G1-dense mandatory racing plan (10, 15, and 7 G1 entries per career respectively) spaced so the run never exceeds the consecutive-race limit the plan itself must guard (mandatory-plan mode overrides the in-game warning). Built for breeding parents: more racing and fewer training turns, so they are deliberately not recommended for badge or rating chasing. Generated with `scripts/generate-racing-plan.mjs`, which any future parent candidate can be run through.

The 2026-07-17 addition is **Copano Rickey**, the third pure dirt body after Haru Urara and Smart Falcon, and the first built as a Pace Chaser — her unique (`Luck Runs My Way`) and her whole innate kit read the back half of the field, so a Front build never triggers them. Her URA preset is the Kashiwa sash profile: Kashiwa Kinen is a Senior-May G1 over Dirt Mile 1600m at Funabashi, and the objective accepts 3rd while a Winner's Sash needs 1st, so its curated dirt agenda covers only the empty Junior/Classic half of her chain (t24/t37/t43/t45) and hands the dense Senior half to her goal races. The generator also offered t51 and t71 entries; both were dropped because they abut goal turns and t51 would race her five turns before Kashiwa — arriving rested beats the extra fans. Her Unity Cup and Trackblazer presets carry Turf F caution advisories, the same call Haru Urara and Smart Falcon get against turf-dominant schedules. An external guide claims her unique needs six green skills for full effect; the game data this repo bundles shows the unique gated only on `phase_laterhalf_random==1`, so no preset chases a green count and the greens in her plan are there on their own merits. Her plans also deliberately omit Chance of Victory, Collaborative Graded Races ○ and Strong Steps: all three are strong Dirt picks but sit behind Potential Lv3/Lv4/Lv5, and listing skills a trainee cannot learn only pads the plan with entries that never fire — add them once her Potential is raised. All three research-graded, and shipping alongside the race-scraper fix that finally brought the Global dirt calendar (Kashiwa included) into `races.json`.

Her growth is Power +10% / Wit +20% with no Speed growth, so Speed is simply the expensive stat on this body.

The 2026-07-15 addition is the first **Blue Farm** variant: Super Creek (Blue Farm), Unity Cup only. It clones the regular Super Creek Unity Cup preset and changes only `focusOnSparkStatTarget` to all five stats (the base competitive preset focuses just Speed+Stamina). The under-600 spark-rescue boost then fires on every stat, so an off-stat that would otherwise finish below the 600 3-star-blue floor gets lifted, trading a little stat-peak focus for broader blue coverage. Research/new and unproven: the plain "Super Creek" preset stays the competitive Unity Cup build, and the variant is unvalidated until its own completed careers are analyzed.

The second 2026-07-17 addition is **Grass Wonder (Saintly Jade Cleric)**, the roster's second Grass Wonder outfit with its own research-graded trio. She keeps the base card's aptitude grid and the same eight-goal chain (Asahi Hai Futurity Stakes through the Senior Arima Kinen win), so the trio mirrors the proven base configuration; what changes is the body and the kit. Her growth leans Stamina/Wit (+15% each), so training priorities promote Stamina over Power, and her skill plans center her own recovery line -- Deep Breaths upgrading into the Cooldown gold, plus A Small Breather -- in place of the base card's Be Still line, which this outfit has no hint discount for. Two kit skills sit behind Potential levels the account has not reached (Late Surger Savvy ○ at Lv4, Relax at Lv5) and are deliberately left out of every plan until her Potential is raised. Her Unity Cup preset is also the first profile to declare the `safe_completion` spending objective: under Adaptive mode it arms recovery protection, and because the plan already carries her own recovery chain the protection acts as a backstop rather than the plan. With two owned outfits of one character, the trainee picker now shows two Grass Wonder rows, and in-game trainee selection targets the exact outfit banner: the base presets skip the Saintly Jade Cleric card, and the new presets accept only it.

The 2026-07-24 addition opened the **Grand Concert** preset lane with Taiki Shuttle, the scenario's first validated profile: her career completed fully hands-off at A+ rank the same day, covering the Lesson shop, all five concerts, and the end-of-career spending. The preset carries the exact configuration that ran that career (her Mile build with Speed/Power/Wit priorities and smart racing), with one change: the Mile Speed target is raised to 1600 to use the scenario's higher Speed cap. Applying it sets the scenario to Grand Concert together with the settings, so no manual scenario switching is needed. As of 2026-07-26, **every character entry has a Grand Concert preset**, derived from its own URA build: Speed-primary Sprint and Mile builds target the full 1600 Speed cap, tempered Sprint/Mile and all Medium builds target 1400, and Long stayers keep their URA Speed weighting so training stays pointed at the Stamina their goals need. The three Legacy Farm variants stay URA-only on purpose.

The 2026-07-25 addition fills that lane with ten more, chosen as a pre-release test batch rather than
a best-of list. The scenario's concert system is trainee-agnostic: the result tier is set purely by
how many songs were learned in the current cycle, with no stat or aptitude check and no fail state,
so a batch of ten similar Speed builds would only re-test one path. These cover what does vary
between trainees instead: goal chains, distance, surface, and the Senior lyric event. **Agnes
Tachyon** and **Mihono Bourbon** are scenario-link characters, so running either as the trainee
upgrades that event's skill hint from white to gold. **Sakura Bakushin O** and **King Halo** are
Sprint, **Maruzensky (Formula R)** and **Daiwa Scarlet** are Mile, **Copano Rickey** is the first
dirt body to enter the scenario, **Vodka** is Medium, and **Super Creek** and **Gold Ship** are
stayers, whose Stamina ceiling is actually lower here (1300) than in URA Finale (1400).

These ten are **derived from each character's URA Finale build** rather than written out again,
because Grand Concert differs from URA Finale in exactly one way a preset cares about: its stat caps.
Deriving keeps the twins in lockstep, so fixing a URA build can no longer leave a hand-copied Grand
Concert clone quietly stale. Three things change in the derivation:

- The scenario is set in both places, so applying the preset switches scenario with it.
- The Speed target rises for Sprint and Mile builds (to the scenario's 1600 cap) and for Medium
  builds (to 1400), but **not** for stayers. A stat target is a weight, not a ceiling: training
  scores a stat by how far behind its target it sits, so raising Speed on a stayer would pull
  training away from the Stamina its 3000m+ goal races need.
- Any curated mandatory racing plan and any declared skill-spend objective are dropped, matching what
  every other non-URA preset does. A curated plan is tuned to the URA goal chain, and in
  mandatory-plan mode voluntary races only happen on planned turns, so a plan that does not fit the
  scenario cannot recover from a fan shortfall.

All ten stay research-graded until a live career completes. Because derived presets are not literals
in the file, the roster total can no longer be counted by grepping `scenario:` lines; the count is
asserted in `src/data/__tests__/characterPresets.test.ts` instead, which is the authority to update
when it changes.

### Currently included characters

The table below covers the three original scenarios. Every entry in it also has a Grand Concert preset derived from its URA build. Validated Grand Concert profiles so far: **Taiki Shuttle** and **Copano Rickey**; every other Grand Concert preset is research-graded until a live career completes.

| Character | Trackblazer | Unity Cup | URA Finale |
|-----------|:-----------:|:---------:|:----------:|
| Admire Vega | Yes | Yes | Yes |
| Agnes Digital | Yes | Yes | Yes |
| Agnes Tachyon | Yes | Yes | Yes |
| Air Groove | Yes | Yes | Yes |
| Air Groove (Legacy Farm) | -- | -- | Yes |
| Air Shakur | Yes | Yes | Yes |
| Bamboo Memory | Yes | Yes | Yes |
| Biwa Hayahide | Yes | Yes | Yes |
| Copano Rickey | Yes | Yes | Yes |
| Curren Chan | Yes | Yes | Yes |
| Daiwa Scarlet | Yes | Yes | Yes |
| Daiwa Scarlet (Legacy Farm) | -- | -- | Yes |
| Eishin Flash | Yes | Yes | Yes |
| El Condor Pasa | Yes | Yes | Yes |
| El Condor Pasa (Kukulkan Warrior) | Yes | Yes | Yes |
| El Condor Pasa (Legacy Farm) | -- | -- | Yes |
| Fine Motion | Yes | Yes | Yes |
| Fuji Kiseki | Yes | Yes | Yes |
| Gold City (Autumn Cosmos) | Yes | Yes | Yes |
| Gold Ship | Yes | Yes | Yes |
| Gold Ship (RUN! RUIN! LAUNCHER!) | Yes | Yes | Yes |
| Grass Wonder | Yes | Yes | Yes |
| Grass Wonder (Saintly Jade Cleric) | Yes | Yes | Yes |
| Haru Urara | Yes | Yes | Yes |
| Hishi Akebono | Yes | Yes | Yes |
| Hishi Amazon | Yes | Yes | Yes |
| Inari One | Yes | Yes | Yes |
| Ines Fujin | Yes | Yes | Yes |
| King Halo | Yes | Yes | Yes |
| King Halo (Cheerleader in Noble White) | Yes | Yes | Yes |
| Kitasan Black | Yes | Yes | Yes |
| Manhattan Cafe | Yes | Yes | Yes |
| Maruzensky (Formula R) | Yes | Yes | Yes |
| Maruzensky (Hot☆Summer Night) | Yes | Yes | Yes |
| Matikanefukukitaru | Yes | Yes | Yes |
| Matikanetannhauser | Yes | Yes | Yes |
| Mayano Top Gun | Yes | Yes | Yes |
| Mayano Top Gun (Sunlight Bouquet) | Yes | Yes | Yes |
| Meisho Doto | Yes | Yes | Yes |
| Mejiro Ardan | Yes | Yes | Yes |
| Mejiro Bright | Yes | Yes | Yes |
| Mejiro Dober | Yes | Yes | Yes |
| Mejiro McQueen (Frontline Elegance) | Yes | Yes | Yes |
| Mejiro Palmer | Yes | Yes | Yes |
| Mejiro Ryan | Yes | Yes | Yes |
| Mihono Bourbon | Yes | Yes | Yes |
| Narita Brian | Yes | Yes | Yes |
| Narita Taishin | Yes | Yes | Yes |
| Nice Nature | Yes | Yes | Yes |
| Nishino Flower | Yes | Yes | Yes |
| Oguri Cap | Yes | Yes | Yes |
| Oguri Cap (Ashen Miracle) | Yes | Yes | Yes |
| Rice Shower | Yes | Yes | Yes |
| Sakura Bakushin O | Yes | Yes | Yes |
| Sakura Chiyono O | Yes | Yes | Yes |
| Satono Diamond | Yes | Yes | Yes |
| Seiun Sky | Yes | Yes | Yes |
| Seiun Sky (Soirée des Chatons) | Yes | Yes | Yes |
| Silence Suzuka | Yes | Yes | Yes |
| Smart Falcon | Yes | Yes | Yes |
| Special Week | Yes | Yes | Yes |
| Special Week (Hopp'n♪Happy Heart) | Yes | Yes | Yes |
| Super Creek | Yes | Yes | Yes |
| Super Creek (Blue Farm) | -- | Yes | -- |
| Sweep Tosho | Yes | Yes | Yes |
| Symboli Rudolf (Emperor's Path) | Yes | Yes | Yes |
| T.M. Opera O (New Year, Same Radiance!) | Yes | Yes | Yes |
| Taiki Shuttle | Yes | Yes | Yes |
| Taiki Shuttle (Bubblegum☆Memories) | Yes | Yes | Yes |
| Tamamo Cross | Yes | Yes | Yes |
| Tokai Teio | Yes | Yes | Yes |
| Tokai Teio (Beyond the Horizon) | Yes | Yes | Yes |
| Tosen Jordan | Yes | Yes | Yes |
| Vodka | Yes | Yes | Yes |
| Winning Ticket (Get to Winning!) | Yes | Yes | Yes |
| Yaeno Muteki | Yes | Yes | Yes |

---

## How Presets Work at Runtime

### User flow

1. User opens UMA Auto+ and taps the **trainee preset card** on the Home page (or picks a scenario first from the center-button dropdown — both orders work).
2. The **trainee picker** opens: searchable, one row per character/outfit, with URA/UC/TB/GC advisory chips, a Validated/Research badge per scenario, and starred favorites pinned on top. Favorites are per outfit: starring one outfit does not star the character's other outfits.
3. User expands a trainee and taps **Apply** on one of her scenario cards (e.g., "El Condor Pasa" — Trackblazer). The scenario is set together with the preset.
4. The preset's settings are merged into the current app configuration **one category at a time, with a shallow spread**: within a category the preset's keys win, but keys it does not ship keep their current values. Three things are deliberately excluded: the `debug` and `discord` categories are skipped entirely so a preset can never clobber a Debug Mode or Discord webhook, and the skill-point check settings plus the two event-override maps are captured before the merge and restored after it.
5. Settings are **saved to SQLite immediately** so the Kotlin backend reads the correct values.
6. A green confirmation snackbar appears: `Preset "El Condor Pasa" applied`.
7. The bot is now configured with that character's optimized settings and ready to start.

### Scenario filtering

The picker lists one row per preset name (that is, per outfit), not per scenario. Each row carries chips for the scenarios that name has a preset for, and the list filters only on the search box and your favorites. The applied preset survives a scenario switch and an app restart, so it does not reset on its own.

### What gets applied

Each preset contains a **complete settings snapshot** covering:

| Settings Category | What It Controls |
|-------------------|-----------------|
| `general` | Scenario, popup checks, crane game, stop conditions, wait delays |
| `training` | Stat prioritization order, training blacklist, failure chance threshold, risky training, rainbow bonus, YOLO detection, distance override |
| `trainingStatTarget` | Per-distance stat targets (Sprint/Mile/Medium/Long x Speed/Stamina/Power/Guts/Wit) |
| `racing` | Fan farming, race retries, force racing, racing plan, preferred terrain/grades/distances, smart racing |
| `skills` | Skill point threshold, preferred running style/distance/surface, skill plans for each phase (skillPointCheck, preFinals, careerComplete) |
| `trainingEvent` | Energy prioritization, OCR confidence, special/character/support/scenario event overrides |
| `scenarioOverrides` | Per-scenario tuning. Mostly Trackblazer (consecutive race limit, energy threshold, shop check grades, irregular training, excluded items), plus the Grand Concert quick-mode preference |

### What is NOT included in presets

- `discord` settings (token, user ID) -- security sensitive, never exported
- `misc.formattedSettingsString` -- runtime display state
- `misc.currentProfileName` -- runtime profile state
- `racing.racingPlanData` -- large raw race data (the processed racing plan is included)
- `racing.appliedRacingSnapshot` -- runtime bookkeeping written on preset apply, read by the bot for config-drift warnings
- `profiles` array -- user-created profiles are separate from built-in presets

### Curated racing plans in presets

A preset MAY ship a curated `racing.racingPlan` (the selected-races JSON) together with
`enableRacingPlan`/`enableMandatoryRacingPlan: true` when a trainee depends on specific race
entries -- Haru Urara (URA Finale) ships her dirt stakes agenda this way because her viable
pool is dirt sprint/mile and its plannable entries are sparse. Every preset states the
racing-plan trio explicitly, so switching presets never drags one trainee's plan onto
another. Entries use the same shape the Racing Plan screen saves:
`{ raceName, date, priority, turnNumber }`.

---

## File Structure

### Where presets are stored

All presets live in a single TypeScript file:

```
src/data/characterPresets.ts
```

This file exports a typed array:

```typescript
import { Settings } from "../context/BotStateContext"

export interface CharacterPreset {
    name: string              // Display name (e.g., "El Condor Pasa")
    scenario: string          // Scenario (e.g., "Trackblazer")
    traineeName?: string      // In-game trainee to select, when it differs from the display name
    settings: DeepPartial<Settings>  // Partial settings, merged over the defaults
}

export const characterPresets: CharacterPreset[] = [
    {
        name: "Agnes Tachyon",
        scenario: "Trackblazer",
        settings: {
            general: { ... },
            training: { ... },
            trainingStatTarget: { ... },
            racing: { ... },
            skills: { ... },
            trainingEvent: { ... },
            scenarioOverrides: { ... },
        }
    },
    // ... the remaining presets
]
```

### How the Home page uses presets

In `src/pages/Home/index.tsx`:

The entry point is `handlePresetChange(presetName, scenarioOverride?)`. The picker supplies the scenario alongside the name, so a preset applies its own scenario rather than being filtered against the currently selected one. Its core is the per-category merge:

```typescript
const merged = { ...bsc.settings }
for (const [category, values] of Object.entries(preset.settings)) {
    // Device/user-level categories are never per-trainee tuning.
    if (category === "debug" || category === "discord") continue
    merged[category] = { ...merged[category], ...values }
}
```

The real function does more around that loop: it captures the skill-point check settings and the two event-override maps beforehand and restores them afterwards, applies the scenario, persists to SQLite, and records the applied snapshot so the selection survives a restart. Read the function rather than relying on this excerpt.

---

## How to Create a New Character Preset

### Prerequisites

- A known settings configuration for the trainee and scenario (an exported settings file is a convenient reference, not a requirement)
- The character must be configured for a specific scenario (URA Finale, Unity Cup, Trackblazer, or Grand Concert)
- Each scenario requires a separate preset (the same character plays differently in each scenario)

### Method 1: Start from an exported settings file

1. Configure the bot in UMA Auto+ (or the original Steve app) for the trainee and
   scenario you are building: stat prioritization, stat targets, racing plan and
   preferences, skill plans (`skillPointCheck`, `preFinals`, `careerComplete`),
   scenario overrides, and training event overrides.
2. Export the configuration from Settings > Settings Management > Export Settings.
   This writes a JSON file containing the configurable settings, as a reference.
3. Use that JSON as a reference while adding a new object to the `characterPresets`
   array in `src/data/characterPresets.ts`: copy the fields you configured into the
   preset's `settings` block, and apply the required-field rules below
   (`enablePopupCheck: false`, no `discordToken`, no `formattedSettingsString` or
   `currentProfileName`).

There is no script that turns an exported settings file into a preset entry
automatically. The export exists so you can build and check a configuration inside
the app before transcribing it; the exported file itself is never committed or read
by any build step.

### Method 2: Manual entry

1. Open `src/data/characterPresets.ts`.

2. Add a new entry to the `characterPresets` array:

   ```typescript
   {
       name: "Special Week",
       scenario: "Trackblazer",
       settings: {
           general: {
               scenario: "Trackblazer",
               enablePopupCheck: false,
               enableCraneGameAttempt: false,
               // ... other general settings
           },
           training: {
               statPrioritization: ["Speed", "Power", "Stamina", "Wit", "Guts"],
               maximumFailureChance: 20,
               // ... other training settings
           },
           trainingStatTarget: {
               trainingSprintStatTarget_speedStatTarget: 1200,
               // ... all stat targets
           },
           racing: {
               enableFarmingFans: true,
               // ... racing settings
           },
           skills: {
               // ... skill settings
           },
           trainingEvent: {
               // ... event override settings
           },
           scenarioOverrides: {
               // ... scenario-specific settings
           },
       }
   }
   ```

3. **Rebuild the APK**.

### Important rules for new presets

- `enablePopupCheck` must be set to `false` in all presets (prevents the bot from stopping on expected popups during queued runs).
- Do NOT include `discordToken` (security).
- Do NOT include `formattedSettingsString` or `currentProfileName` (runtime state).
- Each character needs a **separate preset per scenario** since stat priorities, racing plans, and skill builds differ between scenarios.
- The `name` field must be unique within each scenario.
- **Empty `supportEventOverrides: {}` and `scenarioEventOverrides: {}` are sentinel placeholders, not "no overrides".** When these arrive empty, the Home page's preset-merge code deliberately *preserves* the user's existing overrides across a preset switch. Filling them in "helpfully" will clobber whatever the user configured. Leave them empty unless you intend to overwrite.
- **Parent-farming presets keep every stat listed in `focusOnSparkStatTarget`.** The career-end blue spark picks its stat uniformly at random among the five, and any stat finishing below 600 can never roll a 3-star. Narrowing the list to a trainee's "good" stats silently reintroduces dead rolls. The training scorer's spark-rescue boost only fires on listed stats, so the list *is* the lever — leave all five in place.
- **`skills.skillSpendObjective` is optional and preset-owned**: `rank` (the default when absent -- identical to pre-objective behavior), `safe_completion`, `sparks`, or `race_reward`. It only gates the Adaptive-mode dynamic triggers, so declaring one is harmless for Manual-mode users. Every preset apply *stamps* the field (absent → `rank`), so objectives never leak between presets. Declare `race_reward` only when a specific must-win race is the career's point (Copano Rickey URA's Kashiwa sash profile is the model). Declare `sparks` for farming profiles: under Adaptive mode it buys planned skills only during the career (the broad strategy tail is skipped mid-career), so a sparks preset must plan its own recovery skills -- there is no automatic recovery exception. At career end the remaining balance is spent on profile-compatible skills after the plan (the game discards unspent points at Finish), and the bot checks whether useful affordable skills remain before finishing -- it stops safely instead of pressing Finish when it cannot explain a large unused balance. The four farming profiles (Super Creek Blue Farm and the three Legacy Farms) declare it. Declare `safe_completion` for Long profiles where finishing reliably is the point: on Long (and Medium, safe_completion only) Adaptive careers it additionally arms recovery protection, which buys the cheapest compatible observed recovery (white before gold, existing preference gates, no new trigger) whenever none is owned, gifted, or freshly bought from the plan -- a Potential-gated planned gold never blocks that fallback. Grass Wonder (Saintly Jade Cleric) Unity Cup is the first profile to declare it; its picker identity resolves to the exact in-game outfit banner, and because its plan already carries her own recovery chain the protection is the net, not the plan. Do NOT set `skillSpendMode` or `accountTier` in a preset -- those are the user's global choices.

---

## Maintaining Built-in Presets

`characterPresets.ts` and `presetMeta.ts` split the preset system into two jobs.

- `src/data/characterPresets.ts` owns the presets themselves: `basePresets`
  (hand-written entries), `grandConcertFrom(...)` and the `grandConcertPresets` it
  derives from a trainee's URA Finale preset, and the `trainerAdvisories` /
  `avoidAdvisoryFor` scenario-fit metadata. Add or edit a preset here.
- `src/data/presetMeta.ts` owns per-character lookup and status metadata that is not
  specific to a single preset: base-outfit resolution (`presetCharacter`,
  `presetOutfit`, `characterBaseOutfits`) and validation status (`presetValidation`,
  `validatedPresets`). Update this file when a trainee's outfit identity or
  validation status changes, not when a preset's settings change.

A Grand Concert entry is usually derived rather than hand-written. Check
`grandConcertPresets` before adding one by hand, and prefer calling
`grandConcertFrom("<trainee>", speedTarget?)` from the trainee's URA Finale preset.

The preset count and roster structure are not safe to determine by grepping
`scenario:` lines. The "locks the roster totals the docs quote" test in
`src/data/__tests__/characterPresets.test.ts` is the authority for the exact roster
size and Grand Concert count, and the same test also enforces unique
`name`/`scenario` keys across the whole roster. Skill and race id checks exist only
for the specific trainees the suite explicitly covers (for example Copano Rickey
and Grass Wonder Saintly Jade Cleric); passing `yarn test` does not prove a new
preset's skill or race ids are valid unless a test actually covers it. Run the
suite after any preset change.

After changing a preset:

```bash
node_modules/.bin/tsc --noEmit
yarn test
```

---

## Building After Changes

After modifying presets, rebuild the APK:

```bash
# From project root
cd <REPO_ROOT>

# Regenerate the JavaScript bundle (includes the updated presets)
node android/generate-bundle.js

# Build the debug APK
cd android && ./gradlew assembleDebug
```

The output APK will be at (version number matching the current build):
```
android/app/build/outputs/apk/debug/UMA-Auto-Plus-v1.4.0-arm64-v8a-debug.apk
```

---

## Minimum Settings Per Character

At minimum, each character preset should define:

### Required

| Setting | Why |
|---------|-----|
| `training.statPrioritization` | Determines which stats the bot trains first (order matters) |
| `trainingStatTarget.*` | Target stat values per distance -- the bot stops training a stat when it reaches the target |

### Strongly recommended

| Setting | Why |
|---------|-----|
| `training.preferredDistanceOverride` | Some characters excel at a distance different from their auto-detected aptitude |
| `training.trainingBlacklist` | Prevents the bot from wasting turns on unwanted training types |
| `training.maximumFailureChance` | Risk tolerance -- higher allows more aggressive training |
| `racing.enableFarmingFans` | Whether to race extra for fan count |
| `racing.preferredGrades` | Which race grades to prioritize (G1, G2, G3) |
| `skills.plans` | Which skills to buy and when (preFinals, careerComplete) |

### Optional (scenario-specific)

| Setting | Scenario | Why |
|---------|----------|-----|
| `scenarioOverrides.trackblazerConsecutiveRacesLimit` | Trackblazer | How many races in a row before forced recovery |
| `scenarioOverrides.trackblazerEnableIrregularTraining` | Trackblazer | Whether to train instead of race when training value is high |
| `scenarioOverrides.trackblazerExcludedItems` | Trackblazer | Which shop items to skip |
| `training.enableRainbowTrainingBonus` | All | Whether to prioritize rainbow training opportunities |
| `training.trainWitDuringFinale` | All | Whether to train Wit during the final 3 turns |

---

## Troubleshooting

### Preset doesn't appear in the dropdown
- Verify the `scenario` field exactly matches one of: `"URA Finale"`, `"Unity Cup"`, `"Trackblazer"`, `"Grand Concert"` (case-sensitive).
- Verify the preset was added to the `characterPresets` array in `characterPresets.ts`.
- Rebuild the JS bundle and APK after changes.

### Preset applies wrong settings
- Check that the correct scenario variant is selected. Every character ships 4 presets (URA Finale, Unity Cup, Trackblazer, Grand Concert); the Legacy Farm and Blue Farm variants ship a single preset each.
- Verify the JSON source file was exported while the correct scenario was active.

### Character name displays incorrectly
- The name comes from the `name` field in the preset object.
