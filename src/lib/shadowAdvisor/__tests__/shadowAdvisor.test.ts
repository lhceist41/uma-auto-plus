import { readFileSync } from "node:fs"
import { join } from "node:path"
import process from "node:process"
import { recommend, DEFAULT_SHADOW_POLICY } from "../policy.ts"
import { buildAdvisorContexts } from "../context.ts"
import type { AdvisorRawTurn } from "../context.ts"
import { compareToCommitted } from "../compare.ts"
import { ADVISOR_VERSION } from "../types.ts"
import type { AdvisorDecisionContext } from "../types.ts"
import { createReplayLab } from "../../replayLab.ts"
import type { ReplayDecision } from "../../replayLab.ts"

// ---- builders ----

type StateShape = AdvisorDecisionContext["state"]

function baseState(over: Partial<StateShape> = {}): StateShape {
    return { energy: 70, mood: "GOOD", negativeStatuses: [], stats: { spd: 300 }, skillPts: 100, raceFlags: { mandatory: false, scheduled: false, goalRibbon: false }, ...over }
}

function ctx(opts: {
    careerToken?: string
    seq?: number
    turn?: number | null
    scenarioType?: string | null
    state?: Partial<StateShape>
    trainingContest?: AdvisorDecisionContext["trainingContest"]
    unsupportedScenarioMechanic?: string | null
} = {}): AdvisorDecisionContext {
    return {
        careerToken: opts.careerToken ?? "T|X",
        seq: opts.seq ?? 1,
        turn: opts.turn ?? 10,
        scenarioType: opts.scenarioType ?? "ura",
        state: baseState(opts.state ?? {}),
        trainingContest: opts.trainingContest ?? null,
        unsupportedScenarioMechanic: opts.unsupportedScenarioMechanic,
    }
}

/** A complete five-facility contest where SPEED has the top raw gain and the lowest total risk. */
function goodContest(): AdvisorDecisionContext["trainingContest"] {
    return {
        complete: true,
        facilities: [
            { id: "SPEED", gains: { spd: 30 }, failChance: 10 },
            { id: "STAMINA", gains: { sta: 20 }, failChance: 10 },
            { id: "POWER", gains: { pwr: 15 }, failChance: 10 },
            { id: "GUTS", gains: { grt: 10 }, failChance: 10 },
            { id: "WIT", gains: { wit: 12 }, failChance: 10 },
        ],
    }
}

function committedDecision(over: { committedAction?: string | null; committedTraining?: string | null; trainingSource?: string | null }): ReplayDecision {
    return {
        committedAction: over.committedAction ?? null,
        committedTraining: over.committedTraining ?? null,
        trainingContest: { trainingSource: over.trainingSource ?? null },
    } as unknown as ReplayDecision
}

// ---- Context construction (JOINED ReplayLab fixture) ----

