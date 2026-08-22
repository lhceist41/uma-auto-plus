// Tests for the support-card extractor, using tiny synthetic in-memory SQLite fixtures.
//
// Run: node --test scripts/generate-support-card-data.test.mjs
//
// Nothing here touches the live master.mdb, the network, or any repo file. The point of the fixtures
// is the two things this extractor could get quietly wrong: the command_id mapping (whose obvious
// reading is wrong) and the level-threshold curve (which does not interpolate).

import test from "node:test"
import assert from "node:assert/strict"
import { DatabaseSync } from "node:sqlite"
import { buildPayload, serializePayload, verifyCommandMapping, EFFECT_LEVEL_THRESHOLDS } from "./generate-support-card-data.mjs"

const TABLES = {
    support_card_data:
        "CREATE TABLE support_card_data (id INTEGER, chara_id INTEGER, rarity INTEGER, effect_table_id INTEGER, unique_effect_id INTEGER, command_type INTEGER, command_id INTEGER, support_card_type INTEGER, skill_set_id INTEGER);",
    support_card_effect_table:
        "CREATE TABLE support_card_effect_table (id INTEGER, type INTEGER, init INTEGER, limit_lv5 INTEGER, limit_lv10 INTEGER, limit_lv15 INTEGER, limit_lv20 INTEGER, limit_lv25 INTEGER, limit_lv30 INTEGER, limit_lv35 INTEGER, limit_lv40 INTEGER, limit_lv45 INTEGER, limit_lv50 INTEGER);",
    support_card_unique_effect:
        "CREATE TABLE support_card_unique_effect (id INTEGER, lv INTEGER, type_0 INTEGER, value_0 INTEGER, type_1 INTEGER, value_1 INTEGER);",
    support_card_limit: "CREATE TABLE support_card_limit (rarity INTEGER, limit_0 INTEGER, limit_1 INTEGER, limit_2 INTEGER, limit_3 INTEGER, limit_4 INTEGER);",
    support_card_group: "CREATE TABLE support_card_group (id INTEGER, support_card_id INTEGER, chara_id INTEGER, outing_max INTEGER);",
    single_mode_hint_gain: "CREATE TABLE single_mode_hint_gain (id INTEGER, hint_id INTEGER, support_card_id INTEGER, hint_value_1 INTEGER);",
    single_mode_restrict_support: "CREATE TABLE single_mode_restrict_support (id INTEGER, scenario_id INTEGER, support_card_id INTEGER);",
    single_mode_special_chara: "CREATE TABLE single_mode_special_chara (id INTEGER, scenario_id INTEGER, chara_id INTEGER);",
    single_mode_training_effect: "CREATE TABLE single_mode_training_effect (id INTEGER, command_id INTEGER, sub_id INTEGER, result_state INTEGER, target_type INTEGER, effect_value INTEGER, scenario_id INTEGER);",
    text_data: "CREATE TABLE text_data (category INTEGER, [index] INTEGER, text TEXT);",
}

/** The real per-command training profiles, which are what prove the command mapping. */
const TRAINING_PROFILE = [
    [101, 1, 11],
    [101, 3, 6],
    [102, 3, 9],
    [102, 2, 6],
    [103, 4, 8],
    [103, 1, 5],
    [105, 2, 10],
    [105, 4, 6],
    [106, 5, 10],
    [106, 1, 2],
]

function makeDb({ cards, effects, uniques, training = TRAINING_PROFILE, text, omitTable = null, limits } = {}) {
    const db = new DatabaseSync(":memory:")
    for (const [name, ddl] of Object.entries(TABLES)) {
        if (name === omitTable) continue
        db.exec(ddl)
    }

    const insertCard = db.prepare("INSERT INTO support_card_data VALUES (?,?,?,?,?,?,?,?,?)")
    for (const c of cards ?? [[30001, 1001, 3, 30001, 0, 1, 101, 1, 0]]) insertCard.run(...c)

    const insertEffect = db.prepare("INSERT INTO support_card_effect_table VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")
    // One Friendship Bonus curve: 5 at level 1, 10 from level 25, 15 at level 50.
    for (const e of effects ?? [[30001, 1, 5, -1, -1, -1, -1, 10, -1, -1, -1, -1, 15]]) insertEffect.run(...e)

    const insertUnique = db.prepare("INSERT INTO support_card_unique_effect VALUES (?,?,?,?,?,?)")
    for (const u of uniques ?? []) insertUnique.run(...u)

    const insertTraining = db.prepare("INSERT INTO single_mode_training_effect VALUES (NULL,?,1,2,?,?,1)")
    for (const t of training) insertTraining.run(...t)

    const insertLimit = db.prepare("INSERT INTO support_card_limit VALUES (?,?,?,?,?,?)")
    for (const l of limits ?? [
        [1, 20, 25, 30, 35, 40],
        [2, 25, 30, 35, 40, 45],
        [3, 30, 35, 40, 45, 50],
    ]) {
        insertLimit.run(...l)
    }

    const insertText = db.prepare("INSERT INTO text_data VALUES (?,?,?)")
    for (const t of text ?? [
        [6, 1001, "Special Week"],
        [150, 30001, "Take Hold of Our <I>Dreams</I>!"],
        [151, 1, "Friendship Bonus"],
    ]) {
        insertText.run(...t)
    }
    return db
}

test("resolves a card into named rarity, support type and title with markup stripped", () => {
    const payload = buildPayload(makeDb())
    assert.equal(payload.cards.length, 1)
    const card = payload.cards[0]
    assert.equal(card.rarity, "SSR")
    assert.equal(card.supportType, "Speed")
    assert.equal(card.title, "Take Hold of Our Dreams!")
    assert.equal(card.charaId, 1001)
    assert.equal(payload.characters["1001"], "Special Week")
})

