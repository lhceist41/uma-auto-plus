#!/usr/bin/env node
// Applies three targeted tuning passes to the character presets:
//
//   Change A — per-character `training.focusOnSparkStatTarget`
//     Matches each character's build so the +2.5× spark bonus in
//     Training.calculateStatEfficiencyScore fires on stats that actually matter
//     for them. Previously identical ["Speed","Stamina","Power"] for all 17.
//
//   Change B — per-character `scenarioOverrides.trackblazerExcludedItems`
//     Blacklists Stat-Scroll/Manual items for stats the character doesn't care
//     about (target ≤ threshold). TrackblazerShopList filters the shop by this
//     list before buying — prevents wasting coins on e.g., Wit Scrolls for
//     Gold Ship or Stamina Scrolls for Sakura Bakushin O.
//
//   Change C — Agnes Tachyon `training.statPrioritization` fix
//     Swaps Wit→Power at rank 2. Community meta places Power #2 for Pace
//     Chaser Medium builds, not Wit.
//
// All three changes only touch preset data — no Kotlin changes. Applies to
// all 51 preset entries (17 characters × 3 scenarios), keyed by the preset
// display name so it covers every scenario variant for each character.

const fs = require("fs")
const path = require("path")

const PRESET_FILE = path.resolve(__dirname, "..", "src/data/characterPresets.ts")

// Per-character focusOnSparkStatTarget (which stats to prioritize for 3* sparks).
// Key = preset display name (matches the `name` field in characterPresets.ts).
const SPARK_TARGETS = {
    "Agnes Tachyon": ["Speed", "Power", "Stamina"],
    "Air Groove": ["Speed", "Power", "Stamina"],
    "Daiwa Scarlet": ["Speed", "Power", "Stamina"],
    "El Condor Pasa": ["Speed", "Power", "Stamina"],
    "Gold City (Autumn Cosmos)": ["Speed", "Power", "Stamina"],
    "Gold Ship": ["Speed", "Stamina", "Guts"], // End Closer needs Guts for final burst
    "Grass Wonder": ["Speed", "Power", "Stamina"],
    "Haru Urara": ["Speed", "Power"], // Sprint — no Stamina spark needed
    "Hishi Amazon": ["Speed", "Power", "Stamina"],
    "King Halo": ["Speed", "Power", "Stamina"],
    "Maruzensky (Formula R)": ["Speed", "Power", "Stamina"],
    Matikanefukukitaru: ["Speed", "Stamina", "Power"],
    "Mayano Top Gun": ["Speed", "Stamina", "Power"],
    "Nice Nature": ["Speed", "Power", "Stamina"],
    "Sakura Bakushin O": ["Speed", "Power"], // Sprint — no Stamina spark needed
    "Taiki Shuttle": ["Speed", "Power", "Stamina"],
    Vodka: ["Speed", "Power", "Stamina"],
}

// Per-character trackblazerExcludedItems. Based on each character's stat
// targets: exclude Scroll + Manual items for stats with target ≤ threshold
// where the bot would otherwise be wasting shop coins.
//   - Guts ≤ 350 → exclude Guts Scroll + Manual
//   - Wit ≤ 450 → exclude Wit Scroll + Manual
//   - Stamina ≤ 450 → exclude Stamina Scroll + Manual
//   - Power never excluded (all characters ≥ 550)
//   - Speed never excluded
const SHOP_EXCLUSIONS = {
    "Agnes Tachyon": ["Guts Scroll", "Guts Manual"],
    "Air Groove": ["Guts Scroll", "Guts Manual"],
    "Daiwa Scarlet": ["Guts Scroll", "Guts Manual"],
    "El Condor Pasa": ["Guts Scroll", "Guts Manual"],
    "Gold City (Autumn Cosmos)": ["Guts Scroll", "Guts Manual"],
    "Gold Ship": ["Wit Scroll", "Wit Manual"],
    "Grass Wonder": [],
    "Haru Urara": ["Stamina Scroll", "Stamina Manual", "Wit Scroll", "Wit Manual"],
    "Hishi Amazon": ["Wit Scroll", "Wit Manual"],
    "King Halo": [],
    "Maruzensky (Formula R)": ["Guts Scroll", "Guts Manual"],
    Matikanefukukitaru: ["Guts Scroll", "Guts Manual", "Wit Scroll", "Wit Manual"],
    "Mayano Top Gun": ["Guts Scroll", "Guts Manual"],
    "Nice Nature": ["Guts Scroll", "Guts Manual", "Wit Scroll", "Wit Manual"],
    "Sakura Bakushin O": ["Stamina Scroll", "Stamina Manual", "Guts Scroll", "Guts Manual"],
    "Taiki Shuttle": ["Stamina Scroll", "Stamina Manual", "Guts Scroll", "Guts Manual"],
    Vodka: ["Guts Scroll", "Guts Manual"],
}

// Change C — surgical fix to Agnes Tachyon's statPrioritization (Wit→Power swap
// at rank 2, matching community meta for Medium Pace Chaser builds).
const STAT_PRIORITIZATION_FIXES = {
    "Agnes Tachyon": ["Speed", "Power", "Stamina", "Wit", "Guts"],
}

function applyUpdates(presets) {
    let sparkUpdates = 0
    let shopUpdates = 0
    let prioFixes = 0

    for (const entry of presets) {
        const name = entry.name
        const s = entry.settings
        if (!s) continue

        // Change A — focusOnSparkStatTarget
        const sparks = SPARK_TARGETS[name]
        if (sparks && s.training) {
            s.training.focusOnSparkStatTarget = sparks
            sparkUpdates++
        }

        // Change B — trackblazerExcludedItems (applies to all scenarios since
        // trackblazer* keys are only honored on the Trackblazer scenario, but
        // it's harmless to set them on URA/Unity presets too).
        const exclusions = SHOP_EXCLUSIONS[name]
        if (exclusions !== undefined && s.scenarioOverrides) {
            s.scenarioOverrides.trackblazerExcludedItems = exclusions
            shopUpdates++
        }

        // Change C — statPrioritization fix (only Agnes Tachyon)
        const prioFix = STAT_PRIORITIZATION_FIXES[name]
        if (prioFix && s.training) {
            s.training.statPrioritization = prioFix
            prioFixes++
        }
    }

    return { sparkUpdates, shopUpdates, prioFixes }
}

function main() {
    console.log("Reading preset file...")
    const raw = fs.readFileSync(PRESET_FILE, "utf8")
    const declIdx = raw.indexOf("export const characterPresets")
    const eqIdx = raw.indexOf("=", declIdx)
    const arrayStart = raw.indexOf("[", eqIdx)
    const arrayEnd = raw.lastIndexOf("]")
    const header = raw.slice(0, arrayStart)
    const footer = raw.slice(arrayEnd + 1)
    const presets = JSON.parse(raw.slice(arrayStart, arrayEnd + 1))
    console.log(`  Loaded ${presets.length} preset entries\n`)

    console.log("Applying updates...")
    const { sparkUpdates, shopUpdates, prioFixes } = applyUpdates(presets)
    console.log(`  Change A (focusOnSparkStatTarget):        ${sparkUpdates} presets updated`)
    console.log(`  Change B (trackblazerExcludedItems):      ${shopUpdates} presets updated`)
    console.log(`  Change C (statPrioritization Agnes fix):  ${prioFixes} presets updated`)

    console.log("\nWriting preset file...")
    const serialized = JSON.stringify(presets, null, 4)
    fs.writeFileSync(PRESET_FILE, header + serialized + footer, "utf8")
    console.log(`  Wrote ${PRESET_FILE}`)
}

main()
