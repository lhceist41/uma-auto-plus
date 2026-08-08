import { readFileSync } from "node:fs"
import { join } from "node:path"
import process from "node:process"
import { loadRaceCatalog, createRaceCatalog } from "../catalog.ts"
import { buildObjectiveTimeline, buildAllObjectiveTimelines, RaceLabError } from "../objectives.ts"
import { classifyRaceFit, meetsCurrentRuntimeAptitudeGate } from "../fit.ts"
import { buildSchedule, analyzePressure } from "../pressure.ts"
import { parsePlan, validatePlan } from "../planValidator.ts"
import { annotateHistoricalTurn, annotateHistory } from "../annotate.ts"
import type { CompiledRace, ObjectiveRequirement, PlannedRace } from "../types.ts"

const REPO_ROOT = process.cwd()
const COMPILED_DIR = join(REPO_ROOT, "src/data/compiled")
const OBJECTIVES_PATH = join(REPO_ROOT, "src/data/character_objectives.json")

const catalog = loadRaceCatalog(COMPILED_DIR)
const rawObjectives = JSON.parse(readFileSync(OBJECTIVES_PATH, "utf8")) as Record<string, unknown>

function race(over: Partial<CompiledRace> = {}): CompiledRace {
    return { key: { name: over.name ?? "R", turnNumber: over.turnNumber ?? 10 }, name: "R", turnNumber: 10, date: "d", grade: "G1", raceTrack: "Tokyo", course: null, direction: "Left", terrain: "Turf", distanceType: "Mile", distanceMeters: 1600, fans: 5000, nameFormatted: "f", ...over }
}
function req(turn: number, names: string[], isChoice = names.length > 1): ObjectiveRequirement {
    return { turn, isChoice, options: names.map((n) => ({ raceName: n, canonicalRace: race({ name: n, turnNumber: turn }), rawMeta: { grade: null, surface: null, distanceType: null, fans: null } })) }
}
function plan(entries: [string, number][]): PlannedRace[] {
    return entries.map(([raceName, turnNumber]) => ({ raceName, date: "x", turnNumber, priority: 0 }))
}

// ---- Catalog (Part R) ----

describe("catalog", () => {
    it("exposes 402 real canonical keys with matching stats", () => {
        const s = catalog.catalogStats()
        expect(s.raceCount).toBe(402)
        expect(s.uniqueKeyCount).toBe(402)
        expect(s.distinctBareNameCount).toBe(296)
        expect(s.bareNameCollisionCount).toBe(106)
    })
    it("surfaces a same-name/different-turn collision and distinguishes by key", () => {
        const all = catalog.racesByName("Marine Cup")
        expect(all.length).toBeGreaterThan(1)
        const turns = all.map((r) => r.turnNumber)
        expect(new Set(turns).size).toBe(turns.length) // distinct turns
        expect(catalog.raceByKey("Marine Cup", turns[0])?.turnNumber).toBe(turns[0])
        expect(catalog.raceByKey("Marine Cup", turns[1])?.turnNumber).toBe(turns[1])
    })
    it("returns undefined for a missing key and empty for an unknown name (no raw fallback)", () => {
        expect(catalog.raceByKey("Marine Cup", 9999)).toBeUndefined()
        expect(catalog.racesByName("__nope__")).toEqual([])
    })
    it("racesAtTurn returns all races on a turn, sorted", () => {
        const anyRace = catalog.allRaces()[0]
        const atTurn = catalog.racesAtTurn(anyRace.turnNumber)
        expect(atTurn.some((r) => r.name === anyRace.name)).toBe(true)
    })
    it("has no bare-name single-result helper", () => {
        expect((catalog as unknown as Record<string, unknown>).raceByName).toBeUndefined()
    })
})

// ---- Objectives (Part R) ----

