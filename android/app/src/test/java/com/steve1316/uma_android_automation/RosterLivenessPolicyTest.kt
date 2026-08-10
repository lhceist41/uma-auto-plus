package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Cold-start Trainee Select liveness hardening.
 *
 * A popup-heavy cold start misread a pre-career Skip button as CINEMATIC_INTRO, latched
 * careerLaunchInitiated, and disabled the roster/scenario detectors; the real Trainee Select then
 * fell through to POST_RUN_RESULTS and the generic Next advanced the sticky trainee (the landed
 * identity backstops then failed closed at Legacy Select). The pure [RosterLivenessPolicy] decisions
 * are pinned here; source guards prove the navigator wires them so the generic Next can no longer
 * consume the roster and a false-positive cinematic can no longer disable the detector.
 */
@DisplayName("Cold-start Trainee Select liveness")
class RosterLivenessPolicyTest {
    @Nested
    @DisplayName("rosterSelectionPending")
    inner class RosterSelectionPending {
        @Test
        fun `rotation on owes a Trainee Select`() {
            assertTrue(RosterLivenessPolicy.rosterSelectionPending(finalizeToHome = false, rotationEnabled = true, singleRunTargetArmed = false))
        }

        @Test
        fun `an armed single-run target owes a Trainee Select`() {
            assertTrue(RosterLivenessPolicy.rosterSelectionPending(finalizeToHome = false, rotationEnabled = false, singleRunTargetArmed = true))
        }

        @Test
        fun `neither rotation nor a target owes nothing (manual launch unchanged)`() {
            assertFalse(RosterLivenessPolicy.rosterSelectionPending(finalizeToHome = false, rotationEnabled = false, singleRunTargetArmed = false))
        }

        @Test
        fun `finalize-to-home never owes a Trainee Select`() {
            assertFalse(RosterLivenessPolicy.rosterSelectionPending(finalizeToHome = true, rotationEnabled = true, singleRunTargetArmed = true))
        }
    }

    @Nested
    @DisplayName("mayLatchCareerLaunch")
    inner class MayLatchCareerLaunch {
        @Test
        fun `a proxy state must NOT latch while a Trainee Select is still owed (the false-positive fix)`() {
            assertFalse(RosterLivenessPolicy.mayLatchCareerLaunch(rosterSelectionPending = true))
        }

        @Test
        fun `a proxy state may latch once the roster obligation is settled`() {
            assertTrue(RosterLivenessPolicy.mayLatchCareerLaunch(rosterSelectionPending = false))
        }
    }

    @Nested
    @DisplayName("expectationActive")
    inner class ExpectationActive {
        @Test
        fun `active only once past Home, owing a roster, and pre-launch`() {
            assertTrue(RosterLivenessPolicy.expectationActive(launchFlowEntered = true, rosterSelectionPending = true, careerLaunchInitiated = false))
        }

        @Test
        fun `inactive before Home (the between-run results Next is never suppressed)`() {
            assertFalse(RosterLivenessPolicy.expectationActive(launchFlowEntered = false, rosterSelectionPending = true, careerLaunchInitiated = false))
        }

        @Test
        fun `inactive when no roster is owed (manual and finalize launches unaffected)`() {
            assertFalse(RosterLivenessPolicy.expectationActive(launchFlowEntered = true, rosterSelectionPending = false, careerLaunchInitiated = false))
        }

        @Test
        fun `inactive once the career has started (no in-career roster misclassification)`() {
            assertFalse(RosterLivenessPolicy.expectationActive(launchFlowEntered = true, rosterSelectionPending = true, careerLaunchInitiated = true))
        }
    }

    @Nested
    @DisplayName("expectationTimedOut")
    inner class ExpectationTimedOut {
        @Test
        fun `not timed out below the budget`() {
            assertFalse(RosterLivenessPolicy.expectationTimedOut(reprobes = 9, maxReprobes = 10))
        }

        @Test
        fun `timed out at the budget (fail closed rather than tap the sticky)`() {
            assertTrue(RosterLivenessPolicy.expectationTimedOut(reprobes = 10, maxReprobes = 10))
        }
    }

