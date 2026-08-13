package com.steve1316.uma_android_automation.bot.shadowadvisor

/*
 * Kotlin port of the Shadow Advisor S1 type contract (src/lib/shadowAdvisor/types.ts). Observational only:
 * these types describe what the advisor would recommend from immutable PRE-DECISION facts, and by shape cannot
 * carry the bot's committed action, its selected training, any candidate utility score, enteredRace, observed
 * transitions, finalize results, or a later seq. Field-for-field parity with the TypeScript authority is pinned
 * by checked-in golden fixtures consumed by both Jest and JUnit.
 */

/** Advisor schema version. Mirrors `ADVISOR_VERSION` in types.ts. Not the app version. */
const val ADVISOR_VERSION: String = "1"

/** Canonical training facility identity tokens (match the runtime MainScreen training set). */
val ADVISOR_FACILITIES: List<String> = listOf("SPEED", "STAMINA", "POWER", "GUTS", "WIT")

/** Canonical per-stat gain keys as serialized in the trace `gains` block (5 core stats; note `grt`, not `guts`). */
val ADVISOR_GAIN_KEYS: List<String> = listOf("spd", "sta", "pwr", "grt", "wit")

/** Canonical mood tokens in ascending order (AWFUL worst -> GREAT best), matching the runtime Mood enum. */
val ADVISOR_MOOD_ORDER: List<String> = listOf("AWFUL", "BAD", "NORMAL", "GOOD", "GREAT")

/** One training candidate's factual inputs. Raw stat gains and failChance ONLY - never a current-policy score. */
data class AdvisorFacilityFact(
    val id: String,
    /** Raw per-stat gains (spd/sta/pwr/grt/wit -> number), or null when the gains block was unavailable. */
    val gains: Map<String, Double>?,
    /** Raw failure chance percentage (e.g. 18), or null when unavailable. */
    val failChance: Double?,
)

/** Pre-decision factual state the advisor reads. Absent fields stay null, never defaulted. */
data class AdvisorState(
    val energy: Double?,
    val mood: String?,
    val negativeStatuses: List<String>?,
    val stats: Map<String, Double>?,
    val skillPts: Double?,
    val raceFlags: AdvisorRaceFlags?,
)

data class AdvisorRaceFlags(val mandatory: Boolean, val scheduled: Boolean, val goalRibbon: Boolean)

data class AdvisorTrainingContest(val complete: Boolean, val facilities: List<AdvisorFacilityFact>)

/**
 * The immutable single-turn factual input to the advisor policy. Contains ONLY pre-decision facts. If a fact is
 * unavailable it is null - never a fabricated default. [unsupportedScenarioMechanic] is a factual label the
 * offline S1 context never sets (it cannot prove a hidden mechanic from pre-decision state alone); it exists so
 * the policy has a defined, tested branch.
 */
data class AdvisorDecisionContext(
    val careerToken: String,
    val seq: Int,
    val turn: Int?,
    val scenarioType: String?,
    val state: AdvisorState,
    val trainingContest: AdvisorTrainingContest?,
    val unsupportedScenarioMechanic: String? = null,
)

/** Recommendation status. Coverage is never maximized by guessing. */
enum class RecommendationStatus(val wire: String) {
    RECOMMENDATION_AVAILABLE("recommendationAvailable"),
    INSUFFICIENT_EVIDENCE("insufficientEvidence"),
    NOT_APPLICABLE("notApplicable"),
    UNSUPPORTED_DECISION_CONTEXT("unsupportedDecisionContext"),
}

/** The only actions S1 recommends. Race identity and scenario mechanics are out of S1 scope. */
enum class AdvisorAction(val wire: String) {
    TRAIN("TRAIN"),
    REST("REST"),
    RECOVER_MOOD("RECOVER_MOOD"),
}

