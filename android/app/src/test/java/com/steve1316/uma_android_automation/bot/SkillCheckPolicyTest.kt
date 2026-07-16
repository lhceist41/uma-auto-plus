package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [decideSkillCheck] - the per-turn "should the skill screen open, and why" decision.
 *
 * This seam was extracted from inline conditions that already shipped, so the tests exist to pin the
 * CURRENT behavior, not to describe a new policy. The two that carry the most weight:
 *
 *  - Reaching the threshold with the plan DISABLED is a deliberate bot stop (CampaignBreakpointException),
 *    not a no-op. A refactor that collapsed it into NONE would silently convert "notify me at 1000 SP"
 *    into "ignore 1000 SP", and the user would only find out by finding the bot still running.
 *  - Pre-Finals outranks the high-water check on day 72, matching the original evaluation order.
 */
@DisplayName("decideSkillCheck Tests")
class SkillCheckPolicyTest {
    /** Defaults describe a healthy mid-career turn with nothing due; each test varies one axis. */
    private fun decide(
        skillPoints: Int = 0,
        highWaterThreshold: Int = 350,
        enableSkillPointCheck: Boolean = true,
        highWaterPlanEnabled: Boolean = true,
        alreadyHandledHighWater: Boolean = false,
        day: Int = 30,
        preFinalsPlanEnabled: Boolean = true,
        alreadyHandledPreFinals: Boolean = false,
    ) = decideSkillCheck(
        skillPoints = skillPoints,
        highWaterThreshold = highWaterThreshold,
        enableSkillPointCheck = enableSkillPointCheck,
        highWaterPlanEnabled = highWaterPlanEnabled,
        alreadyHandledHighWater = alreadyHandledHighWater,
        day = day,
        preFinalsPlanEnabled = preFinalsPlanEnabled,
        alreadyHandledPreFinals = alreadyHandledPreFinals,
    )

    @Nested
    @DisplayName("high-water threshold")
    inner class HighWaterTests {
        @Test
        fun `the global check being off means no action at any point total`() {
            val d = decide(skillPoints = 9999, enableSkillPointCheck = false)
            assertEquals(SkillCheckAction.NONE, d.action)
            assertNull(d.trigger)
            assertNull(d.planKey)
        }

        @Test
        fun `below the threshold does nothing`() {
            assertEquals(SkillCheckAction.NONE, decide(skillPoints = 349, highWaterThreshold = 350).action)
        }

        @Test
        fun `reaching the threshold with the plan enabled runs it`() {
            val d = decide(skillPoints = 350, highWaterThreshold = 350, highWaterPlanEnabled = true)
            assertEquals(SkillCheckAction.RUN_PLAN, d.action)
            assertEquals(SkillCheckTrigger.HIGH_WATER, d.trigger)
            assertEquals(PLAN_SKILL_POINT_CHECK, d.planKey)
        }

        @Test
        fun `exceeding the threshold with the plan enabled runs it`() {
            assertEquals(SkillCheckAction.RUN_PLAN, decide(skillPoints = 1200, highWaterThreshold = 1000).action)
        }

        @Test
        fun `reaching the threshold with the plan DISABLED stops the bot instead of doing nothing`() {
            // The legacy "stop at the threshold so I can buy by hand" behavior. Collapsing this into
            // NONE would silently ignore the threshold the user set.
            val d = decide(skillPoints = 1000, highWaterThreshold = 1000, highWaterPlanEnabled = false)
            assertEquals(SkillCheckAction.BREAKPOINT_STOP, d.action)
            assertEquals(SkillCheckTrigger.HIGH_WATER, d.trigger)
            assertNull(d.planKey, "a breakpoint stop runs no plan")
        }

        @Test
        fun `already handled does nothing until the caller re-arms`() {
            assertEquals(SkillCheckAction.NONE, decide(skillPoints = 1000, highWaterThreshold = 350, alreadyHandledHighWater = true).action)
        }

        @Test
        fun `a re-armed caller triggers again on the same points`() {
            // The caller clears its flag once points fall back under the bar; this is the state it
            // passes afterwards.
            val d = decide(skillPoints = 1000, highWaterThreshold = 350, alreadyHandledHighWater = false)
            assertEquals(SkillCheckAction.RUN_PLAN, d.action)
            assertEquals(SkillCheckTrigger.HIGH_WATER, d.trigger)
        }

        @Test
        fun `the disabled global check outranks a disabled plan (no breakpoint when switched off)`() {
            assertEquals(SkillCheckAction.NONE, decide(skillPoints = 9999, enableSkillPointCheck = false, highWaterPlanEnabled = false).action)
        }
    }

