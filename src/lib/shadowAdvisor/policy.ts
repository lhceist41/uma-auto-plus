// Shadow Advisor S1 - the pure policy `raw-gain-ranker-v1`. A deliberately simple, inspectable baseline:
// it ranks a complete training contest by weighted RAW stat gains minus a failure penalty, and applies a
// state-based recovery guardrail. It is NOT tuned against final outcomes and reuses NONE of the current
// bot's scoring: no candidate utility score, no bot thresholds, no bot tie-break, no selected reason.
//
// `recommend` is a pure function of one immutable AdvisorDecisionContext plus static config. It touches no
// filesystem, network, clock, randomness, ReplayLab array, or process-global state. Repeated calls with the
// same (context, config) produce a deeply-equal, byte-identical-serializing result.

import type {
    AdvisorDecisionContext,
    ShadowRecommendation,
    ShadowPolicyConfig,
    ShadowReason,
    ScoreBreakdown,
} from "./types.ts"
import { ADVISOR_VERSION, ADVISOR_FACILITIES, ADVISOR_GAIN_KEYS, ADVISOR_MOOD_ORDER } from "./types.ts"

/** The single S1 baseline policy. Frozen so a caller cannot mutate the shared config. */
export const DEFAULT_SHADOW_POLICY: ShadowPolicyConfig = Object.freeze({
    advisorVersion: ADVISOR_VERSION,
    policyId: "raw-gain-ranker-v1",
    // Equal weight across the five core stat gains: the trace `gains` block carries only these five and no
    // skill-point component, and S1 intentionally does not pretend to know an optimal stat build.
    statGainWeights: Object.freeze({ spd: 1, sta: 1, pwr: 1, grt: 1, wit: 1 }),
    failChanceHardLimit: 40,
    failChancePenaltyCoefficient: 0.5,
    restEnergyThreshold: 30,
    recoverMoodFloor: "NORMAL",
    trainingTieBreakOrder: Object.freeze([...ADVISOR_FACILITIES]),
    allowOverLimitLeastRisk: true,
})

/**
 * Stable offline evidence omissions carried on every training recommendation. These are factual disclosures
 * (the advisor scored raw gains only), not apologies. Deterministic order.
 */
const OFFLINE_TRAINING_LIMITATIONS: readonly string[] = Object.freeze([
    "relationship, rainbow, and hint facts are unavailable offline",
    "support-card presence and bonuses are unavailable offline",
    "advisor scores raw stat gains only; the current-policy utility score is intentionally excluded",
])

/** Rank of a mood token (AWFUL=0 .. GREAT=4), or null when the token is absent/unrecognized. */
function moodRank(mood: string | null): number | null {
    if (mood === null) return null
    const i = (ADVISOR_MOOD_ORDER as readonly string[]).indexOf(mood)
    return i < 0 ? null : i
}

interface ScoredFacility {
    id: string
    weightedGain: number
    failurePenalty: number
    total: number
    failChance: number
    overLimit: boolean
    perStat: Record<string, number>
}

function scoreFacility(id: string, gains: Readonly<Record<string, number>>, failChance: number, config: ShadowPolicyConfig): ScoredFacility {
    const perStat: Record<string, number> = {}
    let weightedGain = 0
    for (const key of ADVISOR_GAIN_KEYS) {
        const gain = gains[key]
        if (typeof gain !== "number" || !Number.isFinite(gain)) continue
        const contribution = gain * (config.statGainWeights[key] ?? 0)
        perStat[key] = contribution
        weightedGain += contribution
    }
    const failurePenalty = failChance * config.failChancePenaltyCoefficient
    return { id, weightedGain, failurePenalty, total: weightedGain - failurePenalty, failChance, overLimit: failChance > config.failChanceHardLimit, perStat }
}

function tieBreakIndex(id: string, tieBreak: readonly string[]): number {
    const i = tieBreak.indexOf(id)
    return i < 0 ? Number.MAX_SAFE_INTEGER : i
}

