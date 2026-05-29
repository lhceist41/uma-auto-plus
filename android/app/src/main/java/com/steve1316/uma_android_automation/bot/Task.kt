package com.steve1316.uma_android_automation.bot

import com.steve1316.automation_library.utils.DiscordUtils
import com.steve1316.automation_library.utils.MessageLog
import com.steve1316.uma_android_automation.MainActivity
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
     */
    data class Error(override val code: TaskResultCode = TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, override val message: String = "Task completed with errors.") : TaskResult
}

/**
 * Base class for all automation tasks.
 *
 * @property game The [Game] instance used for bot interaction.
 */
abstract class Task(game: Game) : DialogHandler(game) {
    companion object {
        val TAG: String = "[${MainActivity.loggerTag}]${this::class.simpleName}"
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

        if (DiscordUtils.enableDiscordNotifications) {
            DiscordUtils.queue.add("```diff\n$diffChar ${MessageLog.getSystemTimeString()} $discordMessage.\n```")
            // Wait to ensure the Discord message queue is processed.
            game.wait(1.0, skipWaitingForLoading = true)
        }
    }

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
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                val tmpResult: TaskResult? = process()
                // Stop the task if a non-null result is received.
                if (tmpResult != null) {
                    result = tmpResult
                    break
                }
            } catch (e: InterruptedException) {
                result =
                    TaskResult.Success(
                        TaskResultCode.TASK_RESULT_MANUALLY_STOPPED,
                        "Bot was manually stopped by the user.",
                    )
                break
            } catch (e: IllegalStateException) {
                // Most often this is `tryHandleAllDialogs` reporting an unrecognized dialog. The
                // upstream design treats that as fatal, but in practice MuMu can spawn transient
                // dialogs (shop/server/scenario popups) that the next iteration can handle once
                // they animate away. Killing a 60-turn career over one mis-OCR'd dialog is the
                // wrong tradeoff. (An `Unhandled dialog: shop` once bubbled to Game.start and
                // ended a career 24 turns early.) Save a screenshot so we know which dialog the
                // bot didn't recognize next time this fires.
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
                // Brief breather so we don't tightloop on the same screen if the dialog persists.
                try {
                    game.wait(1.0, skipWaitingForLoading = true)
                } catch (_: InterruptedException) {
                    result =
                        TaskResult.Success(
                            TaskResultCode.TASK_RESULT_MANUALLY_STOPPED,
                            "Bot was manually stopped by the user.",
                        )
                    break
                }
            }
        }

        handleTaskEnd(result)
        return result
    }
}
