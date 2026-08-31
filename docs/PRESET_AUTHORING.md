# Preset authoring

How to add or edit a built-in character preset. For what presets do from a player's side, see
[PRESETS_GUIDE.md](../PRESETS_GUIDE.md).

## Source of truth

Two files split the job.

- **`src/data/characterPresets.ts`** owns the presets themselves: `basePresets` (the hand-written
  entries), `grandConcertFrom(...)` and the `grandConcertPresets` it derives from each trainee's URA
  Finale preset, and the `trainerAdvisories` / `avoidAdvisoryFor` scenario-fit metadata. The
  exported `characterPresets` is `basePresets` concatenated with `grandConcertPresets`. Add or edit
  a preset here.
- **`src/data/presetMeta.ts`** owns per-character lookup and status metadata that is not specific to
  one preset: base-outfit resolution (`presetCharacter`, `presetOutfit`, `characterBaseOutfits`) and
  validation status (`presetValidation`, `validatedPresets`). Update this file when a trainee's
  outfit identity or her validation status changes, never when a preset's settings change.

A preset's `name` is a persistence key. It is written into saved settings, rotation snapshots, and
the applied-racing snapshot. Renaming one is a migration, not a cosmetic edit.

## Preset shape

```typescript
export interface CharacterPreset {
    name: string                     // Display name, e.g. "El Condor Pasa"
    scenario: string                 // "URA Finale" | "Unity Cup" | "Trackblazer" | "Grand Concert"
    traineeName?: string             // In-game trainee to select, when it differs from the display name
    settings: DeepPartial<Settings>  // Partial settings, merged over the defaults
}
```

`settings` is a `DeepPartial<Settings>` keyed by settings category. Override only what differs from
`defaultSettings` in `src/context/BotStateContext.tsx`; never write out a full `Settings` object.

The categories a preset may ship: `general`, `misc`, `training`, `trainingStatTarget`, `racing`,
`skills`, `trainingEvent`, `scenarioOverrides`.

## How apply works, and what that means for you

Home merges a preset one category at a time with a shallow spread. Within a category the preset's
keys win and keys it does not ship keep their current values. Three consequences matter when
authoring:

- **Arrays replace, they do not merge.** A partial `statPrioritization` is a bug, not a tweak.
- **`debug` and `discord` are skipped entirely.** Do not ship them.
- **`skills.skillPointCheck` and `skills.enableSkillPointCheck` are captured before the merge and
  restored after it**, so a preset cannot own them.
- **`trainingEvent.supportEventOverrides` and `trainingEvent.scenarioEventOverrides` preserve the
  user's values only when a preset ships them empty** -- the sentinel explained below. A preset that
  ships a non-empty map overwrites the user's pick for each event key it names; unnamed user picks
  survive the merge.

Two fields are stamped on every apply (absent means the default), so nothing leaks between presets:
`skills.skillSpendObjective` (absent -> `rank`) and `training.moodFloor` (absent -> `Good`).

### `training.moodFloor`

Preset-owned, one of `Normal`, `Good`, or `Great` (case-insensitive; anything else falls back to
`Good`). Declare a stricter floor only for a trainee whose event chain contains a mood-gated trap.
It is stamped on every apply precisely so a strict floor cannot carry over to the next trainee.

## Required rules

- **`general.enablePopupCheck` must be `false`** in every preset. It stops the bot on expected
  popups, which breaks queued runs.
- **`general.scenario` must be set** and must match the entry's `scenario` field, so applying the
  preset switches scenario with it.
- **`name` must be unique within a scenario.** The roster test enforces uniqueness across the whole
  `name|scenario` key space.
- **Never include** `discordToken`, `misc.formattedSettingsString`, `misc.currentProfileName`,
  `racing.racingPlanData`, or `racing.appliedRacingSnapshot`. The first is security sensitive, the
  rest are runtime state. `misc` itself is a legitimate preset-owned category for ordinary UI/display
  settings (screen text size, overlay button size, and the like); those two runtime fields are the
  exception, not the whole category.
- **Never set `skills.skillSpendMode` or `skills.accountTier`.** Those are the user's global
  choices.
- **State the racing-plan trio explicitly** (`enableRacingPlan`, `enableMandatoryRacingPlan`,
  `racingPlan`) in every preset, even when the answer is off/off/empty. Without it, switching presets
  drags one trainee's plan onto another.

### Empty override maps are sentinels

`trainingEvent.supportEventOverrides: {}` and `trainingEvent.scenarioEventOverrides: {}` do not mean
"no overrides". When they arrive empty, the apply path deliberately **preserves** the user's
existing picks, because those maps are deck- and scenario-specific rather than character-specific.
Filling them in "helpfully" overwrites the user's choices for any event keys the preset names.
Leave them empty unless you intend to do that.

### `training.focusOnSparkStatTarget`

