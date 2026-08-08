import { createReplayLab, renderReplayReport, parseSelectedTrainingScore, REPLAY_SCHEMA, REPLAY_SCHEMA_VERSION } from "../replayLab.ts"
import type { ReplayLabOptions, ReplayResult } from "../replayLab.ts"

// ---- Fixture builders (real-shape records; every test assembles from these) ----

const TOKEN = "Taiki Shuttle|Trackblazer|run1|abc123"

interface TrainingCand {
    id: string
    selected?: boolean
    rejected?: boolean
    reason?: string
    score?: number
}

/** A complete five-facility ANALYSIS contest where `winner` is selected at `winnerScore` and others carry scores. */
function trainingContest(winner: string, winnerScore: number, alts: Record<string, number>, source = "ANALYSIS"): { candidates: unknown[]; selected: Record<string, unknown> } {
    const candidates: unknown[] = [{ type: "training", id: winner, selected: true, reason: `won analysis (Stat Efficiency) with score ${winnerScore.toFixed(2)}` }]
    for (const [id, score] of Object.entries(alts)) {
        candidates.push({ type: "training", id, selected: false, rejected: false, reason: "outscored", score })
    }
    return { candidates, selected: { action: "TRAIN", source: "action_choice", training: winner, trainingSource: source, trainingReason: `won analysis (Stat Efficiency) with score ${winnerScore.toFixed(2)}` } }
}

function decisionLine(over: Record<string, unknown> = {}): string {
    const base: Record<string, unknown> = {
        type: "decision_trace",
        v: 1,
        ts: 1000,
        app: "1.3.8",
        fp: "fp1",
        scenario: "Trackblazer",
        trainee: "Taiki Shuttle",
        preset: "Taiki Shuttle",
        careerToken: TOKEN,
        turn: 10,
        observation: { turnObserved: true },
        state: { energy: 50, mood: "GOOD", skillPts: 100, spd: 300 },
        candidates: [{ type: "action", id: "TRAIN", selected: true, reason: "default" }],
        selected: { action: "TRAIN", source: "action_choice", reason: "default" },
    }
    return JSON.stringify({ ...base, ...over })
}

function stateLine(over: Record<string, unknown> = {}): string {
    const base: Record<string, unknown> = {
        type: "career_state",
        v: 1,
        ts: 1000,
        seq: 1,
        identity: { careerToken: TOKEN, scenario: "Trackblazer", trainee: "Taiki Shuttle", preset: "Taiki Shuttle", fp: "fp1" },
        turn: 10,
        observation: { turnObserved: true },
        condition: { energy: 50, mood: "GOOD" },
        stats: { spd: 300, sta: 200, pwr: 250, grt: 180, wit: 220 },
        skillPts: 100,
        race: { mandatory: false, scheduled: false, goalRibbon: false },
        scenario: { type: "trackblazer", shopCoins: 40, consecutiveRaceCount: 0, megaphoneTurnCounter: 0 },
        provenance: { identityInputs: "configured", derivedIdentity: "derived", date: "observed", condition: "observed", stats: "observed", aptitudes: "unread", race: "observed", scenario: "observed" },
    }
    // Merge identity carefully so a caller can override token/scenario/fp without dropping the rest.
    const merged = { ...base, ...over }
    if (over.identity) merged.identity = { ...(base.identity as object), ...(over.identity as object) }
    return JSON.stringify(merged)
}

function finalizeLine(over: Record<string, unknown> = {}): string {
    const base: Record<string, unknown> = {
        type: "career_finalize",
        ts: 9999,
        careerToken: TOKEN,
        finalizationDecision: "FINISH",
        sessionOutcome: "committed",
        verifiedRemainingSp: 20,
        scanComplete: true,
        plannerComplete: true,
        confirmationComplete: true,
        policy: "trigger-v4",
        objective: "rank",
        finalizationReason: "done",
    }
    return JSON.stringify({ ...base, ...over })
}

