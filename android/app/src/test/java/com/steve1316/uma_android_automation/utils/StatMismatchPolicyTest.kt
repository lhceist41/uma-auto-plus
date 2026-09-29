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

        fun read(value: Int, contradictsHeld: Boolean = false): StatMismatchPolicy.Decision {
            val decision = StatMismatchPolicy.decide(held, value, baseline, strikes, contradictsHeld)
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
                is StatMismatchPolicy.Decision.Discard -> Unit
            }
            return decision
        }

        /** One raw OCR value through the reader's drop floor and then the tracker, as `updateStats` wires them. */
        fun screen(raw: Int): StatMismatchPolicy.Decision {
            val rejected = raw.takeIf { StatReadPlausibility.isImplausibleDrop(it, held) }
            val readerValue = if (rejected != null) -1 else raw
            val tracked = StatMismatchPolicy.trackedReading(readerValue, rejected, held)
            return read(tracked, contradictsHeld = tracked != readerValue)
        }
    }

    @Nested
    @DisplayName("unusable readings")
    inner class DiscardTests {
        @Test
        fun `the -1 sentinel is never written as a stat, at any held value`() {
            // Live 2026-07-28: the floor rejected a WIT read of 1 against a verified 126 and
            // returned -1. abs(-1 - 126) is 127, inside the 150 accept window, so the sentinel was
            // taken at face value and that turn scored with WIT = -1 and completion -0.25%.
            for (held in listOf(1, 50, 126, 148, 149, 231, 684, 1200)) {
                val d = StatMismatchPolicy.decide(oldValue = held, newValue = -1, recordedMismatch = null, strikes = 0)
                assertTrue(d is StatMismatchPolicy.Decision.Discard, "held=$held must discard the sentinel, not accept it")
            }
        }

        @Test
        fun `a discarded reading leaves the held value and the tracker untouched`() {
            val wit = Tracker(held = 126)
            assertTrue(wit.read(-1) is StatMismatchPolicy.Decision.Discard)
            assertEquals(126, wit.held, "the floor's log promises the old value is kept")

            // A later genuine reading still lands normally.
            assertTrue(wit.read(131) is StatMismatchPolicy.Decision.Accept)
            assertEquals(131, wit.held)
        }

        @Test
        fun `repeated sentinels never accumulate strikes toward a promotion`() {
            val wit = Tracker(held = 126)
            repeat(6) { assertTrue(wit.read(-1) is StatMismatchPolicy.Decision.Discard) }
            assertEquals(126, wit.held)
        }

        @Test
        fun `zero is a sentinel too, not a real stat`() {
            val d = StatMismatchPolicy.decide(oldValue = 126, newValue = 0, recordedMismatch = null, strikes = 0)
            assertTrue(d is StatMismatchPolicy.Decision.Discard)
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

    @Nested
    @DisplayName("a held value the floor keeps contradicting")
    inner class FloorLockTests {
        @Test
        fun `the Trackblazer WIT lock recovers after the policy's corroborating reads`() {
            // 2026-09-28 Mejiro Ryan: 136 read as 1361 three times and was promoted, 139 as 1391 was
            // accepted, then the floor rejected every true read from 145 to 512 until career end.
            val wit = Tracker(held = 132)
            listOf(1361, 1361, 1361, 1391).forEach { wit.screen(it) }
            assertEquals(1391, wit.held, "the misread is held, as live")

            assertTrue(wit.screen(145) is StatMismatchPolicy.Decision.Baseline)
            assertTrue(wit.screen(157) is StatMismatchPolicy.Decision.Hold)
            assertTrue(wit.screen(157) is StatMismatchPolicy.Decision.Promote)
            assertEquals(157, wit.held)

            for (read in listOf(166, 171, 248, 319, 442, 512)) wit.screen(read)
            assertEquals(512, wit.held, "normal reads track the true value again")
        }

        @Test
        fun `one stray low read is still rejected`() {
            val guts = Tracker(held = 684)
            assertTrue(guts.screen(300) is StatMismatchPolicy.Decision.Baseline)
            assertEquals(684, guts.held)
            assertTrue(guts.screen(690) is StatMismatchPolicy.Decision.Accept)
            assertEquals(690, guts.held)
            assertTrue(guts.screen(300) is StatMismatchPolicy.Decision.Baseline, "the stray's earlier baseline was cleared, so it starts over")
            assertEquals(690, guts.held)
        }

        @Test
        fun `a contradicting read inside the accept window is not taken at face value`() {
            // 1290 is 101 below 1391: the floor rejects it, and abs(1290 - 1391) is inside the 150 window.
            val wit = Tracker(held = 1391)
            assertTrue(wit.screen(1290) is StatMismatchPolicy.Decision.Baseline)
            assertEquals(1391, wit.held)
        }

        @Test
        fun `dropped-digit reads never reach the tracker`() {
            val guts = Tracker(held = 684)
            repeat(5) { assertTrue(guts.screen(7) is StatMismatchPolicy.Decision.Discard) }
            assertTrue(guts.screen(StatReadPlausibility.MIN_CONTRADICTING_READ - 1) is StatMismatchPolicy.Decision.Discard)
            assertEquals(684, guts.held)
        }

        @Test
        fun `a genuine large rise still promotes, and a small change is still accepted`() {
            val speed = Tracker(held = 132)
            assertTrue(speed.screen(420) is StatMismatchPolicy.Decision.Baseline)
            assertTrue(speed.screen(425) is StatMismatchPolicy.Decision.Hold)
            assertTrue(speed.screen(430) is StatMismatchPolicy.Decision.Promote)
            assertEquals(430, speed.held)
            assertTrue(speed.screen(470) is StatMismatchPolicy.Decision.Accept)
            assertEquals(470, speed.held)
        }

        @Test
        fun `a reading the reader kept is passed through unchanged`() {
            assertEquals(512, StatMismatchPolicy.trackedReading(512, null, 1391))
            assertEquals(512, StatMismatchPolicy.trackedReading(512, 145, 1391))
            assertEquals(-1, StatMismatchPolicy.trackedReading(-1, null, 1391))
            assertEquals(-1, StatMismatchPolicy.trackedReading(-1, 145, -1), "no held value, nothing to contradict")
        }
    }

    @Nested
    @DisplayName("updateStats wiring (source guard)")
    inner class WiringTests {
        private val trainee by lazy {
            var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
            val relative = "android/app/src/main/java/com/steve1316/uma_android_automation/types/Trainee.kt"
            repeat(8) {
                val f = java.io.File(dir, relative)
                if (f.isFile) return@lazy f.readText().replace("\r\n", "\n")
                dir = dir?.parentFile
            }
            throw AssertionError("$relative not found")
        }

        private fun count(needle: String) = trainee.windowed(needle.length).count { it == needle }

        @Test
        fun `both branches collect floor rejections and weigh contradicting reads`() {
            assertTrue(trainee.contains("getStat(statName), floorRejected)"), "threaded reads report floor rejections")
            assertTrue(trainee.contains("floorRejections = floorRejected,"), "sequential reads report floor rejections")
            assertEquals(2, count("val newValue = StatMismatchPolicy.trackedReading(readerValue, floorRejected[statName], oldValue)"))
            assertEquals(2, count("decideStatUpdate(statName, oldValue, newValue, newValue != readerValue)"))
            assertTrue(trainee.contains("StatMismatchPolicy.decide(oldValue, newValue, recorded, mismatchCounts[statName] ?: 0, contradictsHeld)"))
        }

        @Test
        fun `the career-end Details check still sees the dialog's rejections`() {
            assertTrue(trainee.contains("if (isAptitudeDialog) detailsFloorRejections.putAll(floorRejected)"))
        }
    }
}
