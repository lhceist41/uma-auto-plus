package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Skill Point reading")
class SkillPointReadingTest {
    @Nested
    @DisplayName("parseSkillPointsText()")
    inner class ParseTests {
        @Test
        fun `a plain number is accepted`() {
            assertEquals(353, parseSkillPointsText("353"))
            assertEquals(71, parseSkillPointsText("71"))
            assertEquals(2, parseSkillPointsText("2"))
        }

        @Test
        fun `surrounding whitespace and newlines do not matter`() {
            assertEquals(365, parseSkillPointsText("  365 \n"))
        }

        @Test
        fun `a four digit total stays valid - no maximum is imposed`() {
            // Bounding the range would be guesswork; only ambiguity is refused.
            assertEquals(1200, parseSkillPointsText("1200"))
        }

        @Test
        fun `the proven contamination shape is refused instead of concatenated`() {
            // The old parser stripped non-digits and joined: "71 6" became 716, clearing a 350 bar
            // while the true value was 71. This is the exact false-trigger shape from 2026-07-16.
            assertEquals(SKILL_POINTS_UNREADABLE, parseSkillPointsText("71 6"))
            assertEquals(SKILL_POINTS_UNREADABLE, parseSkillPointsText("71\n966"))
        }

        @Test
        fun `a second number anywhere in the crop is refused`() {
            assertEquals(SKILL_POINTS_UNREADABLE, parseSkillPointsText("353 966"))
            assertEquals(SKILL_POINTS_UNREADABLE, parseSkillPointsText("Skill Points 353 Speed 966"))
        }

        @Test
        fun `text with no digits is unreadable`() {
            assertEquals(SKILL_POINTS_UNREADABLE, parseSkillPointsText(""))
            assertEquals(SKILL_POINTS_UNREADABLE, parseSkillPointsText("Skill Points"))
        }

        @Test
        fun `a digit run too large for Int is refused rather than throwing`() {
            assertEquals(SKILL_POINTS_UNREADABLE, parseSkillPointsText("99999999999999"))
        }

        @Test
        fun `non-digit noise around a single number is tolerated`() {
            // One number is unambiguous even with stray glyphs, so it is still trusted.
            assertEquals(353, parseSkillPointsText("|353|"))
        }
    }

    @Nested
    @DisplayName("confirmHighWater()")
    inner class ConfirmTests {
        private val threshold = 350

        @Test
        fun `a legitimate crossing is confirmed and still triggers`() {
            assertEquals(SkillPointConfirmation.CONFIRMED, confirmHighWater(365, threshold))
            assertEquals(SkillPointConfirmation.CONFIRMED, confirmHighWater(threshold, threshold), "exactly at the bar still counts")
        }

        @Test
        fun `the observed garbage cannot trigger against a true low value`() {
            // Candidate said >=350; the fresh read reports the true 71 -> refuse.
            assertEquals(SkillPointConfirmation.REJECTED, confirmHighWater(71, threshold))
        }

        @Test
        fun `a large but genuine value is never refused as if it were garbage`() {
            // No clamp: a real 966 confirms. Only disagreement with the bar refuses.
            assertEquals(SkillPointConfirmation.CONFIRMED, confirmHighWater(966, threshold))
        }

        @Test
        fun `a failed fresh read proves nothing and does not dispatch`() {
            assertEquals(SkillPointConfirmation.UNREADABLE, confirmHighWater(SKILL_POINTS_UNREADABLE, threshold))
            assertEquals(SkillPointConfirmation.UNREADABLE, confirmHighWater(-5, threshold))
        }

        @Test
        fun `a below-bar read is refused, leaving the threshold eligible`() {
            assertEquals(SkillPointConfirmation.REJECTED, confirmHighWater(349, threshold))
            assertEquals(SkillPointConfirmation.REJECTED, confirmHighWater(0, threshold))
        }
    }

