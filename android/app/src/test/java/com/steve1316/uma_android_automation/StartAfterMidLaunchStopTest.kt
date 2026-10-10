package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A Stop inside a run's own launch leaves the game on a setup screen. The next Start must still pick
 * the run's trainee on Trainee Select, and a halt there must not claim a career occupies the slot.
 */
@DisplayName("Start after a stop in the middle of a launch")
class StartAfterMidLaunchStopTest {
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

    private enum class Screen { HOME, SCENARIO, TRAINEE, LEGACY, DECK, CAREER }

    /**
     * Plays a launch from [start] on a game that preselects [preselected], with the navigator's
     * Legacy Select and Support Formation rule: Back while the trainee pick is owed, else stop.
     * Returns the trainee whose career started, or null when the launch stopped.
     */
    private fun launch(start: Screen, preselected: String, target: String, backFound: Boolean = true, rosterMisses: Int = 0): String? {
        var screen = start
        var selected = preselected
        var owed = true
        var backs = 0
        var misses = rosterMisses
        repeat(40) {
            when (screen) {
                Screen.HOME -> screen = Screen.SCENARIO
                Screen.SCENARIO -> screen = Screen.TRAINEE
                Screen.TRAINEE -> {
                    if (misses > 0) {
                        misses--
                    } else {
                        selected = target
                        owed = false
                    }
                    screen = Screen.LEGACY
                }
                Screen.LEGACY, Screen.DECK -> {
                    if (owed) {
                        if (!(backToTraineeSelect(true, backs) && backFound)) return null
                        backs++
                        screen = if (screen == Screen.DECK) Screen.LEGACY else Screen.TRAINEE
                    } else {
                        screen = if (screen == Screen.LEGACY) Screen.DECK else Screen.CAREER
                    }
                }
                Screen.CAREER -> return selected
            }
        }
        throw AssertionError("the launch never ended")
    }

    @Nested
    @DisplayName("the launch")
    inner class Launch {
        @Test
        fun `a Start on any setup screen launches the run's trainee`() {
            for (start in listOf(Screen.HOME, Screen.SCENARIO, Screen.TRAINEE, Screen.LEGACY, Screen.DECK)) {
                assertEquals("Copano Rickey", launch(start, preselected = "Zenno Rob Roy", target = "Copano Rickey"), start.name)
            }
        }

        @Test
        fun `a missed Trainee Select gets one more look, and never starts the wrong trainee`() {
            assertEquals("Copano Rickey", launch(Screen.HOME, "Zenno Rob Roy", "Copano Rickey", rosterMisses = 1))
            assertNull(launch(Screen.HOME, "Zenno Rob Roy", "Copano Rickey", rosterMisses = 3))
            assertNull(launch(Screen.DECK, "Zenno Rob Roy", "Copano Rickey", rosterMisses = 1))
        }

        @Test
        fun `without a Back the launch stops instead of starting the wrong trainee`() {
            assertNull(launch(Screen.LEGACY, "Zenno Rob Roy", "Copano Rickey", backFound = false))
            assertNull(launch(Screen.DECK, "Zenno Rob Roy", "Copano Rickey", backFound = false))
        }

        @Test
        fun `the Back budget covers Support Formation to Trainee Select and no more`() {
            assertEquals(2, TRAINEE_SELECT_MAX_BACKS)
            assertTrue(backToTraineeSelect(traineeOwed = true, backsPressed = 0))
            assertTrue(backToTraineeSelect(traineeOwed = true, backsPressed = 1))
            assertFalse(backToTraineeSelect(traineeOwed = true, backsPressed = 2))
            assertFalse(backToTraineeSelect(traineeOwed = false, backsPressed = 0))
        }
    }

