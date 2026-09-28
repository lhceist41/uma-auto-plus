package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.StartModule.Companion.PostCareerAction
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.utils.StatusBoard
import com.steve1316.uma_android_automation.utils.updateBlockReason
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * "Stop after this career": the queue pauses at the launch point after a finished career, with the
 * next run saved exactly as the loop saves it before launching, so Start continues with that run.
 */
@DisplayName("Stop after this career")
class StopAfterCareerTest {
    private val queue = SessionEndFacts(queueEnabled = true, queueStateActive = true)

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val path = "android/app/src/main/java/com/steve1316/uma_android_automation/$relative"
        repeat(8) {
            listOf(File(dir, path), File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")).firstOrNull { it.isFile }?.let { return it.readText().replace("\r\n", "\n") }
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }

    private val start by lazy { source("StartModule.kt") }

    /** The queue loop's LAUNCH_NEXT branch, from its stop check to the next trainee's rotation. */
    private val launchNext by lazy {
        val from = start.indexOf("if (postCareerAction == PostCareerAction.LAUNCH_NEXT) {")
        val to = start.indexOf("val nextReuse = applyRotationForRun(rotation, i + 1, reuseLastLaunchSetup)", from)
        assertTrue(from > 0 && to > from, "the LAUNCH_NEXT branch")
        start.substring(from, to)
    }

    @Nested
    @DisplayName("the ending")
    inner class Ending {
        @Test
        fun `a pause keeps the resume record and is its own ending`() {
            val verdict = classifySessionEnd(queue.copy(stoppedAfterCareer = true))
            assertEquals(SessionEnd.STOPPED_AFTER_CAREER, verdict.end)
            assertFalse(SessionEnd.STOPPED_AFTER_CAREER.clearsQueueState)
            assertTrue(verdict.resumable)
            assertFalse(verdict.careerInFlight, "the career finished")
            assertEquals(SessionEnd.entries.last(), SessionEnd.STOPPED_AFTER_CAREER, "appended, since names are persisted")
        }

        @Test
        fun `another ending after the pause point wins, and the request is dropped`() {
            val paused = queue.copy(stoppedAfterCareer = true)
            assertEquals(SessionEnd.STOPPED_BY_USER, classifySessionEnd(paused.copy(stopRequested = true)).end)
            assertEquals(SessionEnd.SERVICE_ENDED, classifySessionEnd(paused.copy(serviceRunning = false)).end)
            assertEquals(SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS, classifySessionEnd(paused.copy(haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS)).end)
            assertEquals(SessionEnd.SINGLE_RUN_ENDED, classifySessionEnd(paused.copy(queueEnabled = false)).end)
            assertFalse(classifySessionEnd(paused.copy(stopRequested = true)).resumable, "a Stop clears the record as always")
        }

        @Test
        fun `its words say the player paused it and how to continue, never an error`() {
            val report = JSONObject().put("kind", "STOPPED_AFTER_CAREER").put("queueEnabled", true).put("totalRuns", 4).put("runReached", 2).put("resumable", true)
            val text = queueReportText(report)
            assertEquals("Queue paused", text.title)
            assertEquals("You paused the queue after run 2 of 4. Start continues with run 3.", text.reason)
            assertEquals("Press Start in UMA Auto+ within 24 hours, with Run Queue on and the same number of runs, to continue the queue.", text.nextAction)
            val lost = queueReportText(report.put("resumable", false))
            assertEquals("You paused the queue after run 2 of 4.", lost.reason, "no promise when the record is gone")
            assertNull(lost.nextAction)
            for (word in listOf("error", "interrupted", "failed", "crash")) assertFalse(text.reason.contains(word, ignoreCase = true), word)
            assertTrue(JSONObject(lastReportPayload(report.put("resumable", true).put("sessionId", "s").toString())!!).getBoolean("runEnding"))
        }

        @Test
        fun `its status key is terminal on every surface, and the progress line never posts it`() {
            assertEquals("The bot is not running", STATUS_LABELS["stoppedAfterCareer"])
            assertFalse(postsProgressLine("stoppedAfterCareer"))
            StatusBoard.reset(0L)
            StatusBoard.queueProgress(2, 4, "stoppedAfterCareer", "{}", 1_000L)
            val tally = StatusBoard.Tally(0, 0, 0, 0, 0, 0, 0)
            val status = StatusBoard.statusJson(StatusBoard.snapshot(), 2_000L, sessionActive = false, armed = false, lastProgressAt = null, tally = tally)
            assertEquals("stoppedAfterCareer", status.getString("statusKey"))
            StatusBoard.reset()
        }

        @Test
        fun `the update block names a paused queue too`() {
            val stop = updateBlockReason(botRunning = false, projectionRunning = false, sessionActive = false, resumableQueue = true)!!
            assertTrue(stop.text.startsWith("A paused or interrupted queue is saved."), stop.text)
        }
    }

    @Nested
    @DisplayName("where the queue stops")
    inner class StopPoint {
        @Test
        fun `after the career was recorded and its end steps ran, at the launch point, never mid-career`() {
            val recorded = start.indexOf("val runCareerEndSeq = recordRun(")
            val launch = start.indexOf("if (postCareerAction == PostCareerAction.LAUNCH_NEXT) {")
            assertTrue(recorded in 0 until launch, "the stop point follows recordRun")
            val block = launchNext.substringAfter("if (careerFinished && stopAfterCareerRequested) {")
            assertTrue(block.length < launchNext.length, "only after a finished career")
            val finalize = block.indexOf("navigateWithDeadline(reuseLastLaunchSetup, finalizeToHome = true)")
            val paused = block.indexOf("stoppedAfterCareerRun = i")
            assertTrue(finalize in 0 until paused, "the career-end steps run before the pause")
            assertTrue(block.indexOf("attachCareerEndSparks(ledger, i, runCareerEndSeq)") in finalize until paused)
            assertEquals(1, Regex("stopAfterCareerRequested").findAll(start.substringAfter("for (i in startFromRun..totalRuns) {")).count(), "read once, at the launch point")
        }

        @Test
        fun `the saved state is the loop's own pre-launch save of the next run`() {
            val save = launchNext.indexOf("saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)")
            val stop = launchNext.indexOf("if (careerFinished && stopAfterCareerRequested) {")
            assertTrue(save in 0 until stop, "PHASE_LAUNCHING for run i is saved before the stop")
            assertFalse(launchNext.substring(stop).contains("saveQueueState"), "no second save")
            assertFalse(launchNext.substring(stop).contains("clearQueueState"))
        }

        @Test
        fun `after the loop the pause keeps the record and reports its own key`() {
            val paused = start.indexOf("} else if (pausedAfterRun != null) {")
            val clear = start.indexOf("// Clear persisted queue state since queue finished normally.")
            assertTrue(paused in 0 until clear, "the pause branch comes before the clear")
            val branch = start.substring(paused, clear)
            assertTrue(branch.contains("sendQueueProgressEvent(pausedAfterRun, totalRuns, \"stoppedAfterCareer\""))
            assertFalse(branch.contains("clearQueueState") || branch.contains("queueHalted") || branch.contains("queueFailed") || branch.contains("notifyQueueHalted"))
            assertTrue(start.contains("val pausedAfterRun = stoppedAfterCareerRun?.takeIf { !queueStopRequested && BotService.isRunning }"), "a Stop after the pause point wins")
            assertTrue(launchNext.contains("ledger.stoppedAfterCareer = true\n                            break"))
        }

        @Test
        fun `a career-end navigation that fails is the between-runs halt, not a pause`() {
            val block = launchNext.substringAfter("if (careerFinished && stopAfterCareerRequested) {")
            val failed = block.substringAfter("if (!navResult.success) {").substringBefore("stoppedAfterCareerRun = i")
            assertTrue(failed.contains("ledger.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS"))
            assertTrue(failed.contains("if (navResult.lastDetectedState != \"STOPPED\")"), "a Stop during it is a Stop")
            assertTrue(failed.indexOf("break") > failed.indexOf("ledger.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS"), "it leaves the loop without pausing")
        }

        @Test
        fun `a retry runs before the stop point, so the request waits for the retried career`() {
            val retry = start.indexOf("val retried =\n                        decideRunRetry(")
            val recorded = start.indexOf("val runCareerEndSeq = recordRun(")
            assertTrue(retry in 0 until recorded)
        }

        @Test
        fun `the request lives in memory for one session only`() {
            assertTrue(start.contains("queueSkipRequested = false\n                stopAfterCareerRequested = false\n"), "cleared at session start")
            assertFalse(Regex("saveQueueState\\([^)]*stopAfter").containsMatchIn(start), "never persisted")
            assertTrue(start.contains("map.putBoolean(\"stopAfterCareer\", stopAfterCareerRequested)"), "Home can read it back")
        }
    }

    @Nested
    @DisplayName("continuing and edge cases")
    inner class Continuing {
        @Test
        fun `Start continues with the next run and counts the paused one as done`() {
            val plan = StartModule.resumePlanFor(StartModule.PHASE_LAUNCHING, currentRun = 2, savedCompletedRuns = 2)
            assertEquals(3, plan.startFromRun)
            assertEquals(2, plan.priorCompletedRuns)
        }

        @Test
        fun `the resumed run gets the rotation identity the loop would have given it`() {
            assertTrue(start.contains("val r = applyRotationForRun(rotation, startFromRun, reuseLastLaunchSetup)"), "resume applies the snapshot of the run it starts")
            assertTrue(start.contains("val nextReuse = applyRotationForRun(rotation, i + 1, reuseLastLaunchSetup)"), "the loop would have applied run i+1's")
        }

        @Test
        fun `on the last run and in a single run the request changes nothing`() {
            for (code in TaskResultCode.entries) {
                assertTrue(StartModule.decidePostCareerAction(code, runIndex = 4, totalRuns = 4, enableRunQueue = true, queueStopRequested = false, botRunning = true) != PostCareerAction.LAUNCH_NEXT)
                assertTrue(StartModule.decidePostCareerAction(code, runIndex = 1, totalRuns = 1, enableRunQueue = false, queueStopRequested = false, botRunning = true) != PostCareerAction.LAUNCH_NEXT)
            }
        }
    }
}
