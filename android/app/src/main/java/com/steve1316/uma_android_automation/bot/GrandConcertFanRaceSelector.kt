package com.steve1316.uma_android_automation.bot

/**
 * Pure fan-first ranking for a Grand Concert forced fan-pressure extra race. It answers only "given
 * that racing is already mandatory this turn, which visible race clears the fan requirement fastest",
 * never "should we race or train" - that decision is unchanged and still fail-closed.
 *
 * Grand Concert forced racing does NOT use the row prediction star as a safety gate. On a live GC race
 * list the rows draw no finish-prediction mark, and the yellow distance-aptitude star false-matches the
 * single-star template - so a row-star tier is neither present nor trustworthy here. The row-star model
 * remains valid in non-GC contexts (Trackblazer, generic Standard Racing); this selector is scoped to
 * pure GC fan pressure only.
 *
 * The contract is fans-first with a soft aptitude preference, fail-open:
 *   1. a known fan value always beats an unknown one;
 *   2. among known fan values the larger wins;
 *   3. aptitude compatibility only breaks an exact fan tie (never overrides a larger fan value);
 *   4. Rival only breaks a remaining exact tie;
 *   5. the earliest row breaks any final tie;
 *   6. if every visible row's fan value is unknown, a deterministic row (index 0) is still chosen so a
 *      required fan race is never skipped just because OCR could not identify any race.
 *
 * No placement probability, stat-vs-race formula, grade penalty, or expected-value model is introduced.
 * Aptitude is a boolean soft preference only, never a hard filter.
 *
 * This object knows nothing about [Game], bitmaps, taps, or scrolling. It ranks an already-collected
 * candidate set; the caller gathers that set from the universal fans-row anchors and re-detects the
 * winner geometry before tapping.
 */
object GrandConcertFanRaceSelector {
    /**
     * Whether the fan-first ranking applies to the current forced extra race. Pure fan pressure only:
     * an independent trophy or goal-race-points requirement keeps the legacy generic selection, so
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

    /**
     * One visible GC race row's truthful inputs. No prediction tier is carried: GC selection ignores
     * the row star entirely.
     *
     * @property fans the trusted DB fan value when the row resolves to a unique exact DB race; null when
     *    the DB identity is untrusted (fuzzy, ambiguous, or unresolved).
     * @property aptitudeCompatible true when the exact resolved race has surface AND distance aptitude
     *    at least B; false when the exact race is known and fails that; null when the identity is
     *    untrusted/unknown. A tie-break only.
     * @property isRival whether the row is a Rival race. A tie-break only.
     */
    data class Candidate(val fans: Int?, val aptitudeCompatible: Boolean?, val isRival: Boolean)

    /** The chosen candidate index and a short reason. Index -1 means no candidates were supplied. */
    data class Selection(val index: Int, val reason: String)

    /**
     * Picks the most fan-efficient visible candidate. Highest KNOWN fan value first; aptitude
     * compatibility breaks an exact fan tie; Rival breaks a remaining tie; earliest index breaks any
     * final tie. When every candidate's fan value is unknown, index 0 is still chosen (a required fan
     * race must not be skipped over an OCR miss).
     *
     * @param candidates the visible rows already enumerated and resolved, in list order.
     */
    fun select(candidates: List<Candidate>): Selection {
        if (candidates.isEmpty()) return Selection(-1, "no-candidates")
        if (candidates.all { it.fans == null }) return Selection(0, "all-fan-values-unknown-required-race-fallback")
        var best = 0
        for (i in 1 until candidates.size) {
            if (isBetter(candidates[i], candidates[best])) best = i
        }
        return Selection(best, "fans-first-aptitude-tiebreak")
    }

    /** True when [a] is a strictly better fan-first pick than the current best [b]. */
    private fun isBetter(a: Candidate, b: Candidate): Boolean {
        val aKnown = a.fans != null
        val bKnown = b.fans != null
        if (aKnown != bKnown) return aKnown // a known fan value always beats an unknown one
        if (aKnown && bKnown && a.fans != b.fans) return a.fans!! > b.fans!! // larger known fan value wins
        // Exact fan tie (or both unknown): soft aptitude preference, then Rival, then earliest index.
        val aApt = a.aptitudeCompatible == true
        val bApt = b.aptitudeCompatible == true
        if (aApt != bApt) return aApt
        return a.isRival && !b.isRival
    }

    /**
     * Compact `[GC_FAN_RACE_SELECT]` telemetry for one selection. Reports the truthful inputs (fans and
     * aptitude state per row) with no tier or placement claim, so a later reader can audit the decision.
     * [scanScope] records how the set was gathered (currently the visible page only; a full-list scan is
     * not wired).
     */
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

    /** Compact aptitude state for telemetry: Y (compatible), N (incompatible), ? (unknown identity). */
    private fun aptitudeState(aptitudeCompatible: Boolean?): String =
        when (aptitudeCompatible) {
            null -> "?"
            true -> "Y"
            false -> "N"
        }
}
