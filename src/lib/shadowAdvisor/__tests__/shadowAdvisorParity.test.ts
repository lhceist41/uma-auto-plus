// Golden-fixture parity, TypeScript side. Pins that the authoritative S1 builder + policy still reproduce every
// checked-in fixture output the JUnit suite also asserts against. If this drifts, regenerate the fixtures with
// `node scripts/generate-shadow-fixtures.mjs` only after confirming the change is intended - both languages must
// move together.

import { readFileSync } from "node:fs"
import { join } from "node:path"
import { buildContextFromRecords } from "../context.ts"
import { recommend } from "../policy.ts"

interface Fixture {
    name: string
    decisionTrace: unknown
    careerState: unknown
    expectedContext: unknown
    expectedRecommendation: unknown
}

const fixtures: Fixture[] = JSON.parse(readFileSync(join(__dirname, "..", "__fixtures__", "parity.json"), "utf8"))

describe("shadow advisor golden parity (TS)", () => {
    it("ships a non-trivial fixture set covering the required branches", () => {
        expect(fixtures.length).toBeGreaterThanOrEqual(17)
        const names = new Set(fixtures.map((f) => f.name))
        for (const required of [
            "complete-train-positive-margin",
            "complete-train-negative-margin",
            "train-tie-break-canonical",
            "train-all-over-limit-least-risk",
            "train-missing-gains",
            "train-missing-failchance",
            "train-incomplete-four-facilities",
            "rest-energy-below-threshold",
            "recover-mood-below-floor",
            "race-day-mandatory-not-applicable",
            "state-unavailable-no-contest-no-state",
            "no-contest-not-applicable",
            "forced-default-selection-ignored",
            "numeric-integer-total",
            "numeric-half-step-total",
            "forbidden-post-execution-blocks-ignored",
        ]) {
            expect(names.has(required)).toBe(true)
        }
    })

    it.each(fixtures.map((f) => [f.name, f] as const))("reproduces the context and recommendation for %s", (_name, fixture) => {
        const context = buildContextFromRecords(fixture.decisionTrace, fixture.careerState)
        expect(context).not.toBeNull()
        expect(context).toEqual(fixture.expectedContext)
        expect(recommend(context!)).toEqual(fixture.expectedRecommendation)
    })
})
