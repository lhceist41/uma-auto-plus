package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.CustomImageUtils.RaceDetails

/**
 * Pure fan-efficient ranking for a Grand Concert forced fan-pressure extra race. It answers only
 * "given that racing is already mandatory this turn, which enterable race clears the fan requirement
 * fastest", never "should we race or train" - that decision is unchanged and still fail-closed.
 *
 * Two things differ from the legacy standard-racing pick, and only inside pure Grand Concert fan
 * pressure:
 *   1. Rival status is demoted from an absolute override to an exact-tie breaker. Under a plain fan
 *      deficit a Rival race carries no survival benefit, so letting a Rival beat a much larger
 *      same-tier non-Rival race wastes forced-race turns.
 *   2. An unknown (OCR-failed, `fans < 0`) fan value never outranks a known one within a tier, and
 *      when every candidate's fan value is unknown the caller is told to keep its legacy selection
 *      (this is an optimization, never a reason to abort an otherwise necessary race).
 *
 * Prediction tier stays the primary safety priority (DOUBLE over SINGLE over NONE) exactly as the
 * legacy selector, because a weaker predicted placement can scale the realized payout down more than
 * a larger base makes up. No expected-placement formula is introduced.
 *
 * This object knows nothing about [Game], bitmaps, taps, or scrolling. It ranks an already-collected
 * candidate set; the caller is responsible for how that set was gathered and for re-detecting the
 * winner before tapping.
 */
object GrandConcertFanRaceSelector {
    /**
     * Whether the fan-efficient ranking applies to the current forced extra race. Pure fan pressure
     * only: an independent trophy or goal-race-points requirement keeps the legacy selection, so
     * "maximize fans" can never override another requirement in a mixed-requirement state.
     *
     * @param scenarioIsGrandConcert whether the active scenario is Grand Concert.
     * @param fanPressureActive whether a fan requirement/emergency is forcing this extra race.
     * @param hasTrophyRequirement whether a trophy (G1) requirement is active.
     * @param hasInsufficientGoalRacePtsRequirement whether a goal-race-points shortfall is active.
     */
    fun appliesToForcedRace(
        scenarioIsGrandConcert: Boolean,
        fanPressureActive: Boolean,
        hasTrophyRequirement: Boolean,
        hasInsufficientGoalRacePtsRequirement: Boolean,
    ): Boolean =
        scenarioIsGrandConcert &&
            fanPressureActive &&
            !hasTrophyRequirement &&
            !hasInsufficientGoalRacePtsRequirement

    /** The chosen candidate index, a short reason, and whether the caller should fall back to its
     * legacy selection instead (because no candidate carried a usable fan signal). */
    data class Selection(val index: Int, val reason: String, val useLegacyFallback: Boolean)

    /**
     * Picks the most fan-efficient candidate among already-collected enterable rows. Highest
     * prediction tier first; within the top tier the highest KNOWN displayed fan value; Rival breaks
     * only an exact (tier, fans) tie; earliest index breaks any remaining tie. Returns a
     * legacy-fallback signal (index -1) when every candidate's fan value is unknown.
     *
     * @param candidates the enterable rows already walked and read, in list order.
     */
    fun select(candidates: List<RaceDetails>): Selection {
        if (candidates.isEmpty()) return Selection(-1, "no-candidates", useLegacyFallback = false)
        if (candidates.all { it.fans < 0 }) return Selection(-1, "all-fan-values-unknown", useLegacyFallback = true)
        var best = 0
        for (i in 1 until candidates.size) {
            if (isBetter(candidates[i], candidates[best])) best = i
        }
        return Selection(best, "tier-then-fans-rival-tiebreak", useLegacyFallback = false)
    }

    /** True when [a] is a strictly better fan-efficient pick than the current best [b]. */
    private fun isBetter(a: RaceDetails, b: RaceDetails): Boolean {
        if (a.predictionTier != b.predictionTier) return a.predictionTier > b.predictionTier
        val aKnown = a.fans >= 0
        val bKnown = b.fans >= 0
        if (aKnown != bKnown) return aKnown // a known fan value beats an unknown one within the same tier
        if (a.fans != b.fans) return a.fans > b.fans
        return a.isRival && !b.isRival // Rival breaks an otherwise exact tier+fans tie
    }

    /**
     * Compact `[GC_FAN_RACE_SELECT]` telemetry for one selection over a candidate set. [scanScope]
     * records how the set was gathered (currently the already-visible page only; a full-list scan is
     * not yet wired), so a later reader can tell whether below-the-fold candidates were considered.
     */
    fun telemetryLine(turn: Int, candidates: List<RaceDetails>, selection: Selection, scanScope: String): String {
        val summary =
            candidates.joinToString(" ") { row ->
                val fansText = if (row.fans < 0) "?" else "${row.fans}"
                "$fansText/${row.predictionTier}${if (row.isRival) "/R" else ""}"
            }
        val winner = selection.index.takeIf { it >= 0 }?.let { candidates[it] }
        val winnerFans = winner?.fans?.takeIf { it >= 0 }?.toString() ?: "unknown"
        return "[GRAND_CONCERT] [GC_FAN_RACE_SELECT] turn=$turn scope=$scanScope candidates=${candidates.size} " +
            "[$summary] winnerIdx=${selection.index} winnerFans=$winnerFans winnerTier=${winner?.predictionTier ?: "none"} " +
            "winnerRival=${winner?.isRival ?: false} reason=${selection.reason} legacyFallback=${selection.useLegacyFallback}"
    }
}
