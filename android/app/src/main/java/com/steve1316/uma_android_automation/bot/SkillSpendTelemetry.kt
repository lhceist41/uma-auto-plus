package com.steve1316.uma_android_automation.bot

import org.json.JSONArray
import org.json.JSONObject

/** How a skill-spend session actually ended. One value per real exit in [SkillPlan.start]. */
enum class SkillSpendOutcome {
    /** The purchase commit was verified on screen. */
    COMMITTED,

    /** Skills were bought but the commit could not be verified - selections may still be pending. */
    COMMIT_UNVERIFIED,

    /** The plan had no skill names and no purchase options enabled, so nothing was scanned. */
    EMPTY_PLAN,

    /** Nothing was worth buying: points below the cheapest skill, or the planner selected none. */
    NOTHING_TO_BUY,

    /** The skill list could not be read (no entries parsed, or every buy pass saw zero rows). */
    ABORTED_PARSE,

    /** The career-end Learn screen never opened within its bounded attempts (emitted by Campaign). */
    ABORTED_ENTRY,

    /** The session could not start: not on the skill screen, or the plan key did not resolve. */
    FAILED,

    ;

    /** Lower-snake token used in the corpus, e.g. `commit_unverified`. */
    fun token(): String = name.lowercase()
}

/** A planned purchase as the planner proposed it: the skill and the price it was priced at. */
data class ProposedSkill(val name: String, val price: Int)

/** A planned skill that was not bought, with a reason derived from evidence (never guessed). */
data class SkippedSkill(val name: String, val reason: String)

/**
 * Builds one append-only `type:"skill_spend"` record per skill-spend session.
 *
 * Deliberately one record per invocation of [SkillPlan.start] - NOT one per internal scroll/buy pass.
 * The passes are an execution detail that already re-runs up to three times for coverage; recording
 * them separately would triple-count a single decision and make session counts meaningless.
 *
 * `proposed` is the planner's output. `confirmed` is evidence: skills the screen reported as obtained
 * that were not already owned when the list was parsed - never the set of taps attempted, because a
 * tap that silently missed would otherwise be recorded as a purchase that never happened.
 *
 * Optional identity (`trainee`, `scenario`, `fp`, `turn`) is omitted when unavailable rather than
 * filled with a placeholder, matching the sparks records: a missing field is honest, a fabricated one
 * silently mis-attributes the session to the wrong arm.
 *
 * Pure and Context-free so JUnit can pin the shape without a live Campaign; [SkillPlan] hands the
 * result to `OutcomeCorpus.append` inside a runCatching so a telemetry failure can never change a
 * purchase result.
 */
object SkillSpendTelemetry {
    /** Stamped on every record so a later policy change can be told apart in the corpus.
     * trigger-v2 added the adaptive-threshold fields (`threshold`/`tier`/`reason`); trigger-v3
     * added `objective` plus the trigger-specific rationale fields (`criticalRace`,
     * `criticalRaceSource`, `turnsUntilRace`, `plannedSkill`, `plannedSkillObservedPrice`);
     * trigger-v4 adds `strategyTailAllowed` (2B-1 planned-only shaping: false on Adaptive sparks
     * sessions, true otherwise including every Manual record, absent when the session exited
     * before the planner resolved it) and, still under the same version, the 2B-2 recovery
     * fields: `recoveryRuleActive`/`recoveryRequired` on sessions where the recovery gate was
     * evaluated, plus `recoverySkill`/`recoveryObservedPrice` only when an injection actually
     * bought something. Readers of older records must tolerate absence rather than infer
     * values. */
    const val POLICY_VERSION: String = "trigger-v4"

    /** A planned skill whose live price rose above the remaining budget before it could be bought. */
    const val SKIP_UNAFFORDABLE: String = "unaffordable_drift"

    /** A planned skill still unbought after every buy pass (never scrolled into view, or tap missed). */
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
        // Threshold-policy attribution (trigger-v2): the resolved high-water threshold in effect
        // for this career, the tier that produced it ("manual" when no tier governs), and the
        // resolution reason. Distinct from `trigger`, which records what caused THIS spend.
        threshold?.let { record.put("threshold", it) }
        tier?.let { record.put("tier", it) }
        reason?.let { record.put("reason", it) }
        // Phase 2A attribution (trigger-v3): the preset objective governing trigger gating, and
        // the trigger-specific rationale - which race made CRITICAL_RACE fire (and via which
        // source, goal_ocr or racing_plan), or which observed planned skill made
        // PLANNED_SKILL_AFFORDABLE fire and at what observed price.
        objective?.let { record.put("objective", it) }
        criticalRace?.let { record.put("criticalRace", it) }
        criticalRaceSource?.let { record.put("criticalRaceSource", it) }
        turnsUntilRace?.let { record.put("turnsUntilRace", it) }
        plannedSkill?.let { record.put("plannedSkill", it) }
        plannedSkillObservedPrice?.let { record.put("plannedSkillObservedPrice", it) }
        // 2B-1 planner shaping (trigger-v4): whether this session's strategy tail was allowed.
        // False = planned-only (Adaptive sparks); absent = the planner never resolved it.
        strategyTailAllowed?.let { record.put("strategyTailAllowed", it) }
        // 2B-2 recovery protection (same trigger-v4): gate evaluation and, when an injection
        // bought something, the selected skill at its live observed price. All absent when the
        // gate never armed; skill/price absent on satisfied, no-candidate, and unaffordable
        // sessions (never fabricated).
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
        // Points left on the table by this session. Same value as spAfter today (the screen's live
        // total after the commit); kept as its own field because it is the metric the adaptive work
        // will read, and a future session type may not end with the whole balance unspent.
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
        // Only ever written when true: its absence means "no gap proven", not "no gap".
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

    /**
     * True when the points delta cannot be explained by the confirmed skills alone: more points left
     * the account than their prices account for, so the screen's obtained set missed a real purchase.
     *
     * This is evidence, not a guess - both totals are the skill screen's own reads, and points cannot
     * leave the account without a purchase. It exists because [SkillList.getObtainedSkills] is known to
     * under-report skills bought moments earlier (observed live 2026-07-16: a five-skill careerComplete
     * plan spent exactly its full 563-point cost while the obtained set reported only three of them).
     *
     * When this is true the caller must NOT emit skip reasons: the session provably bought skills the
     * confirmation missed, so "not bought" is false for an unknown subset of them, and there is no
     * evidence identifying which. A flagged gap is honest; a fabricated reason is not.
     *
     * Returns false when either total is unknown - an unknown delta proves nothing either way.
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

    /**
     * Derives the skip reason for each planned skill that was never obtained, from evidence only.
     *
     * A skill whose last known live price exceeds the points left is provably unaffordable; anything
     * else is reported as merely unbought. No other reason is inferred - the pass cannot know whether
     * a row was missed by the scroll or by a dropped tap, so it does not claim to.
     *
     * @param proposed The planner's output.
     * @param confirmed Skills evidenced as obtained this session.
     * @param livePrices Last known screen price per skill name (absent when the row was never seen).
     * @param pointsLeft The points remaining after the session.
     */
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
