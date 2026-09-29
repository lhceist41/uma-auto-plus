package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.Game
import com.steve1316.uma_android_automation.utils.isLaunchQuickModePrompt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A Start or resume while a career sits on the Training selection screen presses the game's Back once
 * and continues the career, and a run that re-enters a career in flight never takes the launch Quick
 * Mode pill taps. The back-out is a pure step played here against fake screens; its wiring in
 * Game.start, the navigator and StartModule is pinned by source guards, since those need a live game.
 */
@DisplayName("Continuing a career left on the Training selection screen")
class ResumeTrainingSelectionTest {
    /** Plays [Game.backOutOfTrainingSelection] against a scripted screen; returns (result, backs, settles). */
    private fun backOut(screens: MutableList<String>, backLands: Boolean = true): Triple<Boolean, Int, Int> {
        var backs = 0
        var settles = 0
        val result =
            Game.backOutOfTrainingSelection(
                onTrainingSelection = { screens.first() == "training selection" },
                pressBack = {
                    backs++
                    if (backLands) screens.removeAt(0)
                    backLands
                },
                settle = { settles++ },
                onTrainingMenu = { screens.first() == "training menu" },
            )
        return Triple(result, backs, settles)
    }

    @Nested
    @DisplayName("the back-out")
    inner class BackOut {
        @Test
        fun `one Back on the Training selection screen returns to the training menu`() {
            assertEquals(Triple(true, 1, 1), backOut(mutableListOf("training selection", "training menu")))
        }

        @Test
        fun `nothing is pressed on any other screen, the training menu included`() {
            for (screen in listOf("training menu", "cutscene", "race list", "Home", "concert pending")) {
                assertEquals(Triple(false, 0, 0), backOut(mutableListOf(screen, "training menu")), screen)
            }
        }

        @Test
        fun `a Back that lands elsewhere or is not found is reported, and never repeated`() {
            assertEquals(Triple(false, 1, 1), backOut(mutableListOf("training selection", "cutscene")))
            assertEquals(Triple(false, 1, 0), backOut(mutableListOf("training selection", "training menu"), backLands = false))
        }
    }

    @Nested
    @DisplayName("the launch Quick Mode prompt")
    inner class QuickMode {
        @Test
        fun `a career in flight never reads a Skip pill as the prompt, and a fresh launch still does`() {
            assertFalse(isLaunchQuickModePrompt(resumingInProgressCareer = true, skipToggleAlreadyDone = false))
            assertTrue(isLaunchQuickModePrompt(resumingInProgressCareer = false, skipToggleAlreadyDone = false))
        }
    }

