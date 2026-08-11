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
    it("resolves all 494 real options across 63 characters, 0 unresolved", () => {
        const { reconciliation } = buildAllObjectiveTimelines(rawObjectives, catalog)
        expect(reconciliation.characterCount).toBe(63)
        expect(reconciliation.optionCount).toBe(494)
        expect(reconciliation.unresolvedCount).toBe(0)
    })
    it("Yukino Bijin (v5.8.6 data refresh) resolves all 9 mandatory objectives canonically, including the adjacent t69/t70 G1 pair", () => {
        const rawCharacters = JSON.parse(readFileSync(join(REPO_ROOT, "src/data/characters.json"), "utf8")) as Record<string, unknown>
        expect("Yukino Bijin" in rawCharacters).toBe(true)
        expect("Yukino Bijin" in rawObjectives).toBe(true)

        // buildObjectiveTimeline throws objectiveRaceUnresolved on any option that does not resolve by
        // canonical (name, turnNumber), so a clean return with 9 single-option mandatory requirements is
        // proof that all 9 resolved canonically - never by bare name, never ambiguous, never unresolved.
        const t = buildObjectiveTimeline("Yukino Bijin", rawObjectives, catalog)
        expect(t.scenario).toBe("URA")
        expect(t.requirements).toHaveLength(9)
        expect(t.requirements.every((r) => !r.isChoice && r.options.length === 1)).toBe(true)
        expect(t.requirements.every((r) => r.options[0].canonicalRace.key.turnNumber === r.turn)).toBe(true)

        // The three names that collide on bare name elsewhere in the catalog must still resolve by key.
        const keyAt = (turn: number) => t.requirements.find((r) => r.turn === turn)!.options[0].canonicalRace.key
        expect(keyAt(38)).toEqual({ name: "Queen Stakes", turnNumber: 38 })
        expect(keyAt(69)).toEqual({ name: "Queen Elizabeth II Cup", turnNumber: 69 })
        expect(keyAt(70)).toEqual({ name: "Japan Cup", turnNumber: 70 })

        // Turns 69 and 70 are adjacent mandatory G1 targets (Queen Elizabeth II Cup, then Japan Cup).
        const t69 = t.requirements.find((r) => r.turn === 69)!
        const t70 = t.requirements.find((r) => r.turn === 70)!
        expect(t.requirements.indexOf(t70) - t.requirements.indexOf(t69)).toBe(1)
        expect(t70.turn - t69.turn).toBe(1)
        expect(t69.options[0].canonicalRace.grade).toBe("G1")
        expect(t70.options[0].canonicalRace.grade).toBe("G1")
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

// ---- Phase 2B: entered-race canonical enrichment ----

describe("entered-race canonical enrichment (Phase 2B)", () => {
    const uraTimeline = buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog) // Fukuryu Stakes @ turn 31 (mandatory)
    const mandatoryReq = uraTimeline.requirements.find((r) => !r.isChoice) as ObjectiveRequirement
    const mandatoryRace = mandatoryReq.options[0].canonicalRace // real (name, turnNumber) that IS an objective
    const daiwaTimeline = buildObjectiveTimeline("Daiwa Scarlet", rawObjectives, catalog) // choice @ turn 34
    const choiceReq = daiwaTimeline.requirements.find((r) => r.isChoice) as ObjectiveRequirement
    const choiceRace = choiceReq.options[0].canonicalRace
    const marineTurns = catalog.racesByName("Marine Cup").map((r) => r.turnNumber) // [31, 55] real collision
    // A real catalog race that is NOT any Copano Rickey objective (for the nonObjective relation).
    const objKeys = new Set(uraTimeline.requirements.flatMap((r) => r.options.map((o) => `${o.canonicalRace.key.name}|${o.canonicalRace.key.turnNumber}`)))
    const nonObjectiveRace = catalog.allRaces().find((r) => !objKeys.has(`${r.name}|${r.turnNumber}`)) as CompiledRace

    function exactFact(name: string, turnNumber: number): { turnNumber: number; resolution: string; path: string; name: string; matchCount: number } {
        return { turnNumber, resolution: "exact", path: "smart", name, matchCount: 1 }
    }

    it("1. old/no-fact input keeps enteredRaceIdentity unavailable and adds no nested enrichment", () => {
        const a = annotateHistoricalTurn({ seq: 5, turn: 31, committedAction: "RACE" }, uraTimeline, undefined, catalog)
        expect(a.enteredRaceIdentity).toBe("unavailable")
        expect(a.enteredRace).toBeUndefined()
    })

    it("2. exact + name + matching key resolves canonical metadata and preserves resolution/path", () => {
        const a = annotateHistoricalTurn(
            { seq: 1, turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe(mandatoryRace.name)
        expect(a.enteredRace?.catalog.status).toBe("resolved")
        if (a.enteredRace?.catalog.status !== "resolved") throw new Error("expected resolved")
        expect(a.enteredRace.catalog.race).toMatchObject({
            name: mandatoryRace.name,
            turnNumber: mandatoryRace.turnNumber,
            grade: mandatoryRace.grade,
            surface: mandatoryRace.terrain,
            distanceType: mandatoryRace.distanceType,
            distance: mandatoryRace.distanceMeters,
        })
        expect(a.enteredRace.fact.resolution).toBe("exact")
        expect(a.enteredRace.fact.path).toBe("smart")
    })

    it("3. fuzzy unique + canonical name joins and stays fuzzy (never upgraded to exact)", () => {
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: { turnNumber: mandatoryRace.turnNumber, resolution: "fuzzy", path: "scheduled", name: mandatoryRace.name, matchCount: 1 } },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe(mandatoryRace.name)
        expect(a.enteredRace?.catalog.status).toBe("resolved")
        expect(a.enteredRace?.fact.resolution).toBe("fuzzy") // preserved, not exact
    })

    it("4. ambiguousSet does not join, identity unavailable, matchCount preserved, no possible-set", () => {
        const a = annotateHistoricalTurn(
            { turn: 31, committedAction: "RACE", enteredRace: { turnNumber: 31, resolution: "ambiguousSet", path: "scheduled", matchCount: 2 } },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe("unavailable")
        expect(a.enteredRace?.catalog.status).toBe("notJoinable")
        if (a.enteredRace?.catalog.status !== "notJoinable") throw new Error("expected notJoinable")
        expect(a.enteredRace.catalog.reason).toBe("ambiguous")
        expect(a.enteredRace.fact.matchCount).toBe(2)
        // No possible-set is invented: the annotation exposes no candidate list.
        expect(Object.keys(a.enteredRace)).toEqual(["fact", "catalog", "fit", "objectiveRelation"])
    })

    it("5. fuzzy multi does not join and preserves fuzzy ambiguity + matchCount", () => {
        const a = annotateHistoricalTurn(
            { turn: 31, committedAction: "RACE", enteredRace: { turnNumber: 31, resolution: "fuzzy", path: "scheduled", matchCount: 3 } },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe("unavailable")
        expect(a.enteredRace?.catalog.status).toBe("notJoinable")
        expect(a.enteredRace?.fact.resolution).toBe("fuzzy")
        expect(a.enteredRace?.fact.matchCount).toBe(3)
    })

    it("6. unresolved does not join and identity stays unavailable", () => {
        const a = annotateHistoricalTurn(
            { turn: 22, committedAction: "RACE", enteredRace: { turnNumber: 22, resolution: "unresolved", path: "standard" } },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe("unavailable")
        if (a.enteredRace?.catalog.status !== "notJoinable") throw new Error("expected notJoinable")
        expect(a.enteredRace.catalog.reason).toBe("unresolved")
    })

    it("7. nonCatalog/unityCupShowdown is a factual event: no join, no fit, identity unavailable", () => {
        const a = annotateHistoricalTurn(
            { turn: 40, committedAction: "RACE", enteredRace: { turnNumber: 40, resolution: "nonCatalog", path: "unityCupShowdown" } },
            uraTimeline,
            { surface: { TURF: "A" }, distance: { MILE: "A" } },
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe("unavailable")
        if (a.enteredRace?.catalog.status !== "notJoinable") throw new Error("expected notJoinable")
        expect(a.enteredRace.catalog.reason).toBe("nonCatalog")
        expect(a.enteredRace.fit).toBeNull()
        expect(a.enteredRace.fact.path).toBe("unityCupShowdown")
    })

    it("8. catalogLookupFailed preserves the producer name and stamps the catalog fingerprint, no throw", () => {
        const a = annotateHistoricalTurn(
            { turn: 9999, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, 9999) }, // real name, absent turn
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe(mandatoryRace.name) // producer truth preserved despite drift
        if (a.enteredRace?.catalog.status !== "catalogLookupFailed") throw new Error("expected catalogLookupFailed")
        expect(a.enteredRace.catalog.name).toBe(mandatoryRace.name)
        expect(a.enteredRace.catalog.turnNumber).toBe(9999)
        expect(a.enteredRace.catalog.catalogFingerprint).toBe(catalog.fingerprint())
    })

    it("9. same-name collision joins only (name, fact.turnNumber); the wrong turn never substitutes", () => {
        const [tA, tB] = marineTurns
        const jA = annotateHistoricalTurn({ turn: tA, committedAction: "RACE", enteredRace: exactFact("Marine Cup", tA) }, undefined, undefined, catalog).enteredRace
        const jB = annotateHistoricalTurn({ turn: tB, committedAction: "RACE", enteredRace: exactFact("Marine Cup", tB) }, undefined, undefined, catalog).enteredRace
        if (jA?.catalog.status !== "resolved" || jB?.catalog.status !== "resolved") throw new Error("expected both resolved")
        expect(jA.catalog.race.turnNumber).toBe(tA)
        expect(jB.catalog.race.turnNumber).toBe(tB)
        // A Marine Cup at a turn that has no Marine Cup fails the lookup rather than binding another occurrence.
        const jGhost = annotateHistoricalTurn({ turn: 9999, committedAction: "RACE", enteredRace: exactFact("Marine Cup", 9999) }, undefined, undefined, catalog).enteredRace
        expect(jGhost?.catalog.status).toBe("catalogLookupFailed")
    })

    it("10. an invalid fact refuses canonical enrichment and stays conservative, no throw", () => {
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: { ...exactFact(mandatoryRace.name, mandatoryRace.turnNumber), valid: false, issues: ["exact resolution with no name"] } },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe("unavailable")
        if (a.enteredRace?.catalog.status !== "notJoinable") throw new Error("expected notJoinable")
        expect(a.enteredRace.catalog.reason).toBe("invalid")
    })

    it("11. aptitude fit reuses classifyRaceFit for a joined race when aptitudes are supplied", () => {
        const apt = { surface: { [mandatoryRace.terrain.toUpperCase()]: "A" }, distance: { [mandatoryRace.distanceType.toUpperCase()]: "A" } }
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) },
            uraTimeline,
            apt,
            catalog,
        )
        expect(a.enteredRace?.fit?.meetsCurrentRuntimeAptitudeGate).toBe(true)
        expect(a.enteredRace?.fit).toEqual(classifyRaceFit(mandatoryRace, apt))
    })

    it("12. aptitude unavailable yields no invented fit (null)", () => {
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRace?.fit).toBeNull()
    })

    it("13. an exact (name, turn) match to a URA mandatory objective is matchesMandatoryObjective", () => {
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRace?.objectiveRelation).toBe("matchesMandatoryObjective")
    })

    it("14. an exact option-key match to a URA choice objective is matchesChoiceOption", () => {
        const a = annotateHistoricalTurn(
            { turn: choiceRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(choiceRace.name, choiceRace.turnNumber) },
            daiwaTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRace?.objectiveRelation).toBe("matchesChoiceOption")
    })

    it("15. a joined race not in the supplied URA objective set is nonObjective", () => {
        const a = annotateHistoricalTurn(
            { turn: nonObjectiveRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(nonObjectiveRace.name, nonObjectiveRace.turnNumber) },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRace?.catalog.status).toBe("resolved")
        expect(a.enteredRace?.objectiveRelation).toBe("nonObjective")
    })

    it("16. no objective timeline yields objectiveRelation unavailable", () => {
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) },
            undefined,
            undefined,
            catalog,
        )
        expect(a.enteredRace?.objectiveRelation).toBe("unavailable")
    })

    it("17. without a URA timeline (e.g. Trackblazer/Unity Cup callers), no URA relation is ever asserted", () => {
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) },
            undefined, // no timeline supplied
            undefined,
            catalog,
        )
        expect(a.enteredRace?.objectiveRelation).toBe("unavailable")
    })

    it("18. producer resolution and path are preserved verbatim through enrichment", () => {
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: { turnNumber: mandatoryRace.turnNumber, resolution: "exact", path: "mandatoryGoal", name: mandatoryRace.name, matchCount: 1 } },
            uraTimeline,
            undefined,
            catalog,
        )
        expect(a.enteredRace?.fact.resolution).toBe("exact")
        expect(a.enteredRace?.fact.path).toBe("mandatoryGoal")
    })

    it("19. the enrichment stamps the current catalog fingerprint on both resolved and failed joins", () => {
        const resolved = annotateHistoricalTurn({ turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) }, undefined, undefined, catalog).enteredRace
        const failed = annotateHistoricalTurn({ turn: 9999, committedAction: "RACE", enteredRace: exactFact(mandatoryRace.name, 9999) }, undefined, undefined, catalog).enteredRace
        if (resolved?.catalog.status !== "resolved" || failed?.catalog.status !== "catalogLookupFailed") throw new Error("unexpected statuses")
        expect(resolved.catalog.catalogFingerprint).toBe(catalog.fingerprint())
        expect(failed.catalog.catalogFingerprint).toBe(catalog.fingerprint())
    })

    it("20. repeated annotation output is byte-identical (deterministic)", () => {
        const input = { turn: mandatoryRace.turnNumber, committedAction: "RACE" as const, enteredRace: exactFact(mandatoryRace.name, mandatoryRace.turnNumber) }
        const a = JSON.stringify(annotateHistoricalTurn(input, uraTimeline, { surface: { TURF: "A" }, distance: { MILE: "A" } }, catalog))
        const b = JSON.stringify(annotateHistoricalTurn(input, uraTimeline, { surface: { TURF: "A" }, distance: { MILE: "A" } }, catalog))
        expect(a).toBe(b)
    })
})

