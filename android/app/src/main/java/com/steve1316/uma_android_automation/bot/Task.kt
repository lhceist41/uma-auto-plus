package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.BotService
import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
import com.steve1316.uma_android_automation.StartModule
import com.steve1316.uma_android_automation.bot.DialogHandler
import com.steve1316.uma_android_automation.bot.DialogHandlerResult
import com.steve1316.uma_android_automation.bot.Game
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The possible result codes for a task's execution. */
enum class TaskResultCode {
    /** The task completed all its objectives successfully. */
    TASK_RESULT_COMPLETE,

    /** The task reached a predefined breakpoint and stopped. */
    TASK_RESULT_BREAKPOINT_REACHED,

    /** The task was manually stopped by the user. */
    TASK_RESULT_MANUALLY_STOPPED,

    /** An unhandled exception occurred during task execution. */
    TASK_RESULT_UNHANDLED_EXCEPTION,

    /** A connection error occurred during task execution. */
    TASK_RESULT_CONNECTION_ERROR,

    /** The task timed out before completing its objectives. */
    TASK_RESULT_TIMED_OUT,

    /** The current run was skipped by the queue system. */
    TASK_RESULT_SKIPPED_BY_QUEUE,

    /** Queue navigation between runs failed. */
    TASK_RESULT_QUEUE_NAVIGATION_FAILED,
}

/**
 * The game could not reach its server within the outage budget, or kept loading with no dialog for too long. An
 * [InterruptedException] so existing stop paths unwind it; reported as [TaskResultCode.TASK_RESULT_CONNECTION_ERROR].
 */
class ConnectionLostException(message: String) : InterruptedException(message)

/** Represents the final result of a task's execution. */
sealed interface TaskResult {
    val code: TaskResultCode
    val message: String

    /**
     * Indicates a successful task completion.
     *
     * @property code The [TaskResultCode] associated with the result.
     * @property message A descriptive message about the result.
     */
    data class Success(override val code: TaskResultCode = TaskResultCode.TASK_RESULT_COMPLETE, override val message: String = "Task completed successfully.") : TaskResult

    /**
     * Indicates a task completion with errors.
     *
     * @property code The [TaskResultCode] associated with the result.
     * @property message A descriptive message about the error.
     * @property reasonKey A single run's launch-navigation reason key for the report, or "".
     */
    data class Error(
        override val code: TaskResultCode = TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
        override val message: String = "Task completed with errors.",
        val reasonKey: String = "",
        val reasonTrainee: String = "",
        val reasonOutfit: String = "",
    ) : TaskResult
}

/**
 * Base class for all automation tasks.
 *
 * @property game The [Game] instance used for bot interaction.
 */
