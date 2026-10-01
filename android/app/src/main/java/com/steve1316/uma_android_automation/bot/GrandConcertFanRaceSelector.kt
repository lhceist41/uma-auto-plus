package com.steve1316.uma_android_automation.bot

/**
 * Fan-first ranking for a Grand Concert forced fan-pressure extra race: which visible race clears the fan requirement
 * fastest, given that racing is already mandatory. Ranks an already-collected candidate set; no row prediction star is
 * used because GC race rows draw no finish-prediction mark and the yellow aptitude star false-matches the one-star template.
 *
 * Order: a known fan value beats an unknown one; among known rows an aptitude-compatible race beats a not-compatible
 * one even at a lower face value (an incompatible high-face race finishes near the back and realizes almost nothing);
 * then larger fans, then Rival, then earliest row. When no known row is compatible this is plain fans-first. When
 * every fan value is unknown, index 0 is chosen so a required fan race is never skipped over an OCR miss.
 * Aptitude is a preference, never a hard filter.
 */
object GrandConcertFanRaceSelector {
    /** Pure fan pressure only: a trophy or goal-race-points requirement keeps the generic selection so "maximize fans" never overrides it. */
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

    /**
     * One visible row's truthful inputs. [fans] is null unless the row resolves to a unique exact DB race.
     * [aptitudeCompatible] is true when surface AND distance aptitude are at least B, null when identity is untrusted
     * (null and false both rank as not-compatible).
     */
    data class Candidate(val fans: Int?, val aptitudeCompatible: Boolean?, val isRival: Boolean)

    data class Selection(val index: Int, val reason: String)

    fun select(candidates: List<Candidate>): Selection {
        if (candidates.isEmpty()) return Selection(-1, "no-candidates")
        if (candidates.all { it.fans == null }) return Selection(0, "all-fan-values-unknown-required-race-fallback")
        var best = 0
        for (i in 1 until candidates.size) {
            if (isBetter(candidates[i], candidates[best])) best = i
        }
        return Selection(best, reasonFor(candidates, best))
    }

    private fun isBetter(a: Candidate, b: Candidate): Boolean {
        val aKnown = a.fans != null
        val bKnown = b.fans != null
        if (aKnown != bKnown) return aKnown // a known fan value always beats an unknown one (before aptitude)
        if (aKnown && bKnown) {
            // An aptitude-compatible race outranks a not-compatible one even at a lower face value (an incompatible
            // high-face race finishes near the back and realizes almost nothing).
            val aApt = a.aptitudeCompatible == true
            val bApt = b.aptitudeCompatible == true
            if (aApt != bApt) return aApt
            if (a.fans != b.fans) return a.fans!! > b.fans!!
        }
        return a.isRival && !b.isRival
    }

    /** `aptitude-first` when a compatible row beat a strictly-higher-face not-compatible known row, so the override is auditable from the log. */
    private fun reasonFor(candidates: List<Candidate>, winner: Int): String {
        val w = candidates[winner]
        if (w.fans == null) return "all-fan-values-unknown-required-race-fallback"
        val winnerCompatible = w.aptitudeCompatible == true
        val aptitudeOverrodeFans =
            winnerCompatible &&
                candidates.any { it.fans != null && it.aptitudeCompatible != true && it.fans!! > w.fans!! }
        return if (aptitudeOverrodeFans) "aptitude-first" else "higher-fans"
    }

    /** Compact `[GC_FAN_RACE_SELECT]` telemetry. [scanScope] records how the set was gathered (visible page only). */
    fun telemetryLine(turn: Int, candidates: List<Candidate>, selection: Selection, scanScope: String): String {
        val summary =
            candidates.joinToString(" ") { row ->
                val fansText = row.fans?.toString() ?: "?"
                val aptText = aptitudeState(row.aptitudeCompatible)
                "$fansText/apt$aptText${if (row.isRival) "/R" else ""}"
            }
        val winner = selection.index.takeIf { it >= 0 }?.let { candidates[it] }
        val winnerFans = winner?.fans?.toString() ?: "unknown"
        val winnerApt = aptitudeState(winner?.aptitudeCompatible)
        val unknownFallback = selection.reason == "all-fan-values-unknown-required-race-fallback"
        return "[GRAND_CONCERT] [GC_FAN_RACE_SELECT] turn=$turn scope=$scanScope candidates=${candidates.size} " +
            "[$summary] winnerIdx=${selection.index} winnerFans=$winnerFans winnerApt=$winnerApt " +
            "winnerRival=${winner?.isRival ?: false} reason=${selection.reason} unknownFanFallback=$unknownFallback tierIgnored=true"
    }

    private fun aptitudeState(aptitudeCompatible: Boolean?): String =
        when (aptitudeCompatible) {
            null -> "?"
            true -> "Y"
            false -> "N"
        }
}
