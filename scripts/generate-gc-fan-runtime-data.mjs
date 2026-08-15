// Generates the compact Grand Concert fan-facts runtime asset the native bot reads at the fan-
// deferral hook. It is a deterministic function of the committed authoritative data
// (src/data/character_objectives.json + src/data/races.json), NOT of any live source, so the root
// files stay the single authority and this asset is a strip-down of what native runtime actually
// needs: per-character GC fan goals (target + deadline turn), mandatory race entry gates (turn +
// per-option fansNeeded, choice turns preserved), and the universal completed-race fan floor
// derived from the payout curves.
//
// Usage:
//   node scripts/generate-gc-fan-runtime-data.mjs            # write the asset
//   node scripts/generate-gc-fan-runtime-data.mjs --check    # verify the committed asset is current (exit 1 if stale)
//
// Determinism: sorted character keys, goals sorted by (turn, targetFans), gates sorted by turn,
// options in source order, fixed top-level key order, two-space JSON, trailing newline. No
// timestamps, no absolute paths, no network, no master.mdb.

import { readFileSync, writeFileSync, renameSync } from "node:fs"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, join } from "node:path"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const OBJECTIVES_PATH = join(REPO, "src", "data", "character_objectives.json")
const RACES_PATH = join(REPO, "src", "data", "races.json")
const ASSET_PATH = join(REPO, "android", "app", "src", "main", "assets", "gc_fan_runtime.json")

/** The Grand Concert scenario id in the master route data; a fan goal applies here iff its
 * appliesToScenarioIds contains it. */
const GRAND_CONCERT_SCENARIO_ID = 3

/** Bumped only when the native reader's expected shape changes. */
const SCHEMA_VERSION = 1

/** A hard-validation failure in the input data. */
class GenerateError extends Error {
    constructor(message) {
        super(message)
        this.name = "GenerateError"
    }
}

/**
 * The universal completed-race fan floor: the smallest payout across every committed race's full
 * placement curve. Because every committed race lists all 18 finishing places and the game's max
 * field size is 18, the worst finish a race can produce is covered, so this min is a proven safe
 * per-race lower bound. Validates every curve is present, positive, and length 18 (the global-floor
 * contract) and throws otherwise.
 *
 * @param {Record<string, any>} races The parsed races.json object.
 * @returns {number} The minimum fan payout across all curves.
 */
export function computeUniversalFloor(races) {
    let floor = Infinity
    let count = 0
    for (const [key, race] of Object.entries(races)) {
        const curve = race.fanPayoutsByPlace
        if (!Array.isArray(curve) || curve.length === 0) {
            throw new GenerateError(`race "${key}" has no fanPayoutsByPlace curve; the universal floor proof needs one`)
        }
        if (curve.length !== 18) {
            throw new GenerateError(`race "${key}" curve has ${curve.length} places, expected 18 (global-floor contract)`)
        }
        for (const p of curve) {
            if (typeof p.fans !== "number" || p.fans <= 0) {
                throw new GenerateError(`race "${key}" has a non-positive payout ${JSON.stringify(p)}`)
            }
            if (p.fans < floor) floor = p.fans
        }
        count++
    }
    if (count === 0) throw new GenerateError("no races found to derive the universal floor")
    if (!Number.isFinite(floor) || floor <= 0) throw new GenerateError(`derived universal floor ${floor} is not a positive number`)
    return floor
}

/**
 * Builds one character's compact fan facts, or null when the character carries nothing native
 * runtime needs (no GC fan goal and no gated mandatory race). Validates the shapes it consumes.
 *
 * @param {string} name The canonical character name (the objective map key).
 * @param {any} char The character's objective entry.
 * @returns {object | null} The compact facts, or null to omit the character.
 */
