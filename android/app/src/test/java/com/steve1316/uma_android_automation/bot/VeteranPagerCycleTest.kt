package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VeteranPagerCycleTest {
    private fun entry(rating: Int = 10192) = RosterEntryObservation(
        character = "Taiki Shuttle", outfit = "Wild Frontier", rank = "A", rating = rating,
        stats = listOf(949, 699, 648, 687, 420), statGrades = List(5) { "A" },
        aptitudes = List(10) { "A" }, favoriteState = "unknown",
    )

    @Test
    fun `initial one-card observation cannot complete without another pager acquisition`() {
        val stored = mutableListOf<RosterEntryObservation>()
        var advances = 0
        val outcome = walkRosterCycle(entry(), 1, 4, 0, visit = { _, e -> stored += e }, advance = { _, _ ->
            advances++
            null
        })
        assertEquals(RosterScanTermination.STALLED, outcome)
        assertEquals(1, advances)
        assertEquals(1, stored.size)
    }

    @Test
    fun `one-card roster and Inspiration traversal store one entry and settle an additional return`() {
        for (startIndex in listOf(0, 1)) {
            var captures = 0
            var advances = 0
            var settledReads = 0
            val anchor = entry()
            val outcome = walkRosterCycle(anchor, 1, 4, 0, startIndex, visit = { index, _ ->
                if (index >= startIndex) captures++
            }, advance = { previous, _ ->
                advances++
                val first = entry().also { settledReads++ }
                val second = entry().also { settledReads++ }
                if (settledRosterPagerRead(previous, first, second, 1)) second else null
            })
            assertEquals(RosterScanTermination.CYCLE_CLOSED, outcome)
            assertEquals(1, advances)
            assertEquals(2, settledReads)
            assertEquals(if (startIndex == 0) 1 else 0, captures)
        }
    }

    @Test
    fun `one-card settling rejects incomplete conflicting and different identities`() {
        val anchor = entry()
        assertFalse(settledRosterPagerRead(anchor, entry(10193), entry(10193), 1))
        assertFalse(settledRosterPagerRead(anchor, anchor, entry(10193), 1))
        assertFalse(settledRosterPagerRead(anchor, anchor.copy(rating = null), anchor, 1))
        assertFalse(settledRosterPagerRead(anchor, anchor, anchor, 2))
        assertTrue(settledRosterPagerRead(anchor, entry(10193), entry(10193), 2))
    }

    @Test
    fun `multi-card cycle needs distinct entries and one extra return to anchor`() {
        val reads = ArrayDeque(listOf(entry(10193), entry()))
        val indexes = mutableListOf<Int>()
        var advances = 0
        val outcome = walkRosterCycle(entry(), 2, 4, 0, visit = { index, _ -> indexes += index }, advance = { _, _ ->
            advances++
            reads.removeFirst()
        })
        assertEquals(RosterScanTermination.CYCLE_CLOSED, outcome)
        assertEquals(listOf(0, 1), indexes)
        assertEquals(2, advances)
    }

    @Test
    fun `bounded and early wrapped traversals cannot complete`() {
        var advances = 0
        assertEquals(RosterScanTermination.ENTRY_LIMIT_REACHED, walkRosterCycle(entry(), 1, 4, 1, visit = { _, _ -> }, advance = { _, _ ->
            advances++
            entry()
        }))
        assertEquals(0, advances)
        assertEquals(RosterScanTermination.WRAPPED, walkRosterCycle(entry(), 2, 4, 0, visit = { _, _ -> }, advance = { _, _ -> entry() }))
    }
}