/** Normal under-limit order: advisor total desc, then lower failChance, then fixed canonical tie-break order. */
function compareScored(a: ScoredFacility, b: ScoredFacility, tieBreak: readonly string[]): number {
    if (a.total !== b.total) return b.total - a.total
    if (a.failChance !== b.failChance) return a.failChance - b.failChance
    return tieBreakIndex(a.id, tieBreak) - tieBreakIndex(b.id, tieBreak)
}

/**
 * Risk-first order for the all-over-limit fallback: lowest failChance first, then higher advisor total, then
 * fixed canonical tie-break order. The winner here is genuinely the least-risk eligible facility, so the
 * emitted "least-risk" reason is factually true.
 */
function compareLeastRisk(a: ScoredFacility, b: ScoredFacility, tieBreak: readonly string[]): number {
    if (a.failChance !== b.failChance) return a.failChance - b.failChance
    if (a.total !== b.total) return b.total - a.total
    return tieBreakIndex(a.id, tieBreak) - tieBreakIndex(b.id, tieBreak)
}

/**
 * The pure S1 policy. Evaluation order: explicit unsupported-mechanic marker, then forced race-day
 * suppression, then the state recovery guardrail (energy before mood), then training ranking, then the
 * no-contest classification. Every branch returns an explicit status; nothing is guessed.
 */
export function recommend(context: AdvisorDecisionContext, config: ShadowPolicyConfig = DEFAULT_SHADOW_POLICY): ShadowRecommendation {
    const base = { advisorVersion: config.advisorVersion, policyId: config.policyId, careerToken: context.careerToken, seq: context.seq, turn: context.turn } as const

    // 1. An explicit factual scenario-mechanic marker means S1 does not model this turn.
    const marker = context.unsupportedScenarioMechanic
    if (typeof marker === "string" && marker.length > 0) {
        return { ...base, status: "unsupportedDecisionContext", reasons: [{ code: "scenarioMechanicUnsupported", detail: `scenario mechanic ${marker} is not modeled by S1` }], limitations: [] }
    }

    // 2. A forced race day (mandatory or scheduled) suppresses training/recovery advice entirely.
    const raceFlags = context.state.raceFlags
    if (raceFlags !== null && (raceFlags.mandatory || raceFlags.scheduled)) {
        const which = raceFlags.mandatory ? "mandatory" : "scheduled"
        return { ...base, status: "notApplicable", reasons: [{ code: "raceDayForced", detail: `${which} race day; training/recovery comparison is not applicable` }], limitations: [] }
    }

    // 3. Recovery guardrail on factual state. Energy takes precedence over mood: low energy raises failure
    //    risk on every training and blocks gains, a harder constraint than a mood multiplier.
    const energy = context.state.energy
    if (energy !== null && energy < config.restEnergyThreshold) {
        return { ...base, status: "recommendationAvailable", recommended: { action: "REST" }, reasons: [{ code: "energyBelowAdvisorThreshold", detail: `energy ${energy} below advisor REST threshold ${config.restEnergyThreshold}` }], limitations: OFFLINE_TRAINING_LIMITATIONS }
    }
    const mRank = moodRank(context.state.mood)
    const floorRank = moodRank(config.recoverMoodFloor)
    if (mRank !== null && floorRank !== null && mRank < floorRank) {
        return { ...base, status: "recommendationAvailable", recommended: { action: "RECOVER_MOOD" }, reasons: [{ code: "moodBelowAdvisorFloor", detail: `mood ${context.state.mood} below advisor floor ${config.recoverMoodFloor}` }], limitations: OFFLINE_TRAINING_LIMITATIONS }
    }

    // 4. Training ranking over a proven complete contest.
    const tc = context.trainingContest
    if (tc !== null) {
        if (!tc.complete) {
            return { ...base, status: "insufficientEvidence", reasons: [{ code: "incompleteTrainingContest", detail: `training contest incomplete (${tc.facilities.length} facility candidate(s), five required)` }], limitations: [] }
        }
        const scored: ScoredFacility[] = []
        for (const f of tc.facilities) {
            if (f.gains === null || f.failChance === null) {
                const missing = f.gains === null ? "gains" : "failChance"
                return { ...base, status: "insufficientEvidence", reasons: [{ code: "incompleteTrainingContest", detail: `facility ${f.id} is missing ${missing}` }], limitations: [] }
            }
            scored.push(scoreFacility(f.id, f.gains, f.failChance, config))
        }
        return rankTraining(base, scored, config)
    }

    // 5. No training contest and no recovery trigger. Distinguish "state unavailable" from "domain N/A".
    if (raceFlags === null) {
        return { ...base, status: "insufficientEvidence", reasons: [{ code: "stateUnavailable", detail: "no training contest and pre-decision state unavailable" }], limitations: [] }
    }
    return { ...base, status: "notApplicable", reasons: [], limitations: [] }
}