    @Nested
    @DisplayName("wiring (source guards)")
    inner class Wiring {
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

        private val game by lazy { source("bot/Game.kt") }
        private val navigator by lazy { source("CareerLaunchNavigator.kt") }
        private val startModule by lazy { source("StartModule.kt") }

        private fun count(src: String, literal: String) = Regex(Regex.escape(literal)).findAll(src).count()

        private fun body(src: String, signature: String): String {
            val start = src.indexOf(signature)
            assertTrue(start >= 0, "$signature not found")
            val end = Regex("\n    (/\\*\\*|// |(private |internal )?fun )").find(src, start + signature.length)?.range?.first ?: src.length
            return src.substring(start, end)
        }

        @Test
        fun `Game start backs out once, before the navigator, and only on the identified screen`() {
            val start = body(game, "fun start(): TaskResult")
            val decide = start.indexOf("val onTrainingSelection = !isMiscTask && isOnTrainingSelection()")
            val backOut = start.indexOf("if (onTrainingSelection && backOutOfTrainingSelection(::isOnTrainingSelection, { ButtonBack.click(imageUtils) }, { wait(1.0) }, ::isOnTrainingMenu)) {")
            val navigation = start.indexOf("if (!isMiscTask && !isOnTrainingMenu()) {")
            assertTrue(decide > start.indexOf("runDiagnostic()?.let { return it }"))
            assertTrue(backOut in decide until navigation)
            assertEquals(1, count(game, "ButtonBack.click("), "no other Back in Game")
            assertEquals(2, count(game, "backOutOfTrainingSelection("), "its definition and one call")
            assertTrue(game.contains("val bitmap = imageUtils.getSourceBitmap()\n        return TrainingSelectionProbe.isTrainingSelection("))
        }

        @Test
        fun `a failed back-out still keeps the navigator off the pill`() {
            assertTrue(game.contains("careerInFlight = careerInFlight || onTrainingSelection,"))
            assertTrue(game.contains("val careerInFlight: Boolean = false) {"), "every other Game keeps the launch routing")
        }

        @Test
        fun `the navigator takes the flag only for the pill decision`() {
            assertTrue(navigator.contains("careerInFlight: Boolean = false,\n    ): NavigationResult {"))
            assertEquals(1, count(navigator, "careerInFlightMode = "), "set once per navigate()")
            assertTrue(navigator.contains("careerInFlightMode = careerInFlight\n"))
            assertEquals(6, count(navigator, "careerInFlightMode"), "declaration, its mention in the navigate() KDoc, assignment, the pill decision and its log reason, and the event-choices handoff only")
            assertTrue(body(navigator, "private fun handleTapToContinue(").contains("careerInFlightMode || careerResumed) && IconTrainingEventHorseshoe.check("))
            assertTrue(navigator.contains("isLaunchQuickModePrompt(resumeInProgressCareerMode || careerInFlightMode, skipToggleAlreadyDone)"))
            // A launch that starts over from the title screen keeps it.
            assertTrue(body(navigator, "private fun startLaunchOver(").contains("resumeInProgressCareer, coldStartOnHome, careerInFlight)"))
        }

        @Test
        fun `the navigator recognises the screen before the pill and presses Back at most once per navigation`() {
            val detect = body(navigator, "private fun detectScreenState(")
            val training = detect.indexOf("if (isTrainingSelection(bitmap)) {\n            return LaunchScreenState.TRAINING_SELECTION_SCREEN")
            assertTrue(training in detect.indexOf("grandConcertConcertPendingScreenPresent(") until detect.indexOf("val skipState ="))
            val handler = body(navigator, "private fun handleTrainingSelectionScreen(")
            assertEquals(1, count(handler, "ButtonBack.click(iu)"))
            assertFalse(Regex("CoordinateTap|\\btap\\(|skip_toggle|ButtonSkip").containsMatchIn(handler), "no pill and no body tap")
            assertTrue(handler.indexOf("if (trainingSelectionBackPressed) {\n            return TransitionResult.Failed(") in 0 until handler.indexOf("ButtonBack.click(iu)"))
            assertTrue(handler.contains("if (ButtonBack.click(iu)) {\n            trainingSelectionBackPressed = true"))
            val navigate = body(navigator, "fun navigate(")
            assertTrue(navigate.indexOf("trainingSelectionBackPressed = false") in 0 until navigate.indexOf("for (attempt in 0 until MAX_DETECTION_ATTEMPTS)"))
            assertTrue(navigator.contains("LaunchScreenState.TRAINING_SELECTION_SCREEN -> handleTrainingSelectionScreen()"))
        }

        @Test
        fun `the queue marks exactly the resumed in-flight run, a run played again and a run after an unfinished career`() {
            // Read once by the next run and cleared, so no later launch can inherit it.
            val mark = "nextRunCareerInFlight = (i == startFromRun && resumeReEntersCareer) || previousRunLeftCareer\n"
            assertTrue(startModule.contains(mark + "                    previousRunLeftCareer = false\n                    var result = runSingleGame()"))
            // An unfinished career is the one the loop already treats as left in the slot.
            val careerFinished = startModule.indexOf("val careerFinished = effectiveResult.code == TaskResultCode.TASK_RESULT_COMPLETE\n")
            assertTrue(startModule.indexOf("previousRunLeftCareer = !careerFinished\n") in careerFinished until startModule.indexOf("navigateWithDeadline(nextReuse,"))
            assertEquals(4, count(startModule, "previousRunLeftCareer = "), "its false start, the clear on reading, the per-run update and a between-run Resume only")
            assertTrue(startModule.contains("if (navResult.careerResumed) previousRunLeftCareer = true"))
            assertTrue(startModule.contains("nextRunCareerInFlight = true\n                        result = runSingleGame()"))
            assertEquals(1, count(startModule, "var result = runSingleGame()"))
            val run = body(startModule, "private fun runSingleGame(")
            assertTrue(run.contains("val careerInFlight = nextRunCareerInFlight.also { nextRunCareerInFlight = false }"), "consumed by one run only")
            assertTrue(run.contains("val entryPoint = Game(context, selection, careerInFlight)"))
            assertEquals(5, count(startModule, "nextRunCareerInFlight"), "declaration, the two marks and the consume (read and clear)")
        }

        @Test
        fun `the resumed cold start and the pass after an unfinished career carry the flag, and only to the pill decision`() {
            assertTrue(startModule.contains("navigateWithDeadline(coldStartReuse, coldStartNavigator, coldStartOnHome = !resumeReEntersCareer, careerInFlight = resumeReEntersCareer)"))
            assertTrue(startModule.contains("navigateWithDeadline(nextReuse, previousCareerComplete = careerFinished, careerInFlight = !careerFinished)"))
            assertTrue(startModule.contains("coldStartOnHome = coldStartOnHome, careerInFlight = careerInFlight)"), "navigateWithDeadline passes it on")
            // The no-career recovery, the relaunch and the title probe never read it.
            val recovery = "BetweenRunRecovery(coldStartOnHome, previousCareerComplete, finalizeToHome, campaignOwnsCareer = resumeInProgressCareer || liveGameAttached)"
            assertTrue(navigator.contains("betweenRunRecovery = $recovery"))
            assertFalse(Regex("careerInFlight(Mode)?\\b").containsMatchIn(source("BetweenRunRecovery.kt")))
        }
    }
}