// ---- Direct-call semantic hardening (validation bundle) ----

describe("entered-race join hardening (validation bundle)", () => {
    const uraTimeline = buildObjectiveTimeline("Copano Rickey", rawObjectives, catalog)
    const mandatoryReq = uraTimeline.requirements.find((r) => !r.isChoice) as ObjectiveRequirement
    const mandatoryRace = mandatoryReq.options[0].canonicalRace

    function joinOf(enteredRace: Record<string, unknown>) {
        const a = annotateHistoricalTurn({ turn: (enteredRace.turnNumber as number) ?? 16, committedAction: "RACE", enteredRace: enteredRace as never }, uraTimeline, undefined, catalog)
        return a.enteredRace
    }
    function reasonOf(enteredRace: Record<string, unknown>): string {
        const j = joinOf(enteredRace)
        if (j?.catalog.status !== "notJoinable") throw new Error(`expected notJoinable, got ${j?.catalog.status}`)
        return j.catalog.reason
    }

    it("unknown future resolution refuses join with reason unknownResolution, raw token preserved, no throw", () => {
        const j = joinOf({ turnNumber: 16, resolution: "quantumMatch", path: "smart", name: "Race A" })
        expect(reasonOf({ turnNumber: 16, resolution: "quantumMatch", path: "smart", name: "Race A" })).toBe("unknownResolution")
        expect(j?.fact.resolution).toBe("quantumMatch") // preserved raw
        const a = annotateHistoricalTurn({ turn: 16, committedAction: "RACE", enteredRace: { turnNumber: 16, resolution: "quantumMatch", path: "smart", name: "Race A" } }, uraTimeline, undefined, catalog)
        expect(a.enteredRaceIdentity).toBe("unavailable")
    })

    it("known producer unresolved keeps reason unresolved (not conflated with unknownResolution)", () => {
        expect(reasonOf({ turnNumber: 22, resolution: "unresolved", path: "standard" })).toBe("unresolved")
    })

    it("exact + matchCount > 1 (valid omitted) refuses canonical join as invalid, identity unavailable", () => {
        const bad = { turnNumber: mandatoryRace.turnNumber, resolution: "exact", path: "smart", name: mandatoryRace.name, matchCount: 2 }
        expect(reasonOf(bad)).toBe("invalid")
        const a = annotateHistoricalTurn({ turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: bad as never }, uraTimeline, undefined, catalog)
        expect(a.enteredRaceIdentity).toBe("unavailable")
    })

    it("unresolved + matchCount is invalid", () => {
        expect(reasonOf({ turnNumber: 22, resolution: "unresolved", path: "standard", matchCount: 2 })).toBe("invalid")
    })

    it("nonCatalog + matchCount is invalid", () => {
        expect(reasonOf({ turnNumber: 40, resolution: "nonCatalog", path: "unityCupShowdown", matchCount: 2 })).toBe("invalid")
    })

    it("ambiguousSet without matchCount is invalid", () => {
        expect(reasonOf({ turnNumber: 31, resolution: "ambiguousSet", path: "scheduled" })).toBe("invalid")
    })

    it("ambiguousSet with matchCount 1 is invalid", () => {
        expect(reasonOf({ turnNumber: 31, resolution: "ambiguousSet", path: "scheduled", matchCount: 1 })).toBe("invalid")
    })

    it("ambiguousSet with matchCount 2 stays ambiguous (joins nothing)", () => {
        expect(reasonOf({ turnNumber: 31, resolution: "ambiguousSet", path: "scheduled", matchCount: 2 })).toBe("ambiguous")
    })

    it("nameless exact is invalid, not merely ambiguous", () => {
        expect(reasonOf({ turnNumber: 16, resolution: "exact", path: "smart", matchCount: 1 })).toBe("invalid")
    })

    it("nameless fuzzy multi (matchCount > 1) stays ambiguous, not invalid", () => {
        expect(reasonOf({ turnNumber: 31, resolution: "fuzzy", path: "scheduled", matchCount: 3 })).toBe("ambiguous")
    })

    it("named fuzzy + matchCount > 1 (valid omitted) is invalid, never a canonical join", () => {
        const bad = { turnNumber: mandatoryRace.turnNumber, resolution: "fuzzy", path: "scheduled", name: mandatoryRace.name, matchCount: 2 }
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: bad as never },
            uraTimeline,
            { surface: { [mandatoryRace.terrain.toUpperCase()]: "A" }, distance: { [mandatoryRace.distanceType.toUpperCase()]: "A" } },
            catalog,
        )
        expect(a.enteredRaceIdentity).toBe("unavailable")
        if (a.enteredRace?.catalog.status !== "notJoinable") throw new Error("expected notJoinable")
        expect(a.enteredRace.catalog.reason).toBe("invalid")
        expect(a.enteredRace.fit).toBeNull()
        expect(a.enteredRace.objectiveRelation).toBe("unavailable")
    })

    it("a valid exact canonical tuple still resolves", () => {
        const j = joinOf({ turnNumber: mandatoryRace.turnNumber, resolution: "exact", path: "smart", name: mandatoryRace.name, matchCount: 1 })
        expect(j?.catalog.status).toBe("resolved")
    })

    it("a valid fuzzy unique tuple still resolves and stays fuzzy", () => {
        const j = joinOf({ turnNumber: mandatoryRace.turnNumber, resolution: "fuzzy", path: "scheduled", name: mandatoryRace.name, matchCount: 1 })
        expect(j?.catalog.status).toBe("resolved")
        expect(j?.fact.resolution).toBe("fuzzy")
    })

    it("finding F: a fact supplied without a catalog keeps legacy identity and adds no nested enrichment", () => {
        // No production caller invokes historical annotation without a catalog; this documents the
        // intentionally-retained behavior (raw producer name still surfaces; no enrichment is faked).
        const a = annotateHistoricalTurn(
            { turn: mandatoryRace.turnNumber, committedAction: "RACE", enteredRace: exactFactLocal(mandatoryRace.name, mandatoryRace.turnNumber) },
            uraTimeline,
            undefined,
            undefined,
        )
        expect(a.enteredRaceIdentity).toBe(mandatoryRace.name)
        expect(a.enteredRace).toBeUndefined()
    })

    function exactFactLocal(name: string, turnNumber: number) {
        return { turnNumber, resolution: "exact", path: "smart", name, matchCount: 1 }
    }
})