export function buildCharacter(name, char) {
    const fanGoals = []
    for (const g of char.fanGoals ?? []) {
        if (!Array.isArray(g.appliesToScenarioIds)) {
            throw new GenerateError(`"${name}" fanGoal missing appliesToScenarioIds: ${JSON.stringify(g)}`)
        }
        // Only Grand-Concert-applicable goals reach the runtime asset.
        if (!g.appliesToScenarioIds.includes(GRAND_CONCERT_SCENARIO_ID)) continue
        if (!Number.isInteger(g.turn) || g.turn <= 0 || !Number.isInteger(g.targetFans) || g.targetFans <= 0) {
            throw new GenerateError(`"${name}" fanGoal malformed turn/targetFans: ${JSON.stringify(g)}`)
        }
        fanGoals.push({ turn: g.turn, targetFans: g.targetFans })
    }
    // Deterministic order independent of source ordering.
    fanGoals.sort((a, b) => a.turn - b.turn || a.targetFans - b.targetFans)

    const mandatoryRaces = []
    for (const m of char.mandatoryRaces ?? []) {
        if (!Number.isInteger(m.turn) || !Array.isArray(m.options) || m.options.length === 0) {
            throw new GenerateError(`"${name}" mandatoryRace malformed turn/options: ${JSON.stringify(m)}`)
        }
        const options = m.options.map((o) => {
            // fansNeeded absent is distinct from an explicit 0: an absent gate is unknown, not "no gate".
            // The current committed data has it on every option; guard so a regression fails loudly.
            if (typeof o.fansNeeded !== "number" || !Number.isInteger(o.fansNeeded) || o.fansNeeded < 0) {
                throw new GenerateError(`"${name}" turn ${m.turn} option "${o.raceName}" has a bad fansNeeded: ${JSON.stringify(o.fansNeeded)}`)
            }
            if (typeof o.raceName !== "string" || o.raceName.length === 0) {
                throw new GenerateError(`"${name}" turn ${m.turn} option missing raceName: ${JSON.stringify(o)}`)
            }
            return { raceName: o.raceName, fansNeeded: o.fansNeeded }
        })
        // Only keep gates that actually gate on fans; a turn whose every option needs 0 fans carries
        // no runtime pressure. Preserve the option set (choice turns) for the ones that do.
        if (options.every((o) => o.fansNeeded <= 0)) continue
        mandatoryRaces.push({ turn: m.turn, isChoice: options.length > 1, options })
    }
    mandatoryRaces.sort((a, b) => a.turn - b.turn)

    if (fanGoals.length === 0 && mandatoryRaces.length === 0) return null
    return { fanGoals, mandatoryRaces }
}

/**
 * Builds the full runtime payload from the two authoritative datasets.
 *
 * @param {Record<string, any>} objectives The parsed character_objectives.json object.
 * @param {Record<string, any>} races The parsed races.json object.
 * @returns {object} The payload object with deterministic key ordering.
 */
export function buildPayload(objectives, races) {
    const universalFloor = computeUniversalFloor(races)
    const characters = {}
    for (const name of Object.keys(objectives).sort()) {
        const built = buildCharacter(name, objectives[name])
        if (built !== null) characters[name] = built
    }
    return {
        schemaVersion: SCHEMA_VERSION,
        universalCompletedRaceFanFloor: universalFloor,
        characters,
    }
}

/**
 * Serializes the payload to the exact on-disk form: two-space JSON with a trailing newline.
 *
 * @param {object} payload The payload object.
 * @returns {string} The serialized asset text.
 */
export function serialize(payload) {
    return `${JSON.stringify(payload, null, 2)}\n`
}

function readJson(path) {
    return JSON.parse(readFileSync(path, "utf8"))
}

function generate() {
    const objectives = readJson(OBJECTIVES_PATH)
    const races = readJson(RACES_PATH)
    return serialize(buildPayload(objectives, races))
}

function main(argv) {
    const check = argv.includes("--check")
    const text = generate()
    if (check) {
        let current = null
        try {
            current = readFileSync(ASSET_PATH, "utf8")
        } catch {
            console.error(`gc_fan_runtime.json missing at ${ASSET_PATH}; run without --check to generate it`)
            return 1
        }
        if (current !== text) {
            console.error("gc_fan_runtime.json is stale; re-run scripts/generate-gc-fan-runtime-data.mjs")
            return 1
        }
        console.log("gc_fan_runtime.json is up to date")
        return 0
    }
    // Atomic write: write a sibling temp then rename over the target.
    const tmp = `${ASSET_PATH}.tmp`
    writeFileSync(tmp, text, "utf8")
    renameSync(tmp, ASSET_PATH)
    const payload = JSON.parse(text)
    console.log(`wrote ${ASSET_PATH}: ${Object.keys(payload.characters).length} characters, floor=${payload.universalCompletedRaceFanFloor}`)
    return 0
}

// Only run as a CLI, not when imported by the test.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        process.exit(main(process.argv.slice(2)))
    } catch (e) {
        console.error(e instanceof GenerateError ? `generate error: ${e.message}` : e)
        process.exit(2)
    }
}

export { GenerateError, ASSET_PATH, SCHEMA_VERSION }
