package com.steve1316.uma_android_automation.bot.shadowadvisor

/*
 * Port of src/lib/shadowAdvisor/types.ts, pinned by golden fixtures shared with Jest. Observational only: the types
 * hold PRE-DECISION facts and cannot carry the bot's committed action or any candidate utility score.
 */

/** Advisor schema version, not the app version. */
const val ADVISOR_VERSION: String = "1"

val ADVISOR_FACILITIES: List<String> = listOf("SPEED", "STAMINA", "POWER", "GUTS", "WIT")

/** Gain keys as serialized in the trace `gains` block (`grt`, not `guts`). */
val ADVISOR_GAIN_KEYS: List<String> = listOf("spd", "sta", "pwr", "grt", "wit")

val ADVISOR_MOOD_ORDER: List<String> = listOf("AWFUL", "BAD", "NORMAL", "GOOD", "GREAT")

/** Raw stat gains and failChance only, never a current-policy score. */
data class AdvisorFacilityFact(
    val id: String,
    val gains: Map<String, Double>?,
    val failChance: Double?,
)

/** Absent fields stay null, never defaulted. */
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

/** Pre-decision facts only; unavailable means null, never a fabricated default. */
data class AdvisorDecisionContext(
    val careerToken: String,
    val seq: Int,
    val turn: Int?,
    val scenarioType: String?,
    val state: AdvisorState,
    val trainingContest: AdvisorTrainingContest?,
    val unsupportedScenarioMechanic: String? = null,
)

enum class RecommendationStatus(val wire: String) {
    RECOMMENDATION_AVAILABLE("recommendationAvailable"),
    INSUFFICIENT_EVIDENCE("insufficientEvidence"),
    NOT_APPLICABLE("notApplicable"),
    UNSUPPORTED_DECISION_CONTEXT("unsupportedDecisionContext"),
}

enum class AdvisorAction(val wire: String) {
    TRAIN("TRAIN"),
    REST("REST"),
    RECOVER_MOOD("RECOVER_MOOD"),
}

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

/** A heuristic score margin, NOT a confidence or a probability. */
data class ScoreMargin(val value: Double, val over: String)

/** Never includes the current-policy utility score. */
data class ScoreBreakdown(
    val weightedGain: Double,
    val failurePenalty: Double,
    val total: Double,
    val perStat: Map<String, Double>,
)

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

/** Advisor-owned; no value is derived from bot settings or tuned to outcomes. */
data class ShadowPolicyConfig(
    val advisorVersion: String,
    val policyId: String,
    val statGainWeights: Map<String, Double>,
    val failChanceHardLimit: Double,
    /** Deterministic penalty multiplier, not an expected-value claim. */
    val failChancePenaltyCoefficient: Double,
    val restEnergyThreshold: Double,
    val recoverMoodFloor: String,
    val trainingTieBreakOrder: List<String>,
    val allowOverLimitLeastRisk: Boolean,
)

/** Baseline policy `raw-gain-ranker-v1`; values mirror `DEFAULT_SHADOW_POLICY` in policy.ts exactly. */
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