describe("context construction", () => {
    const rawTraining = [
        { type: "training", id: "SPEED", selected: true, rejected: false, reason: "won analysis (X) with score 41.20", gains: { spd: 30, sta: 4 }, failChance: 12, score: 41.2 },
        { type: "training", id: "STAMINA", selected: false, rejected: false, reason: "outscored", gains: { sta: 22 }, failChance: 9, score: 22.0 },
        { type: "training", id: "POWER", selected: false, rejected: false, reason: "outscored", gains: { pwr: 18 }, failChance: 15, score: 18.0 },
        { type: "training", id: "GUTS", selected: false, rejected: false, reason: "outscored", gains: { grt: 10 }, failChance: 8, score: 10.0 },
        { type: "training", id: "WIT", selected: false, rejected: false, reason: "outscored", gains: { wit: 14 }, failChance: 6, score: 14.0 },
    ]
    const stateObj = {
        type: "career_state",
        v: 1,
        ts: 1000,
        seq: 1,
        identity: { careerToken: "T|A", scenario: "Trackblazer", trainee: "Taiki", preset: "Taiki", fp: "fp1" },
        turn: 10,
        observation: { turnObserved: true },
        condition: { energy: 70, mood: "GOOD", negativeStatuses: [], positiveStatuses: [] },
        stats: { spd: 300, sta: 200, pwr: 250, grt: 180, wit: 220 },
        skillPts: 100,
        race: { mandatory: false, scheduled: false, goalRibbon: false },
        scenario: { type: "trackblazer", shopCoins: 40 },
    }
    const decisionObj = {
        type: "decision_trace",
        v: 1,
        ts: 1000,
        careerToken: "T|A",
        scenario: "Trackblazer",
        seq: 1,
        turn: 10,
        observation: { turnObserved: true },
        state: { energy: 70, mood: "GOOD", skillPts: 100, spd: 300 },
        candidates: rawTraining,
        selected: { action: "TRAIN", source: "action_choice", training: "SPEED", trainingSource: "ANALYSIS", trainingReason: "won analysis (X) with score 41.20" },
    }

    function joinedCareer() {
        const lab = createReplayLab()
        lab.ingestCareerStateLine(JSON.stringify(stateObj), 1)
        lab.noteCareerStateFile()
        lab.ingestDecisionLine(JSON.stringify(decisionObj), 1)
        return lab.finish().careers[0]
    }

    const rawBySeq = new Map<number, AdvisorRawTurn>([[1, { candidates: rawTraining, careerState: stateObj }]])

    it("1. builds one context from a sequenced JOINED decision", () => {
        const career = joinedCareer()
        expect(career.capability).toBe("JOINED")
        const contexts = buildAdvisorContexts(career, rawBySeq)
        expect(contexts).toHaveLength(1)
    })

    it("2. preserves careerToken + seq", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        expect(c.careerToken).toBe("T|A")
        expect(c.seq).toBe(1)
        expect(c.turn).toBe(10)
    })

    it("3. preserves state facts without fake defaults", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        expect(c.state.energy).toBe(70)
        expect(c.state.mood).toBe("GOOD")
        expect(c.state.raceFlags).toEqual({ mandatory: false, scheduled: false, goalRibbon: false })
        expect(c.state.stats).toEqual({ spd: 300, sta: 200, pwr: 250, grt: 180, wit: 220 })
        expect(c.scenarioType).toBe("trackblazer")
    })

    it("4. a complete training contest preserves all five facilities", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        expect(c.trainingContest?.complete).toBe(true)
        expect(c.trainingContest?.facilities.map((f) => f.id).sort()).toEqual(["GUTS", "POWER", "SPEED", "STAMINA", "WIT"])
    })

    it("5. preserves raw gains and failChance", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        const speed = c.trainingContest?.facilities.find((f) => f.id === "SPEED")
        expect(speed?.gains).toEqual({ spd: 30, sta: 4 })
        expect(speed?.failChance).toBe(12)
    })

    it("6. candidate.score is not present in the AdvisorDecisionContext", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        expect(JSON.stringify(c)).not.toContain("score")
        for (const f of c.trainingContest?.facilities ?? []) {
            expect((f as unknown as Record<string, unknown>).score).toBeUndefined()
        }
    })

    it("7. committed bot action / selected training is not present in the context", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        const json = JSON.stringify(c)
        expect(json).not.toContain("committedAction")
        expect(json).not.toContain("selected")
        expect(json).not.toContain("trainingSource")
    })

    it("8. enteredRace is not present in the context", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        expect(JSON.stringify(c)).not.toContain("enteredRace")
    })

    it("9. transition / finalize outcome data is not present in the context", () => {
        const json = JSON.stringify(buildAdvisorContexts(joinedCareer(), rawBySeq)[0])
        expect(json).not.toContain("transition")
        expect(json).not.toContain("finalize")
        expect(json).not.toContain("betweenDecision")
    })

    it("10. missing raw state stays unavailable (no fake defaults)", () => {
        const noState = new Map<number, AdvisorRawTurn>([[1, { candidates: rawTraining, careerState: null }]])
        const c = buildAdvisorContexts(joinedCareer(), noState)[0]
        expect(c.state.energy).toBeNull()
        expect(c.state.mood).toBeNull()
        expect(c.state.raceFlags).toBeNull()
        expect(c.state.negativeStatuses).toBeNull()
        expect(c.scenarioType).toBeNull()
    })

    // ---- Finding 3: career-token guard ----

    it("F3a. a raw career_state row with a mismatched careerToken is ignored (state null, no turn fallback)", () => {
        const foreign = { ...stateObj, identity: { ...stateObj.identity, careerToken: "T|OTHER" } }
        const map = new Map<number, AdvisorRawTurn>([[1, { candidates: rawTraining, careerState: foreign }]])
        const c = buildAdvisorContexts(joinedCareer(), map)[0]
        expect(c.state.energy).toBeNull()
        expect(c.state.mood).toBeNull()
        expect(c.state.raceFlags).toBeNull()
        expect(c.scenarioType).toBeNull()
    })

    it("F3b. a matching careerToken still projects state normally", () => {
        const c = buildAdvisorContexts(joinedCareer(), rawBySeq)[0]
        expect(c.state.energy).toBe(70)
        expect(c.state.raceFlags).not.toBeNull()
    })

    it("F3c. no cross-career state facts leak, and the foreign state cannot drive the recommendation", () => {
        // A foreign row with alarming low energy / negative status that WOULD trigger REST if trusted.
        const foreign = { ...stateObj, identity: { ...stateObj.identity, careerToken: "T|OTHER" }, condition: { energy: 5, mood: "AWFUL", negativeStatuses: ["Headache"], positiveStatuses: [] } }
        const map = new Map<number, AdvisorRawTurn>([[1, { candidates: rawTraining, careerState: foreign }]])
        const c = buildAdvisorContexts(joinedCareer(), map)[0]
        const json = JSON.stringify(c)
        expect(json).not.toContain("Headache")
        expect(json).not.toContain("AWFUL")
        // With the foreign state nulled out, the (present, complete) contest still yields TRAIN, not a REST
        // driven by the other career's energy 5.
        expect(recommend(c).recommended?.action).toBe("TRAIN")
    })
})

