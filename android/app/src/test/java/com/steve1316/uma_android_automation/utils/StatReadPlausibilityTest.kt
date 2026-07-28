package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [StatReadPlausibility], the floor added to the stat readers after a dropped
 * leading digit reached the outcome ledger and the spark-redraw pricing (2026-07-26).
 */
@DisplayName("StatReadPlausibility Tests")
class StatReadPlausibilityTest {
    /**
     * Mirrors what a reader plus its caller do with a rejected read: the reader returns its -1
     * sentinel and the last verified value is kept, because `Trainee.updateStats` never promotes a
     * value below 1. Used so the replays below assert the value a career actually ends up holding.
     */
    private fun applyRead(read: Int, lastVerified: Int): Int = if (StatReadPlausibility.isImplausibleDrop(read, lastVerified)) lastVerified else read

    @Nested
    @DisplayName("parseStatDigits")
    inner class ParseTests {
        @Test
        fun `trailing punctuation is stripped rather than failing the read`() {
            // Observed live on STAMINA every turn: "354:" is a correct 354 with OCR noise attached.
            assertEquals(354, StatReadPlausibility.parseStatDigits("354:"))
        }

        @Test
        fun `other non-digit noise around a stat is stripped`() {
            assertEquals(684, StatReadPlausibility.parseStatDigits(" 684 "))
            assertEquals(1248, StatReadPlausibility.parseStatDigits("1,248"))
            assertEquals(7, StatReadPlausibility.parseStatDigits("7."))
        }

        @Test
        fun `text with no digits is unparseable`() {
            assertNull(StatReadPlausibility.parseStatDigits(""))
            assertNull(StatReadPlausibility.parseStatDigits("---"))
        }
    }

    @Nested
    @DisplayName("isImplausibleDrop")
    inner class DropTests {
        @Test
        fun `the bound is well clear of the worst real event decrease`() {
            // src/data/*.json quantifies single-event decreases at -5, -10 and one -20.
            assertTrue(StatReadPlausibility.MAX_SINGLE_EVENT_DROP >= 20 * 5)
        }

        @Test
        fun `a small legitimate decrease passes`() {
            assertFalse(StatReadPlausibility.isImplausibleDrop(669, 684)) // -15, an event outcome
            assertFalse(StatReadPlausibility.isImplausibleDrop(664, 684)) // -20, the worst in game data
        }

        @Test
        fun `a decrease exactly at the bound still passes`() {
            assertFalse(StatReadPlausibility.isImplausibleDrop(584, 684)) // -100
            assertTrue(StatReadPlausibility.isImplausibleDrop(583, 684)) // -101
        }

        @Test
        fun `increases always pass`() {
            assertFalse(StatReadPlausibility.isImplausibleDrop(757, 7))
            assertFalse(StatReadPlausibility.isImplausibleDrop(684, 684))
        }

        @Test
        fun `every corruption observed in the recorded sessions is rejected`() {
            assertTrue(StatReadPlausibility.isImplausibleDrop(7, 684)) // 2026-07-26 23:42
            assertTrue(StatReadPlausibility.isImplausibleDrop(7, 703)) // 2026-07-27 07:24
            assertTrue(StatReadPlausibility.isImplausibleDrop(1, 231)) // 2026-07-27 06:43 SPEED
            assertTrue(StatReadPlausibility.isImplausibleDrop(4, 445)) // Tosen Jordan POWER
        }

        @Test
        fun `no baseline yet means the guard is inactive`() {
            // Stats initialize to -1 and Campaign builds a fresh Trainee per career, so this is
            // both the first read of a career and the whole of a career the bot resumed.
            assertFalse(StatReadPlausibility.isImplausibleDrop(7, -1))
            assertFalse(StatReadPlausibility.isImplausibleDrop(7, 0))
        }
    }

    @Nested
    @DisplayName("replays")
    inner class ReplayTests {
        @Test
        fun `the real GUTS sequence keeps 684 instead of promoting 7`() {
            // 2026-07-26: 684 verified at 23:40:14, then 7 at 23:41:04 and 7 again at 23:42:20.
            // The second identical 7 is what the old consistency rule promoted to trusted.
            var verified = StatReadPlausibility.parseStatDigits("684")!!
            assertEquals(684, verified)

            verified = applyRead(StatReadPlausibility.parseStatDigits("7")!!, verified)
            assertEquals(684, verified, "first 7 must be rejected")

            verified = applyRead(StatReadPlausibility.parseStatDigits("7")!!, verified)
            assertEquals(684, verified, "a repeated 7 must not become trusted")
        }

        @Test
        fun `a legitimate small decrease is still accepted mid-career`() {
            var verified = 684
            verified = applyRead(669, verified)
            assertEquals(669, verified)
        }

        @Test
        fun `a new career's low first read is not measured against the previous career's endgame`() {
            // The wedge this must not cause: run 2 of a queue starts at single-digit stats while
            // the previous career ended near 1200. A fresh Trainee carries -1, so the guard is off.
            val previousCareerEnd = 1248
            val freshCareerBaseline = -1
            assertTrue(StatReadPlausibility.isImplausibleDrop(12, previousCareerEnd))
            assertFalse(StatReadPlausibility.isImplausibleDrop(12, freshCareerBaseline))
            assertEquals(12, applyRead(12, freshCareerBaseline))
        }

        @Test
        fun `a career can still climb normally once its baseline exists`() {
            var verified = -1
            for (read in listOf(12, 45, 118, 260, 431, 684, 1102)) {
                verified = applyRead(read, verified)
            }
            assertEquals(1102, verified)
        }
    }
}
