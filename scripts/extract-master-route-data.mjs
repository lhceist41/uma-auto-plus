// Grand Concert fan-goal extractor - offline, deterministic.
//
// The game's route fan-count goals ("earn N fans by turn T") live in the installed game database
// (master.mdb), not in the GameTora manifests the scraper uses. This tool reads them read-only and
// enriches the committed per-character objective family (src/data/character_objectives.json) with a
// `fanGoals` field, so later code can answer "which fan goal applies to this trainee in Grand
// Concert, and when is it due" without master.mdb at runtime.
//
// Authoritative source shape (all verified against the live English master.mdb):
//   single_mode_route(chara_id -> race_set_id)
//   single_mode_route_race(race_set_id, scenario_group_id, condition_type, condition_value_1, turn)
//     condition_type = 3 is the fan-count goal: condition_value_1 = target fans, turn = deadline.
//   single_mode_scenario_group(group_id, scenario_id) maps a group to its member scenarios.
//   text_data(category=6, index=chara_id) -> character name.
// Grand Concert is scenario id 3; a goal applies to GC iff its scenario group contains scenario 3
// (membership-driven, never a hardcoded group number).
//
// Deterministic: same DB -> byte-identical enrichment. No timestamps. Read-only on the DB. The raw
// objective layer stays authority; this only appends `fanGoals`, never touches mandatoryRaces.
//
// Usage:
//   node scripts/extract-master-route-data.mjs --db <path-to-master.mdb> [--objectives <path>] [--check]
//     --db          Path to the installed game's master.mdb (required; never hardcoded).
//     --objectives  character_objectives.json to enrich (default: <repo>/src/data/character_objectives.json).
//     --check       Compute in memory and compare to the committed file; write nothing. Exit 3 if stale.
//
// Exit codes: 0 clean | 2 usage/DB-unsuitable | 3 (--check) stale output.

import { DatabaseSync } from "node:sqlite"
import { createHash } from "node:crypto"
import { readFileSync, writeFileSync } from "node:fs"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, resolve } from "node:path"

const GRAND_CONCERT_SCENARIO_ID = 3
const FAN_COUNT_CONDITION_TYPE = 3
const CHARA_NAME_TEXT_CATEGORY = 6

/** A failure that maps to a CLI exit code, so the core functions can be tested by asserting throws
 * instead of terminating the test process. */
export class ExtractError extends Error {
    constructor(message, code = 2) {
        super(message)
        this.code = code
    }
}

function parseArgs(argv) {
    const args = { db: null, objectives: null, check: false }
    for (let i = 2; i < argv.length; i++) {
        const a = argv[i]
        if (a === "--check") args.check = true
        else if (a === "--db") args.db = argv[++i]
        else if (a === "--objectives") args.objectives = argv[++i]
        else if (a === "--help" || a === "-h") {
            console.log("Usage: node scripts/extract-master-route-data.mjs --db <master.mdb> [--objectives <path>] [--check]")
            process.exit(0)
        } else {
            console.error(`Unknown argument: ${a}`)
            process.exit(2)
        }
    }
    if (!args.db) {
        console.error("Missing required --db <path-to-master.mdb>")
        process.exit(2)
    }
    return args
}

/** Folds a name to letters+digits, lowercased, for tolerant character-name matching
 * ("T.M. Opera O" <-> "TM Opera O"). */
export function fold(name) {
    return String(name).toLowerCase().replace(/[^a-z0-9]/g, "")
}

/** Rejects a stale/incompatible DB before anything is written. The stale pre-Grand-Concert dump
 * lacks scenario 3 and the Copano route entirely, so these checks fail fast on it. */
export function assertSuitable(db) {
    const fail = (msg) => {
        throw new ExtractError(`master.mdb is not suitable for Grand Concert extraction: ${msg}`)
    }
    const scenario = db.prepare("SELECT id FROM single_mode_scenario WHERE id = ?").get(GRAND_CONCERT_SCENARIO_ID)
    if (!scenario) fail("scenario id 3 (Grand Concert) is absent")
    const gcGroups = db.prepare("SELECT DISTINCT group_id FROM single_mode_scenario_group WHERE scenario_id = ?").all(GRAND_CONCERT_SCENARIO_ID)
    if (gcGroups.length === 0) fail("no scenario group contains scenario 3")
    const copanoRoute = db.prepare("SELECT race_set_id FROM single_mode_route WHERE chara_id = 1098").get()
    if (!copanoRoute) fail("Copano Rickey route (chara 1098) is absent")
    const copanoGoal = db
        .prepare("SELECT condition_value_1 AS target, turn FROM single_mode_route_race WHERE race_set_id = ? AND condition_type = ?")
        .get(copanoRoute.race_set_id, FAN_COUNT_CONDITION_TYPE)
    if (!copanoGoal || copanoGoal.target !== 3000 || copanoGoal.turn !== 24) {
        fail(`Copano fan goal expected 3000 fans / turn 24, got ${JSON.stringify(copanoGoal)}`)
    }
}

/** Extracts every Grand Concert-applicable fan-count goal, grouped by canonical character name,
 * deterministically ordered. */
