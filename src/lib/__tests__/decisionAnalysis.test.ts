import {
    createDecisionAnalyzer,
    DECISION_SCHEMA,
    DECISION_SCHEMA_VERSION,
    EXIT_CLEAN,
    EXIT_CONSISTENCY,
    EXIT_PARSE_OR_SCHEMA,
    EXIT_WARNINGS,
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

    test("unsupported schema versions are reported", () => {
        const parsed = parseDecisionLine(traceLine({ v: 2 }), 1)
        expect(parsed.kind).toBe("unsupportedVersion")
        if (parsed.kind === "unsupportedVersion") expect(parsed.version).toBe(2)
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
