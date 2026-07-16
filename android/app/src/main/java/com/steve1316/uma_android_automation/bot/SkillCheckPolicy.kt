package com.steve1316.uma_android_automation.bot

/** Why the skill screen was opened. Recorded on every skill-spend telemetry record. */
enum class SkillCheckTrigger {
    /** Skill Points reached the user's configured threshold mid-career. */
    HIGH_WATER,

    /** The turn before the finale season (day 72). */
    SCENARIO_FINALS,

    /** The career-end "Learn" screen, the last purchase of the career. */
    CAREER_COMPLETE,

    /** The Debug Settings skill-buy harness. */
    MANUAL,
}

/** What the run loop does about a skill check this turn. */
enum class SkillCheckAction {
    /** Nothing to do. */
    NONE,

    /** Open the skill screen and run [SkillCheckDecision.planKey]. */
    RUN_PLAN,

    /** Threshold reached with its plan disabled: stop the bot, the long-standing "notify me" behavior. */
    BREAKPOINT_STOP,
}

/**
 * One turn's skill-check decision: what to do, why, and which plan to run.
 *
 * [trigger] and [planKey] are null exactly when [action] is [SkillCheckAction.NONE], and [planKey] is
 * null for [SkillCheckAction.BREAKPOINT_STOP] (no plan runs).
 */
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
 * Pure, Context-free decision behind the mid-career skill checks in `Campaign.performGlobalChecks`.
 *
 * Extracted verbatim from the inline conditions so the precedence and the guards are unit-testable
 * without a live Campaign; it decides ONLY whether to open the skill screen and which plan to run,
 * never which skills to buy. The caller still owns navigation, the Main-screen confirmation, the
 * bounded attempt counters, and the flags - this function reads them, it does not mutate them.
 *
 * Two behaviors here are load-bearing and must not drift:
 *  - Pre-Finals wins over the high-water check when both are due on day 72 (the original order).
 *  - Reaching the threshold with the `skillPointCheck` plan DISABLED is not "do nothing": it stops
 *    the bot via CampaignBreakpointException. Collapsing that into NONE would silently turn a
 *    deliberate stop into an ignored threshold, so it gets its own action.
 *
 * The threshold re-arm (clearing `alreadyHandledHighWater` once points fall back under the bar) stays
 * with the caller, which owns that mutable flag; this function only sees the resulting state.
 *
 * @param skillPoints The trainee's current Skill Points (per-turn OCR).
 * @param highWaterThreshold The user's `skills.skillPointCheck` value.
 * @param enableSkillPointCheck The user's `skills.enableSkillPointCheck` toggle.
 * @param highWaterPlanEnabled Whether the `skillPointCheck` plan itself is enabled.
 * @param alreadyHandledHighWater Whether the high-water check already ran since the last re-arm.
 * @param day The current career turn (1-75).
 * @param preFinalsPlanEnabled Whether the `preFinals` plan is enabled.
 * @param alreadyHandledPreFinals Whether the Pre-Finals check already ran this career.
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
): SkillCheckDecision {
    // Pre-Finals first: this is the original evaluation order in performGlobalChecks.
    if (!alreadyHandledPreFinals && day == PRE_FINALS_DAY && preFinalsPlanEnabled) {
        return SkillCheckDecision(SkillCheckAction.RUN_PLAN, SkillCheckTrigger.SCENARIO_FINALS, PLAN_PRE_FINALS)
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

/** The turn Pre-Finals buying runs on: the last turn before the finale season. */
const val PRE_FINALS_DAY: Int = 72

/** Settings key of the plan the high-water check runs. */
const val PLAN_SKILL_POINT_CHECK: String = "skillPointCheck"

/** Settings key of the plan the Pre-Finals check runs. */
const val PLAN_PRE_FINALS: String = "preFinals"

/** Settings key of the plan the career-end Learn screen runs. */
const val PLAN_CAREER_COMPLETE: String = "careerComplete"
