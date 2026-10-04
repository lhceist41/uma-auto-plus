package com.steve1316.uma_android_automation

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
 * screen is checked first. Only another scenario's button on two fresh captures stops it; a missed or ambiguous read plays on.
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
        fun `no scenario button plays on, so a URA career and a missed template never stop`() {
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
            val check = body.indexOf("if (!game.careerLaunched) checkReEnteredCareerScenario()")
            assertTrue(check > body.indexOf("if (!checkMainScreen()) {"), "on the career screen")
            assertTrue(check < body.indexOf("onBeforeMainScreenUpdate()"), "before the scenario's own hook can act")
            assertTrue(body.substring(0, check).contains("if (!bCareerScenarioChecked) {\n            bCareerScenarioChecked = true"), "once per run")
        }

        @Test
        fun `a proven mismatch stops the queue with its own key, before any tap`() {
            val body = campaign.substring(campaign.indexOf("private fun checkReEnteredCareerScenario() {"), campaign.indexOf("private fun stopOnSlotScenarioMismatch("))
            assertTrue(body.contains("StartModule.queueStopKey = \"CAREER_SCENARIO_MISMATCH\""))
            assertTrue(body.contains("StartModule.queueStopRequested = true"))
            assertTrue(body.contains("throw InterruptedException(reason)"))
            assertFalse(Regex("\\.tap|click\\(", RegexOption.IGNORE_CASE).containsMatchIn(body), "no tap or click")
            assertTrue("CAREER_SCENARIO_MISMATCH" in REPORT_REASON_KEYS, "the stop has its own words for the player")
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