describe("objectives", () => {
    it("resolves all 485 real options across 62 characters, 0 unresolved", () => {
        const { reconciliation } = buildAllObjectiveTimelines(rawObjectives, catalog)
        expect(reconciliation.characterCount).toBe(62)
        expect(reconciliation.optionCount).toBe(485)
        expect(reconciliation.unresolvedCount).toBe(0)
    })
    it("preserves every option of a genuine choice objective (Daiwa Scarlet turn 34)", () => {
        const t = buildObjectiveTimeline("Daiwa Scarlet", rawObjectives, catalog)
        const choice = t.requirements.find((r) => r.turn === 34)!
        expect(choice.isChoice).toBe(true)
        const names = choice.options.map((o) => o.raceName).sort()
        expect(names).toEqual(["Japanese Oaks", "Tokyo Yushun (Japanese Derby)"])
        // each option resolved to its own canonical race at that turn
        expect(choice.options.every((o) => o.canonicalRace.turnNumber === 34)).toBe(true)
    })
    it("tags the timeline URA (does not claim TB/Unity Cup validity)", () => {
        expect(buildObjectiveTimeline("Daiwa Scarlet", rawObjectives, catalog).scenario).toBe("URA")
    })
    it("hard-fails on a synthetic unresolved option, never guessing by bare name", () => {
        const bad = { Ghost: { name: "Ghost", mandatoryRaces: [{ turn: 5, isChoice: false, options: [{ raceName: "No Such Race" }] }] } }
        expect(() => buildObjectiveTimeline("Ghost", bad, catalog)).toThrow(RaceLabError)
        try {
            buildObjectiveTimeline("Ghost", bad, catalog)
        } catch (e) {
            expect((e as RaceLabError).code).toBe("objectiveRaceUnresolved")
        }
    })
})

// ---- Fit (Part R) ----

describe("aptitude fit", () => {
    const apt = { surface: { TURF: "A", DIRT: "C" }, distance: { MILE: "B", LONG: "G" } }
    it("maps surface + distance exactly from the race's terrain/distanceType", () => {
        const fit = classifyRaceFit(race({ terrain: "Turf", distanceType: "Mile" }), apt)
        expect(fit.surface.aptitudeKey).toBe("TURF")
        expect(fit.surface.grade).toBe("A")
        expect(fit.distance.aptitudeKey).toBe("MILE")
        expect(fit.distance.grade).toBe("B")
    })
    it("applies the exact current-runtime gate (both >= B)", () => {
        expect(meetsCurrentRuntimeAptitudeGate(race({ terrain: "Turf", distanceType: "Mile" }), apt)).toBe(true) // A + B
        expect(meetsCurrentRuntimeAptitudeGate(race({ terrain: "Dirt", distanceType: "Mile" }), apt)).toBe(false) // C + B
        expect(meetsCurrentRuntimeAptitudeGate(race({ terrain: "Turf", distanceType: "Long" }), apt)).toBe(false) // A + G
    })
    it("reports unavailable (not a default) for a missing aptitude, and null gate", () => {
        const fit = classifyRaceFit(race({ terrain: "Dirt", distanceType: "Sprint" }), { surface: { TURF: "A" } })
        expect(fit.surface.grade).toBeNull() // DIRT not in map
        expect(fit.surface.status).toBe("unavailable")
        expect(fit.distance.grade).toBeNull() // SPRINT not in map
        expect(fit.meetsCurrentRuntimeAptitudeGate).toBeNull()
    })
})

// ---- Pressure (Part R) ----

