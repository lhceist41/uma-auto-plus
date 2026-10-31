package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FailureChanceFrameTest {
    /**
     * The Training screen after a tab tap: the "Failure" label matches on capture number [settledAt] and every later one.
     * Frames are numbered by capture, and a location names the frame it was found on, so a location from another frame is visible.
     */
    private class FakeTrainingScreen(
        val settledAt: Int,
        val captureMs: Long = 60,
        val pauseMs: Long = 100,
    ) {
        var now = 0L
        var captures = 0
        var pauses = 0

        fun capture(): Int {
            captures++
            now += captureMs
            return captures
        }

        fun locate(frame: Int): String? = if (frame >= settledAt) "label on frame $frame" else null

        fun settle(): Pair<Int, String?> =
            captureSettledFailureChanceFrame(
                capture = ::capture,
                locate = ::locate,
                nowMs = { now },
                pause = {
                    pauses++
                    now += pauseMs
                },
            )
    }

    @Test
    fun `a label on the first capture is read at once`() {
        val screen = FakeTrainingScreen(settledAt = 1)
        val (frame, location) = screen.settle()
        assertEquals(1, frame)
        assertEquals("label on frame 1", location)
        assertEquals(1, screen.captures)
        assertEquals(0, screen.pauses)
    }

    @Test
    fun `a label that settles after a few captures is read on the capture that shows it`() {
        for (settledAt in 2..5) {
            val screen = FakeTrainingScreen(settledAt = settledAt)
            val (frame, location) = screen.settle()
            assertEquals(settledAt, frame, "settled at $settledAt")
            assertEquals("label on frame $settledAt", location, "settled at $settledAt")
            assertEquals(settledAt, screen.captures, "settled at $settledAt")
            assertEquals(settledAt - 1, screen.pauses, "settled at $settledAt")
        }
    }

    @Test
    fun `a label that never shows gives the last frame with no location once the budget is spent`() {
        val screen = FakeTrainingScreen(settledAt = Int.MAX_VALUE)
        val (frame, location) = screen.settle()
        assertNull(location)
        assertEquals(screen.captures, frame, "the frame handed on is the last one captured")
        assertTrue(screen.now >= FAILURE_CHANCE_SETTLE_MS, "stopped at ${screen.now}ms, before the budget")
        assertTrue(screen.now < FAILURE_CHANCE_SETTLE_MS + screen.pauseMs + screen.captureMs, "overran the budget: ${screen.now}ms")
        assertEquals(screen.pauses + 1, screen.captures, "every recapture follows one pause")
    }

    @Test
    fun `no capture starts after the budget, even when the next one would show the label`() {
        val slow = FakeTrainingScreen(settledAt = 3, captureMs = 600, pauseMs = 100)
        // Capture 1 ends at 600ms, capture 2 at 1300ms: the deadline has passed, so there is no capture 3.
        val (frame, location) = slow.settle()
        assertNull(location)
        assertEquals(2, frame)
        assertEquals(2, slow.captures)
    }

    @Test
    fun `the location always belongs to the frame it is returned with`() {
        for (settledAt in 1..12) {
            val (frame, location) = FakeTrainingScreen(settledAt = settledAt).settle()
            if (location != null) assertEquals("label on frame $frame", location, "settled at $settledAt")
        }
    }
}
