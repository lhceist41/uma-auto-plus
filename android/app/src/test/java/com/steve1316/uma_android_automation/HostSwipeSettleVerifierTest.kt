package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HostSwipeSettleVerifierTest {
    @Test
    fun `immediate stable samples classify moved and no effect`() {
        val moved = settle(before = "A", samples = fingerprints("B", "B"))
        assertEquals(SwipeMovement.MOVED, moved.result.movement)
        assertEquals("B", moved.result.stableAfter.value)
        assertEquals(2, moved.result.samplesTaken)

        val noEffect = settle(before = "A", samples = fingerprints("A", "A"))
        assertEquals(SwipeMovement.NO_EFFECT, noEffect.result.movement)
        assertEquals("A", noEffect.result.stableAfter.value)
        assertEquals(2, noEffect.result.samplesTaken)
    }

    @Test
    fun `inertial samples can settle as moved or no effect`() {
        val moved = settle(before = "A", samples = fingerprints("B", "C", "D", "E", "E"))
        assertEquals(SwipeMovement.MOVED, moved.result.movement)
        assertEquals("E", moved.result.stableAfter.value)
        assertEquals(5, moved.result.samplesTaken)

        val noEffect = settle(before = "A", samples = fingerprints("B", "C", "A", "A"))
        assertEquals(SwipeMovement.NO_EFFECT, noEffect.result.movement)
        assertEquals("A", noEffect.result.stableAfter.value)
        assertEquals(4, noEffect.result.samplesTaken)
    }

    @Test
    fun `changing samples exhaust the bounded settle window`() {
        val settled = settle(before = "A", samples = fingerprints("B", "C", "D", "E", "F", "G", "H", "I"))

        assertEquals(SwipeMovement.UNCERTAIN, settled.result.movement)
        assertFalse(settled.result.stableAfter.recognized)
        assertEquals(HOST_SWIPE_SETTLE_MAX_SAMPLES, settled.result.samplesTaken)
        assertEquals(HOST_SWIPE_SETTLE_MAX_SAMPLES, settled.captureCount)
        assertEquals(1, settled.swipeCount)
        assertEquals(HOST_SWIPE_INITIAL_SETTLE_SECONDS, settled.waits.first())
        assertEquals(
            List(HOST_SWIPE_SETTLE_MAX_SAMPLES - 1) { HOST_SWIPE_SETTLE_POLL_SECONDS },
            settled.waits.drop(1),
        )
        assertEquals(3.75, settled.waits.sum(), 0.0001)
    }

    @Test
    fun `unrecognized and blank samples reset consecutive stability`() {
        val unrecognized = ScreenFingerprint(recognized = false, value = "B")
        val eventual = settle(before = "A", samples = fingerprints("B") + unrecognized + fingerprints("B", "B"))
        assertEquals(SwipeMovement.MOVED, eventual.result.movement)
        assertEquals(4, eventual.result.samplesTaken)
        assertEquals(3, eventual.result.recognizedSamples)

        val separated =
            settle(
                before = "A",
                samples = fingerprints("B") + unrecognized + fingerprints("B", "C", "D", "E", "F", "G"),
            )
        assertEquals(SwipeMovement.UNCERTAIN, separated.result.movement)

        val blank = List(HOST_SWIPE_SETTLE_MAX_SAMPLES) { ScreenFingerprint(recognized = true, value = "") }
        val blanks = settle(before = "A", samples = blank)
        assertEquals(SwipeMovement.UNCERTAIN, blanks.result.movement)
        assertEquals(0, blanks.result.recognizedSamples)

        val aliased = ScreenFingerprint(recognized = true, value = "B")
        val aliases = settle(before = "A", samples = listOf(aliased, aliased) + fingerprints("C", "D", "E", "F", "G", "H"))
        assertEquals(SwipeMovement.UNCERTAIN, aliases.result.movement)
        assertFalse(aliases.result.stableAfter.recognized)
    }

    @Test
    fun `one matching value at the deadline is not stable evidence`() {
        val settled = settle(before = "A", samples = fingerprints("B", "C", "D", "E", "F", "G", "H", "A"))

        assertEquals(SwipeMovement.UNCERTAIN, settled.result.movement)
        assertFalse(settled.result.stableAfter.recognized)
        assertEquals(HOST_SWIPE_SETTLE_MAX_SAMPLES, settled.result.samplesTaken)
    }

    @Test
    fun `stop during settling exits without another capture or swipe`() {
        val settled =
            settle(
                before = "A",
                samples = fingerprints("B", "B"),
                stopAfterWaitCount = 1,
            )

        assertEquals(SwipeMovement.UNCERTAIN, settled.result.movement)
        assertTrue(settled.result.stopped)
        assertEquals(0, settled.result.samplesTaken)
        assertEquals(0, settled.captureCount)
        assertEquals(1, settled.swipeCount)
    }

    @Test
    fun `executed transport without stable recognized samples stays uncertain`() {
        val settled =
            settle(
                before = "A",
                samples = List(HOST_SWIPE_SETTLE_MAX_SAMPLES) { ScreenFingerprint(recognized = false, value = "") },
            )

        assertEquals(InputExecutionStatus.EXECUTED, settled.result.execution.status)
        assertEquals(SwipeMovement.UNCERTAIN, settled.result.movement)
        assertFalse(settled.result.stableAfter.recognized)
        assertEquals(1, settled.swipeCount)
    }

    private fun fingerprints(vararg values: String): List<ScreenFingerprint> =
        values.map { ScreenFingerprint(recognized = true, value = it) }

    private fun settle(
        before: String,
        samples: List<ScreenFingerprint>,
        stopAfterWaitCount: Int? = null,
    ): SettleHarness {
        var swipeCount = 0
        var captureCount = 0
        var stopped = false
        val waits = mutableListOf<Double>()
        val result =
            executeHostSwipeWithSettleVerification(
                before = ScreenFingerprint(recognized = true, value = before),
                executeSwipe = {
                    swipeCount++
                    InputExecutionResult(InputExecutionStatus.EXECUTED, foreground = true, detailCode = "ADB_EXIT_0")
                },
                shouldStop = { stopped },
                waitBeforeSample = {
                    waits += it
                    if (stopAfterWaitCount != null && waits.size >= stopAfterWaitCount) stopped = true
                },
                captureAfter = { samples[captureCount++] },
            )
        return SettleHarness(result, waits, captureCount, swipeCount)
    }

    private data class SettleHarness(
        val result: HostSwipeSettleResult,
        val waits: List<Double>,
        val captureCount: Int,
        val swipeCount: Int,
    )
}
