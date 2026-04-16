#!/usr/bin/env node
// One-shot script to fix character preset format bugs + update stat targets
// based on community-meta research. See commit message for details.
//
// Run: node scripts/update-character-presets.js
//
// The script:
// 1. Reads src/data/characterPresets.ts
// 2. Extracts the JSON array portion (between the first `[` after `=` and the last `]`)
// 3. Applies format normalization to skills.preferred* fields
// 4. Applies per-character updates (style/distance/surface + primary-distance stat targets)
// 5. Writes the file back with original header preserved

const fs = require("fs")
const path = require("path")

const PRESET_FILE = path.resolve(__dirname, "..", "src/data/characterPresets.ts")

// Community-meta research table. Each entry: the character's A-aptitude primary
// distance + best running style + best surface + endgame stat targets for that
// distance. Sources: game8.co, pcgamer, gamerant, gamerblurb, steamcommunity,
// goonhammer (see research notes).
const RESEARCH = {
    "Agnes Tachyon": {
        distance: "medium",
        style: "pace_chaser",
        surface: "turf",
        speed: 1100,
        stamina: 550,
        power: 600,
        guts: 350,
        wit: 500,
    },
    "Air Groove": {
        // A-rank Pace Chaser AND Late Surger — community guides split, but Pace
        // Chaser is the safer default for her unique skill. Keeping current choice.
        distance: "medium",
        style: "pace_chaser",
        surface: "turf",
        speed: 1000,
        stamina: 650,
        power: 700,
        guts: 350,
        wit: 500,
    },
    "Daiwa Scarlet": {
        distance: "mile",
        style: "front_runner",
        surface: "turf",
        speed: 1200,
        stamina: 550,
        power: 600,
        guts: 350,
        wit: 500,
    },
    "El Condor Pasa": {
        distance: "mile",
        style: "pace_chaser",
        surface: "turf",
        speed: 1100,
        stamina: 600,
        power: 700,
        guts: 350,
        wit: 550,
    },
    "Gold City (Autumn Cosmos)": {
        // Mile specialist per community meta; unique skill needs Pace Chaser.
        distance: "mile",
        style: "pace_chaser",
        surface: "turf",
        speed: 1100,
        stamina: 550,
        power: 650,
        guts: 350,
        wit: 550,
    },
    "Gold Ship": {
        distance: "long",
        style: "end_closer",
        surface: "turf",
        speed: 1000,
        stamina: 900,
        power: 700,
        guts: 500,
        wit: 450,
    },
    "Grass Wonder": {
        distance: "medium",
        style: "late_surger",
        surface: "turf",
        speed: 1100,
        stamina: 600,
        power: 700,
        guts: 400,
        wit: 500,
    },
    "Haru Urara": {
        // Canonically bad; Dirt Sprint is her only A-rank combo.
        distance: "sprint",
        style: "late_surger",
        surface: "dirt",
        speed: 900,
        stamina: 400,
        power: 700,
        guts: 400,
        wit: 450,
    },
    "Hishi Amazon": {
        distance: "medium",
        style: "end_closer",
        surface: "turf",
        speed: 1000,
        stamina: 600,
        power: 750,
        guts: 400,
        wit: 450,
    },
    "King Halo": {
        // A-rank in Sprint/Mile/Medium. Current preset uses Mile which is valid;
        // research prefers Sprint for higher win-rate. Going with Mile for the
        // more race-rich distance pool — more chances for the bot to fit in extras.
        distance: "mile",
        style: "late_surger",
        surface: "turf",
        speed: 1000,
        stamina: 550,
        power: 800,
        guts: 400,
        wit: 500,
    },
    "Maruzensky (Formula R)": {
        distance: "mile",
        style: "front_runner",
        surface: "turf",
        speed: 1200,
        stamina: 550,
        power: 550,
        guts: 350,
        wit: 550,
    },
    "Matikanefukukitaru": {
        // Community meta: Long specialist despite being tagged as Medium by some
        // guides. Her A-rank distances are Medium AND Long; pushing Long per research.
        distance: "long",
        style: "late_surger",
        surface: "turf",
        speed: 1000,
        stamina: 800,
        power: 550,
        guts: 350,
        wit: 450,
    },
    "Mayano Top Gun": {
        distance: "long",
        style: "pace_chaser",
        surface: "turf",
        speed: 1000,
        stamina: 800,
        power: 600,
        guts: 350,
        wit: 500,
    },
    "Nice Nature": {
        distance: "medium",
        style: "late_surger",
        surface: "turf",
        speed: 900,
        stamina: 650,
        power: 750,
        guts: 350,
        wit: 450,
    },
    "Sakura Bakushin O": {
        distance: "sprint",
        style: "front_runner",
        surface: "turf",
        speed: 1200,
        stamina: 400,
        power: 550,
        guts: 350,
        wit: 550,
    },
    "Taiki Shuttle": {
        distance: "mile",
        style: "pace_chaser",
        surface: "turf",
        speed: 1200,
        stamina: 450,
        power: 600,
        guts: 350,
        wit: 550,
    },
    Vodka: {
        distance: "mile",
        style: "late_surger",
        surface: "turf",
        speed: 1100,
        stamina: 500,
        power: 700,
        guts: 350,
        wit: 500,
    },
}

