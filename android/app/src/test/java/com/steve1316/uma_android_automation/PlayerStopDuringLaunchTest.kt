package com.steve1316.uma_android_automation

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The player's Stop (app or overlay) while the next run's launch navigation runs is the player's stop: the queue says so and
 * keeps its saved run for Start. An interrupt nobody can attribute to the player keeps today's words.
 */
@DisplayName("Player stop during a launch navigation")
class PlayerStopDuringLaunchTest {
    @Nested
    @DisplayName("decision")
    inner class Decision {
        @Test
        fun `the app's Stop sets the stop flag with no bot reason`() {
            assertTrue(StartModule.navigationStoppedByPlayer(stopRequested = true, botRunning = true, botStopReason = null, runPostedException = false))
        }

        @Test
        fun `the overlay's Stop ends the service with no bot reason and no crash`() {
            assertTrue(StartModule.navigationStoppedByPlayer(stopRequested = false, botRunning = false, botStopReason = null, runPostedException = false))
        }

        @Test
        fun `a bot stop is never the player's`() {
            for (running in listOf(true, false)) {
                assertFalse(StartModule.navigationStoppedByPlayer(stopRequested = true, botRunning = running, botStopReason = "Between-run navigation did not respond", runPostedException = false))
            }
        }

        @Test
        fun `an interrupt with the service still running and no stop request is not the player's`() {
            assertFalse(StartModule.navigationStoppedByPlayer(stopRequested = false, botRunning = true, botStopReason = null, runPostedException = false))
        }

        @Test
        fun `a teardown after a crash is not the player's`() {
            assertFalse(StartModule.navigationStoppedByPlayer(stopRequested = false, botRunning = false, botStopReason = null, runPostedException = true))
        }
    }

    @Nested
    @DisplayName("resume record")
    inner class ResumeRecord {
        @Test
        fun `a player stop during a launch keeps the saved run for Start`() {
            assertTrue(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = false, stopLeftCareer = false, launchStopped = true))
        }

        @Test
        fun `but not after the last career, which it would replay`() {
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = true, stopLeftCareer = false, launchStopped = true))
        }

        @Test
        fun `a player stop between careers outside a launch still clears it`() {
            assertFalse(StartModule.keepsResumeRecordAfterStop(queueStopRequested = true, botStopReason = null, lastCareerFinished = false, stopLeftCareer = false))
        }

        @Test
        fun `the between-run launch saves the next run before it navigates, and a stopped navigation does not halt`() {
            val site = start.substring(start.indexOf("val careerFinished = effectiveResult.code == TaskResultCode.TASK_RESULT_COMPLETE"))
            val save = site.indexOf("saveQueueState(context, active = true, currentRun = i, totalRuns = totalRuns, phase = PHASE_LAUNCHING, completedRuns = completedRuns)")
            val nav = site.indexOf("val navResult = navigateWithDeadline(nextReuse, previousCareerComplete = careerFinished")
            assertTrue(save in 0 until nav, "Start continues at the next run")
            val failure = site.substring(nav)
            assertTrue(failure.indexOf("} else if (navResult.lastDetectedState != \"STOPPED\") {") in 0 until failure.indexOf("ledger.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS"))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val wrapper by lazy { body(start, "private fun navigateWithDeadline(") }

        @Test
        fun `both ways out of the navigation read the player's stop`() {
            assertTrue(wrapper.contains("noteStopByPlayer(navigator.navigate("), "a navigation that returned")
            assertTrue(wrapper.contains("noteStopByPlayer(result).copy(careerResumed = navigator.careerResumed)"), "an interrupted one")
        }

        @Test
        fun `an unexplained interrupt waits for the overlay Stop before it is called an interrupt`() {
            val settle = wrapper.indexOf("if (!deadlineFired.get()) awaitStopEvidence()")
            val stopped = wrapper.indexOf("queueStopRequested || !BotService.isRunning -> stoppedNavigation()")
            assertTrue(settle in 0 until stopped, "the stop evidence settles before it is read")
            assertTrue(stopped > wrapper.indexOf("deadlineFired.get() ->"), "a deadline wedge is decided first")
            assertTrue(stopped < wrapper.indexOf("lastDetectedState = \"INTERRUPTED\""))
        }

        @Test
        fun `a teardown without a stop request keeps today's reason`() {
            assertTrue(
                wrapper.contains("failureReason = \"Between-run navigation was interrupted before the deadline (likely a stop or service teardown), not a wedge.\","),
            )
        }

        @Test
        fun `the player's stop raises the stop flag and keeps the record, and only then`() {
            val note = body(start, "private fun noteStopByPlayer(result: NavigationResult): NavigationResult {")
            val decide = note.indexOf("navigationStoppedByPlayer(queueStopRequested, BotService.isRunning, queueStopReason, lastRunPostedException)")
            assertTrue(decide in 0 until note.indexOf("queueStopRequested = true"))
            assertTrue(note.indexOf("queueStopRequested = true") < note.indexOf("launchStoppedByPlayer = true"))
            assertTrue(Regex("""if \(result\.success \|\|.* !navigationStoppedByPlayer\(""").containsMatchIn(note), "a success or anyone else's stop is returned unchanged")
            assertEquals(1, Regex("launchStoppedByPlayer = true").findAll(start).count(), "set only there")
            assertTrue(start.contains("accessibilityHaltKey = null\n                launchStoppedByPlayer = false"), "reset every session")
            assertTrue(start.contains("keepsResumeRecordAfterStop(queueStopRequested, stopReason, lastCareerFinished, stopLeftCareer, launchStoppedByPlayer)"))
        }

        @Test
        fun `an unfinished career the game reported is never rewritten into the player's stop`() {
            val note = body(start, "private fun noteStopByPlayer(result: NavigationResult): NavigationResult {")
            val unchanged = note.substring(0, note.indexOf(") return result\n"))
            assertTrue(unchanged.contains("result.reasonKey == \"CAREER_NOT_FINISHED\""), "its halt keeps the record and re-enters the career")
            assertTrue(unchanged.length < note.indexOf("queueStopRequested = true"))
        }
    }

    @Nested
    @DisplayName("words")
    inner class Words {
        @Test
        fun `the session ends as the player's stop, resumable, in the player-stop words`() {
            val verdict = classifySessionEnd(SessionEndFacts(queueEnabled = true, stopRequested = true, stopByBot = false, serviceRunning = false, queueStateActive = true))
            assertEquals(SessionEnd.STOPPED_BY_USER, verdict.end)
            assertTrue(verdict.resumable)
            val text = queueReportText(JSONObject().put("kind", verdict.end.name).put("queueEnabled", true).put("totalRuns", 2).put("completedRuns", 1).put("resumable", verdict.resumable))
            assertEquals("Queue paused", text.title)
            assertEquals("You stopped the queue with 1 of 2 runs done.", text.reason)
            assertFalse(text.reason.contains("could not get past"))
        }
    }

    private val start by lazy { source("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt") }

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

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative).readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }
}
