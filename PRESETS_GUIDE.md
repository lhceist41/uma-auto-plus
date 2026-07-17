# Built-in Character Presets Guide

This document explains how built-in character presets work in UMA Auto+, how they are structured, and how to create new presets for additional characters.

---

## Overview

UMA Auto+ ships with **220 built-in character presets** (72 character entries x 3 scenarios, plus three URA-only Legacy Farm variants and one Unity Cup Blue Farm variant). Trainees are picked in the searchable trainee picker on the Home page -- one row per character/outfit with per-scenario advisory chips and a validation badge -- and applying a preset sets its scenario together with its settings.

The 2026-06-11 batch added seven entries from the June banner research pass: Sweep Tosho, Mihono Bourbon, Mejiro Palmer, El Condor Pasa (Kukulkan Warrior), Tosen Jordan, Super Creek, and Matikanetannhauser. Their URA presets ship curated racing plans (see "Curated racing plans in presets" below); Tosen Jordan's presets enable negative-skill buying so the buy pass clears her 3D Nail Art speed debuff. Symboli Rudolf (Emperor's Path) was added 2026-06-18 — a Late Surger Medium built around her late-overtake unique; URA and Trackblazer are her recommended scenarios. Biwa Hayahide (Pace Chaser, built as a Long stayer) and Mejiro Ryan (Late Surger Medium) were added 2026-06-21 to make two owned-but-unpresetted Medium/Long-A turf bodies farmable hands-off; both fill the Medium/Long Team-Trials slots and are URA + Trackblazer recommended.

The 2026-07-05 tuning pass put Stamina first in the URA Finale and Unity Cup presets for Mayano Top Gun, Tosen Jordan, and Symboli Rudolf: all three were reaching their Long goal races (Kikuka Sho, Tenno Sho Spring) short on stamina and force-ending, and the training-priority order — not the stat targets — is what governs early training. Their Trackblazer presets are unchanged (its goals are result-point-based, not a Long chain). Winning Ticket (Get to Winning!) kept her Trackblazer preset but picked up an avoid advisory for it: her Sprint=G/Mile=F aptitudes make the Junior Result-Points checkpoint unreliable, so the "Yes" below means the preset ships, not that it is recommended — the Home advisory flags it.

