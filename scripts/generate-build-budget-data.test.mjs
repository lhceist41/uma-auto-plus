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
import { buildPayload, buildStatCaps, buildTraineeBase, buildTraineeGrowth, buildTrainingEffects, deriveCommandTrainingTypes, serializePayload, verifyNamedGroups, GenerateError } from "./generate-build-budget-data.mjs"

const TABLES = {
    succession_factor: "CREATE TABLE succession_factor (factor_id INTEGER, factor_group_id INTEGER, rarity INTEGER, grade INTEGER, factor_type INTEGER, effect_group_id INTEGER);",
    succession_factor_effect: "CREATE TABLE succession_factor_effect (id INTEGER, factor_group_id INTEGER, effect_id INTEGER, target_type INTEGER, value_1 INTEGER, value_2 INTEGER);",
    card_data: "CREATE TABLE card_data (id INTEGER, chara_id INTEGER, default_rarity INTEGER, talent_speed INTEGER, talent_stamina INTEGER, talent_pow INTEGER, talent_guts INTEGER, talent_wiz INTEGER, running_style INTEGER);",
    card_rarity_data:
        "CREATE TABLE card_rarity_data (card_id INTEGER, rarity INTEGER, speed INTEGER, stamina INTEGER, pow INTEGER, guts INTEGER, wiz INTEGER, max_speed INTEGER, max_stamina INTEGER, max_pow INTEGER, max_guts INTEGER, max_wiz INTEGER, " +
        "proper_distance_short INTEGER, proper_distance_mile INTEGER, proper_distance_middle INTEGER, proper_distance_long INTEGER, proper_ground_turf INTEGER, proper_ground_dirt INTEGER, " +
        "proper_running_style_nige INTEGER, proper_running_style_senko INTEGER, proper_running_style_sashi INTEGER, proper_running_style_oikomi INTEGER);",
    single_mode_scenario: "CREATE TABLE single_mode_scenario (id INTEGER, max_speed INTEGER, max_stamina INTEGER, max_pow INTEGER, max_guts INTEGER, max_wiz INTEGER);",
    single_mode_training: "CREATE TABLE single_mode_training (command_id INTEGER, command_level INTEGER, command_type INTEGER, failure_rate INTEGER);",
    single_mode_training_effect: "CREATE TABLE single_mode_training_effect (command_id INTEGER, sub_id INTEGER, result_state INTEGER, target_type INTEGER, effect_value INTEGER, scenario_id INTEGER);",
    text_data: 'CREATE TABLE text_data (category INTEGER, "index" INTEGER, text TEXT);',
}

/**
 * The base and camp training boards, as the shipped database orders them.
 *
 * Deliberately kept in the shipped, counter-intuitive order: 102 is Power and 105 is Stamina on the
 * base board, while the camp board runs straight down Speed/Stamina/Power/Guts/Wit. A fixture that
 * "tidied" that would stop testing the thing that is easy to get wrong.
 */
const TRAINING_ROWS = [
    [101, { 1: 11, 3: 6, 10: -21, 30: 4 }],
    [102, { 2: 6, 3: 9, 10: -20, 30: 4 }],
    [103, { 1: 5, 3: 5, 4: 8, 10: -22, 30: 4 }],
    [105, { 2: 10, 4: 6, 10: -19, 30: 4 }],
    [106, { 1: 2, 5: 10, 10: 5, 30: 5 }],
    [601, { 1: 15, 3: 8, 10: -27, 30: 4 }],
    [602, { 2: 14, 4: 8, 10: -25, 30: 4 }],
    [603, { 2: 8, 3: 13, 10: -26, 30: 4 }],
    [604, { 1: 6, 3: 5, 4: 13, 10: -28, 30: 4 }],
    [605, { 1: 4, 5: 14, 10: 5, 30: 5 }],
]

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

    const rarity = db.prepare(`INSERT INTO card_rarity_data VALUES (${new Array(22).fill("?").join(", ")})`)
    for (const cardId of [100101, 100102]) {
        for (const star of [3, 4, 5]) {
            const step = (star - 3) * 10
            rarity.run(cardId, star, 83 + step, 88 + step, 98 + step, 90 + step, 91 + step, 1200, 1200, 1200, 1200, 1200, 2, 5, 7, 7, 7, 1, 1, 7, 7, 5)
        }
    }

    const scenario = db.prepare("INSERT INTO single_mode_scenario VALUES (?, ?, ?, ?, ?, ?)")
    scenario.run(1, 200, 200, 200, 200, 200)
    scenario.run(3, 400, 100, 100, 300, 100)

    const training = db.prepare("INSERT INTO single_mode_training VALUES (?, ?, ?, ?)")
    const trainingEffect = db.prepare("INSERT INTO single_mode_training_effect VALUES (?, ?, ?, ?, ?, ?)")
    for (const [commandId, targets] of TRAINING_ROWS) {
        for (let level = 1; level <= 5; level++) training.run(commandId, level, 1, 500 + level)
        for (const scenarioId of [1, 3]) {
            for (const [targetType, value] of Object.entries(targets)) {
                // Grand Concert pays two less secondary Stamina out of Power training than URA does.
                const shipped = scenarioId === 3 && commandId === 102 && targetType === "2" ? value - 2 : value
                trainingEffect.run(commandId, 1, 2, Number(targetType), shipped, scenarioId)
            }
        }
    }

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

