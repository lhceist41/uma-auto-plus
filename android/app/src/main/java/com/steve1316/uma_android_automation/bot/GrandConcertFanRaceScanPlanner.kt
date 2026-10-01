package com.steve1316.uma_android_automation.bot

/**
 * Inert groundwork for a future below-the-fold fan-race scan; not wired into any production path (live selection uses the visible-page selector only).
 * Lookup keys a row on `(turnNumber, nameFormatted)`: unique for 376 of 402 races, but 13 turns hold two races under one formatted name and
 * fuzzy matching has almost no same-turn margin (a one-digit "1600m" vs "1800m" slip scores ~0.99). So a DB fan value is trusted only for a
 * unique exact resolution; an untrusted row stays enterable but carries no DB fan signal.
 */
object GrandConcertFanRaceScanPlanner {
    enum class LookupTier { NONE, EXACT, FUZZY }

    data class ScanCandidate(
        val detectedName: String,
        val canonicalName: String?,
        val lookupTier: LookupTier,
        val matchCount: Int,
        val dbFans: Int?,
        val aptitudeCompatible: Boolean?,
        val isRival: Boolean,
        val pageOrdinal: Int,
    ) {
        val isTrusted: Boolean get() = lookupTier == LookupTier.EXACT && matchCount == 1 && canonicalName != null

        /** A multi-match or fuzzy match is unknown so it can never mis-rank a row. */
        val trustedDbFans: Int? get() = if (isTrusted) dbFans else null

        val trustedAptitudeCompatible: Boolean? get() = if (isTrusted) aptitudeCompatible else null
    }

    /** Null for untrusted rows, which are never merged (dedup by coordinate is forbidden). */
    fun trustedIdentity(turn: Int, candidate: ScanCandidate): String? =
        if (candidate.isTrusted) "$turn|${candidate.canonicalName}" else null

    /** A winner from a scan whose [bottomProven] is false must not be presented as a full-list optimum. */
    data class Plan(
        val winnerIndex: Int,
        val reason: String,
        val bottomProven: Boolean,
        val deduped: List<ScanCandidate>,
    )

    /** Untrusted rows carry an unknown fan value and never outrank a trusted one; an all-untrusted union still yields index 0 rather than aborting. */
    fun plan(turn: Int, candidates: List<ScanCandidate>, bottomProven: Boolean): Plan {
        val deduped = dedupe(turn, candidates)
        val forRanking =
            deduped.map { row ->
                GrandConcertFanRaceSelector.Candidate(row.trustedDbFans, row.trustedAptitudeCompatible, row.isRival)
            }
        val selection = GrandConcertFanRaceSelector.select(forRanking)
        return Plan(selection.index, selection.reason, bottomProven, deduped)
    }

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

    /** A re-detected row is tapped only if it re-resolves to the same trusted identity as the first pass, so no stale coordinate is pressed. */
    fun restorationMatches(turn: Int, firstPassWinner: ScanCandidate, secondPassRow: ScanCandidate): Boolean {
        val first = trustedIdentity(turn, firstPassWinner) ?: return false
        val second = trustedIdentity(turn, secondPassRow) ?: return false
        return first == second
    }
}
