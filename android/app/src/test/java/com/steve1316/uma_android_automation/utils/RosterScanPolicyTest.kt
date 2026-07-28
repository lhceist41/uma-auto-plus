package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RosterScanPolicy], the certainty gate added after a roster scan silently skipped
 * five owned trainees and halted the queue (2026-07-28).
 */
@DisplayName("RosterScanPolicy Tests")
class RosterScanPolicyTest {
    /** The live grid: 5 columns by 2 rows per page. */
    private val gridRows = 2

    @Nested
    @DisplayName("nextStartRow, every certainty combination")
    inner class StartRowTests {
        @Test
        fun `measured advance over a fully read page earns the skip`() {
            // The only case that may skip: a 1-row advance over a 2-row band means row 0 is
            // carried over and was already read, so the next page starts at row 1.
            assertEquals(1, RosterScanPolicy.nextStartRow(gridRows, advancedRows = 1, previousPageFullyRead = true))
            assertEquals(0, RosterScanPolicy.nextStartRow(gridRows, advancedRows = 2, previousPageFullyRead = true))
        }

        @Test
        fun `unmeasurable swipe never earns the skip, even on a fully read page`() {
            // 'movement unmeasurable' is an uncertainty signal, not benign noise: the carry-over
            // is unknown, so nothing may be assumed already read.
            assertEquals(0, RosterScanPolicy.nextStartRow(gridRows, advancedRows = null, previousPageFullyRead = true))
        }

        @Test
        fun `a blank read never earns the skip, even on a measured advance`() {
            // This is the exact 2026-07-28 combination: the swipe measured 262px (a 1-row advance)
            // over a page whose row 1 had read blank. The old code skipped row 0 of the next page,
            // which was those same unread cells, and Hishi Amazon was never seen.
            assertEquals(0, RosterScanPolicy.nextStartRow(gridRows, advancedRows = 1, previousPageFullyRead = false))
        }

        @Test
        fun `both uncertain is still zero`() {
            assertEquals(0, RosterScanPolicy.nextStartRow(gridRows, advancedRows = null, previousPageFullyRead = false))
        }

        @Test
        fun `an over-large advance clamps rather than going negative`() {
            // A swipe that outruns the scan band must not produce a negative start row.
            assertEquals(0, RosterScanPolicy.nextStartRow(gridRows, advancedRows = 5, previousPageFullyRead = true))
        }

        @Test
        fun `a zero-row advance re-reads the whole page`() {
            assertEquals(gridRows, RosterScanPolicy.nextStartRow(gridRows, advancedRows = 0, previousPageFullyRead = true))
        }
    }

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
    @DisplayName("the 2026-07-28 sequence")
    inner class RegressionTests {
        @Test
        fun `replaying the page that lost Hishi Amazon now re-reads instead of skipping`() {
            // Page 3 read row 0 (Mayano Top Gun .. King Halo) and then five blank cells on row 1.
            val pageFullyRead = false
            // The swipe that followed measured 262px against a ~262px row: a 1-row advance.
            val advancedRows = 1

            val old = (gridRows - advancedRows).coerceIn(0, gridRows) // what the old code computed
            assertEquals(1, old, "the old arithmetic skipped row 0 of the next page")

            val fixed = RosterScanPolicy.nextStartRow(gridRows, advancedRows, pageFullyRead)
            assertEquals(0, fixed, "the unread row must force a full re-scan of the next page")
            assertNotEquals(old, fixed)
        }
    }
}
