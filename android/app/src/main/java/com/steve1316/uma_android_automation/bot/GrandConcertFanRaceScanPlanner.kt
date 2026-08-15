package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.PredictionTier
import com.steve1316.uma_android_automation.utils.CustomImageUtils.RaceDetails

/**
 * Pure, inert groundwork for a future Grand Concert below-the-fold fan-race scan. It models the
 * candidates a non-committing name-only list scan would collect (one detected row per page), applies
 * the fail-closed DB-identity trust policy, deduplicates overlapping pages by trusted identity, and
 * ranks the union with the already-landed [GrandConcertFanRaceSelector] contract. It knows nothing of
 * [Game], bitmaps, taps, or scrolling, and is NOT wired into any production path - the live selection
 * still uses the visible-page selector only.
 *
 * DB-identity trust policy (proven offline over all 402 committed races): the runtime lookup keys a
 * row on `(turnNumber, nameFormatted)` - the OCR'd track/distance/direction label - not the race
 * title. That key is unique for 376 races, but 13 turns carry two different races under one formatted
 * name (e.g. Oka Sho 10500 vs Arlington Cup 3800), and fuzzy matching over those structured strings
 * has an almost-zero same-turn margin (a one-digit "1600m" vs "1800m" slip scores ~0.99). So a DB fan
 * value is trusted ONLY for a UNIQUE EXACT resolution; a multi-match or any fuzzy match carries
 * `dbFans = unknown` and never outranks a trusted row. An untrusted row stays enterable; it just
 * contributes no DB fan signal, and the caller falls back to the landed visible-page ranking.
 */
object GrandConcertFanRaceScanPlanner {
    /** The resolution tier of one row against the turn-scoped race database. Mirrors the runtime
     * lookup's own tiers; kept local so the planner stays pure and independently testable. */
    enum class LookupTier { NONE, EXACT, FUZZY }

    /**
     * One row a non-committing first-pass scan would collect. [dbFans] is the fan value of a single
     * resolved match, if any; it is only ever USED through [trustedDbFans].
     */
    data class ScanCandidate(
        val detectedName: String,
        val canonicalName: String?,
        val lookupTier: LookupTier,
        val matchCount: Int,
        val dbFans: Int?,
        val predictionTier: PredictionTier,
        val isRival: Boolean,
        val pageOrdinal: Int,
    ) {
        /** True only when this row resolves to exactly one DB race by exact turn-scoped identity. */
        val isTrusted: Boolean get() = lookupTier == LookupTier.EXACT && matchCount == 1 && canonicalName != null

        /** DB fans is trusted ONLY for a unique exact resolution; a multi-match (a same-turn formatted
         * -name collision) or any fuzzy match is unknown so it can never mis-rank a row. */
        val trustedDbFans: Int? get() = if (isTrusted) dbFans else null
    }

    /** The stable cross-page identity of a trusted row on [turn]; null for untrusted rows, which are
     * never merged (dedup by coordinate is forbidden). */
    fun trustedIdentity(turn: Int, candidate: ScanCandidate): String? =
        if (candidate.isTrusted) "$turn|${candidate.canonicalName}" else null

    /** The planned winner over the deduplicated union, plus the completeness of the scan. A winner
     * from a scan whose [bottomProven] is false must not be presented as a full-list optimum. */
    data class Plan(
        val winnerIndex: Int,
        val reason: String,
        val bottomProven: Boolean,
        val useLegacyFallback: Boolean,
        val deduped: List<ScanCandidate>,
    )

    /**
     * Deduplicates a multi-page scan by trusted identity, then ranks the union using only trusted DB
     * fans through the landed [GrandConcertFanRaceSelector] contract (tier first, known fans within a
     * tier, Rival as an exact-tie breaker). Untrusted rows carry an unknown fan value and so never
     * outrank a trusted one.
     *
     * @param turn the current career turn (the DB lookup scope).
     * @param candidates the rows collected across all scanned pages, in scan order.
     * @param bottomProven whether the scan proved it reached the list bottom (vs a bounded/incomplete scan).
     */
    fun plan(turn: Int, candidates: List<ScanCandidate>, bottomProven: Boolean): Plan {
        val deduped = dedupe(turn, candidates)
        val forRanking =
            deduped.map { row ->
                RaceDetails(row.trustedDbFans ?: -1, row.predictionTier == PredictionTier.DOUBLE, row.isRival, row.predictionTier)
            }
        val selection = GrandConcertFanRaceSelector.select(forRanking)
        return Plan(selection.index, selection.reason, bottomProven, selection.useLegacyFallback, deduped)
    }

    /** Collapses rows that share a trusted identity across overlapping pages, keeping the first; every
     * untrusted/unresolved row is preserved separately (never merged). */
    private fun dedupe(turn: Int, candidates: List<ScanCandidate>): List<ScanCandidate> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<ScanCandidate>()
        for (candidate in candidates) {
            val id = trustedIdentity(turn, candidate)
            if (id != null) {
                if (id in seen) continue
                seen.add(id)
            }
            out.add(candidate)
        }
        return out
    }

    /**
     * Second-pass restoration guard: a re-detected row may be tapped only when it re-resolves to the
     * SAME trusted identity the first pass chose. A mismatch, or an untrusted re-resolution on either
     * side, rejects the tap so no stale/wrong coordinate is ever pressed.
     */
    fun restorationMatches(turn: Int, firstPassWinner: ScanCandidate, secondPassRow: ScanCandidate): Boolean {
        val first = trustedIdentity(turn, firstPassWinner) ?: return false
        val second = trustedIdentity(turn, secondPassRow) ?: return false
        return first == second
    }
}