// ---- Training policy ----

describe("training policy", () => {
    it("11. a deterministic complete contest returns recommendationAvailable TRAIN", () => {
        const r = recommend(ctx({ trainingContest: goodContest() }))
        expect(r.status).toBe("recommendationAvailable")
        expect(r.recommended?.action).toBe("TRAIN")
        expect(r.advisorVersion).toBe(ADVISOR_VERSION)
        expect(r.policyId).toBe("raw-gain-ranker-v1")
    })

    it("12. the highest advisor raw-gain score wins under the baseline", () => {
        const r = recommend(ctx({ trainingContest: goodContest() }))
        expect(r.recommended?.trainingType).toBe("SPEED")
    })

    it("13. a current-bot candidate score cannot influence the recommendation (absent by type)", () => {
        // The context has no score field at all; two runs over the same facts are identical.
        const a = recommend(ctx({ trainingContest: goodContest() }))
        const b = recommend(ctx({ trainingContest: goodContest() }))
        expect(JSON.stringify(a)).toBe(JSON.stringify(b))
        expect(JSON.stringify(a)).not.toContain("\"score\"")
    })

    it("14. the failure penalty can change the ranking", () => {
        // POWER has the highest raw gain but a punishing failChance; WIT wins on total.
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "SPEED", gains: { spd: 10 }, failChance: 5 },
                { id: "STAMINA", gains: { sta: 10 }, failChance: 5 },
                { id: "POWER", gains: { pwr: 40 }, failChance: 38 },
                { id: "GUTS", gains: { grt: 10 }, failChance: 5 },
                { id: "WIT", gains: { wit: 30 }, failChance: 6 },
            ],
        }
        // POWER total = 40 - 19 = 21; WIT total = 30 - 3 = 27. WIT wins.
        expect(recommend(ctx({ trainingContest: contest })).recommended?.trainingType).toBe("WIT")
    })

    it("15. the hard failChance limit excludes an over-limit top candidate", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "SPEED", gains: { spd: 100 }, failChance: 50 }, // highest total but over the 40 limit
                { id: "STAMINA", gains: { sta: 20 }, failChance: 10 },
                { id: "POWER", gains: { pwr: 10 }, failChance: 10 },
                { id: "GUTS", gains: { grt: 10 }, failChance: 10 },
                { id: "WIT", gains: { wit: 12 }, failChance: 10 },
            ],
        }
        const r = recommend(ctx({ trainingContest: contest }))
        expect(r.recommended?.trainingType).toBe("STAMINA") // best under-limit
    })

    it("15b. all over limit: the least-risk facility wins even when it is NOT the highest total (risk-first)", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "SPEED", gains: { spd: 100 }, failChance: 60 }, // highest total (70) but highest risk
                { id: "STAMINA", gains: { sta: 20 }, failChance: 41 }, // lowest total but MINIMUM failChance
                { id: "POWER", gains: { pwr: 50 }, failChance: 50 },
                { id: "GUTS", gains: { grt: 40 }, failChance: 45 },
                { id: "WIT", gains: { wit: 60 }, failChance: 55 },
            ],
        }
        const r = recommend(ctx({ trainingContest: contest }))
        expect(r.status).toBe("recommendationAvailable")
        expect(r.recommended?.trainingType).toBe("STAMINA") // min failChance, not the highest total
        expect(r.reasons.some((x) => x.code === "failureRiskAboveThreshold")).toBe(true)
        expect(Math.min(...contest.facilities.map((f) => f.failChance as number))).toBe(41) // least-risk claim is true
    })

    it("15c. all over limit, tie on failChance: the higher advisor total wins", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "SPEED", gains: { spd: 60 }, failChance: 45 }, // total 37.5, min failChance
                { id: "STAMINA", gains: { sta: 30 }, failChance: 45 }, // total 7.5, min failChance
                { id: "POWER", gains: { pwr: 10 }, failChance: 50 },
                { id: "GUTS", gains: { grt: 10 }, failChance: 55 },
                { id: "WIT", gains: { wit: 10 }, failChance: 60 },
            ],
        }
        expect(recommend(ctx({ trainingContest: contest })).recommended?.trainingType).toBe("SPEED")
    })

    it("15d. all over limit, tie on failChance and total: fixed training order wins", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "STAMINA", gains: { sta: 60 }, failChance: 45 }, // total 37.5
                { id: "SPEED", gains: { spd: 60 }, failChance: 45 }, // total 37.5 -> SPEED first in canonical order
                { id: "POWER", gains: { pwr: 10 }, failChance: 50 },
                { id: "GUTS", gains: { grt: 10 }, failChance: 55 },
                { id: "WIT", gains: { wit: 10 }, failChance: 60 },
            ],
        }
        expect(recommend(ctx({ trainingContest: contest })).recommended?.trainingType).toBe("SPEED")
    })

    it("16. no RaceLab concept is involved; recommendation depends only on gains/failChance", () => {
        const r = recommend(ctx({ trainingContest: goodContest(), scenarioType: "trackblazer" }))
        expect(r.recommended?.trainingType).toBe("SPEED")
    })

    it("17. a tie on advisor score is broken by lower failChance", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "SPEED", gains: { spd: 30 }, failChance: 20 }, // total 20
                { id: "STAMINA", gains: { sta: 35 }, failChance: 30 }, // total 20, higher failChance
                { id: "POWER", gains: { pwr: 5 }, failChance: 10 },
                { id: "GUTS", gains: { grt: 5 }, failChance: 10 },
                { id: "WIT", gains: { wit: 5 }, failChance: 10 },
            ],
        }
        expect(recommend(ctx({ trainingContest: contest })).recommended?.trainingType).toBe("SPEED")
    })

    it("18. a tie on score and failChance is broken by fixed canonical order", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "STAMINA", gains: { sta: 30 }, failChance: 20 }, // total 20
                { id: "SPEED", gains: { spd: 30 }, failChance: 20 }, // total 20, same failChance -> SPEED first
                { id: "POWER", gains: { pwr: 5 }, failChance: 10 },
                { id: "GUTS", gains: { grt: 5 }, failChance: 10 },
                { id: "WIT", gains: { wit: 5 }, failChance: 10 },
            ],
        }
        expect(recommend(ctx({ trainingContest: contest })).recommended?.trainingType).toBe("SPEED")
    })

    it("19. repeated recommendation is deeply and byte-identically equal", () => {
        const c = ctx({ trainingContest: goodContest() })
        expect(recommend(c)).toEqual(recommend(c))
        expect(JSON.stringify(recommend(c))).toBe(JSON.stringify(recommend(c)))
    })
})

