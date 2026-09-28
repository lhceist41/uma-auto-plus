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
 * A Continue Career Resume re-enters a career that already occupies the game's slot: it owes no
 * Trainee Select, hands the career to the campaign, and every halt after it says a career is in
 * flight. A frozen game that UMA Auto+ could not close is reported as such, with the player's fix.
 */
@DisplayName("Resuming a career in progress, and a game that froze")
class ResumedCareerHandoffTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"

    private fun source(relative: String) = repoFile("$main/$relative").readText().replace("\r\n", "\n")

    private val navigator by lazy { source("CareerLaunchNavigator.kt") }
    private val startModule by lazy { source("StartModule.kt") }

    private fun block(text: String, start: String, end: String): String {
        val from = text.indexOf(start)
        assertTrue(from >= 0, "missing: $start")
        val to = text.indexOf(end, from + start.length)
        assertTrue(to > from, "missing end: $end")
        return text.substring(from, to)
    }

    @Nested
    @DisplayName("the Resume")
    inner class Resume {
        private val handler by lazy { block(navigator, "private fun handleContinueCareerDialog(): TransitionResult {", "private fun handleCareerSummary()") }

        @Test
        fun `a GOAL COMPLETE Next after Resume is no longer suppressed as a misread roster`() {
            // The 2026-09-28 failure: rotation on, Home seen, Resume, then GOAL COMPLETE read as POST_RUN_RESULTS.
            val pendingAtLaunch = RosterLivenessPolicy.rosterSelectionPending(finalizeToHome = false, rotationEnabled = true, singleRunTargetArmed = false)
            assertTrue(RosterLivenessPolicy.expectationActive(launchFlowEntered = true, rosterSelectionPending = pendingAtLaunch, careerLaunchInitiated = false), "the trap this fixes")
            assertTrue(handler.contains("rosterSelectionPending = false"), "a Resume must end the Trainee Select obligation")
            assertFalse(RosterLivenessPolicy.expectationActive(launchFlowEntered = true, rosterSelectionPending = false, careerLaunchInitiated = false))
        }

        @Test
        fun `the obligation ends and the resume is recorded as soon as the Resume tap lands, before any wait`() {
            val click = handler.indexOf("if (ButtonResume.click(iu)) {")
            val wait = handler.indexOf("waitSafe(3.0)", click)
            assertTrue(click >= 0 && wait > click)
            val beforeWait = handler.substring(click, wait)
            assertTrue(beforeWait.contains("rosterSelectionPending = false"))
            assertTrue(beforeWait.contains("careerResumed = true"))
        }

        @Test
        fun `the career goes to the campaign only once the Continue Career dialog is gone`() {
            val success = handler.indexOf("return TransitionResult.Success")
            assertTrue(success >= 0, "a Resume hands the career over")
            val guard = handler.lastIndexOf("if (!ButtonResume.check(iu)) {", success)
            assertTrue(guard in 0 until success, "Success only after the dialog is verified gone")
            assertTrue(handler.substring(success).contains("return TransitionResult.Continue"), "a dialog still shown is re-detected, not handed over")
        }

        @Test
        fun `a started-over launch keeps the resume, a new navigation clears it`() {
            val navigate = block(navigator, "fun navigate(", "pendingBetweenRunDialog = null")
            assertTrue(navigate.contains("if (!restartingLaunch) careerResumed = false\n        resumeHandoverPending = false\n        if (restartingLaunch) {"))
            assertEquals(1, Regex("careerResumed = false").findAll(navigator).count())
            assertEquals(1, Regex("\n        resumeHandoverPending = false\n").findAll(navigator).count(), "the handover never outlives its own pass")
        }

        @Test
        fun `after a Resume any screen but the dialog is handed over before a launch handler can act on it`() {
            val loop = block(navigator, "val detectedState =\n", "handleState(currentState, reuseLastLaunchSetup, autoFillSupports)")
            val guard = "if (resumeHandoverPending && detectedState != LaunchScreenState.CONTINUE_CAREER_DIALOG) {"
            val at = loop.indexOf(guard)
            assertTrue(at >= 0, "the handover is checked on every detected screen")
            assertTrue(at < loop.indexOf("if (detectedState != LaunchScreenState.UNKNOWN) {"), "before stuck counting, unknown handling and any relaunch")
            val handover = loop.substring(at, loop.indexOf("}", at))
            assertTrue(handover.contains("return NavigationResult(success = true, lastDetectedState = detectedState.name, careerResumed = true)"))
            assertTrue(handler.contains("resumeHandoverPending = true"), "only a Resume arms it")
            assertEquals(1, Regex("resumeHandoverPending = true").findAll(navigator).count())
        }

        @Test
        fun `every navigation result carries the resume, interrupted ones included`() {
            val wrapper = block(startModule, "private fun navigateWithDeadline(", "/** Logs a failed [NavigationResult]")
            assertEquals(1, Regex("""navigator\.navigate\(""").findAll(wrapper).count())
            assertEquals(2, Regex("""\.copy\(careerResumed = navigator\.careerResumed\)""").findAll(wrapper).count(), "the normal return and the interrupted results")
            val interrupted = wrapper.substring(wrapper.indexOf("} catch (e: InterruptedException) {"), wrapper.indexOf("} finally {"))
            assertTrue(interrupted.contains("            }.copy(careerResumed = navigator.careerResumed)\n"), "the copy wraps every branch of the interrupt result")
            assertEquals(3, Regex("NavigationResult\\(").findAll(interrupted).count())
        }
    }

    @Nested
    @DisplayName("the halt after a Resume")
    inner class Halt {
        private val coldStart by lazy { block(startModule, "val navResult = navigateWithDeadline(coldStartReuse, coldStartNavigator", "} else if (coldStartNavigator != null) {") }
        private val betweenRuns by lazy { block(startModule, "val navResult = navigateWithDeadline(nextReuse, previousCareerComplete = careerFinished", "break") }

        @Test
        fun `a failed cold start that was resuming a career says a career is in flight`() {
            val failure = coldStart.substring(0, coldStart.indexOf("} else {"))
            assertTrue(failure.contains("queueHaltCareerInFlight = resumeReEntersCareer || navResult.careerResumed"))
            assertTrue(failure.indexOf("queueHaltCareerInFlight") > failure.indexOf("if (navResult.lastDetectedState != \"STOPPED\") {"))
        }

        @Test
        fun `a cold start that resumed a career runs it as a career in flight`() {
            val success = coldStart.substring(coldStart.indexOf("} else {"))
            assertTrue(success.contains("if (navResult.careerResumed) resumeReEntersCareer = true"))
            assertTrue(startModule.contains("nextRunCareerInFlight = (i == startFromRun && resumeReEntersCareer) || previousRunLeftCareer"))
        }

        @Test
        fun `a between-run Resume counts as a career left in the slot`() {
            assertTrue(betweenRuns.contains("if (navResult.careerResumed) previousRunLeftCareer = true"))
            assertTrue(betweenRuns.contains("queueHaltCareerInFlight = !careerFinished || navResult.careerResumed"))
        }
    }

    @Nested
    @DisplayName("a game that froze")
    inner class FrozenGame {
        private fun report(kind: String, key: String? = null) =
            JSONObject()
                .put("kind", kind)
                .put("queueEnabled", true)
                .put("totalRuns", 3)
                .put("runReached", 1)
                .put("resumable", true)
                .apply { if (key != null) put("reasonKey", key) }

        private val frozen by lazy {
            listOf(
                report("GAME_UNRECOVERABLE"),
                report("RUN_HALTED", "GAME_UNRECOVERABLE"),
                report("LAUNCH_FAILED_BEFORE_RUN", "GAME_UNRECOVERABLE"),
                report("NAVIGATION_FAILED_BETWEEN_RUNS", "GAME_UNRECOVERABLE"),
            ).map { queueReportText(it) }
        }

        @Test
        fun `the reason says the game froze and how to close it, since the banner shows only the reason`() {
            for (text in frozen) {
                assertTrue(text.reason.contains("froze"), text.reason)
                assertTrue(text.reason.contains("could not close it"), text.reason)
                assertTrue(text.reason.contains("Close the game fully (swipe it away in Recent apps) and open it again."), text.reason)
            }
        }

        @Test
        fun `nothing claims the game was restarted or asks only to check it`() {
            for (text in frozen) {
                val all = "${text.title} ${text.reason} ${text.nextAction}"
                assertFalse(all.contains("restart", ignoreCase = true), all)
                assertFalse(all.contains("check it"), all)
                assertTrue(text.nextAction!!.contains("press Start", ignoreCase = true), all)
            }
        }
    }
}
