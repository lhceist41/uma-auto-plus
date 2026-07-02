package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CustomImageUtils.runOcrRetryLadder] - the bounded threshold-relaxation retry behind
 * every retryOcrOnMisread call site.
 *
 * The load-bearing invariants: no attempt may run at an effective threshold of 255 or above (a 255
 * THRESH_BINARY pass blacks out every pixel, so the OCR result is guaranteed garbage), and on
 * exhaustion the FIRST attempt's result is returned, never a later one - later attempts use
 * progressively blacker binarizations, so the earliest read carries the most information. The same
 * file shipped a retry loop that let a later failed attempt clobber an earlier success four days
 * before these tests were written; this pins the contract.
 */
@DisplayName("runOcrRetryLadder Tests")
class OcrRetryLadderTest {
    /** Runs the ladder recording every increment attempted; results are "r<increment>". */
    private fun run(
        baseThreshold: Int,
        maxSteps: Int = 8,
        acceptWhen: (String) -> Boolean = { false },
    ): Pair<String, List<Double>> {
        val increments = mutableListOf<Double>()
        val result =
            CustomImageUtils.runOcrRetryLadder(baseThreshold, maxSteps, acceptWhen) { increment ->
                increments.add(increment)
                "r${increment.toInt()}"
            }
        return Pair(result, increments)
    }

    @Test
    @DisplayName("Acceptable first read returns immediately with a single attempt")
    fun `first acceptable short-circuits`() {
        val (result, increments) = run(baseThreshold = 230, acceptWhen = { true })
        assertEquals("r0", result)
        assertEquals(listOf(0.0), increments)
    }

    @Test
    @DisplayName("At the shipped default threshold 230, no attempt reaches an effective threshold of 255")
    fun `default threshold never binarizes all-black`() {
        val (result, increments) = run(baseThreshold = 230)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0), increments)
        assertEquals(true, increments.all { 230 + it < 255.0 })
        assertEquals("r0", result)
    }

    @Test
    @DisplayName("A read that becomes acceptable mid-ladder returns that result and stops")
    fun `mid-ladder success returns that attempt`() {
        val (result, increments) = run(baseThreshold = 230, acceptWhen = { it == "r10" })
        assertEquals("r10", result)
        assertEquals(listOf(0.0, 5.0, 10.0), increments)
    }

    @Test
    @DisplayName("Low base threshold is capped by maxSteps, not the 255 bound")
    fun `low threshold capped by step count`() {
        val (_, increments) = run(baseThreshold = 0, maxSteps = 8)
        assertEquals(listOf(0.0, 5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0), increments)
    }

    @Test
    @DisplayName("Base threshold 249 keeps its one legitimate retry at effective 254")
    fun `threshold 249 gets one retry`() {
        val (_, increments) = run(baseThreshold = 249)
        assertEquals(listOf(0.0, 5.0), increments)
    }

    @Test
    @DisplayName("Base threshold 250 leaves no room below 255: no retries at all")
    fun `threshold 250 gets no retries`() {
        val (result, increments) = run(baseThreshold = 250)
        assertEquals(listOf(0.0), increments)
        assertEquals("r0", result)
    }

    @Test
    @DisplayName("Base threshold at or above 255 still performs the initial attempt only")
    fun `threshold 255 gets initial attempt only`() {
        val (_, increments) = run(baseThreshold = 255)
        assertEquals(listOf(0.0), increments)
    }

    @Test
    @DisplayName("Exhaustion returns the FIRST attempt's result, not the last")
    fun `exhaustion returns first result`() {
        var calls = 0
        val result =
            CustomImageUtils.runOcrRetryLadder(230, 8, { false }) { _ ->
                calls++
                "attempt$calls"
            }
        assertEquals("attempt1", result)
        assertEquals(5, calls)
    }
}
