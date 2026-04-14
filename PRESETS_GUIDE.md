# Built-in Character Presets Guide

This document explains how built-in character presets work in UMA Auto+, how they are structured, and how to create new presets for additional characters.

---

## Overview

UMA Auto+ ships with **45 built-in character presets** (15 characters x 3 scenarios). When a user selects a scenario on the Home page, a dropdown appears showing only the characters available for that scenario. Picking a character instantly applies all of its optimized settings.

### Currently included characters

| Character | Trackblazer | Unity Cup | URA Finale |
|-----------|:-----------:|:---------:|:----------:|
| Agnes Tachyon | Yes | Yes | Yes |
| Air Groove | Yes | Yes | Yes |
| Daiwa Scarlet | Yes | Yes | Yes |
| El Condor Pasa | Yes | Yes | Yes |
| Gold Ship | Yes | Yes | Yes |
| Grass Wonder | Yes | Yes | Yes |
| Haru Urara | Yes | Yes | Yes |
| Hishi Amazon | Yes | Yes | Yes |
| King Halo | Yes | Yes | Yes |
| Matikanefukukitaru | Yes | Yes | Yes |
| Mayano Top Gun | Yes | Yes | Yes |
| Nice Nature | Yes | Yes | Yes |
| Sakura Bakushin O | Yes | Yes | Yes |
| Taiki Shuttle | Yes | Yes | Yes |
| Vodka | Yes | Yes | Yes |

---

## How Presets Work at Runtime

### User flow

1. User opens UMA Auto+ and selects a **scenario** (e.g., "Trackblazer") on the Home page.
2. A **character preset dropdown** appears below the scenario selector, filtered to show only presets for that scenario (15 characters).
3. User picks a character (e.g., "El Condor Pasa").
4. The preset's full settings are **deep-merged** into the current app configuration, overwriting every settings category.
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
- `profiles` array -- user-created profiles are separate from built-in presets

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
    settings: Partial<Settings>  // Full settings object
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

4. **Place the file** in the appropriate scenario folder:
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
output += '    settings: Partial<Settings>\n'
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

The output APK will be at:
```
android/app/build/outputs/apk/debug/v5.4.8-UmaAndroidAutomation-arm64-v8a-debug.apk
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
