package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.MAX_INCONCLUSIVE_SCENARIO_CHECKS
import com.steve1316.uma_android_automation.bot.STEADY_SCENARIO_BUTTONS
import com.steve1316.uma_android_automation.bot.ScenarioCheckStep
import com.steve1316.uma_android_automation.bot.careerScenarioUnproven
import com.steve1316.uma_android_automation.bot.scenarioCheckStep
import com.steve1316.uma_android_automation.bot.foreignCareerScenario
import com.steve1316.uma_android_automation.bot.scenarioButtonConfidence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A career the run did not start (a resume, a retry, a Start on a career in progress) is played by the campaign its settings name, so its
 * screen is checked first. Another scenario's button on two fresh captures stops it, and so does a Grand Concert career screen without
 * its Races button, the one button that shows on every turn. Any other miss or ambiguous read plays on.
 */
@DisplayName("Scenario check on a career the run did not start")
class ResumedCareerScenarioTest {
    private val gc = "Grand Concert"
    private val tb = "Trackblazer"
    private val uc = "Unity Cup"
    private val ura = "URA Finale"

    @Nested
    @DisplayName("decision")
    inner class Decision {
        @Test
        fun `another scenario's button on both captures stops, for every configured scenario`() {
            for (configured in listOf(gc, tb, uc, ura)) {
                for (seen in listOf(gc, tb, uc).filter { it != configured }) {
                    assertEquals(seen, foreignCareerScenario(configured, setOf(seen), setOf(seen)), "$configured career showing $seen")
                }
            }
        }

        @Test
        fun `the configured scenario's own button plays on`() {
            for (configured in listOf(gc, tb, uc)) {
                assertNull(foreignCareerScenario(configured, setOf(configured), emptySet()))
            }
        }

        @Test
        fun `a foreign button on one capture only plays on`() {
            assertNull(foreignCareerScenario(ura, setOf(gc), emptySet()))
            assertNull(foreignCareerScenario(ura, emptySet(), setOf(gc)))
            assertNull(foreignCareerScenario(ura, setOf(gc), setOf(tb)))
        }

        @Test
        fun `no scenario button proves no other known scenario`() {
            for (configured in listOf(gc, tb, uc, ura)) {
                assertNull(foreignCareerScenario(configured, emptySet(), emptySet()))
            }
        }

        @Test
        fun `the configured button next to a foreign one plays on`() {
            assertNull(foreignCareerScenario(gc, setOf(gc, tb), setOf(gc, tb)))
            assertNull(foreignCareerScenario(gc, setOf(tb), setOf(gc, tb)))
        }

        @Test
        fun `two foreign buttons at once are ambiguous and play on`() {
            assertNull(foreignCareerScenario(ura, setOf(gc, tb), setOf(gc, tb)))
        }
    }

    @Nested
    @DisplayName("unproven career")
    inner class Unproven {
        @Test
        fun `a Grand Concert career screen without any scenario button on both captures is unproven`() {
            assertTrue(careerScenarioUnproven(gc, emptySet(), emptySet()))
        }

        @Test
        fun `its Races button on either capture proves it`() {
            assertFalse(careerScenarioUnproven(gc, setOf(gc), emptySet()))
            assertFalse(careerScenarioUnproven(gc, emptySet(), setOf(gc)))
        }

        @Test
        fun `scenarios without a button on every turn still play on with no button`() {
            for (configured in listOf(tb, uc, ura)) {
                assertFalse(careerScenarioUnproven(configured, emptySet(), emptySet()), configured)
            }
        }

        @Test
        fun `another scenario's button on any capture leaves the decision to the mismatch check`() {
            assertFalse(careerScenarioUnproven(gc, setOf(tb), emptySet()))
            assertFalse(careerScenarioUnproven(gc, emptySet(), setOf(uc)))
            assertFalse(careerScenarioUnproven(gc, setOf(tb), setOf(tb)))
            assertEquals(tb, foreignCareerScenario(gc, setOf(tb), setOf(tb)))
        }

        @Test
        fun `only Grand Concert counts as showing its button on every turn`() {
            // Unity Cup's race button is a race-turn button, URA Finale has none, and no Trackblazer career-screen fixture proves its Shop button.
            assertEquals(setOf(gc), STEADY_SCENARIO_BUTTONS.keys)
        }
    }