/** Structured factual reason codes. Detail strings may carry real numeric inputs; never causal/optimality claims. */
enum class ShadowReasonCode(val wire: String) {
    TRAINING_SCORE_HIGHER("trainingScoreHigher"),
    TRAINING_ALTERNATIVE_EXCLUDED_BY_FAILURE_RISK("trainingAlternativeExcludedByFailureRisk"),
    FAILURE_RISK_LOWER("failureRiskLower"),
    FAILURE_RISK_ABOVE_THRESHOLD("failureRiskAboveThreshold"),
    ENERGY_BELOW_ADVISOR_THRESHOLD("energyBelowAdvisorThreshold"),
    MOOD_BELOW_ADVISOR_FLOOR("moodBelowAdvisorFloor"),
    RACE_DAY_FORCED("raceDayForced"),
    INCOMPLETE_TRAINING_CONTEST("incompleteTrainingContest"),
    STATE_UNAVAILABLE("stateUnavailable"),
    SCENARIO_MECHANIC_UNSUPPORTED("scenarioMechanicUnsupported"),
}

data class ShadowReason(val code: ShadowReasonCode, val detail: String)

/** A heuristic score margin between the pick and its best alternative. NOT a confidence or a probability. */
data class ScoreMargin(val value: Double, val over: String)

/** Enough decomposition to reproduce the advisor score. Never includes the current-policy utility score. */
data class ScoreBreakdown(
    val weightedGain: Double,
    val failurePenalty: Double,
    val total: Double,
    val perStat: Map<String, Double>,
)

/** The advisor's factual recommendation for one turn. Deterministic for identical (context, config). */
data class ShadowRecommendation(
    val advisorVersion: String,
    val policyId: String,
    val careerToken: String,
    val seq: Int,
    val turn: Int?,
    val status: RecommendationStatus,
    val recommendedAction: AdvisorAction? = null,
    val recommendedTrainingType: String? = null,
    val scoreMargin: ScoreMargin? = null,
    val reasons: List<ShadowReason>,
    val limitations: List<String>,
    val scoreBreakdown: ScoreBreakdown? = null,
)

/** Static, advisor-owned policy configuration. No value is derived from bot settings or tuned to outcomes. */
data class ShadowPolicyConfig(
    val advisorVersion: String,
    val policyId: String,
    /** Per-stat weight applied to raw gains (keys spd/sta/pwr/grt/wit). Advisor-owned, not from bot config. */
    val statGainWeights: Map<String, Double>,
    /** failChance percentage above which a candidate is excluded from a normal pick. */
    val failChanceHardLimit: Double,
    /** Multiplier turning failChance into a subtracted penalty (deterministic, not an expected-value claim). */
    val failChancePenaltyCoefficient: Double,
    /** Energy below this triggers a REST recommendation (advisor-owned threshold). */
    val restEnergyThreshold: Double,
    /** Recover mood when the mood rank is strictly below this floor token's rank. */
    val recoverMoodFloor: String,
    /** Fixed canonical order used as the final training tie-break. */
    val trainingTieBreakOrder: List<String>,
    /** When every complete candidate exceeds the hard limit: if true, pick the least-risk one; else refuse. */
    val allowOverLimitLeastRisk: Boolean,
)

/**
 * The single S1 baseline policy `raw-gain-ranker-v1`. Values mirror `DEFAULT_SHADOW_POLICY` in policy.ts exactly:
 * equal weight across the five core stat gains, a 40% failChance hard limit, a 0.5 penalty coefficient, a REST
 * energy threshold of 30, a NORMAL mood floor, the canonical facility tie-break, and least-risk over-limit fallback.
 */
val DEFAULT_SHADOW_POLICY: ShadowPolicyConfig =
    ShadowPolicyConfig(
        advisorVersion = ADVISOR_VERSION,
        policyId = "raw-gain-ranker-v1",
        statGainWeights = mapOf("spd" to 1.0, "sta" to 1.0, "pwr" to 1.0, "grt" to 1.0, "wit" to 1.0),
        failChanceHardLimit = 40.0,
        failChancePenaltyCoefficient = 0.5,
        restEnergyThreshold = 30.0,
        recoverMoodFloor = "NORMAL",
        trainingTieBreakOrder = ADVISOR_FACILITIES,
        allowOverLimitLeastRisk = true,
    )
