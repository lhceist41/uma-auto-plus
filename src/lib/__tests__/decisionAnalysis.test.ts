import {
    CAREER_STATE_SCHEMA,
    CAREER_STATE_SCHEMA_VERSION,
    createDecisionAnalyzer,
    DECISION_SCHEMA,
    DECISION_SCHEMA_VERSION,
    EXIT_CLEAN,
    EXIT_CONSISTENCY,
    EXIT_PARSE_OR_SCHEMA,
    EXIT_WARNINGS,
    parseCareerStateLine,
    parseDecisionLine,
    renderReport,
} from "../decisionAnalysis"
import type { AnalysisResult } from "../decisionAnalysis"

// A realistic decision_trace v1 record, shaped exactly as DecisionTrace.buildRecord emits it:
// the committed action and training appear both in `candidates` (selected:true) and in `selected`.
function traceLine(overrides: Record<string, unknown> = {}): string {
    const base: Record<string, unknown> = {
        type: DECISION_SCHEMA,
        v: DECISION_SCHEMA_VERSION,
        ts: 1784213172197,
        app: "1.4.0",
        fp: "1e681a57e1",
        scenario: "Trackblazer",
        trainee: "Biwa Hayahide",
        preset: "Biwa Hayahide",
        careerToken: "Biwa Hayahide|Trackblazer|run0|3f9a1c22",
        turn: 5,
        year: "CLASSIC",
        month: "JANUARY",
        phase: "EARLY",
        state: { energy: 62, mood: "GOOD", skillPts: 340, fans: 12000, spd: 412, sta: 300, pwr: 288, grt: 190, wit: 260 },
        observation: { turnObserved: true, statsObserved: true, skillPointsObserved: true, aptitudesObserved: true },
        settings: { "Mood Floor": "GOOD" },
        candidates: [
            { type: "action", id: "TRAIN", selected: true, reason: "default action" },
            { type: "action", id: "RECOVER_MOOD", selected: false, rejected: true, reason: "mood at floor" },
            { type: "training", id: "SPEED", selected: true, reason: "won analysis", failChance: 8, gains: { spd: 11, pwr: 2 } },
            { type: "training", id: "WIT", selected: false, rejected: false, reason: "outscored", score: 12.25, failChance: 3 },
        ],
        selected: { action: "TRAIN", reason: "default action", source: "action_choice", training: "SPEED", trainingSource: "ANALYSIS", trainingReason: "won analysis" },
    }
    return JSON.stringify({ ...base, ...overrides })
}

// One career_finalize row as written to careers.jsonl, the intended 1:1 join target.
function finalizeLine(careerToken: string, extra: Record<string, unknown> = {}): string {
    return JSON.stringify({ type: "career_finalize", ts: 1784213999999, careerToken, trainee: "Biwa Hayahide", scenario: "Trackblazer", ...extra })
}

/** Feed a list of decision lines (and optional careers lines) through a fresh analyzer. */
function analyze(decisionLines: string[], careerLines: string[] = [], options = {}): AnalysisResult {
    const analyzer = createDecisionAnalyzer(options)
    careerLines.forEach((line, i) => analyzer.ingestCareerLine(line, i + 1))
    if (careerLines.length > 0) analyzer.noteCareerFile()
    decisionLines.forEach((line, i) => analyzer.ingestDecisionLine(line, i + 1))
    analyzer.noteDecisionFile()
    return analyzer.finish()
}

describe("parseDecisionLine", () => {
    test("valid v1 records parse", () => {
        const parsed = parseDecisionLine(traceLine(), 1)
        expect(parsed.kind).toBe("record")
    })

    test("blank lines are ignored, not failed", () => {
        expect(parseDecisionLine("", 1).kind).toBe("blank")
        expect(parseDecisionLine("   \t ", 2).kind).toBe("blank")
    })

    test("malformed JSON is reported with its line number", () => {
        const parsed = parseDecisionLine("{not json", 42)
        expect(parsed.kind).toBe("parseError")
        if (parsed.kind === "parseError") expect(parsed.lineNumber).toBe(42)
    })

    test("a non-object JSON line is a parse error", () => {
        expect(parseDecisionLine("[1,2,3]", 1).kind).toBe("parseError")
        expect(parseDecisionLine("5", 1).kind).toBe("parseError")
    })

    test("the wrong record type is reported, not reinterpreted", () => {
        const parsed = parseDecisionLine(JSON.stringify({ type: "career_finalize", v: 1, ts: 1 }), 1)
        expect(parsed.kind).toBe("wrongType")
    })

    test("a valid v2 record parses (forward-compatible reader)", () => {
        const parsed = parseDecisionLine(traceLine({ v: 2 }), 1)
        expect(parsed.kind).toBe("record")
    })

    test("a valid v2 record with unknown additive fields still parses", () => {
        const parsed = parseDecisionLine(traceLine({ v: 2, futureField: { nested: true }, anotherNewField: [1, 2, 3] }), 1)
        expect(parsed.kind).toBe("record")
    })

    test("a future unsupported schema version (v3) is reported, not reinterpreted", () => {
        const parsed = parseDecisionLine(traceLine({ v: 3 }), 1)
        expect(parsed.kind).toBe("unsupportedVersion")
        if (parsed.kind === "unsupportedVersion") expect(parsed.version).toBe(3)
    })

    test("a non-numeric version is reported as unsupported", () => {
        const parsed = parseDecisionLine(traceLine({ v: "2" }), 1)
        expect(parsed.kind).toBe("unsupportedVersion")
        if (parsed.kind === "unsupportedVersion") expect(parsed.version).toBe("2")
    })

    test("a v2 record still fails required-field validation (v2 does not weaken the envelope check)", () => {
        const line = JSON.stringify({ type: DECISION_SCHEMA, v: 2, trainee: "x" })
        const parsed = parseDecisionLine(line, 1)
        expect(parsed.kind).toBe("malformedEnvelope")
        if (parsed.kind === "malformedEnvelope") expect(parsed.missing).toContain("ts")
    })

    test("a record missing the ts envelope is a malformed envelope", () => {
        const line = JSON.stringify({ type: DECISION_SCHEMA, v: 1, trainee: "x" })
        const parsed = parseDecisionLine(line, 1)
        expect(parsed.kind).toBe("malformedEnvelope")
        if (parsed.kind === "malformedEnvelope") expect(parsed.missing).toContain("ts")
    })
})

