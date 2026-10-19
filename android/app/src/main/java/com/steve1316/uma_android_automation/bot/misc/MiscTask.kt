package com.steve1316.uma_android_automation.bot.misc

import android.graphics.Bitmap
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.CareerLaunchNavigator
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.bot.FrozenFrameTracker
import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.bot.GameReopen
import com.steve1316.uma_android_automation.bot.Task
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.bot.miscFrozenGameReopens
import com.steve1316.uma_android_automation.bot.reopenOutcomeWords

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

    /** A frozen game shows a known screen that never changes, so these modes stuck-bail on it instead of reaching a restart. */
    private val frames = FrozenFrameTracker()

    private var frozenGameReopens: Int = 0

    private var actedIterations: Long = 0

    /** After a mode finished on Home, collects Missions and Presents and makes the Club item request when those are turned on. */
    override fun start(maxRuntimeMinutes: Int): TaskResult {
        val result = super.start(maxRuntimeMinutes)
        if (result !is TaskResult.Success) return result
        try {
            GameExtrasDevice.runFromSettings(game)?.let { MessageLog.i(TAG, "[DAILY_EXTRAS] ${it.joinToString("; ")}.") }
        } catch (e: InterruptedException) {
            MessageLog.w(TAG, "[DAILY_EXTRAS] Stopped: ${e.message}")
        } catch (e: Exception) {
            // The mode's own result stands; the extras are a follow-up.
            MessageLog.w(TAG, "[DAILY_EXTRAS] Failed: ${e.message}")
        }
        return result
    }

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
            if (recoverFrozenGame()) return null
            val msg =
                "Safety bailout: $MAX_CONSECUTIVE_UNKNOWNS consecutive UNKNOWN screen detections. " +
                    "Game UI may have changed or the bot is on an unexpected screen."
            MessageLog.w(TAG, "[WARN] $msg")
            return TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, msg)
        }

        if (iterationsWithoutProgress > MAX_STUCK_ITERATIONS) {
            if (recoverFrozenGame()) return null
            val msg =
                "Safety bailout: stuck in state $lastKnownStateName for $MAX_STUCK_ITERATIONS " +
                    "consecutive iterations without progress."
            MessageLog.w(TAG, "[WARN] $msg")
            return TaskResult.Error(TaskResultCode.TASK_RESULT_TIMED_OUT, msg)
        }

        return null
    }

    /**
     * One restart per run for a game that stopped drawing, then Home through the between-run navigator, which knows the
     * title screen and the login popups; these modes then start over from Home. False leaves the bailout as it was.
     */
    private fun recoverFrozenGame(): Boolean {
        // The rails run before this iteration's capture; one fresh frame shows whether the last tap moved the screen.
        frames.record(game.frozenFrameGridOf(game.imageUtils.getSourceBitmap()), actedIterations, System.currentTimeMillis())
        val frozen = frames.frozen(System.currentTimeMillis())
        if (!miscFrozenGameReopens(frozen, { game.otherAppInFront() }, { game.ownInputReachesScreen() }, frozenGameReopens)) return false
        frozenGameReopens++
        val reopen = game.reopenGame(attempt = 2)
        MessageLog.w(TAG, "[RECOVERY] The game stopped responding on a screen the bot knows (its own taps still reach the screen): ${reopenOutcomeWords(reopen)}.")
        if (reopen == GameReopen.NOT_DISPATCHED) return false
        val home = CareerLaunchNavigator(game.myContext).navigate(reuseLastLaunchSetup = true, finalizeToHome = true)
        if (!home.success) {
            MessageLog.w(TAG, "[RECOVERY] The restarted game did not reach Home (${home.failureReason}); stopping this run.")
            return false
        }
        MessageLog.i(TAG, "[RECOVERY] Back on Home after the restart; starting this mode over from Home.")
        consecutiveUnknowns = 0
        iterationsWithoutProgress = 0
        lastKnownStateName = ""
        frames.reset()
        return true
    }

    /** One bitmap shared across the template checks of an iteration, as Campaign.checkMainScreen does. */
    protected fun captureSourceBitmap(): Bitmap =
        game.imageUtils.getSourceBitmap().also {
            // A recognised state's handler taps; an UNKNOWN iteration only waits. A handler whose button is missing taps
            // nothing, so this can overcount; the probe still has to show the bot's taps arriving.
            if (lastKnownStateName.isNotEmpty() && consecutiveUnknowns == 0) actedIterations++
            frames.record(game.frozenFrameGridOf(it), actedIterations, System.currentTimeMillis())
        }
}
