package com.steve1316.uma_android_automation.bot.misc

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.Task
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode

/**
 * Base for non-career ("misc") tasks that automate standalone game modes from the Home Screen, each a
 * state machine shaped like CareerLaunchNavigator. The first [process] call bails if not on Home: these
 * machines are too narrow to recover, and a wrong start is a scheduling bug upstream. The safety-rail
 * limits match the career navigator's.
 */
abstract class MiscTask(game: Game) : Task(game) {
    companion object {
        const val MAX_ITERATIONS_SAFETY: Int = 300

        const val MAX_CONSECUTIVE_UNKNOWNS: Int = 5

        const val MAX_STUCK_ITERATIONS: Int = 15
    }

    protected val TAG: String = "[${MainActivity.loggerTag}]${this::class.simpleName}"

    protected var iterationsCompleted: Int = 0

    protected var consecutiveUnknowns: Int = 0

    protected var iterationsWithoutProgress: Int = 0

    protected var lastKnownStateName: String = ""

    /** Dismisses incidental popups (login bonus, gift, announcement) that can interrupt any misc flow; subclasses may override. */
    protected open fun handleIncidentalPopups(): Boolean {
        return try {
            tryHandleAllDialogs(timeoutMs = 5000)
        } catch (e: IllegalStateException) {
            // Misc tasks log an unhandled dialog and continue; they do not abort like Campaign.
            MessageLog.w(TAG, "[WARN] handleIncidentalPopups:: Unhandled dialog: ${e.message}")
            false
        }
    }

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

    /** One bitmap shared across the template checks of an iteration, as Campaign.checkMainScreen does. */
    protected fun captureSourceBitmap(): Bitmap = game.imageUtils.getSourceBitmap()
}
