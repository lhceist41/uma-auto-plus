// Tests for the race-survival extractor, using tiny synthetic in-memory SQLite fixtures.
//
// Run: node --test scripts/generate-race-survival-data.test.mjs
//
// Nothing here touches the live master.mdb, the network, or any repo file. The point of the fixtures
// is the four things this extractor could get quietly wrong: the fixed-point scale on the finish-time
// band, the sign convention on ability_type 9, the condition enums that a witness skill proves, and
// the aptitude-grade ordering that the whole G..S mapping rests on. Each has a passing fixture and a
// fixture that breaks it, because a verification that cannot fail is not a verification.

import test from "node:test"
import assert from "node:assert/strict"
import { DatabaseSync } from "node:sqlite"
import { buildPayload, serializePayload, verifyAptitudeOrdering, verifyConditionEnums, verifyFixedPointScale, verifyGroundEnum, verifyHpAbilityType, GenerateError, FIXED_POINT_DIVISOR, HP_ABILITY_TYPE } from "./generate-race-survival-data.mjs"

const EFFECT_COLUMNS = []
for (const slot of ["1_1", "1_2", "1_3", "2_1", "2_2", "2_3"]) {
    EFFECT_COLUMNS.push(`ability_type_${slot} INTEGER`, `float_ability_value_${slot} INTEGER`, `target_type_${slot} INTEGER`, `target_value_${slot} INTEGER`)
}

const TABLES = {
    race_course_set:
        "CREATE TABLE race_course_set (id INTEGER, race_track_id INTEGER, distance INTEGER, ground INTEGER, inout INTEGER, turn INTEGER, course_set_status_id INTEGER, finish_time_min INTEGER, finish_time_min_random_range INTEGER, finish_time_max INTEGER, finish_time_max_random_range INTEGER);",
    race_proper_distance_rate: "CREATE TABLE race_proper_distance_rate (id INTEGER, proper_rate_speed INTEGER, proper_rate_power INTEGER);",
    race_proper_ground_rate: "CREATE TABLE race_proper_ground_rate (id INTEGER, proper_rate INTEGER);",
    race_proper_runningstyle_rate: "CREATE TABLE race_proper_runningstyle_rate (id INTEGER, proper_rate INTEGER);",
    skill_data: `CREATE TABLE skill_data (id INTEGER, rarity INTEGER, condition_1 TEXT, precondition_1 TEXT, condition_2 TEXT, precondition_2 TEXT, float_cooldown_time_1 INTEGER, float_cooldown_time_2 INTEGER, ${EFFECT_COLUMNS.join(", ")});`,
    text_data: 'CREATE TABLE text_data (category INTEGER, "index" INTEGER, text TEXT);',
}

/** The four tracks the ground-enum proof needs, plus two normal ones. */
const TRACKS = [
    [10001, "Sapporo"],
    [10006, "Tokyo"],
    [10101, "Oi"],
    [10103, "Kawasaki"],
    [10104, "Funabashi"],
    [10105, "Morioka"],
]

/** Course rows: turf on the two JRA tracks, dirt on the four the ground proof uses. */
const COURSES = [
    [10104, 10001, 2000, 1, 1, 1, 3, 1171000, 10000, 1230000, 10000],
    [10604, 10006, 1600, 1, 1, 2, 0, 921000, 10000, 970000, 10000],
    [11103, 10101, 2000, 2, 1, 1, 2, 1219000, 10000, 1290000, 10000],
    [11301, 10103, 1600, 2, 1, 1, 0, 960000, 10000, 1020000, 10000],
    [11401, 10104, 1000, 2, 1, 2, 1, 580000, 10000, 630000, 10000],
    [11501, 10105, 1600, 2, 1, 2, 2, 960000, 10000, 1020000, 10000],
]

