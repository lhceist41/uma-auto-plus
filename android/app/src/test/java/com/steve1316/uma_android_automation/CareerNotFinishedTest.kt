package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.json.JSONObject
import java.io.File

/**
 * A Finish the game never took (the daily reset bounced it) leaves the finished career in the slot. The finalize and the launch after a
 * finished career stop on that proof, keep the career, and point Start back at the run that played it.
 */
@DisplayName("Career still in progress after its Finish")
class CareerNotFinishedTest {
    private val unread: () -> Boolean = { throw AssertionError("the slot was read when it could not decide") }

    @Nested
    @DisplayName("decision")
    inner class Decision {
        @Test
        fun `the in-progress wordmark on Home after a finished career means the Finish did not land`() {
            assertTrue(careerStillInSlotAfterFinish(followsFinishedCareer = true, newCareerLaunched = false) { true })
        }

        @Test
        fun `a plain Home after a finished career is a finished career`() {
            assertFalse(careerStillInSlotAfterFinish(followsFinishedCareer = true, newCareerLaunched = false) { false })
        }

        @Test
        fun `a launch that follows no finished career resumes the slot as before, without reading it`() {
            assertFalse(careerStillInSlotAfterFinish(followsFinishedCareer = false, newCareerLaunched = false, careerInSlot = unread))
        }

        @Test
        fun `a career this navigation launched itself is never mistaken for the finished one`() {
            assertFalse(careerStillInSlotAfterFinish(followsFinishedCareer = true, newCareerLaunched = true, careerInSlot = unread))
        }
    }

    @Nested
    @DisplayName("the launch latch")
    inner class LaunchLatch {
        /** The navigator's flag after a launch latch fires at each step of [homeSeenAtLatch]. */
        private fun launched(finalizeToHome: Boolean, vararg homeSeenAtLatch: Boolean) = homeSeenAtLatch.any { launchesNewCareer(finalizeToHome, homeSeen = it) }

        @Test
        fun `a finalize still halts on the wordmark after the relaunched game's intro latched before Home, as at a daily reset bounce`() {
            val latched = launched(finalizeToHome = true, false, false)
            assertTrue(careerStillInSlotAfterFinish(followsFinishedCareer = true, newCareerLaunched = latched) { true })
        }

        @Test
        fun `a finalize never launches a career, even with a latch after Home`() {
            assertFalse(launchesNewCareer(finalizeToHome = true, homeSeen = true))
        }

        @Test
        fun `the launch after a finished career with rotation off still halts when the intro latched before Home`() {
            val latched = launched(finalizeToHome = false, false)
            assertTrue(careerStillInSlotAfterFinish(followsFinishedCareer = true, newCareerLaunched = latched) { true })
        }

        @Test
        fun `a career the launch started after Home is never mistaken for the finished one`() {
            val latched = launched(finalizeToHome = false, false, true)
            assertFalse(careerStillInSlotAfterFinish(followsFinishedCareer = true, newCareerLaunched = latched, careerInSlot = unread))
        }

        @Test
        fun `the checks read this flag, set only at the launch latch and reset per navigation`() {
            val navigator = source("$main/CareerLaunchNavigator.kt")
            assertFalse(navigator.contains("careerStillInSlotAfterFinish(followsFinishedCareer, careerLaunchInitiated)"), "the latch alone fires on the relaunch intro")
            assertEquals(2, Regex(Regex.escape("careerStillInSlotAfterFinish(followsFinishedCareer, newCareerLaunched)")).findAll(navigator).count())
            val latch = navigator.indexOf("                    careerLaunchInitiated = true\n")
            assertTrue(navigator.substring(latch).lineSequence().drop(1).first().trim() == "if (launchesNewCareer(finalizeToHomeMode, homeSeen = launchFlowEntered)) newCareerLaunched = true")
            assertEquals(1, Regex(Regex.escape("newCareerLaunched = true")).findAll(navigator).count(), "set only there")
            assertTrue(navigator.contains("careerLaunchInitiated = false\n        newCareerLaunched = false\n"), "reset with the latch")
        }
    }