// ---- Failure-risk exclusion reason (finding 2) ----

describe("failure-risk exclusion reason", () => {
    // SPEED has the highest total but is over the hard limit and excluded; STAMINA (under-limit) wins with a
    // NEGATIVE margin over the excluded SPEED.
    const exclusionContest: AdvisorDecisionContext["trainingContest"] = {
        complete: true,
        facilities: [
            { id: "SPEED", gains: { spd: 150 }, failChance: 55 }, // over limit; total 122.5 (excluded)
            { id: "STAMINA", gains: { sta: 30 }, failChance: 10 }, // under limit; total 25 (winner)
            { id: "POWER", gains: { pwr: 10 }, failChance: 10 },
            { id: "GUTS", gains: { grt: 8 }, failChance: 10 },
            { id: "WIT", gains: { wit: 6 }, failChance: 10 },
        ],
    }

    it("selects the under-limit winner and does NOT label it trainingScoreHigher", () => {
        const r = recommend(ctx({ trainingContest: exclusionContest }))
        expect(r.recommended?.trainingType).toBe("STAMINA")
        expect(r.reasons.some((x) => x.code === "trainingScoreHigher")).toBe(false)
    })

    it("emits a distinct risk-exclusion reason exposing the excluded failChance and the advisor limit", () => {
        const excl = recommend(ctx({ trainingContest: exclusionContest })).reasons.find((x) => x.code === "trainingAlternativeExcludedByFailureRisk")
        expect(excl).toBeDefined()
        expect(excl?.detail).toContain("55") // excluded SPEED failChance
        expect(excl?.detail).toContain("40") // advisor hard limit
    })

    it("retains a negative scoreMargin as a factual disclosure (over the excluded candidate)", () => {
        const r = recommend(ctx({ trainingContest: exclusionContest }))
        expect(r.scoreMargin?.value).toBeLessThan(0)
        expect(r.scoreMargin?.over).toBe("SPEED")
    })

    it("a normal positive-margin winner still emits trainingScoreHigher", () => {
        const r = recommend(ctx({ trainingContest: goodContest() }))
        expect(r.reasons[0].code).toBe("trainingScoreHigher")
        expect(r.scoreMargin?.value ?? -1).toBeGreaterThanOrEqual(0)
    })
})

