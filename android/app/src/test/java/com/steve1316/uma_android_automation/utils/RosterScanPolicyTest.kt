package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Guards against a roster scan silently skipping owned trainees and halting the queue. */
@DisplayName("RosterScanPolicy Tests")
class RosterScanPolicyTest {
    /** What the navigator passes: rowPx * 0.4f with rowPx = height x traineeRowStepFraction (0.099), 76 px on 1080x1920. */
    private val minMove = 1920 * 0.099f * 0.4f

    @Nested
    @DisplayName("blank means retry, not empty")
    inner class BlankReadTests {
        @Test
        fun `retries are capped`() {
            assertTrue(RosterScanPolicy.shouldRetryBlank(0))
            assertTrue(RosterScanPolicy.shouldRetryBlank(1))
            assertFalse(RosterScanPolicy.shouldRetryBlank(RosterScanPolicy.MAX_BLANK_RETRIES))
            assertFalse(RosterScanPolicy.shouldRetryBlank(RosterScanPolicy.MAX_BLANK_RETRIES + 1))
            assertEquals(2, RosterScanPolicy.MAX_BLANK_RETRIES)
        }

        @Test
        fun `a failed read earns one re-anchored second pass`() {
            assertTrue(RosterScanPolicy.needsSecondPass(failedReads = 1, passIndex = 0))
            assertTrue(RosterScanPolicy.needsSecondPass(failedReads = 5, passIndex = 0))
        }

        @Test
        fun `a clean scan does not pay for a second pass`() {
            // A complete scan that missed the target really did miss it; a second 90-second pass
            // would confirm nothing.
            assertFalse(RosterScanPolicy.needsSecondPass(failedReads = 0, passIndex = 0))
        }

        @Test
        fun `the second pass never recurses into a third`() {
            assertFalse(RosterScanPolicy.needsSecondPass(failedReads = 5, passIndex = 1))
        }
    }

    @Nested
    @DisplayName("onlyExcludedOutfitOwned")
    inner class OnlyExcludedOutfitOwned {
        private val near = 0.70

        @Test
        fun `a near-miss name on a non-excluded cell keeps the name-matching diagnosis`() {
            assertFalse(RosterScanPolicy.onlyExcludedOutfitOwned(failedReads = 0, excludedOutfitSeen = "Rouge Caroler", nearestSimilarity = 0.82, nearNameSimilarity = near))
            assertFalse(RosterScanPolicy.onlyExcludedOutfitOwned(failedReads = 0, excludedOutfitSeen = "Rouge Caroler", nearestSimilarity = near, nearNameSimilarity = near))
        }

        @Test
        fun `a complete read that skipped her other outfit explains the miss`() {
            assertTrue(RosterScanPolicy.onlyExcludedOutfitOwned(failedReads = 0, excludedOutfitSeen = "Rouge Caroler", nearestSimilarity = 0.5, nearNameSimilarity = near))
        }

        @Test
        fun `with no outfit of hers read, the not-found answer stands`() {
            assertFalse(RosterScanPolicy.onlyExcludedOutfitOwned(failedReads = 0, excludedOutfitSeen = null, nearestSimilarity = 0.5, nearNameSimilarity = near))
        }

        @Test
        fun `an incomplete read cannot claim she is owned only in that outfit`() {
            assertFalse(RosterScanPolicy.onlyExcludedOutfitOwned(failedReads = 2, excludedOutfitSeen = "Rouge Caroler", nearestSimilarity = 0.5, nearNameSimilarity = near))
        }
    }

