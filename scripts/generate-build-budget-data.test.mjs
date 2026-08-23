// Tests for the build-budget extractor, using tiny synthetic in-memory SQLite fixtures.
//
// Run: node --test scripts/generate-build-budget-data.test.mjs
//
// Nothing here touches the live master.mdb, the network, or any repo file. The fixtures exist for the
// five things this extractor could get quietly wrong: the blue group -> stat mapping, the pink group
// -> aptitude mapping, ladder contiguity, the thirty-point growth invariant, and an effect target the
// extractor has no description for. Each has a passing fixture and a fixture that breaks it, because
// a verification that cannot fail is not a verification.

import test from "node:test"
import assert from "node:assert/strict"
import { DatabaseSync } from "node:sqlite"
import { buildPayload, buildTraineeGrowth, serializePayload, verifyNamedGroups, GenerateError } from "./generate-build-budget-data.mjs"

const TABLES = {
    succession_factor: "CREATE TABLE succession_factor (factor_id INTEGER, factor_group_id INTEGER, rarity INTEGER, grade INTEGER, factor_type INTEGER, effect_group_id INTEGER);",
    succession_factor_effect: "CREATE TABLE succession_factor_effect (id INTEGER, factor_group_id INTEGER, effect_id INTEGER, target_type INTEGER, value_1 INTEGER, value_2 INTEGER);",
    card_data: "CREATE TABLE card_data (id INTEGER, chara_id INTEGER, default_rarity INTEGER, talent_speed INTEGER, talent_stamina INTEGER, talent_pow INTEGER, talent_guts INTEGER, talent_wiz INTEGER, running_style INTEGER);",
    text_data: 'CREATE TABLE text_data (category INTEGER, "index" INTEGER, text TEXT);',
}

const BLUE_NAMES = ["Speed", "Stamina", "Power", "Guts", "Wit"]
const PINK_GROUPS = [
    [11, "Turf"],
    [12, "Dirt"],
    [21, "Front Runner"],
    [22, "Pace Chaser"],
    [23, "Late Surger"],
    [24, "End Closer"],
    [31, "Sprint"],
    [32, "Mile"],
    [33, "Medium"],
    [34, "Long"],
]

/** The blue start-stat ladder the shipped database carries, used verbatim so a change is visible. */
const BLUE_START_LADDER = [1, 4, 7, 10, 13, 16, 19, 22, 25, 28]
const BLUE_CAP_LADDER = [1, 1, 1, 2, 2, 2, 3, 3, 3, 4]

/**
 * Builds an in-memory database holding the five blue groups, the ten pink groups and two trainee
 * cards. Every fixture starts from this and breaks exactly one thing.
 */
