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
}
