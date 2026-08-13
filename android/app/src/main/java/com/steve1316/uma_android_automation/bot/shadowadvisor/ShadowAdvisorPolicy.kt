package com.steve1316.uma_android_automation.bot.shadowadvisor

/**
 * Kotlin port of the Shadow Advisor S1 policy `raw-gain-ranker-v1` (`src/lib/shadowAdvisor/policy.ts`). A pure
 * function of one immutable [AdvisorDecisionContext] plus static config: it ranks a complete training contest by
 * weighted RAW stat gains minus a failure penalty and applies a state recovery guardrail. It reuses NONE of the
 * bot's scoring (no candidate utility score, no bot thresholds, no bot tie-break, no selected reason), touches no
 * filesystem/network/clock/RNG, and produces a deeply-equal result for the same (context, config). Every
 * advisor-visible number is formatted through [JsNumber] so reason strings and the serialized record match the
 * TypeScript authority byte-for-byte; that parity is pinned by checked-in golden fixtures shared with Jest.
 */
object ShadowAdvisorPolicy {
    /**
     * Stable offline evidence omissions carried on every training recommendation. Factual disclosures (the advisor
     * scored raw gains only), not apologies. Order mirrors OFFLINE_TRAINING_LIMITATIONS in policy.ts exactly.
     */
    val OFFLINE_TRAINING_LIMITATIONS: List<String> =
        listOf(
            "relationship, rainbow, and hint facts are unavailable offline",
            "support-card presence and bonuses are unavailable offline",
            "advisor scores raw stat gains only; the current-policy utility score is intentionally excluded",
        )

    private data class ScoredFacility(
        val id: String,
        val weightedGain: Double,
        val failurePenalty: Double,
        val total: Double,
        val failChance: Double,
        val overLimit: Boolean,
        val perStat: Map<String, Double>,
    )

    /** Rank of a mood token (AWFUL=0 .. GREAT=4), or null when the token is absent/unrecognized. */
    private fun moodRank(mood: String?): Int? {
        if (mood == null) return null
        val i = ADVISOR_MOOD_ORDER.indexOf(mood)
        return if (i < 0) null else i
    }

    private fun scoreFacility(id: String, gains: Map<String, Double>, failChance: Double, config: ShadowPolicyConfig): ScoredFacility {
        val perStat = LinkedHashMap<String, Double>()
        var weightedGain = 0.0
        for (key in ADVISOR_GAIN_KEYS) {
            val gain = gains[key] ?: continue
            if (!gain.isFinite()) continue
            val contribution = gain * (config.statGainWeights[key] ?: 0.0)
            perStat[key] = contribution
            weightedGain += contribution
        }
        val failurePenalty = failChance * config.failChancePenaltyCoefficient
        return ScoredFacility(id, weightedGain, failurePenalty, weightedGain - failurePenalty, failChance, failChance > config.failChanceHardLimit, perStat)
    }

    private fun tieBreakIndex(id: String, tieBreak: List<String>): Int {
        val i = tieBreak.indexOf(id)
        // Unknown ids sort last. A complete contest is always the five canonical facilities, so this only ever
        // fires defensively; ADVISOR_FACILITIES all resolve to 0..4 and never overflow the compareTo chains below.
        return if (i < 0) Int.MAX_VALUE else i
    }

    /** Normal under-limit order: advisor total desc, then lower failChance, then fixed canonical tie-break order. */
    private fun compareScored(tieBreak: List<String>): Comparator<ScoredFacility> =
        Comparator { a, b ->
            val byTotal = b.total.compareTo(a.total)
            if (byTotal != 0) return@Comparator byTotal
            val byFail = a.failChance.compareTo(b.failChance)
            if (byFail != 0) return@Comparator byFail
            tieBreakIndex(a.id, tieBreak).compareTo(tieBreakIndex(b.id, tieBreak))
        }

    /** Risk-first order for the all-over-limit fallback: lowest failChance first, then higher total, then canonical. */
    private fun compareLeastRisk(tieBreak: List<String>): Comparator<ScoredFacility> =
        Comparator { a, b ->
            val byFail = a.failChance.compareTo(b.failChance)
            if (byFail != 0) return@Comparator byFail
            val byTotal = b.total.compareTo(a.total)
            if (byTotal != 0) return@Comparator byTotal
            tieBreakIndex(a.id, tieBreak).compareTo(tieBreakIndex(b.id, tieBreak))
        }

    private fun n(value: Double): String = JsNumber.format(value)

    private fun r1(value: Double): String = JsNumber.format(JsNumber.round1(value))