The 2026-07-06 batch added the six top-tier cards the community asked for: Kitasan Black, Nishino Flower, Seiun Sky, Maruzensky (Hot☆Summer Night), Oguri Cap, and Oguri Cap (Ashen Miracle). All six are research-graded — built from verified per-card aptitude/objective data (career objective chains are shared across a character's outfits, so the two Oguri cards share one racing plan) and cloned from the nearest validated archetype, but none has completed a live career on the maintainer's account yet; the picker shows them with the Research badge. Kitasan and Seiun train Stamina first (Long goal chains), Nishino is a Sprint/Mile speed build with a participation-only Oaks gate, and both recovery-gated uniques (summer Maruzensky, Ashen Miracle Oguri) ship skill plans that front-load the recovery skills their uniques need.

The second 2026-07-06 batch completed the community S-tier list with 17 more cards: ten new characters (Silence Suzuka, Manhattan Cafe, Narita Taishin, Tamamo Cross, Mejiro Dober, Special Week, Smart Falcon, Meisho Doto, Tokai Teio, T.M. Opera O in her New Year outfit) plus alternate outfits for Special Week, Tokai Teio, Seiun Sky, Mayano Top Gun, Gold Ship, King Halo, and Taiki Shuttle. All research-graded, same pipeline as the first batch: cloned from the nearest validated archetype, per-card grids and growths verified, racing plans checked against the race database and each card's goal turns. Notables: Smart Falcon is the second dirt specialist after Haru Urara -- his URA preset ships a curated dirt agenda whose three Junior entries exist to satisfy the turn-25 two-win goal (mandatory plans race planned turns only, so a plan that starts too late force-ends the career). Narita Taishin is the roster's first End Closer, with his archetype's Late-Surger skills swapped to End counterparts. Silence Suzuka is Front-forced (her unique needs a clear lead) and Medium-primary despite the Mile-A grid, because her goal chain is almost entirely Medium. Trackblazer avoid advisories ship for the narrow-Junior-pool bodies (Manhattan Cafe, Tamamo Cross, Meisho Doto, both Teio cards) and for Smart Falcon's Turf E.

The 2026-07-10 batch closed the remaining global-roster gaps with 18 characters: Admire Vega, Agnes Digital, Air Shakur, Bamboo Memory, Curren Chan, Eishin Flash, Fine Motion, Fuji Kiseki, Hishi Akebono, Inari One, Ines Fujin, Mejiro Ardan, Mejiro Bright, Narita Brian, Rice Shower, Sakura Chiyono O, Satono Diamond, and Yaeno Muteki. Same research-graded pipeline: verified grids, growths, and goal chains; racing plans validated entry-by-entry against the race database; event pins verified against the character event data. Notables: Inari One joins the dirt roster as an End Closer whose skill plan carries a surface-agnostic recovery for her dirt-to-turf Arima pivot; Ines Fujin is Front Runner-locked like Silence Suzuka; the sprinters with thin fan-gated Junior pools (Curren Chan, Hishi Akebono) carry Trackblazer avoid advisories; goal-sparse or gate-heavy bodies (Agnes Digital's unscheduled turn-47-60 G1 window, Rice Shower's near-empty Junior, Mejiro Ardan's 6,000-fan wall at turn 30, Hishi Akebono's early fan checkpoints) all ship curated racing plans. Bamboo Memory postdates the bundled game data: her presets ship empty event overrides until the next characters.json sync, so her story events fall back to the OCR stat-option heuristic.

The 2026-07-11 additions are the first **Legacy Farm** variants: Daiwa Scarlet, El Condor Pasa, and Air Groove parent-farming editions (URA only). Each clones the character's regular URA preset and swaps in a generated G1-dense mandatory racing plan — 17, 20, and 14 G1 entries per career respectively — spaced so the run never exceeds the consecutive-race limit the plan itself must guard (mandatory-plan mode overrides the in-game warning). Built for breeding parents: more racing and fewer training turns, so they are deliberately not recommended for badge or rating chasing. Generated with `scripts/generate-racing-plan.mjs`, which any future parent candidate can be run through.

The 2026-07-17 addition is **Copano Rickey**, the third pure dirt body after Haru Urara and Smart Falcon, and the first built as a Pace Chaser — her unique (`Luck Runs My Way`) and her whole innate kit read the back half of the field, so a Front build never triggers them. Her URA preset is the Kashiwa sash profile: Kashiwa Kinen is a Senior-May G1 over Dirt Mile 1600m at Funabashi, and the objective accepts 3rd while a Winner's Sash needs 1st, so its curated dirt agenda covers only the empty Junior/Classic half of her chain (t24/t37/t43/t45) and hands the dense Senior half to her goal races. The generator also offered t51 and t71 entries; both were dropped because they abut goal turns and t51 would race her five turns before Kashiwa — arriving rested beats the extra fans. Her Unity Cup and Trackblazer presets carry Turf F caution advisories, the same call Haru Urara and Smart Falcon get against turf-dominant schedules. An external guide claims her unique needs six green skills for full effect; the game data this repo bundles shows the unique gated only on `phase_laterhalf_random==1`, so no preset chases a green count and the greens in her plan are there on their own merits. Her plans also deliberately omit Chance of Victory, Collaborative Graded Races ○ and Strong Steps: all three are strong Dirt picks but sit behind Potential Lv3/Lv4/Lv5, and listing skills a trainee cannot learn only pads the plan with entries that never fire — add them once her Potential is raised. All three research-graded, and shipping alongside the race-scraper fix that finally brought the Global dirt calendar (Kashiwa included) into `races.json`.

Her first live career (2026-07-17) **won Kashiwa and took the sash**, reaching the race on Speed 657 / Stamina 337 / Power 656 / Guts 452 / Wit 413 and finishing on 850 / 479 / 838 / 645 / 625 (A rank, URA finals swept 3/3). That is one career, so it shows the profile is viable rather than repeatable — but it is the only stat evidence that exists for her, and it is worth knowing that the sash did not require the ~900-Speed-at-Kashiwa figure an earlier draft of this profile assumed. Treat higher stats as ceilings to aim at, not as thresholds she must clear; her growth is Power +10% / Wit +20% with no Speed growth, so Speed is simply the expensive stat on this body.

The 2026-07-15 addition is the first **Blue Farm** variant: Super Creek (Blue Farm), Unity Cup only. It clones the regular Super Creek Unity Cup preset and changes only `focusOnSparkStatTarget` to all five stats — the base competitive preset focuses just Speed+Stamina. The under-600 spark-rescue boost then fires on every stat, so an off-stat that would otherwise finish below the 600 3-star-blue floor gets lifted, trading a little stat-peak focus for broader blue coverage. Chosen because the analyzer measured Super Creek as the account's most reliable all-five-≥600 Unity Cup arm. Research/new and unproven — the plain "Super Creek" preset stays the competitive Unity Cup build, and the variant is unvalidated until its own completed careers are analyzed.

The second 2026-07-17 addition is **Grass Wonder (Saintly Jade Cleric)**, the roster's second Grass Wonder outfit with its own research-graded trio. She keeps the base card's aptitude grid and the same eight-goal chain (Asahi Hai Futurity Stakes through the Senior Arima Kinen win), so the trio mirrors the proven base configuration; what changes is the body and the kit. Her growth leans Stamina/Wit (+15% each), so training priorities promote Stamina over Power, and her skill plans center her own recovery line -- Deep Breaths upgrading into the Cooldown gold, plus A Small Breather -- in place of the base card's Be Still line, which this outfit has no hint discount for. Two kit skills sit behind Potential levels the account has not reached (Late Surger Savvy ○ at Lv4, Relax at Lv5) and are deliberately left out of every plan until her Potential is raised. With two owned outfits of one character, the trainee picker now shows two Grass Wonder rows, and in-game trainee selection targets the exact outfit banner: the base presets skip the Saintly Jade Cleric card, and the new presets accept only it.

### Currently included characters

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
2. The **trainee picker** opens: searchable, one row per character/outfit, with URA/UC/TB advisory chips, a Validated/Research badge per scenario, and starred favorites pinned on top.
3. User expands a trainee and taps **Apply** on one of her scenario cards (e.g., "El Condor Pasa" — Trackblazer). The scenario is set together with the preset.
4. The preset's settings are **deep-merged** into the current app configuration, overwriting every settings category it ships.
5. Settings are **saved to SQLite immediately** so the Kotlin backend reads the correct values.
6. A green confirmation snackbar appears: `Preset "El Condor Pasa" applied`.
7. The bot is now configured with that character's optimized settings and ready to start.

### Scenario filtering

Only presets matching the selected scenario are shown. When the user changes scenario, the preset selection resets. This prevents accidentally using a Trackblazer preset for a URA Finale run.

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
| `scenarioOverrides` | Trackblazer-specific: consecutive race limit, energy threshold, shop check grades, irregular training, excluded items |

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
    settings: DeepPartial<Settings>  // Full settings object
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
    // ... 44 more presets
]
```

### How the Home page uses presets

In `src/pages/Home/index.tsx`:

```typescript
// Filter presets by currently selected scenario
const filteredPresets = characterPresets
    .filter((p) => p.scenario === bsc.settings.general.scenario)
    .map((p) => ({ value: p.name, label: p.name }))

