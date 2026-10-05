package com.steve1316.uma_android_automation.bot

/**
 * Decides whether the career may be Finished, since Finish discards every unspent skill point. Evidence-based, never a
 * fixed threshold. Manual mode never arms the gate.
 */

/** Corruption check only (digit concatenation yields absurd values); never an acceptance rule for discarding points. */
internal const val FINALIZE_SP_OCR_PLAUSIBLE_MAX = 99_999

/** Armed verdict lifetime: three times the 10-minute between-run deadline, so a verdict from an abandoned finalization is unusable. */
internal const val FINALIZE_VERDICT_MAX_AGE_MS: Long = 30L * 60L * 1000L

/** Exclusion key for a row the game refused across the whole tap-retry budget. */
internal const val DEAD_TAP_EXCLUSION = "unbuyable_dead_tap"

internal enum class FinalizeDecision {
    FINISH,

    RETRY_SPEND,

    BLOCK,
}

/**
 * [dialogResolvableBelowSp] is set only on a BLOCK whose sole cause is affordable candidates in a session where the game refused a
 * purchase the read balance allowed: that balance read is in doubt, so the Complete Career dialog's own balance may settle it.
 */
internal data class FinalizeEvaluation(val decision: FinalizeDecision, val reason: String, val dialogResolvableBelowSp: Int? = null)

/**
 * Candidate-exhaustion evidence from a skill-spend session. Every excluded candidate is counted under its recorded reason,
 * so "exhausted" is a proven statement, not an assumption about unexamined rows.
 */
internal data class FinalizeEvidence(
    val sessionOutcome: SkillSpendOutcome,
    val trigger: SkillCheckTrigger?,
    val planKey: String?,
    val scanComplete: Boolean,
    val plannerComplete: Boolean,
    /** Not COMMIT_UNVERIFIED and the points delta agrees with the confirmed purchase set. */
    val confirmationComplete: Boolean,
    val fallbackAttempted: Boolean,
    val verifiedRemainingSp: Int?,
    val eligibleCandidateCount: Int,
    val affordableEligibleCandidateCount: Int,
    val cheapestAffordableEligibleName: String?,
    val cheapestAffordableEligiblePrice: Int?,
    val cheapestEligiblePrice: Int?,
    /** Keys: wrong_axes, negative, inherited_unique, double_circle. */
    val excludedByReason: Map<String, Int>,
    val timestampMs: Long,
) {
    fun fallbackExhausted(): Boolean = fallbackAttempted && affordableEligibleCandidateCount == 0

    fun excludedSummary(): String =
        if (excludedByReason.isEmpty()) "none" else excludedByReason.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }
}

/**
 * Decides whether the career may be finished from session evidence. [detailsSp] is the trainee's balance, which today is copied
 * from the same skill-screen read as the evidence, so the stale check only catches a later overwrite, never an OCR misread.
 * No price-floor shortcut: hint discounts are screen-observed and never bounded by repository data, so even a tiny balance
 * needs the same proof. Manual mode always finishes; otherwise the evidence must be complete and no affordable eligible
 * candidate may remain. Anything else is RETRY_SPEND while the one re-run is unused, then BLOCK.
 */