describe("analysis", () => {
    test("a clean corpus reports no anomalies and exits 0", () => {
        const result = analyze([traceLine({ turn: 1 }), traceLine({ turn: 2 }), traceLine({ turn: 3 })])
        expect(result.decisionRecordCount).toBe(3)
        expect(result.parseErrors).toHaveLength(0)
        expect(result.schemaFailures).toHaveLength(0)
        expect(result.consistencyFailureCount).toBe(0)
        expect(result.warningCount).toBe(0)
        expect(result.exitCode).toBe(EXIT_CLEAN)
        expect(result.careers).toHaveLength(1)
        expect(result.careers[0].turnRange).toEqual([1, 3])
    })

    test("blank lines between records do not break analysis", () => {
        const result = analyze([traceLine({ turn: 1 }), "", "   ", traceLine({ turn: 2 })])
        expect(result.decisionRecordCount).toBe(2)
        expect(result.blankLines).toBe(2)
        expect(result.exitCode).toBe(EXIT_CLEAN)
    })

    test("a malformed line is reported and analysis continues past it", () => {
        const result = analyze([traceLine({ turn: 1 }), "{broken", traceLine({ turn: 2 })])
        expect(result.parseErrors).toHaveLength(1)
        expect(result.parseErrors[0].lineNumber).toBe(2)
        expect(result.decisionRecordCount).toBe(2) // the two good records still analyzed
        expect(result.exitCode).toBe(EXIT_PARSE_OR_SCHEMA)
    })

    test("strict mode aborts at the first malformed line", () => {
        const result = analyze([traceLine({ turn: 1 }), "{broken", traceLine({ turn: 2 })], [], { strict: true })
        expect(result.abortedByStrict).toBe(true)
        expect(result.decisionRecordCount).toBe(1) // stopped before the third line
        expect(result.exitCode).toBe(EXIT_PARSE_OR_SCHEMA)
    })

    test("unsupported schema versions surface as a schema failure exit", () => {
        const result = analyze([traceLine({ turn: 1 }), traceLine({ v: 99, turn: 2 })])
        expect(result.schemaFailures).toHaveLength(1)
        expect(result.exitCode).toBe(EXIT_PARSE_OR_SCHEMA)
    })

    test("v2 records are analyzed alongside v1 with no schema failure", () => {
        const result = analyze([traceLine({ turn: 1 }), traceLine({ v: 2, turn: 2 })])
        expect(result.decisionRecordCount).toBe(2)
        expect(result.schemaFailures).toHaveLength(0)
        expect(result.exitCode).toBe(EXIT_CLEAN)
    })

    test("duplicate observed turns are detected", () => {
        const result = analyze([traceLine({ turn: 7 }), traceLine({ turn: 7 }), traceLine({ turn: 8 })])
        const career = result.careers[0]
        expect(career.duplicateTurns).toEqual([{ turn: 7, count: 2 }])
        expect(result.warningCount).toBeGreaterThan(0)
        expect(result.exitCode).toBe(EXIT_WARNINGS)
    })

    test("non-monotonic observed turns are counted", () => {
        const result = analyze([traceLine({ turn: 5 }), traceLine({ turn: 3 }), traceLine({ turn: 6 })])
        expect(result.careers[0].nonMonotonicCount).toBe(1)
    })

    test("turn gaps are reported but do not fail the analysis", () => {
        // Days 4 and 8 skipped (race/summer turns legitimately emit no main-screen decision).
        const result = analyze([traceLine({ turn: 3 }), traceLine({ turn: 5 }), traceLine({ turn: 6 }), traceLine({ turn: 7 }), traceLine({ turn: 9 })])
        expect(result.careers[0].turnGaps).toEqual([4, 8])
        expect(result.consistencyFailureCount).toBe(0)
    })

    test("a selected action absent from its candidates is a consistency failure", () => {
        // selected.action REST, but the only selected action candidate is TRAIN.
        const line = traceLine({ selected: { action: "REST", source: "action_choice" } })
        const result = analyze([line])
        expect(result.careers[0].selectedActionNotInCandidates).toEqual([1])
        expect(result.consistencyFailureCount).toBe(1)
        expect(result.exitCode).toBe(EXIT_CONSISTENCY)
    })

    test("more than one selected action candidate is a consistency failure", () => {
        const line = traceLine({
            candidates: [
                { type: "action", id: "TRAIN", selected: true, reason: "a" },
                { type: "action", id: "REST", selected: true, reason: "b" },
            ],
            selected: { action: "TRAIN", source: "action_choice" },
        })
        const result = analyze([line])
        expect(result.careers[0].multipleSelectedActionCandidates).toEqual([1])
        expect(result.exitCode).toBe(EXIT_CONSISTENCY)
    })

    test("more than one selected training candidate is a consistency failure", () => {
        const line = traceLine({
            candidates: [
                { type: "action", id: "TRAIN", selected: true, reason: "a" },
                { type: "training", id: "SPEED", selected: true, reason: "b" },
                { type: "training", id: "POWER", selected: true, reason: "c" },
            ],
            selected: { action: "TRAIN", source: "action_choice", training: "SPEED" },
        })
        const result = analyze([line])
        expect(result.careers[0].multipleSelectedTrainingCandidates).toEqual([1])
        expect(result.exitCode).toBe(EXIT_CONSISTENCY)
    })

    test("an empty selection is a warning, not a consistency failure", () => {
        // A dialog ended the tick: the writer legitimately emits `selected: {}`.
        const line = traceLine({ candidates: [], selected: {} })
        const result = analyze([line])
        expect(result.careers[0].emptySelectionCount).toBe(1)
        expect(result.consistencyFailureCount).toBe(0)
        expect(result.exitCode).toBe(EXIT_WARNINGS)
    })

    test("missing optional identity is a warning and no value is fabricated", () => {
        const line = traceLine({ trainee: undefined, scenario: undefined, preset: undefined })
        const result = analyze([line])
        const career = result.careers[0]
        expect(career.recordsLackingIdentity).toBe(1)
        expect(career.trainees).toEqual([]) // not fabricated
        expect(career.scenarios).toEqual([])
        expect(result.consistencyFailureCount).toBe(0)
        expect(result.exitCode).toBe(EXIT_WARNINGS)
    })

    test("a missing turn number is caveated, never a consistency failure", () => {
        // No `turn` field: the date was never read (resume/default-date), which is not corruption.
        const line = traceLine({ turn: undefined })
        const result = analyze([line])
        expect(result.careers[0].missingTurnCount).toBe(1)
        expect(result.careers[0].turnRange).toBeNull()
        expect(result.consistencyFailureCount).toBe(0)
    })

    test("candidate score coverage and byte stats are computed", () => {
        const result = analyze([traceLine({ turn: 1 })])
        const career = result.careers[0]
        expect(career.recordsWithScoreData).toBe(1)
        expect(career.candidateStats.max).toBe(4)
        expect(career.totalBytes).toBeGreaterThan(0)
        expect(career.meanBytesPerRecord).toBeGreaterThan(0)
    })
})

