package com.steve1316.uma_android_automation.bot

/**
 * Career-finalization guard: the decision layer between "the career arc is done" and the Finish
 * click that irreversibly discards every unspent skill point.
 *
 * Why this exists: a sparks-objective Unity Cup career ended with 716 unspent skill points. The
 * careerComplete session had run, parsed the screen, bought the two remaining planned entries,
 * and committed - everything reported success - and the between-run navigator then pressed
 * Finish straight through the "Remaining Skill Points: 716 pts" confirmation. Nothing in the
 * pipeline ever read or bounded the balance.
 *
 * The decision is EVIDENCE-based, never a fixed threshold: a balance is finishable only when
 * the completed session proves the balance cannot buy anything useful (below the game's minimum
 * skill price, no eligible compatible candidate left after a complete scan, or every eligible
 * candidate priced above the balance). A raw number like "350 is fine" is wrong in both
 * directions - a 350 balance with a 50-point compatible skill on screen is money left on the
 * table, and a 716 balance with every candidate excluded for recorded reasons is legitimate.
 *
 * Manual mode never arms the gate, so Manual behavior is bit-for-bit unchanged.
 */

/** Plausibility ceiling for the popup OCR value. A corruption check ONLY (digit concatenation
 * produces absurd values); never an acceptance rule for discarding points. */
internal const val FINALIZE_SP_OCR_PLAUSIBLE_MAX = 99_999

/** How long an armed verdict stays usable, in milliseconds. Finalization navigation runs
 * seconds after the verdict is armed and its between-run deadline is 10 minutes; three times
 * that bound tolerates heavy dialog recovery while making a verdict from an abandoned earlier
 * finalization structurally unusable. */
internal const val FINALIZE_VERDICT_MAX_AGE_MS: Long = 30L * 60L * 1000L

/** What the finalization guard decided for the balance it was shown. */
internal enum class FinalizeDecision {
    /** The balance is proven unspendable (or the mode is Manual): pressing Finish is approved. */
    FINISH,

    /** The evidence is incomplete or an affordable compatible candidate remains, and the one
     * controlled re-run of the careerComplete plan is still available. */
    RETRY_SPEND,

    /** Still not finishable after the retry (or the retry is unavailable): never press Finish;
     * stop the run safely and leave the career untouched for the operator. */
    BLOCK,
}

/** A finalization decision together with the exact durable reason that produced it. */
internal data class FinalizeEvaluation(val decision: FinalizeDecision, val reason: String)

/**
 * Candidate-exhaustion evidence from a skill-spend session, captured by [SkillPlan] on every
 * session record independently of telemetry IO. All classification uses the same constrained
 * candidate rules as the career-end fallback ([SkillPlan.Companion.careerEndFallbackCandidateAllowed]):
 * a candidate is ELIGIBLE when it is still purchasable, passes the Style-preference axes, is
 * not negative, not an inherited unique, and not a skip-toggled double-circle upgrade; every
 * excluded candidate is counted under its recorded reason so "exhausted" is a proven statement,
 * never an assumption about unexamined rows.
 */
internal data class FinalizeEvidence(
    val sessionOutcome: SkillSpendOutcome,
    val trigger: SkillCheckTrigger?,
    val planKey: String?,
    /** The candidate scan reached a confirmed end of list (scrollbar at track bottom or the
     * content-based end); false when it aborted, froze, timed out, or never ran. */
    val scanComplete: Boolean,
    /** Planning genuinely concluded: the session ended COMMITTED or NOTHING_TO_BUY. */
    val plannerComplete: Boolean,
    /** Purchase confirmation is trustworthy: not COMMIT_UNVERIFIED and the points delta agrees
     * with the confirmed purchase set. */
    val confirmationComplete: Boolean,
    /** The constrained career-end fallback ran this session (sparks at CAREER_COMPLETE). */
    val fallbackAttempted: Boolean,
    /** The screen-verified balance after the last purchase, or null when never read. */
    val verifiedRemainingSp: Int?,
    val eligibleCandidateCount: Int,
    val affordableEligibleCandidateCount: Int,
    val cheapestAffordableEligibleName: String?,
    val cheapestAffordableEligiblePrice: Int?,
    val cheapestEligiblePrice: Int?,
    /** Unbought candidates excluded from eligibility, counted per recorded reason
     * (wrong_axes / negative / inherited_unique / double_circle). */
    val excludedByReason: Map<String, Int>,
    val timestampMs: Long,
) {
    /** The fallback ran and nothing eligible remains affordable - the exhaustion statement. */
    fun fallbackExhausted(): Boolean = fallbackAttempted && affordableEligibleCandidateCount == 0

    fun excludedSummary(): String =
        if (excludedByReason.isEmpty()) "none" else excludedByReason.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }
}

