package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.Task
import com.steve1316.uma_android_automation.bot.TaskResultCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/** A mid-career Data Update stop cleared the record, and the next Start began run 1 of a fresh queue on the career still in the slot. */
@DisplayName("Bot stops keep the resume record and are logged as bot stops")
class BotStopResumeRecordTest {
    private val dataUpdateReason = "The game asked to download additional data, and the Data Update prompt's OK button was not found."

    @Nested
    @DisplayName("keepsResumeRecordAfterStop")
    inner class ResumeRecord {
        private val complete = TaskResultCode.TASK_RESULT_COMPLETE

        private fun lastCareerFinished(totalRuns: Int, vararg results: TaskResultCode): Boolean =
            results.withIndex().any { (i, code) -> StartModule.finishesLastCareer(i + 1, totalRuns, code) }

        @Test
        fun `a bot stop keeps the record`() {
            assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = dataUpdateReason, lastCareerFinished = false, stopLeftCareer = false))
        }

        @Test
        fun `a player's stop between careers clears it`() {
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = false, stopLeftCareer = false))
        }

        @Test
        fun `a player's stop in the middle of a career keeps it`() {
            assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = false, stopLeftCareer = true))
        }

        @Test
        fun `a player's stop keeps it only when a stop was requested`() {
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = false, botStopReason = null, lastCareerFinished = false, stopLeftCareer = true))
        }

        @Test
        fun `nothing is kept once the last career finished`() {
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = true, stopLeftCareer = true))
        }

        @Test
        fun `a bot stop keeps it whether or not its run left a career`() {
            for (left in listOf(true, false)) {
                assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = dataUpdateReason, lastCareerFinished = false, stopLeftCareer = left))
            }
        }

        @Test
        fun `a queue that ran out without a stop clears it`() {
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = false, botStopReason = null, lastCareerFinished = false, stopLeftCareer = false))
        }

        @Test
        fun `a bot stop after the last run finished clears it`() {
            val finished = lastCareerFinished(5, complete, complete, complete, complete, complete)
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = dataUpdateReason, lastCareerFinished = finished, stopLeftCareer = false))
        }

        @Test
        fun `a bot stop before the last run finished keeps it`() {
            val finished = lastCareerFinished(5, complete, complete, complete, complete)
            assertFalse(finished, "run 4 of 5 is not the last run")
            assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = dataUpdateReason, lastCareerFinished = finished, stopLeftCareer = false))
        }

        @Test
        fun `a bot stop after the last run finished clears it even when an earlier run errored`() {
            val finished = lastCareerFinished(5, complete, complete, TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, complete, complete)
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = dataUpdateReason, lastCareerFinished = finished, stopLeftCareer = false))
        }

        @Test
        fun `a last run whose career is still in the slot keeps it`() {
            for (unfinished in listOf(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE)) {
                val finished = lastCareerFinished(5, complete, complete, complete, complete, unfinished)
                assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = dataUpdateReason, lastCareerFinished = finished, stopLeftCareer = false), unfinished.name)
            }
        }
    }

    @Nested
    @DisplayName("careerLedgerResult")
    inner class LedgerResult {
        @Test
        fun `a bot stop is recorded as STOPPED_BY_BOT`() {
            assertEquals("STOPPED_BY_BOT", Task.careerLedgerResult(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, dataUpdateReason))
        }

        @Test
        fun `a user stop stays MANUALLY_STOPPED`() {
            assertEquals("MANUALLY_STOPPED", Task.careerLedgerResult(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, null))
        }

        @Test
        fun `every other result keeps its name whatever the stop reason`() {
            for (code in TaskResultCode.entries.filter { it != TaskResultCode.TASK_RESULT_MANUALLY_STOPPED }) {
                val expected = code.name.removePrefix("TASK_RESULT_")
                assertEquals(expected, Task.careerLedgerResult(code, null))
                assertEquals(expected, Task.careerLedgerResult(code, dataUpdateReason))
            }
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val campaign by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt") }
        private val startModule by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt") }

        @Test
        fun `both career ledger outputs use the bot-stop result and carry the stop key`() {
            assertEquals(2, Regex("careerLedgerResult\\(result\\.code, StartModule\\.queueStopReason\\)").findAll(campaign).count(), "careers.jsonl and the [CAREER_END] line")
            assertFalse(campaign.contains("result.code.name.removePrefix(\"TASK_RESULT_\")"), "no ledger output bypasses the helper")
            assertEquals(2, Regex("StartModule\\.queueStopKey\\?\\.let").findAll(campaign).count(), "stopKey on both outputs")
        }

        @Test
        fun `only the bot's own stop sites set a stop reason`() {
            val sites = listOf(
                "android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt",
                "android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt",
                "android/app/src/main/java/com/steve1316/uma_android_automation/bot/DialogHandler.kt",
            ).sumOf { path -> Regex("queueStopReason =\\s*\"|queueStopReason = reason|queueStopReason =\\n").findAll(source(path)).count() }
            assertEquals(6, sites, "navigation deadline, trainee mismatch, trainee outside the rotation, scenario mismatch, resumed career's scenario, data prompt")
            for (userStop in listOf("fun stop() {", "fun stopQueue() {", "internal fun stopForLostCapture() {")) {
                val at = startModule.indexOf(userStop)
                assertTrue(at >= 0, userStop)
                val indent = at - (startModule.lastIndexOf('\n', at) + 1)
                val end = startModule.indexOf("\n" + " ".repeat(indent) + "}", at)
                assertTrue(end > at, "$userStop has a closing brace")
                val body = startModule.substring(at, end)
                assertFalse(body.contains("queueStopReason"), "$userStop must not set a stop reason")
            }
        }

        @Test
        fun `only a stop that ends a career run marks its career as left in the slot`() {
            assertEquals(1, Regex("stopLeftCareer = runScenario").findAll(startModule).count(), "set in one place")
            assertFalse(Regex("stopLeftCareer = true").containsMatchIn(startModule))
            val branch = startModule.substring(startModule.indexOf("TaskResultCode.TASK_RESULT_MANUALLY_STOPPED -> {"), startModule.indexOf("TaskResultCode.TASK_RESULT_COMPLETE -> {"))
            assertTrue(
                branch.contains("stopLeftCareer = runScenario != \"Daily Races\" && runScenario != \"Team Trials\"\n                                break"),
                "the stopped run's own branch, misc runs excluded",
            )
            assertTrue(startModule.contains("keepsResumeRecordAfterStop(queueStopRequested, stopReason, lastCareerFinished, stopLeftCareer)"))
        }

        @Test
        fun `the run loop marks the last career finished from that run's own result`() {
            val set = startModule.indexOf("if (finishesLastCareer(i, totalRuns, effectiveResult.code)) lastCareerFinished = true")
            val effective = startModule.indexOf("val effectiveResult =")
            assertTrue(effective in 0 until set, "decided after the run's effective result")
            assertEquals(1, Regex("lastCareerFinished = true").findAll(startModule).count(), "set nowhere else")
            assertFalse(startModule.contains("completedRuns < totalRuns"), "no count-based proxy")
        }
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }
}