// ---- Recovery guardrail ----

describe("recovery guardrail", () => {
    it("20. low energy yields REST", () => {
        const r = recommend(ctx({ state: { energy: 25 }, trainingContest: goodContest() }))
        expect(r.recommended?.action).toBe("REST")
        expect(r.reasons[0].code).toBe("energyBelowAdvisorThreshold")
    })

    it("21. low mood yields RECOVER_MOOD", () => {
        const r = recommend(ctx({ state: { energy: 70, mood: "BAD" }, trainingContest: goodContest() }))
        expect(r.recommended?.action).toBe("RECOVER_MOOD")
        expect(r.reasons[0].code).toBe("moodBelowAdvisorFloor")
    })

    it("22. both low uses the documented precedence: energy before mood", () => {
        const r = recommend(ctx({ state: { energy: 20, mood: "AWFUL" }, trainingContest: goodContest() }))
        expect(r.recommended?.action).toBe("REST")
    })

    it("23. a mandatory race flag suppresses recovery -> notApplicable / raceDayForced", () => {
        const r = recommend(ctx({ state: { energy: 20, raceFlags: { mandatory: true, scheduled: false, goalRibbon: true } }, trainingContest: goodContest() }))
        expect(r.status).toBe("notApplicable")
        expect(r.reasons[0].code).toBe("raceDayForced")
    })

    it("24. a scheduled race flag suppresses recovery", () => {
        const r = recommend(ctx({ state: { energy: 20, raceFlags: { mandatory: false, scheduled: true, goalRibbon: false } } }))
        expect(r.status).toBe("notApplicable")
        expect(r.reasons[0].code).toBe("raceDayForced")
    })

    it("25. no contest and fully unavailable state -> insufficientEvidence", () => {
        const r = recommend(ctx({ state: { energy: null, mood: null, negativeStatuses: null, stats: null, skillPts: null, raceFlags: null }, trainingContest: null }))
        expect(r.status).toBe("insufficientEvidence")
        expect(r.reasons[0].code).toBe("stateUnavailable")
    })
})