    /**
     * The scan used to stop after 9 pages, about 55 trainees on 1080x1920, while its result still said
     * every cell was read. These pin where a pass may stop paging.
     */
    @Nested
    @DisplayName("pageEnd")
    inner class PageEndTests {
        private val plenty = 400_000L
        private val max = RosterScanPolicy.MAX_PAGE_SWIPES

        private fun end(page: Int = 3, newThisPage: Int = 5, arrivedUnmeasured: Boolean = false, delta: Int? = 272, msLeft: Long = plenty) =
            RosterScanPolicy.pageEnd(page, max, newThisPage, arrivedUnmeasured, delta, minMove, msLeft)

        @Test
        fun `a measured swipe that moved continues even when its page showed no new names`() {
            // 210 px moved mid-list on 2026-11-27 and can uncover no new row; that is not the end of the list.
            assertEquals(RosterScanPolicy.PageEnd.CONTINUE, end(newThisPage = 0, delta = 210))
            assertEquals(RosterScanPolicy.PageEnd.CONTINUE, end(newThisPage = 0, delta = 210, arrivedUnmeasured = true))
            assertEquals(RosterScanPolicy.PageEnd.CONTINUE, end(newThisPage = 0, delta = null, arrivedUnmeasured = false))
        }

        @Test
        fun `the bottom is a measured swipe that did not move, or no new names between unmeasurable swipes`() {
            assertEquals(RosterScanPolicy.PageEnd.BOTTOM, end(delta = 0))
            assertEquals(RosterScanPolicy.PageEnd.BOTTOM, end(delta = 75))
            assertEquals(RosterScanPolicy.PageEnd.CONTINUE, end(delta = 77))
            assertEquals(RosterScanPolicy.PageEnd.BOTTOM, end(newThisPage = 0, delta = null, arrivedUnmeasured = true))
            assertEquals(RosterScanPolicy.PageEnd.CONTINUE, end(newThisPage = 5, delta = null, arrivedUnmeasured = true))
        }

        @Test
        fun `page 9 and later keep going while the list moves`() {
            for (page in 8 until max) assertEquals(RosterScanPolicy.PageEnd.CONTINUE, end(page = page), "page $page")
            assertEquals(RosterScanPolicy.PageEnd.PAGE_LIMIT, end(page = max))
            assertTrue(RosterScanPolicy.PageEnd.PAGE_LIMIT.stoppedEarly)
        }

        @Test
        fun `the page guard covers 150 trainees at one row per swipe`() {
            // The shortest full swipe seen (254 px) moves 1.08 rows; page 0 reads two rows.
            val rows = 2 + max
            assertTrue(rows * 5 >= 150, "pages cover only ${rows * 5} trainees")
        }

        @Test
        fun `a scan with too little time left stops early, but a measured bottom is still the bottom`() {
            val low = RosterScanPolicy.SCAN_RESERVE_MS - 1
            assertEquals(RosterScanPolicy.PageEnd.OUT_OF_TIME, end(msLeft = low))
            assertTrue(RosterScanPolicy.PageEnd.OUT_OF_TIME.stoppedEarly)
            assertEquals(RosterScanPolicy.PageEnd.BOTTOM, end(msLeft = low, delta = 0))
            assertFalse(RosterScanPolicy.PageEnd.BOTTOM.stoppedEarly)
            assertEquals(RosterScanPolicy.PageEnd.CONTINUE, end(msLeft = RosterScanPolicy.SCAN_RESERVE_MS))
        }

        @Test
        fun `a second pass runs only when one as long as the first fits before the reserve`() {
            assertTrue(RosterScanPolicy.fitsAnotherPass(msLeft = 400_000L, passMs = 120_000L))
            assertFalse(RosterScanPolicy.fitsAnotherPass(msLeft = 250_000L, passMs = 120_000L))
        }
    }