/** The witness skills every enum proof rests on, plus enough HP skills to clear the witness floors. */
const SKILLS = [
    // [id, rarity, condition_1, desc, name, effects: [type, value, targetType, targetValue]]
    [201142, 1, "distance_type==3&is_move_lane==1", "Slightly decrease fatigue when moving sideways. (Medium)", "Soft Step", [[9, 150, 1, 0]]],
    [200742, 1, "distance_type==4&phase_random==1", "Take a breather and slightly decrease fatigue mid-race. (Long)", "Deep Breaths", [[9, 150, 1, 0]]],
    [201282, 1, "running_style==1&slope==1", "Slightly reduce fatigue on an uphill. (Front Runner)", "Moxie", [[9, 150, 1, 0]]],
    [200572, 1, "running_style==2&phase_random==1", "Slightly decrease fatigue mid-race. (Pace Chaser)", "Preferred Position", [[9, 150, 1, 0]]],
    [201422, 1, "running_style==3&phase_random==1", "Slightly recover endurance when positioned midpack mid-race. (Late Surger)", "A Small Breather", [[9, 150, 1, 0]]],
    [200622, 1, "running_style==4&phase_random==1", "Slightly decrease fatigue mid-race. (End Closer)", "Standing By", [[9, 150, 1, 0]]],
    [202002, 1, "ground_type==2&phase_random==1", "Slightly recover endurance when positioned toward the back mid-race. (Dirt)", "Familiar Ground", [[9, 150, 1, 0]]],
    [200481, 2, "is_surrounded==1", "Recover endurance when surrounded mid-race.", "Unruffled", [[9, 550, 1, 0]]],
    [200482, 1, "is_surrounded==1", "Slightly recover endurance when surrounded mid-race.", "Calm in a Crowd", [[9, 150, 1, 0]]],
    [200391, 1, "slope==1", "Moderately increase fatigue on an uphill.", "Ramp Revulsion", [[9, -200, 1, 0]]],
    [200401, 1, "is_surrounded==1", "Moderately lose endurance when surrounded.", "Packphobia", [[9, -200, 1, 0]]],
    [200521, 1, "distance_diff_rate>=90", "Moderately increase fatigue when in the lead by too large of a margin.", "Running Idle", [[9, -200, 1, 0]]],
    [201291, 2, "phase==2", "Moderately expend endurance to increase acceleration late-race. (Front Runner)", "Reignition", [[9, -200, 1, 0]]],
    [202151, 2, "phase==1", "Moderately expend endurance to greatly increase velocity mid-race. (Late Surger)", "Keep Going!", [[9, -200, 1, 0]]],
    [201161, 2, "distance_type==3&phase==1", "Disturb runners directly ahead mid-race. (Medium)", "Mystifying Murmur", [[9, -300, 9, 18]]],
    [201162, 1, "distance_type==3&phase==1", "Slightly disturb runners directly ahead mid-race. (Medium)", "Murmur", [[9, -100, 9, 18]]],
    [200831, 1, "phase==0", "Slightly increase fatigue for front runners early-race.", "Subdued Front Runners", [[9, -100, 18, 1]]],
    [200861, 1, "phase==0", "Slightly increase fatigue for pace chasers early-race.", "Subdued Pace Chasers", [[9, -100, 18, 2]]],
    [200891, 1, "phase==0", "Slightly increase fatigue for late surgers early-race.", "Subdued Late Surgers", [[9, -100, 18, 3]]],
    [201441, 2, "running_style==3&phase==2", "Startle other runners late-race. (Late Surger)", "All-Seeing Eyes", [[9, -300, 4, 18]]],
    [201221, 2, "distance_type==4&order_rate>=50", "Slightly steal endurance from runners ahead mid-race. (Long)", "Stamina Siphon", [[9, -100, 9, 5], [9, 350, 1, 0]]],
    [300101, 3, "is_exist_chara_id==1", "Recover a fellow dreamer's endurance on a corner.", "Cheers of a Fellow Dreamer", [[9, 550, 22, 1]]],
]

// Twelve more plain white recoveries, so the recovery witness floor of 20 is cleared by real rows
// rather than by lowering the floor.
for (let i = 0; i < 12; i++) {
    SKILLS.push([210001 + i, 1, "phase_random==1", "Slightly recover endurance mid-race.", `Filler Recovery ${i}`, [[9, 150, 1, 0]]])
}

