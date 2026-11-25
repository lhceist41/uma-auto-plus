package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A bot stop thrown while `Game` is built (the unknown-scenario stop) escaped the task loop's own handling, so a single run and a
 * queue's cold start read "unexpected error" instead of the bot's reason.
 */
@DisplayName("A bot stop before the game is built reads as the bot's stop")
class BotStopBeforeGameTest {
    private val reason = "The selected scenario is not one this version of UMA Auto+ plays. Select another preset on Home, then press Start again."
    private val key = "SCENARIO_UNSUPPORTED"

    private fun startModule(): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val f = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt")
            if (f.isFile) return f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("StartModule.kt not found from the test working directory")
    }

    private fun reportText(ledger: SessionLedger, stopByBot: Boolean, queueStateActive: Boolean = false): ReportText {
        val verdict = classifySessionEnd(ledger.facts(stopRequested = stopByBot, stopByBot = stopByBot, serviceRunning = true, queueStateActive = queueStateActive))
        if (stopByBot && ledger.reasonKey.isEmpty()) ledger.reasonKey = key
        return queueReportText(ledger.report(verdict, 2L).toJson())
    }

    private fun singleRun(code: TaskResultCode) =
        SessionLedger("single", 0L, "9.9.9", 1).apply {
            totalRuns = 1
            addRun(RunRecord(1, 0L, 1L, code.name, null, null, null, null))
        }

    private fun coldStart() =
        SessionLedger("cold", 0L, "9.9.9", 1).apply {
            queueEnabled = true
            totalRuns = 4
        }

    @Test
    fun `a bot stop ends the run as the bot's stop with its reason`() {
        val result = StartModule.resultForRunException(InterruptedException(reason), botStopReason = reason)
        assertTrue(result is TaskResult.Success)
        assertEquals(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, result.code)
        assertEquals(reason, result.message)
    }

    @Test
    fun `an interrupt with no bot reason and a real exception keep reading as an error`() {
        val noReason = StartModule.resultForRunException(InterruptedException("stop"), botStopReason = null)
        assertTrue(noReason is TaskResult.Error)
        assertEquals(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, noReason.code)
        val crash = StartModule.resultForRunException(IllegalStateException("boom"), botStopReason = reason)
        assertTrue(crash is TaskResult.Error, "a real exception is an error even after a bot stop reason was set")
        assertEquals(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, crash.code)
        assertEquals("Unhandled exception: boom", crash.message)
    }

    @Test
    fun `a single run stopped by the bot shows the bot's words, an unexpected exception still reads as an error`() {
        val stopped = reportText(singleRun(StartModule.resultForRunException(InterruptedException(reason), reason).code), stopByBot = true)
        assertEquals("Run stopped", stopped.title)
        assertEquals("The bot stopped the run: ${REPORT_REASON_KEYS.getValue(key).reason}", stopped.reason)

        val crashed = reportText(singleRun(StartModule.resultForRunException(IllegalStateException("boom"), null).code), stopByBot = false)
        assertEquals("Run ended with an error", crashed.title)
    }

    @Test
    fun `a bot stop at a queue's cold start reads as the bot's stop, an escaped exception as an error`() {
        val stopped = reportText(coldStart(), stopByBot = true)
        assertTrue(stopped.reason.startsWith("The bot stopped the queue with 0 of 4 runs done: "), stopped.reason)
        assertTrue(stopped.reason.contains(REPORT_REASON_KEYS.getValue(key).reason), stopped.reason)

        val crashed = reportText(coldStart().apply { unexpectedError = true }, stopByBot = false)
        assertEquals("Stopped by an error", crashed.title)
    }

    @Test
    fun `the single run's catch builds its result from the bot stop reason`() {
        val src = startModule()
        val run = src.substring(src.indexOf("private fun runSingleGame("), src.indexOf("botThread.start()"))
        assertTrue(run.contains("taskResult = resultForRunException(e, queueStopReason)"), run)
    }

    @Test
    fun `the cold-start probe catches only a bot stop and lets the loop's first stop check exit`() {
        val src = startModule()
        val probeStart = src.indexOf("coldStartNavigator.isOnHomeScreen()")
        val loop = src.indexOf("for (i in startFromRun..totalRuns) {", probeStart)
        assertTrue(probeStart >= 0 && loop > probeStart)
        val probe = src.substring(probeStart, loop)
        val catchAt = probe.indexOf("} catch (e: InterruptedException) {")
        val finallyAt = probe.indexOf("} finally {\n                        coldStartNavigator?.close()")
        assertTrue(catchAt in 0 until finallyAt, "the catch sits on the probe's own try, before its finally")
        val body = probe.substring(catchAt, finallyAt)
        assertTrue(body.contains("if (queueStopReason == null) throw e"), "a player's stop or an internal interrupt keeps escaping as before")
        assertTrue(body.contains("Thread.interrupted()"), "the interrupt flag is cleared")
        assertTrue(body.contains("queueStopRequested = true"), body)
        val firstCheck = src.substring(loop, src.indexOf("break", loop))
        assertTrue(firstCheck.contains("if (queueStopRequested || !BotService.isRunning) {"), firstCheck)
    }
}
