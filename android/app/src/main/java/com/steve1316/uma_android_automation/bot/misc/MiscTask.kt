package com.steve1316.uma_android_automation.bot.misc

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.Task
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode

/**
 * Abstract base class for non-career ("misc") automation tasks.
 *
 * ## Architecture
 *
 * The existing [com.steve1316.uma_android_automation.bot.Campaign] subclasses (Trackblazer,
 * UnityCup, UraFinale) automate the *inside* of a single career run. Misc tasks automate
 * the standalone game modes reachable from the Home Screen:
 *
 *   - [DailyRaceTask]       — `Race` tab → `Daily Program` → `Daily Races` (3 attempts/day)
 *   - [DailyLegendRaceTask] — `Race` tab → `Daily Program` → `Daily Legend Races` (1/day)  [future]
 *   - [TeamTrialsTask]      — `Race` tab → `Team Trials` (RP-gated, up to 5 per full RP)   [future]
 *
 * Each concrete subclass implements a finite-state machine in the same shape as
 * [com.steve1316.uma_android_automation.CareerLaunchNavigator]: a screen-state enum, a
 * [detectScreenState]-equivalent, and a `when (state)` dispatch inside [process]. Shared
 * concerns — incidental popup dismissal, stuck-state watchdogs, wait helpers — live here.
 *
 * ## Expected starting screen
 *
 * Every misc task expects to begin on the game's Home Screen. Callers (typically the run
 * queue dispatcher) should navigate there before invoking [start]. The first call to
 * [process] asserts we're on Home Screen and bails cleanly if not — misc tasks should
 * never try to "recover" from an unexpected starting state because their state machines
 * are narrower than [com.steve1316.uma_android_automation.CareerLaunchNavigator] and a
 * wrong starting state indicates a serious scheduling bug upstream.
 *
 * ## Safety rails
 *
 * - [MAX_ITERATIONS_SAFETY] caps the state machine at N loop iterations before bailing.
 * - [MAX_CONSECUTIVE_UNKNOWNS] caps consecutive UNKNOWN detections before bailing.
 * - [MAX_STUCK_ITERATIONS] caps consecutive iterations stuck in the same non-goal state.
 *
 * These match the values used by the career navigator so behaviour is consistent across
 * the bot's two finite-state machines.
 *
 * @property game The [Game] instance used for bot interaction.
 */
abstract class MiscTask(game: Game) : Task(game) {
    companion object {
        /** Maximum number of loop iterations before forcing a [TaskResult.Error] timeout. */
        const val MAX_ITERATIONS_SAFETY: Int = 300

        /** Maximum consecutive UNKNOWN screen detections before bailing. */
        const val MAX_CONSECUTIVE_UNKNOWNS: Int = 5

        /** Maximum consecutive iterations stuck in the same non-goal state before bailing. */
        const val MAX_STUCK_ITERATIONS: Int = 15
    }

    protected val TAG: String = "[${MainActivity.loggerTag}]${this::class.simpleName}"

    /** Iteration counter for the state machine. Incremented at the top of every [process] call. */
    protected var iterationsCompleted: Int = 0

    /** How many consecutive iterations have produced an UNKNOWN screen detection. */
    protected var consecutiveUnknowns: Int = 0

    /** How many consecutive iterations we've been stuck in the same non-goal state. */
    protected var iterationsWithoutProgress: Int = 0

    /** The last non-UNKNOWN screen state we detected. Used to measure progress. */
    protected var lastKnownStateName: String = ""

    /**
     * Shared popup-dismissal helper for incidental popups (login bonus, gift received,
     * campaign announcement, mission complete, etc.) that can interrupt any misc flow.
     *
     * Default implementation delegates to [Task.tryHandleAllDialogs] which covers most
     * standard dialogs the bot already knows how to dismiss. Subclasses can override to
     * add mode-specific popup handling if needed.
     *
     * @return True if at least one popup was handled; false if none were detected.
     */
    protected open fun handleIncidentalPopups(): Boolean {
        return try {
            tryHandleAllDialogs(timeoutMs = 5000)
        } catch (e: IllegalStateException) {
            // An unhandled dialog means the state machine should log it and continue;
            // misc tasks don't abort on unknown dialogs the way Campaign can.
            MessageLog.w(TAG, "[WARN] handleIncidentalPopups:: Unhandled dialog: ${e.message}")
            false
        }
    }

    /**
     * Update progress tracking based on the detected state name.
     *
     * Call this at the top of every [process] iteration, after detecting the current
     * screen state. Increments [iterationsWithoutProgress] when we see the same
     * non-terminal state repeatedly, and [consecutiveUnknowns] when we can't identify
     * the screen at all.
     *
     * @param stateName The name of the current screen state (typically `state.name`).
     * @param isUnknown Whether the state is the UNKNOWN fallback.
     */
    protected fun trackProgress(stateName: String, isUnknown: Boolean) {
        if (isUnknown) {
            consecutiveUnknowns += 1
        } else {
            consecutiveUnknowns = 0
        }

        if (stateName == lastKnownStateName && !isUnknown) {
            iterationsWithoutProgress += 1
        } else {
            iterationsWithoutProgress = 0
            if (!isUnknown) {
                lastKnownStateName = stateName
            }
        }
    }

    /**
     * Check whether any of the safety rails have tripped. Call at the top of [process].
     *
     * @return A [TaskResult.Error] to return from [process] if safety tripped, or null to continue.
     */
    protected fun checkSafetyRails(): TaskResult? {
        iterationsCompleted += 1

        if (iterationsCompleted > MAX_ITERATIONS_SAFETY) {
            val msg = "Safety bailout: exceeded $MAX_ITERATIONS_SAFETY iterations."
            MessageLog.w(TAG, "[WARN] $msg")
            return TaskResult.Error(TaskResultCode.TASK_RESULT_TIMED_OUT, msg)
        }

        if (consecutiveUnknowns > MAX_CONSECUTIVE_UNKNOWNS) {
            val msg =
                "Safety bailout: $MAX_CONSECUTIVE_UNKNOWNS consecutive UNKNOWN screen detections. " +
                    "Game UI may have changed or the bot is on an unexpected screen."
            MessageLog.w(TAG, "[WARN] $msg")
            return TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, msg)
        }

        if (iterationsWithoutProgress > MAX_STUCK_ITERATIONS) {
            val msg =
                "Safety bailout: stuck in state $lastKnownStateName for $MAX_STUCK_ITERATIONS " +
                    "consecutive iterations without progress."
            MessageLog.w(TAG, "[WARN] $msg")
            return TaskResult.Error(TaskResultCode.TASK_RESULT_TIMED_OUT, msg)
        }

        return null
    }

    /**
     * Take a fresh screenshot and return the bitmap. Cheap helper so state-detection
     * code in subclasses can share a single bitmap across multiple template checks in
     * the same iteration, matching the perf pattern established in [Campaign.checkMainScreen].
     */
    protected fun captureSourceBitmap(): Bitmap = game.imageUtils.getSourceBitmap()
}