The training scorer's under-600 spark-rescue boost fires only on the stats listed here, so the list
is the lever. The career-end blue spark picks its stat uniformly at random among all five, and a
stat below 600 can never roll a 3-star.

- Competitive presets narrow the list to the stats the build actually wants.
- **Parent-farming presets keep all five.** Narrowing a farming preset silently reintroduces dead
  rolls.

### `skills.skillSpendObjective`

Optional, preset-owned, one of `rank` (the default when absent, identical to pre-objective
behavior), `safe_completion`, `sparks`, or `race_reward`. It gates only the Adaptive-mode dynamic
triggers, so declaring one is inert for Manual-mode users. The full policy lives in
`HOW_IT_WORKS.md`; the authoring rules are:

- **`race_reward`** only when a specific must-win race is the career's point. It also arms recovery
  protection on Long careers, same as `safe_completion` below.
- **`sparks`** for farming profiles. Under Adaptive mode it buys planned skills only during the
  career, so a `sparks` preset **must plan its own recovery skills**. There is no automatic recovery
  exception.
- **`safe_completion`** for Long profiles where finishing reliably is the point. It additionally
  arms recovery protection on Long and Medium careers, which buys the cheapest compatible observed
  recovery when none is owned. Treat that as a backstop, not a substitute for planning recovery.

The canonical values live in `SKILL_SPEND_OBJECTIVES` (`src/lib/adaptiveSkillPolicy.ts`), and the
test suite asserts every declared objective is one of them.

## Adding a preset

1. Configure the build in the app for that trainee and scenario: stat prioritization, stat targets,
   racing plan and preferences, the three skill plans (`skillPointCheck`, `preFinals`,
   `careerComplete`), scenario overrides, and character event overrides. For a curated or farm
   racing plan, `scripts/generate-racing-plan.mjs` can generate the plan from
   `character_objectives.json`; it does not generate a complete preset entry.
2. Optionally export it from Settings > Settings Management > Export Settings and use the JSON as a
   reference while transcribing. **There is no script that converts an export into a preset entry.**
   The exported file is never committed and no build step reads it.
3. Add the entry to `basePresets` in `src/data/characterPresets.ts`, applying the required rules
   above.
4. Add a `trainerAdvisories` entry when the trainee's aptitudes make a scenario a good or bad fit.
   `recommended` lists scenarios; each `avoid` entry needs a `scenario` and a non-empty `reason`
   that names the actual aptitude problem, because the reason text is shown to the user.
5. Update `presetMeta.ts` if the trainee is a new base card (`characterBaseOutfits`) or a career has
   validated the preset (`validatedPresets`).
6. Update the roster-total assertion in `src/data/__tests__/characterPresets.test.ts`.

### Grand Concert entries

Prefer derivation over hand-writing. Call `grandConcertFrom("<URA preset name>", speedTarget?)` from
`grandConcertPresets` rather than adding a fourth literal. The helper throws if no URA Finale preset
of that name exists, sets the scenario in both places, drops any curated racing plan and any
skill-spend objective, and raises the Speed target only where a target was passed. Taiki Shuttle's
Grand Concert entry is an existing hand-written literal predating this pattern; treat it as a
one-off, not a template for new entries.

Speed targets follow the scenario's caps: Speed-primary Sprint and Mile builds go to the higher
Speed cap, the handful of tempered Sprint/Mile builds whose URA Speed target already sits below the
normal baseline go to the middle value instead, Medium builds go to that same middle value, and
**stayers keep their URA weighting** and take no `speedTarget` argument. A stat target is a weight,
not a ceiling: raising Speed on a stayer pulls training away from the Stamina her goal races need.
The test asserts no derived target exceeds a Grand Concert stat cap.

## Roster counts

**Do not count presets by grepping `scenario:` lines.** Derived Grand Concert twins are not literals
in the file, and `grandConcertFrom`'s own body adds a matching line.

The "locks the roster totals the docs quote" test in `src/data/__tests__/characterPresets.test.ts`
is the authority for the total, the Grand Concert count, and `name|scenario` uniqueness. Update it
in the same change as the preset.

Skill-id and race-id checks exist only for the specific trainees the suite explicitly covers.
Passing the suite does not prove a new preset's skill or race ids are valid unless a test actually
covers it.

## Verification

After any preset change:

```bash
node_modules/.bin/tsc --noEmit
yarn test
```

A preset change is a TypeScript change, so it does not reach a standalone APK until the JS bundle is
regenerated:

```bash
yarn android:bundle
```

Or build and bundle in one step with `yarn build:bundle`. Installing a debug APK does **not**
rebundle on its own.

`PRESETS_GUIDE.md` is the documented player-facing view of this roster. When a change alters what a
player sees -- a new variant family, a changed scenario restriction, a new advisory category -- move
the guide in the same change.