    @Nested
    @DisplayName("inconclusive recheck")
    inner class Inconclusive {
        /** The steps a run of passes takes, counting as the campaign does: a retry adds one, every other step resets to zero. */
        private fun run(passes: List<Pair<Boolean, Boolean>>): List<ScenarioCheckStep> {
            var count = 0
            return passes.map { (unproven, held) ->
                scenarioCheckStep(unproven, held, count).also { count = if (it == ScenarioCheckStep.RETRY) count + 1 else 0 }
            }
        }

        private val lost = true to false
        private val held = true to true
        private val proven = false to false

        @Test
        fun `an unproven pass whose career screen holds stops at once`() {
            assertEquals(ScenarioCheckStep.STOP, scenarioCheckStep(unproven = true, screenHeld = true, inconclusiveBefore = 0))
        }

        @Test
        fun `a lost career screen retries, and the third in a row fails closed`() {
            assertEquals(3, MAX_INCONCLUSIVE_SCENARIO_CHECKS)
            assertEquals(listOf(ScenarioCheckStep.RETRY, ScenarioCheckStep.RETRY, ScenarioCheckStep.STOP), run(listOf(lost, lost, lost)))
        }

        @Test
        fun `a held screen after one or two lost ones stops`() {
            assertEquals(listOf(ScenarioCheckStep.RETRY, ScenarioCheckStep.STOP), run(listOf(lost, held)))
            assertEquals(listOf(ScenarioCheckStep.RETRY, ScenarioCheckStep.RETRY, ScenarioCheckStep.STOP), run(listOf(lost, lost, held)))
        }

        @Test
        fun `a proven pass after one or two lost ones plays and resets the count`() {
            assertEquals(listOf(ScenarioCheckStep.RETRY, ScenarioCheckStep.PLAY), run(listOf(lost, proven)))
            assertEquals(
                listOf(ScenarioCheckStep.RETRY, ScenarioCheckStep.RETRY, ScenarioCheckStep.PLAY, ScenarioCheckStep.RETRY, ScenarioCheckStep.RETRY, ScenarioCheckStep.STOP),
                run(listOf(lost, lost, proven, lost, lost, lost)),
            )
        }

        @Test
        fun `a proven pass never stops, whatever came before`() {
            for (before in 0..5) assertEquals(ScenarioCheckStep.PLAY, scenarioCheckStep(unproven = false, screenHeld = false, inconclusiveBefore = before))
        }
    }

    @Nested
    @DisplayName("match bar")
    inner class MatchBar {
        // What the Grand Concert Races template scores on the stock Races button of every other scenario's career screen.
        private val gcTemplateOnStockRaces = 0.704

        @Test
        fun `a lowered template confidence never lets the stock Races button read as Grand Concert`() {
            for (player in listOf(0.5, 0.6, 0.7, 0.704, 0.75)) {
                assertTrue(gcTemplateOnStockRaces < scenarioButtonConfidence(player), "slider at $player")
            }
        }

        @Test
        fun `the bar is 0_8 at most player settings and follows a stricter one`() {
            assertEquals(0.8, scenarioButtonConfidence(0.5))
            assertEquals(0.8, scenarioButtonConfidence(0.8))
            assertEquals(0.9, scenarioButtonConfidence(0.9))
        }

        @Test
        fun `every scenario button is checked against that bar`() {
            val main = "android/app/src/main/java/com/steve1316/uma_android_automation"
            val body = source("$main/bot/Campaign.kt").let { it.substring(it.indexOf("private fun scenariosOnScreen()"), it.indexOf("private fun checkReEnteredCareerScenario()")) }
            assertTrue(body.contains("val confidence = scenarioButtonConfidence(game.imageUtils.confidence)"))
            assertTrue(body.contains("it.check(game.imageUtils, sourceBitmap = bitmap, confidence = confidence)"))
        }
    }