// ---- Unsupported / insufficient ----

describe("unsupported and insufficient", () => {
    it("26. an incomplete training contest -> insufficientEvidence", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = { complete: false, facilities: [{ id: "SPEED", gains: { spd: 10 }, failChance: 5 }] }
        const r = recommend(ctx({ trainingContest: contest }))
        expect(r.status).toBe("insufficientEvidence")
        expect(r.reasons[0].code).toBe("incompleteTrainingContest")
    })

    it("27. no contest, state known, not race day, no recovery -> notApplicable", () => {
        const r = recommend(ctx({ state: { energy: 70, mood: "GOOD" }, trainingContest: null }))
        expect(r.status).toBe("notApplicable")
    })

    it("28. an explicit unsupported scenario mechanic marker -> unsupportedDecisionContext", () => {
        const r = recommend(ctx({ unsupportedScenarioMechanic: "grandConcertLesson", trainingContest: goodContest() }))
        expect(r.status).toBe("unsupportedDecisionContext")
        expect(r.reasons[0].code).toBe("scenarioMechanicUnsupported")
    })

    it("29. a complete contest with a facility missing gains/failChance -> insufficientEvidence", () => {
        const contest: AdvisorDecisionContext["trainingContest"] = {
            complete: true,
            facilities: [
                { id: "SPEED", gains: null, failChance: 10 },
                { id: "STAMINA", gains: { sta: 20 }, failChance: 10 },
                { id: "POWER", gains: { pwr: 15 }, failChance: 10 },
                { id: "GUTS", gains: { grt: 10 }, failChance: 10 },
                { id: "WIT", gains: { wit: 12 }, failChance: 10 },
            ],
        }
        const r = recommend(ctx({ trainingContest: contest }))
        expect(r.status).toBe("insufficientEvidence")
        expect(r.reasons[0].detail).toContain("gains")
    })
})

// ---- Recommendation semantics ----

describe("recommendation semantics", () => {
    it("30. scoreMargin is a heuristic margin, never a confidence/probability", () => {
        const r = recommend(ctx({ trainingContest: goodContest() }))
        expect(r.scoreMargin).toBeDefined()
        expect(typeof r.scoreMargin?.value).toBe("number")
        expect(r.scoreMargin?.over).toBe("STAMINA")
        expect(JSON.stringify(r).toLowerCase()).not.toContain("confidence")
        expect(JSON.stringify(r).toLowerCase()).not.toContain("probability")
    })

    it("31. scoreBreakdown reproduces the total", () => {
        const r = recommend(ctx({ trainingContest: goodContest() }))
        const b = r.scoreBreakdown!
        expect(b.weightedGain - b.failurePenalty).toBeCloseTo(b.total, 6)
        expect(b.total).toBeCloseTo(25, 6) // SPEED: 30*1 - 10*0.5
    })

    it("32. every training recommendation carries an offline limitation", () => {
        const r = recommend(ctx({ trainingContest: goodContest() }))
        expect(r.limitations.some((l) => l.includes("relationship"))).toBe(true)
    })

    it("33. reason ordering is deterministic (trainingScoreHigher first)", () => {
        const r = recommend(ctx({ trainingContest: goodContest() }))
        expect(r.reasons[0].code).toBe("trainingScoreHigher")
    })

    it("34. output strings contain no causal/optimality vocabulary", () => {
        const texts = [
            recommend(ctx({ trainingContest: goodContest() })),
            recommend(ctx({ state: { energy: 20 } })),
            recommend(ctx({ state: { energy: 70, mood: "AWFUL" } })),
            recommend(ctx({ state: { raceFlags: { mandatory: true, scheduled: false, goalRibbon: false } } })),
        ]
            .map((r) => JSON.stringify(r).toLowerCase())
            .join(" ")
        for (const banned of ["better", "optimal", "mistake", "should have", "would have", "would win"]) {
            expect(texts).not.toContain(banned)
        }
    })
})