function buildFixtureDb(overrides = {}) {
    const db = new DatabaseSync(":memory:")
    for (const sql of Object.values(TABLES)) db.exec(sql)

    const courses = overrides.courses ?? COURSES
    const insertCourse = db.prepare("INSERT INTO race_course_set VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    for (const row of courses) insertCourse.run(...row)

    const rates = overrides.aptitudeRates ?? [0, 1000, 2000, 4000, 6000, 8000, 9000, 10000, 10500]
    const insertDistance = db.prepare("INSERT INTO race_proper_distance_rate VALUES (?, ?, ?)")
    const insertGround = db.prepare("INSERT INTO race_proper_ground_rate VALUES (?, ?)")
    const insertStyle = db.prepare("INSERT INTO race_proper_runningstyle_rate VALUES (?, ?)")
    rates.forEach((rate, id) => {
        insertDistance.run(id, rate, rate)
        insertGround.run(id, rate)
        insertStyle.run(id, rate)
    })

    const insertText = db.prepare('INSERT INTO text_data VALUES (?, ?, ?)')
    for (const [id, name] of TRACKS) insertText.run(35, id, name)

    const slots = ["1_1", "1_2", "1_3", "2_1", "2_2", "2_3"]
    const columns = ["id", "rarity", "condition_1", "precondition_1", "condition_2", "precondition_2", "float_cooldown_time_1", "float_cooldown_time_2"]
    for (const slot of slots) columns.push(`ability_type_${slot}`, `float_ability_value_${slot}`, `target_type_${slot}`, `target_value_${slot}`)
    const insertSkill = db.prepare(`INSERT INTO skill_data (${columns.join(", ")}) VALUES (${columns.map(() => "?").join(", ")})`)

    for (const [id, rarity, condition, desc, name, effects] of overrides.skills ?? SKILLS) {
        const values = [id, rarity, condition, "", "", "", 5000000, 0]
        for (let i = 0; i < slots.length; i++) {
            const effect = effects[i]
            values.push(effect ? effect[0] : 0, effect ? effect[1] : 0, effect ? effect[2] : 0, effect ? effect[3] : 0)
        }
        insertSkill.run(...values)
        insertText.run(47, id, name)
        insertText.run(48, id, desc)
    }
    return db
}

test("builds a payload whose courses carry the decoded finish-time band", () => {
    const db = buildFixtureDb()
    const payload = buildPayload(db)
    assert.equal(payload.schema, "race_survival_evidence")
    assert.equal(payload.schemaVersion, 1)
    assert.equal(payload.fixedPointDivisor, FIXED_POINT_DIVISOR)
    assert.equal(payload.hpAbilityType, HP_ABILITY_TYPE)

    const oi = payload.courses.find((c) => c.id === 11103)
    assert.equal(oi.track, "Oi")
    assert.equal(oi.ground, "dirt")
    assert.equal(oi.finishTimeMinRaw / FIXED_POINT_DIVISOR, 121.9)
    assert.equal(oi.finishTimeMaxRaw / FIXED_POINT_DIVISOR, 129.0)
    db.close()
})

test("carries every HP skill with its exact value, target and conditions", () => {
    const db = buildFixtureDb()
    const payload = buildPayload(db)
    const murmur = payload.hpSkillEffects.find((s) => s.id === 201161)
    assert.equal(murmur.name, "Mystifying Murmur")
    assert.equal(murmur.rarity, 2)
    assert.deepEqual(murmur.effects, [{ slot: "1_1", hpValueRaw: -300, targetType: 9, targetValue: 18 }])
    assert.equal(murmur.condition1, "distance_type==3&phase==1")
    assert.equal(murmur.cooldown1Raw, 5000000)

    // A skill with no HP effect never appears, and a steal skill keeps both halves.
    const siphon = payload.hpSkillEffects.find((s) => s.id === 201221)
    assert.equal(siphon.effects.length, 2)
    db.close()
})

test("emits ids ascending and serializes to pure ASCII with a trailing newline", () => {
    const db = buildFixtureDb()
    const text = serializePayload(buildPayload(db))
    assert.ok(text.endsWith("\n"))
    assert.ok(!/[^\x00-\x7f]/.test(text), "payload must be pure ASCII")
    const ids = JSON.parse(text).hpSkillEffects.map((s) => s.id)
    assert.deepEqual(ids, [...ids].sort((a, b) => a - b))
    db.close()
})

test("is deterministic: the same database serializes byte-identically", () => {
    const a = buildFixtureDb()
    const b = buildFixtureDb()
    assert.equal(serializePayload(buildPayload(a)), serializePayload(buildPayload(b)))
    a.close()
    b.close()
})

test("verifyFixedPointScale rejects a finish time that leaves the plausible seconds band", () => {
    assert.equal(verifyFixedPointScale([{ id: 1, finish_time_min: 1171000, finish_time_max: 1230000 }]), true)
    assert.throws(() => verifyFixedPointScale([{ id: 1, finish_time_min: 117, finish_time_max: 123 }]), GenerateError)
    assert.throws(() => verifyFixedPointScale([{ id: 1, finish_time_min: 1230000, finish_time_max: 1171000 }]), GenerateError)
})

test("verifyHpAbilityType rejects a heal aimed at the opposing field", () => {
    const descriptions = new Map([[1, "Slightly recover endurance mid-race."]])
    assert.throws(() => verifyHpAbilityType([{ skillId: 1, hpValueRaw: 300, targetType: 9, targetValue: 18 }], descriptions), GenerateError)
})

test("verifyHpAbilityType rejects an undecoded target type rather than passing it through", () => {
    const descriptions = new Map([[1, "Does something."]])
    assert.throws(() => verifyHpAbilityType([{ skillId: 1, hpValueRaw: 300, targetType: 77, targetValue: 0 }], descriptions), GenerateError)
})

test("verifyHpAbilityType rejects a description and a sign that disagree", () => {
    const db = buildFixtureDb({
        skills: SKILLS.map((s) => (s[0] === 200481 ? [200481, 2, "is_surrounded==1", "Recover endurance when surrounded mid-race.", "Unruffled", [[9, -550, 1, 0]]] : s)),
    })
    assert.throws(() => buildPayload(db), GenerateError)
    db.close()
})

test("verifyConditionEnums rejects a witness whose condition stops matching its description", () => {
    const descriptions = new Map([[200742, "Take a breather and slightly decrease fatigue mid-race. (Long)"]])
    const conditions = new Map([[200742, "distance_type==2&phase_random==1"]])
    assert.throws(() => verifyConditionEnums(conditions, descriptions), GenerateError)
})

test("verifyAptitudeOrdering rejects a table that stops ascending over ids 1..8", () => {
    const good = [0, 1000, 2000, 4000, 6000, 8000, 9000, 10000, 10500].map((rate, id) => ({ id, proper_rate: rate }))
    assert.equal(verifyAptitudeOrdering("t", good, ["proper_rate"]), true)
    const bad = good.map((row) => (row.id === 5 ? { id: 5, proper_rate: 100 } : row))
    assert.throws(() => verifyAptitudeOrdering("t", bad, ["proper_rate"]), GenerateError)
})

test("verifyGroundEnum rejects a dirt-only track carrying a turf ground value", () => {
    assert.equal(verifyGroundEnum(COURSES.map((c) => ({ id: c[0], race_track_id: c[1], ground: c[3] }))), true)
    const flipped = COURSES.map((c) => ({ id: c[0], race_track_id: c[1], ground: c[1] === 10101 ? 1 : c[3] }))
    assert.throws(() => verifyGroundEnum(flipped), GenerateError)
})

test("verifyGroundEnum rejects a ground value outside the decoded pair", () => {
    const stray = COURSES.map((c) => ({ id: c[0], race_track_id: c[1], ground: c[0] === 10604 ? 3 : c[3] }))
    assert.throws(() => verifyGroundEnum(stray), GenerateError)
})
