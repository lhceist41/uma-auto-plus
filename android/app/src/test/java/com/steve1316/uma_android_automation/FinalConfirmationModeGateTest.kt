package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.utils.FinalConfirmationMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure N0 flow-decision tests. The final Start Career press is reachable only from
 * NORMAL_CAREER_VERIFIED; Independent Training earns exactly one corrective tab tap and then a fresh
 * re-read whose decision can never ask for another tap.
 */
@DisplayName("Final Confirmation mode gate decisions")
class FinalConfirmationModeGateTest {
    // --- test 20 / 8: only NORMAL_CAREER_VERIFIED authorizes the final Start Career press -----------
    @Test
    fun `only normal career authorizes the final start`() {
        assertTrue(FinalConfirmationModeGate.finalStartCareerAuthorized(FinalConfirmationMode.NORMAL_CAREER_VERIFIED))
        assertFalse(FinalConfirmationModeGate.finalStartCareerAuthorized(FinalConfirmationMode.INDEPENDENT_TRAINING_VERIFIED))
        assertFalse(FinalConfirmationModeGate.finalStartCareerAuthorized(FinalConfirmationMode.MODE_UNRECOGNIZED))
    }

    // --- first-look decisions -----------------------------------------------------------------------
    @Test
    fun `normal career proceeds on first look`() {
        assertEquals(
            FinalConfirmationModeGate.Decision.Proceed,
            FinalConfirmationModeGate.decide(FinalConfirmationMode.NORMAL_CAREER_VERIFIED),
        )
    }

    @Test
    fun `independent training requests a corrective switch on first look`() {
        assertEquals(
            FinalConfirmationModeGate.Decision.CorrectToNormalCareer,
            FinalConfirmationModeGate.decide(FinalConfirmationMode.INDEPENDENT_TRAINING_VERIFIED),
        )
    }

    // --- test (5-adjacent): unrecognized first look refuses, named ----------------------------------
    @Test
    fun `unrecognized first look refuses with a named reason`() {
        val d = FinalConfirmationModeGate.decide(FinalConfirmationMode.MODE_UNRECOGNIZED)
        assertInstanceOf(FinalConfirmationModeGate.Decision.Refuse::class.java, d)
        assertTrue((d as FinalConfirmationModeGate.Decision.Refuse).reason.contains("MODE_UNRECOGNIZED"))
    }

    // --- test 12: corrective fresh Normal Career -> proceed -----------------------------------------
    @Test
    fun `after correction a fresh normal career proceeds`() {
        assertEquals(
            FinalConfirmationModeGate.Decision.Proceed,
            FinalConfirmationModeGate.decideAfterCorrection(FinalConfirmationMode.NORMAL_CAREER_VERIFIED),
        )
    }

    // --- test 13: corrective fresh Independent Training -> MODE_SWITCH_FAILED / fail closed ----------
    @Test
    fun `after correction a still-independent read fails closed as a switch failure`() {
        val d = FinalConfirmationModeGate.decideAfterCorrection(FinalConfirmationMode.INDEPENDENT_TRAINING_VERIFIED)
        assertInstanceOf(FinalConfirmationModeGate.Decision.Refuse::class.java, d)
        assertTrue((d as FinalConfirmationModeGate.Decision.Refuse).reason.contains("MODE_SWITCH_FAILED"))
    }

    // --- test 14: corrective fresh unrecognized -> fail closed --------------------------------------
    @Test
    fun `after correction an unrecognized read fails closed`() {
        val d = FinalConfirmationModeGate.decideAfterCorrection(FinalConfirmationMode.MODE_UNRECOGNIZED)
        assertInstanceOf(FinalConfirmationModeGate.Decision.Refuse::class.java, d)
    }

    // --- test 10: the correction is one-shot -- no fresh read ever asks for another tap -------------
    @Test
    fun `correction is bounded to one tap`() {
        assertEquals(1, FinalConfirmationModeGate.MAX_MODE_CORRECTION_TAPS)
        for (mode in FinalConfirmationMode.entries) {
            val d = FinalConfirmationModeGate.decideAfterCorrection(mode)
            assertFalse(
                d is FinalConfirmationModeGate.Decision.CorrectToNormalCareer,
                "decideAfterCorrection must never request another correction (mode=$mode)",
            )
        }
    }

    // --- test 20 (structural): the only path to Proceed is a verified Normal Career -----------------
    @Test
    fun `proceed is emitted only for a normal career read`() {
        for (mode in FinalConfirmationMode.entries) {
            val first = FinalConfirmationModeGate.decide(mode)
            val corrected = FinalConfirmationModeGate.decideAfterCorrection(mode)
            if (first is FinalConfirmationModeGate.Decision.Proceed || corrected is FinalConfirmationModeGate.Decision.Proceed) {
                assertEquals(FinalConfirmationMode.NORMAL_CAREER_VERIFIED, mode, "Proceed leaked for mode=$mode")
            }
        }
    }
}
