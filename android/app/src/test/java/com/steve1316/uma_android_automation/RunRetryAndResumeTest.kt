package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.StartModule.Companion.PostCareerAction
import com.steve1316.uma_android_automation.StartModule.Companion.ResumePlan
import com.steve1316.uma_android_automation.bot.TaskResultCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * An errored run is played once more as the same run, a resume re-enters the career it left, and the
 * count of finished careers stays true across both. StartModule's run loop is impractical to run in a
 * JVM test, so the decisions are pure and the loop's wiring is pinned by source guards.
 */
@DisplayName("Run retry and resume")
class RunRetryAndResumeTest {
    private fun retry(
        code: TaskResultCode,
        enableRunQueue: Boolean = true,
        miscMode: Boolean = false,
        diagnostic: Boolean = false,
        queueStopRequested: Boolean = false,
        skipRequested: Boolean = false,
        botRunning: Boolean = true,
        gameRecoveryFailed: Boolean = false,
        accessibilityHalt: Boolean = false,
        retriesLeft: Int = StartModule.RUN_RETRY_BUDGET,
    ) = StartModule.decideRunRetry(code, enableRunQueue, miscMode, diagnostic, queueStopRequested, skipRequested, botRunning, gameRecoveryFailed, accessibilityHalt, retriesLeft)

    @Nested
    @DisplayName("which runs are retried")
    inner class Retry {
        @Test
        fun `only an error inside the career is retried`() {
            val retried = TaskResultCode.entries.filter { retry(it) }.toSet()
            assertEquals(setOf(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, TaskResultCode.TASK_RESULT_CONNECTION_ERROR, TaskResultCode.TASK_RESULT_TIMED_OUT), retried)
        }

        @Test
        fun `a finished career, a breakpoint, a stop, a skip or a failed launch is never retried`() {
            for (code in listOf(
                TaskResultCode.TASK_RESULT_COMPLETE,
                TaskResultCode.TASK_RESULT_BREAKPOINT_REACHED,
                TaskResultCode.TASK_RESULT_MANUALLY_STOPPED,
                TaskResultCode.TASK_RESULT_SKIPPED_BY_QUEUE,
                TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED,
            )) {
                assertFalse(retry(code), code.name)
            }
        }

        @Test
        fun `each condition alone rules a retry out`() {
            val error = TaskResultCode.TASK_RESULT_TIMED_OUT
            assertTrue(retry(error))
            assertFalse(retry(error, enableRunQueue = false), "a single run")
            assertFalse(retry(error, miscMode = true), "Daily Races and Team Trials have no career")
            assertFalse(retry(error, diagnostic = true), "a diagnostic is single-shot")
            assertFalse(retry(error, queueStopRequested = true), "a stop")
            assertFalse(retry(error, skipRequested = true), "a skip")
            assertFalse(retry(error, botRunning = false), "the service is gone")
            assertFalse(retry(error, gameRecoveryFailed = true), "the game could not be recovered")
            assertFalse(retry(error, accessibilityHalt = true), "an accessibility repair could not help")
            assertFalse(retry(error, retriesLeft = 0), "the budget is spent")
        }

        @Test
        fun `the budget allows two retries per Start, then errors move on`() {
            assertEquals(2, StartModule.RUN_RETRY_BUDGET)
            var left = StartModule.RUN_RETRY_BUDGET
            val decisions =
                (1..3).map {
                    retry(TaskResultCode.TASK_RESULT_CONNECTION_ERROR, retriesLeft = left).also { retried -> if (retried) left-- }
                }
            assertEquals(listOf(true, true, false), decisions)
        }

        @Test
        fun `after a run the queue still launches the next one or finishes as before`() {
            for (code in TaskResultCode.entries) {
                assertEquals(PostCareerAction.LAUNCH_NEXT, StartModule.decidePostCareerAction(code, 2, 5, true, false, true), code.name)
            }
            assertEquals(PostCareerAction.FINALIZE_TO_HOME, StartModule.decidePostCareerAction(TaskResultCode.TASK_RESULT_COMPLETE, 5, 5, true, false, true))
            assertEquals(PostCareerAction.STOP, StartModule.decidePostCareerAction(TaskResultCode.TASK_RESULT_TIMED_OUT, 5, 5, true, false, true))
        }
    }