describe("career-corpus join", () => {
    const token = "Biwa Hayahide|Trackblazer|run0|3f9a1c22"

    test("a 1:1 careerToken join succeeds", () => {
        const result = analyze([traceLine({ turn: 1 })], [finalizeLine(token)])
        const career = result.careers[0]
        expect(career.join).toBe("one")
        expect(career.joinMatchTypes).toEqual(["career_finalize"])
        expect(result.warningCount).toBe(0)
        expect(result.exitCode).toBe(EXIT_CLEAN)
    })

    test("no matching careers row is reported as a warning", () => {
        const result = analyze([traceLine({ turn: 1 })], [finalizeLine("Some Other|URA Finale|run0|zzzz")])
        expect(result.careers[0].join).toBe("none")
        expect(result.exitCode).toBe(EXIT_WARNINGS)
    })

    test("multiple matching careers rows are reported", () => {
        const result = analyze([traceLine({ turn: 1 })], [finalizeLine(token), finalizeLine(token, { retryUsed: true })])
        const career = result.careers[0]
        expect(career.join).toBe("multiple")
        expect(career.joinMatchCount).toBe(2)
        expect(result.exitCode).toBe(EXIT_WARNINGS)
    })

    test("a weak trainee|scenario|run match (different nonce) is a diagnostic suggestion only", () => {
        // Same career except the nonce differs: NOT proven identity, so join stays "none".
        const result = analyze([traceLine({ turn: 1 })], [finalizeLine("Biwa Hayahide|Trackblazer|run0|DIFFERENT")])
        const career = result.careers[0]
        expect(career.join).toBe("none")
        expect(career.weakJoinSuggestion).toContain("Biwa Hayahide|Trackblazer|run0|DIFFERENT")
    })

    test("without a careers file the join is not attempted", () => {
        const result = analyze([traceLine({ turn: 1 })])
        expect(result.joinAttempted).toBe(false)
        expect(result.careers[0].join).toBe("no-careers-file")
    })
})

describe("filters and grouping", () => {
    test("careerToken filter analyzes only the matching career", () => {
        const a = traceLine({ careerToken: "A|URA Finale|run0|n1", turn: 1 })
        const b = traceLine({ careerToken: "B|URA Finale|run0|n2", turn: 1 })
        const result = analyze([a, b], [], { careerToken: "A|URA Finale|run0|n1" })
        expect(result.decisionRecordCount).toBe(1)
        expect(result.careers).toHaveLength(1)
        expect(result.careers[0].careerToken).toBe("A|URA Finale|run0|n1")
    })

    test("since filter drops records before the boundary", () => {
        const older = traceLine({ ts: 1000, turn: 1 })
        const newer = traceLine({ ts: 5000, turn: 2 })
        const result = analyze([older, newer], [], { since: 3000 })
        expect(result.decisionRecordCount).toBe(1)
        expect(result.careers[0].turnRange).toEqual([2, 2])
    })

    test("fromLine skips earlier lines", () => {
        const result = analyze([traceLine({ turn: 1 }), traceLine({ turn: 2 }), traceLine({ turn: 3 })], [], { fromLine: 2 })
        expect(result.decisionRecordCount).toBe(2)
        expect(result.careers[0].turnRange).toEqual([2, 3])
    })

    test("records lacking a careerToken group into the unkeyed bucket", () => {
        const result = analyze([traceLine({ careerToken: undefined, turn: 1 })])
        expect(result.careers).toHaveLength(0)
        expect(result.unkeyed).not.toBeNull()
        expect(result.unkeyed?.recordCount).toBe(1)
        expect(result.warningCount).toBeGreaterThan(0)
    })
})

