package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for the Smart Borrow priority selection. Row texts mirror the two-line OCR reads
 * from the 2026-07-13 picker captures.
 */
@DisplayName("Smart Borrow priority selection")
class SmartBorrowTest {
    private val page1 =
        listOf(
            "[Tracen Reception]\nTazuna Hayakawa",
            "[Touching Sleeves Is Good Luck! ♪]\nMatikanefukukitaru",
            "[Dreams Do Come True!]\nWinning Ticket",
            "[Fire at My Heels]\nKitasan Black",
            "[Beyond This Shining Moment]\nSilence Suzuka",
        )

    @Test
    @DisplayName("The highest-priority card wins even when a lower-priority one sits higher in the list")
    fun testPriorityBeatsRowOrder() {
        // Tazuna (priority 3) is row 0, but Kitasan (priority 0) on row 3 must win.
        val best = smartBorrowBestMatch(page1)
        assertEquals(0 to 3, best, "Kitasan Black is the highest-priority card in the pool")
    }

    @Test
    @DisplayName("Without the top cards, the best remaining list entry is picked")
    fun testBestRemaining() {
        val rows = listOf("[Dreams Do Come True!]\nWinning Ticket", "[Tracen Reception]\nTazuna Hayakawa")
        val best = smartBorrowBestMatch(rows)
        assertEquals(3 to 1, best, "Tazuna is the best list card present; Winning Ticket is not on the list")
    }

    @Test
    @DisplayName("A pool with no list cards yields no pick")
    fun testNoListCard() {
        assertNull(smartBorrowBestMatch(listOf("[Dreams Do Come True!]\nWinning Ticket", "[Run(my)way]\nGold City")))
        assertNull(smartBorrowBestMatch(emptyList()))
    }

    @Test
    @DisplayName("A user preference passed as priority zero outranks the whole curated list")
    fun testPreferencePrepended() {
        val priorities = listOf("[Sentimental Flare ♪] Maruzensky") + SmartBorrowList.priority
        val rows = page1 + "[Sentimental Flare ♪]\nMaruzensky"
        assertEquals(0 to 5, smartBorrowBestMatch(rows, priorities), "The pinned card outranks Kitasan")
    }
}
