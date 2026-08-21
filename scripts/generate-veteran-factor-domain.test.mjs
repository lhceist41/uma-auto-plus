// Tests for the Veteran factor-domain generator: synthetic fixtures for shape/validation and the
// tier-glyph/unique-split behavior, plus the real committed data for coverage, the collision-free
// property the native resolver relies on, and byte-identity of the checked-in asset.
//
// Run: node --test scripts/generate-veteran-factor-domain.test.mjs

import test from "node:test"
import assert from "node:assert/strict"
import { readFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { dirname, join } from "node:path"
import { buildPayload, generate, factorBaseName, GenerateError, ASSET_PATH, SCHEMA_VERSION, STATS, APTITUDES } from "./generate-veteran-factor-domain.mjs"

const HERE = dirname(fileURLToPath(import.meta.url))
const REPO = join(HERE, "..")
const skills = Object.values(JSON.parse(readFileSync(join(REPO, "src", "data", "skills.json"), "utf8")))
const races = Object.values(JSON.parse(readFileSync(join(REPO, "src", "data", "races.json"), "utf8")))
const scenarios = JSON.parse(readFileSync(join(REPO, "src", "data", "scenarios.json"), "utf8"))

/** The SAME skeleton the native resolver derives (normalizeIdentityText): NFD-fold to lowercase
 * alphanumerics. Used here to pin the collision-free property of the DATA, independent of Kotlin. */
const skel = (s) => [...s.toLowerCase().normalize("NFD")].filter((c) => /[a-z0-9]/.test(c)).join("")

test("factorBaseName strips the trailing tier glyph but keeps embedded decoration", () => {
    assert.equal(factorBaseName("Firm Conditions ○"), "Firm Conditions")
    assert.equal(factorBaseName("Firm Conditions ◎"), "Firm Conditions")
    assert.equal(factorBaseName("Non-Standard Distance ×"), "Non-Standard Distance")
    assert.equal(factorBaseName("Corazón ☆ Ardiente"), "Corazón ☆ Ardiente")
    assert.equal(factorBaseName("Calm in a Crowd"), "Calm in a Crowd")
    // The U+2014 em dash (banned from committed content) folds to "--"; skeleton matching is unaffected.
    const emDash = String.fromCharCode(0x2014)
    assert.equal(factorBaseName(`Sunrise Banner${emDash}Katsuragi Ace!`), "Sunrise Banner--Katsuragi Ace!")
})

test("buildPayload splits uniques by id, collapses tier variants, and keeps fixed stat/aptitude", () => {
    const payload = buildPayload(
        [
            { id: 100101, name_en: "Shooting for Victory!" },
            { id: 10241, name_en: "1st Place Kiss☆" },
            { id: 200152, name_en: "Firm Conditions ○" },
            { id: 200153, name_en: "Firm Conditions ◎" },
            { id: 200154, name_en: "Firm Conditions ×" },
            { id: 300051, name_en: "Blatant Fear" },
        ],
        [{ name: "Yasuda Kinen" }, { name: "Yasuda Kinen" }],
        { "URA Finale": {}, Trackblazer: {} },
    )
    assert.deepEqual(payload.families.stat, [...STATS].sort())
    assert.deepEqual(payload.families.aptitude, [...APTITUDES].sort())
    // id-first-digit "1" -> unique; both uniques land there, nothing else does.
    assert.deepEqual(payload.families.unique, ["1st Place Kiss☆", "Shooting for Victory!"])
    // Three tier variants collapse to one base name; the id-3 special stays a white skill.
    assert.deepEqual(payload.families.skill, ["Blatant Fear", "Firm Conditions"])
    assert.deepEqual(payload.families.race, ["Yasuda Kinen"])
    assert.deepEqual(payload.families.scenario, ["Trackblazer", "URA Finale"])
    assert.equal(payload.schemaVersion, SCHEMA_VERSION)
    assert.deepEqual(payload.counts, { stat: 5, aptitude: 10, unique: 2, skill: 2, race: 1, scenario: 2 })
})

test("buildPayload rejects an empty skill list, an empty race list, and a nameless skill", () => {
    assert.throws(() => buildPayload([], races, scenarios), GenerateError)
    assert.throws(() => buildPayload(skills, [], scenarios), GenerateError)
    assert.throws(() => buildPayload([{ name_en: "No Id Skill" }], [{ name: "R" }], { S: {} }), GenerateError)
})

test("the committed domain covers every family and the live-observed fixture factor names", () => {
    const payload = buildPayload(skills, races, scenarios)
    for (const [family, list] of Object.entries(payload.families)) {
        assert.ok(list.length > 0, `family ${family} is empty`)
        assert.equal(new Set(list).size, list.length, `family ${family} has a duplicate`)
    }
    // The fixtures (android/.../fixtures/inspiration) name these exact factors; each must resolve in
    // its own family by skeleton, or the live capture would leave them fail-closed unresolved.
    const inFamily = (family, name) => payload.families[family].some((x) => skel(x) === skel(name))
    assert.ok(inFamily("stat", "Power"))
    assert.ok(inFamily("aptitude", "Mile"))
    assert.ok(inFamily("aptitude", "Pace Chaser"))
    assert.ok(inFamily("unique", "Shooting for Victory!"))
    assert.ok(inFamily("unique", "Behold Thine Emperor's Divine Might"))
    assert.ok(inFamily("skill", "Firm Conditions"))
    assert.ok(inFamily("skill", "Long Corners"))
    assert.ok(inFamily("skill", "Standard Distance"))
    assert.ok(inFamily("skill", "Pace Chaser Savvy"))
    assert.ok(inFamily("race", "Yasuda Kinen"))
    assert.ok(inFamily("scenario", "URA Finale"))
})

test("no two distinct canonical names in a resolvable domain share a skeleton", () => {
    // The native resolver's exact-skeleton fast path trusts that a skeleton maps to ONE canonical
    // name. STAT, APTITUDE, UNIQUE each resolve within their own family; WHITE resolves against the
    // union of skill+race+scenario. A collision (two different names, same skeleton) would make BOTH
    // fail-closed unresolvable, so this pins the property against the committed data.
    const payload = buildPayload(skills, races, scenarios)
    const check = (names, label) => {
        const byS = new Map()
        for (const n of names) {
            const k = skel(n)
            const prev = byS.get(k)
            assert.ok(prev === undefined || prev === n, `${label}: skeleton collision "${prev}" vs "${n}"`)
            byS.set(k, n)
        }
    }
    check(payload.families.stat, "stat")
    check(payload.families.aptitude, "aptitude")
    check(payload.families.unique, "unique")
    check([...payload.families.skill, ...payload.families.race, ...payload.families.scenario], "white")
})

test("the committed asset is byte-identical to a fresh generation", () => {
    assert.equal(readFileSync(ASSET_PATH, "utf8"), generate())
})

test("generation is deterministic across repeat runs", () => {
    assert.equal(generate(), generate())
})