test("splits the stat ceiling into the shared baseline and the per-scenario bonus", () => {
    const db = makeDb()
    const caps = buildStatCaps(db)
    assert.equal(caps.baseline, 1200)
    const gc = caps.scenarioBonus.find((s) => s.scenarioId === 3)
    assert.equal(gc.bonus.Speed, 400)
    assert.equal(gc.bonus.Stamina, 100)
    db.close()
})

test("rejects a per-card stat ceiling that is no longer shared", () => {
    const db = makeDb((d) => d.exec("UPDATE card_rarity_data SET max_stamina = 1400 WHERE card_id = 100101 AND rarity = 5"))
    assert.throws(() => buildStatCaps(db), GenerateError)
    db.close()
})

test("carries per-star starting stats and aptitude letters", () => {
    const db = makeDb()
    const base = buildTraineeBase(db)
    const five = base.find((b) => b.cardId === 100101 && b.starLevel === 5)
    assert.deepEqual(five.startStats, { Speed: 103, Stamina: 108, Power: 118, Guts: 110, Wit: 111 })
    assert.equal(five.aptitudes.medium, "A")
    assert.equal(five.aptitudes.dirt, "G")
    db.close()
})

test("rejects an aptitude index outside the decoded grade range", () => {
    const db = makeDb((d) => d.exec("UPDATE card_rarity_data SET proper_distance_long = 9 WHERE card_id = 100101"))
    assert.throws(() => buildTraineeBase(db), GenerateError)
    db.close()
})

test("derives the counter-intuitive command mapping rather than assuming id order", () => {
    const db = makeDb()
    const types = deriveCommandTrainingTypes(db)
    assert.equal(types[102], "Power")
    assert.equal(types[105], "Stamina")
    assert.equal(types[602], "Stamina")
    assert.equal(types[603], "Power")
    db.close()
})

test("rejects a board whose five trainings no longer raise five distinct stats", () => {
    const db = makeDb((d) => d.exec("UPDATE single_mode_training_effect SET effect_value = 1 WHERE command_id = 105 AND target_type = 2"))
    assert.throws(() => deriveCommandTrainingTypes(db), GenerateError)
    db.close()
})

test("carries Power training's secondary Stamina, and its per-scenario difference", () => {
    const db = makeDb()
    const effects = buildTrainingEffects(db)
    const ura = effects.find((e) => e.scenarioId === 1 && e.commandId === 102)
    const gc = effects.find((e) => e.scenarioId === 3 && e.commandId === 102)
    assert.equal(ura.trainingType, "Power")
    assert.equal(ura.stats.Stamina, 6)
    assert.equal(gc.stats.Stamina, 4)
    assert.equal(ura.stats.Power, gc.stats.Power)
    assert.equal(ura.skillPoints, 4)
    assert.equal(ura.energy, -20)
    assert.deepEqual(ura.failureRateByLevel, [501, 502, 503, 504, 505])
    db.close()
})

test("flags the camp board without assuming it shares the base board's id ordering", () => {
    const db = makeDb()
    const effects = buildTrainingEffects(db)
    assert.equal(effects.find((e) => e.commandId === 602 && e.scenarioId === 1).isCamp, true)
    assert.equal(effects.find((e) => e.commandId === 102 && e.scenarioId === 1).isCamp, false)
    db.close()
})