    @Nested
    @DisplayName("pre-finals (day 72)")
    inner class PreFinalsTests {
        @Test
        fun `day 72 with the plan enabled runs pre-finals`() {
            val d = decide(day = PRE_FINALS_DAY)
            assertEquals(SkillCheckAction.RUN_PLAN, d.action)
            assertEquals(SkillCheckTrigger.SCENARIO_FINALS, d.trigger)
            assertEquals(PLAN_PRE_FINALS, d.planKey)
        }

        @Test
        fun `only day 72 triggers pre-finals`() {
            for (day in listOf(1, 71, 73, 75)) {
                val d = decide(day = day)
                assertEquals(SkillCheckAction.NONE, d.action, "day $day must not trigger pre-finals")
            }
        }

        @Test
        fun `pre-finals runs once per career`() {
            assertEquals(SkillCheckAction.NONE, decide(day = PRE_FINALS_DAY, alreadyHandledPreFinals = true).action)
        }

        @Test
        fun `a disabled pre-finals plan does nothing`() {
            assertEquals(SkillCheckAction.NONE, decide(day = PRE_FINALS_DAY, preFinalsPlanEnabled = false).action)
        }
    }

    @Nested
    @DisplayName("precedence")
    inner class PrecedenceTests {
        @Test
        fun `pre-finals wins over the high-water check when both are due`() {
            val d = decide(skillPoints = 9999, highWaterThreshold = 350, day = PRE_FINALS_DAY)
            assertEquals(SkillCheckTrigger.SCENARIO_FINALS, d.trigger)
            assertEquals(PLAN_PRE_FINALS, d.planKey)
        }

        @Test
        fun `pre-finals wins even when the high-water plan is disabled (no breakpoint on day 72)`() {
            // Order matters: the original checked pre-finals first, so a day-72 threshold hit with a
            // disabled plan buys pre-finals rather than stopping the bot.
            val d = decide(skillPoints = 9999, day = PRE_FINALS_DAY, highWaterPlanEnabled = false)
            assertEquals(SkillCheckAction.RUN_PLAN, d.action)
            assertEquals(SkillCheckTrigger.SCENARIO_FINALS, d.trigger)
        }

        @Test
        fun `once pre-finals is handled, day 72 falls through to the high-water check`() {
            val d = decide(skillPoints = 9999, highWaterThreshold = 350, day = PRE_FINALS_DAY, alreadyHandledPreFinals = true)
            assertEquals(SkillCheckAction.RUN_PLAN, d.action)
            assertEquals(SkillCheckTrigger.HIGH_WATER, d.trigger)
        }
    }

    @Nested
    @DisplayName("decision shape")
    inner class ShapeTests {
        @Test
        fun `NONE never names a trigger or a plan`() {
            val d = decide(skillPoints = 0)
            assertEquals(SkillCheckAction.NONE, d.action)
            assertNull(d.trigger)
            assertNull(d.planKey)
        }

        @Test
        fun `RUN_PLAN always names both a trigger and a plan`() {
            for (d in listOf(decide(skillPoints = 9999), decide(day = PRE_FINALS_DAY))) {
                assertEquals(SkillCheckAction.RUN_PLAN, d.action)
                assertEquals(true, d.trigger != null, "RUN_PLAN must name a trigger")
                assertEquals(true, d.planKey != null, "RUN_PLAN must name a plan")
            }
        }

        @Test
        fun `the plan key always matches its trigger`() {
            assertEquals(PLAN_SKILL_POINT_CHECK, decide(skillPoints = 9999).planKey)
            assertEquals(PLAN_PRE_FINALS, decide(day = PRE_FINALS_DAY).planKey)
        }
    }
}
