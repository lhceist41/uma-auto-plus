// Shadow Advisor S1 - shared type contract. Pure, offline, deterministic. This file has NO ReplayLab,
// RaceLab, Android, or runtime imports: it is the policy-facing contract, and the S1 policy is a pure
// function of an AdvisorDecisionContext. The context type is deliberately shaped so that current-policy
// data (candidate utility score, committed action, selected training) and future/post-decision data
// (transitions, enteredRace, finalize, later seqs) CANNOT be expressed in it - anti-leakage by type.

/** Advisor schema version. Bumped only on a breaking context/recommendation shape change. Not the app version. */
export const ADVISOR_VERSION = "1"

/** Canonical training facility identity tokens (match the runtime MainScreen training set). */
export const ADVISOR_FACILITIES = ["SPEED", "STAMINA", "POWER", "GUTS", "WIT"] as const
export type AdvisorFacility = (typeof ADVISOR_FACILITIES)[number]

/** Canonical per-stat gain keys as serialized in the trace `gains` block (spd/sta/pwr/grt/wit; 5 core stats). */
export const ADVISOR_GAIN_KEYS = ["spd", "sta", "pwr", "grt", "wit"] as const
export type AdvisorGainKey = (typeof ADVISOR_GAIN_KEYS)[number]

/** Canonical mood tokens in ascending order (AWFUL worst -> GREAT best), matching the runtime Mood enum. */
export const ADVISOR_MOOD_ORDER = ["AWFUL", "BAD", "NORMAL", "GOOD", "GREAT"] as const
export type AdvisorMood = (typeof ADVISOR_MOOD_ORDER)[number]

/** One training candidate's factual inputs. Raw stat gains and failChance ONLY - never a current-policy score. */
export interface AdvisorFacilityFact {
    readonly id: string
    /** Raw per-stat gains (spd/sta/pwr/grt/wit -> integer), or null when the gains block was unavailable. */
    readonly gains: Readonly<Record<string, number>> | null
    /** Raw failure chance percentage (e.g. 18), or null when unavailable. */
    readonly failChance: number | null
}

/**
 * The immutable single-turn factual input to the advisor policy. It contains ONLY pre-decision facts and
 * cannot, by type, carry: the bot's committed action, its selected training, any candidate utility score,
 * enteredRace, observed transitions, finalize results, or any later seq. If a fact is unavailable it is
 * null - never a fabricated default.
 */
export interface AdvisorDecisionContext {
    readonly careerToken: string
    readonly seq: number
    readonly turn: number | null
    readonly scenarioType: string | null
    readonly state: {
        readonly energy: number | null
        readonly mood: string | null
        readonly negativeStatuses: readonly string[] | null
        readonly stats: Readonly<Record<string, number>> | null
        readonly skillPts: number | null
        readonly raceFlags: { readonly mandatory: boolean; readonly scheduled: boolean; readonly goalRibbon: boolean } | null
    }
    readonly trainingContest: {
        readonly complete: boolean
        readonly facilities: readonly AdvisorFacilityFact[]
    } | null
    /**
     * OPTIONAL factual marker that this turn is dominated by a scenario mechanic S1 does not model (Grand
     * Concert lesson, Trackblazer item, Unity Cup showdown). S1's context.ts never sets it: it cannot prove
     * a hidden mechanic from pre-decision state alone and prefers a limitation note. The field exists so the
     * policy has a defined, tested branch and a later stage can populate it from an explicit factual marker.
     * It is a factual label, never a committed action.
     */
    readonly unsupportedScenarioMechanic?: string | null
}

/** Recommendation status. Coverage is never maximized by guessing: an unsupported/insufficient turn says so. */
export type RecommendationStatus = "recommendationAvailable" | "insufficientEvidence" | "notApplicable" | "unsupportedDecisionContext"

/** The only actions S1 recommends. Race identity and scenario mechanics are out of S1 scope. */
export type AdvisorAction = "TRAIN" | "REST" | "RECOVER_MOOD"

/** Structured factual reason codes. Detail strings may carry real numeric inputs; never causal/optimality claims. */
export type ShadowReasonCode =
    | "trainingScoreHigher"
    | "trainingAlternativeExcludedByFailureRisk"
    | "failureRiskLower"
    | "failureRiskAboveThreshold"
    | "energyBelowAdvisorThreshold"
    | "moodBelowAdvisorFloor"
    | "raceDayForced"
    | "incompleteTrainingContest"
    | "stateUnavailable"
    | "scenarioMechanicUnsupported"

export interface ShadowReason {
    readonly code: ShadowReasonCode
    readonly detail: string
}

/** A heuristic score margin between the pick and its best alternative. NOT a confidence or a probability. */
export interface ScoreMargin {
    readonly value: number
    readonly over: string
}

/** Enough decomposition to reproduce the advisor score. Never includes the current-policy utility score. */
export interface ScoreBreakdown {
    readonly weightedGain: number
    readonly failurePenalty: number
    readonly total: number
    readonly perStat: Readonly<Record<string, number>>
}

/** The advisor's factual recommendation for one turn. Deterministic for identical (context, config). */
export interface ShadowRecommendation {
    readonly advisorVersion: string
    readonly policyId: string
    readonly careerToken: string
    readonly seq: number
    readonly turn: number | null
    readonly status: RecommendationStatus
    readonly recommended?: { readonly action: AdvisorAction; readonly trainingType?: string }
    readonly scoreMargin?: ScoreMargin
    readonly reasons: readonly ShadowReason[]
    readonly limitations: readonly string[]
    readonly scoreBreakdown?: ScoreBreakdown
}

/** Static, advisor-owned policy configuration. No value is derived from bot settings or tuned to outcomes. */
export interface ShadowPolicyConfig {
    readonly advisorVersion: string
    readonly policyId: string
    /** Per-stat weight applied to raw gains (keys spd/sta/pwr/grt/wit). Advisor-owned, not from bot config. */
    readonly statGainWeights: Readonly<Record<string, number>>
    /** failChance percentage above which a candidate is excluded from a normal pick. */
    readonly failChanceHardLimit: number
    /** Multiplier turning failChance into a subtracted penalty (deterministic, not an expected-value claim). */
    readonly failChancePenaltyCoefficient: number
    /** Energy below this triggers a REST recommendation (advisor-owned threshold). */
    readonly restEnergyThreshold: number
    /** Recover mood when the mood rank is strictly below this floor token's rank. */
    readonly recoverMoodFloor: string
    /** Fixed canonical order used as the final training tie-break. */
    readonly trainingTieBreakOrder: readonly string[]
    /** When every complete candidate exceeds the hard limit: if true, pick the least-risk one; else refuse. */
    readonly allowOverLimitLeastRisk: boolean
}

/** How the advisor's recommendation relates to the bot's actually-committed decision (comparison layer only). */
export type ComparisonState = "sameAction" | "sameActionDifferentTraining" | "differentAction" | "advisorUnavailable" | "comparisonNotApplicable"

/** Factual comparison output. trainingSource is preserved metadata for later segmentation, never a policy input. */
export interface ComparisonResult {
    readonly state: ComparisonState
    readonly advisorAction: AdvisorAction | null
    readonly advisorTraining: string | null
    readonly committedAction: string | null
    readonly committedTraining: string | null
    readonly trainingSource: string | null
}
