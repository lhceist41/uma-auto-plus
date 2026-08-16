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
 * The contract is known-first, aptitude-first, fans-second, fail-open:
 *   1. a known fan value always beats an unknown one (trusted identity is load-bearing);
 *   2. among two known-fan rows an aptitude-compatible race outranks a not-compatible one, even at a
 *      lower face value: an incompatible high-face race finishes near the back and realizes almost
 *      nothing, while a compatible lower-face race realizes far more;
 *   3. within one aptitude class the larger known fan value wins;
 *   4. Rival only breaks a remaining exact tie;
 *   5. the earliest row breaks any final tie;
 *   6. if every visible row's fan value is unknown, a deterministic row (index 0) is still chosen so a
 *      required fan race is never skipped just because OCR could not identify any race; aptitude is
 *      not consulted in that all-unknown fallback (no trusted identity means no trusted aptitude).
 *
 * Fail-open: when no known-fan row is aptitude-compatible, ranking is exactly the prior fans-first
 * behavior (larger known fans, then Rival, then earliest), so an all-incompatible page is never made
 * worse by the aptitude preference.
 *
 * No placement probability, stat-vs-race formula, grade penalty, or expected-value model is introduced.
 * Aptitude is a boolean preference that reorders known-fan rows; it is never a hard filter (an
 * incompatible race is still chosen when it is the only or best trusted option).
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
     *    untrusted/unknown. Primary rank among known-fan rows (compatible outranks not-compatible),
     *    ahead of face value; a null/false pair is not distinguished (both are not-compatible).
     * @property isRival whether the row is a Rival race. A tie-break only.
     */
    data class Candidate(val fans: Int?, val aptitudeCompatible: Boolean?, val isRival: Boolean)

    /** The chosen candidate index and a short reason. Index -1 means no candidates were supplied. */
    data class Selection(val index: Int, val reason: String)

    /**
     * Picks the best visible candidate. Known fan value first; among known rows an aptitude-compatible
     * race outranks a not-compatible one (even at a lower face value), then higher face value within an
     * aptitude class; Rival breaks a remaining exact tie; earliest index breaks any final tie. When
     * every candidate's fan value is unknown, index 0 is still chosen (a required fan race must not be
     * skipped over an OCR miss).
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
        return Selection(best, reasonFor(candidates, best))
    }

    /** True when [a] is a strictly better known-first, aptitude-first, fans-second pick than [b]. */
    private fun isBetter(a: Candidate, b: Candidate): Boolean {
        val aKnown = a.fans != null
        val bKnown = b.fans != null
        if (aKnown != bKnown) return aKnown // a known fan value always beats an unknown one (before aptitude)
        if (aKnown && bKnown) {
            // Among known-fan rows, an aptitude-compatible race outranks a not-compatible one even at a
            // lower face value: an incompatible high-face race finishes near the back and realizes almost
            // nothing, while a compatible lower-face race realizes far more. Face value only decides
            // within one aptitude class.
            val aApt = a.aptitudeCompatible == true
            val bApt = b.aptitudeCompatible == true
            if (aApt != bApt) return aApt
            if (a.fans != b.fans) return a.fans!! > b.fans!!
        }
        // Both unknown, or an exact tie within the same aptitude class: Rival, then earliest index.
        return a.isRival && !b.isRival
    }

    /**
     * Names why the winner won so an aptitude-over-fans override is auditable from the log:
     * `aptitude-first` when a compatible row beat a strictly-higher-face not-compatible known row (the
     * lever this ranking added); otherwise `higher-fans` (the winner led its aptitude class on face
     * value, or on a Rival/earliest tie-break). The all-unknown case keeps its own fallback reason.
     */
    private fun reasonFor(candidates: List<Candidate>, winner: Int): String {
        val w = candidates[winner]
        if (w.fans == null) return "all-fan-values-unknown-required-race-fallback"
        val winnerCompatible = w.aptitudeCompatible == true
        val aptitudeOverrodeFans =
            winnerCompatible &&
                candidates.any { it.fans != null && it.aptitudeCompatible != true && it.fans!! > w.fans!! }
        return if (aptitudeOverrodeFans) "aptitude-first" else "higher-fans"
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