describe("schedule pressure", () => {
    it("an isolated race produces no streak", () => {
        const p = analyzePressure(buildSchedule([req(10, ["A"])], []))
        expect(p.streaks).toHaveLength(0)
        expect(p.entries).toHaveLength(1)
    })
    it("two adjacent turns form a length-2 streak", () => {
        const p = analyzePressure(buildSchedule([req(10, ["A"]), req(11, ["B"])], []))
        expect(p.streaks).toEqual([{ kind: "streak", startTurn: 10, endTurn: 11, length: 2, source: "objective", reachesConsecutiveLimit: null }])
    })
    it("a 3+ streak is detected and the consecutive-limit flag is set only when a limit is supplied", () => {
        const p = analyzePressure(buildSchedule([req(10, ["A"]), req(11, ["B"]), req(12, ["C"])], []), { consecutiveLimit: 2 })
        expect(p.streaks[0].length).toBe(3)
        expect(p.streaks[0].reachesConsecutiveLimit).toBe(true)
        expect(analyzePressure(buildSchedule([req(10, ["A"]), req(11, ["B"]), req(12, ["C"])], [])).streaks[0].reachesConsecutiveLimit).toBeNull()
    })
    it("a choice objective still occupies its turn as one slot", () => {
        const p = analyzePressure(buildSchedule([req(10, ["A", "B"])], []))
        expect(p.entries[0]).toEqual({ turn: 10, source: "objective", raceCount: 1 })
    })
    it("overlapping plan + objective on one turn is a same-turn stack, marked source both", () => {
        const p = analyzePressure(buildSchedule([req(10, ["A"])], plan([["B", 10]])))
        expect(p.sameTurn).toEqual([{ kind: "sameTurn", turn: 10, raceCount: 2, source: "both" }])
    })
    it("gaps and streak ordering are deterministic", () => {
        const p = analyzePressure(buildSchedule([req(20, ["A"]), req(10, ["B"]), req(11, ["C"])], []))
        expect(p.streaks.map((s) => s.startTurn)).toEqual([10])
        expect(p.gaps).toEqual([{ kind: "gap", fromTurn: 11, toTurn: 20, gap: 9 }])
    })
})

// ---- Plan validation (Part R) ----

describe("plan validation", () => {
    const timeline = () => buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog)
    const errs = (issues: { severity: string; code: string }[]) => issues.filter((i) => i.severity === "error").map((i) => i.code)

    it("accepts a valid optional (non-objective) race without error", () => {
        const r = validatePlan(plan([["Marine Cup", 31]]), catalog) // no objective timeline -> just catalog checks
        expect(r.ok).toBe(true)
    })
    it("flags a nonexistent race", () => {
        const r = validatePlan(plan([["No Such Race", 10]]), catalog)
        expect(errs(r.issues)).toContain("raceNotFound")
    })
    it("flags a turn mismatch and lists the real turns (collision-safe)", () => {
        const r = validatePlan(plan([["Marine Cup", 40]]), catalog)
        const mm = r.issues.find((i) => i.code === "planTurnMismatch")!
        expect(mm.severity).toBe("error")
        expect(mm.detail).toContain("31")
        expect(mm.detail).toContain("55")
    })
    it("flags a same-turn double booking (conflicting different races)", () => {
        const r = validatePlan(plan([["Marine Cup", 31], ["Fukuryu Stakes", 31]]), catalog)
        expect(errs(r.issues)).toContain("conflictingRacesOnTurn")
    })
    it("flags a mandatory-objective conflict but accepts a matching objective race", () => {
        const conflict = validatePlan(plan([["Marine Cup", 31]]), catalog, timeline()) // turn 31 objective is Fukuryu Stakes
        expect(errs(conflict.issues)).toContain("objectiveConflict")
        const match = validatePlan(plan([["Fukuryu Stakes", 31]]), catalog, timeline())
        expect(errs(match.issues)).not.toContain("objectiveConflict")
        expect(match.issues.some((i) => i.code === "matchesObjective")).toBe(true)
    })
    it("does not error when a plan omits an objective turn (runtime handles it)", () => {
        const r = validatePlan(plan([["Marine Cup", 55]]), catalog, timeline())
        expect(r.ok).toBe(true)
    })
    it("surfaces malformed entries and orders issues deterministically", () => {
        const parsed = parsePlan('[{"raceName":"X"}]') // missing turnNumber
        expect(parsed.issues[0].code).toBe("malformedPlanEntry")
        const r1 = validatePlan(plan([["Marine Cup", 40], ["No Such Race", 10]]), catalog)
        const r2 = validatePlan(plan([["No Such Race", 10], ["Marine Cup", 40]]), catalog)
        expect(r1.issues).toEqual(r2.issues) // order-independent
    })
})

// ---- Scenario honesty (Part R) ----

describe("scenario honesty", () => {
    it("objective timelines are URA only; no automatic TB/Unity Cup objective claim exists", () => {
        const t = buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog)
        expect(t.scenario).toBe("URA")
        // The model has no field asserting TB/Unity Cup objective validity.
        expect(Object.keys(t)).toEqual(["character", "scenario", "requirements"])
    })
})