function run(decisions: string[], states: string[] = [], finalizes: string[] = [], options: ReplayLabOptions = {}, stateFileSupplied = states.length > 0): ReplayResult {
    const lab = createReplayLab(options)
    states.forEach((l, i) => lab.ingestCareerStateLine(l, i + 1))
    if (stateFileSupplied) lab.noteCareerStateFile()
    finalizes.forEach((l, i) => lab.ingestFinalizeLine(l, i + 1))
    decisions.forEach((l, i) => lab.ingestDecisionLine(l, i + 1))
    return lab.finish()
}

// ---- parseSelectedTrainingScore ----

describe("parseSelectedTrainingScore", () => {
    it("reads the trailing number, even when the mode contains its own parentheses", () => {
        expect(parseSelectedTrainingScore("won analysis (Stat Efficiency (Year 2+)) with score 3101.16")).toBe(3101.16)
        expect(parseSelectedTrainingScore("won analysis (X) with score 0.00")).toBe(0)
    })
    it("returns null for an unknown score or a non-matching reason", () => {
        expect(parseSelectedTrainingScore("won analysis (X) with score ?")).toBeNull()
        expect(parseSelectedTrainingScore("outscored")).toBeNull()
        expect(parseSelectedTrainingScore(null)).toBeNull()
    })
})

// ---- TRACE_ONLY ----

describe("TRACE_ONLY career", () => {
    const decisions = [decisionLine({ seq: undefined, ts: 1 }), decisionLine({ seq: undefined, ts: 2, turn: 11 })]

    it("classifies a seq-less career as TRACE_ONLY and synthesizes no seq", () => {
        const r = run(decisions)
        expect(r.careers).toHaveLength(1)
        const c = r.careers[0]
        expect(c.capability).toBe("TRACE_ONLY")
        expect(c.decisions.every((d) => d.seq === null)).toBe(true)
        expect(c.transitions).toHaveLength(0)
        expect(c.sequencedDecisionCount).toBe(0)
    })

    it("keeps candidates inspectable and preserves source order", () => {
        const r = run(decisions)
        const c = r.careers[0]
        expect(c.decisions[0].ts).toBe(1)
        expect(c.decisions[1].ts).toBe(2)
        expect(c.decisions[0].actionContest?.selectedAction).toBe("TRAIN")
    })

    it("does not require a career_state file and never builds transitions without one", () => {
        const r = run([decisionLine({ seq: 1 })], [], [], {}, false) // state file NOT supplied
        expect(r.careers[0].capability).toBe("TRACE_ONLY")
        expect(r.exitCode).toBe(0)
    })
})

// ---- JOINED ----

describe("JOINED career", () => {
    const decisions = [decisionLine({ seq: 1, turn: 10 }), decisionLine({ seq: 2, turn: 11 }), decisionLine({ seq: 3, turn: 12 })]
    const states = [stateLine({ seq: 1, condition: { energy: 50, mood: "GOOD" } }), stateLine({ seq: 2, condition: { energy: 40, mood: "GREAT" } }), stateLine({ seq: 3, condition: { energy: 60, mood: "GREAT" } })]

    it("joins state and trace by token+seq and reports JOINED", () => {
        const r = run(decisions, states)
        const c = r.careers[0]
        expect(c.capability).toBe("JOINED")
        expect(c.joinedCount).toBe(3)
        expect(c.stateWithoutTraceCount).toBe(0)
        expect(c.traceWithoutStateCount).toBe(0)
        expect(c.decisions.map((d) => d.seq)).toEqual([1, 2, 3])
        expect(c.decisions.every((d) => d.hasStateJoin)).toBe(true)
    })

    it("orders the timeline by seq even when the decisions arrive out of order", () => {
        const shuffled = [decisionLine({ seq: 3, turn: 12 }), decisionLine({ seq: 1, turn: 10 }), decisionLine({ seq: 2, turn: 11 })]
        const c = run(shuffled, states).careers[0]
        expect(c.decisions.map((d) => d.seq)).toEqual([1, 2, 3])
    })

    it("builds one betweenDecisionObservedTransition per consecutive available state, labelled non-causally", () => {
        const c = run(decisions, states).careers[0]
        expect(c.transitions).toHaveLength(2)
        expect(c.transitions.every((t) => t.label === "betweenDecisionObservedTransition")).toBe(true)
        const t = c.transitions[0]
        expect(t.fromSeq).toBe(1)
        expect(t.toSeq).toBe(2)
        expect(t.consecutive).toBe(true)
        expect(t.spansGap).toBe(false)
        // energy 50 -> 40 is an observed numeric diff, never an "effect" of the action.
        const energy = t.diffs.find((d) => d.field === "energy")
        expect(energy).toEqual({ field: "energy", kind: "numeric", from: 50, to: 40, delta: -10 })
        expect(t.chosenActionAtFromSeq).toBe("TRAIN")
    })
})

