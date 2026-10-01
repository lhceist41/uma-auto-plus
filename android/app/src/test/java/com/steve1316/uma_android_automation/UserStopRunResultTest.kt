package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/** A Stop pressed during launch navigation came back as a failed navigation and showed as an error on the card. */
@DisplayName("A run the player stops is reported as stopped, not as an error")
class UserStopRunResultTest {
    private val errorCodes =
        listOf(
            TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED,
            TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION,
            TaskResultCode.TASK_RESULT_TIMED_OUT,
            TaskResultCode.TASK_RESULT_CONNECTION_ERROR,
        )

    private fun loopSource(): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            for (rel in listOf("src/main/java", "android/app/src/main/java")) {
                val f = File(dir, "$rel/com/steve1316/uma_android_automation/StartModule.kt")
                if (f.isFile) return f.readText().replace("\r\n", "\n")
            }
            dir = dir?.parentFile
        }
        error("could not locate StartModule.kt")
    }

    @Test
    fun `a player stop turns every error result into MANUALLY_STOPPED`() {
        for (code in errorCodes) {
            val stopped = StartModule.resultForStoppedRun(TaskResult.Error(code, "Auto-navigation to training menu failed: Queue stopped during trainee selection."), botStopReason = null)
            assertTrue(stopped is TaskResult.Success, "$code")
            assertEquals(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, stopped.code, "$code")
        }
    }

    @Test
    fun `a bot stop keeps its own result and reason`() {
        for (code in errorCodes) {
            val error = TaskResult.Error(code, "stopped by the bot")
            assertSame(error, StartModule.resultForStoppedRun(error, botStopReason = "Trainee mismatch."), "$code")
        }
    }

    @Test
    fun `a run that did not error keeps its result when a stop lands`() {
        for (code in listOf(TaskResultCode.TASK_RESULT_COMPLETE, TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE, TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED)) {
            val result = TaskResult.Success(code, "done")
            assertSame(result, StartModule.resultForStoppedRun(result, botStopReason = null), "$code")
        }
    }

    @Test
    fun `an overlay stop is the service ending with no bot reason and no posted exception`() {
        assertTrue(StartModule.isOverlayStop(botRunning = false, botStopReason = null, runPostedException = false))
        assertFalse(StartModule.isOverlayStop(botRunning = true, botStopReason = null, runPostedException = false), "the service is still running")
        assertFalse(StartModule.isOverlayStop(botRunning = false, botStopReason = "Trainee mismatch.", runPostedException = false), "a bot stop")
        assertFalse(StartModule.isOverlayStop(botRunning = false, botStopReason = null, runPostedException = true), "an exception also ends the service")
    }

    @Test
    fun `an overlay stop reads as a player stop, never as the navigation error it came back as`() {
        val navigationError = TaskResult.Error(TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED, "Auto-navigation to training menu failed: Queue stopped during trainee selection.")
        val stopRequested = StartModule.isOverlayStop(botRunning = false, botStopReason = null, runPostedException = false)
        val reported = if (stopRequested) StartModule.resultForStoppedRun(navigationError, botStopReason = null) else navigationError
        assertEquals(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, reported.code)
    }

    @Test
    fun `a run that ended on an ExceptionEvent is never reported as stopped by the player`() {
        val crash = RuntimeException("uncaught in a worker thread")
        assertTrue(StartModule.isCrash(crash))
        assertFalse(StartModule.isCrash(InterruptedException()), "the library reads an InterruptedException as a manual stop")
        val overlayStop = StartModule.isOverlayStop(botRunning = false, botStopReason = null, runPostedException = StartModule.isCrash(crash))
        assertFalse(overlayStop)
        val error = TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, "crashed")
        val reported = if (overlayStop) StartModule.resultForStoppedRun(error, botStopReason = null) else error
        assertSame(error, reported)
        assertEquals(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, reported.code)
    }

    @Test
    fun `the module records every posted ExceptionEvent as a crash`() {
        val source = loopSource()
        val handler = source.substringAfter("fun onExceptionEvent(event: ExceptionEvent) {").substringBefore("\n    }\n")
        assertTrue(source.contains("@Subscribe(priority = 1)\n    fun onExceptionEvent(event: ExceptionEvent) {"), "StartModule no longer subscribes to ExceptionEvent ahead of the library")
        assertTrue(handler.contains("if (isCrash(event.exception)) lastRunPostedException = true"), "a posted exception no longer marks the run as crashed")
    }

    @Test
    fun `an overlay stop that unwinds through a wait is a stop, not a crash`() {
        assertTrue(StartModule.isOverlayStop(botRunning = false, botStopReason = null, runPostedException = StartModule.isCrash(InterruptedException())))
        assertFalse(StartModule.isOverlayStop(botRunning = false, botStopReason = null, runPostedException = StartModule.isCrash(IllegalStateException())))
        val catchBlock = loopSource().substringAfter("EventBus.getDefault().postSticky(ExceptionEvent(e))").substringBefore("taskResult =")
        assertTrue(catchBlock.contains("lastRunPostedException = isCrash(e)"), "the run's catch marks every exception as a crash again")
        assertFalse(catchBlock.contains("lastRunPostedException = true"))
    }

    @Test
    fun `the queue loop gives an overlay stop the stop flag before it reads the run result`() {
        val source = loopSource()
        val flag = source.indexOf("if (isOverlayStop(BotService.isRunning, queueStopReason, lastRunPostedException)) queueStopRequested = true")
        assertTrue(flag >= 0, "the queue loop no longer treats an overlay stop as a stop")
        assertTrue(flag > source.indexOf("var result = runSingleGame()"), "the flag is set before the run has ended")
        assertTrue(flag < source.indexOf("val launchStop = (result as? TaskResult.Error)"), "the flag must be set before the result is classified")
    }

    @Test
    fun `the queue loop reports a requested stop through it`() {
        val branch = loopSource().substringAfter("queueStopRequested -> {").substringBefore("unplayable == UnplayableRunStep.SKIP")
        assertTrue(branch.contains("resultForStoppedRun(result, queueStopReason)"), "a requested stop no longer goes through resultForStoppedRun")
    }
}