internal fun evaluateCareerFinalization(
    mode: SkillSpendMode,
    detailsSp: Int?,
    evidence: FinalizeEvidence?,
    retryUsed: Boolean,
): FinalizeEvaluation {
    if (mode != SkillSpendMode.ADAPTIVE) {
        return FinalizeEvaluation(FinalizeDecision.FINISH, "Manual skill-spend mode: finalization guard not armed.")
    }

    fun notFinishable(problem: String, balance: Int?, dialogResolvableBelowSp: Int? = null): FinalizeEvaluation {
        val prefix = "UNSPENT_SKILL_POINTS: ${balance?.toString() ?: "an unknown number of"} skill points remain at career finalization and $problem"
        return if (retryUsed) {
            val outcome =
                dialogResolvableBelowSp?.let {
                    "The game refused a purchase this balance allowed, so the Complete Career dialog decides: Finish only if two reads " +
                        "agree on fewer than $it points."
                } ?: "Not pressing Finish; the career is left untouched."
            FinalizeEvaluation(
                FinalizeDecision.BLOCK,
                "$prefix. The one controlled re-run of the careerComplete plan was already used. $outcome",
                dialogResolvableBelowSp,
            )
        } else {
            FinalizeEvaluation(FinalizeDecision.RETRY_SPEND, "$prefix.")
        }
    }

    if (evidence == null || evidence.trigger != SkillCheckTrigger.CAREER_COMPLETE) {
        return notFinishable("no careerComplete skill-spend session ran for this career", detailsSp)
    }
    val sp = evidence.verifiedRemainingSp
        ?: return notFinishable("the verified final balance is unavailable (session ended ${evidence.sessionOutcome.token()})", detailsSp)
    if (detailsSp != null && detailsSp != sp) {
        return notFinishable("the balance is stale: the Details read shows $detailsSp but the spend session verified $sp", detailsSp)
    }
    if (!evidence.scanComplete) {
        return notFinishable("the skill screen scan did not reach a confirmed end of the list, so candidate exhaustion is unproven", sp)
    }
    if (!evidence.plannerComplete) {
        return notFinishable("the planner did not complete (session ended ${evidence.sessionOutcome.token()})", sp)
    }
    if (!evidence.confirmationComplete) {
        return notFinishable("purchase confirmation was incomplete (session ended ${evidence.sessionOutcome.token()})", sp)
    }
    if (evidence.affordableEligibleCandidateCount > 0) {
        val cheapest =
            evidence.cheapestAffordableEligibleName?.let { "\"$it\" at ${evidence.cheapestAffordableEligiblePrice} points" }
                ?: "${evidence.cheapestAffordableEligiblePrice} points"
        return notFinishable(
            "${evidence.affordableEligibleCandidateCount} affordable compatible candidate(s) remain - cheapest $cheapest",
            sp,
            evidence.cheapestAffordableEligiblePrice.takeIf { (evidence.excludedByReason[DEAD_TAP_EXCLUSION] ?: 0) > 0 },
        )
    }
    if (evidence.eligibleCandidateCount == 0) {
        return FinalizeEvaluation(
            FinalizeDecision.FINISH,
            "No eligible compatible candidates remain after a complete scan; remaining $sp points are unspendable. Excluded candidates: ${evidence.excludedSummary()}.",
        )
    }
    val cheapestEligible = evidence.cheapestEligiblePrice
    if (cheapestEligible != null && sp < cheapestEligible) {
        return FinalizeEvaluation(
            FinalizeDecision.FINISH,
            "Remaining $sp points are below the cheapest eligible compatible candidate ($cheapestEligible points; " +
                "${evidence.eligibleCandidateCount} eligible, none affordable). Excluded candidates: ${evidence.excludedSummary()}.",
        )
    }
    // Contradictory evidence (eligible candidates, none affordable, yet the cheapest fits the balance): never finish.
    return notFinishable("the candidate evidence is inconsistent (eligible=${evidence.eligibleCandidateCount}, cheapest=$cheapestEligible)", sp)
}

/** Candidate snapshot taken after the buy passes; [obtained] and [virtual] rows are not candidates. */
internal data class RemainingCandidate(
    val name: String,
    val price: Int,
    val obtained: Boolean,
    val virtual: Boolean,
    val isNegative: Boolean,
    val isInheritedUnique: Boolean,
    val isDoubleCircle: Boolean,
    val matchesAxes: Boolean,
    /** The buy ran the full tap-retry budget with no Skill Points movement: the game refused it, so the row is not spendable. */
    val deadTapExhausted: Boolean = false,
)

internal data class CandidateExhaustion(
    val eligibleCount: Int,
    val affordableCount: Int,
    val cheapestAffordableName: String?,
    val cheapestAffordablePrice: Int?,
    val cheapestEligiblePrice: Int?,
    val excludedByReason: Map<String, Int>,
)

/** Classifies candidates still purchasable after the session's buys: eligible, or under the first applicable exclusion reason. */
internal fun classifyRemainingCandidates(
    candidates: List<RemainingCandidate>,
    remainingSp: Int,
    skipDoubleCircleUpgrades: Boolean,
): CandidateExhaustion {
    var eligible = 0
    var affordable = 0
    var cheapestAffordable: Int? = null
    var cheapestAffordableName: String? = null
    var cheapestEligible: Int? = null
    val excluded = mutableMapOf<String, Int>()
    for (candidate in candidates) {
        if (candidate.obtained || candidate.virtual || candidate.price <= 0) continue
        val exclusionReason: String? =
            when {
                // Strongest exclusion: the buy was attempted and refused (no SP movement across the retry budget).
                candidate.deadTapExhausted -> DEAD_TAP_EXCLUSION
                candidate.isNegative -> "negative"
                candidate.isInheritedUnique -> "inherited_unique"
                skipDoubleCircleUpgrades && candidate.isDoubleCircle -> "double_circle"
                !candidate.matchesAxes -> "wrong_axes"
                else -> null
            }
        if (exclusionReason != null) {
            excluded[exclusionReason] = (excluded[exclusionReason] ?: 0) + 1
            continue
        }
        eligible++
        if (cheapestEligible == null || candidate.price < cheapestEligible!!) cheapestEligible = candidate.price
        if (candidate.price <= remainingSp) {
            affordable++
            if (cheapestAffordable == null || candidate.price < cheapestAffordable!!) {
                cheapestAffordable = candidate.price
                cheapestAffordableName = candidate.name
            }
        }
    }
    return CandidateExhaustion(
        eligibleCount = eligible,
        affordableCount = affordable,
        cheapestAffordableName = cheapestAffordableName,
        cheapestAffordablePrice = cheapestAffordable,
        cheapestEligiblePrice = cheapestEligible,
        excludedByReason = excluded.toMap(),
    )
}