// ---- Missing state / missing trace ----

describe("partial coverage", () => {
    it("a sequenced decision with no career_state stays factual and raises traceWithoutState", () => {
        const r = run([decisionLine({ seq: 1 }), decisionLine({ seq: 2 })], [stateLine({ seq: 1 })])
        const c = r.careers[0]
        expect(c.capability).toBe("JOINED")
        expect(c.decisions.find((d) => d.seq === 2)?.hasStateJoin).toBe(false)
        expect(c.anomalies.some((a) => a.type === "traceWithoutState" && a.seq === 2)).toBe(true)
        expect(r.exitCode).toBe(1) // warning, not a hard failure
    })

    it("a career_state with no matching decision stays factual and raises stateWithoutTrace", () => {
        const r = run([decisionLine({ seq: 1 })], [stateLine({ seq: 1 }), stateLine({ seq: 2 })])
        const c = r.careers[0]
        expect(c.stateWithoutTraceCount).toBe(1)
        expect(c.anomalies.some((a) => a.type === "stateWithoutTrace" && a.seq === 2)).toBe(true)
        expect(c.decisions).toHaveLength(1) // no fabricated decision
    })
})

// ---- Duplicate keys ----

describe("duplicate composite keys", () => {
    it("reports a duplicate (token, seq) among states as a consistency failure (exit 3)", () => {
        const r = run([decisionLine({ seq: 1 })], [stateLine({ seq: 1 }), stateLine({ seq: 1 })])
        const c = r.careers[0]
        expect(c.anomalies.some((a) => a.type === "duplicateTokenSeq" && a.seq === 1)).toBe(true)
        expect(r.exitCode).toBe(3)
    })

    it("reports a duplicate (token, seq) among sequenced traces as a consistency failure (exit 3)", () => {
        const r = run([decisionLine({ seq: 1 }), decisionLine({ seq: 1 })], [stateLine({ seq: 1 })])
        const c = r.careers[0]
        expect(c.anomalies.some((a) => a.type === "duplicateTokenSeq" && a.seq === 1)).toBe(true)
        expect(r.exitCode).toBe(3)
    })
})

describe("sequenced career with a state file but no matching states", () => {
    it("stays JOINED so the traceWithoutState coverage gap is surfaced, not silently downgraded", () => {
        // A career_state file is supplied (covering some other token), but none of it matches this career.
        const r = run([decisionLine({ seq: 1 }), decisionLine({ seq: 2 })], [stateLine({ seq: 1, identity: { careerToken: "OTHER" } })])
        const c = r.careers.find((x) => x.careerToken === TOKEN)!
        expect(c.capability).toBe("JOINED")
        expect(c.joinedCount).toBe(0)
        expect(c.anomalies.filter((a) => a.type === "traceWithoutState")).toHaveLength(2)
        expect(r.exitCode).toBe(1)
    })
})

// ---- Finalize join ----