/**
 * Decide whether the career may be finished, from session evidence plus the independent
 * Details-dialog balance read. Total and pure; every path names its exact evidence in the
 * reason so the durable log can reconstruct the decision.
 *
 * Acceptance requires PROOF, with no price-floor shortcut: the packaged skill data does not
 * support one (the cheapest purchasable negative costs 40 before any discount, and hint
 * discounts are screen-observed, never bounded by repository data), so even a tiny balance is
 * accepted only through the same evidence as a large one. The rules:
 * 1. Manual mode: always finish (the guard is Adaptive-only, like every objective behavior).
 * 2. Otherwise the evidence must be present and complete (session ran at CAREER_COMPLETE, scan
 *    reached a confirmed end, planner concluded, confirmation verified, balances agree), and
 *    then: no affordable eligible candidate may remain, and the balance must either face zero
 *    eligible candidates (all exclusions recorded) or sit below the cheapest eligible price.
 *    Adaptive careerComplete sessions therefore always scan, even below the mid-career
 *    cannot-afford heuristic - [SkillPlan.start] skips its early exit for them.
 *
 * Anything else is not finishable: RETRY_SPEND while the one controlled re-run is unused,
 * BLOCK after it.
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

    fun notFinishable(problem: String, balance: Int?): FinalizeEvaluation {
        val prefix = "UNSPENT_SKILL_POINTS: ${balance?.toString() ?: "an unknown number of"} skill points remain at career finalization and $problem"
        return if (retryUsed) {
            FinalizeEvaluation(
                FinalizeDecision.BLOCK,
                "$prefix. The one controlled re-run of the careerComplete plan was already used. Not pressing Finish; the career is left untouched.",
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
    // Eligible candidates exist, none counted affordable, yet the cheapest eligible price does
    // not exceed the balance - the evidence contradicts itself. Never finish on contradictory
    // evidence.
    return notFinishable("the candidate evidence is inconsistent (eligible=${evidence.eligibleCandidateCount}, cheapest=$cheapestEligible)", sp)
}

/**
 * One candidate row as the exhaustion classifier needs it, snapshotted from the live
 * [com.steve1316.uma_android_automation.types.SkillListEntry] state AFTER the buy passes:
 * [obtained] is true for every purchase the screen (or the in-memory purchase model that
 * mirrors it) reports as bought, and [virtual] is true for upgrade tiers the list does not
 * actually offer yet. Both are freshness facts - a confirmed purchase leaves the remaining
 * set through [obtained], and an in-place upgrade enters it only once its base purchase made
 * the game render it.
 */
internal data class RemainingCandidate(
    val name: String,
    val price: Int,
    val obtained: Boolean,
    val virtual: Boolean,
    val isNegative: Boolean,
    val isInheritedUnique: Boolean,
    val isDoubleCircle: Boolean,
    val matchesAxes: Boolean,
    /** True when this session's buy attempts on the row ran the full tap-retry budget without
     * Skill Points ever moving ([com.steve1316.uma_android_automation.types.SkillList.deadTapSkills]).
     * The game itself refused the purchase, so the row is not actually spendable no matter what
     * the scan claims - counting it as an affordable candidate stalled a queue on 2026-07-26
     * when the scan listed an already-owned skill as buyable. */
    val deadTapExhausted: Boolean = false,
)