    /**
     * The pure S1 policy. Evaluation order: explicit unsupported-mechanic marker, then forced race-day
     * suppression, then the state recovery guardrail (energy before mood), then training ranking, then the
     * no-contest classification. Every branch returns an explicit status; nothing is guessed.
     */
    fun recommend(context: AdvisorDecisionContext, config: ShadowPolicyConfig = DEFAULT_SHADOW_POLICY): ShadowRecommendation {
        fun base(
            status: RecommendationStatus,
            recommendedAction: AdvisorAction? = null,
            recommendedTrainingType: String? = null,
            scoreMargin: ScoreMargin? = null,
            reasons: List<ShadowReason>,
            limitations: List<String>,
            scoreBreakdown: ScoreBreakdown? = null,
        ) = ShadowRecommendation(
            advisorVersion = config.advisorVersion,
            policyId = config.policyId,
            careerToken = context.careerToken,
            seq = context.seq,
            turn = context.turn,
            status = status,
            recommendedAction = recommendedAction,
            recommendedTrainingType = recommendedTrainingType,
            scoreMargin = scoreMargin,
            reasons = reasons,
            limitations = limitations,
            scoreBreakdown = scoreBreakdown,
        )

        // 1. An explicit factual scenario-mechanic marker means S1 does not model this turn.
        val marker = context.unsupportedScenarioMechanic
        if (marker != null && marker.isNotEmpty()) {
            return base(
                RecommendationStatus.UNSUPPORTED_DECISION_CONTEXT,
                reasons = listOf(ShadowReason(ShadowReasonCode.SCENARIO_MECHANIC_UNSUPPORTED, "scenario mechanic $marker is not modeled by S1")),
                limitations = emptyList(),
            )
        }

        // 2. A forced race day (mandatory or scheduled) suppresses training/recovery advice entirely.
        val raceFlags = context.state.raceFlags
        if (raceFlags != null && (raceFlags.mandatory || raceFlags.scheduled)) {
            val which = if (raceFlags.mandatory) "mandatory" else "scheduled"
            return base(
                RecommendationStatus.NOT_APPLICABLE,
                reasons = listOf(ShadowReason(ShadowReasonCode.RACE_DAY_FORCED, "$which race day; training/recovery comparison is not applicable")),
                limitations = emptyList(),
            )
        }

        // 3. Recovery guardrail on factual state. Energy takes precedence over mood.
        val energy = context.state.energy
        if (energy != null && energy < config.restEnergyThreshold) {
            return base(
                RecommendationStatus.RECOMMENDATION_AVAILABLE,
                recommendedAction = AdvisorAction.REST,
                reasons = listOf(ShadowReason(ShadowReasonCode.ENERGY_BELOW_ADVISOR_THRESHOLD, "energy ${n(energy)} below advisor REST threshold ${n(config.restEnergyThreshold)}")),
                limitations = OFFLINE_TRAINING_LIMITATIONS,
            )
        }
        val mRank = moodRank(context.state.mood)
        val floorRank = moodRank(config.recoverMoodFloor)
        if (mRank != null && floorRank != null && mRank < floorRank) {
            return base(
                RecommendationStatus.RECOMMENDATION_AVAILABLE,
                recommendedAction = AdvisorAction.RECOVER_MOOD,
                reasons = listOf(ShadowReason(ShadowReasonCode.MOOD_BELOW_ADVISOR_FLOOR, "mood ${context.state.mood} below advisor floor ${config.recoverMoodFloor}")),
                limitations = OFFLINE_TRAINING_LIMITATIONS,
            )
        }

        // 4. Training ranking over a proven complete contest.
        val tc = context.trainingContest
        if (tc != null) {
            if (!tc.complete) {
                return base(
                    RecommendationStatus.INSUFFICIENT_EVIDENCE,
                    reasons = listOf(ShadowReason(ShadowReasonCode.INCOMPLETE_TRAINING_CONTEST, "training contest incomplete (${tc.facilities.size} facility candidate(s), five required)")),
                    limitations = emptyList(),
                )
            }
            val scored = ArrayList<ScoredFacility>(tc.facilities.size)
            for (f in tc.facilities) {
                if (f.gains == null || f.failChance == null) {
                    val missing = if (f.gains == null) "gains" else "failChance"
                    return base(
                        RecommendationStatus.INSUFFICIENT_EVIDENCE,
                        reasons = listOf(ShadowReason(ShadowReasonCode.INCOMPLETE_TRAINING_CONTEST, "facility ${f.id} is missing $missing")),
                        limitations = emptyList(),
                    )
                }
                scored.add(scoreFacility(f.id, f.gains, f.failChance, config))
            }
            return rankTraining(context, scored, config)
        }

        // 5. No training contest and no recovery trigger. Distinguish "state unavailable" from "domain N/A".
        if (raceFlags == null) {
            return base(
                RecommendationStatus.INSUFFICIENT_EVIDENCE,
                reasons = listOf(ShadowReason(ShadowReasonCode.STATE_UNAVAILABLE, "no training contest and pre-decision state unavailable")),
                limitations = emptyList(),
            )
        }
        return base(RecommendationStatus.NOT_APPLICABLE, reasons = emptyList(), limitations = emptyList())
    }

