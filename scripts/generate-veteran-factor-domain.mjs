// Generates the canonical Veteran factor-name domain the native Inspiration/lineage reader snaps its
// noisy factor-card OCR onto, so a factor read twice yields the SAME canonical name and therefore the
// SAME fingerprint. It is a deterministic function of the committed authoritative data
// (src/data/skills.json + races.json + scenarios.json), NOT of any live source.
//
// Why this exists: factor cards show a skill name with its grade glyph ("Firm Conditions ○"), a race
// name, a scenario name, a stat, or an aptitude. OCR drops or mangles the glyph and mis-reads letters
// ("FIRM CONDITIONSO", "LONG COMERS"), so a fingerprint built straight off the raw read flips between
// values for unchanged evidence (~3.5% of names differed across two same-build passes). Snapping the
// read onto the nearest known canonical name recovers the exact identity string the fingerprint needs.
//
// Families and their sources:
//   stat      - the five training stats (authoritative: scenarios.json stat gains)
//   aptitude  - surface (races.json terrain), distance (races.json distanceType), running style
//               (skills.json "<Style> Corners/Savvy" prefixes). Ten fixed aptitude factor names.
//   unique    - character unique skills: skills.json entries whose id begins with "1". Base name, with
//               its (rare) tier glyph stripped; deduplicated.
//   skill     - every other skill (id not starting with "1"). Base name with the trailing tier glyph
//               (× / ○ / ◎) stripped, so "Firm Conditions ×/○/◎" collapse to one canonical name that
//               matches the glyph-less OCR read; deduplicated.
//   race      - race names (races.json). A card that truncates a long race name ("Mile Ch.") simply
//               fails to resolve fail-closed; the full names still resolve.
//   scenario  - playable scenario names (scenarios.json keys) PLUS legacy non-playable scenario
//               spark names a Veteran can still carry (LEGACY_SCENARIO_FACTORS). scenarios.json is the
//               bot's playable-scenario table (it also feeds the master-data compiler and the UI), so
//               a historical scenario that only ever shows up as an ancestry spark is added here, not
//               there.
//
// The native resolver conditions on the pixel-classified row kind: STAT->stat, APTITUDE->aptitude,
// UNIQUE->unique, WHITE->{skill,race,scenario}. It computes each candidate's normalized skeleton
// itself (the SAME normalization it applies to the OCR read), so this asset carries only canonical
// display names, never skeletons.
//
// Usage:
//   node scripts/generate-veteran-factor-domain.mjs            # write the asset
//   node scripts/generate-veteran-factor-domain.mjs --check    # verify the committed asset is current (exit 1 if stale)
//
// Determinism: families sorted by UTF-16 code unit, fixed top-level key order, two-space JSON,
// trailing newline. No timestamps, no absolute paths, no network.

import { readFileSync, writeFileSync, renameSync } from "node:fs"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, join } from "node:path"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const SKILLS_PATH = join(REPO, "src", "data", "skills.json")
const RACES_PATH = join(REPO, "src", "data", "races.json")
const SCENARIOS_PATH = join(REPO, "src", "data", "scenarios.json")
const ASSET_PATH = join(REPO, "android", "app", "src", "main", "assets", "veteran_factor_domain.json")

/** Bumped only when the native reader's expected shape changes. */
const SCHEMA_VERSION = 1

const SOURCE = "src/data skills.json (base names, tier glyphs stripped) + races.json + scenarios.json + legacy scenario spark names"

/** Legacy / non-playable scenario spark names a Veteran can still carry from a past career, which are
 * NOT in the playable scenarios.json table. "TS Climax Scenario" is the Twinkle Series Climax scenario:
 * the bot never plays it, but veterans trained under it show it as a scenario factor, and the domain
 * must canonicalize that read instead of failing it closed. Kept here (provenance in the generator,
 * generated into the asset, guarded by --check) rather than in scenarios.json, which would leak a
 * non-playable scenario into the master-data compiler and the training-event UI. */
const LEGACY_SCENARIO_FACTORS = ["TS Climax Scenario"]

/** The five training stat factor names, as scenarios.json spends them. */
const STATS = ["Speed", "Stamina", "Power", "Guts", "Wit"]

/** The ten aptitude factor names: two surfaces, four distances, four running styles. Every string is
 * present in the committed data (races.json terrain/distanceType, skills.json style-skill prefixes);
 * they are fixed here because the aptitude cards themselves are not a data table. */
const APTITUDES = ["Turf", "Dirt", "Sprint", "Mile", "Medium", "Long", "Front Runner", "Pace Chaser", "Late Surger", "End Closer"]

/** A trailing skill grade-tier marker (× / ○ / ◎), optionally spaced. Stripped so the graded variants
 * of one skill collapse onto the single base name the glyph-less factor card shows. The decorative ☆
 * inside some unique names ("Corazón ☆ Ardiente") is NOT a tier marker and is deliberately left alone. */
const TIER_MARKER = /\s*[×○◎]+\s*$/u

class GenerateError extends Error {
    constructor(message) {
        super(message)
        this.name = "GenerateError"
    }
}

