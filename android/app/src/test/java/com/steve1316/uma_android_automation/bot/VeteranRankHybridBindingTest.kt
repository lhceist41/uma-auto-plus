package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.utils.RosterCardRatingRead
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VeteranRankHybridBindingTest {
    private fun entry(
        rating: Int?,
        rank: String? = null,
        family: String = "B",
        character: String = "Taiki Shuttle",
        stats: List<Int?> = listOf(949, 699, 648, 687, 420),
    ) = RosterEntryObservation(
        character, "Wild Frontier", rank, rating, stats, List(5) { "A" },
        listOf("A", "B", "A", "A", "E", "G", "C", "A", "E", "G"),
        "unknown", diagnostics = RosterEntryDiagnostics(rankFamily = family, visualRank = rank),
    )

    private fun readings(vararg values: String): List<RosterCardRatingRead> =
        values.mapIndexed { i, raw -> RosterCardRatingRead(i, raw, com.steve1316.uma_android_automation.utils.parseRating(raw)) }

    private fun finalize(
        entries: List<RosterEntryObservation>,
        cards: List<RosterCardRatingRead>,
        termination: RosterScanTermination = RosterScanTermination.CYCLE_CLOSED,
        initial: RosterListState = RosterListState(entries.size, 260, true, "Rating", "Asc"),
        final: RosterListState? = initial,
        viewport: Boolean = true,
        transitions: Boolean = true,
        limit: Int = 0,
    ): List<RosterEntryObservation> = finalizeHybridRanks(
        initial, final, termination, limit, entries.mapIndexed { i, e -> i.toLong() to e }, cards, viewport, transitions,
    ).map { it.second }

    @Test
    fun `a unique bound E or B rating resolves only after complete traversal`() {
        for ((rating, family, expected) in listOf(Triple(1904, "E", "E+"), Triple(8543, "B", "B+"), Triple(8774, "B", "B+"), Triple(9335, "B", "B+"), Triple(9359, "B", "B+"), Triple(9470, "B", "B+"), Triple(9497, "B", "B+"), Triple(9653, "B", "B+"))) {
            val rows = listOf(entry(rating, family = family), entry(12000, rank = "A", family = "A"))
            val result = finalize(rows, readings(rating.toString()))
            assertEquals(expected, result[0].rank, "$rating")
            assertEquals("accepted", result[0].diagnostics?.bindingStatus)
            assertNotNull(entryFingerprint(result[0]))
            assertNull(finalize(rows, readings(rating.toString()), termination = RosterScanTermination.COUNT_REACHED)[0].rank)
        }
    }

    @Test
    fun `malformed list evidence is local and later candidates still bind`() {
        val rows = listOf(entry(1300, family = "E"), entry(8543), entry(9335))
        val result = finalize(rows, readings("1300", "8O43", "9335"))
        assertEquals("E", result[0].rank)
        assertNull(result[1].rank)
        assertEquals("list_parse_failed", result[1].diagnostics?.bindingRejectReason)
        assertEquals("B+", result[2].rank)
        assertEquals("anchor_unproven", finalize(rows, readings("", "8543", "9335"))[2].diagnostics?.bindingRejectReason)
    }

    @Test
    fun `card 24 may bind but card 25 has no measured list evidence`() {
        val rows = (0..25).map { entry(1300 + it, family = "E") }
        val cards = (0..24).map { RosterCardRatingRead(it, (1300 + it).toString(), 1300 + it) }
        val result = finalize(rows, cards)
        assertEquals("E", result[24].rank)
        assertNull(result[25].rank)
        assertEquals("list_evidence_missing", result[25].diagnostics?.bindingRejectReason)
    }

    @Test
    fun `rating disagreement and duplicate rating reject the affected candidates`() {
        val base = listOf(entry(1300, family = "E"), entry(8543), entry(9335))
        val mismatch = finalize(base, readings("1300", "9335", "9335"))
        assertEquals("list_details_mismatch", mismatch[1].diagnostics?.bindingRejectReason)
        assertEquals("B+", mismatch[2].rank)

        val duplicate = base + entry(9335, character = "Copano Rickey")
        val result = finalize(duplicate, readings("1300", "8543", "9335", "9335"))
        assertEquals("B+", result[1].rank)
        for (index in 2..3) {
            assertNull(result[index].rank)
            assertEquals("duplicate_rating", result[index].diagnostics?.bindingRejectReason)
        }
        val exactDuplicate = base + base[2]
        assertEquals("identity_census_unproven", finalize(exactDuplicate, readings("1300", "8543", "9335", "9335"))[2].diagnostics?.bindingRejectReason)
    }

    @Test
    fun `anchor terminal transition and final list proof are all required`() {
        val rows = listOf(entry(1300, family = "E"), entry(9335))
        val cards = readings("1300", "9335")
        assertEquals("anchor_unproven", finalize(rows, readings("1904", "9335"))[1].diagnostics?.bindingRejectReason)
        assertEquals("transition_unproven", finalize(rows, cards, transitions = false)[1].diagnostics?.bindingRejectReason)
        assertEquals("terminal_unproven", finalize(rows, cards, termination = RosterScanTermination.COUNT_REACHED)[1].diagnostics?.bindingRejectReason)
        assertEquals("terminal_unproven", finalize(rows, cards, limit = 2)[1].diagnostics?.bindingRejectReason)
        assertEquals("final_state_mismatch", finalize(rows, cards, final = RosterListState(2, 260, true, "Rating", "Desc"))[1].diagnostics?.bindingRejectReason)
        assertEquals("details_census_incomplete", finalize(rows + entry(null), cards)[1].diagnostics?.bindingRejectReason)
        assertEquals("not_attempted", finalize(rows, cards, initial = RosterListState(2, 260, true, "Name", "Asc"))[1].diagnostics?.bindingStatus)
        assertEquals("not_attempted", finalize(rows, cards, viewport = false)[1].diagnostics?.bindingStatus)
    }

    @Test
    fun `unsupported interval and family conflict never mint a rank`() {
        val rows = listOf(entry(1300, family = "E"), entry(2300), entry(8543, family = "E"))
        val result = finalize(rows, readings("1300", "2300", "8543"))
        assertEquals("unsupported_interval", result[1].diagnostics?.bindingRejectReason)
        assertEquals("family_mismatch", result[2].diagnostics?.bindingRejectReason)
    }

    @Test
    fun `rank free identity ignores diagnostics and rank but rejects missing fields`() {
        val one = entry(9335, family = "B")
        assertEquals(rankFreeRosterIdentity(one), rankFreeRosterIdentity(one.copy(rank = "B+", diagnostics = null)))
        assertNull(rankFreeRosterIdentity(one.copy(stats = listOf(949, null, 648, 687, 420))))
        assertFalse(detailsTransitionProven(one, one.copy(diagnostics = null)))
        assertTrue(detailsTransitionProven(one, one.copy(rating = 9336)))
    }

    @Test
    fun `a new Details member needs two agreeing reads after one chevron tap`() {
        val before = entry(9335)
        val next = entry(9359)
        assertTrue(settledDetailsTransition(before, next, next.copy(diagnostics = null)))
        assertFalse(settledDetailsTransition(before, before, next))
        assertFalse(settledDetailsTransition(before, next, next.copy(rating = 9470)))
        val partial = next.copy(rating = null)
        assertTrue(settledDetailsTransition(before, next.copy(stats = listOf(949, null, 648, 687, 420)), partial.copy(rating = 9359)))
    }

    private fun roster(rows: List<RosterEntryObservation>): AssembledRosterScan = assembleRosterScan(
        "fresh", 1L, 2L, RosterListState(rows.size, 260, true, "Rating", "Asc"), 0,
        rows.mapIndexed { i, row -> i.toLong() to row }, RosterScanTermination.CYCLE_CLOSED,
        "test", 1080, 1920,
    )

    @Test
    fun `filtered exact tuple reuses bound rank and reproduces fingerprint`() {
        val bound = entry(9335, rank = "B+", family = "B")
        val scan = roster(listOf(bound))
        assertTrue(scan.header.trustedForRetention)
        val filtered = bound.copy(rank = null, diagnostics = RosterEntryDiagnostics(rankFamily = "B"))
        assertEquals(scan.entries.single().rosterFingerprint, boundFilteredFingerprint(filtered, scan))
        assertNull(boundFilteredFingerprint(filtered.copy(stats = listOf(949, null, 648, 687, 420)), scan))
        assertNull(boundFilteredFingerprint(filtered.copy(aptitudes = filtered.aptitudes.mapIndexed { i, value -> if (i == 4) null else value }), scan))
        assertNull(boundFilteredFingerprint(filtered.copy(rating = 9336), scan))
        assertNull(boundFilteredFingerprint(filtered.copy(diagnostics = RosterEntryDiagnostics(rankFamily = "E")), scan))
        assertNull(boundFilteredFingerprint(filtered, scan.copy(entries = listOf(scan.entries.single().copy(rosterFingerprint = "0".repeat(32))))))
    }

    @Test
    fun `ambiguous rank free join and visual A conflict reject filtered member`() {
        val lower = entry(9335, rank = "B+", family = "B")
        val duplicate = lower.copy(rank = "B")
        assertNull(boundFilteredFingerprint(lower.copy(rank = null), roster(listOf(lower, duplicate))))

        val visual = entry(10192, rank = "A", family = "A")
        val scan = roster(listOf(visual))
        assertEquals(scan.entries.single().rosterFingerprint, boundFilteredFingerprint(visual, scan))
        assertNull(boundFilteredFingerprint(visual.copy(rank = "A+", diagnostics = RosterEntryDiagnostics(rankFamily = "A", visualRank = "A+")), scan))
    }

    @Test
    fun `identity join can bind a member beyond the visible 25 cards`() {
        val rows = (0..30).map { entry(8200 + it, rank = "B+", family = "B") }
        val scan = roster(rows)
        val filtered = rows[30].copy(rank = null, diagnostics = RosterEntryDiagnostics(rankFamily = "B"))
        assertEquals(scan.entries[30].rosterFingerprint, boundFilteredFingerprint(filtered, scan))
    }
}
