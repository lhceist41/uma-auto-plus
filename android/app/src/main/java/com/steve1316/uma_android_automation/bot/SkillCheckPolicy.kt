package com.steve1316.uma_android_automation.bot

import com.steve1316.uma_android_automation.bot.campaigns.GrandConcert

enum class SkillCheckTrigger {
    HIGH_WATER,

    SCENARIO_FINALS,

    CAREER_COMPLETE,

    MANUAL,

    /** Adaptive-only: a critical race is 1-2 turns away; spend before it instead of waiting for the threshold. */
    CRITICAL_RACE,

    /** Adaptive-only: a user-planned skill seen on the skill screen is now affordable at its observed price. */
    PLANNED_SKILL_AFFORDABLE,
}

enum class SkillCheckAction {
    NONE,

    RUN_PLAN,

    /** Threshold reached with its plan disabled: stops the bot (the "notify me" behavior). */
    BREAKPOINT_STOP,
}

/** [trigger] and [planKey] are null when [action] is NONE; [planKey] is also null for BREAKPOINT_STOP. */
data class SkillCheckDecision(
    val action: SkillCheckAction,
    val trigger: SkillCheckTrigger? = null,
    val planKey: String? = null,
) {
    companion object {
        val none = SkillCheckDecision(SkillCheckAction.NONE)
    }
}

/**
 * Pure decision behind the mid-career skill checks in `Campaign.performGlobalChecks`: whether to open the
 * skill screen and which plan to run, never which skills to buy. Pre-Finals wins over high-water when both
 * are due on day 72. Reaching the threshold with the `skillPointCheck` plan disabled returns BREAKPOINT_STOP,
 * not NONE, so a deliberate stop is not silently ignored.
 */
fun decideSkillCheck(
    skillPoints: Int,
    highWaterThreshold: Int,
    enableSkillPointCheck: Boolean,
    highWaterPlanEnabled: Boolean,
    alreadyHandledHighWater: Boolean,
    day: Int,
    preFinalsPlanEnabled: Boolean,
    alreadyHandledPreFinals: Boolean,
    criticalRaceDue: Boolean = false,
    affordableSkillDue: Boolean = false,
): SkillCheckDecision {
    if (!alreadyHandledPreFinals && day == PRE_FINALS_DAY && preFinalsPlanEnabled) {
        return SkillCheckDecision(SkillCheckAction.RUN_PLAN, SkillCheckTrigger.SCENARIO_FINALS, PLAN_PRE_FINALS)
    }

    // The critical-race and affordable-skill triggers run the skillPointCheck plan, so they are disabled with it
    // and never breakpoint-stop; that stays exclusive to the high-water plan-disabled branch below.
    if (criticalRaceDue && highWaterPlanEnabled) {
        return SkillCheckDecision(SkillCheckAction.RUN_PLAN, SkillCheckTrigger.CRITICAL_RACE, PLAN_SKILL_POINT_CHECK)
    }
    if (affordableSkillDue && highWaterPlanEnabled) {
        return SkillCheckDecision(SkillCheckAction.RUN_PLAN, SkillCheckTrigger.PLANNED_SKILL_AFFORDABLE, PLAN_SKILL_POINT_CHECK)
    }

    if (!alreadyHandledHighWater && enableSkillPointCheck && skillPoints >= highWaterThreshold) {
        return if (highWaterPlanEnabled) {
            SkillCheckDecision(SkillCheckAction.RUN_PLAN, SkillCheckTrigger.HIGH_WATER, PLAN_SKILL_POINT_CHECK)
        } else {
            SkillCheckDecision(SkillCheckAction.BREAKPOINT_STOP, SkillCheckTrigger.HIGH_WATER)
        }
    }

    return SkillCheckDecision.none
}

/**
 * A session that held SP for a scenario gold spends nothing, so SP never falls below the threshold that normally re-arms high water.
 * Re-arm once SP covers the held gold's live cost. Requiring growth since the hold bounds re-opens when the Main-screen and Learn-screen
 * reads disagree.
 */
fun shouldRearmHighWater(
    alreadyHandledHighWater: Boolean,
    heldGoldCost: Int?,
    heldAtSkillPoints: Int,
    skillPoints: Int,
): Boolean = alreadyHandledHighWater && heldGoldCost != null && skillPoints >= heldGoldCost && skillPoints > heldAtSkillPoints

/**
 * A Grand Concert gold still held when the last turn arrives re-opens the check once, affordable or not, so the session that holds nothing
 * runs before the Finals instead of leaving the skills to the career-end spend. Clearing [heldGoldCost] on the re-arm keeps it to once.
 */
fun shouldRearmAtCareerEnd(
    alreadyHandledHighWater: Boolean,
    heldGoldCost: Int?,
    grandConcert: Boolean,
    day: Int,
): Boolean = alreadyHandledHighWater && heldGoldCost != null && grandConcert && day == GrandConcert.CAREER_END_TURN

const val PRE_FINALS_DAY: Int = 72

const val PLAN_SKILL_POINT_CHECK: String = "skillPointCheck"

const val PLAN_PRE_FINALS: String = "preFinals"

const val PLAN_CAREER_COMPLETE: String = "careerComplete"
