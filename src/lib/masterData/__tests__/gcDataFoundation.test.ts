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

    it("has exactly 402 races, each with a full 18-place payout curve", () => {
        expect(all).toHaveLength(402)
        // 18-place lock: the conservative-floor proof depends on every committed curve covering all
        // finishing places up to the game's max field size (18). A future refresh that shipped a
        // shorter curve must fail here and revisit the floor argument rather than silently weaken it.
        for (const r of all) expect(r.fanPayoutsByPlace).toHaveLength(18)
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

describe("Grand Concert mandatory-race fan-entry gate (fansNeeded)", () => {
    type Option = { raceName: string; fans: number; fansNeeded?: number }
    type MRace = { turn: number; options: Option[] }
    const allOptions: { char: string; turn: number; opt: Option }[] = []
    for (const [name, c] of Object.entries(chars)) {
        for (const mr of (c.mandatoryRaces ?? []) as MRace[]) {
            for (const opt of mr.options) allOptions.push({ char: name, turn: mr.turn, opt })
        }
    }
    const gateFor = (char: string, raceName: string, turn: number) =>
        allOptions.find((x) => x.char === char && x.opt.raceName === raceName && x.turn === turn)?.opt

    it("Copano Rickey Champions Cup (turn 47) requires 12000 fans to enter", () => {
        const opt = gateFor("Copano Rickey", "Champions Cup", 47)
        expect(opt?.fansNeeded).toBe(12000)
        expect(opt?.fans).not.toBe(opt?.fansNeeded) // reward (10000) is not the gate (12000)
    })

    it("Copano Rickey February Stakes (turn 52) requires 12000 fans to enter", () => {
        expect(gateFor("Copano Rickey", "February Stakes", 52)?.fansNeeded).toBe(12000)
    })

    it("a factual low gate exists: Copano Rickey Fukuryu Stakes (turn 31) = 350", () => {
        expect(gateFor("Copano Rickey", "Fukuryu Stakes", 31)?.fansNeeded).toBe(350)
    })

    it("a factual high gate exists: Agnes Tachyon Arima Kinen (turn 72) = 25000", () => {
        expect(gateFor("Agnes Tachyon", "Arima Kinen", 72)?.fansNeeded).toBe(25000)
    })

    it("every committed gate is a non-negative integer", () => {
        for (const { opt } of allOptions) {
            if (opt.fansNeeded === undefined) continue
            expect(Number.isInteger(opt.fansNeeded)).toBe(true)
            expect(opt.fansNeeded).toBeGreaterThanOrEqual(0)
        }
    })

    it("no semantically identical mandatory-race option carries conflicting gates", () => {
        // Identity mirrors the scraper dedup: (character, turn, raceName). A recurrence with a
        // different gate would be a data-integrity break, not a legitimate variant.
        const byKey = new Map<string, number>()
        for (const { char, turn, opt } of allOptions) {
            if (opt.fansNeeded === undefined) continue
            const key = `${char}|${turn}|${opt.raceName}`
            const prev = byKey.get(key)
            if (prev !== undefined) expect(opt.fansNeeded).toBe(prev)
            byKey.set(key, opt.fansNeeded)
        }
    })
})