/** The classifier's output: the candidate-exhaustion counts the finalization decision runs on. */
internal data class CandidateExhaustion(
    val eligibleCount: Int,
    val affordableCount: Int,
    val cheapestAffordableName: String?,
    val cheapestAffordablePrice: Int?,
    val cheapestEligiblePrice: Int?,
    val excludedByReason: Map<String, Int>,
)

/**
 * Classify every candidate still purchasable after the session's buys. Purchased ([RemainingCandidate.obtained])
 * and not-yet-rendered ([RemainingCandidate.virtual]) rows are not candidates at all - they
 * contribute to neither the eligible set nor the exclusion counts, because the exclusion
 * counts describe what REMAINS on the screen. Every remaining candidate lands exactly once:
 * eligible (affordable when its live price fits [remainingSp], the verified post-purchase
 * balance), or under the first recorded exclusion reason that applies
 * (negative / inherited_unique / double_circle / wrong_axes). Pure so JUnit pins it.
 */
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
                // Strongest exclusion first: the purchase was ATTEMPTED this session and the game
                // refused it (zero SP movement across the full tap-retry budget), so the row is
                // provably unspendable regardless of what the scan model claims about it.
                candidate.deadTapExhausted -> "unbuyable_dead_tap"
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

/**
 * The immutable identity of one career's finalization. Composed of the trainee identity (the
 * applied preset's outfit-bearing banner when known, else the OCR'd name), the scenario, the
 * queue run number (0 for single runs), and a per-career nonce generated when the Campaign
 * instance was CONSTRUCTED - so two careers can never share a token even when everything else
 * matches (back-to-back queue runs of the same trainee included). Arm time is deliberately NOT
 * part of the identity; it only bounds staleness via [FINALIZE_VERDICT_MAX_AGE_MS].
 */
internal fun buildCareerFinalizeToken(
    traineeIdentity: String,
    scenario: String,
    queueRun: Int?,
    careerNonce: String,
): String = "$traineeIdentity|$scenario|run${queueRun ?: 0}|$careerNonce"

/**
 * Whether a finished run's result must clear any armed finalization verdict. Only a COMPLETE
 * run's verdict may survive into the finalize navigation that follows it; every other result
 * (manual stop, abort, unhandled error, breakpoint, queue skip) means the career this verdict
 * described is no longer the next thing being finalized. Pure so JUnit pins every result code.
 */
internal fun shouldClearVerdictForRunResult(code: TaskResultCode): Boolean = code != TaskResultCode.TASK_RESULT_COMPLETE

/**
 * Parse the "Remaining Skill Points: NNN pts" value out of the Complete Career confirmation
 * dialog's OCR text. Tolerant of the usual OCR damage: casing, line breaks, l/1-for-i swaps in
 * the phrase words, a comma or period inside the number, and a missing colon. Returns null when
 * the phrase is absent or the number is implausible - the caller treats null as "could not
 * read", never as zero.
 */
internal fun parseRemainingSkillPoints(text: String): Int? {
    // Fold only the letter confusion (l -> i); folding the DIGIT 1 would corrupt the number
    // being extracted, so a 1-for-i swap inside the phrase words is handled by the [i1]
    // classes in the pattern instead.
    val norm = text.lowercase().replace('\n', ' ').replace('l', 'i')
    val match = Regex("rema[i1]n[i1]ng\\s*sk[i1]+\\s*po[i1]nts?\\s*[:;.,]?\\s*([0-9][0-9,.]{0,6})").find(norm) ?: return null
    val value = match.groupValues[1].filter { it.isDigit() }.toIntOrNull() ?: return null
    return value.takeIf { it in 0..FINALIZE_SP_OCR_PLAUSIBLE_MAX }
}

/** Performance-point type codes on the Grand Concert Complete Career dialog, in screen order. */
internal val GRAND_CONCERT_POINT_CODES: List<String> = listOf("da", "pa", "vo", "vi", "co")

