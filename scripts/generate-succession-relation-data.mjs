// Extracts the game's own succession-relation tables from an installed master.mdb into a committed,
// offline data file the ParentLab affinity advisor reads. Read-only against the database, deterministic
// output, no network.
//
// What is being extracted, and what it is NOT:
//   succession_relation         relation_type -> relation_point
//   succession_relation_member  relation_type -> chara_id
//   succession_relation_rank    the three rank bands the game buckets a relation total into
//   text_data category 6        chara_id -> English character name
//
// Two characters that both belong to a relation type share that type's points. Summing the points of
// every type they share is the pairwise base relation between them, and that sum is directly checkable
// against the game (the highest pair the shipped tables produce is 43). It is NOT the affinity total
// the game displays for a lineage: the rank bands run to 151+, which no single pair can reach, so the
// game aggregates several relations. Which ones, and with what weights, is not in this database and is
// deliberately not guessed here.
//
// Usage:
//   node scripts/generate-succession-relation-data.mjs --db <path-to-master.mdb>
//   node scripts/generate-succession-relation-data.mjs --db <path> --check   # exit 1 if the committed file is stale
//
// Determinism: relation types ascending, members ascending, character names sorted, fixed key order,
// two-space JSON, trailing newline. No timestamps, no absolute paths.

import { readFileSync, writeFileSync, renameSync, existsSync } from "node:fs"
import { DatabaseSync } from "node:sqlite"
import { fileURLToPath, pathToFileURL } from "node:url"
import { dirname, join } from "node:path"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const OUT_PATH = join(REPO, "src", "data", "succession_relations.json")

export const SUCCESSION_RELATION_SCHEMA = "parent_lab_succession_relations"
export const SUCCESSION_RELATION_SCHEMA_VERSION = 1

/** text_data category holding the English character name, keyed by chara_id. */
const TEXT_CATEGORY_CHARA_NAME = 6

const SOURCE = "master.mdb: succession_relation, succession_relation_member, succession_relation_rank, text_data(category 6)"

class GenerateError extends Error {
    constructor(message) {
        super(message)
        this.name = "GenerateError"
    }
}

function requireTables(db) {
    const have = new Set(
        db
            .prepare("SELECT name FROM sqlite_master WHERE type='table'")
            .all()
            .map((r) => r.name),
    )
    for (const table of ["succession_relation", "succession_relation_member", "succession_relation_rank", "text_data"]) {
        if (!have.has(table)) throw new GenerateError(`master.mdb has no ${table} table; this is not a database this extractor can read`)
    }
}

/**
 * Builds the payload from an open database handle.
 *
 * Fails closed on anything it cannot resolve: a duplicate relation type, a member whose relation type
 * has no point value, or a character id with no English name. A silently dropped member would make a
 * pair look less related than it is, which is worse than not shipping the file.
 */