/** Immutable identity of one career's finalization: trainee, scenario, run number and a per-career nonce, so back-to-back runs never share a token. */
internal fun buildCareerFinalizeToken(
    traineeIdentity: String,
    scenario: String,
    queueRun: Int?,
    careerNonce: String,
): String = "$traineeIdentity|$scenario|run${queueRun ?: 0}|$careerNonce"

/** Whether a finished run's result must clear any armed verdict; only COMPLETE may survive into the finalize navigation. */
internal fun shouldClearVerdictForRunResult(code: TaskResultCode): Boolean = code != TaskResultCode.TASK_RESULT_COMPLETE

/** Parses "Remaining Skill Points: NNN pts" from the confirmation OCR; null (never zero) when absent or implausible. */
internal fun parseRemainingSkillPoints(text: String): Int? {
    // Fold only l -> i; folding the digit 1 would corrupt the number, so 1-for-i swaps are handled by the [i1] classes.
    val norm = text.lowercase().replace('\n', ' ').replace('l', 'i')
    val match = Regex("rema[i1]n[i1]ng\\s*sk[i1]+\\s*po[i1]nts?\\s*[:;.,]?\\s*([0-9][0-9,.]{0,6})").find(norm) ?: return null
    val value = match.groupValues[1].filter { it.isDigit() }.toIntOrNull() ?: return null
    return value.takeIf { it in 0..FINALIZE_SP_OCR_PLAUSIBLE_MAX }
}

internal val GRAND_CONCERT_POINT_CODES: List<String> = listOf("da", "pa", "vo", "vi", "co")

/** Digit-concatenation check only, like [FINALIZE_SP_OCR_PLAUSIBLE_MAX]. */
internal const val FINALIZE_PP_OCR_PLAUSIBLE_MAX = 9_999

/** Type codes readable before the region counts as the performance table; far past what a Skill Points dialog could produce by accident. */
internal const val FINALIZE_PP_MIN_TYPES = 3

/** What the Complete Career balance region shows: one skill-point value, or (Grand Concert) five performance-point balances and no skill-point value. */
internal sealed interface CompleteCareerBalances {
    data class SkillPoints(val value: Int) : CompleteCareerBalances

    /** Performance points are never skill points and must not be substituted for one. */
    data class PerformancePoints(val byType: Map<String, Int>) : CompleteCareerBalances

    data object Unreadable : CompleteCareerBalances
}

/** Normalizes the dialog OCR text; the digit 1 is left alone so numbers survive. */
private fun normalizeFinalizeDialogText(text: String): String = text.lowercase().replace('\n', ' ').replace('l', 'i')

/**
 * Whether [norm] carries "performance points" anywhere. The phrase is in both the banner (white on pink, ~60 grey levels) and the
 * warning (blue on white, ~145), so matching the bare phrase can use the more readable one. Absent from the skill-point dialog.
 */
private fun carriesPerformancePointsPhrase(norm: String): Boolean = Regex("performance\\s*po[i1]nts?").containsMatchIn(norm)

/**
 * Parses the Grand Concert performance-point table into per-type balances, or null when the region is not that table. The phrase
 * or at least [FINALIZE_PP_MIN_TYPES] type codes is conclusive; a phrase-only match returns an empty map, because knowing it is a
 * performance dialog is what stops a skill-point balance being read off a screen that has none.
 */
internal fun parseRemainingPerformancePoints(text: String): Map<String, Int>? {
    val norm = normalizeFinalizeDialogText(text)
    val phrasePresent = carriesPerformancePointsPhrase(norm)
    val found = LinkedHashMap<String, Int>()
    for (code in GRAND_CONCERT_POINT_CODES) {
        val match = Regex("\\b$code\\b\\s*([0-9]{1,5})").find(norm) ?: continue
        val value = match.groupValues[1].toIntOrNull() ?: continue
        if (value in 0..FINALIZE_PP_OCR_PLAUSIBLE_MAX) found[code] = value
    }
    if (!phrasePresent && found.size < FINALIZE_PP_MIN_TYPES) return null
    return found
}

/** Bounds the OCR excerpt logged for an unclassifiable region so a repeated failure cannot flood the log. */
internal const val FINALIZE_OCR_EXCERPT_MAX = 240