/** Plausibility ceiling for one performance-point balance. Same role as
 * [FINALIZE_SP_OCR_PLAUSIBLE_MAX]: a digit-concatenation check, never an acceptance rule. */
internal const val FINALIZE_PP_OCR_PLAUSIBLE_MAX = 9_999

/** How many of the five type codes must be readable before the region counts as the performance
 * table on structure alone. Three is far past anything a Skill Points dialog could produce by
 * accident, and tolerates the label plus two codes being garbled. */
internal const val FINALIZE_PP_MIN_TYPES = 3

/**
 * What the Complete Career dialog's balance region is actually showing.
 *
 * The region is scenario-dependent. Outside Grand Concert it carries a single
 * "Remaining Skill Points: NNN pts" value. Inside Grand Concert the same band carries
 * "Remaining Performance Points" with the five Da/Pa/Vo/Vi/Co balances and NO skill-point value
 * at all, so there is nothing there to cross-check a skill-point balance against.
 */
internal sealed interface CompleteCareerBalances {
    /** The non-scenario dialog: one skill-point balance, usable for corroboration. */
    data class SkillPoints(val value: Int) : CompleteCareerBalances

    /** The Grand Concert dialog: performance-point balances by type code. These are NEVER skill
     * points and must never be substituted for one. */
    data class PerformancePoints(val byType: Map<String, Int>) : CompleteCareerBalances

    /** Neither shape could be read out of the region. */
    data object Unreadable : CompleteCareerBalances
}

/** Shared normalization for the dialog's OCR text: casing, line breaks, and the l-for-i glyph
 * confusion. The DIGIT 1 is deliberately left alone so numbers survive intact. */
private fun normalizeFinalizeDialogText(text: String): String = text.lowercase().replace('\n', ' ').replace('l', 'i')

/**
 * Parse the Grand Concert "Remaining Performance Points" table into its per-type balances, or
 * null when the region is not that table.
 *
 * Accepts the region on either the label or the structure, because either alone is already
 * conclusive proof it is not a Skill Points dialog: the label phrase, or at least
 * [FINALIZE_PP_MIN_TYPES] of the five type codes each followed by a number.
 */
internal fun parseRemainingPerformancePoints(text: String): Map<String, Int>? {
    val norm = normalizeFinalizeDialogText(text)
    val labelled = Regex("rema[i1]n[i1]ng\\s*performance\\s*po[i1]nts?").containsMatchIn(norm)
    val found = LinkedHashMap<String, Int>()
    for (code in GRAND_CONCERT_POINT_CODES) {
        val match = Regex("\\b$code\\b\\s*([0-9]{1,5})").find(norm) ?: continue
        val value = match.groupValues[1].toIntOrNull() ?: continue
        if (value in 0..FINALIZE_PP_OCR_PLAUSIBLE_MAX) found[code] = value
    }
    if (!labelled && found.size < FINALIZE_PP_MIN_TYPES) return null
    return found.takeIf { it.isNotEmpty() }
}

/**
 * Classify what the Complete Career dialog's balance region is showing.
 *
 * Performance points are tested FIRST and deliberately so: the Grand Concert dialog's own
 * warning line reads "You will lose any unused skill and performance points", so the word
 * "skill" is present on a screen that carries no skill-point balance. Testing skill points
 * first would let a future loosening of that pattern latch onto the wrong dialog and hand the
 * caller a performance-point number dressed as a skill-point balance.
 */
internal fun classifyCompleteCareerBalances(text: String): CompleteCareerBalances {
    parseRemainingPerformancePoints(text)?.let { return CompleteCareerBalances.PerformancePoints(it) }
    parseRemainingSkillPoints(text)?.let { return CompleteCareerBalances.SkillPoints(it) }
    return CompleteCareerBalances.Unreadable
}

/**
 * Whether the popup consistency check must block Finish: only when TWO readable popup values
 * both contradict the career-side verified balance (the same two-read rule the skill-point
 * trigger uses against OCR ghosts). The popup is a consistency check, never the sole source of
 * truth: an unreadable read stays inconclusive and the caller proceeds on the verified balance,
 * which an approved verdict has already proven complete.
 */