describe("finalize join", () => {
    it("joins a career_finalize by token and exposes only structural fields", () => {
        const c = run([decisionLine({ seq: 1 })], [stateLine({ seq: 1 })], [finalizeLine()]).careers[0]
        expect(c.finalize.present).toBe(true)
        expect(c.finalize.finalizationDecision).toBe("FINISH")
        expect(c.finalize.sessionOutcome).toBe("committed")
        expect(c.finalize.verifiedRemainingSp).toBe(20)
    })

    it("a career without a finalize remains valid (a pre-resume segment legitimately has none)", () => {
        const c = run([decisionLine({ seq: 1 })], [stateLine({ seq: 1 })], []).careers[0]
        expect(c.finalize.present).toBe(false)
        expect(run([decisionLine({ seq: 1 })], [stateLine({ seq: 1 })], []).exitCode).toBe(0)
    })

    it("skips non-finalize rows in a mixed careers corpus without error", () => {
        const mixed = [JSON.stringify({ type: "spark_choice", careerToken: TOKEN }), finalizeLine()]
        const r = run([decisionLine({ seq: 1 })], [stateLine({ seq: 1 })], mixed)
        expect(r.careers[0].finalize.present).toBe(true)
        expect(r.failures).toHaveLength(0)
    })
})

// ---- Training contest classification ----

describe("training contest", () => {
    const tc = trainingContest("GUTS", 3101.16, { SPEED: 208.88, STAMINA: 28.98, POWER: 103.21, WIT: 40.88 })

    it("recognizes a complete five-facility contest, identifies the selection, preserves runner-up scores", () => {
        const c = run([decisionLine({ seq: 1, ...tc })], [stateLine({ seq: 1 })]).careers[0]
        const contest = c.decisions[0].trainingContest
        expect(contest.present).toBe(true)
        expect(contest.complete).toBe(true)
        expect(contest.selected?.id).toBe("GUTS")
        expect(contest.selected?.score).toBe(3101.16)
        expect(contest.selected?.scoreSource).toBe("reasonParsed")
        const speed = contest.candidates.find((x) => x.id === "SPEED")
        expect(speed?.score).toBe(208.88)
        expect(speed?.scoreSource).toBe("structured")
    })

    it("suppresses the selected score and flags an anomaly when the reason has no parseable score", () => {
        const bad = trainingContest("GUTS", 0, { SPEED: 10, STAMINA: 5, POWER: 3, WIT: 2 })
        // Corrupt the winner reason so the score is unrecoverable.
        ;(bad.candidates[0] as Record<string, unknown>).reason = "won analysis (X) with score ?"
        ;(bad.selected as Record<string, unknown>).trainingReason = "won analysis (X) with score ?"
        const c = run([decisionLine({ seq: 1, ...bad })], [stateLine({ seq: 1 })]).careers[0]
        expect(c.decisions[0].trainingContest.selected?.scoreSource).toBe("unavailable")
        expect(c.anomalies.some((a) => a.type === "unparseableSelectedTrainingScore")).toBe(true)
    })

    it("marks an incomplete contest (fewer than five facilities) and suppresses the gap", () => {
        const partial = trainingContest("SPEED", 100, { STAMINA: 50 }) // only 2 facilities
        const c = run([decisionLine({ seq: 1, ...partial })], [stateLine({ seq: 1 })]).careers[0]
        expect(c.decisions[0].trainingContest.complete).toBe(false)
        expect(c.decisions[0].trainingContest.recordedScoreGap.eligible).toBe(false)
        expect(c.anomalies.some((a) => a.type === "incompleteTrainingContest")).toBe(true)
    })
})

// ---- recordedScoreGap ----