function makeDb(mutate = () => {}) {
    const db = new DatabaseSync(":memory:")
    for (const sql of Object.values(TABLES)) db.exec(sql)

    const factor = db.prepare("INSERT INTO succession_factor VALUES (?, ?, ?, ?, ?, ?)")
    const effect = db.prepare("INSERT INTO succession_factor_effect VALUES (?, ?, ?, ?, ?, ?)")
    const text = db.prepare("INSERT INTO text_data VALUES (?, ?, ?)")
    let effectId = 0

    BLUE_NAMES.forEach((name, i) => {
        const groupId = i + 1
        for (const rarity of [1, 2, 3]) {
            const factorId = groupId * 100 + rarity
            factor.run(factorId, groupId, rarity, 1, 1, 10 + rarity)
            text.run(147, factorId, name)
        }
        BLUE_START_LADDER.forEach((value, level) => effect.run(++effectId, groupId, level + 1, groupId, value, 0))
        BLUE_CAP_LADDER.forEach((value, level) => effect.run(++effectId, groupId, level + 1, groupId + 60, value, 0))
    })

    for (const [groupId, name] of PINK_GROUPS) {
        for (const rarity of [1, 2, 3]) {
            const factorId = groupId * 100 + rarity
            factor.run(factorId, groupId, rarity, 1, 2, 20 + rarity)
            text.run(147, factorId, name)
        }
        for (const level of [1, 2]) effect.run(++effectId, groupId, level, groupId, level, 0)
    }

    const card = db.prepare("INSERT INTO card_data VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
    card.run(100101, 1001, 3, 0, 20, 0, 0, 10, 3)
    card.run(100102, 1001, 3, 10, 10, 10, 0, 0, 3)
    text.run(5, 100101, "[Special Dreamer]")
    text.run(5, 100102, "[Hopp'n Happy Heart]")
    text.run(6, 1001, "Special Week")

    mutate(db)
    return db
}

test("builds a payload carrying the decoded blue start-stat and max-stat ladders apart", () => {
    const db = makeDb()
    const payload = buildPayload(db)
    const stamina = payload.factorGroups.find((g) => g.canonicalName === "Stamina")
    assert.equal(stamina.family, "stat")
    assert.equal(stamina.maxRarity, 3)
    const start = stamina.effects.find((e) => e.kind === "START_STAT")
    const cap = stamina.effects.find((e) => e.kind === "MAX_STAT")
    assert.deepEqual(start.value1, BLUE_START_LADDER)
    assert.deepEqual(cap.value1, BLUE_CAP_LADDER)
    assert.equal(start.stat, "Stamina")
    assert.equal(cap.stat, "Stamina")
    assert.notEqual(start.targetType, cap.targetType)
    db.close()
})

test("carries the ten pink aptitude groups with the group id as their own target type", () => {
    const db = makeDb()
    const payload = buildPayload(db)
    const long = payload.factorGroups.find((g) => g.canonicalName === "Long")
    assert.equal(long.family, "aptitude")
    const aptitude = long.effects.find((e) => e.kind === "APTITUDE")
    assert.equal(aptitude.targetType, 34)
    assert.equal(aptitude.aptitude, "Long")
    assert.deepEqual(aptitude.value1, [1, 2])
    db.close()
})

test("carries per-outfit growth rates that sum to thirty", () => {
    const db = makeDb()
    const growth = buildTraineeGrowth(db)
    assert.equal(growth.length, 2)
    assert.deepEqual(growth[0].growth, { speed: 0, stamina: 20, power: 0, guts: 0, wit: 10 })
    assert.equal(growth[0].character, "Special Week")
    assert.equal(growth[0].outfit, "[Special Dreamer]")
    db.close()
})

test("rejects a card whose growth rates do not distribute thirty points", () => {
    const db = makeDb((d) => d.exec("UPDATE card_data SET talent_wiz = 15 WHERE id = 100101"))
    assert.throws(() => buildTraineeGrowth(db), GenerateError)
    db.close()
})

test("rejects a ladder whose levels are not contiguous from one", () => {
    const db = makeDb((d) => d.exec("DELETE FROM succession_factor_effect WHERE factor_group_id = 2 AND effect_id = 3 AND target_type = 2"))
    assert.throws(() => buildPayload(db), GenerateError)
    db.close()
})

test("rejects an effect target the extractor has no description for", () => {
    const db = makeDb((d) => d.exec("UPDATE succession_factor_effect SET target_type = 99 WHERE factor_group_id = 1 AND target_type = 1"))
    assert.throws(() => buildPayload(db), GenerateError)
    db.close()
})

test("rejects a factor group carrying two different names", () => {
    const db = makeDb((d) => d.exec("UPDATE text_data SET text = 'Endurance' WHERE category = 147 AND \"index\" = 203"))
    assert.throws(() => buildPayload(db), GenerateError)
    db.close()
})

test("rejects a factor whose name is missing from the name table", () => {
    const db = makeDb((d) => d.exec('DELETE FROM text_data WHERE category = 147 AND "index" = 201'))
    assert.throws(() => buildPayload(db), GenerateError)
    db.close()
})

test("verifyNamedGroups rejects a blue group whose stat mapping has moved", () => {
    const groups = buildPayload(makeDb()).factorGroups
    const moved = groups.map((g) => (g.canonicalName === "Power" ? { ...g, effects: g.effects.map((e) => (e.kind === "START_STAT" ? { ...e, targetType: 5 } : e)) } : g))
    assert.throws(() => verifyNamedGroups(moved), GenerateError)
})

test("verifyNamedGroups rejects a pink group whose aptitude mapping has moved", () => {
    const groups = buildPayload(makeDb()).factorGroups
    const moved = groups.map((g) => (g.canonicalName === "Mile" ? { ...g, effects: g.effects.map((e) => (e.kind === "APTITUDE" ? { ...e, targetType: 33 } : e)) } : g))
    assert.throws(() => verifyNamedGroups(moved), GenerateError)
})

test("verifyNamedGroups accepts the shipped mapping unchanged", () => {
    const groups = buildPayload(makeDb()).factorGroups
    assert.doesNotThrow(() => verifyNamedGroups(groups))
})

test("serializes to pure ASCII with a trailing newline, byte-identically twice", () => {
    const a = serializePayload(buildPayload(makeDb()))
    const b = serializePayload(buildPayload(makeDb()))
    assert.equal(a, b)
    assert.ok(a.endsWith("\n"))
    assert.ok(!/[^\x00-\x7f]/.test(a), "payload must be pure ASCII")
})
