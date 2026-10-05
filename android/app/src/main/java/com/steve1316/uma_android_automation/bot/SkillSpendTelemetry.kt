package com.steve1316.uma_android_automation.bot

import org.json.JSONArray
import org.json.JSONObject

/** How a skill-spend session actually ended. One value per real exit in [SkillPlan.start]. */
enum class SkillSpendOutcome {
    COMMITTED,

    /** Skills were bought but the commit could not be verified; selections may still be pending. */
    COMMIT_UNVERIFIED,

    EMPTY_PLAN,

    NOTHING_TO_BUY,

    ABORTED_PARSE,

    /** The career-end Learn screen never opened within its bounded attempts (emitted by Campaign). */
    ABORTED_ENTRY,

    FAILED,

    ;

    fun token(): String = name.lowercase()
}

data class ProposedSkill(val name: String, val price: Int)

data class SkippedSkill(val name: String, val reason: String)

/**
 * One append-only `type:"skill_spend"` record per [SkillPlan.start] call, not per internal buy pass (the passes re-run
 * up to three times and would triple-count a decision). `confirmed` is skills the screen reported obtained that were not
 * already owned, never the taps attempted (a silent miss would read as a purchase). Optional identity fields are omitted,
 * never faked. Pure and Context-free for JUnit; the caller wraps the append in runCatching so telemetry cannot change a purchase.
 */
object SkillSpendTelemetry {
    /** Stamped on every record so a policy change can be told apart in the corpus. Readers of older records must tolerate absent fields. */
    const val POLICY_VERSION: String = "trigger-v4"

    const val SKIP_UNAFFORDABLE: String = "unaffordable_drift"

    const val SKIP_UNBOUGHT: String = "unbought_after_passes"

    @Suppress("LongParameterList")
    fun buildRecord(
        timestamp: Long,
        outcome: SkillSpendOutcome,
        trigger: SkillCheckTrigger?,
        planKey: String?,
        strategy: String?,
        trainee: String?,
        scenario: String?,
        fp: String?,
        turn: Int?,
        spBefore: Int?,
        spAfter: Int?,
        proposed: List<ProposedSkill>,
        confirmed: List<String>,
        skipped: List<SkippedSkill>,
        confirmedIncomplete: Boolean = false,
        threshold: Int? = null,
        tier: String? = null,
        reason: String? = null,
        objective: String? = null,
        criticalRace: String? = null,
        criticalRaceSource: String? = null,
        turnsUntilRace: Int? = null,
        plannedSkill: String? = null,
        plannedSkillObservedPrice: Int? = null,
        strategyTailAllowed: Boolean? = null,
        careerEndFallback: Boolean? = null,
        recoveryRuleActive: Boolean? = null,
        recoveryRequired: Boolean? = null,
        recoverySkill: String? = null,
        recoveryObservedPrice: Int? = null,
    ): JSONObject {
        val record = JSONObject()
        record.put("type", "skill_spend")
        record.put("ts", timestamp)
        record.put("policy", POLICY_VERSION)
        record.put("outcome", outcome.token())
        threshold?.let { record.put("threshold", it) }
        tier?.let { record.put("tier", it) }
        reason?.let { record.put("reason", it) }
        objective?.let { record.put("objective", it) }
        criticalRace?.let { record.put("criticalRace", it) }
        criticalRaceSource?.let { record.put("criticalRaceSource", it) }
        turnsUntilRace?.let { record.put("turnsUntilRace", it) }
        plannedSkill?.let { record.put("plannedSkill", it) }
        plannedSkillObservedPrice?.let { record.put("plannedSkillObservedPrice", it) }
        strategyTailAllowed?.let { record.put("strategyTailAllowed", it) }
        careerEndFallback?.let { record.put("careerEndFallback", it) }
        recoveryRuleActive?.let { record.put("recoveryRuleActive", it) }
        recoveryRequired?.let { record.put("recoveryRequired", it) }
        recoverySkill?.let { record.put("recoverySkill", it) }
        recoveryObservedPrice?.let { record.put("recoveryObservedPrice", it) }
        trigger?.let { record.put("trigger", it.name) }
        planKey?.let { record.put("plan", it) }
        strategy?.let { record.put("strategy", it) }
        trainee?.let { record.put("trainee", it) }
        scenario?.let { record.put("scenario", it) }
        fp?.let { record.put("fp", it) }
        turn?.let { record.put("turn", it) }
        spBefore?.let { record.put("spBefore", it) }
        spAfter?.let { record.put("spAfter", it) }
        // Duplicates spAfter on purpose: a separate metric, since a future session type may not end with the whole balance unspent.
        spAfter?.let { record.put("unspent", it) }
        if (proposed.isNotEmpty()) {
            record.put(
                "proposed",
                JSONArray().apply {
                    proposed.forEach { skill ->
                        put(
                            JSONObject().apply {
                                put("name", skill.name)
                                put("price", skill.price)
                            },
                        )
                    }
                },
            )
        }
        if (confirmed.isNotEmpty()) record.put("confirmed", JSONArray().apply { confirmed.forEach { put(it) } })
        // Written only when true: absence means "no gap proven", not "no gap".
        if (confirmedIncomplete) record.put("confirmedIncomplete", true)
        if (skipped.isNotEmpty()) {
            record.put(
                "skipped",
                JSONArray().apply {
                    skipped.forEach { skill ->
                        put(
                            JSONObject().apply {
                                put("name", skill.name)
                                put("reason", skill.reason)
                            },
                        )
                    }
                },
            )
        }
        return record
    }