describe("recordedScoreGap", () => {
    it("is eligible and equals the winning margin for a score-dominant ANALYSIS pick", () => {
        const tc = trainingContest("GUTS", 3101.16, { SPEED: 208.88, STAMINA: 28.98, POWER: 103.21, WIT: 40.88 })
        const g = run([decisionLine({ seq: 1, ...tc })], [stateLine({ seq: 1 })]).careers[0].decisions[0].trainingContest.recordedScoreGap
        expect(g.eligible).toBe(true)
        expect(g.value).toBeCloseTo(3101.16 - 208.88, 5)
        expect(g.bestAlternative).toEqual({ id: "SPEED", score: 208.88 })
    })

    it("is suppressed for a forced/hint selection source, never reported as a gap", () => {
        const tc = trainingContest("SPEED", 100, { POWER: 200, STAMINA: 5, GUTS: 3, WIT: 2 }, "FORCED_FROM_SKIPPED")
        const g = run([decisionLine({ seq: 1, ...tc })], [stateLine({ seq: 1 })]).careers[0].decisions[0].trainingContest.recordedScoreGap
        expect(g.eligible).toBe(false)
        expect(g.suppressed).toBe(true)
        expect(g.value).toBeNull()
    })

    it("is suppressed when the selection is ANALYSIS-labelled but not score-dominant (the SPEED-0 vs POWER-71.85 case)", () => {
        const tc = trainingContest("SPEED", 0, { STAMINA: 36.27, POWER: 71.85, GUTS: 49.32, WIT: 26.88 })
        const g = run([decisionLine({ seq: 1, ...tc })], [stateLine({ seq: 1 })]).careers[0].decisions[0].trainingContest.recordedScoreGap
        expect(g.eligible).toBe(false)
        expect(g.suppressed).toBe(true)
        expect(g.value).toBeNull()
        expect(g.bestAlternative).toEqual({ id: "POWER", score: 71.85 })
    })

    it("is never produced for an action-only / race decision (no training contest)", () => {
        const race = decisionLine({ seq: 1, candidates: [{ type: "action", id: "RACE", selected: true, reason: "race day" }], selected: { action: "RACE", source: "action_choice", reason: "race day" }, raceEligibility: { eligible: true, reason: "race day" } })
        const c = run([race], [stateLine({ seq: 1 })]).careers[0]
        expect(c.decisions[0].trainingContest.present).toBe(false)
        expect(c.decisions[0].trainingContest.recordedScoreGap.eligible).toBe(false)
    })
})

// ---- Transition semantics: gaps ----

describe("transition semantics with a sequence gap", () => {
    it("spans a gap when an intermediate state is unavailable and never infers the missing record", () => {
        const decisions = [decisionLine({ seq: 1 }), decisionLine({ seq: 3 })]
        const states = [stateLine({ seq: 1, condition: { energy: 50, mood: "GOOD" } }), stateLine({ seq: 3, condition: { energy: 30, mood: "GOOD" } })]
        const c = run(decisions, states).careers[0]
        expect(c.transitions).toHaveLength(1)
        const t = c.transitions[0]
        expect(t.fromSeq).toBe(1)
        expect(t.toSeq).toBe(3)
        expect(t.consecutive).toBe(false)
        expect(t.spansGap).toBe(true)
        expect(t.seqGap).toBe(2)
        expect(c.anomalies.some((a) => a.type === "seqGap")).toBe(true)
    })

    it("reports a numeric field's availability change (unread) rather than a fake delta from missing to default", () => {
        const decisions = [decisionLine({ seq: 1 }), decisionLine({ seq: 2 })]
        // seq 2 state omits stats entirely (unread), so stats.spd is an availability change, not 300 -> 0.
        const states = [stateLine({ seq: 1 }), stateLine({ seq: 2, stats: undefined })]
        const t = run(decisions, states).careers[0].transitions[0]
        const spd = t.diffs.find((d) => d.field === "stats.spd")
        expect(spd?.kind).toBe("availability")
        expect(spd?.delta).toBeUndefined()
    })
})

// ---- Cohorts ----