    @Nested
    @DisplayName("the run record")
    inner class Record {
        @Test
        fun `a retried run says so as a plain fact, and an ordinary run says it was not`() {
            val retried = runRecordJson(RunRecord(2, 10, 20, "TASK_RESULT_COMPLETE", null, null, null, null, retried = true))
            val plain = runRecordJson(RunRecord(3, 30, 40, "TASK_RESULT_COMPLETE", null, null, null, null))
            assertEquals(true, retried.get("retried"))
            assertEquals(false, plain.get("retried"))
        }
    }

    @Nested
    @DisplayName("where a resume starts")
    inner class Resume {
        @Test
        fun `a career in flight is re-entered, rotation or not`() {
            assertEquals(ResumePlan(4, 3), StartModule.resumePlanFor(StartModule.PHASE_CAREER, 4, null))
            assertEquals(ResumePlan(1, 0), StartModule.resumePlanFor(StartModule.PHASE_CAREER, 1, null))
        }

        @Test
        fun `a halt on the last run resumes that run instead of finding nothing to do`() {
            val plan = StartModule.resumePlanFor(StartModule.PHASE_CAREER, 6, 5)
            assertEquals(6, plan.startFromRun)
            assertTrue(plan.startFromRun <= 6, "run 6 is still played")
        }

        @Test
        fun `after a launch boundary the next run starts`() {
            assertEquals(ResumePlan(5, 4), StartModule.resumePlanFor(StartModule.PHASE_LAUNCHING, 4, null))
            assertEquals(7, StartModule.resumePlanFor(StartModule.PHASE_LAUNCHING, 6, 6).startFromRun, "past the end: nothing to resume")
        }

        @Test
        fun `a saved count is trusted up to what the saved position proves`() {
            assertEquals(1, StartModule.resumePlanFor(StartModule.PHASE_CAREER, 4, 1).priorCompletedRuns, "errored runs before it are not done")
            assertEquals(3, StartModule.resumePlanFor(StartModule.PHASE_CAREER, 4, 9).priorCompletedRuns, "never more than the runs before it")
            assertEquals(0, StartModule.resumePlanFor(StartModule.PHASE_CAREER, 4, -2).priorCompletedRuns)
            assertEquals(4, StartModule.resumePlanFor(StartModule.PHASE_LAUNCHING, 4, 4).priorCompletedRuns)
            assertEquals(3, StartModule.resumePlanFor(StartModule.PHASE_CAREER, 4, null).priorCompletedRuns, "an older saved state without a count")
        }
    }