abstract class Task(game: Game) : DialogHandler(game) {
    companion object {
        val TAG: String = "[${MainActivity.loggerTag}]${this::class.simpleName}"

        /**
         * Result code for an interrupted run: a user Stop or queue skip wins, otherwise a lost connection is reported
         * as such.
         */
        internal fun interruptResultCode(skipRequested: Boolean, stopRequested: Boolean, serviceRunning: Boolean, connectionLost: Boolean): TaskResultCode =
            when {
                skipRequested -> TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE
                stopRequested || !serviceRunning -> TaskResultCode.TASK_RESULT_MANUALLY_STOPPED
                connectionLost -> TaskResultCode.TASK_RESULT_CONNECTION_ERROR
                else -> TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION
            }

        /** A stop the bot requested itself (it always sets [StartModule.queueStopReason]) reads STOPPED_BY_BOT, never the user's MANUALLY_STOPPED. */
        internal fun careerLedgerResult(code: TaskResultCode, botStopReason: String?): String =
            if (code == TaskResultCode.TASK_RESULT_MANUALLY_STOPPED && botStopReason != null) "STOPPED_BY_BOT" else code.name.removePrefix("TASK_RESULT_")
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // Debug Tests

    /**
     * Run all tests for this task.
     *
     * @return Whether any tests were executed.
     */
    open fun startTests(): Boolean {
        return false
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Process a single iteration of the task's main loop.
     *
     * @return A [TaskResult] if the main loop should stop, or null to continue iterating.
     */
    abstract fun process(): TaskResult?

    /**
     * Attempt to handle all active dialog boxes.
     *
     * This method continuously handles dialogs until no more are detected or the timeout is reached.
     *
     * @param timeoutMs The maximum time (in milliseconds) allowed for this operation.
     * @return True if at least one dialog was successfully handled, false otherwise.
     * @throws IllegalStateException If an unhandled dialog is detected.
     */
    fun tryHandleAllDialogs(timeoutMs: Int = 15000): Boolean {
        var bWasDialogHandled = false
        var dialogResult: DialogHandlerResult = DialogHandlerResult.NoDialogDetected
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            dialogResult = handleDialogs()

            if (dialogResult !is DialogHandlerResult.Handled) {
                break
            }
            bWasDialogHandled = true
        }

        if (dialogResult is DialogHandlerResult.Unhandled) {
            throw IllegalStateException("Unhandled dialog: ${dialogResult.dialog.name}")
        }

        return bWasDialogHandled
    }

    /**
     * Handle cleanup actions when the task's main loop finishes.
     *
     * This method logs the result and sends a Discord notification if enabled.
     *
     * @param result The [TaskResult] that caused the task to end.
     */
    private fun handleTaskEnd(result: TaskResult) {
        val logMessage = "${result.javaClass.simpleName} (${result.code}): ${result.message}"
        game.notificationMessage = logMessage
        val discordMessage = "${this::class.simpleName}:: ${result.javaClass.simpleName} (${result.code}): ${result.message}"
        var diffChar: String
        when (result) {
            is TaskResult.Success -> {
                MessageLog.i(TAG, logMessage)
                diffChar = "+"
            }

            is TaskResult.Error -> {
                MessageLog.e(TAG, logMessage)
                diffChar = "-"
            }
        }

        // One greppable outcome line per career run; campaigns emit it, non-career tasks return null.
        careerEndLedgerLine(result)?.let { MessageLog.i(TAG, it) }

        // AFTER the ledger line is in the buffer, so the per-career file contains its own line.
        writePerCareerLog(result)

        if (DiscordUtils.enableDiscordNotifications) {
            DiscordUtils.queue.add("```diff\n$diffChar ${MessageLog.getSystemTimeString()} $discordMessage.\n```")
            // Wait to ensure the Discord message queue is processed.
            game.wait(1.0, skipWaitingForLoading = true)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    protected open fun careerEndLedgerLine(result: TaskResult): String? = null

    /**
     * Optional per-career log-file write, called AFTER the ledger line is logged so the file contains its own
     * `[CAREER_END]` line.
     */
    protected open fun writePerCareerLog(result: TaskResult) {}

    // //////////////////////////////////////////////////////////////////////////////////////////////////
    // //////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Run the task's main loop until completion, manual stop, or per-run timeout.
     *
     * The timeout exists as a safety net for queue runs - we never want a single career to
     * stall forever and prevent later runs in the queue from starting. Default is 180 minutes
     * (3 hours), configurable per run via the `runQueue.maxRuntimePerRunMinutes` setting.
     *
     * @param maxRuntimeMinutes Per-run safety timeout in minutes. Default 180.
     * @return The final [TaskResult] of the task's execution.
     */
    open fun start(maxRuntimeMinutes: Int = 180): TaskResult {
        var result: TaskResult =
            TaskResult.Error(
                TaskResultCode.TASK_RESULT_TIMED_OUT,
                "The task timed out after $maxRuntimeMinutes minutes.",
            )

        val timeoutMs = (maxRuntimeMinutes * (60 * 1000)).toLong()
        val startTime = System.currentTimeMillis()
        // Bounds the unhandled-dialog recover net: a dialog it can never clear would retry until the 3-min watchdog
        // kills the whole process, taking the queue with it.
        var consecutiveUnhandledDialogs = 0
        var lastUnhandledDialogMessage: String? = null
        val maxIdenticalUnhandledDialogs = 5
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                game.connectionBudget.beginIteration()
                val tmpResult: TaskResult? = process()
                consecutiveUnhandledDialogs = 0
                game.connectionBudget.endIterationNormally()
                // Stop the task if a non-null result is received.
                if (tmpResult != null) {
                    result = tmpResult
                    break
                }
            } catch (e: InterruptedException) {
                result = interruptResult(e)
                break
            } catch (e: IllegalStateException) {
                // An unrecognized dialog is not fatal here: MuMu spawns transient dialogs (shop/server/scenario popups)
                // that the next iteration handles once they animate away, and ending a 60-turn career over one
                // mis-OCR'd dialog is the wrong tradeoff. Save a screenshot to identify it. A bounded run of the SAME
                // unhandled dialog ends the run cleanly instead of spinning to the watchdog; the queue's stopOnError
                // then decides continue-vs-abort.
                if (e.message == lastUnhandledDialogMessage) {
                    consecutiveUnhandledDialogs++
                } else {
                    consecutiveUnhandledDialogs = 1
                    lastUnhandledDialogMessage = e.message
                }
                if (consecutiveUnhandledDialogs >= maxIdenticalUnhandledDialogs) {
                    result =
                        TaskResult.Error(
                            TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
                            "Persistent unhandled dialog after $consecutiveUnhandledDialogs recoveries: ${e.message}",
                        )
                    break
                }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "unhandled_dialog_$timestamp"
                try {
                    game.imageUtils.saveBitmap(filename = filename, fullRes = true)
                    MessageLog.w(
                        TAG,
                        "[WARN] start:: Recovered from unhandled dialog: ${e.message}. Screenshot saved: $filename.png. Continuing main loop.",
                    )
                } catch (saveErr: Exception) {
                    MessageLog.w(
                        TAG,
                        "[WARN] start:: Recovered from unhandled dialog: ${e.message}. (Failed to save screenshot: ${saveErr.message}.) Continuing main loop.",
                    )
                }
                try {
                    game.wait(1.0, skipWaitingForLoading = true)
                } catch (ie: InterruptedException) {
                    result = interruptResult(ie)
                    break
                }
            }
        }

        // The result is settled; teardown waits (log save, Discord flush) must not re-raise it.
        game.connectionLostReason = null
        handleTaskEnd(result)
        return result
    }

    /**
     * Builds the [TaskResult] for an [InterruptedException] from the main loop, attributed to its actual source: a real
     * Stop sets [StartModule.queueStopRequested] or tears the service down ([BotService.isRunning] == false). Any other
     * interrupt is an internal give-up or watchdog and is reported as an error so the queue's stopOnError decides;
     * reporting it as MANUALLY_STOPPED made the queue discard every remaining run.
     */
    private fun interruptResult(e: InterruptedException): TaskResult {
        // Taken first, before the settle below: the stall watchdog's interrupt carries no message of its own.
        val watchdogReason = WatchdogReason.take()
        // Clear the interrupt flag so task teardown (log save, events) is not poisoned by it.
        Thread.interrupted()
        // A user Stop interrupts this thread and flips its flags from another thread, and the interrupt routinely lands
        // first, which once attributed a real Stop to a watchdog. Settle briefly before attributing; watchdog
        // interrupts never set these flags, so they pay the wait once at task end.
        val settleDeadline = System.currentTimeMillis() + 1500
        while (System.currentTimeMillis() < settleDeadline &&
            !StartModule.queueStopRequested &&
            !StartModule.queueSkipRequested &&
            BotService.isRunning
        ) {
            try {
                Thread.sleep(100)
            } catch (_: InterruptedException) {
            }
        }
        val connectionLost = e is ConnectionLostException || game.connectionLostReason != null
        return when (interruptResultCode(StartModule.queueSkipRequested, StartModule.queueStopRequested, BotService.isRunning, connectionLost)) {
            TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE ->
                TaskResult.Success(
                    TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE,
                    "Run was skipped from the queue controls.",
                )
            TaskResultCode.TASK_RESULT_MANUALLY_STOPPED ->
                // queueStopReason is set by a deliberate internal stop (e.g. the trainee-mismatch guard); null means a
                // genuine user Stop.
                TaskResult.Success(
                    TaskResultCode.TASK_RESULT_MANUALLY_STOPPED,
                    StartModule.queueStopReason ?: "Bot was manually stopped by the user.",
                )
            TaskResultCode.TASK_RESULT_CONNECTION_ERROR ->
                TaskResult.Error(
                    TaskResultCode.TASK_RESULT_CONNECTION_ERROR,
                    game.connectionLostReason ?: e.message ?: "The game lost its connection to the server.",
                )
            else ->
                TaskResult.Error(
                    TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
                    watchdogReason ?: e.message ?: "Bot was interrupted by an internal watchdog or safety-net, not by the user.",
                )
        }
    }
}
