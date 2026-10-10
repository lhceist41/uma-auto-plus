package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.StartModule.Companion.PHASE_CAREER
import com.steve1316.uma_android_automation.StartModule.Companion.PHASE_LAUNCHING
import com.steve1316.uma_android_automation.StartModule.Companion.RotationConfig
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.File

/** A stopped or resumed queue plays the right slot and the right career, and every stop says why and what to do next. */
@DisplayName("Queue stop and resume")
class QueueStopResumeTest {
    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"
    private val start by lazy { source("$main/StartModule.kt") }
    private val campaign by lazy { source("$main/bot/Campaign.kt") }

    private fun body(text: String, signature: String): String {
        val begin = text.indexOf(signature)
        assertTrue(begin >= 0, signature)
        val open = text.indexOf('{', text.indexOf(")", begin))
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(begin, i + 1)
            }
        }
        throw AssertionError("unbalanced: $signature")
    }

    private fun ledger(queueEnabled: Boolean, total: Int) =
        SessionLedger("stop", 1_000L, "9.9.9", 1).apply {
            this.queueEnabled = queueEnabled
            totalRuns = total
        }

    @Nested
    @DisplayName("a single run the bot stopped says why")
    inner class SingleRunBotStop {
        @Test
        fun `the writer gives a single run the bot's stop key when the run carried none`() {
            val writer = body(start, "private fun writeSessionReport(ledger: SessionLedger): QueueReport? {")
            assertTrue(writer.contains("if (verdict.end == SessionEnd.SINGLE_RUN_ENDED && facts.stopByBot && ledger.reasonKey.isEmpty()) ledger.reasonKey = queueStopKey.orEmpty()"))
        }

        @Test
        fun `a career from another scenario reads as that stop, with its fix`() {
            val l = ledger(queueEnabled = false, total = 1).apply { addRun(RunRecord(1, 0L, 0L, "TASK_RESULT_MANUALLY_STOPPED", null, null, null, null)) }
            val verdict = classifySessionEnd(l.facts(stopRequested = true, stopByBot = true, serviceRunning = true, queueStateActive = false))
            assertEquals(SessionEnd.SINGLE_RUN_ENDED, verdict.end)
            l.reasonKey = "CAREER_SCENARIO_MISMATCH"
            val text = queueReportText(l.report(verdict, 2_000L).toJson())
            assertEquals("Run stopped", text.title)
            assertEquals("The bot stopped the run: the career in the game is from a different scenario than this run's settings. The career is kept.", text.reason)
            assertTrue(text.nextAction!!.startsWith("Select a preset for that career's scenario on Home"))
        }

        @Test
        fun `the player's own stop of a single run keeps its plain words`() {
            val report = JSONObject().put("kind", "SINGLE_RUN_ENDED").put("totalRuns", 1).put("runs", JSONArray().put(JSONObject().put("run", 1).put("resultCode", "TASK_RESULT_MANUALLY_STOPPED")))
            assertEquals("The run was stopped.", queueReportText(report).reason)
        }
    }

    @Nested
    @DisplayName("a bot stop that kept the queue reports it as kept")
    inner class BotStopKeepsQueue {
        private val botStop = SessionEndFacts(queueEnabled = true, stopRequested = true, stopByBot = true)

        @Test
        fun `it is resumable exactly when the record survived`() {
            assertTrue(classifySessionEnd(botStop.copy(queueStateActive = true)).resumable)
            assertFalse(classifySessionEnd(botStop.copy(queueStateActive = false)).resumable, "the record went with the last career")
        }

        @Test
        fun `the continued queue's card keeps the runs before the stop`() {
            val l =
                ledger(queueEnabled = true, total = 5).apply {
                    completedRuns = 2
                    reasonKey = "TRAINEE_MISMATCH"
                    (1..2).forEach { addRun(RunRecord(it, 0L, 0L, "TASK_RESULT_COMPLETE", null, null, "COMPLETED", 75)) }
                    addRun(RunRecord(3, 0L, 0L, "TASK_RESULT_MANUALLY_STOPPED", null, null, null, null))
                }
            val report = l.report(classifySessionEnd(l.facts(stopRequested = true, stopByBot = true, serviceRunning = true, queueStateActive = true)), 2_000L).toJson()
            val earlier = earlierQueueFor(report, totalRuns = 5, startFromRun = 3) ?: fail("dropped as if the queue had been cleared")
            assertEquals(listOf(1, 2), (0 until earlier.getJSONArray("runs").length()).map { earlier.getJSONArray("runs").getJSONObject(it).getInt("run") })
            assertEquals("TRAINEE_MISMATCH", earlier.getJSONArray("stops").getJSONObject(0).getString("reasonKey"))
        }

        @Test
        fun `a kept queue reads as paused, and a stop whose fix starts over never says to continue it`() {
            val kept = JSONObject().put("kind", "STOPPED_BY_BOT").put("queueEnabled", true).put("totalRuns", 5).put("completedRuns", 2).put("runReached", 3).put("resumable", true)
            val data = queueReportText(JSONObject(kept.toString()).put("reasonKey", "DATA_DOWNLOAD_PROMPT"))
            assertEquals("Queue paused", data.title)
            assertTrue(data.nextAction!!.endsWith("within 24 hours, with Run Queue on and the same number of runs, to continue the queue."))
            val scenario = queueReportText(JSONObject(kept.toString()).put("reasonKey", "CAREER_SCENARIO_MISMATCH"))
            assertFalse(scenario.nextAction!!.contains("continue the queue"))
        }
    }

    @Nested
    @DisplayName("a resumed rotation keeps its resync")
    inner class ResyncOffset {
        private val now = 10_000_000L

        private fun record(vararg extra: Pair<String, String>) =
            mapOf("active" to "true", "currentRun" to "3", "totalRuns" to "6", "phase" to PHASE_CAREER, "timestamp" to (now - 60_000L).toString()) + extra

        @Test
        fun `the saved offset is read back, and a record saved before it reads as none`() {
            assertEquals(1, StartModule.queueStateFrom(record("rotationOffset" to "1"), now)!!.rotationOffset)
            assertEquals(0, StartModule.queueStateFrom(record(), now)!!.rotationOffset)
            assertEquals(0, StartModule.queueStateFrom(record("rotationOffset" to "x"), now)!!.rotationOffset)
        }

        @Test
        fun `with the saved offset the resumed run reloads the slot it was resynced onto`() {
            // A URA slot and a Grand Concert slot: the resync refuses a slot of another scenario, so a lost offset is never repaired.
            val rotation = RotationConfig(enabled = true, switchEvery = 1, inGameNames = listOf("Ura Trainee", "Concert Trainee"))
            val offset = rotation.resyncOffsetFor(run = 3, targetIndex = 1)
            val saved = StartModule.queueStateFrom(record("rotationOffset" to offset.toString()), now)!!
            assertEquals(1, rotation.indexForRun(saved.currentRun, saved.rotationOffset))
            assertEquals(0, rotation.indexForRun(saved.currentRun, 0), "the slot a lost offset reloads")
        }

        @Test
        fun `the offset is saved with every live record and at the resync, and restored before the first slot loads`() {
            assertTrue(body(start, "fun saveQueueState(").contains("arrayOf(\"queueState\", \"rotationOffset\", rotationCursorOffset.toString())"))
            val resync = body(start, "fun resyncRotationOntoCareer(context: Context, index: Int): Boolean {")
            assertTrue(resync.indexOf("setQueueStateValue(context, \"rotationOffset\", rotationCursorOffset.toString())") > resync.indexOf("rotationCursorOffset = rotation.resyncOffsetFor("))
            val reset = start.indexOf("                rotationCursorOffset = 0\n")
            val read = start.indexOf("resumedRotationOffset = saved.rotationOffset")
            val restore = start.indexOf("rotationCursorOffset = resumedRotationOffset")
            val firstSlot = start.indexOf("val r = applyRotationForRun(rotation, startFromRun, reuseLastLaunchSetup)")
            assertTrue(reset in 0 until read && read < restore && restore < firstSlot)
        }
    }

    @Nested
    @DisplayName("a stop inside a run's own launch")
    inner class OwnLaunchStop {
        private val launchError = TaskResult.Error(TaskResultCode.TASK_RESULT_QUEUE_NAVIGATION_FAILED, "Auto-navigation to training menu failed: stopped.")

        @Test
        fun `only a launch that had not re-entered a career counts`() {
            assertTrue(StartModule.stopEndedRunLaunch(launchError, careerInFlight = false))
            assertFalse(StartModule.stopEndedRunLaunch(launchError, careerInFlight = true), "a resumed career is in the slot")
            assertFalse(StartModule.stopEndedRunLaunch(TaskResult.Success(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, "stopped"), careerInFlight = false), "a stop inside the career")
            assertFalse(StartModule.stopEndedRunLaunch(TaskResult.Error(TaskResultCode.TASK_RESULT_UNHANDLED_EXCEPTION, "boom"), careerInFlight = false))
        }

        @Test
        fun `the record points Start at that run's launch, never at a career in flight`() {
            val stop = body(start, "TaskResultCode.TASK_RESULT_MANUALLY_STOPPED -> {")
            val own = stop.indexOf("if (enableRunQueue && stopEndedRunLaunch(result, runReEntersCareer || retried)) {")
            assertTrue(own >= 0)
            val ownBranch = stop.substring(own, stop.indexOf("stopLeftCareer = runScenario", own))
            val save = ownBranch.indexOf("saveQueueState(context, active = true, currentRun = i - 1, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)")
            assertTrue(ownBranch.indexOf("runLaunchStopped = i > 1") in 0 until ownBranch.indexOf("if (runLaunchStopped) {"))
            assertTrue(save in ownBranch.indexOf("if (runLaunchStopped) {") until ownBranch.indexOf("clearQueueState(context)"), "run 1 has no launch boundary to save")
            assertFalse(ownBranch.contains("stopLeftCareer = "))
            assertTrue(start.contains("val runReEntersCareer = (i == startFromRun && resumeReEntersCareer) || previousRunLeftCareer\n                    nextRunCareerInFlight = runReEntersCareer"))
            // The saved launch boundary resumes the same run as a new launch.
            val plan = StartModule.resumePlanFor(PHASE_LAUNCHING, currentRun = 3, savedCompletedRuns = 3)
            assertEquals(4, plan.startFromRun)
            assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = false, stopLeftCareer = false, launchStopped = true))
        }
    }

    @Nested
    @DisplayName("a stop during the wait between runs")
    inner class WaitStop {
        @Test
        fun `keeps the saved run, which says where Start picks up`() {
            val abort = start.substring(start.indexOf("if (!interruptibleWait(delayBetweenRuns)) {"), start.indexOf("MessageLog.i(TAG, \"[QUEUE] Between-run wait aborted. Exiting queue.\")"))
            val keep = abort.indexOf("} else if (!isMiscQueue && navigationStoppedByPlayer(queueStopRequested, BotService.isRunning, queueStopReason, lastRunPostedException)) {")
            assertTrue(keep > abort.indexOf("ledger.haltEnd = SessionEnd.WAIT_INTERRUPTED"))
            assertTrue(abort.indexOf("stopLeftCareer = true") > keep)
            assertTrue(abort.indexOf("queueStopRequested = true") > keep, "an overlay Stop reads as the player's stop, as after a run")
            assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = false, stopLeftCareer = true))
            assertEquals(4, StartModule.resumePlanFor(PHASE_LAUNCHING, currentRun = 3, savedCompletedRuns = 3).startFromRun, "after a finished career: the next run plays the waiting one")
            assertEquals(3, StartModule.resumePlanFor(PHASE_CAREER, currentRun = 3, savedCompletedRuns = 2).startFromRun, "after an unfinished one: the same run carries it on")
        }
    }

    @Nested
    @DisplayName("the rotation's trainee check")
    inner class RotationCheckGate {
        @Test
        fun `runs only for a queue, which is the only session that applies a rotation slot`() {
            val verify = body(campaign, "private fun verifyRotationTrainee() {")
            val gate = "if (!SettingsHelper.getBooleanSetting(\"runQueue\", \"enableRunQueue\", true) || !SettingsHelper.getBooleanSetting(\"runQueue\", \"enableTraineeRotation\", false)) return"
            assertTrue(verify.indexOf(gate) in 0 until verify.indexOf("queueState\", \"currentTrainee\""))
            assertTrue(start.contains("if (enableRunQueue && rotation.enabled && startFromRun <= totalRuns && BotService.isRunning && !queueStopRequested) {"))
        }
    }

    @Nested
    @DisplayName("an unfinished Finish's halt")
    inner class CareerNotFinishedWords {
        @Test
        fun `tells the player to press Start, not to handle the career by hand`() {
            assertTrue(StartModule.CAREER_NOT_FINISHED_NEXT.contains("Press Start in UMA Auto+ and the bot finishes it"))
            assertFalse(StartModule.CAREER_NOT_FINISHED_NEXT.contains("in-game"))
            val notify = body(start, "private fun notifyQueueHalted(")
            assertTrue(notify.indexOf("CAREER_NOT_FINISHED_NEXT") in 0 until notify.indexOf("handled in-game"), "a lost Finish is decided first")
            assertTrue(start.contains("notifyQueueHalted(doneRuns, totalRuns, unrun, halt, queueHaltCareerInFlight, queueHaltFinishLost)"))
            val log = start.indexOf("MessageLog.e(TAG, \"[QUEUE] \$CAREER_NOT_FINISHED_NEXT\")")
            assertTrue(log in start.indexOf("if (queueHaltFinishLost) {") until start.indexOf("no further run can start until it is finished or abandoned in-game"))
        }
    }

    @Nested
    @DisplayName("the resumed-career scenario check's documentation")
    inner class ScenarioCheckDocs {
        @Test
        fun `names the scenarios the screen can prove and says a URA Finale career is not caught`() {
            val buttons = campaign.substringAfter("private val scenarioButtons: Map<String, ButtonInterface> =").substringBefore("\n\n")
            assertFalse(buttons.contains("URA"))
            val paragraph = source("TROUBLESHOOTING.md").lines().first { it.startsWith("Before a career it did not start itself plays its first turn") }
            for (scenario in listOf("Unity Cup", "Trackblazer", "Grand Concert")) assertTrue(paragraph.contains(scenario), scenario)
            assertTrue(paragraph.contains("A URA Finale career has nothing on that screen to recognise it by"))
        }
    }
}