/** Ranks a fully-scored complete contest and builds the TRAIN recommendation (or refuses per config). */
function rankTraining(
    base: { advisorVersion: string; policyId: string; careerToken: string; seq: number; turn: number | null },
    scored: ScoredFacility[],
    config: ShadowPolicyConfig,
): ShadowRecommendation {
    const underLimit = scored.filter((s) => !s.overLimit)
    const allOverLimit = underLimit.length === 0
    if (allOverLimit && !config.allowOverLimitLeastRisk) {
        return { ...base, status: "insufficientEvidence", reasons: [{ code: "failureRiskAboveThreshold", detail: `every candidate exceeds the failChance limit ${config.failChanceHardLimit}; no supported training pick` }], limitations: [] }
    }
    // Under-limit picks rank by total (compareScored); the all-over-limit fallback ranks risk-first
    // (compareLeastRisk) so the winner is truly the least-risk facility.
    const pool = allOverLimit ? scored : underLimit
    const comparator = allOverLimit ? compareLeastRisk : compareScored
    const winner = [...pool].sort((a, b) => comparator(a, b, config.trainingTieBreakOrder))[0]

    // Best alternative for the margin: the highest-total facility other than the winner (always defined for
    // a five-facility contest). The margin is negative exactly when a higher-total candidate was excluded by
    // the hard failChance limit - a factual disclosure, reported with a distinct reason (never "score higher").
    const secondBest = [...scored].filter((s) => s.id !== winner.id).sort((a, b) => compareScored(a, b, config.trainingTieBreakOrder))[0] ?? winner
    const margin = winner.total - secondBest.total

    const reasons: ShadowReason[] = []
    if (allOverLimit) {
        reasons.push({ code: "failureRiskAboveThreshold", detail: `all candidates exceed the failChance limit ${config.failChanceHardLimit}; selected least-risk ${winner.id} at failChance ${winner.failChance}` })
    } else if (margin >= 0) {
        // The winner also has the higher (or equal) advisor score over the best alternative.
        reasons.push({ code: "trainingScoreHigher", detail: `${winner.id} advisor score ${round1(winner.total)} exceeded ${secondBest.id} ${round1(secondBest.total)} by ${round1(margin)}` })
    } else {
        // A higher-scoring alternative existed but was excluded by the hard failChance limit; the winner is
        // NOT higher-scoring, so it is never labelled trainingScoreHigher.
        reasons.push({ code: "trainingAlternativeExcludedByFailureRisk", detail: `${secondBest.id} advisor score ${round1(secondBest.total)} exceeded ${winner.id} ${round1(winner.total)} but its failChance ${secondBest.failChance} exceeded advisor limit ${config.failChanceHardLimit}` })
    }
    if (winner.failChance < secondBest.failChance) {
        reasons.push({ code: "failureRiskLower", detail: `${winner.id} failChance ${winner.failChance} below ${secondBest.id} ${secondBest.failChance}` })
    }

    const scoreBreakdown: ScoreBreakdown = { weightedGain: winner.weightedGain, failurePenalty: winner.failurePenalty, total: winner.total, perStat: winner.perStat }
    return {
        ...base,
        status: "recommendationAvailable",
        recommended: { action: "TRAIN", trainingType: winner.id },
        scoreMargin: { value: winner.total - secondBest.total, over: secondBest.id },
        reasons,
        limitations: OFFLINE_TRAINING_LIMITATIONS,
        scoreBreakdown,
    }
}

/** Deterministic one-decimal rounding for stable reason strings (no locale, no float drift in output). */
function round1(n: number): number {
    return Math.round(n * 10) / 10
}