internal fun popupContradictsVerifiedBalance(verifiedSp: Int, firstRead: Int?, secondRead: Int?): Boolean =
    firstRead != null && firstRead != verifiedSp && secondRead != null && secondRead != verifiedSp

/**
 * The armed verdict for one exact career finalization. [careerToken] identifies the career the
 * verdict was produced for; the navigator captures the token it observes when its finalization
 * navigation starts and rejects any verdict that does not match it, so a verdict can never act
 * across queue runs, trainee or scenario switches, or a later unrelated navigation.
 */
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
)

/**
 * Whether [verdict] may govern the current finalization: it must exist, match the token the
 * navigator captured when this finalization began, and be younger than
 * [FINALIZE_VERDICT_MAX_AGE_MS]. Everything else - a verdict from a previous career or queue
 * run (different token), one that appeared after this navigation began (no captured token), or
 * one left over long enough to be structurally stale - is unusable, and in Adaptive mode an
 * unusable verdict means Finish is refused, never clicked on faith.
 */
internal fun finalizeVerdictUsable(verdict: FinalizeVerdict?, expectedToken: String?, nowMs: Long): Boolean =
    verdict != null &&
        expectedToken != null &&
        verdict.careerToken == expectedToken &&
        (nowMs - verdict.armedAtMs) in 0..FINALIZE_VERDICT_MAX_AGE_MS

/**
 * The identity of the career currently being played, created by the REAL career task when a run
 * starts. [nonce] distinguishes two careers that agree on trainee, scenario, and run number, and
 * it is generated at run start rather than at object-construction time so that throwaway helper
 * objects cannot mint or replace a career identity.
 */
internal data class CareerFinalizationContext(val nonce: String, val queueRun: Int?, val startedAtMs: Long)

/**
 * Process-local handoff between the career task (which produces the evidence and verdict on the
 * End screen, where the skill machinery lives) and the between-run navigator (which owns the
 * actual Complete Career and Finish clicks).
 *
 * Lifecycle, and the rule that keeps it honest: **only explicit lifecycle events mutate this
 * gate; object construction never does.** A verdict is armed by the career task just before it
 * returns COMPLETE, and is invalidated only by [beginCareer] (a real run starting), any run
 * result other than COMPLETE (manual stop, abort, error, breakpoint, skipped run), the
 * navigator reaching Home, and the Finish click that consumes it. An earlier revision cleared
 * the gate from the scenario Campaign's initializer; because the navigator builds a throwaway
 * Game (and therefore a Campaign) during its own startup, that clear erased the verdict the
 * navigator was about to consume - the guard could then never authorize a Finish. Constructors
 * must stay side-effect free here.
 *
 * In-memory on purpose: a process or service restart loses both context and verdict, and the
 * navigator treats a missing verdict in Adaptive mode as "refuse to Finish", which fails safe.
 */
internal object CareerFinalizeGate {
    @Volatile
    var verdict: FinalizeVerdict? = null
        private set

    /** The career identity created by the current run, or null when no real career task has
     * started in this process. */
    @Volatile
    var context: CareerFinalizationContext? = null
        private set

    /**
     * Called by the REAL career task at run start (and nowhere else): drops any verdict left by
     * the previous career and installs this career's identity. This is the one place a career
     * identity is created.
     */
    fun beginCareer(nonce: String, queueRun: Int?, nowMs: Long) {
        verdict = null
        context = CareerFinalizationContext(nonce, queueRun, nowMs)
    }

    fun arm(newVerdict: FinalizeVerdict) {
        verdict = newVerdict
    }

    /** Invalidate the current verdict WITHOUT touching the career identity: used by the
     * lifecycle events that end a finalization opportunity (non-COMPLETE run result, Home
     * return, and the Finish click that consumes it). */
    fun clear() {
        verdict = null
    }

    /** Full reset, for test isolation only. */
    fun reset() {
        verdict = null
        context = null
    }
}