    @Nested
    @DisplayName("wiring")
    inner class Wiring {
        private val main = "android/app/src/main/java/com/steve1316/uma_android_automation"
        private val campaign by lazy { source("$main/bot/Campaign.kt") }
        private val game by lazy { source("$main/bot/Game.kt") }
        private val startModule by lazy { source("$main/StartModule.kt") }
        private val navigator by lazy { source("$main/CareerLaunchNavigator.kt") }

        @Test
        fun `the check runs once, at the first career screen, before any scenario hook and only for a career the run did not start`() {
            val body = campaign.substring(campaign.indexOf("open fun handleMainScreen(): Boolean {"))
            val check = body.indexOf("if (!game.careerLaunched && !checkReEnteredCareerScenario()) {")
            assertTrue(check > body.indexOf("if (!checkMainScreen()) {"), "on the career screen")
            assertTrue(check < body.indexOf("onBeforeMainScreenUpdate()"), "before the scenario's own hook can act")
            assertTrue(body.substring(0, check).contains("if (!bCareerScenarioChecked) {\n            bCareerScenarioChecked = true"), "once per run")
        }

        @Test
        fun `an inconclusive check plays no turn and runs again on the next main-screen pass`() {
            val body = campaign.substring(campaign.indexOf("open fun handleMainScreen(): Boolean {"))
            val check = body.indexOf("if (!game.careerLaunched && !checkReEnteredCareerScenario()) {")
            assertTrue(
                body.substring(check).startsWith(
                    "if (!game.careerLaunched && !checkReEnteredCareerScenario()) {\n                bCareerScenarioChecked = false\n                return false\n            }",
                ),
                "the flag is cleared and the pass returns before anything else",
            )
            assertTrue(body.indexOf("return false", check) < body.indexOf("onBeforeMainScreenUpdate()"), "before any turn work")
        }

        @Test
        fun `the inconclusive count rises only on a retry and resets on every other outcome`() {
            val body = campaign.substring(campaign.indexOf("private fun checkReEnteredCareerScenario(): Boolean {"), campaign.indexOf("private fun stopOnSlotScenarioMismatch("))
            val step = body.indexOf("val step = scenarioCheckStep(unproven, unproven && checkMainScreen(), inconclusiveScenarioChecks)")
            val retry = body.indexOf("if (step == ScenarioCheckStep.RETRY) {\n            inconclusiveScenarioChecks++")
            val retryReturn = body.indexOf("return false", retry)
            val reset = body.indexOf("inconclusiveScenarioChecks = 0")
            assertTrue(step in 0 until retry, "decided once per pass")
            assertTrue(retryReturn in retry until reset, "a retry leaves before the reset")
            assertTrue(reset < body.indexOf("if (step == ScenarioCheckStep.STOP) {"), "a stop or a play resets it")
            assertEquals(1, Regex("inconclusiveScenarioChecks\\+\\+").findAll(campaign).count(), "counted in one place")
        }

        @Test
        fun `a proven mismatch stops the queue with its own key, before any tap`() {
            val body = campaign.substring(campaign.indexOf("private fun checkReEnteredCareerScenario(): Boolean {"), campaign.indexOf("private fun stopOnSlotScenarioMismatch("))
            assertTrue(body.contains("StartModule.queueStopKey = \"CAREER_SCENARIO_MISMATCH\""))
            assertTrue(body.contains("StartModule.queueStopRequested = true"))
            assertTrue(body.contains("throw InterruptedException(reason)"))
            assertFalse(Regex("\\.tap|click\\(", RegexOption.IGNORE_CASE).containsMatchIn(body), "no tap or click")
            assertTrue("CAREER_SCENARIO_MISMATCH" in REPORT_REASON_KEYS, "the stop has its own words for the player")
        }

        @Test
        fun `an unproven career stops with its own key on a second look at a career screen that still shows, before any tap`() {
            val body = campaign.substring(campaign.indexOf("private fun checkReEnteredCareerScenario(): Boolean {"), campaign.indexOf("private fun stopOnSlotScenarioMismatch("))
            assertTrue(
                body.contains("if (game.scenario in first || (first.isEmpty() && game.scenario !in STEADY_SCENARIO_BUTTONS)) {"),
                "a Grand Concert screen with no button gets its second look",
            )
            val unproven = body.indexOf("if (step == ScenarioCheckStep.STOP) {")
            assertTrue(body.indexOf("val unproven = careerScenarioUnproven(game.scenario, first, second)") > body.indexOf("scenariosOnScreen()\n            }"), "after both looks")
            assertTrue(unproven < body.indexOf("val foreign = foreignCareerScenario("), "before the play-on path")
            val stop = body.substring(unproven, body.indexOf("val foreign = foreignCareerScenario("))
            assertTrue(stop.contains("StartModule.queueStopKey = \"CAREER_SCENARIO_UNPROVEN\""))
            assertTrue(stop.contains("StartModule.queueStopRequested = true"))
            assertTrue(stop.contains("throw InterruptedException(reason)"))
            assertFalse(Regex("\\.tap|click\\(", RegexOption.IGNORE_CASE).containsMatchIn(body), "no tap or click")
            assertTrue("CAREER_SCENARIO_UNPROVEN" in REPORT_REASON_KEYS, "the stop has its own words for the player")
        }

        @Test
        fun `URA Finale has no button, so only the three others can be proven`() {
            val map = campaign.substring(campaign.indexOf("private val scenarioButtons"), campaign.indexOf("private fun scenariosOnScreen()"))
            assertTrue(map.contains("GrandConcertScenario.KEY to ButtonRacesGrandConcert"))
            assertTrue(map.contains("\"Trackblazer\" to ButtonShopTrackblazer"))
            assertTrue(map.contains("\"Unity Cup\" to ButtonUnityCupRace"))
            assertFalse(map.contains("URA"))
        }

        @Test
        fun `only a navigation that started a career marks the run's career as launched`() {
            assertTrue(navigator.contains("return NavigationResult(success = true, lastDetectedState = currentState.name, careerLaunched = careerLaunchInitiated)"))
            assertTrue(
                navigator.contains("return NavigationResult(success = true, lastDetectedState = detectedState.name, careerResumed = true)"),
                "a resume never claims a launch",
            )
            assertTrue(startModule.contains("nextRunCareerLaunched = navResult.careerLaunched"), "the cold-start launch")
            assertTrue(startModule.contains("nextRunCareerLaunched = navResult.success && navResult.careerLaunched"), "the launch between runs")
            assertTrue(startModule.contains("val careerLaunched = nextRunCareerLaunched.also { nextRunCareerLaunched = false }"), "one run consumes it")
            assertTrue(startModule.contains("Game(context, selection, careerInFlight, careerLaunched)"))
            assertTrue(game.contains("if (navResult.careerLaunched) careerLaunched = true"), "the run's own launch")
        }

        @Test
        fun `a retry and a new session never inherit a launch`() {
            val retry = startModule.substring(startModule.indexOf("if (retried) {"), startModule.indexOf("if (isOverlayStop("))
            assertFalse(retry.contains("nextRunCareerLaunched"), "the retry's second Game finds the flag already consumed")
            val reset = startModule.substring(startModule.indexOf("rotationResyncPrevIndex = -1\n                queueCurrentRun = 1"))
            assertTrue(reset.substring(0, 200).contains("nextRunCareerLaunched = false"))
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