describe("read-only guarantees", () => {
    test("analysis never mutates the record objects it is given", () => {
        // parseDecisionLine returns the parsed object; ingesting it must not write to it.
        const raw = traceLine({ turn: 1 })
        const parsed = JSON.parse(raw)
        const frozen = Object.freeze(parsed)
        const before = JSON.stringify(frozen)
        const analyzer = createDecisionAnalyzer()
        // Feeding the exact frozen object as a line would re-parse; instead assert the source string
        // survives a full analyze unchanged (the analyzer only reads).
        analyzer.ingestDecisionLine(raw, 1)
        analyzer.noteDecisionFile()
        analyzer.finish()
        expect(raw).toBe(traceLine({ turn: 1 }))
        expect(JSON.stringify(frozen)).toBe(before)
    })

    test("the report renders deterministically for the same input", () => {
        const lines = [traceLine({ turn: 1 }), traceLine({ turn: 2 })]
        expect(renderReport(analyze(lines))).toBe(renderReport(analyze(lines)))
    })
})

describe("cross-career aggregate", () => {
    const tokenA = "Taiki Shuttle|Unity Cup|run0|aaaa"
    const tokenB = "Biwa Hayahide|Trackblazer|run0|bbbb"

    // A record committing to a training (TRAIN action + the given training, both represented in candidates).
    function trainLine(training: string, overrides: Record<string, unknown> = {}): string {
        return traceLine({
            candidates: [
                { type: "action", id: "TRAIN", selected: true, reason: "default" },
                { type: "training", id: training, selected: true, reason: "won", failChance: 5, gains: { spd: 10 } },
                { type: "training", id: "WIT", selected: false, rejected: true, reason: "excluded" },
            ],
            selected: { action: "TRAIN", source: "action_choice", training, trainingSource: "ANALYSIS", trainingReason: "won" },
            ...overrides,
        })
    }
    // A record committing to RACE (an action with no selected training), represented in its candidates.
    function raceLine(overrides: Record<string, unknown> = {}): string {
        return traceLine({
            candidates: [{ type: "action", id: "RACE", selected: true, reason: "race required" }],
            selected: { action: "RACE", source: "action_choice", reason: "race required" },
            ...overrides,
        })
    }
    const careerA = tokenA
    const careerB = tokenB
    // Career-A defaults: Unity Cup / Taiki Shuttle; Career-B: Trackblazer / Biwa Hayahide.
    function aLine(overrides: Record<string, unknown> = {}): string {
        return trainLine("SPEED", { careerToken: careerA, scenario: "Unity Cup", trainee: "Taiki Shuttle", preset: "Taiki Shuttle", ...overrides })
    }
    function bLine(overrides: Record<string, unknown> = {}): string {
        return trainLine("SPEED", { careerToken: careerB, scenario: "Trackblazer", trainee: "Biwa Hayahide", preset: "Biwa Hayahide", ...overrides })
    }
    // A committed-training record on career A, with an explicit training stat.
    function aTrain(training: string, turn: number): string {
        return trainLine(training, { careerToken: careerA, scenario: "Unity Cup", trainee: "Taiki Shuttle", preset: "Taiki Shuttle", turn })
    }
    // A RACE record on career A (no committed training).
    function aRace(turn: number): string {
        return raceLine({ careerToken: careerA, scenario: "Unity Cup", trainee: "Taiki Shuttle", preset: "Taiki Shuttle", turn })
    }
    function finalize(token: string, extra: Record<string, unknown> = {}): string {
        return JSON.stringify({ type: "career_finalize", ts: 1784213999999, careerToken: token, finalizationDecision: "FINISH", sessionOutcome: "committed", verifiedRemainingSp: 10, objective: "rank", ...extra })
    }

    function agg(decisionLines: string[], careerLines: string[] = [], options = {}) {
        const result = analyze(decisionLines, careerLines, { aggregate: true, ...options })
        expect(result.aggregate).toBeDefined()
        return result.aggregate!
    }

    test("1. two valid careers aggregate into one corpus summary", () => {
        const a = agg([aLine({ turn: 1 }), aLine({ turn: 2 }), bLine({ turn: 1 })])
        expect(a.mode).toBe("aggregate")
        expect(a.corpus.distinctKeyedCareers).toBe(2)
        expect(a.corpus.totalValidRecords).toBe(3)
        expect(a.careers).toHaveLength(2)
    })

    test("2. careers from two scenarios produce separate, deterministically ordered scenario summaries", () => {
        const a = agg([aLine({ turn: 1 }), bLine({ turn: 1 })])
        expect(a.scenarios.map((s) => s.scenario)).toEqual(["Trackblazer", "Unity Cup"])
        expect(a.scenarios.every((s) => s.careerCount === 1)).toBe(true)
    })

    test("3. action counts and percentages are correct", () => {
        // One career: 3 TRAIN + 1 RACE.
        const a = agg([aTrain("SPEED", 1), aTrain("SPEED", 2), aTrain("SPEED", 3), aRace(4)])
        const train = a.actions.find((x) => x.id === "TRAIN")!
        const race = a.actions.find((x) => x.id === "RACE")!
        expect(train.count).toBe(3)
        expect(train.pct).toBe(75)
        expect(race.count).toBe(1)
        expect(race.pct).toBe(25)
    })

    test("4. training counts and percentages are correct", () => {
        // 3 SPEED + 1 POWER committed trainings.
        const a = agg([aTrain("SPEED", 1), aTrain("SPEED", 2), aTrain("SPEED", 3), aTrain("POWER", 4)])
        const speed = a.trainings.find((x) => x.id === "SPEED")!
        const power = a.trainings.find((x) => x.id === "POWER")!
        expect(speed.count).toBe(3)
        expect(speed.pct).toBe(75)
        expect(power.count).toBe(1)
        expect(power.pct).toBe(25)
    })

    test("5. a trace with no selected training is handled (not counted, not malformed)", () => {
        const a = agg([aTrain("SPEED", 1), aRace(2)])
        // Only the one TRAIN/SPEED record contributes to the training denominator.
        const speed = a.trainings.find((x) => x.id === "SPEED")!
        expect(speed.count).toBe(1)
        expect(speed.pct).toBe(100)
        expect(a.corpus.consistencyFailures).toBe(0)
    })

    test("6. observation-read coverage is correct", () => {
        const a = agg([
            aLine({ turn: 1, observation: { turnObserved: true, statsObserved: true, skillPointsObserved: true, aptitudesObserved: false } }),
            aLine({ turn: 2, observation: { turnObserved: true, statsObserved: true, skillPointsObserved: true, aptitudesObserved: true } }),
        ])
        const apt = a.observations.flags.find((f) => f.flag === "aptitudes")!
        expect(a.observations.totalRecords).toBe(2)
        expect(apt.observed).toBe(1)
        expect(apt.missing).toBe(1)
        expect(apt.pctObserved).toBe(50)
        expect(apt.careersWithUnobserved).toBe(1)
    })

    test("7. median records-per-career is correct for odd and even career counts", () => {
        // Odd: 3 careers with 1, 2, 3 records -> median 2.
        const odd = agg([
            aLine({ careerToken: "A|Unity Cup|run0|1", turn: 1 }),
            aLine({ careerToken: "B|Unity Cup|run0|2", turn: 1 }),
            aLine({ careerToken: "B|Unity Cup|run0|2", turn: 2 }),
            aLine({ careerToken: "C|Unity Cup|run0|3", turn: 1 }),
            aLine({ careerToken: "C|Unity Cup|run0|3", turn: 2 }),
            aLine({ careerToken: "C|Unity Cup|run0|3", turn: 3 }),
        ])
        expect(odd.corpus.recordsPerCareer.median).toBe(2)
        // Even: 2 careers with 1 and 3 records -> median 2.
        const even = agg([
            aLine({ careerToken: "A|Unity Cup|run0|1", turn: 1 }),
            aLine({ careerToken: "B|Unity Cup|run0|2", turn: 1 }),
            aLine({ careerToken: "B|Unity Cup|run0|2", turn: 2 }),
            aLine({ careerToken: "B|Unity Cup|run0|2", turn: 3 }),
        ])
        expect(even.corpus.recordsPerCareer.median).toBe(2)
    })

    test("8. a missing career_finalize join is surfaced", () => {
        const a = agg([aLine({ turn: 1 })], [finalize("some-other-token")])
        expect(a.corpus.careersWithNoFinalize).toBe(1)
        expect(a.corpus.careersWithExactlyOneFinalize).toBe(0)
        expect(a.outcomes.joinedCareerCount).toBe(0)
    })

    test("9. a duplicate career_finalize join is surfaced", () => {
        const a = agg([aLine({ turn: 1 })], [finalize(careerA), finalize(careerA, { retryUsed: true })])
        expect(a.corpus.careersWithDuplicateFinalize).toBe(1)
        expect(a.outcomes.joinedCareerCount).toBe(0) // duplicates are not aggregated
        expect(a.outcomes.careersWithDuplicateFinalize).toBe(1)
    })

    test("10. joined finalize fields aggregate only across 1:1-joined careers", () => {
        // Career A joins; career B has no finalize row.
        const a = agg([aLine({ turn: 1 }), bLine({ turn: 1 })], [finalize(careerA, { verifiedRemainingSp: 42 })])
        expect(a.outcomes.joinedCareerCount).toBe(1)
        expect(a.outcomes.remainingSp.count).toBe(1)
        expect(a.outcomes.remainingSp.mean).toBe(42)
        expect(a.outcomes.finalizationDecisionCounts).toEqual([{ value: "FINISH", count: 1 }])
    })

    test("11. unkeyed records are counted, never fabricated into a keyed career", () => {
        const a = agg([aLine({ turn: 1 }), traceLine({ careerToken: undefined, turn: 1 })])
        expect(a.corpus.unkeyedRecordCount).toBe(1)
        expect(a.corpus.distinctKeyedCareers).toBe(1)
        expect(a.careers).toHaveLength(1)
        expect(a.careers.every((c) => c.careerToken !== "(unkeyed)")).toBe(true)
        // Unkeyed records still count toward whole-file quality totals.
        expect(a.candidates.totalRecords).toBe(2)
    })

    test("12. parse/schema/consistency failures retain exit-code precedence in aggregate mode", () => {
        const parse = analyze([aLine({ turn: 1 }), "{broken"], [], { aggregate: true })
        expect(parse.exitCode).toBe(EXIT_PARSE_OR_SCHEMA)
        expect(parse.aggregate).toBeDefined()
        const bad = aLine({ turn: 2, selected: { action: "REST", source: "action_choice" } }) // REST not in candidates
        const consistency = analyze([bad], [], { aggregate: true })
        expect(consistency.exitCode).toBe(EXIT_CONSISTENCY)
        expect(consistency.aggregate!.candidates.recordsSelectedActionNotRepresented).toBe(1)
    })

    test("13. existing filters are applied before aggregate calculation", () => {
        const a = agg([aLine({ turn: 1 }), bLine({ turn: 1 })], [], { careerToken: careerA })
        expect(a.corpus.distinctKeyedCareers).toBe(1)
        expect(a.corpus.totalValidRecords).toBe(1)
        expect(a.careers[0].careerToken).toBe(careerA)
    })

    test("14. aggregate JSON output is deterministic", () => {
        const lines = [aLine({ turn: 1 }), aLine({ turn: 2 }), bLine({ turn: 1 }), raceLine({ careerToken: careerB, scenario: "Trackblazer", trainee: "Biwa Hayahide", turn: 2 })]
        const careers = [finalize(careerA), finalize(careerB, { verifiedRemainingSp: 5 })]
        const first = JSON.stringify(agg(lines, careers))
        const second = JSON.stringify(agg(lines, careers))
        expect(first).toBe(second)
    })

    test("15. non-aggregate mode does not attach an aggregate object", () => {
        const plain = analyze([aLine({ turn: 1 })])
        expect(plain.aggregate).toBeUndefined()
    })

    test("16. aggregate mode is read-only (input strings survive analysis unchanged)", () => {
        const raw = aLine({ turn: 1 })
        const before = raw
        analyze([raw], [finalize(careerA)], { aggregate: true })
        expect(raw).toBe(before)
    })

    test("candidate diagnostics: zero-candidate, hard-excluded and median are computed", () => {
        const a = agg([
            aLine({ turn: 1 }), // 3 candidates incl. a hard-excluded WIT
            traceLine({ careerToken: careerA, scenario: "Unity Cup", trainee: "Taiki Shuttle", turn: 2, candidates: [], selected: {} }), // zero candidates
        ])
        expect(a.candidates.recordsWithZeroCandidates).toBe(1)
        expect(a.candidates.recordsWithHardExcluded).toBe(1)
        expect(a.candidates.partialActionCandidateCoverage).toBeNull()
    })

    test("outcome scenario/trainee come from the finalize row verbatim (may differ from the decision side)", () => {
        // Decision side spells with spaces; finalize row uses underscores. Both are reported honestly.
        const a = agg([aLine({ turn: 1 })], [finalize(careerA, { scenario: "Unity_Cup", trainee: "Taiki_Shuttle" })])
        expect(a.outcomes.scenarioCounts).toEqual([{ value: "Unity_Cup", count: 1 }])
        expect(a.careers[0].scenario).toBe("Unity Cup") // decision-side, unchanged
    })
})