    @Nested
    @DisplayName("the navigator wiring")
    inner class Wiring {
        private val legacy by lazy { block(navigator, "private fun handleLegacySelectScreen(): TransitionResult {", "if (legacyAutoSelectAlreadyDone) {") }
        private val deck by lazy { block(navigator, "private fun handleSupportDeckScreen(", "if (!reuseLastLaunchSetup) {") }
        private val backOut by lazy { block(navigator, "private fun backToTraineeSelectIfOwed(screen: String): Boolean {", "private fun handleLegacySelectScreen()") }

        @Test
        fun `Legacy Select goes back to Trainee Select before its backstops stop the launch`() {
            val back = legacy.indexOf("if (backToTraineeSelectIfOwed(\"Legacy Select\")) return TransitionResult.Continue")
            assertTrue(back >= 0)
            assertTrue(back < legacy.indexOf("TransitionResult.Failed("))
            assertTrue(legacy.contains("if (rotationSwitchOwed()) {"), "the rotation backstop stays for a launch that cannot go back")
            assertTrue(legacy.contains("if (SingleRunTraineeGate.mustFailClosed(singleRunTraineeTarget, singleRunTraineeSelectHandled)) {"))
        }

        @Test
        fun `Support Formation goes back before any deck work, and a rotation switch still owed stops it`() {
            val back = deck.indexOf("if (backToTraineeSelectIfOwed(\"Support Formation\")) return TransitionResult.Continue")
            assertTrue(back >= 0)
            assertTrue(back < deck.indexOf("TransitionResult.Failed("))
            val rotation = deck.indexOf("if (rotationSwitchOwed()) {")
            assertTrue(rotation > back)
            assertTrue(deck.indexOf("TransitionResult.Failed(", rotation) > rotation)
        }

        @Test
        fun `the pick is owed for a rotation switch or an unverified single-run trainee, and only a found Back counts`() {
            assertTrue(backOut.contains("val owed = rotationSwitchOwed() || SingleRunTraineeGate.mustFailClosed(singleRunTraineeTarget, singleRunTraineeSelectHandled)"))
            val press = backOut.indexOf("if (!backToTraineeSelect(owed, traineeSelectBacks) || !ButtonBack.click(iu)) return false")
            assertTrue(press >= 0)
            assertTrue(backOut.indexOf("traineeSelectBacks++") > press)
        }

        @Test
        fun `each navigation starts with the full Back budget`() {
            val reset = block(navigator, "        legacyAutoSelectAlreadyDone = false\n", "careerLaunchInitiated = false")
            assertTrue(reset.contains("traineeSelectBacks = 0"))
        }
    }

    @Nested
    @DisplayName("the halt")
    inner class Halt {
        private fun failedOn(state: String, key: String = "") = NavigationResult(success = false, lastDetectedState = state, reasonKey = key)

        @Test
        fun `a navigation that stopped on a setup screen leaves no career in the slot`() {
            for (state in listOf("SCENARIO_SELECT", "TRAINEE_SELECT_SCREEN", "LEGACY_SELECT_SCREEN", "SUPPORT_DECK_SCREEN", "PRE_RUN_CONFIRMATION")) {
                assertFalse(navigationLeftCareer(careerExpected = true, nav = failedOn(state)), state)
            }
        }

        @Test
        fun `a navigation that stopped anywhere else keeps what the queue knew`() {
            for (state in listOf("HOME_SCREEN", "UNKNOWN", "TAP_TO_CONTINUE", "QUICK_MODE_PROMPT", "CAREER_SUMMARY", "STOPPED")) {
                assertTrue(navigationLeftCareer(careerExpected = true, nav = failedOn(state)), state)
                assertFalse(navigationLeftCareer(careerExpected = false, nav = failedOn(state)), state)
            }
        }

        @Test
        fun `both launch-failure halts read the failed screen before claiming a career`() {
            assertTrue(startModule.contains("queueHaltCareerInFlight = navigationLeftCareer(resumeReEntersCareer || navResult.careerResumed, navResult)"), "cold start")
            assertTrue(startModule.contains("queueHaltCareerInFlight = navigationLeftCareer(!careerFinished || navResult.careerResumed, navResult)"), "between runs")
            assertFalse(startModule.contains("queueHaltCareerInFlight = resumeReEntersCareer || navResult.careerResumed\n"))
        }
    }
}