describe("cohort labelling", () => {
    it("keeps careers with different fingerprints distinct and never claims policy-code identity", () => {
        const a = decisionLine({ seq: undefined, careerToken: "T|A", fp: "fpA" })
        const b = decisionLine({ seq: undefined, careerToken: "T|B", fp: "fpB" })
        const r = run([a, b])
        expect(r.careers).toHaveLength(2)
        expect(new Set(r.careers.map((c) => c.cohort.fp))).toEqual(new Set(["fpA", "fpB"]))
        const text = renderReplayReport(r)
        expect(text).toContain("cohort label only; not a policy-code identity")
    })
})

// ---- Filters ----

describe("filters", () => {
    it("careerToken filter keeps state and trace in step (no artificial stateWithoutTrace)", () => {
        const decisions = [decisionLine({ seq: 1, careerToken: "T|keep" }), decisionLine({ seq: 1, careerToken: "T|drop" })]
        const states = [stateLine({ seq: 1, identity: { careerToken: "T|keep" } }), stateLine({ seq: 1, identity: { careerToken: "T|drop" } })]
        const r = run(decisions, states, [], { careerToken: "T|keep" })
        expect(r.careers).toHaveLength(1)
        expect(r.careers[0].careerToken).toBe("T|keep")
        expect(r.careers[0].stateWithoutTraceCount).toBe(0)
    })

    it("action filter selects whole careers that committed the action (state/trace never split)", () => {
        const trainCareer = decisionLine({ seq: undefined, careerToken: "T|train", selected: { action: "TRAIN", source: "action_choice" } })
        const raceCareer = decisionLine({ seq: undefined, careerToken: "T|race", selected: { action: "RACE", source: "action_choice" } })
        const r = run([trainCareer, raceCareer], [], [], { action: "RACE" })
        expect(r.careers.map((c) => c.careerToken)).toEqual(["T|race"])
    })
})

// ---- Malformed input ----

describe("malformed input", () => {
    it("reports a decisions parse error as a parse/schema failure (exit 2), never silently ignored", () => {
        const r = run(["{not json", decisionLine({ seq: 1 })], [stateLine({ seq: 1 })])
        expect(r.failures.some((f) => f.stream === "decisions")).toBe(true)
        expect(r.exitCode).toBe(2)
    })

    it("reports a malformed career_state envelope as a parse/schema failure", () => {
        const r = run([decisionLine({ seq: 1 })], [JSON.stringify({ type: "career_state", v: 1, ts: 1, identity: {}, seq: 1 })])
        expect(r.failures.some((f) => f.stream === "careerState")).toBe(true)
        expect(r.exitCode).toBe(2)
    })
})

// ---- Determinism + read-only ----

describe("determinism and output shape", () => {
    const decisions = [decisionLine({ seq: 1 }), decisionLine({ seq: 2 })]
    const states = [stateLine({ seq: 1 }), stateLine({ seq: 2, condition: { energy: 40, mood: "GREAT" } })]

    it("repeated JSON export is byte-identical for identical inputs", () => {
        const a = JSON.stringify(run(decisions, states, [finalizeLine()]))
        const b = JSON.stringify(run(decisions, states, [finalizeLine()]))
        expect(a).toBe(b)
    })

    it("does not mutate the input line strings", () => {
        const lines = [decisionLine({ seq: 1 })]
        const before = lines[0]
        run(lines, states)
        expect(lines[0]).toBe(before)
    })

    it("carries the ReplayLab schema and version on the result", () => {
        const r = run(decisions, states)
        expect(r.schema).toBe(REPLAY_SCHEMA)
        expect(r.version).toBe(REPLAY_SCHEMA_VERSION)
    })

    it("the text report never emits the word regret or a causal-effect label", () => {
        const tc = trainingContest("GUTS", 3101.16, { SPEED: 208.88, STAMINA: 28.98, POWER: 103.21, WIT: 40.88 })
        const text = renderReplayReport(run([decisionLine({ seq: 1, ...tc })], states)).toLowerCase()
        expect(text).not.toContain("regret")
        expect(text).not.toContain("action effect")
        expect(text).not.toContain("reward")
    })
})
