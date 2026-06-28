package com.steve1316.uma_android_automation.bot

/**
 * The career-outcome label emitted in the `[CAREER_END]` ledger, derived from the task result code
 * and whether the bot confirmed a force-end at its source.
 *
 * - `INCOMPLETE` - the run did not finish a career under bot control: a user stop (breakpoint /
 *   stop-at-date / manual) or a bot failure (watchdog, timeout, unhandled exception). These must
 *   NEVER be counted as force-ends; only the result code separates them, so branch on it first.
 * - `FORCE_END` - a force-end the bot observed at its source. Today that is only a lost mandatory
 *   race the game will not let us retry past ([Campaign] sets the flag in handleTryAgainDialog).
 * - `COMPLETED` - reached the career-end screen with no confirmed force-end: a true win OR an
 *   unflagged early force-end (a fan / Result-Pts checkpoint miss is invisible at its trigger).
 *   `turn` is the discriminator here (a full arc ends near the scenario max, a force-end ends early).
 *
 * Pure and side-effect-free so the three branches are unit-testable without a live [Campaign].
 */
internal fun classifyCareerOutcome(resultCode: TaskResultCode, careerForceEnded: Boolean): String =
    when {
        resultCode != TaskResultCode.TASK_RESULT_COMPLETE -> "INCOMPLETE"
        careerForceEnded -> "FORCE_END"
        else -> "COMPLETED"
    }