    /**
     * Replays a scan on the measured 1080x1920 geometry: 235 px rows, the two tap rows at
     * y 1123 and 1313, row 0's top at y 988 with the list at its top, scroll range 235 x rows - 472.
     */
    @Nested
    @DisplayName("scan replay on 1080x1920")
    inner class ScanReplay {
        /** The 11-26 14:02 scan's swipes, the last one before the bottom excluded. */
        private val liveSwipes = listOf(278, 278, 262, 276, 274, 278)

        private inner class Result(val end: RosterScanPolicy.PageEnd, val rowsRead: Set<Int>, val shortSwipePageNew: Int?, val lastPage: Int)

        private fun replay(rows: Int, swipes: List<Int>, maxSwipes: Int = RosterScanPolicy.MAX_PAGE_SWIPES, shortSwipeAt: Int = -1, msLeftAt: (Int) -> Long = { 400_000L }): Result {
            val maxScroll = 235 * rows - 472
            var scroll = 0
            val read = HashSet<Int>()
            var shortSwipePageNew: Int? = null
            var page = 0
            while (true) {
                val before = read.size
                for (y in listOf(1123, 1313)) {
                    val r = Math.floorDiv(y - 988 + scroll, 235)
                    if (r in 0 until rows) read += r
                }
                val newThisPage = (read.size - before) * 5
                if (page == shortSwipeAt + 1) shortSwipePageNew = newThisPage
                val delta = minOf(swipes[page % swipes.size], maxScroll - scroll)
                scroll += delta
                val end = RosterScanPolicy.pageEnd(page, maxSwipes, newThisPage, false, delta, minMove, msLeftAt(page))
                if (end != RosterScanPolicy.PageEnd.CONTINUE) return Result(end, read, shortSwipePageNew, page)
                page++
            }
        }

        @Test
        fun `a 60-trainee roster is read to its last row and ends on the bottom`() {
            val result = replay(rows = 12, swipes = liveSwipes)
            assertEquals(RosterScanPolicy.PageEnd.BOTTOM, result.end)
            assertEquals((0 until 12).toSet(), result.rowsRead)
        }

        @Test
        fun `the old 9-page cap loses that roster's last row and now says so`() {
            val result = replay(rows = 12, swipes = liveSwipes, maxSwipes = 8)
            assertFalse(11 in result.rowsRead, "precondition: the old cap must miss the last row")
            assertEquals(RosterScanPolicy.PageEnd.PAGE_LIMIT, result.end)
        }

        @Test
        fun `a short mid-list swipe that uncovers no new row does not end the scan`() {
            // 280 + 290 + 290 puts a row edge just above the lower tap row, so the 210 px swipe adds nothing.
            val swipes = listOf(280, 290, 290, 210, 272, 272, 272, 272, 272, 272, 272, 272)
            val result = replay(rows = 12, swipes = swipes, shortSwipeAt = 3)
            assertEquals(0, result.shortSwipePageNew, "precondition: the page after the 210 px swipe must show no new names")
            assertEquals(RosterScanPolicy.PageEnd.BOTTOM, result.end)
            assertEquals((0 until 12).toSet(), result.rowsRead)
        }

        @Test
        fun `every released outfit fits one pass`() {
            // 126 outfits in the game data today: 26 rows.
            val result = replay(rows = 26, swipes = listOf(254))
            assertEquals(RosterScanPolicy.PageEnd.BOTTOM, result.end)
            assertEquals((0 until 26).toSet(), result.rowsRead)
        }

        @Test
        fun `a roster too long for the time left stops early with time to spare`() {
            // 15.3 s per page and 67 s from a match to the loaded career measured; 87 s measured from a career end to
            // Trainee Select, 40 s of anchoring assumed.
            val startLeft = 600_000L - 87_000L - 40_000L
            val pageMs = 15_300L
            val msLeftAt = { page: Int -> startLeft - (page + 1) * pageMs }
            val result = replay(rows = 60, swipes = listOf(272), msLeftAt = msLeftAt)
            assertEquals(RosterScanPolicy.PageEnd.OUT_OF_TIME, result.end)
            assertTrue(result.rowsRead.size < 60)
            assertTrue(msLeftAt(result.lastPage - 1) >= RosterScanPolicy.SCAN_RESERVE_MS, "it stopped a page too soon")
            assertTrue(msLeftAt(result.lastPage) > 0, "it ran past the deadline")
            // The worst case after the last go-ahead: one more page, a match on its last cell, then the launch.
            assertTrue(msLeftAt(result.lastPage - 1) - pageMs - 67_000L >= 60_000L, "a match on the last page read would not launch a minute before the deadline")
        }
    }
}