// ---- Comparison ----

describe("comparison taxonomy", () => {
    const trainRec = recommend(ctx({ trainingContest: goodContest() })) // TRAIN SPEED

    it("35. same action + same training -> sameAction", () => {
        const r = compareToCommitted(trainRec, committedDecision({ committedAction: "TRAIN", committedTraining: "SPEED", trainingSource: "ANALYSIS" }))
        expect(r.state).toBe("sameAction")
    })

    it("36. both TRAIN but different training -> sameActionDifferentTraining", () => {
        const r = compareToCommitted(trainRec, committedDecision({ committedAction: "TRAIN", committedTraining: "STAMINA", trainingSource: "ANALYSIS" }))
        expect(r.state).toBe("sameActionDifferentTraining")
    })

    it("37. bot TRAIN, advisor REST -> differentAction", () => {
        const restRec = recommend(ctx({ state: { energy: 20 } }))
        const r = compareToCommitted(restRec, committedDecision({ committedAction: "TRAIN", committedTraining: "SPEED" }))
        expect(r.state).toBe("differentAction")
    })

    it("38. an unavailable advisor recommendation -> advisorUnavailable", () => {
        const insufficient = recommend(ctx({ trainingContest: { complete: false, facilities: [] } }))
        const r = compareToCommitted(insufficient, committedDecision({ committedAction: "TRAIN", committedTraining: "SPEED" }))
        expect(r.state).toBe("advisorUnavailable")
    })

    it("39. a RACE-committed turn -> comparisonNotApplicable", () => {
        const r = compareToCommitted(trainRec, committedDecision({ committedAction: "RACE" }))
        expect(r.state).toBe("comparisonNotApplicable")
    })

    it("40. trainingSource is copied into the comparison metadata", () => {
        const r = compareToCommitted(trainRec, committedDecision({ committedAction: "TRAIN", committedTraining: "SPEED", trainingSource: "FORCED_FROM_SKIPPED" }))
        expect(r.trainingSource).toBe("FORCED_FROM_SKIPPED")
    })

    it("41. comparison never mutates the recommendation object", () => {
        const before = JSON.stringify(trainRec)
        compareToCommitted(trainRec, committedDecision({ committedAction: "RACE" }))
        expect(JSON.stringify(trainRec)).toBe(before)
    })
})

// ---- Source-boundary / dependency ----

describe("source boundary", () => {
    const DIR = join(process.cwd(), "src/lib/shadowAdvisor")
    const srcByName = new Map(["types.ts", "context.ts", "policy.ts", "compare.ts"].map((f) => [f, readFileSync(join(DIR, f), "utf8")]))
    // Import-anchored: only real `import ... from "path"` statements count (never a quoted phrase in prose).
    const IMPORT_RE = /^\s*import\b[^\n]*?from\s+"([^"]+)"/gm
    const importsOf = (src: string): string[] => [...src.matchAll(IMPORT_RE)].map((m) => m[1])
    const allImports = [...srcByName.values()].flatMap(importsOf)

    it("42. no Android/Kotlin/runtime imports (only local + ReplayLab types)", () => {
        for (const path of allImports) {
            expect(path.toLowerCase()).not.toContain("android")
            expect(path).not.toContain(".kt")
            expect(path.toLowerCase()).not.toContain("gesture")
            // Every import is either a local S1 file or the allowed ReplayLab type module.
            expect(path.startsWith("./") || path === "../replayLab.ts").toBe(true)
        }
    })

    it("43. no RaceLab dependency", () => {
        expect(allImports.some((i) => i.toLowerCase().includes("racelab"))).toBe(false)
    })

    it("44. no current-policy score helper dependency", () => {
        // The advisor never imports decisionAnalysis (the score/reason parser); policy imports only its types.
        expect(allImports.some((i) => i.includes("decisionAnalysis"))).toBe(false)
        expect([...new Set(importsOf(srcByName.get("policy.ts") as string))]).toEqual(["./types.ts"])
    })
})
