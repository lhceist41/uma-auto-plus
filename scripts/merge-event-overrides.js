#!/usr/bin/env node
// Merges the per-batch research JSON files produced by the event-choice research
// agents into characterEventOverrides entries on every preset.
//
// Input:  C:\temp\uma-research\batch1-results.json .. batch4-results.json
// Output: src/data/characterPresets.ts (in-place update, same JSON-array-in-TS
//         wrapper pattern the Tier-1 script used)
//
// The research agents output keyed by the in-game character name (e.g. "Gold
// City", not "Gold City (Autumn Cosmos)"), because that's what the bot's OCR
// emits and what `characterEventOverrides` is keyed on. We apply each
// character's overrides to all 3 scenario presets for that character.

const fs = require("fs")
const path = require("path")

const RESEARCH_DIR = "C:\\temp\\uma-research"
const PRESET_FILE = path.resolve(__dirname, "..", "src/data/characterPresets.ts")

// Map from preset.name (as it appears in characterPresets.ts) to the in-game
// character name used as the OCR-side key. Most are identical; outfit-variants
// are not.
const PRESET_TO_INGAME = {
    "Agnes Tachyon": "Agnes Tachyon",
    "Air Groove": "Air Groove",
    "Daiwa Scarlet": "Daiwa Scarlet",
    "El Condor Pasa": "El Condor Pasa",
    "Gold City (Autumn Cosmos)": "Gold City",
    "Gold Ship": "Gold Ship",
    "Grass Wonder": "Grass Wonder",
    "Haru Urara": "Haru Urara",
    "Hishi Amazon": "Hishi Amazon",
    "King Halo": "King Halo",
    "Maruzensky (Formula R)": "Maruzensky",
    Matikanefukukitaru: "Matikanefukukitaru",
    "Mayano Top Gun": "Mayano Top Gun",
    "Nice Nature": "Nice Nature",
    "Sakura Bakushin O": "Sakura Bakushin O",
    "Taiki Shuttle": "Taiki Shuttle",
    Vodka: "Vodka",
}

function loadBatchResults() {
    const combined = {}
    for (let i = 1; i <= 4; i++) {
        const file = path.join(RESEARCH_DIR, `batch${i}-results.json`)
        if (!fs.existsSync(file)) {
            console.warn(`  [warn] ${file} missing — skipping`)
            continue
        }
        try {
            const contents = JSON.parse(fs.readFileSync(file, "utf8"))
            for (const [char, overrides] of Object.entries(contents)) {
                if (combined[char]) {
                    console.warn(`  [warn] duplicate character key ${char} — later wins`)
                }
                combined[char] = overrides
            }
            const eventCount = Object.values(contents).reduce((a, o) => a + Object.keys(o).length, 0)
            console.log(`  Loaded batch${i}: ${Object.keys(contents).length} chars, ${eventCount} events`)
        } catch (e) {
            console.error(`  [error] failed to parse batch${i}: ${e.message}`)
            process.exit(1)
        }
    }
    return combined
}

function applyOverrides(presets, research) {
    let totalWritten = 0
    let charsUpdated = 0
    for (const entry of presets) {
        const ingameName = PRESET_TO_INGAME[entry.name]
        if (!ingameName) continue // not one of our target characters
        const overrides = research[ingameName]
        if (!overrides || Object.keys(overrides).length === 0) {
            console.warn(`  [warn] no research data for ${entry.name} (ingame=${ingameName})`)
            continue
        }

        // Convert { eventName: optionIdx } to { "<char>|<eventName>": optionIdx }
        // (see TrainingEvent.kt's checkCharacterEventOverride — key format is
        // "$characterName|$eventTitle")
        const coverrideMap = {}
        for (const [evName, optIdx] of Object.entries(overrides)) {
            if (typeof optIdx !== "number" || !Number.isInteger(optIdx) || optIdx < 0) {
                console.warn(`    [warn] ${entry.name} / ${evName}: invalid index ${optIdx} — skipping`)
                continue
            }
            coverrideMap[`${ingameName}|${evName}`] = optIdx
            totalWritten++
        }

        if (!entry.settings.trainingEvent) entry.settings.trainingEvent = {}
        entry.settings.trainingEvent.characterEventOverrides = coverrideMap
        charsUpdated++
    }
    return { totalWritten, charsUpdated }
}

function main() {
    console.log("Loading batch results...")
    const research = loadBatchResults()
    console.log(`\nTotal: ${Object.keys(research).length} characters with research data\n`)

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

    console.log("Applying overrides...")
    const { totalWritten, charsUpdated } = applyOverrides(presets, research)
    console.log(`\n  Updated ${charsUpdated} preset entries with ${totalWritten} total event overrides\n`)

    console.log("Writing preset file...")
    const serialized = JSON.stringify(presets, null, 4)
    fs.writeFileSync(PRESET_FILE, header + serialized + footer, "utf8")
    console.log(`  Wrote ${PRESET_FILE}`)
}

main()