// ---- Historical annotation (Part J / Part R) ----

describe("historical annotation", () => {
    const timeline = () => buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog) // objective at turn 31
    it("annotates an objective RACE turn with options and never infers entered race identity", () => {
        const a = annotateHistoricalTurn({ seq: 5, turn: 31, committedAction: "RACE", raceDayFlags: { mandatory: true, scheduled: false, goalRibbon: true } }, timeline())
        expect(a.isObjectiveTurn).toBe(true)
        expect(a.raceActionRecorded).toBe(true)
        expect(a.objectiveOptions[0].raceName).toBe("Fukuryu Stakes")
        expect(a.enteredRaceIdentity).toBe("unavailable")
    })
    it("a RACE on a non-objective turn records the action with race identity unavailable", () => {
        const a = annotateHistoricalTurn({ seq: 9, turn: 40, committedAction: "RACE" }, timeline())
        expect(a.isObjectiveTurn).toBe(false)
        expect(a.raceActionRecorded).toBe(true)
        expect(a.objectiveOptions).toEqual([])
        expect(a.enteredRaceIdentity).toBe("unavailable")
    })
    it("attaches fit for objective races when aptitudes are supplied", () => {
        const a = annotateHistoricalTurn({ seq: 1, turn: 31, committedAction: "RACE" }, timeline(), { surface: { DIRT: "A" }, distance: { MILE: "A" } })
        expect(a.objectiveOptions[0].fit?.meetsCurrentRuntimeAptitudeGate).toBe(true)
    })
})

// ---- Read-only / determinism (Part R) ----

describe("read-only and determinism", () => {
    it("repeated JSON export of a validation report is byte-identical", () => {
        const p = plan([["Marine Cup", 40], ["Champions Cup", 47]])
        const a = JSON.stringify(validatePlan(p, catalog, buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog)))
        const b = JSON.stringify(validatePlan(p, catalog, buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog)))
        expect(a).toBe(b)
    })
    it("does not mutate the input plan array or catalog", () => {
        const p = plan([["Marine Cup", 31]])
        const before = JSON.stringify(p)
        validatePlan(p, catalog)
        expect(JSON.stringify(p)).toBe(before)
        expect(Object.isFrozen(catalog.allRaces())).toBe(true)
    })
    it("annotateHistory is deterministic and sorted by seq", () => {
        const t = buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog)
        const out = annotateHistory([{ seq: 3, turn: 40, committedAction: "TRAIN" }, { seq: 1, turn: 31, committedAction: "RACE" }], t)
        expect(out.map((a) => a.seq)).toEqual([1, 3])
    })
})

// ---- synthetic collision safety with a hand-built catalog (Part I) ----

describe("synthetic same-name collision", () => {
    it("bare-name lookup returns both; canonical lookup and plan validation distinguish them", () => {
        // Build a tiny catalog with two same-name races on different turns.
        const twin = (turn: number): CompiledRace => race({ name: "Twin Cup", turnNumber: turn })
        const fakeReader = {
            races: Object.freeze([twin(12), twin(30)]),
            raceByKey: (n: string, t: number) => (n === "Twin Cup" && (t === 12 || t === 30) ? twin(t) : undefined),
            racesByName: (n: string) => (n === "Twin Cup" ? [twin(12), twin(30)] : []),
            skills: Object.freeze([]),
            skillById: () => undefined,
            fingerprint: "synthetic",
            manifest: {} as never,
        }
        const cat = createRaceCatalog(fakeReader as never)
        expect(cat.racesByName("Twin Cup").map((r) => r.turnNumber)).toEqual([12, 30])
        expect(cat.raceByKey("Twin Cup", 12)?.turnNumber).toBe(12)
        // A plan on the wrong turn cannot be silently bound to a valid twin.
        const r = validatePlan(plan([["Twin Cup", 20]]), cat)
        expect(r.issues.find((i) => i.code === "planTurnMismatch")?.detail).toContain("12, 30")
    })
})
