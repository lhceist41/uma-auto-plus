// Tests for the succession-relation extractor, using tiny synthetic in-memory SQLite fixtures.
//
// Run: node --test scripts/generate-succession-relation-data.test.mjs
//
// Nothing here touches the live master.mdb, the network, or any repo file.

import test from "node:test"
import assert from "node:assert/strict"
import { DatabaseSync } from "node:sqlite"
import { buildPayload, serialize, GenerateError } from "./generate-succession-relation-data.mjs"

/**
 * Builds a minimal master.mdb-shaped database.
 *
 * The default fixture has known arithmetic: 1001 and 1002 share types 1 (5 points) and 2 (3 points),
 * 1001 and 1003 share type 3 (2 points), 1003 belongs to a solo type that can never be shared, and
 * type 9 has no members at all.
 */
function makeDb({ relations, members, names, ranks, omitTable = null } = {}) {
    const db = new DatabaseSync(":memory:")
    const tables = {
        succession_relation: "CREATE TABLE succession_relation (relation_type INTEGER, relation_point INTEGER);",
        succession_relation_member: "CREATE TABLE succession_relation_member (id INTEGER, relation_type INTEGER, chara_id INTEGER);",
        succession_relation_rank: "CREATE TABLE succession_relation_rank (relation_rank INTEGER, rank_value_min INTEGER, rank_value_max INTEGER);",
        text_data: "CREATE TABLE text_data (category INTEGER, [index] INTEGER, text TEXT);",
    }
    for (const [name, ddl] of Object.entries(tables)) if (name !== omitTable) db.exec(ddl)

    const rel = relations ?? [
        [1, 5],
        [2, 3],
        [3, 2],
        [4, 1],
        [9, 7],
    ]
    const mem = members ?? [
        [1, 1001],
        [1, 1002],
        [2, 1001],
        [2, 1002],
        [3, 1001],
        [3, 1003],
        [4, 1003],
    ]
    const nam = names ?? [
        [1001, "Special Week"],
        [1002, "Silence Suzuka"],
        [1003, "T.M. Opera O"],
    ]
    const rnk = ranks ?? [
        [1, 0, 50],
        [2, 51, 150],
        [3, 151, 9999],
    ]
    if (omitTable !== "succession_relation") for (const [t, p] of rel) db.prepare("INSERT INTO succession_relation VALUES (?, ?)").run(t, p)
    if (omitTable !== "succession_relation_member") {
        let id = 1
        for (const [t, c] of mem) db.prepare("INSERT INTO succession_relation_member VALUES (?, ?, ?)").run(id++, t, c)
    }
    if (omitTable !== "succession_relation_rank") for (const [r, lo, hi] of rnk) db.prepare("INSERT INTO succession_relation_rank VALUES (?, ?, ?)").run(r, lo, hi)
    if (omitTable !== "text_data") for (const [idx, text] of nam) db.prepare("INSERT INTO text_data VALUES (6, ?, ?)").run(idx, text)
    return db
}

test("extracts relation types, members and names", () => {
    const payload = buildPayload(makeDb())
    assert.equal(payload.schema, "parent_lab_succession_relations")
    assert.deepEqual(payload.characters, { "Silence Suzuka": 1002, "Special Week": 1001, "T.M. Opera O": 1003 })
    assert.deepEqual(
        payload.relations.map((r) => r.relationType),
        [1, 2, 3, 4],
    )
    assert.deepEqual(payload.relations[0], { relationType: 1, relationPoint: 5, members: [1001, 1002] })
})

test("drops member-less relation types and reports how many", () => {
    const payload = buildPayload(makeDb())
    // Type 9 has a point value but no members, so it can never be shared by two characters.
    assert.equal(payload.diagnostics.relationTypes, 5)
    assert.equal(payload.diagnostics.relationTypesWithMembers, 4)
    assert.equal(payload.diagnostics.relationTypesWithoutMembers, 1)
    // Type 4 has exactly one member, so it is kept but cannot contribute to a pair.
    assert.equal(payload.diagnostics.pairwiseCapableTypes, 3)
})

test("reports the highest pairwise total the tables can produce", () => {
    const payload = buildPayload(makeDb())
    // 1001 <-> 1002 share types 1 and 2: 5 + 3 = 8, the largest pair in the fixture.
    assert.equal(payload.diagnostics.maxPairwisePoints, 8)
    assert.equal(payload.diagnostics.topRankBandMinValue, 151)
})

test("fails closed on a member whose relation type has no point value", () => {
    const db = makeDb({ members: [[77, 1001], [77, 1002]] })
    assert.throws(() => buildPayload(db), GenerateError)
})

test("fails closed on a character with no English name", () => {
    const db = makeDb({ names: [[1001, "Special Week"], [1002, "Silence Suzuka"]] })
    assert.throws(() => buildPayload(db), GenerateError)
})

test("fails closed on a duplicated relation type", () => {
    const db = makeDb({ relations: [[1, 5], [1, 4], [2, 3], [3, 2], [4, 1]] })
    assert.throws(() => buildPayload(db), GenerateError)
})

test("fails closed when a required table is absent", () => {
    assert.throws(() => buildPayload(makeDb({ omitTable: "succession_relation_rank" })), GenerateError)
})

test("serializes deterministically", () => {
    const a = serialize(buildPayload(makeDb()))
    const b = serialize(buildPayload(makeDb()))
    assert.equal(a, b)
    assert.ok(a.endsWith("}\n"))
})