export function buildPayload(db) {
    requireTables(db)

    const points = new Map()
    for (const row of db.prepare("SELECT relation_type, relation_point FROM succession_relation").all()) {
        if (points.has(row.relation_type)) throw new GenerateError(`duplicate relation_type ${row.relation_type} in succession_relation`)
        points.set(row.relation_type, row.relation_point)
    }

    const members = new Map()
    let memberRows = 0
    for (const row of db.prepare("SELECT relation_type, chara_id FROM succession_relation_member").all()) {
        memberRows++
        if (!points.has(row.relation_type)) throw new GenerateError(`succession_relation_member references relation_type ${row.relation_type} with no point value`)
        let set = members.get(row.relation_type)
        if (!set) {
            set = new Set()
            members.set(row.relation_type, set)
        }
        set.add(row.chara_id)
    }

    const names = new Map()
    for (const row of db.prepare('SELECT "index" AS idx, text FROM text_data WHERE category = ?').all(TEXT_CATEGORY_CHARA_NAME)) {
        names.set(row.idx, row.text)
    }

    // Only relation types with at least one member are kept: a type with no members can never be shared
    // by two characters, so it carries no pairwise information. The count of what was dropped is
    // reported rather than left implicit.
    const relations = []
    for (const relationType of [...members.keys()].sort((a, b) => a - b)) {
        relations.push({
            relationType,
            relationPoint: points.get(relationType),
            members: [...members.get(relationType)].sort((a, b) => a - b),
        })
    }

    const charaIds = new Set()
    for (const relation of relations) for (const id of relation.members) charaIds.add(id)

    const characters = {}
    const unnamed = []
    for (const id of [...charaIds].sort((a, b) => a - b)) {
        const name = names.get(id)
        if (!name) {
            unnamed.push(id)
            continue
        }
        if (characters[name] !== undefined && characters[name] !== id) {
            throw new GenerateError(`character name "${name}" maps to both chara_id ${characters[name]} and ${id}`)
        }
        characters[name] = id
    }
    if (unnamed.length > 0) throw new GenerateError(`succession_relation_member references chara_id(s) with no English name: ${unnamed.join(", ")}`)

    const rankBands = db
        .prepare("SELECT relation_rank, rank_value_min, rank_value_max FROM succession_relation_rank")
        .all()
        .map((r) => ({ rank: r.relation_rank, minValue: r.rank_value_min, maxValue: r.rank_value_max }))
        .sort((a, b) => a.rank - b.rank)
    if (rankBands.length === 0) throw new GenerateError("succession_relation_rank is empty")

    // The highest pairwise total the shipped tables can produce. It travels with the file so a consumer
    // can see for itself that no single pair reaches the top rank band, which is the evidence that the
    // in-game total aggregates more than one relation.
    const byChara = new Map()
    for (const relation of relations) {
        for (const id of relation.members) {
            let map = byChara.get(id)
            if (!map) {
                map = new Map()
                byChara.set(id, map)
            }
            map.set(relation.relationType, relation.relationPoint)
        }
    }
    const ids = [...byChara.keys()].sort((a, b) => a - b)
    let maxPairwisePoints = 0
    for (let i = 0; i < ids.length; i++) {
        const a = byChara.get(ids[i])
        for (let j = i + 1; j < ids.length; j++) {
            const b = byChara.get(ids[j])
            let sum = 0
            for (const [type, point] of a) if (b.has(type)) sum += point
            if (sum > maxPairwisePoints) maxPairwisePoints = sum
        }
    }

    const sortedCharacters = {}
    for (const name of Object.keys(characters).sort()) sortedCharacters[name] = characters[name]

    return {
        schema: SUCCESSION_RELATION_SCHEMA,
        schemaVersion: SUCCESSION_RELATION_SCHEMA_VERSION,
        source: SOURCE,
        rankBands,
        characters: sortedCharacters,
        relations,
        diagnostics: {
            relationTypes: points.size,
            relationTypesWithMembers: relations.length,
            relationTypesWithoutMembers: points.size - relations.length,
            pairwiseCapableTypes: relations.filter((r) => r.members.length >= 2).length,
            memberRows,
            charactersWithMembership: ids.length,
            maxPairwisePoints,
            topRankBandMinValue: rankBands[rankBands.length - 1].minValue,
        },
    }
}

export function serialize(payload) {
    return `${JSON.stringify(payload, null, 2)}\n`
}

function parseArgs(argv) {
    const args = { db: null, out: OUT_PATH, check: false }
    for (let i = 2; i < argv.length; i++) {
        const a = argv[i]
        if (a === "--check") args.check = true
        else if (a === "--db") args.db = argv[++i]
        else if (a === "--out") args.out = argv[++i]
        else if (a === "--help" || a === "-h") {
            console.log("Usage: node scripts/generate-succession-relation-data.mjs --db <master.mdb> [--out <path>] [--check]")
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

function main() {
    const args = parseArgs(process.argv)
    if (!existsSync(args.db)) {
        console.error(`master.mdb not found: ${args.db}`)
        process.exit(2)
    }
    const db = new DatabaseSync(args.db, { readOnly: true })
    let text
    try {
        text = serialize(buildPayload(db))
    } finally {
        db.close()
    }

    if (args.check) {
        const current = existsSync(args.out) ? readFileSync(args.out, "utf8") : null
        if (current === text) {
            console.log(`${args.out} is current`)
            return
        }
        console.error(`${args.out} is stale; re-run without --check`)
        process.exit(1)
    }

    const tmp = `${args.out}.tmp`
    writeFileSync(tmp, text, "utf8")
    renameSync(tmp, args.out)
    const payload = JSON.parse(text)
    console.log(`wrote ${args.out}: ${payload.relations.length} relation types, ${payload.diagnostics.charactersWithMembership} characters, max pairwise ${payload.diagnostics.maxPairwisePoints}`)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    try {
        main()
    } catch (err) {
        if (err instanceof GenerateError) {
            console.error(err.message)
            process.exit(2)
        }
        throw err
    }
}

export { GenerateError, OUT_PATH }
