package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.types.StatName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FailureChanceFrameTest {
    /**
     * The Training screen after a tab tap: the "Failure" label matches on capture number [settledAt] and every later one,
     * except captures listed in [missesOn]. Frames are numbered by capture, and a location names the frame it was found on,
     * so a location from another frame is visible.
     */
    private class FakeTrainingScreen(
        val settledAt: Int,
        val captureMs: Long = 60,
        val pauseMs: Long = 100,
        val settleMs: Long = 200,
        val missesOn: Set<Int> = emptySet(),
    ) {
        var now = 0L
        var captures = 0
        var pauses = 0
        var settles = 0

        fun capture(): Int {
            captures++
            now += captureMs
            return captures
        }

        fun locate(frame: Int): String? = if (frame >= settledAt && frame !in missesOn) "label on frame $frame" else null

        fun settle(): Pair<Int, String?> =
            captureSettledFailureChanceFrame(
                capture = ::capture,
                locate = ::locate,
                nowMs = { now },
                pause = {
                    pauses++
                    now += pauseMs
                },
                settle = {
                    settles++
                    now += settleMs
                },
            )
    }

    @Test
    fun `a label on the first capture is read on one more frame taken after the settle`() {
        val screen = FakeTrainingScreen(settledAt = 1)
        val (frame, location) = screen.settle()
        assertEquals(2, frame)
        assertEquals("label on frame 2", location)
        assertEquals(2, screen.captures)
        assertEquals(0, screen.pauses)
        assertEquals(1, screen.settles)
    }

    @Test
    fun `a label that shows after a few captures is settled once and read on the next frame`() {
        for (settledAt in 2..5) {
            val screen = FakeTrainingScreen(settledAt = settledAt)
            val (frame, location) = screen.settle()
            assertEquals(settledAt + 1, frame, "settled at $settledAt")
            assertEquals("label on frame ${settledAt + 1}", location, "settled at $settledAt")
            assertEquals(settledAt - 1, screen.pauses, "settled at $settledAt")
            assertEquals(1, screen.settles, "settled at $settledAt")
        }
    }

    @Test
    fun `a label not found again on the settled frame keeps its first location`() {
        val screen = FakeTrainingScreen(settledAt = 2, missesOn = setOf(3))
        val (frame, location) = screen.settle()
        assertEquals(3, frame, "the settled frame is the one read")
        assertEquals("label on frame 2", location, "the location found before the settle is kept")
    }

    @Test
    fun `a label that never shows gives the last frame with no location once the budget is spent, without a settle`() {
        val screen = FakeTrainingScreen(settledAt = Int.MAX_VALUE)
        val (frame, location) = screen.settle()
        assertNull(location)
        assertEquals(screen.captures, frame, "the frame handed on is the last one captured")
        assertTrue(screen.now >= FAILURE_CHANCE_SETTLE_MS, "stopped at ${screen.now}ms, before the budget")
        assertTrue(screen.now < FAILURE_CHANCE_SETTLE_MS + screen.pauseMs + screen.captureMs, "overran the budget: ${screen.now}ms")
        assertEquals(screen.pauses + 1, screen.captures, "every recapture follows one pause")
        assertEquals(0, screen.settles)
    }

    @Test
    fun `no capture starts after the budget, even when the next one would show the label`() {
        val slow = FakeTrainingScreen(settledAt = 3, captureMs = 600, pauseMs = 100)
        // Capture 1 ends at 600ms, capture 2 at 1300ms: the deadline has passed, so there is no capture 3.
        val (frame, location) = slow.settle()
        assertNull(location)
        assertEquals(2, frame)
        assertEquals(2, slow.captures)
        assertEquals(0, slow.settles)
    }

    @Test
    fun `a label found at the end of the budget adds only the settle and one capture`() {
        for (settledAt in 1..12) {
            val screen = FakeTrainingScreen(settledAt = settledAt)
            screen.settle()
            val bound = FAILURE_CHANCE_SETTLE_MS + screen.pauseMs + screen.captureMs + screen.settleMs + screen.captureMs
            assertTrue(screen.now <= bound, "settled at $settledAt took ${screen.now}ms, bound $bound")
            assertTrue(screen.settles <= 1, "settled at $settledAt")
        }
    }

    // Label match centres per facility in the live logs at the 1080 width; the label template is 97x33 and a match
    // needs the whole template inside the searched region.
    private val liveLabelX = mapOf(StatName.SPEED to 159, StatName.STAMINA to 350, StatName.POWER to 541, StatName.GUTS to 732, StatName.WIT to 923)

    private fun labelFits(region: IntArray, centreX: Int, centreY: Int): Boolean {
        val (x, y, w, h) = region.toList()
        val left = centreX - 97 / 2
        val top = centreY - 33 / 2
        return left >= x && left + 97 <= x + w && top >= y && top + 33 <= y + h
    }

    @Test
    fun `each facility's label falls in its own column and in no other`() {
        for (height in listOf(1920, 2340, 2400)) {
            for (stat in StatName.entries) {
                val column = failureBubbleColumn(stat, 1080, height)
                // The label rests at y 1371 to 1386 in the logs at 1080x1920.
                for (y in listOf(1371, 1386)) {
                    assertTrue(labelFits(column, liveLabelX.getValue(stat), y), "$stat label at y $y, height $height")
                    for (other in StatName.entries - stat) {
                        assertFalse(labelFits(column, liveLabelX.getValue(other), y), "$other label found in $stat column, height $height")
                    }
                }
            }
        }
    }

    @Test
    fun `a stale bubble over the previous facility is not found in the selected facility's column`() {
        val order = StatName.entries
        for (i in 1 until order.size) {
            val column = failureBubbleColumn(order[i], 1080, 1920)
            assertFalse(labelFits(column, liveLabelX.getValue(order[i - 1]), 1373), "${order[i - 1]} bubble read while on ${order[i]}")
        }
        // Each label keeps room to slide sideways: at least 40 px on both sides inside its column.
        for (stat in order) {
            val (x, _, w, _) = failureBubbleColumn(stat, 1080, 1920).toList()
            val left = liveLabelX.getValue(stat) - 97 / 2
            assertTrue(left - x >= 40 && (x + w) - (left + 97) >= 40, "$stat margin: column $x..${x + w}, label $left..${left + 97}")
        }
    }

    @Test
    fun `the columns stay on screen and cover the bottom half`() {
        for (stat in StatName.entries) {
            val (x, y, w, h) = failureBubbleColumn(stat, 1080, 1920).toList()
            assertTrue(x >= 0 && x + w <= 1080, "$stat column $x..${x + w}")
            assertEquals(960, y)
            assertEquals(960, h)
            assertEquals(216, w)
        }
    }
}