test("command 102 is Power and 105 is Stamina, not the other way round", () => {
    const payload = buildPayload(
        makeDb({
            cards: [
                [30001, 1001, 3, 30001, 0, 1, 102, 1, 0],
                [30002, 1001, 3, 30001, 0, 1, 105, 1, 0],
                [30003, 1001, 3, 30001, 0, 1, 103, 1, 0],
            ],
            text: [
                [6, 1001, "Special Week"],
                [150, 30001, "A"],
                [150, 30002, "B"],
                [150, 30003, "C"],
            ],
        }),
    )
    assert.deepEqual(
        payload.cards.map((c) => c.supportType),
        ["Power", "Stamina", "Guts"],
    )
})

test("refuses to write when the training tables stop agreeing with the command mapping", () => {
    // Swap the profiles of 102 and 105 so the argmax disagrees with the shipped constant.
    const swapped = [
        [101, 1, 11],
        [102, 2, 10],
        [103, 4, 8],
        [105, 3, 9],
        [106, 5, 10],
    ]
    assert.throws(() => verifyCommandMapping(makeDb({ training: swapped })), /disagree/)
    assert.throws(() => buildPayload(makeDb({ training: swapped })), /disagree/)
})

test("refuses to write when a training command has no rows left to prove it", () => {
    const partial = TRAINING_PROFILE.filter(([id]) => id !== 106)
    assert.throws(() => buildPayload(makeDb({ training: partial })), /can no longer be proven/)
})

test("the effect curve keeps -1 holes rather than interpolating them", () => {
    const payload = buildPayload(makeDb())
    const curve = payload.cards[0].effects[0].curve
    assert.equal(curve.length, EFFECT_LEVEL_THRESHOLDS.length)
    assert.deepEqual(curve, [5, -1, -1, -1, -1, 10, -1, -1, -1, -1, 15])
})

test("carries the unique effect, its unlock level, and flags an undecoded type without inventing a value", () => {
    const payload = buildPayload(
        makeDb({
            cards: [[30001, 1001, 3, 30001, 20001, 1, 101, 1, 0]],
            uniques: [[20001, 40, 101, 1, 8, 20]],
            text: [
                [6, 1001, "Special Week"],
                [150, 30001, "A"],
                [151, 8, "Training Effectiveness"],
                [155, 30001, "Training Effectiveness (Friendship Gauge 80+)"],
            ],
        }),
    )
    const unique = payload.cards[0].uniqueEffect
    assert.equal(unique.unlockLevel, 40)
    assert.equal(unique.description, "Training Effectiveness (Friendship Gauge 80+)")
    assert.deepEqual(unique.effects, [{ type: 8, value: 20 }])
    assert.deepEqual(unique.undecodedTypes, [101])
})

test("keeps R cards in the catalogue with a null title", () => {
    const payload = buildPayload(
        makeDb({
            cards: [
                [10001, 1001, 1, 30001, 0, 1, 101, 1, 0],
                [30001, 1001, 3, 30001, 0, 1, 101, 1, 0],
            ],
        }),
    )
    assert.equal(payload.cards.length, 2)
    const r = payload.cards.find((c) => c.rarity === "R")
    assert.equal(r.title, null)
})

test("fails closed when no card has a title at all", () => {
    assert.throws(() => buildPayload(makeDb({ text: [[6, 1001, "Special Week"]] })), /not the title category/)
})

test("fails closed on a missing table and on an unmapped support card type", () => {
    assert.throws(() => buildPayload(makeDb({ omitTable: "support_card_group" })), /has no support_card_group table/)
    assert.throws(() => buildPayload(makeDb({ cards: [[30001, 1001, 3, 30001, 0, 1, 101, 9, 0]] })), /unmapped support_card_type 9/)
})

test("fails closed when a card names a unique effect the table does not have", () => {
    assert.throws(() => buildPayload(makeDb({ cards: [[30001, 1001, 3, 30001, 999, 1, 101, 1, 0]] })), /which support_card_unique_effect does not have/)
})

test("carries scenario restrictions, group members and hint skills, each sorted", () => {
    const db = makeDb({ cards: [[30081, 1001, 3, 30001, 0, 0, 0, 3, 0]], text: [[6, 1001, "Special Week"], [150, 30081, "Passing the Dream On"]] })
    db.exec("INSERT INTO single_mode_restrict_support VALUES (1,3,30081),(2,2,30081);")
    db.exec("INSERT INTO support_card_group VALUES (1,30081,1030,1),(2,30081,1013,1);")
    db.exec("INSERT INTO single_mode_hint_gain VALUES (1,9,30081,200512),(2,9,30081,200162);")
    db.exec("INSERT INTO single_mode_special_chara VALUES (1,3,1002),(2,3,9008);")
    const card = buildPayload(db).cards[0]
    assert.equal(card.supportType, "Group")
    assert.deepEqual(card.restrictedScenarioIds, [2, 3])
    assert.deepEqual(card.groupMemberCharaIds, [1013, 1030])
    assert.deepEqual(card.hintSkillIds, [200162, 200512])
    assert.deepEqual(buildPayload(db).scenarioSpecialCharacters, [{ scenarioId: 3, specialCharaIds: [1002, 9008] }])
})

test("serialization is byte-stable across two builds of the same database", () => {
    const a = serializePayload(buildPayload(makeDb()))
    const b = serializePayload(buildPayload(makeDb()))
    assert.equal(a, b)
    assert.ok(a.endsWith("\n"))
})