    /** Ranks a fully-scored complete contest and builds the TRAIN recommendation (or refuses per config). */
    private fun rankTraining(context: AdvisorDecisionContext, scored: List<ScoredFacility>, config: ShadowPolicyConfig): ShadowRecommendation {
        fun rec(
            status: RecommendationStatus,
            recommendedAction: AdvisorAction? = null,
            recommendedTrainingType: String? = null,
            scoreMargin: ScoreMargin? = null,
            reasons: List<ShadowReason>,
            limitations: List<String>,
            scoreBreakdown: ScoreBreakdown? = null,
        ) = ShadowRecommendation(
            config.advisorVersion,
            config.policyId,
            context.careerToken,
            context.seq,
            context.turn,
            status,
            recommendedAction,
            recommendedTrainingType,
            scoreMargin,
            reasons,
            limitations,
            scoreBreakdown,
        )

        val underLimit = scored.filter { !it.overLimit }
        val allOverLimit = underLimit.isEmpty()
        if (allOverLimit && !config.allowOverLimitLeastRisk) {
            return rec(
                RecommendationStatus.INSUFFICIENT_EVIDENCE,
                reasons =
                    listOf(
                        ShadowReason(ShadowReasonCode.FAILURE_RISK_ABOVE_THRESHOLD, "every candidate exceeds the failChance limit ${n(config.failChanceHardLimit)}; no supported training pick"),
                    ),
                limitations = emptyList(),
            )
        }
        val pool = if (allOverLimit) scored else underLimit
        val comparator = if (allOverLimit) compareLeastRisk(config.trainingTieBreakOrder) else compareScored(config.trainingTieBreakOrder)
        val winner = pool.sortedWith(comparator).first()

        // Best alternative for the margin: the highest-total facility other than the winner, always via compareScored.
        val secondBest = scored.filter { it.id != winner.id }.sortedWith(compareScored(config.trainingTieBreakOrder)).firstOrNull() ?: winner
        val margin = winner.total - secondBest.total

        val reasons = ArrayList<ShadowReason>(2)
        if (allOverLimit) {
            reasons.add(
                ShadowReason(
                    ShadowReasonCode.FAILURE_RISK_ABOVE_THRESHOLD,
                    "all candidates exceed the failChance limit ${n(config.failChanceHardLimit)}; selected least-risk ${winner.id} at failChance ${n(winner.failChance)}",
                ),
            )
        } else if (margin >= 0) {
            reasons.add(ShadowReason(ShadowReasonCode.TRAINING_SCORE_HIGHER, "${winner.id} advisor score ${r1(winner.total)} exceeded ${secondBest.id} ${r1(secondBest.total)} by ${r1(margin)}"))
        } else {
            reasons.add(
                ShadowReason(
                    ShadowReasonCode.TRAINING_ALTERNATIVE_EXCLUDED_BY_FAILURE_RISK,
                    "${secondBest.id} advisor score ${r1(
                        secondBest.total,
                    )} exceeded ${winner.id} ${r1(winner.total)} but its failChance ${n(secondBest.failChance)} exceeded advisor limit ${n(config.failChanceHardLimit)}",
                ),
            )
        }
        if (winner.failChance < secondBest.failChance) {
            reasons.add(ShadowReason(ShadowReasonCode.FAILURE_RISK_LOWER, "${winner.id} failChance ${n(winner.failChance)} below ${secondBest.id} ${n(secondBest.failChance)}"))
        }

        val scoreBreakdown = ScoreBreakdown(winner.weightedGain, winner.failurePenalty, winner.total, winner.perStat)
        return rec(
            RecommendationStatus.RECOMMENDATION_AVAILABLE,
            recommendedAction = AdvisorAction.TRAIN,
            recommendedTrainingType = winner.id,
            scoreMargin = ScoreMargin(winner.total - secondBest.total, secondBest.id),
            reasons = reasons,
            limitations = OFFLINE_TRAINING_LIMITATIONS,
            scoreBreakdown = scoreBreakdown,
        )
    }
}
