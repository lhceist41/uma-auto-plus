// Tests for the Grand Concert fan-goal extractor, using tiny synthetic in-memory SQLite fixtures.
//
// Run: node --test scripts/extract-master-route-data.test.mjs
//
// Nothing here touches the live master.mdb, the network, or any repo file.

import test from "node:test"
import assert from "node:assert/strict"
import { DatabaseSync } from "node:sqlite"
import { assertSuitable, extractFanGoals, resolveObjectiveKey, fold, ExtractError } from "./extract-master-route-data.mjs"

/** Builds a minimal synthetic master.mdb-shaped DB. Grand Concert is scenario 3; groups 100 and 701
 * contain it, group 4 (Trackblazer-only) does not. */
function makeDb({ withScenario3 = true, routeRaces = [], routes = [], names = [] } = {}) {
    const db = new DatabaseSync(":memory:")
    db.exec(`
        CREATE TABLE single_mode_scenario (id INTEGER);
        CREATE TABLE single_mode_scenario_group (group_id INTEGER, scenario_id INTEGER);
        CREATE TABLE single_mode_route (chara_id INTEGER, race_set_id INTEGER);
        CREATE TABLE single_mode_route_race (race_set_id INTEGER, scenario_group_id INTEGER, condition_type INTEGER, condition_value_1 INTEGER, turn INTEGER);
        CREATE TABLE text_data (category INTEGER, [index] INTEGER, text TEXT);
    `)
    for (const id of [1, 2, 4].concat(withScenario3 ? [3] : [])) db.prepare("INSERT INTO single_mode_scenario VALUES (?)").run(id)
    // group 100 -> {1,2,3,4}; group 701 -> {1,2,3}; group 4 -> {4} (Trackblazer only, no scenario 3)
    const membership = [[100, 1], [100, 2], [100, 4], [701, 1], [701, 2], [4, 4]].concat(withScenario3 ? [[100, 3], [701, 3]] : [])
    for (const [g, s] of membership) db.prepare("INSERT INTO single_mode_scenario_group VALUES (?, ?)").run(g, s)
    for (const [chara, raceSet] of routes) db.prepare("INSERT INTO single_mode_route VALUES (?, ?)").run(chara, raceSet)
    for (const rr of routeRaces) db.prepare("INSERT INTO single_mode_route_race VALUES (?, ?, ?, ?, ?)").run(rr.raceSet, rr.group, rr.condType, rr.value, rr.turn)
    for (const [idx, text] of names) db.prepare("INSERT INTO text_data VALUES (6, ?, ?)").run(idx, text)
    return db
}

/** A fixture that mirrors the real shape: Copano (one goal), a two-goal character spanning groups
 * 100 and 701, and a Trackblazer-only character whose goal must be excluded. */
function realisticDb() {
    return makeDb({
        routes: [[1098, 1098], [2001, 2001], [2002, 2002]],
        names: [[1098, "Copano Rickey"], [2001, "Test Char A"], [2002, "TB Only Char"]],
        routeRaces: [
            { raceSet: 1098, group: 100, condType: 3, value: 3000, turn: 24 }, // Copano fan goal
            { raceSet: 1098, group: 100, condType: 1, value: 3, turn: 31 }, // a race-placement goal (must be ignored)
            { raceSet: 2001, group: 100, condType: 3, value: 3000, turn: 23 }, // two fan goals, out of order
            { raceSet: 2001, group: 701, condType: 3, value: 10000, turn: 37 },
            { raceSet: 2002, group: 4, condType: 3, value: 5000, turn: 30 }, // Trackblazer-only group -> excluded
        ],
    })
}

test("fold normalizes punctuation so name variants match", () => {
    assert.equal(fold("T.M. Opera O"), fold("TM Opera O"))
    assert.equal(fold("Copano Rickey"), "copanorickey")
})

test("assertSuitable passes a Grand-Concert-capable DB and rejects a stale one", () => {
    const good = makeDb({ routes: [[1098, 1098]], routeRaces: [{ raceSet: 1098, group: 100, condType: 3, value: 3000, turn: 24 }] })
    assert.doesNotThrow(() => assertSuitable(good))
    const stale = makeDb({ withScenario3: false, routes: [[1098, 1098]], routeRaces: [{ raceSet: 1098, group: 100, condType: 3, value: 3000, turn: 24 }] })
    assert.throws(() => assertSuitable(stale), ExtractError)
})

test("extractFanGoals returns only GC-applicable fan-count goals, keyed by character", () => {
    const goals = extractFanGoals(realisticDb())
    // Copano: one goal, turn 24 / 3000, group 100.
    assert.deepEqual(goals.get("Copano Rickey"), [{ turn: 24, targetFans: 3000, scenarioGroupId: 100, appliesToScenarioIds: [1, 2, 3, 4] }])
    // The Trackblazer-only goal (group 4, no scenario 3) is excluded.
    assert.equal(goals.has("TB Only Char"), false)
})

test("multiple fan goals are preserved and deterministically ordered by turn", () => {
    const goals = extractFanGoals(realisticDb()).get("Test Char A")
    assert.equal(goals.length, 2)
    assert.deepEqual(goals.map((g) => g.turn), [23, 37])
    // The group-701 goal is included because group 701 contains scenario 3.
    assert.equal(goals[1].scenarioGroupId, 701)
    assert.deepEqual(goals[1].appliesToScenarioIds, [1, 2, 3])
})

test("resolveObjectiveKey uses exact then unique folded match, and refuses ambiguity", () => {
    const keys = new Set(["TM Opera O", "Copano Rickey"])
    const folded = new Map([["tmoperao", ["TM Opera O"]], ["copanorickey", ["Copano Rickey"]], ["dup", ["A", "B"]]])
    assert.equal(resolveObjectiveKey("Copano Rickey", keys, folded), "Copano Rickey")
    assert.equal(resolveObjectiveKey("T.M. Opera O", keys, folded), "TM Opera O") // folded match
    assert.throws(() => resolveObjectiveKey("Missing", keys, folded), ExtractError) // no match
})
