package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [classifyCareerOutcome] - the `[CAREER_END]` ledger's win/force-end/incomplete label.
 *
 * The load-bearing invariant: a non-COMPLETE result code is ALWAYS INCOMPLETE and is never counted as
 * a force-end, even when the force-end flag is set (a stopped-on-mandatory-failure run throws and ends
 * as an exception, but it is still a bot stop, not a clean career outcome). The result code is checked
 * before the flag for exactly this reason.
 */
@DisplayName("classifyCareerOutcome Tests")
class CareerOutcomeTest {
    @Test
    @DisplayName("COMPLETE with no force-end flag is COMPLETED (win or unflagged force-end, split later)")
    fun `complete without force-end is completed`() {
        assertEquals("COMPLETED", classifyCareerOutcome(TaskResultCode.TASK_RESULT_COMPLETE, careerForceEnded = false))
    }

    @Test
    @DisplayName("COMPLETE with the force-end flag is FORCE_END")
    fun `complete with force-end is force_end`() {
        assertEquals("FORCE_END", classifyCareerOutcome(TaskResultCode.TASK_RESULT_COMPLETE, careerForceEnded = true))
    }

    @Test
    @DisplayName("Every non-COMPLETE result code is INCOMPLETE regardless of the flag")
    fun `non-complete codes are incomplete`() {
        val nonComplete =
            TaskResultCode.values().filter { it != TaskResultCode.TASK_RESULT_COMPLETE }
        for (code in nonComplete) {
            assertEquals("INCOMPLETE", classifyCareerOutcome(code, careerForceEnded = false), "expected INCOMPLETE for $code")
            // The result-code guard wins over the flag: a thrown mandatory-failure that surfaces as an
            // exception is a bot stop, not a force-end outcome, so it must not be labeled FORCE_END.
            assertEquals("INCOMPLETE", classifyCareerOutcome(code, careerForceEnded = true), "expected INCOMPLETE for $code even when flagged")
        }
    }

    @Test
    @DisplayName("Fingerprint is key-order independent and 10 hex chars")
    fun `fingerprint ignores map order`() {
        val a = outcomeConfigFingerprint("1.0.0", linkedMapOf("x" to "1", "y" to "2"))
        val b = outcomeConfigFingerprint("1.0.0", linkedMapOf("y" to "2", "x" to "1"))
        assertEquals(a, b)
        assertEquals(10, a.length)
        assertEquals(true, a.all { it in "0123456789abcdef" })
    }

    @Test
    @DisplayName("Fingerprint changes when a tunable or the app version changes")
    fun `fingerprint is sensitive to value and version`() {
        val base = outcomeConfigFingerprint("1.0.0", mapOf("x" to "1"))
        assertEquals(false, base == outcomeConfigFingerprint("1.0.0", mapOf("x" to "2")))
        assertEquals(false, base == outcomeConfigFingerprint("1.0.1", mapOf("x" to "1")))
    }

    @Test
    @DisplayName("COMPLETED with a swept finale (wins == races > 0) is WIN")
    fun `completed with swept finale is win`() {
        assertEquals("WIN", classifyCareerQuality("COMPLETED", finaleRaces = 3, finaleWins = 3))
        assertEquals("WIN", classifyCareerQuality("COMPLETED", finaleRaces = 1, finaleWins = 1))
    }

    @Test
    @DisplayName("COMPLETED that reached the finale but dropped a race is FINALE_LOST")
    fun `completed with a lost finale race is finale_lost`() {
        assertEquals("FINALE_LOST", classifyCareerQuality("COMPLETED", finaleRaces = 3, finaleWins = 2))
        assertEquals("FINALE_LOST", classifyCareerQuality("COMPLETED", finaleRaces = 3, finaleWins = 0))
        assertEquals("FINALE_LOST", classifyCareerQuality("COMPLETED", finaleRaces = 1, finaleWins = 0))
    }

    @Test
    @DisplayName("COMPLETED with no observed finale stays COMPLETED (Unity Cup / Trackblazer never tag FINALE)")
    fun `completed with no finale stays completed`() {
        assertEquals("COMPLETED", classifyCareerQuality("COMPLETED", finaleRaces = 0, finaleWins = 0))
    }

    @Test
    @DisplayName("Non-COMPLETED outcomes pass through unchanged regardless of finale counts")
    fun `non-completed outcomes pass through`() {
        assertEquals("FORCE_END", classifyCareerQuality("FORCE_END", finaleRaces = 0, finaleWins = 0))
        assertEquals("INCOMPLETE", classifyCareerQuality("INCOMPLETE", finaleRaces = 3, finaleWins = 3))
        // A force-end that somehow also observed finale races must never be relabeled a WIN.
        assertEquals("FORCE_END", classifyCareerQuality("FORCE_END", finaleRaces = 3, finaleWins = 3))
    }
}