    @Nested
    @DisplayName("the run loop's wiring")
    inner class Wiring {
        private val startModule by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt") }
        private val loop by lazy {
            val start = startModule.indexOf("for (i in startFromRun..totalRuns) {")
            assertTrue(start >= 0)
            startModule.substring(start, startModule.indexOf("ledger.completedRuns = completedRuns\n                ledger.haltRun = queueHaltRun", start))
        }

        @Test
        fun `launching is saved only after a finished career`() {
            // Only where no career of the saved run is in the slot.
            assertEquals(3, Regex("phase = PHASE_LAUNCHING").findAll(startModule).count(), "the launch boundary, a skipped run and a run that could not start")
            val leave = startModule.substringAfter("private fun leaveSkippedRun(").substringBefore("\n    }\n")
            assertTrue(leave.contains("saveQueueState(context, active = true, currentRun = run, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)"))
            val notStarted = loop.substringAfter("if (unplayable == UnplayableRunStep.HALT && launchStop != null) {").substringBefore("// Evaluate the result.")
            assertTrue(notStarted.contains("saveQueueState(context, active = true, currentRun = i - 1, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)"))
            val gate = loop.indexOf("val careerFinished = effectiveResult.code == TaskResultCode.TASK_RESULT_COMPLETE\n                        if (careerFinished) {")
            val save = loop.indexOf("saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)")
            assertTrue(gate in 0 until save, "gate $gate, save $save")
            assertTrue(loop.substring(gate, save).count { it == '\n' } == 2, "the save is the first thing inside the gate")
        }

        @Test
        fun `no navigation claims an unfinished career is finished`() {
            assertFalse(startModule.contains("previousCareerComplete = true"))
            assertTrue(loop.contains("navigateWithDeadline(nextReuse, previousCareerComplete = careerFinished, careerInFlight = !careerFinished)"))
        }

        @Test
        fun `the retry plays the same run once, before the run is recorded`() {
            val decide = loop.indexOf("decideRunRetry(")
            val block = loop.indexOf("if (retried) {")
            val again = loop.indexOf("result = runSingleGame()", block)
            val effective = loop.indexOf("val effectiveResult =")
            val record = loop.indexOf("recordRun(ledger, i, runStartedAt, careerEndSeqBeforeRun, effectiveResult.code, retried)")
            assertTrue(decide in 0 until block && block < again && again < effective && effective < record, "decide $decide, block $block, again $again, effective $effective, record $record")
            assertEquals(2, Regex("runSingleGame\\(\\)").findAll(loop).count(), "one first attempt and one retry, no loop")
            val retryBlock = loop.substring(block, again)
            for (forbidden in listOf("saveQueueState(", "applyRotationForRun(", "navigateWithDeadline(", "completedRuns")) assertFalse(retryBlock.contains(forbidden), forbidden)
            assertTrue(retryBlock.contains("runRetriesLeft--"))
            assertTrue(retryBlock.contains("CareerFinalizeGate.beginCareer("), "the re-entered career gets its own finalize identity")
        }

        @Test
        fun `the retry decision reads the live flags`() {
            val call = loop.substring(loop.indexOf("decideRunRetry("), loop.indexOf("if (retried) {"))
            for (arg in listOf(
                "resultCode = result.code",
                "enableRunQueue = enableRunQueue",
                "diagnostic = debugDiagnosticArmed",
                "queueStopRequested = queueStopRequested",
                "skipRequested = queueSkipRequested",
                "botRunning = BotService.isRunning",
                "gameRecoveryFailed = gameRecoveryFailed",
                "accessibilityHalt = accessibilityHaltKey != null",
                "retriesLeft = runRetriesLeft",
            )) {
                assertTrue(call.contains(arg), arg)
            }
            assertTrue(startModule.contains("var runRetriesLeft = RUN_RETRY_BUDGET\n                for (i in startFromRun..totalRuns) {"))
        }

        @Test
        fun `only a finished career is counted`() {
            assertEquals(1, Regex("completedRuns\\+\\+").findAll(loop).count())
            assertTrue(loop.contains("TaskResultCode.TASK_RESULT_COMPLETE -> {\n                            completedRuns++"))
        }

        @Test
        fun `every saved position carries the count, and a resume reads it back`() {
            assertTrue(loop.contains("saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_CAREER, completedRuns = completedRuns)"))
            assertTrue(startModule.contains("QueueState(active, currentRun, totalRuns, ageMs, phase, raw[\"completedRuns\"]?.toIntOrNull())"))
            assertTrue(startModule.contains("val plan = resumePlanFor(saved.phase, saved.currentRun, saved.completedRuns)"))
            assertTrue(startModule.contains("val next = plan.startFromRun"))
            assertFalse(startModule.contains("rotation.enabled && saved.phase"), "the resume no longer depends on rotation")
        }

        @Test
        fun `a halt after an unfinished career says the career is still in the slot`() {
            assertEquals(2, Regex("queueHaltCareerInFlight = \\(?!careerFinished").findAll(loop).count(), "the missing-snapshot and navigation halts")
        }

        @Test
        fun `a saved queue can be resumed for a day`() {
            assertTrue(startModule.contains("private const val QUEUE_STATE_STALE_MS: Long = 24 * 60 * 60 * 1000L"))
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