    @Nested
    @DisplayName("the gate composed with the trigger policy")
    inner class GateWithPolicyTests {
        private val threshold = 350

        /** Mirrors Campaign.confirmHighWaterCrossing: dispatch only on CONFIRMED, and rewrite on REJECTED. */
        private fun gate(freshRead: Int): Pair<Boolean, Int?> =
            when (confirmHighWater(freshRead, threshold)) {
                SkillPointConfirmation.CONFIRMED -> Pair(true, freshRead)
                SkillPointConfirmation.REJECTED -> Pair(false, freshRead)
                SkillPointConfirmation.UNREADABLE -> Pair(false, null)
            }

        private fun decide(sp: Int, handled: Boolean) =
            decideSkillCheck(
                skillPoints = sp,
                highWaterThreshold = threshold,
                enableSkillPointCheck = true,
                highWaterPlanEnabled = true,
                alreadyHandledHighWater = handled,
                day = 1,
                preFinalsPlanEnabled = true,
                alreadyHandledPreFinals = true,
            )

        @Test
        fun `a contaminated candidate is refused and the rewrite re-arms for a later real crossing`() {
            // Turn N: contaminated 716 crosses the bar, so the policy proposes the plan...
            assertEquals(SkillCheckAction.RUN_PLAN, decide(716, false).action)
            // ...but the gate re-reads the true 71 and refuses, rewriting the trusted value.
            val (dispatched, rewritten) = gate(71)
            assertEquals(false, dispatched, "a contaminated candidate must not open the skill screen")
            assertEquals(71, rewritten)
            // The rewritten value sits below the bar, so the next turn re-arms rather than
            // re-confirming the same bad number, and the threshold is still eligible.
            assertEquals(SkillCheckAction.NONE, decide(rewritten!!, false).action)
            // Turn M: the trainee genuinely crosses. It still fires - nothing was marked handled.
            assertEquals(SkillCheckAction.RUN_PLAN, decide(400, false).action)
        }

        @Test
        fun `a legitimate crossing survives the gate untouched`() {
            assertEquals(SkillCheckAction.RUN_PLAN, decide(365, false).action)
            val (dispatched, trusted) = gate(365)
            assertEquals(true, dispatched)
            assertEquals(365, trusted)
        }

        @Test
        fun `an unreadable confirmation dispatches nothing and writes nothing`() {
            val (dispatched, rewritten) = gate(SKILL_POINTS_UNREADABLE)
            assertEquals(false, dispatched)
            assertEquals(null, rewritten, "a failed read must not overwrite the trusted value")
        }

        @Test
        fun `the gate never fires when the policy did not propose a crossing`() {
            // Below the bar the policy returns NONE, so the gate is never consulted - no extra read.
            assertEquals(SkillCheckAction.NONE, decide(349, false).action)
            assertEquals(SkillCheckAction.NONE, decide(0, false).action)
        }

        @Test
        fun `breakpoint-stop semantics are unchanged and equally gated`() {
            // Plan disabled: a real crossing still throws the legacy breakpoint...
            val stop =
                decideSkillCheck(
                    skillPoints = 400,
                    highWaterThreshold = threshold,
                    enableSkillPointCheck = true,
                    highWaterPlanEnabled = false,
                    alreadyHandledHighWater = false,
                    day = 1,
                    preFinalsPlanEnabled = true,
                    alreadyHandledPreFinals = true,
                )
            assertEquals(SkillCheckAction.BREAKPOINT_STOP, stop.action)
            assertEquals(SkillCheckTrigger.HIGH_WATER, stop.trigger)
            // ...but a contaminated candidate is refused before it can stop a healthy run.
            assertEquals(false, gate(71).first)
        }

        @Test
        fun `an unconfirmed turn is a skip, not a handled threshold`() {
            // The gate must behave like the existing not-on-Main-screen path: decline the action but
            // leave the run's state alone. Marking it handled here would forfeit the career's purchase.
            var handled = false
            val (dispatched, _) = gate(71)
            if (dispatched) handled = true
            assertEquals(false, handled, "a refused confirmation must never mark the threshold handled")
            assertEquals(SkillCheckAction.RUN_PLAN, decide(400, handled).action, "a later real crossing still fires")
        }

        @Test
        fun `a disabled check never reaches the gate`() {
            val off =
                decideSkillCheck(
                    skillPoints = 999,
                    highWaterThreshold = threshold,
                    enableSkillPointCheck = false,
                    highWaterPlanEnabled = true,
                    alreadyHandledHighWater = false,
                    day = 1,
                    preFinalsPlanEnabled = true,
                    alreadyHandledPreFinals = true,
                )
            assertEquals(SkillCheckAction.NONE, off.action)
        }
    }
}