export function extractFanGoals(db) {
    const gcGroupIds = new Set(
        db.prepare("SELECT DISTINCT group_id FROM single_mode_scenario_group WHERE scenario_id = ?").all(GRAND_CONCERT_SCENARIO_ID).map((r) => r.group_id),
    )
    // Cache each group's full scenario membership (sorted), for the auditable appliesToScenarioIds.
    const groupMembers = new Map()
    for (const gid of gcGroupIds) {
        groupMembers.set(gid, db.prepare("SELECT scenario_id FROM single_mode_scenario_group WHERE group_id = ? ORDER BY scenario_id").all(gid).map((r) => r.scenario_id))
    }
    const rows = db
        .prepare(
            `SELECT r.chara_id AS charaId, rr.scenario_group_id AS groupId, rr.turn AS turn, rr.condition_value_1 AS targetFans
             FROM single_mode_route_race rr
             JOIN single_mode_route r ON r.race_set_id = rr.race_set_id
             WHERE rr.condition_type = ?`,
        )
        .all(FAN_COUNT_CONDITION_TYPE)
        .filter((row) => gcGroupIds.has(row.groupId))

    const nameStmt = db.prepare("SELECT text FROM text_data WHERE category = ? AND [index] = ?")
    const byName = new Map()
    for (const row of rows) {
        const nameRow = nameStmt.get(CHARA_NAME_TEXT_CATEGORY, row.charaId)
        const name = nameRow ? nameRow.text : null
        if (!name) throw new ExtractError(`No character name for chara_id ${row.charaId}`)
        const goal = {
            turn: row.turn,
            targetFans: row.targetFans,
            scenarioGroupId: row.groupId,
            appliesToScenarioIds: groupMembers.get(row.groupId),
        }
        if (!byName.has(name)) byName.set(name, [])
        byName.get(name).push(goal)
    }
    // Deterministic order: goals by (turn, targetFans); characters by name.
    for (const goals of byName.values()) goals.sort((a, b) => a.turn - b.turn || a.targetFans - b.targetFans)
    return byName
}

/** Matches a master character name to a committed objectives key: exact first, then a unique folded
 * match. Ambiguous or missing matches stop the run rather than guess. */
export function resolveObjectiveKey(masterName, objectiveKeys, foldedIndex) {
    if (objectiveKeys.has(masterName)) return masterName
    const candidates = foldedIndex.get(fold(masterName))
    if (!candidates || candidates.length !== 1) {
        throw new ExtractError(`Cannot safely map master character "${masterName}" to a committed objectives key (candidates: ${JSON.stringify(candidates || [])})`)
    }
    return candidates[0]
}

/** Serializes objectives exactly as the scraper does: 4-space indent, top-level keys sorted, no
 * trailing newline, and the committed file's line endings preserved. */
export function serialize(objectives, eol) {
    const sorted = {}
    for (const key of Object.keys(objectives).sort()) sorted[key] = objectives[key]
    const text = JSON.stringify(sorted, null, 4)
    return eol === "\r\n" ? text.replace(/\n/g, "\r\n") : text
}

function main() {
    const args = parseArgs(process.argv)
    const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..")
    const objectivesPath = args.objectives ? resolve(args.objectives) : resolve(repoRoot, "src/data/character_objectives.json")

    const db = new DatabaseSync(args.db, { readOnly: true })
    assertSuitable(db)
    const fanGoalsByName = extractFanGoals(db)
    db.close?.()

    const committedRaw = readFileSync(objectivesPath, "utf8")
    const eol = committedRaw.includes("\r\n") ? "\r\n" : "\n"
    const objectives = JSON.parse(committedRaw)
    const objectiveKeys = new Set(Object.keys(objectives))
    const foldedIndex = new Map()
    for (const key of objectiveKeys) {
        const f = fold(key)
        if (!foldedIndex.has(f)) foldedIndex.set(f, [])
        foldedIndex.get(f).push(key)
    }

    // Idempotent: drop any previously-written fanGoals, then re-add from the current DB.
    let enrichedCharacters = 0
    let emittedGoals = 0
    for (const key of objectiveKeys) {
        if (objectives[key] && typeof objectives[key] === "object") delete objectives[key].fanGoals
    }
    for (const [masterName, goals] of fanGoalsByName) {
        const key = resolveObjectiveKey(masterName, objectiveKeys, foldedIndex)
        objectives[key].fanGoals = goals
        enrichedCharacters++
        emittedGoals += goals.length
    }

    const output = serialize(objectives, eol)
    const dbHash = createHash("sha256").update(readFileSync(args.db)).digest("hex")

    console.log(`[extract] source DB: ${args.db}`)
    console.log(`[extract] source DB sha256: ${dbHash}`)
    const gcGroupList = [...new Set([...fanGoalsByName.values()].flat().map((g) => g.scenarioGroupId))].sort((a, b) => a - b)
    console.log(`[extract] scenario groups used: ${gcGroupList.join(", ")}`)
    console.log(`[extract] characters enriched with fanGoals: ${enrichedCharacters}`)
    console.log(`[extract] Grand Concert fan goals emitted: ${emittedGoals}`)

    if (args.check) {
        if (output === committedRaw) {
            console.log("[extract] --check: committed character_objectives.json is up to date.")
            process.exit(0)
        }
        console.error("[extract] --check: committed character_objectives.json is STALE (differs from a fresh extraction).")
        process.exit(3)
    }
    writeFileSync(objectivesPath, output, "utf8")
    console.log(`[extract] wrote ${objectivesPath}`)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        main()
    } catch (err) {
        console.error(err.message)
        process.exit(err instanceof ExtractError ? err.code : 1)
    }
}
