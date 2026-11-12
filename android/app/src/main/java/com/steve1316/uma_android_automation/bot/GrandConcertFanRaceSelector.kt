package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.Aptitude

/**
 * Fan-first ranking for a Grand Concert forced fan-pressure extra race: which visible race clears the fan requirement
 * fastest, given that racing is already mandatory. Ranks an already-collected candidate set; no row prediction star is
 * used because GC race rows draw no finish-prediction mark and the yellow aptitude star false-matches the one-star template.
 *
 * Order: a known fan value beats an unknown one; among known rows the better aptitude tier wins even at a lower face
 * value (S, A and B are one tier, then C, D, E, F, G, then an untrusted row), since an off-aptitude race finishes near
 * the back and realizes almost nothing; then larger fans, then Rival, then earliest row. When every fan value is
 * unknown, index 0 is chosen so a required fan race is never skipped over an OCR miss. Aptitude is a preference, never
 * a hard filter.
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
     * [worstAptitude] is the worse of the trainee's surface and distance aptitude for the race, null when identity is
     * untrusted.
     */
    data class Candidate(val fans: Int?, val worstAptitude: Aptitude?, val isRival: Boolean)

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

    /** B or better finishes well enough that only fans separate it; below B every grade step costs placement. Untrusted is lowest. */
    private fun tier(aptitude: Aptitude?): Int = aptitude?.let { minOf(it, Aptitude.B).ordinal } ?: -1

    private fun isBetter(a: Candidate, b: Candidate): Boolean {
        val aKnown = a.fans != null
        val bKnown = b.fans != null
        if (aKnown != bKnown) return aKnown // a known fan value always beats an unknown one (before aptitude)
        if (aKnown && bKnown) {
            val aTier = tier(a.worstAptitude)
            val bTier = tier(b.worstAptitude)
            if (aTier != bTier) return aTier > bTier
            if (a.fans != b.fans) return a.fans!! > b.fans!!
        }
        return a.isRival && !b.isRival
    }

    /** `aptitude-first` when the winner beat a strictly-higher-face known row of a worse tier, so the override is auditable from the log. */
    private fun reasonFor(candidates: List<Candidate>, winner: Int): String {
        val w = candidates[winner]
        if (w.fans == null) return "all-fan-values-unknown-required-race-fallback"
        val winnerTier = tier(w.worstAptitude)
        val aptitudeOverrodeFans = candidates.any { it.fans != null && tier(it.worstAptitude) < winnerTier && it.fans!! > w.fans!! }
        return if (aptitudeOverrodeFans) "aptitude-first" else "higher-fans"
    }

    /** Compact `[GC_FAN_RACE_SELECT]` telemetry. [scanScope] records how the set was gathered (visible page only). */
    fun telemetryLine(turn: Int, candidates: List<Candidate>, selection: Selection, scanScope: String): String {
        val summary =
            candidates.joinToString(" ") { row ->
                val fansText = row.fans?.toString() ?: "?"
                "$fansText/apt${aptitudeText(row.worstAptitude)}${if (row.isRival) "/R" else ""}"
            }
        val winner = selection.index.takeIf { it >= 0 }?.let { candidates[it] }
        val winnerFans = winner?.fans?.toString() ?: "unknown"
        val unknownFallback = selection.reason == "all-fan-values-unknown-required-race-fallback"
        return "[GRAND_CONCERT] [GC_FAN_RACE_SELECT] turn=$turn scope=$scanScope candidates=${candidates.size} " +
            "[$summary] winnerIdx=${selection.index} winnerFans=$winnerFans winnerApt=${aptitudeText(winner?.worstAptitude)} " +
            "winnerRival=${winner?.isRival ?: false} reason=${selection.reason} unknownFanFallback=$unknownFallback tierIgnored=true"
    }

    private fun aptitudeText(aptitude: Aptitude?): String = aptitude?.name ?: "?"
}
