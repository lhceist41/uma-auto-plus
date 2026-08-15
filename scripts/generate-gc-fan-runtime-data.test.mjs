// Tests for the Grand Concert fan-runtime asset generator: synthetic fixtures for shape/validation
// and the real committed data for the expected Copano facts and byte-identity of the checked-in
// asset.
//
// Run: node --test scripts/generate-gc-fan-runtime-data.test.mjs

import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { dirname, join } from "node:path"
import { buildCharacter, buildPayload, computeUniversalFloor, serialize, GenerateError, ASSET_PATH } from "./generate-gc-fan-runtime-data.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const objectives = JSON.parse(readFileSync(join(REPO, "src", "data", "character_objectives.json"), "utf8"))
const races = JSON.parse(readFileSync(join(REPO, "src", "data", "races.json"), "utf8"))

// A tiny 18-place monotone curve whose minimum is `min`.
const curve = (first, min) => Array.from({ length: 18 }, (_, i) => ({ place: i + 1, fans: i === 17 ? min : Math.max(min, first - i * 10) }))
const racesFixture = { "A (x)": { fanPayoutsByPlace: curve(500, 7) }, "B (y)": { fanPayoutsByPlace: curve(300, 9) } }

test("computeUniversalFloor returns the global minimum across all curves", () => {
    assert.equal(computeUniversalFloor(racesFixture), 7)
})

test("computeUniversalFloor rejects a missing, short, or non-positive curve", () => {
    assert.throws(() => computeUniversalFloor({ "A (x)": {} }), GenerateError)
    assert.throws(() => computeUniversalFloor({ "A (x)": { fanPayoutsByPlace: curve(500, 7).slice(0, 14) } }), GenerateError)
    const bad = curve(500, 7)
    bad[5].fans = 0
    assert.throws(() => computeUniversalFloor({ "A (x)": { fanPayoutsByPlace: bad } }), GenerateError)
})

test("buildCharacter keeps only Grand-Concert-applicable fan goals, sorted", () => {
    const built = buildCharacter("Test", {
        fanGoals: [
            { turn: 30, targetFans: 5000, scenarioGroupId: 701, appliesToScenarioIds: [1, 2, 3] },
            { turn: 24, targetFans: 3000, scenarioGroupId: 100, appliesToScenarioIds: [1, 2, 3, 4] },
            { turn: 20, targetFans: 1000, scenarioGroupId: 4, appliesToScenarioIds: [4] }, // Trackblazer-only -> excluded
        ],
        mandatoryRaces: [{ turn: 40, options: [{ raceName: "R", fansNeeded: 12000 }] }],
    })
    assert.deepEqual(built.fanGoals, [
        { turn: 24, targetFans: 3000 },
        { turn: 30, targetFans: 5000 },
    ])
})

test("buildCharacter preserves choice-turn options and flags isChoice", () => {
    const built = buildCharacter("Test", {
        fanGoals: [],
        mandatoryRaces: [{ turn: 30, options: [{ raceName: "Spring", fansNeeded: 1750 }, { raceName: "Mainichi", fansNeeded: 1250 }] }],
    })
    assert.equal(built.mandatoryRaces[0].isChoice, true)
    assert.deepEqual(built.mandatoryRaces[0].options, [{ raceName: "Spring", fansNeeded: 1750 }, { raceName: "Mainichi", fansNeeded: 1250 }])
})

test("buildCharacter drops a turn whose every option needs 0 fans, and drops an empty character", () => {
    const built = buildCharacter("Test", { fanGoals: [], mandatoryRaces: [{ turn: 12, options: [{ raceName: "Debut", fansNeeded: 0 }] }] })
    assert.equal(built, null)
})

test("buildCharacter throws on an absent or malformed fansNeeded (absent is not 0)", () => {
    assert.throws(() => buildCharacter("Test", { fanGoals: [], mandatoryRaces: [{ turn: 40, options: [{ raceName: "R" }] }] }), GenerateError)
    assert.throws(() => buildCharacter("Test", { fanGoals: [], mandatoryRaces: [{ turn: 40, options: [{ raceName: "R", fansNeeded: -1 }] }] }), GenerateError)
})

test("buildPayload from committed data: Copano fan facts are exactly as expected", () => {
    const payload = buildPayload(objectives, races)
    const copano = payload.characters["Copano Rickey"]
    assert.deepEqual(copano.fanGoals, [{ turn: 24, targetFans: 3000 }])
    const gate = (t) => copano.mandatoryRaces.find((m) => m.turn === t)
    assert.equal(gate(31).options[0].fansNeeded, 350)
    assert.equal(gate(47).options[0].fansNeeded, 12000)
    assert.equal(gate(52).options[0].fansNeeded, 12000)
    assert.equal(payload.universalCompletedRaceFanFloor, 7)
})

test("buildPayload preserves a multiple-goal character and a character with no fan goal", () => {
    const payload = buildPayload(objectives, races)
    assert.equal(payload.characters["Haru Urara"].fanGoals.length, 3)
    const admire = payload.characters["Admire Vega"]
    assert.equal(admire.fanGoals.length, 0)
    assert.ok(admire.mandatoryRaces.length > 0)
})

test("buildPayload preserves a differing-threshold choice turn without flattening it", () => {
    const payload = buildPayload(objectives, races)
    const t30 = payload.characters["Matikanefukukitaru"].mandatoryRaces.find((m) => m.turn === 30)
    assert.equal(t30.isChoice, true)
    const needs = t30.options.map((o) => o.fansNeeded).sort((a, b) => a - b)
    assert.deepEqual(needs, [1250, 1750])
})

test("serialization is deterministic and the committed asset is up to date", () => {
    const text = serialize(buildPayload(objectives, races))
    assert.equal(text, serialize(buildPayload(objectives, races))) // stable
    const committed = readFileSync(ASSET_PATH, "utf8")
    assert.equal(committed, text, "gc_fan_runtime.json is stale; re-run scripts/generate-gc-fan-runtime-data.mjs")
})

test("every emitted fan goal in the committed asset is Grand-Concert-applicable and well-formed", () => {
    const payload = buildPayload(objectives, races)
    let goals = 0
    for (const c of Object.values(payload.characters)) {
        for (const g of c.fanGoals) {
            goals++
            assert.ok(Number.isInteger(g.turn) && g.turn > 0)
            assert.ok(Number.isInteger(g.targetFans) && g.targetFans > 0)
            assert.deepEqual(Object.keys(g).sort(), ["targetFans", "turn"])
        }
    }
    assert.ok(goals >= 20) // 25 across the current roster; guards against silent loss
})