// When user picks a preset, deep-merge its settings
const handlePresetChange = async (presetName) => {
    const preset = characterPresets.find(
        (p) => p.name === presetName && p.scenario === bsc.settings.general.scenario
    )
    // Merge each settings category
    const merged = { ...bsc.settings }
    for (const [category, values] of Object.entries(preset.settings)) {
        merged[category] = { ...merged[category], ...values }
    }
    bsc.setSettings(merged)
    await saveSettings()  // Persist to SQLite
}
```

---

## How to Create a New Character Preset

### Prerequisites

- A fully configured settings export for the character (JSON file)
- The character must be configured for a specific scenario (Trackblazer, Unity Cup, or URA Finale)
- Each scenario requires a separate preset (the same character plays differently in each scenario)

### Method 1: Export from the app (recommended)

1. **Configure the bot** in UMA Auto+ (or the original Steve app) for the new character:
   - Set stat prioritization order
   - Set stat targets per distance
   - Configure training blacklist
   - Set up racing plan and preferences
   - Configure skill plans (preFinals, careerComplete, skillPointCheck)
   - Set scenario-specific overrides
   - Configure training event overrides for the character

2. **Export settings** via Settings > Settings Management > Export Settings.
   This produces a JSON file like `UAA-settings-2026-04-13T120000.json`.

3. **Rename the file** following the naming convention:
   ```
   {character-name-kebab-case}-profile-{scenario-kebab-case}.json
   ```
   Examples:
   - `special-week-profile-trackblazer.json`
   - `tokai-teio-profile-unity-cup.json`
   - `silence-suzuka-profile-ura-finale.json`

4. **Place the file** in the appropriate scenario folder (the same `<YOUR_PROFILES_DIRECTORY>` referenced by the extraction script below):
   ```
   <YOUR_PROFILES_DIRECTORY>/Trackblazer/
   <YOUR_PROFILES_DIRECTORY>/Unity-Cup/
   <YOUR_PROFILES_DIRECTORY>/URA-Finale/
   ```

5. **Run the extraction script** to regenerate `characterPresets.ts` (see "Regenerating All Presets" below).

6. **Rebuild the APK**.

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
- **`skills.skillSpendObjective` is optional and preset-owned**: `rank` (the default when absent -- identical to pre-objective behavior), `safe_completion`, `sparks`, or `race_reward`. It only gates the Adaptive-mode dynamic triggers, so declaring one is harmless for Manual-mode users. Every preset apply *stamps* the field (absent → `rank`), so objectives never leak between presets. Declare `race_reward` only when a specific must-win race is the career's point (Copano Rickey URA's Kashiwa sash profile is the model). Declare `sparks` for farming profiles: under Adaptive mode it buys planned skills only (the broad strategy tail is skipped and leftover SP accepted), so a sparks preset must plan its own recovery skills -- there is no automatic recovery exception. The four farming profiles (Super Creek Blue Farm and the three Legacy Farms) declare it. Declare `safe_completion` for Long profiles where finishing reliably is the point: on Long (and Medium, safe_completion only) Adaptive careers it additionally arms recovery protection, which buys the cheapest compatible observed recovery (white before gold, existing preference gates, no new trigger) whenever none is owned, gifted, or freshly bought from the plan -- a Potential-gated planned gold never blocks that fallback. No shipped profile declares it yet; the first will need a preset whose picker identity resolves to the intended in-game outfit. Do NOT set `skillSpendMode` or `accountTier` in a preset -- those are the user's global choices.

---

## Regenerating All Presets

If you add new JSON profile files to the source folders, you can regenerate the entire `characterPresets.ts` file using this Python script:

```python
import json
import os

