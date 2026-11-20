package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CustomImageUtils.runFailureChanceReads] - the read budget behind every
 * findTrainingFailureChance call site.
 *
 * Two invariants carry the weight. The first valid read must short-circuit, because a later failed
 * attempt clobbering an earlier success turns tries=3 into a miss instead of hardening the read. And a
 * failed read must be paced before the next attempt: right after a loading/"Connecting" round-trip the
 * failure bubble is still mid-render, so unpaced retries re-read the same blank region and burn the
 * whole budget in ~250ms, costing the turn its training analysis. The pace must stay off the common
 * path (no wait before the first attempt, none after a success) and off the tail (no wait after the
 * last attempt, where no read is left to benefit).
 */
@DisplayName("runFailureChanceReads Tests")
class FailureChanceReadsTest {
    private val invalid = CustomImageUtils.INVALID_FAILURE_CHANCE

    /** Runs the budget over [reads], recording the attempt numbers made and the pauses taken. */
    private fun run(tries: Int, reads: List<Int>): Triple<Int, List<Int>, Int> {
        val attempts = mutableListOf<Int>()
        var paces = 0
        val result =
            CustomImageUtils.runFailureChanceReads(
                tries = tries,
                attempt = { i ->
                    attempts.add(i)
                    reads.getOrElse(i - 1) { invalid }
                },
                onInvalid = {},
                pace = { paces++ },
            )
        return Triple(result, attempts, paces)
    }

    @Test
    @DisplayName("A valid first read returns immediately with no pacing wait")
    fun `first valid read short-circuits`() {
        val (result, attempts, paces) = run(tries = 3, reads = listOf(25))
        assertEquals(25, result)
        assertEquals(listOf(1), attempts, "must not spend a second read after a success")
        assertEquals(0, paces, "the common path must never pay the pacing wait")
    }

    @Test
    @DisplayName("A 0% read is valid and short-circuits (0 is a real failure chance, not a miss)")
    fun `zero is a valid read`() {
        val (result, attempts, paces) = run(tries = 3, reads = listOf(0))
        assertEquals(0, result)
        assertEquals(listOf(1), attempts)
        assertEquals(0, paces)
    }

    @Test
    @DisplayName("One invalid read with budget remaining paces exactly once before retrying")
    fun `invalid read paces once then retries`() {
        val (result, attempts, paces) = run(tries = 3, reads = listOf(invalid, 40))
        assertEquals(40, result, "the later valid read is returned, never overwritten")
        assertEquals(listOf(1, 2), attempts)
        assertEquals(1, paces, "exactly one wait, between the failed read and the retry")
    }

    @Test
    @DisplayName("The final failed attempt does not schedule a trailing wait")
    fun `no pace after the last attempt`() {
        val (result, attempts, paces) = run(tries = 3, reads = listOf(invalid, invalid, invalid))
        assertEquals(invalid, result)
        assertEquals(listOf(1, 2, 3), attempts, "the budget is spent exactly once per try")
        assertEquals(2, paces, "n-1 waits for n attempts - nothing waits after the last read")
    }

    @Test
    @DisplayName("A single-try budget never paces and never retries")
    fun `single try is unpaced`() {
        val (result, attempts, paces) = run(tries = 1, reads = listOf(invalid))
        assertEquals(invalid, result)
        assertEquals(listOf(1), attempts)
        assertEquals(0, paces)
    }

    @Test
    @DisplayName("A valid read on the last attempt is returned and paced n-1 times")
    fun `valid last read is returned`() {
        val (result, attempts, paces) = run(tries = 3, reads = listOf(invalid, invalid, 15))
        assertEquals(15, result)
        assertEquals(listOf(1, 2, 3), attempts)
        assertEquals(2, paces)
    }

    @Test
    @DisplayName("The retry count stays bounded by the budget")
    fun `attempts never exceed the budget`() {
        for (tries in 1..5) {
            val (_, attempts, paces) = run(tries = tries, reads = emptyList())
            assertEquals(tries, attempts.size, "budget $tries must run exactly $tries attempts")
            assertEquals(tries - 1, paces, "budget $tries must pace exactly ${tries - 1} times")
        }
    }

    @Test
    @DisplayName("A non-positive budget is clamped to a single attempt")
    fun `budget is clamped`() {
        for (tries in listOf(0, -1)) {
            val (result, attempts, paces) = run(tries = tries, reads = listOf(30))
            assertEquals(30, result)
            assertEquals(listOf(1), attempts, "tries=$tries must still perform one read")
            assertEquals(0, paces)
        }
    }

    @Test
    @DisplayName("Every invalid attempt is reported exactly once, successes are not")
    fun `onInvalid fires per failed attempt only`() {
        val reported = mutableListOf<Int>()
        val result =
            CustomImageUtils.runFailureChanceReads(
                tries = 3,
                attempt = { i -> if (i < 3) invalid else 50 },
                onInvalid = { i -> reported.add(i) },
                pace = {},
            )
        assertEquals(50, result)
        assertEquals(listOf(1, 2), reported)
    }

    @Test
    @DisplayName("The digits right before the % sign are the value, not every digit joined")
    fun `parse takes the digits before the percent sign`() {
        assertEquals(14, CustomImageUtils.parseFailureChance("5 14%", requirePercent = true))
        assertEquals(14, CustomImageUtils.parseFailureChance("14 %", requirePercent = true))
        assertEquals(14, CustomImageUtils.parseFailureChance("Failure\n14%", requirePercent = true))
        assertEquals(0, CustomImageUtils.parseFailureChance("0%", requirePercent = true))
        assertEquals(0, CustomImageUtils.parseFailureChance("O%", requirePercent = true), "an O read for a zero is a zero")
        assertEquals(100, CustomImageUtils.parseFailureChance("100%", requirePercent = true))
    }

    @Test
    @DisplayName("A value above 100 is rejected, never trimmed to two digits")
    fun `parse rejects values above 100`() {
        for (requirePercent in listOf(true, false)) {
            assertNull(CustomImageUtils.parseFailureChance("120%", requirePercent), "120% requirePercent=$requirePercent")
            assertNull(CustomImageUtils.parseFailureChance("101%", requirePercent), "101% requirePercent=$requirePercent")
            assertNull(CustomImageUtils.parseFailureChance("5 143%", requirePercent), "a stray digit before 43% must not give 14 or 43")
        }
        assertNull(CustomImageUtils.parseFailureChance("143", requirePercent = false), "\"14%\" read as 143 is not guessed as 14")
        assertNull(CustomImageUtils.parseFailureChance("1451 14", requirePercent = false), "the first digit run decides, and 1451 is invalid")
    }

    @Test
    @DisplayName("A read with no % sign is rejected where the sign is required, else its first digit run is taken")
    fun `parse without a percent sign`() {
        assertNull(CustomImageUtils.parseFailureChance("U70", requirePercent = true), "a 0% bubble read as U70 must not become 70%")
        assertEquals(70, CustomImageUtils.parseFailureChance("U70", requirePercent = false))
        assertEquals(5, CustomImageUtils.parseFailureChance("5 14", requirePercent = false), "the first run, not 514")
        assertNull(CustomImageUtils.parseFailureChance("14", requirePercent = true))
    }

    @Test
    @DisplayName("No digits is no read")
    fun `parse with no digits`() {
        for (text in listOf("", "%", "Failure", "\n")) {
            assertNull(CustomImageUtils.parseFailureChance(text, requirePercent = false), "\"$text\"")
            assertNull(CustomImageUtils.parseFailureChance(text, requirePercent = true), "\"$text\"")
        }
    }
}