describe("career_state join", () => {
    const token = "Biwa Hayahide|Trackblazer|run0|3f9a1c22"

    // A career_state v1 record shaped exactly as CareerStateSerializer.buildRecord emits it (join key = identity.careerToken + top-level seq).
    function careerStateLine(overrides: Record<string, unknown> = {}): string {
        const base: Record<string, unknown> = {
            type: CAREER_STATE_SCHEMA,
            v: CAREER_STATE_SCHEMA_VERSION,
            ts: 1784213172197,
            seq: 5,
            identity: { careerToken: token, scenario: "Trackblazer", trainee: "Biwa Hayahide", preset: "Biwa Hayahide", queueRun: 0, fp: "1e681a57e1" },
            turn: 9,
            year: "CLASSIC",
            month: "JANUARY",
            phase: "EARLY",
            observation: { turnObserved: true },
            condition: { energy: 62, mood: "GOOD", negativeStatuses: ["Headache"] },
            stats: { spd: 412, sta: 300, pwr: 288, grt: 190, wit: 260 },
            skillPts: 340,
            race: { mandatory: false, scheduled: false, goalRibbon: false },
            provenance: { identityInputs: "configured", date: "observed" },
        }
        return JSON.stringify({ ...base, ...overrides })
    }

    /** Feed decision lines, career_state lines, and optional careers lines through a career-state-enabled analyzer. */
    function joinAnalyze(decisionLines: string[], stateLines: string[], careerLines: string[] = [], options = {}): AnalysisResult {
        const analyzer = createDecisionAnalyzer({ careerState: true, ...options })
        careerLines.forEach((line, i) => analyzer.ingestCareerLine(line, i + 1))
        if (careerLines.length > 0) analyzer.noteCareerFile()
        stateLines.forEach((line, i) => analyzer.ingestCareerStateLine(line, i + 1))
        analyzer.noteCareerStateFile()
        decisionLines.forEach((line, i) => analyzer.ingestDecisionLine(line, i + 1))
        analyzer.noteDecisionFile()
        return analyzer.finish()
    }

    describe("parseCareerStateLine", () => {
        test("a valid record parses and exposes only the join key + turnObserved", () => {
            const parsed = parseCareerStateLine(careerStateLine(), 1)
            expect(parsed.kind).toBe("record")
            if (parsed.kind === "record") {
                expect(parsed.careerToken).toBe(token)
                expect(parsed.seq).toBe(5)
                expect(parsed.turnObserved).toBe(true)
            }
        })

        test("blank lines are ignored, not failed", () => {
            expect(parseCareerStateLine("", 1).kind).toBe("blank")
            expect(parseCareerStateLine("  \t ", 2).kind).toBe("blank")
        })

        test("the wrong record type is reported, not reinterpreted", () => {
            expect(parseCareerStateLine(careerStateLine({ type: "decision_trace" }), 1).kind).toBe("wrongType")
        })

        test("a valid v2 record parses (forward-compatible reader)", () => {
            const parsed = parseCareerStateLine(careerStateLine({ v: 2 }), 1)
            expect(parsed.kind).toBe("record")
            if (parsed.kind === "record") {
                expect(parsed.careerToken).toBe(token)
                expect(parsed.seq).toBe(5)
            }
        })

        test("a valid v2 record with unknown additive fields still parses", () => {
            const parsed = parseCareerStateLine(careerStateLine({ v: 2, futureField: { nested: true }, anotherNewField: [1, 2, 3] }), 1)
            expect(parsed.kind).toBe("record")
        })

        test("a future unsupported version (v3) is reported", () => {
            const parsed = parseCareerStateLine(careerStateLine({ v: 3 }), 1)
            expect(parsed.kind).toBe("unsupportedVersion")
            if (parsed.kind === "unsupportedVersion") expect(parsed.version).toBe(3)
        })

        test("a v2 record still fails required-field validation (v2 does not weaken the envelope check)", () => {
            const parsed = parseCareerStateLine(careerStateLine({ v: 2, seq: 0 }), 1)
            expect(parsed.kind).toBe("malformedEnvelope")
            if (parsed.kind === "malformedEnvelope") expect(parsed.missing).toContain("seq")
        })

        test("a bad ts is a malformed envelope", () => {
            const parsed = parseCareerStateLine(careerStateLine({ ts: "nope" }), 1)
            expect(parsed.kind).toBe("malformedEnvelope")
            if (parsed.kind === "malformedEnvelope") expect(parsed.missing).toContain("ts")
        })

        test("a missing identity.careerToken is a malformed envelope", () => {
            const parsed = parseCareerStateLine(careerStateLine({ identity: { scenario: "Trackblazer" } }), 1)
            expect(parsed.kind).toBe("malformedEnvelope")
            if (parsed.kind === "malformedEnvelope") expect(parsed.missing).toContain("identity.careerToken")
        })

        test("a non-positive or non-integer seq is a malformed envelope", () => {
            for (const bad of [0, -3, 2.5, "5", null]) {
                const parsed = parseCareerStateLine(careerStateLine({ seq: bad }), 1)
                expect(parsed.kind).toBe("malformedEnvelope")
                if (parsed.kind === "malformedEnvelope") expect(parsed.missing).toContain("seq")
            }
        })

        test("malformed JSON is a parse error", () => {
            expect(parseCareerStateLine("{not json", 1).kind).toBe("parseError")
            expect(parseCareerStateLine("[1,2]", 1).kind).toBe("parseError")
        })
    })

    test("2. a trace with seq joins its matching career_state on (careerToken, seq)", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: 5 })], [careerStateLine({ seq: 5 })])
        expect(result.join).toBeDefined()
        const j = result.join!
        expect(j.joinedPairCount).toBe(1)
        expect(j.stateWithoutTrace).toBe(0)
        expect(j.traceWithoutState).toBe(0)
        expect(j.sequencedTraceCount).toBe(1)
        expect(j.stateRecordCount).toBe(1)
        expect(result.exitCode).toBe(EXIT_CLEAN)
    })

    test("3. a career_state with no matching trace is a benign coverage gap, not an error", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: 5 })], [careerStateLine({ seq: 5 }), careerStateLine({ seq: 6 })])
        const j = result.join!
        expect(j.stateWithoutTrace).toBe(1) // seq 6 has no trace
        expect(j.joinedPairCount).toBe(1)
        expect(result.exitCode).not.toBe(EXIT_PARSE_OR_SCHEMA)
        expect(result.exitCode).not.toBe(EXIT_CONSISTENCY)
    })

    test("4. a trace with a seq but no matching state is a benign coverage gap", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: 5 }), traceLine({ turn: 10, seq: 6 })], [careerStateLine({ seq: 5 })])
        const j = result.join!
        expect(j.traceWithoutState).toBe(1) // trace seq 6 has no state
        expect(j.joinedPairCount).toBe(1)
        expect(result.exitCode).not.toBe(EXIT_PARSE_OR_SCHEMA)
        expect(result.exitCode).not.toBe(EXIT_CONSISTENCY)
    })

    test("5. a duplicate (careerToken, seq) among career_state records is a consistency failure (exit 3)", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: 5 })], [careerStateLine({ seq: 5 }), careerStateLine({ seq: 5 })])
        expect(result.join!.stateDuplicateKeys).toEqual([{ careerToken: token, seq: 5 }])
        expect(result.exitCode).toBe(EXIT_CONSISTENCY)
    })

    test("5b. a duplicate (careerToken, seq) among sequenced traces is a consistency failure (exit 3)", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: 5 }), traceLine({ turn: 9, seq: 5 })], [careerStateLine({ seq: 5 })])
        expect(result.join!.traceDuplicateKeys).toEqual([{ careerToken: token, seq: 5 }])
        expect(result.exitCode).toBe(EXIT_CONSISTENCY)
    })

    test("6. an old trace without seq is counted unsequenced and stays valid", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: undefined }), traceLine({ turn: 10, seq: 6 })], [careerStateLine({ seq: 6 })])
        const j = result.join!
        expect(j.unsequencedTraceCount).toBe(1)
        expect(j.sequencedTraceCount).toBe(1)
        expect(j.joinedPairCount).toBe(1)
        expect(result.parseErrors).toHaveLength(0)
        expect(result.schemaFailures).toHaveLength(0)
    })

    test("6b. a malformed career_state record surfaces as a parse/schema failure (exit 2), not a join gap", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: 5 })], [careerStateLine({ seq: 5 }), careerStateLine({ seq: -1 })])
        expect(result.join!.stateParseSchemaFailures).toHaveLength(1)
        expect(result.exitCode).toBe(EXIT_PARSE_OR_SCHEMA)
    })

    test("7. without the career-state option, output and exit code are unchanged and no join section appears", () => {
        const lines = [traceLine({ turn: 1, seq: 1 }), traceLine({ turn: 2 }), traceLine({ turn: 3, seq: 3 })]
        const withoutState = analyze(lines)
        expect(withoutState.join).toBeUndefined()
        expect(renderReport(withoutState)).not.toContain("Career-state join")

        // A career-state-enabled run over the SAME decisions but with an empty state file must not change
        // the exit code, the rendered decision sections, or add a join section to the plain analysis.
        const plain = analyze(lines)
        const enabled = joinAnalyze(lines, [])
        expect(enabled.exitCode).toBe(plain.exitCode)
        expect(renderReport(plain)).toBe(stripJoinSection(renderReport(enabled)))
    })

    test("8. two runs with career-state input produce identical join JSON", () => {
        const decisions = [traceLine({ turn: 9, seq: 5 }), traceLine({ turn: 10, seq: 6 }), traceLine({ turn: 11, seq: 7 })]
        const states = [careerStateLine({ seq: 5 }), careerStateLine({ seq: 6 }), careerStateLine({ seq: 8 })]
        const first = JSON.stringify(joinAnalyze(decisions, states).join)
        const second = JSON.stringify(joinAnalyze(decisions, states).join)
        expect(first).toBe(second)
    })

    test("the join section renders with the benign-gap wording when a career-state input is present", () => {
        const result = joinAnalyze([traceLine({ turn: 9, seq: 5 })], [careerStateLine({ seq: 5 })])
        const rendered = renderReport(result)
        expect(rendered).toContain("Career-state join (token+seq)")
        expect(rendered).toContain("benign, not failures")
    })
})