/** The U+2014 em dash, built from its code point so this source stays free of the character itself. A
 * few unique skill names carry one (e.g. "Sunrise Banner", an em dash, then "Katsuragi Ace!"). */
const EM_DASH = String.fromCharCode(0x2014)

/** Strips the trailing tier glyph, collapses whitespace, and folds the U+2014 em dash to "--". The em
 * dash is banned from committed content, and the fold is identity-safe: the native resolver matches on
 * a skeleton that drops all punctuation, so the em dash and "--" reduce to the same thing. */
export function factorBaseName(name) {
    return name.replaceAll(EM_DASH, "--").replace(TIER_MARKER, "").replace(/\s+/g, " ").trim()
}

/** Deduplicates by exact string, preserving first-seen order, then sorts by UTF-16 code unit. */
function dedupeSorted(names) {
    const seen = new Set()
    const out = []
    for (const raw of names) {
        const name = factorBaseName(raw)
        if (!name) throw new GenerateError(`empty factor name derived from ${JSON.stringify(raw)}`)
        if (seen.has(name)) continue
        seen.add(name)
        out.push(name)
    }
    return out.sort()
}

/**
 * Builds the asset payload from the three committed data files.
 *
 * @param {Array<{id:number,name_en?:string}>} skills The parsed skills.json array.
 * @param {Array<{name?:string}>} races The parsed races.json array.
 * @param {Record<string, unknown>} scenarios The parsed scenarios.json object (keys are the names).
 * @returns {object} The payload, ready to serialize.
 */
export function buildPayload(skills, races, scenarios) {
    if (!Array.isArray(skills) || skills.length === 0) throw new GenerateError("skills.json is empty or not an array")
    if (!Array.isArray(races) || races.length === 0) throw new GenerateError("races.json is empty or not an array")

    // A unique skill's id begins with "1" (100101 Shooting for Victory!, 10241 1st Place Kiss☆,
    // 1100011 an evolved unique); every other skill id begins with "2" or "3".
    const isUnique = (id) => String(id).startsWith("1")

    const uniqueNames = []
    const skillNames = []
    for (const s of skills) {
        const name = typeof s?.name_en === "string" ? s.name_en.trim() : ""
        if (!name) continue
        if (s.id == null) throw new GenerateError(`skill ${JSON.stringify(name)} has no id`)
        ;(isUnique(s.id) ? uniqueNames : skillNames).push(name)
    }

    const raceNames = []
    for (const r of races) {
        const name = typeof r?.name === "string" ? r.name.trim() : ""
        if (name) raceNames.push(name)
    }

    const scenarioNames = Object.keys(scenarios).map((k) => k.trim()).filter(Boolean)

    const families = {
        stat: [...STATS].sort(),
        aptitude: [...APTITUDES].sort(),
        unique: dedupeSorted(uniqueNames),
        skill: dedupeSorted(skillNames),
        race: dedupeSorted(raceNames),
        scenario: dedupeSorted([...scenarioNames, ...LEGACY_SCENARIO_FACTORS]),
    }

    for (const [family, list] of Object.entries(families)) {
        if (list.length === 0) throw new GenerateError(`family "${family}" is empty`)
    }

    const counts = Object.fromEntries(Object.entries(families).map(([k, v]) => [k, v.length]))
    return { schemaVersion: SCHEMA_VERSION, source: SOURCE, counts, families }
}

/** skills.json and races.json are objects keyed by name; the payload builder wants the value rows. */
const asRows = (data) => (Array.isArray(data) ? data : Object.values(data))

/** Reads the committed inputs and returns the asset text, trailing newline included. */
export function generate() {
    const skills = asRows(JSON.parse(readFileSync(SKILLS_PATH, "utf8")))
    const races = asRows(JSON.parse(readFileSync(RACES_PATH, "utf8")))
    const scenarios = JSON.parse(readFileSync(SCENARIOS_PATH, "utf8"))
    return `${JSON.stringify(buildPayload(skills, races, scenarios), null, 2)}\n`
}

function main(argv) {
    const check = argv.includes("--check")
    const text = generate()
    if (check) {
        let current = null
        try {
            current = readFileSync(ASSET_PATH, "utf8")
        } catch {
            console.error(`veteran_factor_domain.json missing at ${ASSET_PATH}; run without --check to generate it`)
            return 1
        }
        if (current !== text) {
            console.error("veteran_factor_domain.json is stale; re-run scripts/generate-veteran-factor-domain.mjs")
            return 1
        }
        console.log("veteran_factor_domain.json is up to date")
        return 0
    }
    const tmp = `${ASSET_PATH}.tmp`
    writeFileSync(tmp, text, "utf8")
    renameSync(tmp, ASSET_PATH)
    const payload = JSON.parse(text)
    console.log(`wrote ${ASSET_PATH}: ${JSON.stringify(payload.counts)}`)
    return 0
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        process.exit(main(process.argv.slice(2)))
    } catch (e) {
        console.error(e instanceof GenerateError ? `generate error: ${e.message}` : e)
        process.exit(2)
    }
}

export { GenerateError, ASSET_PATH, SCHEMA_VERSION, SOURCE, STATS, APTITUDES, TIER_MARKER, LEGACY_SCENARIO_FACTORS }