// Map our lowercase-snake distance to the key prefix used in trainingStatTarget
const DISTANCE_KEY_PREFIX = {
    sprint: "trainingSprintStatTarget",
    mile: "trainingMileStatTarget",
    medium: "trainingMediumStatTarget",
    long: "trainingLongStatTarget",
}

// Map lowercase-snake distance back to the capitalized form used in the racing
// section's preferredDistances array. Note: racing uses "Short" for sprint.
const DISTANCE_TO_RACING_LABEL = {
    sprint: "Short",
    mile: "Mile",
    medium: "Medium",
    long: "Long",
}

function applyUpdates(presets) {
    let changed = 0
    for (const entry of presets) {
        const research = RESEARCH[entry.name]
        if (!research) {
            console.warn(`  ! No research entry for '${entry.name}' — leaving alone`)
            continue
        }

        const s = entry.settings
        if (!s) continue

        // --- skills section: lowercase snake_case (matches UI dropdowns + RunningStyle.fromName)
        if (s.skills) {
            s.skills.preferredRunningStyle = research.style
            s.skills.preferredTrackDistance = research.distance
            s.skills.preferredTrackSurface = research.surface
        }

        // --- racing section: capitalized labels
        if (s.racing) {
            // preferredTerrain: capitalized (Turf / Dirt)
            s.racing.preferredTerrain = research.surface === "turf" ? "Turf" : "Dirt"
            // preferredDistances: array of capitalized labels, primary first
            const primary = DISTANCE_TO_RACING_LABEL[research.distance]
            const secondaries = ["Short", "Mile", "Medium", "Long"].filter((d) => d !== primary)
            s.racing.preferredDistances = [primary, ...secondaries]
        }

        // --- trainingStatTarget: update ONLY the primary distance. Other distances
        // are fallbacks if the user changes preferredTrackDistance later; leaving
        // them alone preserves the current behavior for those edge cases.
        if (s.trainingStatTarget) {
            const prefix = DISTANCE_KEY_PREFIX[research.distance]
            if (prefix) {
                s.trainingStatTarget[`${prefix}_speedStatTarget`] = research.speed
                s.trainingStatTarget[`${prefix}_staminaStatTarget`] = research.stamina
                s.trainingStatTarget[`${prefix}_powerStatTarget`] = research.power
                s.trainingStatTarget[`${prefix}_gutsStatTarget`] = research.guts
                s.trainingStatTarget[`${prefix}_witStatTarget`] = research.wit
            }
        }

        changed++
    }
    return changed
}

function main() {
    const raw = fs.readFileSync(PRESET_FILE, "utf8")

    // Extract JSON array: everything from the first `[` AFTER the `=` up to the last `]`.
    // (Avoid matching the `[` inside the `CharacterPreset[]` type annotation.)
    const declIdx = raw.indexOf("export const characterPresets")
    const eqIdx = raw.indexOf("=", declIdx)
    const arrayStart = raw.indexOf("[", eqIdx)
    const arrayEnd = raw.lastIndexOf("]")
    if (arrayStart < 0 || arrayEnd < 0 || arrayEnd <= arrayStart) {
        console.error("Could not locate the JSON array in the preset file.")
        process.exit(1)
    }

    const header = raw.slice(0, arrayStart)
    const footer = raw.slice(arrayEnd + 1) // anything after the closing ]
    const arrayText = raw.slice(arrayStart, arrayEnd + 1)

    let presets
    try {
        presets = JSON.parse(arrayText)
    } catch (e) {
        console.error("JSON.parse failed:", e.message)
        process.exit(1)
    }

    console.log(`Loaded ${presets.length} preset entries`)

    const changed = applyUpdates(presets)
    console.log(`Updated ${changed} entries`)

    // Serialize back. Preserve 4-space indent (matches the rest of the file's
    // style as seen in git log/diffs on this project).
    const serialized = JSON.stringify(presets, null, 4)
    // Re-indent by 0 levels (top of file — already flush-left). The wrapper
    // `export const ... = ` ends with a newline so the array starts at column 0.
    const newContent = header + serialized + footer
    fs.writeFileSync(PRESET_FILE, newContent, "utf8")
    console.log(`Wrote ${PRESET_FILE}`)
}

main()
