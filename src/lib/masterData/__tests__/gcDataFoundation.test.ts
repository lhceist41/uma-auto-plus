// Grand Concert data foundation: asserts the committed fan-goal and race-payout data
// so a regression in the raw data (or a bad re-extraction) fails a test rather than a run.

import objectives from "../../../data/character_objectives.json"
import racesData from "../../../data/races.json"

type FanGoal = { turn: number; targetFans: number; scenarioGroupId: number; appliesToScenarioIds: number[] }
type ObjChar = { name: string; mandatoryRaces?: unknown[]; fanGoals?: FanGoal[] }
type FanPayout = { place: number; fans: number }
type Race = { name: string; turnNumber: number; fans: number; fanPayoutsByPlace?: FanPayout[] }

const chars = objectives as Record<string, ObjChar>
const races = racesData as Record<string, Race>
const GRAND_CONCERT_SCENARIO_ID = 3

describe("Grand Concert fan goals (master.mdb route data)", () => {
    const goalsFor = (name: string) => chars[name]?.fanGoals ?? []

    it("Copano Rickey: 3000 fans by turn 24, applies to Grand Concert", () => {
        const g = goalsFor("Copano Rickey")
        expect(g).toHaveLength(1)
        expect(g[0].turn).toBe(24)
        expect(g[0].targetFans).toBe(3000)
        expect(g[0].appliesToScenarioIds).toContain(GRAND_CONCERT_SCENARIO_ID)
    })

    it("has a turn-23 goal (Hishi Akebono) and a turn-25 goal (Seiun Sky)", () => {
        expect(goalsFor("Hishi Akebono").some((x) => x.turn === 23)).toBe(true)
        expect(goalsFor("Seiun Sky").some((x) => x.turn === 25)).toBe(true)
    })

    it("has a goal with a target other than 3000 (Silence Suzuka: 5000 at turn 27)", () => {
        const g = goalsFor("Silence Suzuka")
        expect(g.some((x) => x.targetFans === 5000 && x.turn === 27)).toBe(true)
    })

    it("preserves multiple fan goals for a character (Hishi Akebono)", () => {
        const g = goalsFor("Hishi Akebono")
        expect(g.length).toBeGreaterThanOrEqual(2)
        // Deterministically ordered by (turn, targetFans).
        const turns = g.map((x) => x.turn)
        expect(turns).toEqual([...turns].sort((a, b) => a - b))
    })

    it("every emitted fan goal is genuinely Grand-Concert-applicable and well-formed", () => {
        let total = 0
        for (const c of Object.values(chars)) {
            for (const g of c.fanGoals ?? []) {
                total++
                expect(g.appliesToScenarioIds).toContain(GRAND_CONCERT_SCENARIO_ID) // never a non-GC (e.g. Trackblazer-only) goal
                expect(Number.isInteger(g.turn)).toBe(true)
                expect(g.turn).toBeGreaterThan(0)
                expect(g.targetFans).toBeGreaterThan(0)
                expect(Number.isInteger(g.scenarioGroupId)).toBe(true)
            }
        }
        expect(total).toBeGreaterThanOrEqual(20) // 25 across the current roster; guards against silent loss
    })

    it("leaves characters without a fan goal untouched (no empty fanGoals arrays)", () => {
        for (const c of Object.values(chars)) {
            if (c.fanGoals !== undefined) expect(c.fanGoals.length).toBeGreaterThan(0)
        }
    })
})

describe("Grand Concert race payout curves (bulk-refreshed)", () => {
    const all = Object.values(races)

    it("has exactly 402 races, each with a non-empty payout curve", () => {
        expect(all).toHaveLength(402)
        for (const r of all) expect((r.fanPayoutsByPlace ?? []).length).toBeGreaterThan(0)
    })

    it("every curve is place-sorted, has unique places, and place-1 equals the scalar fans", () => {
        for (const r of all) {
            const curve = r.fanPayoutsByPlace!
            const places = curve.map((p) => p.place)
            expect(places).toEqual([...places].sort((a, b) => a - b))
            expect(new Set(places).size).toBe(places.length)
            const first = curve.find((p) => p.place === 1)
            expect(first?.fans).toBe(r.fans)
        }
    })

    it("payouts are positive and monotone non-increasing by place", () => {
        for (const r of all) {
            const curve = r.fanPayoutsByPlace!
            for (let i = 0; i < curve.length; i++) {
                expect(curve[i].fans).toBeGreaterThan(0)
                if (i > 0) expect(curve[i].fans).toBeLessThanOrEqual(curve[i - 1].fans)
            }
        }
    })

    // Conservative completed-race floor: the minimum payout in a race's own curve. Safe because every
    // committed race lists all 18 finishing places and the game's max field size is 18, so the worst
    // finish a race can produce is covered by the curve. No entryNum needed for the committed data.
    it("a conservative fan floor resolves for every race and never exceeds first place", () => {
        let globalMin = Infinity
        for (const r of all) {
            const curve = r.fanPayoutsByPlace!
            const floor = Math.min(...curve.map((p) => p.fans))
            expect(floor).toBeGreaterThan(0)
            expect(floor).toBeLessThanOrEqual(r.fans)
            globalMin = Math.min(globalMin, floor)
        }
        // The globally-proven minimum across the committed dataset (master single_mode_fan_count min is
        // 5; the committed races reference only tables whose smallest tail is 7).
        expect(globalMin).toBeGreaterThanOrEqual(5)
    })
})
