import { execFileSync } from "node:child_process"
import { mkdtempSync, writeFileSync, rmSync, readFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { join } from "node:path"
import process from "node:process"
import { evaluateCorpus, stableStringify, EVALUATION_VERSION } from "../evaluate.ts"

// ---- fixture builders ----

interface Gain {
    spd?: number
    sta?: number
    pwr?: number
    grt?: number
    wit?: number
}
function trainCand(id: string, gains: Gain, failChance: number, selected = false, score = 1): Record<string, unknown> {
    return { type: "training", id, selected, rejected: false, reason: selected ? `won analysis (X) with score ${score.toFixed(2)}` : "outscored", gains, failChance, score }
}
/** Five-facility contest with SPEED as top raw gain (all failChance equal so no failureRiskLower noise). */
function fullContest(selectedId: string, fc = 10): Record<string, unknown>[] {
    return [
        trainCand("SPEED", { spd: 30 }, fc, selectedId === "SPEED"),
        trainCand("STAMINA", { sta: 20 }, fc, selectedId === "STAMINA"),
        trainCand("POWER", { pwr: 15 }, fc, selectedId === "POWER"),
        trainCand("GUTS", { grt: 10 }, fc, selectedId === "GUTS"),
        trainCand("WIT", { wit: 12 }, fc, selectedId === "WIT"),
    ]
}
function decRec(o: { token?: string; seq: number; turn?: number; action?: string; training?: string; trainingSource?: string; candidates?: unknown[]; scenario?: string }): Record<string, unknown> {
    const action = o.action ?? "TRAIN"
    const selected: Record<string, unknown> =
        action === "TRAIN"
            ? { action: "TRAIN", source: "action_choice", training: o.training, trainingSource: o.trainingSource ?? "ANALYSIS", trainingReason: "won analysis (X) with score 1.00" }
            : { action, source: "action_choice", reason: "x" }
    return { type: "decision_trace", v: 1, ts: 1000, careerToken: o.token ?? "T|A", scenario: o.scenario ?? "Trackblazer", seq: o.seq, turn: o.turn ?? 10, observation: { turnObserved: true }, candidates: o.candidates ?? [], selected }
}
function stateRec(o: { token?: string; seq: number; energy?: number; mood?: string; mandatory?: boolean; scheduled?: boolean; scenarioType?: string | null }): Record<string, unknown> {
    const rec: Record<string, unknown> = {
        type: "career_state",
        v: 1,
        ts: 1000,
        seq: o.seq,
        identity: { careerToken: o.token ?? "T|A", scenario: "Trackblazer", trainee: "Taiki", preset: "Taiki", fp: "fp1" },
        turn: 10,
        observation: { turnObserved: true },
        condition: { energy: o.energy ?? 70, mood: o.mood ?? "GOOD", negativeStatuses: [], positiveStatuses: [] },
        stats: { spd: 300, sta: 200, pwr: 250, grt: 180, wit: 220 },
        skillPts: 100,
        race: { mandatory: o.mandatory ?? false, scheduled: o.scheduled ?? false, goalRibbon: false },
    }
    if (o.scenarioType !== null) rec.scenario = { type: o.scenarioType ?? "trackblazer" }
    return rec
}

/** The standard mixed corpus exercising every comparison state. Returns [decisions, states]. */
function standardCorpus(): [Record<string, unknown>[], Record<string, unknown>[]] {
    const d = [
        decRec({ seq: 1, action: "TRAIN", training: "SPEED", candidates: fullContest("SPEED") }), // advisor SPEED -> sameAction
        decRec({ seq: 2, action: "TRAIN", training: "STAMINA", candidates: fullContest("STAMINA") }), // advisor SPEED -> sameActionDifferentTraining
        decRec({ seq: 3, action: "TRAIN", training: "SPEED", trainingSource: "FORCED_FROM_SKIPPED", candidates: fullContest("SPEED") }), // low energy -> advisor REST -> differentAction
        decRec({ seq: 4, action: "RACE", candidates: fullContest("SPEED") }), // advisor TRAIN available, bot RACE -> comparisonNotApplicable
        decRec({ seq: 5, action: "DATE", candidates: [] }), // no contest -> advisor notApplicable -> advisorUnavailable
        decRec({ seq: 6, action: "TRAIN", training: "SPEED", candidates: [trainCand("SPEED", { spd: 10 }, 10, true)] }), // incomplete -> insufficient -> advisorUnavailable
    ]
    const s = [
        stateRec({ seq: 1 }),
        stateRec({ seq: 2 }),
        stateRec({ seq: 3, energy: 20 }), // triggers advisor REST
        stateRec({ seq: 4 }),
        stateRec({ seq: 5 }),
        stateRec({ seq: 6 }),
    ]
    return [d, s]
}

// ---- Assembly / raw grouping ----

describe("assembly and raw grouping", () => {
    it("2/3. same seq in different careers cannot contaminate; no turn fallback", () => {
        const d = [decRec({ token: "T|A", seq: 5, training: "SPEED", candidates: fullContest("SPEED") }), decRec({ token: "T|B", seq: 5, training: "SPEED", candidates: fullContest("SPEED") })]
        const s = [stateRec({ token: "T|A", seq: 5, scenarioType: "trackblazer" }), stateRec({ token: "T|B", seq: 5, scenarioType: "ura" })]
        const r = evaluateCorpus(d, s)
        const a = r.rows.find((x) => x.careerToken === "T|A")
        const b = r.rows.find((x) => x.careerToken === "T|B")
        expect(a?.scenarioType).toBe("trackblazer")
        expect(b?.scenarioType).toBe("ura") // did not pick up T|A's seq-5 state
    })

    it("4. seq-less decision rows are not joined by turn", () => {
        const d = [{ type: "decision_trace", v: 1, ts: 1, careerToken: "T|A", turn: 10, observation: { turnObserved: true }, candidates: fullContest("SPEED"), selected: { action: "TRAIN", training: "SPEED" } }]
        const s = [stateRec({ seq: 1 })]
        const r = evaluateCorpus(d, s)
        expect(r.source.skippedUnsequencedDecisionCount).toBe(1)
        expect(r.source.contextsBuilt).toBe(0)
    })

    it("5. missing raw state degrades safely (state unavailable, issue recorded)", () => {
        const d = [decRec({ seq: 1, training: "SPEED", candidates: fullContest("SPEED") })]
        const r = evaluateCorpus(d, [stateRec({ seq: 2 })]) // no state for seq 1
        // seq1 decision has a state file supplied but no matching state -> JOINED, missing-state issue.
        expect(r.issues.some((i) => i.type === "replayCareerMissingRawState" && i.seq === 1)).toBe(true)
        // Complete contest still lets the advisor TRAIN (training does not need state).
        expect(r.rows.find((x) => x.seq === 1)?.advisor.action).toBe("TRAIN")
    })

    it("7. a duplicate decision seq is detected and excluded, not arbitrarily selected", () => {
        const d = [decRec({ seq: 1, training: "SPEED", candidates: fullContest("SPEED") }), decRec({ seq: 1, training: "WIT", candidates: fullContest("WIT") })]
        const r = evaluateCorpus(d, [stateRec({ seq: 1 })])
        expect(r.issues.some((i) => i.type === "duplicateDecisionSeq" && i.seq === 1)).toBe(true)
        expect(r.rows.some((x) => x.seq === 1)).toBe(false) // excluded
        expect(r.source.duplicateSkippedContextCount).toBeGreaterThan(0)
    })

    it("8. a duplicate state seq is detected and excluded", () => {
        const d = [decRec({ seq: 1, training: "SPEED", candidates: fullContest("SPEED") })]
        const s = [stateRec({ seq: 1 }), stateRec({ seq: 1, energy: 5 })]
        const r = evaluateCorpus(d, s)
        expect(r.issues.some((i) => i.type === "duplicateStateSeq" && i.seq === 1)).toBe(true)
        expect(r.rows.some((x) => x.seq === 1)).toBe(false)
    })
})

// ---- Core counts + ratios + taxonomy ----

describe("core counts, ratios, taxonomy", () => {
    const [d, s] = standardCorpus()
    const r = evaluateCorpus(d, s)

    it("9. recommendation status counts partition contexts", () => {
        const sc = r.summary.statusCounts
        expect(sc.recommendationAvailable + sc.insufficientEvidence + sc.notApplicable + sc.unsupportedDecisionContext).toBe(r.source.contextsBuilt)
        expect(r.source.contextsBuilt).toBe(6)
        expect(sc).toEqual({ recommendationAvailable: 4, insufficientEvidence: 1, notApplicable: 1, unsupportedDecisionContext: 0 })
    })

    it("10. advisor action counts sum to recommendationAvailable", () => {
        const a = r.summary.advisorActionCounts
        expect(a.TRAIN + a.REST + a.RECOVER_MOOD).toBe(r.summary.statusCounts.recommendationAvailable)
        expect(a).toEqual({ TRAIN: 3, REST: 1, RECOVER_MOOD: 0 })
    })

    it("11. comparison counts partition contexts exactly", () => {
        const c = r.summary.comparisonCounts
        expect(c.sameAction + c.sameActionDifferentTraining + c.differentAction + c.advisorUnavailable + c.comparisonNotApplicable).toBe(r.source.contextsBuilt)
        expect(c).toEqual({ sameAction: 1, sameActionDifferentTraining: 1, differentAction: 1, advisorUnavailable: 2, comparisonNotApplicable: 1 })
    })

    it("12/13/14. comparableCount excludes advisorUnavailable + comparisonNotApplicable", () => {
        expect(r.summary.comparableCount).toBe(3) // 1 + 1 + 1
    })

    it("15/16. availability + coverage rates use contextsBuilt denominator", () => {
        expect(r.summary.recommendationAvailabilityRate).toEqual({ numerator: 4, denominator: 6, value: 4 / 6 })
        expect(r.summary.comparisonCoverageRate).toEqual({ numerator: 3, denominator: 6, value: 0.5 })
    })

    it("17/18/19. agreement + disagreement rates use comparableCount denominator", () => {
        expect(r.summary.exactAgreementRate).toEqual({ numerator: 1, denominator: 3, value: 1 / 3 })
        expect(r.summary.actionFamilyAgreementRate).toEqual({ numerator: 2, denominator: 3, value: 2 / 3 })
        expect(r.summary.disagreementRate).toEqual({ numerator: 2, denominator: 3, value: 2 / 3 })
    })

    it("21/22/23. candidate vs action-family disagreement stay distinct", () => {
        expect(r.summary.candidateDisagreementCount).toBe(1) // sameActionDifferentTraining
        expect(r.summary.actionFamilyDisagreementCount).toBe(1) // differentAction
        expect(r.summary.totalDisagreementCount).toBe(2)
    })

    it("29/31. comparisonNotApplicable segmented by committed action; RACE not in advisor matrix", () => {
        expect(r.comparisonNotApplicableByCommittedAction).toEqual({ RACE: 1 })
        expect(Object.keys(r.actionMatrix)).toEqual(["TRAIN"]) // only comparable committed families
        expect(r.actionMatrix.TRAIN).toEqual({ REST: 1, TRAIN: 2 }) // sorted keys
    })

    it("36. reason-code counts are correct and sorted", () => {
        expect(r.reasonCodeCounts).toEqual({ energyBelowAdvisorThreshold: 1, incompleteTrainingContest: 1, trainingScoreHigher: 3 })
    })
})

it("20. a zero denominator yields value null, not 0", () => {
    // A corpus with no comparable rows: one RACE turn with an available advisor rec.
    const d = [decRec({ seq: 1, action: "RACE", candidates: fullContest("SPEED") })]
    const r = evaluateCorpus(d, [stateRec({ seq: 1 })])
    expect(r.summary.comparableCount).toBe(0)
    expect(r.summary.exactAgreementRate.value).toBeNull()
    expect(r.summary.disagreementRate.value).toBeNull()
})

// ---- Segmentation ----

describe("segmentation", () => {
    it("24/25. scenario segmentation + deterministic UNAVAILABLE bucket", () => {
        const d = [decRec({ seq: 1, training: "SPEED", candidates: fullContest("SPEED") }), decRec({ seq: 2, training: "SPEED", candidates: fullContest("SPEED") })]
        const s = [stateRec({ seq: 1, scenarioType: "grandconcert" }), stateRec({ seq: 2, scenarioType: null })]
        const r = evaluateCorpus(d, s)
        expect(r.scenarioSegments.map((x) => x.scenarioType)).toEqual(["UNAVAILABLE", "grandconcert"]) // sorted
        expect(r.scenarioSegments.find((x) => x.scenarioType === "grandconcert")?.contextCount).toBe(1)
    })

    it("26/27/28. trainingSource segmentation uses metadata; raw forced key + UNAVAILABLE bucket", () => {
        const [d, s] = standardCorpus()
        const r = evaluateCorpus(d, s)
        const keys = r.trainingSourceSegments.map((x) => x.trainingSource)
        expect(keys).toEqual(["ANALYSIS", "FORCED_FROM_SKIPPED"]) // sorted raw keys; RACE (seq4) excluded (not TRAIN)
        expect(r.trainingSourceSegments.find((x) => x.trainingSource === "ANALYSIS")?.botTrainDecisionCount).toBe(3) // seq 1,2,6
        expect(r.trainingSourceSegments.find((x) => x.trainingSource === "FORCED_FROM_SKIPPED")?.botTrainDecisionCount).toBe(1) // seq 3
    })

    it("27b. a bot TRAIN decision with no trainingSource lands in the UNAVAILABLE bucket", () => {
        const d = [{ type: "decision_trace", v: 1, ts: 1, careerToken: "T|A", scenario: "Trackblazer", seq: 1, turn: 10, observation: { turnObserved: true }, candidates: fullContest("SPEED"), selected: { action: "TRAIN", training: "SPEED" } }]
        const r = evaluateCorpus(d, [stateRec({ seq: 1 })])
        expect(r.trainingSourceSegments.map((x) => x.trainingSource)).toEqual(["UNAVAILABLE"])
    })
})

// ---- Margin ----

describe("margin statistics", () => {
    it("32. count/min/max/mean/median computed", () => {
        const [d, s] = standardCorpus()
        const m = evaluateCorpus(d, s).marginStats
        expect(m.count).toBe(3) // seq 1,2,4 are TRAIN recs with a margin
        expect(m.min).not.toBeNull()
        expect(m.max).not.toBeNull()
    })

    it("33. negative margins are preserved (higher-total alternative excluded by risk)", () => {
        const contest = [trainCand("SPEED", { spd: 150 }, 55, false), trainCand("STAMINA", { sta: 30 }, 10, true), trainCand("POWER", { pwr: 10 }, 10, false), trainCand("GUTS", { grt: 8 }, 10, false), trainCand("WIT", { wit: 6 }, 10, false)]
        const r = evaluateCorpus([decRec({ seq: 1, training: "STAMINA", candidates: contest })], [stateRec({ seq: 1 })])
        expect(r.marginStats.min).toBeLessThan(0)
    })

    it("34. a zero margin is preserved", () => {
        const contest = [trainCand("SPEED", { spd: 20 }, 10, true), trainCand("STAMINA", { sta: 20 }, 10, false), trainCand("POWER", { pwr: 5 }, 10, false), trainCand("GUTS", { grt: 5 }, 10, false), trainCand("WIT", { wit: 5 }, 10, false)]
        const r = evaluateCorpus([decRec({ seq: 1, training: "SPEED", candidates: contest })], [stateRec({ seq: 1 })])
        expect(r.marginStats.min).toBe(0)
    })

    it("35. no margins yields null stats", () => {
        // Only a REST recommendation (low energy), which carries no scoreMargin.
        const r = evaluateCorpus([decRec({ seq: 1, action: "REST" })], [stateRec({ seq: 1, energy: 10 })])
        expect(r.marginStats).toEqual({ count: 0, min: null, max: null, mean: null, median: null, p25: null, p75: null })
    })
})

// ---- Determinism ----

describe("determinism", () => {
    it("38/39. repeated evaluation deep-equal + JSON byte-identical", () => {
        const [d, s] = standardCorpus()
        expect(evaluateCorpus(d, s)).toEqual(evaluateCorpus(d, s))
        expect(stableStringify(evaluateCorpus(d, s))).toBe(stableStringify(evaluateCorpus(d, s)))
    })

    it("40/41/42. shuffled raw decision + state input order produces identical JSON", () => {
        const [d, s] = standardCorpus()
        const base = stableStringify(evaluateCorpus(d, s))
        const dShuf = [d[3], d[0], d[5], d[1], d[4], d[2]]
        const sShuf = [s[5], s[2], s[0], s[4], s[1], s[3]]
        expect(stableStringify(evaluateCorpus(dShuf, sShuf))).toBe(base)
    })
})

// ---- No leakage / claims ----

describe("no leakage and no forbidden claims", () => {
    it("43. the evaluator never places a committed action into the advisor recommendation", () => {
        // seq committed RACE, but the advisor rec (from context) recommends TRAIN independently.
        const r = evaluateCorpus([decRec({ seq: 1, action: "RACE", candidates: fullContest("SPEED") })], [stateRec({ seq: 1 })])
        const row = r.rows[0]
        expect(row.committed.action).toBe("RACE")
        expect(row.advisor.action).toBe("TRAIN") // advisor is unaffected by the committed RACE
        expect(row.comparison).toBe("comparisonNotApplicable")
    })

    it("44. output contains no enteredRace / transition / finalize evidence", () => {
        const [d, s] = standardCorpus()
        const json = stableStringify(evaluateCorpus(d, s))
        for (const forbidden of ["enteredRace", "transition", "finalize", "raceExecution"]) expect(json).not.toContain(forbidden)
    })

    it("45. text/JSON output contains no causal/optimality/accuracy vocabulary", () => {
        const [d, s] = standardCorpus()
        const json = stableStringify(evaluateCorpus(d, s)).toLowerCase()
        for (const banned of ["accuracy", "better", "worse", "mistake", "would have", "optimal", "success rate", "win rate"]) expect(json).not.toContain(banned)
    })
})

// ---- CLI ----

describe("CLI", () => {
    const SCRIPT = join(process.cwd(), "scripts/shadow-advisor.mjs")
    let dir: string
    let tracePath: string
    let statePath: string

    beforeAll(() => {
        dir = mkdtempSync(join(tmpdir(), "s2cli-"))
        const [d, s] = standardCorpus()
        tracePath = join(dir, "decisions.jsonl")
        statePath = join(dir, "career_state.jsonl")
        writeFileSync(tracePath, d.map((r) => JSON.stringify(r)).join("\n") + "\n")
        writeFileSync(statePath, s.map((r) => JSON.stringify(r)).join("\n") + "\n")
    })
    afterAll(() => rmSync(dir, { recursive: true, force: true }))

    function run(args: string[]): { code: number; stdout: string } {
        try {
            const stdout = execFileSync(process.execPath, [SCRIPT, ...args], { encoding: "utf8" })
            return { code: 0, stdout }
        } catch (e) {
            const err = e as { status?: number; stdout?: string }
            return { code: err.status ?? 1, stdout: err.stdout ?? "" }
        }
    }

    it("46. text smoke prints coverage + comparison sections", () => {
        const { code, stdout } = run(["--trace", tracePath, "--state", statePath])
        expect(code).toBe(0)
        expect(stdout).toContain("Shadow Advisor S2 evaluation")
        expect(stdout).toContain("comparable = sameAction")
    })

    it("47. --json parses and carries schema/version + partition invariant", () => {
        const { stdout } = run(["--trace", tracePath, "--state", statePath, "--json"])
        const parsed = JSON.parse(stdout)
        expect(parsed.evaluationVersion).toBe(EVALUATION_VERSION)
        expect(parsed.policyId).toBe("raw-gain-ranker-v1")
        const c = parsed.summary.comparisonCounts
        expect(c.sameAction + c.sameActionDifferentTraining + c.differentAction + c.advisorUnavailable + c.comparisonNotApplicable).toBe(parsed.source.contextsBuilt)
    })

    it("48. --details prints per-turn rows", () => {
        const { stdout } = run(["--trace", tracePath, "--state", statePath, "--details"])
        expect(stdout).toContain("## per-turn")
        expect(stdout).toContain("seq 1")
    })

    it("49. --career-token exact filter evaluates only the matching career", () => {
        const { stdout } = run(["--trace", tracePath, "--state", statePath, "--career-token", "T|A", "--json"])
        const parsed = JSON.parse(stdout)
        expect(parsed.source.careerTokenFilter).toBe("T|A")
        expect(parsed.rows.every((r: { careerToken: string }) => r.careerToken === "T|A")).toBe(true)
    })

    it("50. malformed JSONL exits nonzero", () => {
        const bad = join(dir, "bad.jsonl")
        writeFileSync(bad, "{not json\n")
        const { code } = run(["--trace", bad, "--state", statePath])
        expect(code).toBe(2)
    })

    it("51. missing required args exits nonzero", () => {
        expect(run(["--trace", tracePath]).code).toBe(2)
        expect(run([]).code).toBe(2)
    })

    it("determinism: two CLI --json runs are byte-identical", () => {
        const a = run(["--trace", tracePath, "--state", statePath, "--json"]).stdout
        const b = run(["--trace", tracePath, "--state", statePath, "--json"]).stdout
        expect(a).toBe(b)
    })

    it("boundary: S2 source files import no RaceLab / Android / decisionAnalysis", () => {
        const files = ["evaluate.ts", "report.ts"].map((f) => readFileSync(join(process.cwd(), "src/lib/shadowAdvisor", f), "utf8"))
        const imports = files.flatMap((src) => [...src.matchAll(/^\s*import\b[^\n]*?from\s+"([^"]+)"/gm)].map((m) => m[1]))
        for (const p of imports) {
            expect(p.toLowerCase()).not.toContain("racelab")
            expect(p.toLowerCase()).not.toContain("android")
            expect(p).not.toContain("decisionAnalysis")
        }
    })
})