/** One-line, length-bounded, printable OCR text for the unreadable-branch diagnostic. */
internal fun sanitizeOcrExcerpt(text: String): String {
    val flattened = text.map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("").replace(Regex(" +"), " ").trim()
    if (flattened.isEmpty()) return "(empty)"
    return if (flattened.length <= FINALIZE_OCR_EXCERPT_MAX) flattened else flattened.take(FINALIZE_OCR_EXCERPT_MAX) + "..."
}

/**
 * Classifies what the balance region shows. Performance points are tested first: the Grand Concert warning contains "skill",
 * so testing skill points first could hand back a performance-point number as a skill-point balance. An empty performance
 * map means a Grand Concert dialog with unreadable balances.
 */
internal fun classifyCompleteCareerBalances(text: String): CompleteCareerBalances {
    parseRemainingPerformancePoints(text)?.let { return CompleteCareerBalances.PerformancePoints(it) }
    parseRemainingSkillPoints(text)?.let { return CompleteCareerBalances.SkillPoints(it) }
    return CompleteCareerBalances.Unreadable
}

/**
 * Whether the popup check must block Finish: only when two readable popup values both contradict the verified balance.
 * An unreadable read stays inconclusive; the popup is never the sole source of truth.
 */
internal fun popupContradictsVerifiedBalance(verifiedSp: Int, firstRead: Int?, secondRead: Int?): Boolean =
    firstRead != null && firstRead != verifiedSp && secondRead != null && secondRead != verifiedSp

/** The armed verdict for one exact career; the navigator rejects any verdict whose [careerToken] differs from the one it captured. */
internal data class FinalizeVerdict(
    val careerToken: String,
    val queueRun: Int?,
    val trainee: String,
    val scenario: String,
    val objective: String,
    val approved: Boolean,
    val verifiedRemainingSp: Int,
    val sessionTimestampMs: Long?,
    val reason: String,
    val armedAtMs: Long,
    /** Copied from [FinalizeEvaluation.dialogResolvableBelowSp]; only a BLOCK verdict carries it. */
    val dialogResolvableBelowSp: Int? = null,
)

/** A BLOCK the navigator may open the Complete Career dialog for, to settle with [dialogBalanceResolvesBlock]. */
internal fun FinalizeVerdict.blockResolvableByDialog(): Boolean = !approved && dialogResolvableBelowSp != null

/**
 * Whether two Complete Career dialog reads settle a resolvable BLOCK. Both must be skill-point values, agree, and sit below both the
 * skill-screen balance and the cheapest candidate that was affordable at it: every eligible candidate then costs more than the
 * dialog balance. One read, a disagreement, or a Grand Concert performance-point dialog never settles it.
 */
internal fun dialogBalanceResolvesBlock(verdict: FinalizeVerdict, first: CompleteCareerBalances, second: CompleteCareerBalances): Boolean {
    if (!verdict.blockResolvableByDialog()) return false
    val bound = verdict.dialogResolvableBelowSp ?: return false
    val a = (first as? CompleteCareerBalances.SkillPoints)?.value ?: return false
    val b = (second as? CompleteCareerBalances.SkillPoints)?.value ?: return false
    return a == b && a < verdict.verifiedRemainingSp && a < bound
}

/** Whether [verdict] may govern this finalization: it exists, matches the captured token and is younger than [FINALIZE_VERDICT_MAX_AGE_MS]. */
internal fun finalizeVerdictUsable(verdict: FinalizeVerdict?, expectedToken: String?, nowMs: Long): Boolean =
    verdict != null &&
        expectedToken != null &&
        verdict.careerToken == expectedToken &&
        (nowMs - verdict.armedAtMs) in 0..FINALIZE_VERDICT_MAX_AGE_MS

/** Identity of the career being played, created by the real career task at run start so throwaway helper objects cannot mint one. */
internal data class CareerFinalizationContext(val nonce: String, val queueRun: Int?, val startedAtMs: Long)

/**
 * Process-local handoff between the career task (evidence and verdict) and the between-run navigator (Complete Career and Finish
 * clicks). Only explicit lifecycle events mutate it; construction never does, because the navigator's throwaway Game/Campaign
 * once erased the verdict it was about to consume. A missing verdict in Adaptive mode refuses Finish, so a restart fails safe.
 */
internal object CareerFinalizeGate {
    @Volatile
    var verdict: FinalizeVerdict? = null
        private set

    @Volatile
    var context: CareerFinalizationContext? = null
        private set

    /** Called by the real career task at run start only: drops any prior verdict and installs this career's identity. */
    fun beginCareer(nonce: String, queueRun: Int?, nowMs: Long) {
        verdict = null
        context = CareerFinalizationContext(nonce, queueRun, nowMs)
    }

    fun arm(newVerdict: FinalizeVerdict) {
        verdict = newVerdict
    }

    /** Invalidates the verdict without touching the career identity. */
    fun clear() {
        verdict = null
    }

    fun reset() {
        verdict = null
        context = null
    }
}
