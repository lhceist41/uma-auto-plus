package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Sequence tests for [StatMismatchPolicy], the decision `Trainee.updateStats` runs for each stat.
 *
 * These drive the tracker layer directly, below the read-layer floor in [StatReadPlausibility], so
 * they show what the mismatch rule does on its own with no plausibility guard shadowing it.
 */
@DisplayName("StatMismatchPolicy Tests")
class StatMismatchPolicyTest {
    /**
     * Replays a run of readings through the policy exactly as `updateStats` does: it owns the held
     * value plus the per-stat baseline and strike count, and feeds them back on each reading.
     */
    private class Tracker(var held: Int) {
        private var baseline: Int? = null
        private var strikes: Int = 0

        fun read(value: Int): StatMismatchPolicy.Decision {
            val decision = StatMismatchPolicy.decide(held, value, baseline, strikes)
            when (decision) {
                is StatMismatchPolicy.Decision.Accept -> {
                    held = value
                    baseline = null
                    strikes = 0
                }
                is StatMismatchPolicy.Decision.Promote -> {
                    held = value
                    baseline = null
                    strikes = 0
                }
                is StatMismatchPolicy.Decision.Baseline -> {
                    baseline = decision.value
                    strikes = 0
                }
                is StatMismatchPolicy.Decision.Hold -> strikes = decision.strikes
            }
            return decision
        }
    }

    @Nested
    @DisplayName("the seeded first strike")
    inner class SeedTests {
        @Test
        fun `the real 684 to 7 to 7 sequence never promotes the misread`() {
            // Live 2026-07-26: GUTS held 684, read 7 at 23:41:04 and 7 again at 23:42:20. The old
            // tracker seeded its baseline at -1, so the first 7 landed inside the 50-wide
            // consistency window on arrival, scored a free strike, and the second 7 promoted it.
            val guts = Tracker(held = 684)

            val first = guts.read(7)
            assertTrue(first is StatMismatchPolicy.Decision.Baseline, "the first 7 records a baseline, it corroborates nothing")
            assertEquals(684, guts.held)

            val second = guts.read(7)
            assertTrue(second is StatMismatchPolicy.Decision.Hold, "the second 7 is only the first real strike")
            assertEquals(1, (second as StatMismatchPolicy.Decision.Hold).strikes)
            assertEquals(684, guts.held, "684 must survive the sequence that used to promote 7")
        }

        @Test
        fun `no value can look consistent before a baseline exists`() {
            // The seed bias only ever showed up for small values, because abs(v - (-1)) < 50 is
            // exactly v < 49. Every one of those must now record rather than corroborate.
            for (misread in listOf(1, 2, 7, 30, 48)) {
                val d = StatMismatchPolicy.decide(oldValue = 684, newValue = misread, recordedMismatch = null, strikes = 0)
                assertTrue(d is StatMismatchPolicy.Decision.Baseline, "$misread should record a baseline, not corroborate one")
            }
        }

        @Test
        fun `the other observed corruptions are equally unable to promote on arrival`() {
            val speed = Tracker(held = 231) // 2026-07-27 06:43, SPEED 231 -> 1
            assertTrue(speed.read(1) is StatMismatchPolicy.Decision.Baseline)
            assertTrue(speed.read(1) is StatMismatchPolicy.Decision.Hold)
            assertEquals(231, speed.held)
        }
    }

    @Nested
    @DisplayName("genuine drift still recovers")
    inner class GenuineTests {
        @Test
        fun `a repeated new value after a real mismatch still promotes`() {
            // The reason this machinery exists: when the HELD value is the misread, consistent
            // readings of the true value must eventually win.
            val guts = Tracker(held = 7) // held value is itself a corruption
            assertTrue(guts.read(757) is StatMismatchPolicy.Decision.Baseline)
            assertTrue(guts.read(757) is StatMismatchPolicy.Decision.Hold)

            val third = guts.read(757)
            assertTrue(third is StatMismatchPolicy.Decision.Promote, "a corroborated baseline must be trusted")
            assertEquals(757, guts.held)
        }

        @Test
        fun `promotion still needs two strikes, not one`() {
            assertEquals(2, StatMismatchPolicy.STRIKES_TO_PROMOTE)
            val d = StatMismatchPolicy.decide(oldValue = 684, newValue = 300, recordedMismatch = 300, strikes = 1)
            assertTrue(d is StatMismatchPolicy.Decision.Promote)
            assertEquals(2, (d as StatMismatchPolicy.Decision.Promote).strikes)
        }

        @Test
        fun `a disagreeing reading restarts the baseline instead of accumulating`() {
            val guts = Tracker(held = 900)
            assertTrue(guts.read(300) is StatMismatchPolicy.Decision.Baseline)
            assertTrue(guts.read(500) is StatMismatchPolicy.Decision.Baseline, "500 is not within 50 of 300")
            assertTrue(guts.read(500) is StatMismatchPolicy.Decision.Hold)
            assertEquals(900, guts.held)
        }

        @Test
        fun `a negative sentinel is never promoted however often it repeats`() {
            val guts = Tracker(held = 684)
            repeat(6) { guts.read(-1) }
            assertEquals(684, guts.held)
        }
    }

    @Nested
    @DisplayName("ordinary reads are untouched")
    inner class AcceptTests {
        @Test
        fun `a small change is taken at face value`() {
            val guts = Tracker(held = 684)
            assertTrue(guts.read(690) is StatMismatchPolicy.Decision.Accept)
            assertEquals(690, guts.held)
        }

        @Test
        fun `the first read of a career is accepted whatever its size`() {
            val guts = Tracker(held = -1)
            assertTrue(guts.read(12) is StatMismatchPolicy.Decision.Accept)
            assertEquals(12, guts.held)
        }

        @Test
        fun `an accepted read clears a baseline left by an earlier mismatch`() {
            val guts = Tracker(held = 684)
            assertTrue(guts.read(7) is StatMismatchPolicy.Decision.Baseline)
            assertTrue(guts.read(690) is StatMismatchPolicy.Decision.Accept)
            // The stale 7 baseline must be gone, so a later 7 records again rather than striking.
            assertTrue(guts.read(7) is StatMismatchPolicy.Decision.Baseline)
            assertEquals(690, guts.held)
        }
    }
}