    /** One `type:"career_finalize"` record per career finalization (the final decision; a used retry appears as `retryUsed`). Optional fields are omitted, never fabricated. */
    @Suppress("LongParameterList")
    internal fun buildCareerFinalizeRecord(
        timestamp: Long,
        decision: String,
        reason: String,
        careerToken: String,
        trainee: String?,
        scenario: String?,
        objective: String?,
        queueRun: Int?,
        verifiedRemainingSp: Int?,
        retryUsed: Boolean,
        evidence: FinalizeEvidence?,
    ): JSONObject {
        val record = JSONObject()
        record.put("type", "career_finalize")
        record.put("ts", timestamp)
        record.put("policy", POLICY_VERSION)
        record.put("finalizationDecision", decision)
        record.put("finalizationReason", reason)
        record.put("careerToken", careerToken)
        record.put("retryUsed", retryUsed)
        trainee?.let { record.put("trainee", it) }
        scenario?.let { record.put("scenario", it) }
        objective?.let { record.put("objective", it) }
        queueRun?.let { record.put("queueRun", it) }
        verifiedRemainingSp?.let { record.put("verifiedRemainingSp", it) }
        evidence?.let { e ->
            record.put("scanComplete", e.scanComplete)
            record.put("plannerComplete", e.plannerComplete)
            record.put("confirmationComplete", e.confirmationComplete)
            record.put("constrainedFallbackAttempted", e.fallbackAttempted)
            record.put("constrainedFallbackExhausted", e.fallbackExhausted())
            record.put("eligibleCandidateCount", e.eligibleCandidateCount)
            record.put("affordableEligibleCandidateCount", e.affordableEligibleCandidateCount)
            e.cheapestAffordableEligiblePrice?.let { record.put("cheapestAffordableEligiblePrice", it) }
            e.cheapestAffordableEligibleName?.let { record.put("cheapestAffordableEligibleName", it) }
            e.cheapestEligiblePrice?.let { record.put("cheapestEligiblePrice", it) }
            if (e.excludedByReason.isNotEmpty()) {
                record.put("excludedCandidateCountsByReason", JSONObject().apply { e.excludedByReason.forEach { (k, v) -> put(k, v) } })
            }
            record.put("sessionOutcome", e.sessionOutcome.token())
        }
        return record
    }

    /**
     * The navigator's follow-up record when the Complete Career dialog settled (or failed to settle) a resolvable BLOCK. Its own type keeps
     * the career's single `career_finalize` row the 1:1 join target. `skillScreenSp` is the career-side read the BLOCK was based on;
     * `verifiedRemainingSp` is written only when the dialog balance won.
     */
    internal fun buildDialogBalanceFinalizeRecord(
        timestamp: Long,
        decision: String,
        reason: String,
        verdict: FinalizeVerdict,
        firstRead: Int?,
        secondRead: Int?,
    ): JSONObject {
        val record = JSONObject()
        record.put("type", "career_finalize_dialog")
        record.put("ts", timestamp)
        record.put("policy", POLICY_VERSION)
        record.put("finalizationDecision", decision)
        record.put("finalizationReason", reason)
        record.put("careerToken", verdict.careerToken)
        record.put("retryUsed", true)
        record.put("trainee", verdict.trainee.replace(" ", "_"))
        record.put("scenario", verdict.scenario.replace(" ", "_"))
        record.put("objective", verdict.objective)
        verdict.queueRun?.let { record.put("queueRun", it) }
        record.put("skillScreenSp", verdict.verifiedRemainingSp)
        verdict.dialogResolvableBelowSp?.let { record.put("cheapestAffordableEligiblePrice", it) }
        record.put("dialogRemainingSp", JSONArray(listOf(firstRead ?: JSONObject.NULL, secondRead ?: JSONObject.NULL)))
        if (decision == FinalizeDecision.FINISH.name && firstRead != null) record.put("verifiedRemainingSp", firstRead)
        return record
    }

    /**
     * True when more points left the account than the confirmed skills' prices explain, so the obtained set missed a real
     * purchase ([SkillList.getObtainedSkills] is known to under-report skills bought moments earlier). The caller must then
     * emit no skip reasons: which skills were missed is unknown. False when either total is unknown.
     */
    fun confirmationIsIncomplete(
        proposed: List<ProposedSkill>,
        confirmed: Set<String>,
        spBefore: Int?,
        spAfter: Int?,
    ): Boolean {
        if (spBefore == null || spAfter == null) return false
        val spent: Int = spBefore - spAfter
        if (spent <= 0) return false
        val accountedFor: Int = proposed.filter { it.name in confirmed }.sumOf { it.price }
        return spent > accountedFor
    }

    /** Skip reason per planned skill never obtained, from evidence only: a last-known live price above the points left is unaffordable, anything else merely unbought (a missed scroll cannot be told from a dropped tap). */
    fun deriveSkipped(
        proposed: List<ProposedSkill>,
        confirmed: Set<String>,
        livePrices: Map<String, Int>,
        pointsLeft: Int,
    ): List<SkippedSkill> =
        proposed
            .filter { it.name !in confirmed }
            .map { candidate ->
                val price = livePrices[candidate.name]
                val reason = if (price != null && price > pointsLeft) SKIP_UNAFFORDABLE else SKIP_UNBOUGHT
                SkippedSkill(candidate.name, reason)
            }
}
