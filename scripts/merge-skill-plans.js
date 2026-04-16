#!/usr/bin/env node
// Merges per-character skill priority lists (produced by the skill-research
// agents) into the `plans.preFinals.plan` and `plans.careerComplete.plan`
// fields on every preset.
//
// Input:  C:\temp\uma-research\skills-batch{1..4}.json
//         Format: { "CharacterName": ["Skill Name 1", "Skill Name 2", ...] }
// Output: src/data/characterPresets.ts (in-place update)
//
// The `plan` field in each preset's plans is a comma-separated list of skill
// IDs (integers) — see SkillPlan.kt line 51-56. We resolve each skill name
// via skills.json and emit an ordered comma-separated ID string.
//
// If a research agent outputs a skill name that doesn't exist in skills.json,
// it gets flagged + dropped from the plan (we never want to inject garbage that
// the bot would silently drop anyway).

const fs = require("fs")
const path = require("path")

const RESEARCH_DIR = "C:\\temp\\uma-research"
const PRESET_FILE = path.resolve(__dirname, "..", "src/data/characterPresets.ts")
const SKILLS_FILE = path.resolve(__dirname, "..", "src/data/skills.json")

// Maps preset.name → in-game key used by the research agents (matches the
// PRESET_TO_INGAME table from the event-overrides merge script).
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

function buildSkillLookup() {
    const skills = JSON.parse(fs.readFileSync(SKILLS_FILE, "utf8"))
    const nameToId = {}
    const lowerToName = {}
    for (const [name, sk] of Object.entries(skills)) {
        nameToId[name] = sk.id
        lowerToName[name.toLowerCase().trim()] = name
    }
    return { nameToId, lowerToName }
}

function resolveSkills(skillNames, lookup, character) {
    const resolved = []
    const unresolved = []
    for (const name of skillNames) {
        if (lookup.nameToId[name] !== undefined) {
            resolved.push(lookup.nameToId[name])
            continue
        }
        // Try case-insensitive match as a mild fuzzy fallback
        const canonicalName = lookup.lowerToName[name.toLowerCase().trim()]
        if (canonicalName && lookup.nameToId[canonicalName] !== undefined) {
            resolved.push(lookup.nameToId[canonicalName])
            continue
        }
        unresolved.push(name)
    }
    if (unresolved.length > 0) {
        console.warn(`  [unresolved for ${character}] ${unresolved.length} skill(s): ${unresolved.slice(0, 3).map((n) => JSON.stringify(n)).join(", ")}${unresolved.length > 3 ? "..." : ""}`)
    }
    return { resolved, unresolved }
}

function loadBatches() {
    const combined = {}
    for (let i = 1; i <= 4; i++) {
        const file = path.join(RESEARCH_DIR, `skills-batch${i}.json`)
        if (!fs.existsSync(file)) {
            console.warn(`  [warn] ${file} missing`)
            continue
        }
        try {
            const data = JSON.parse(fs.readFileSync(file, "utf8"))
            for (const [char, list] of Object.entries(data)) {
                if (combined[char]) console.warn(`  [warn] duplicate ${char} — later wins`)
                combined[char] = list
            }
            const skillCount = Object.values(data).reduce((a, l) => a + l.length, 0)
            console.log(`  Loaded skills-batch${i}: ${Object.keys(data).length} chars, ${skillCount} skill entries`)
        } catch (e) {
            console.error(`  [error] parsing ${file}: ${e.message}`)
            process.exit(1)
        }
    }
    return combined
}

function main() {
    console.log("Loading skills database lookup...")
    const lookup = buildSkillLookup()
    console.log(`  Indexed ${Object.keys(lookup.nameToId).length} skill names\n`)

    console.log("Loading batch results...")
    const research = loadBatches()
    console.log(`\nTotal: ${Object.keys(research).length} characters with research data\n`)

    console.log("Resolving skill names → IDs...")
    const resolvedByChar = {}
    let totalResolved = 0
    let totalUnresolved = 0
    for (const [char, names] of Object.entries(research)) {
        const { resolved, unresolved } = resolveSkills(names, lookup, char)
        resolvedByChar[char] = resolved
        totalResolved += resolved.length
        totalUnresolved += unresolved.length
        console.log(`  ${char.padEnd(30)} ${resolved.length} resolved / ${names.length} requested`)
    }
    console.log(`\n  Total: ${totalResolved} resolved, ${totalUnresolved} unresolved (dropped)`)

    console.log("\nReading preset file...")
    const raw = fs.readFileSync(PRESET_FILE, "utf8")
    const declIdx = raw.indexOf("export const characterPresets")
    const eqIdx = raw.indexOf("=", declIdx)
    const arrayStart = raw.indexOf("[", eqIdx)
    const arrayEnd = raw.lastIndexOf("]")
    const header = raw.slice(0, arrayStart)
    const footer = raw.slice(arrayEnd + 1)
    const presets = JSON.parse(raw.slice(arrayStart, arrayEnd + 1))
    console.log(`  Loaded ${presets.length} preset entries`)

    console.log("\nApplying skill plans to presets...")
    let updated = 0
    for (const entry of presets) {
        const ingame = PRESET_TO_INGAME[entry.name]
        if (!ingame) continue
        const ids = resolvedByChar[ingame]
        if (!ids || ids.length === 0) {
            console.warn(`  [warn] no resolved skills for ${entry.name} (ingame=${ingame})`)
            continue
        }
        const planString = ids.join(",")
        // Apply to BOTH preFinals and careerComplete — both use optimize_skills,
        // both should benefit from the user-planned priority list.
        const plans = entry.settings.skills?.plans
        if (!plans) continue
        if (plans.preFinals) plans.preFinals.plan = planString
        if (plans.careerComplete) plans.careerComplete.plan = planString
        updated++
    }
    console.log(`  Updated plans on ${updated} preset entries\n`)

    console.log("Writing preset file...")
    const serialized = JSON.stringify(presets, null, 4)
    fs.writeFileSync(PRESET_FILE, header + serialized + footer, "utf8")
    console.log(`  Wrote ${PRESET_FILE}`)
}

main()