    @Nested
    @DisplayName("navigator wiring")
    inner class NavigatorWiring {
        private val navigator by lazy { source("$main/CareerLaunchNavigator.kt") }

        @Test
        fun `the finalize Home branch reads both wordmarks before it reports success`() {
            val branch = navigator.substring(navigator.indexOf("LaunchScreenState.HOME_SCREEN ->"), navigator.indexOf("LaunchScreenState.QUICK_MODE_PROMPT -> handleQuickModePrompt()"))
            val check = branch.indexOf("if (careerStillInSlotAfterFinish(followsFinishedCareer, newCareerLaunched) { homeShowsCareerInProgress() }) {")
            assertTrue(check >= 0, "Home consults the slot")
            assertTrue(check < branch.indexOf("careerNotFinished(") && branch.indexOf("careerNotFinished(") < branch.indexOf("} else if (finalizeToHomeMode) {"), "the stop comes before the finalize success")
            assertTrue(branch.indexOf("} else if (finalizeToHomeMode) {") < branch.indexOf("TransitionResult.Success"), "a plain Home still ends the finalize")
            assertTrue(branch.contains("handleHomeScreen()"), "and still launches the next career")
            val read = body(navigator, "private fun homeShowsCareerInProgress(): Boolean {")
            assertTrue(read.contains("ButtonCareerHomeTextActive.check(iu, sourceBitmap = bitmap) || ButtonCareerHomeTextEvent.check(iu, sourceBitmap = bitmap)"))
            val first = read.indexOf("if (!wordmark(iu.getSourceBitmap())) return false")
            assertTrue(first in 0 until read.indexOf("waitSafe(1.0)") && read.indexOf("waitSafe(1.0)") < read.indexOf("return wordmark(iu.getSourceBitmap())"), "two fresh captures")
        }

        @Test
        fun `Continue Career after a finished career stops before Resume, in both modes`() {
            val handler = body(navigator, "private fun handleContinueCareerDialog(): TransitionResult {")
            val stop = handler.indexOf("if (careerStillInSlotAfterFinish(followsFinishedCareer, newCareerLaunched) { true }) {")
            assertTrue(stop >= 0 && stop < handler.indexOf("ButtonResume.click(iu)"), "no Resume into the next slot")
            assertTrue(handler.substring(stop).startsWith("if (careerStillInSlotAfterFinish(followsFinishedCareer, newCareerLaunched) { true }) {\n            return careerNotFinished("))
            assertTrue(
                navigator.contains(
                    "followsFinishedCareer = (finalizeToHome || previousCareerComplete) && scenario != \"Daily Races\" && scenario != \"Team Trials\"",
                ),
                "finalize and the launch after a finished career; a misc task's slot is the player's",
            )
        }

        @Test
        fun `the stop taps nothing and carries its own key`() {
            val stop = body(navigator, "private fun careerNotFinished(transition: String): TransitionResult.Failed {")
            assertTrue(stop.contains("reasonKey = \"CAREER_NOT_FINISHED\""))
            assertFalse(Regex("\\.tap|click\\(", RegexOption.IGNORE_CASE).containsMatchIn(stop), "no tap or click")
            assertTrue("CAREER_NOT_FINISHED" in REPORT_REASON_KEYS, "the stop has its own words for the player")
        }
    }

    @Nested
    @DisplayName("queue")
    inner class Queue {
        private val start by lazy { source("$main/StartModule.kt") }

        @Test
        fun `the halt saves CAREER for the run that played the career, not LAUNCHING`() {
            val halt = body(start, "fun haltCareerNotFinished(run: Int) {")
            assertTrue(halt.contains("saveQueueState(context, active = true, currentRun = run, totalRuns = totalRuns, phase = PHASE_CAREER, completedRuns = completedRuns)"))
            assertFalse(halt.contains("PHASE_LAUNCHING"))
            assertTrue(halt.contains("ledger.haltEnd = SessionEnd.RUN_HALTED"))
            assertTrue(halt.contains("queueHaltRun = run\n"))
            assertTrue(halt.contains("queueHaltCareerInFlight = true"))
            assertTrue(halt.contains("lastCareerFinished = false"), "the queue does not report itself finished")
            assertTrue(halt.indexOf("completedRuns--") in 0 until halt.indexOf("saveQueueState("), "the career is not counted as finished")
        }

        @Test
        fun `a resume from that record re-enters the same run`() {
            val plan = StartModule.resumePlanFor(StartModule.PHASE_CAREER, currentRun = 2, savedCompletedRuns = 1)
            assertEquals(2, plan.startFromRun)
            assertEquals(1, plan.priorCompletedRuns)
        }

        @Test
        fun `the finalize, the stop after a career and the launch of the next run all halt on it`() {
            val finalize = start.substring(start.indexOf("val finalizeResult = navigateWithDeadline(reuseLastLaunchSetup, finalizeToHome = true)"))
            val finalizeHalt = finalize.indexOf("} else if (enableRunQueue && finalizeResult.reasonKey == \"CAREER_NOT_FINISHED\") {")
            assertTrue(finalizeHalt in 0 until finalize.indexOf("ledger.finalizeStopKey = finalizeResult.reasonKey"), "a queue halts instead of reporting itself finished")
            assertTrue(finalize.substring(finalizeHalt).contains("haltCareerNotFinished(i)"))

            for (call in listOf("val navResult = navigateWithDeadline(reuseLastLaunchSetup, finalizeToHome = true)", "val navResult = navigateWithDeadline(nextReuse, previousCareerComplete = careerFinished")) {
                val site = start.substring(start.indexOf(call))
                val halt = site.indexOf("if (navResult.reasonKey == \"CAREER_NOT_FINISHED\") {\n")
                assertTrue(halt in 0 until site.indexOf("ledger.haltEnd = SessionEnd.NAVIGATION_FAILED_BETWEEN_RUNS"), call)
                assertTrue(site.substring(halt).lineSequence().drop(1).first().trim() == "haltCareerNotFinished(i)", call)
            }
        }

        @Test
        fun `the player reads why, and that Start finishes it`() {
            val halted = JSONObject().put("kind", "RUN_HALTED").put("queueEnabled", true).put("totalRuns", 2).put("runReached", 2).put("resumable", true).put("reasonKey", "CAREER_NOT_FINISHED")
            val text = queueReportText(halted)
            assertTrue(text.reason.startsWith("The queue stopped during run 2 of 2: the game's daily reset"))
            assertTrue(text.reason.endsWith("Start finishes it."))
            val single = JSONObject().put("kind", "SINGLE_RUN_ENDED").put("queueEnabled", false).put("totalRuns", 1).put("finalizeStopKey", "CAREER_NOT_FINISHED")
            assertEquals("Career not finished", queueReportText(single).title)
        }
    }

    private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

    private fun body(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue(start >= 0, signature)
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, i + 1)
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
