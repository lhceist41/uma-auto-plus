package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [RosterScanPolicy], the certainty rules added after a roster scan silently skipped
 * five owned trainees and halted the queue (2026-07-28).
 */
@DisplayName("RosterScanPolicy Tests")
class RosterScanPolicyTest {
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
        /** The navigator's NEAR_NAME_SIMILARITY. */
        private val near = 0.70

        @Test
        fun `a near-miss name on a non-excluded cell keeps the name-matching diagnosis`() {
            // She may own both outfits with her plain banner misread just under the match threshold.
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
}