base_dir = "<YOUR_PROFILES_DIRECTORY>"
scenarios = {
    "Trackblazer": "Trackblazer",
    "Unity-Cup": "Unity Cup",
    "URA-Finale": "URA Finale",
}

presets = []

for folder_name, scenario_name in scenarios.items():
    folder_path = os.path.join(base_dir, folder_name)
    if not os.path.exists(folder_path):
        continue

    for filename in sorted(os.listdir(folder_path)):
        if not filename.endswith(".json"):
            continue

        with open(os.path.join(folder_path, filename), 'r', encoding='utf-8') as f:
            data = json.load(f)

        # Extract character name from filename
        name_part = filename.replace(f"-profile-{folder_name.lower()}.json", "")
        char_name = " ".join(word.capitalize() for word in name_part.split("-"))

        # Apply required transformations
        if "general" in data:
            data["general"]["enablePopupCheck"] = False
        if "profiles" in data:
            del data["profiles"]
        if "discord" in data and "discordToken" in data["discord"]:
            del data["discord"]["discordToken"]
        if "misc" in data:
            data["misc"].pop("formattedSettingsString", None)
            data["misc"].pop("currentProfileName", None)
        if "trainingEvent" in data:
            data["trainingEvent"].pop("scenarioEventData", None)
        if "racing" in data:
            data["racing"].pop("racingPlanData", None)

        presets.append({
            "name": char_name,
            "scenario": scenario_name,
            "settings": data,
        })

# Generate TypeScript
output = 'import { Settings } from "../context/BotStateContext"\n\n'
output += 'export interface CharacterPreset {\n'
output += '    name: string\n'
output += '    scenario: string\n'
output += '    settings: DeepPartial<Settings>\n'
output += '}\n\n'
output += 'export const characterPresets: CharacterPreset[] = \n'
output += json.dumps(presets, indent=4, ensure_ascii=False)
output += '\n'

with open("<REPO_ROOT>/src/data/characterPresets.ts", 'w', encoding='utf-8') as f:
    f.write(output)

print(f"Generated {len(presets)} presets")
```

Run from the project root:
```bash
python generate_presets.py
```

---

## Filename Naming Convention

Source JSON files must follow this pattern:

```
{character-name}-profile-{scenario}.json
```

| Part | Format | Examples |
|------|--------|---------|
| Character name | kebab-case (lowercase, hyphens) | `el-condor-pasa`, `special-week`, `tokai-teio` |
| Scenario | kebab-case matching folder name | `trackblazer`, `unity-cup`, `ura-finale` |

The script converts the character name from kebab-case to Title Case:
- `el-condor-pasa` becomes `El Condor Pasa`
- `sakura-bakushin-o` becomes `Sakura Bakushin O`
- `matikanefukukitaru` becomes `Matikanefukukitaru`

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
android/app/build/outputs/apk/debug/UMA-Auto-Plus-v1.3.8-arm64-v8a-debug.apk
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
- Verify the `scenario` field exactly matches one of: `"Trackblazer"`, `"Unity Cup"`, `"URA Finale"` (case-sensitive).
- Verify the preset was added to the `characterPresets` array in `characterPresets.ts`.
- Rebuild the JS bundle and APK after changes.

### Preset applies wrong settings
- Check that the correct scenario variant is selected (each character has 3 separate presets).
- Verify the JSON source file was exported while the correct scenario was active.

### Character name displays incorrectly
- The name comes from the `name` field in the preset object.
- If using the extraction script, the name is derived from the filename. Use proper kebab-case: `my-character-name-profile-trackblazer.json`.