    @Nested
    @DisplayName("navigator wiring (source guard)")
    inner class NavigatorWiring {
        private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `rosterSelectionPending is derived through the policy and reset per navigate`() {
            assertTrue(nav.contains("RosterLivenessPolicy.rosterSelectionPending(finalizeToHome"), "init uses the policy and passes finalizeToHome")
            assertTrue(nav.contains("launchFlowEntered = false"), "launchFlowEntered resets per navigate (no leak across runs)")
            assertTrue(nav.contains("rosterExpectationReprobes = 0"), "the re-probe counter resets per navigate")
        }

        @Test
        fun `the careerLaunchInitiated latch is gated on the policy so a pre-verification cinematic cannot disable the detector`() {
            val latch = nav.indexOf("careerLaunchInitiated = true")
            assertTrue(latch >= 0)
            val gate = nav.lastIndexOf("RosterLivenessPolicy.mayLatchCareerLaunch(rosterSelectionPending)", latch)
            assertTrue(gate in (latch - 400) until latch, "the latch block is guarded by mayLatchCareerLaunch")
        }

        @Test
        fun `launchFlowEntered arms on the HOME_SCREEN detection`() {
            val home = nav.indexOf("if (detectedState == LaunchScreenState.HOME_SCREEN) {")
            assertTrue(home >= 0)
            val arm = nav.indexOf("launchFlowEntered = true", home)
            assertTrue(arm in home until (home + 120), "HOME_SCREEN detection sets launchFlowEntered")
        }

        @Test
        fun `handlePostRunResults consults the expectation before any generic Next tap`() {
            val handler = nav.indexOf("private fun handlePostRunResults(")
            assertTrue(handler >= 0)
            val expect = nav.indexOf("rosterLivenessExpectation()?.let { return it }", handler)
            val nextClick = nav.indexOf("ButtonNext.click(iu, sourceBitmap = bitmap)", handler)
            assertTrue(expect in handler until nextClick, "the expectation runs before the generic Next cascade")
        }

        @Test
        fun `the expectation suppresses Next and routes fresh detections to the real handlers`() {
            val expect = nav.indexOf("private fun rosterLivenessExpectation(")
            assertTrue(expect >= 0)
            val body = nav.substring(expect, nav.indexOf("private fun handlePostRunResults(", expect))
            assertTrue(body.contains("RosterLivenessPolicy.expectationActive("), "the active check uses the policy")
            assertTrue(body.contains("waitSafe(ROSTER_EXPECT_SETTLE_SECONDS)"), "it settles before recapturing")
            assertTrue(body.contains("iu.getSourceBitmap()"), "it recaptures a fresh frame")
            assertTrue(body.contains("return handleTraineeSelectScreen()"), "a recognized roster routes to the verified-target handler")
            assertTrue(body.contains("return handleScenarioSelect()"), "a recognized scenario routes to its handler")
        }

        @Test
        fun `the expectation fails closed on timeout and never clicks Start Career`() {
            val expect = nav.indexOf("private fun rosterLivenessExpectation(")
            val body = nav.substring(expect, nav.indexOf("private fun handlePostRunResults(", expect))
            assertTrue(body.contains("RosterLivenessPolicy.expectationTimedOut("), "the timeout uses the policy")
            assertTrue(body.contains("TransitionResult.Failed("), "timeout fails closed")
            assertFalse(body.contains("Start Career"), "the expectation never taps Start Career")
        }

        @Test
        fun `a verified roster clears the expectation for both single-run and rotation`() {
            val mark = nav.indexOf("private fun markSingleRunTraineeVerified()")
            assertTrue(mark >= 0)
            val body = nav.substring(mark, nav.indexOf("\n    }\n", mark) + 1)
            assertTrue(body.contains("rosterSelectionPending = false"), "the verified-advance points clear the roster obligation")
        }
    }

    @Nested
    @DisplayName("identity + detector non-regression (source guard)")
    inner class NonRegression {
        private val nav by lazy { sourceFile("CareerLaunchNavigator.kt").readText().replace("\r\n", "\n") }

        @Test
        fun `the three Start-Career identity backstops remain present`() {
            assertEquals(
                3,
                Regex("SingleRunTraineeGate\\.mustFailClosed\\(").findAll(nav).count(),
                "Legacy Select, Support Deck, and Pre-Run Confirmation still fail closed on unverified identity",
            )
        }

        @Test
        fun `isTraineeSelectScreen still rejects the in-career details, event, and dialog screens`() {
            val det = nav.indexOf("private fun isTraineeSelectScreen(")
            assertTrue(det >= 0)
            val body = nav.substring(det, nav.indexOf("\n    }\n", det) + 1)
            assertTrue(body.contains("DETAIL"), "the Umamusume Details reject is intact")
            assertTrue(body.contains("EVENT"), "the trainee-event banner reject is intact")
            assertTrue(body.contains("DialogUtils.check"), "the dialog-gradient reject is intact")
        }

        @Test
        fun `the landed careerLaunchInitiated protection is preserved (still latched, just gated)`() {
            assertTrue(nav.contains("careerLaunchInitiated = true"), "the latch still exists")
            assertTrue(nav.contains("!careerLaunchInitiated"), "detectors still gate on it")
        }
    }

    private fun sourceFile(relative: String): File = File(kotlinRoot(), relative).also { require(it.isFile) { "missing ${it.path}" } }

    private fun kotlinRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        repeat(5) {
            val a = File(dir, "src/main/java/com/steve1316/uma_android_automation")
            if (a.isDirectory) return a
            val b = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation")
            if (b.isDirectory) return b
            dir = dir?.parentFile
        }
        throw IllegalStateException("could not locate the Kotlin source root from ${System.getProperty("user.dir")}")
    }
}