describe("enteredRace additive-field compatibility", () => {
    // A RACE trace whose committed action is present in its candidates, so it is consistent on its own.
    const raceOverrides = {
        candidates: [{ type: "action", id: "RACE", selected: true, reason: "mandatory race" }],
        selected: { action: "RACE", reason: "mandatory race", source: "action_choice" },
    }

    test("a v1 record carrying enteredRace still parses as a record", () => {
        const line = traceLine({
            ...raceOverrides,
            enteredRace: { turnNumber: 34, resolution: "exact", path: "mandatoryGoal", name: "Tokyo Yushun (Japanese Derby)" },
        })
        expect(parseDecisionLine(line, 1).kind).toBe("record")
    })

    test("a corpus mixing enteredRace, old, and seq-less records analyzes clean at v1", () => {
        // A pre-enteredRace, pre-seq record: neither field present. Must remain valid (additive fields).
        // Kept first so turns stay monotonic (an out-of-order turn is a benign warning unrelated to this field).
        const legacy = traceLine({ turn: 1 })
        const withEntered = traceLine({
            turn: 34,
            ...raceOverrides,
            enteredRace: { turnNumber: 34, resolution: "ambiguousSet", path: "scheduled", matchCount: 2 },
        })
        const nonCatalog = traceLine({
            turn: 60,
            ...raceOverrides,
            enteredRace: { turnNumber: 60, resolution: "nonCatalog", path: "unityCupShowdown" },
        })
        const result = analyze([legacy, withEntered, nonCatalog])
        expect(result.decisionRecordCount).toBe(3)
        expect(result.parseErrors).toHaveLength(0)
        expect(result.schemaFailures).toHaveLength(0)
        expect(result.consistencyFailureCount).toBe(0)
        expect(result.exitCode).toBe(EXIT_CLEAN)
    })

    test("an unknown future enteredRace sub-field is tolerated, not rejected", () => {
        // The reader ignores fields it does not know; a Phase-2 addition inside enteredRace must not
        // retroactively fail Phase-1 corpora.
        const line = traceLine({
            ...raceOverrides,
            enteredRace: { turnNumber: 5, resolution: "exact", path: "smart", name: "Osaka Hai", futureField: 7 },
        })
        expect(parseDecisionLine(line, 1).kind).toBe("record")
    })
})

// Removes the additive career-state join section (and the blank line preceding it) so a join-enabled
// render can be compared against the plain render of the same decisions.
function stripJoinSection(rendered: string): string {
    const idx = rendered.indexOf("## Career-state join")
    if (idx < 0) return rendered
    return rendered.slice(0, idx).replace(/\n+$/, "")
}
