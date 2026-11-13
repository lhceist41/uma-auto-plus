package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Scenario Select taps the carousel chevron only on a frame that still shows the screen's header. On a busy host the game left the
 * screen several seconds after Next, every later frame matched no logo, and the old loop kept tapping the chevron spot on Trainee and
 * Legacy Select until its bound ran out.
 */
@DisplayName("Scenario Select carousel step")
class ScenarioCarouselStepTest {
    @Test
    fun `a frame without the header detects the screen again, whatever logo it shows`() {
        for (target in listOf(true, false)) {
            for (known in listOf(true, false)) {
                for (settled in listOf(true, false)) {
                    assertEquals(ScenarioCarouselStep.REDETECT, scenarioCarouselStep(false, target, known, settled), "target=$target known=$known settled=$settled")
                }
            }
        }
    }

    @Test
    fun `the target card advances`() {
        assertEquals(ScenarioCarouselStep.ADVANCE, scenarioCarouselStep(true, true, false, false))
        assertEquals(ScenarioCarouselStep.ADVANCE, scenarioCarouselStep(true, true, false, true))
    }

    @Test
    fun `a first read with no known logo waits instead of paging`() {
        assertEquals(ScenarioCarouselStep.WAIT, scenarioCarouselStep(true, false, false, false))
    }

    @Test
    fun `a settled read with no known logo pages past a card the bot has no logo for`() {
        assertEquals(ScenarioCarouselStep.PAGE, scenarioCarouselStep(true, false, false, true))
    }

    @Test
    fun `another known card pages at once`() {
        assertEquals(ScenarioCarouselStep.PAGE, scenarioCarouselStep(true, false, true, false))
        assertEquals(ScenarioCarouselStep.PAGE, scenarioCarouselStep(true, false, true, true))
    }

    @Test
    fun `the chevron tap and Next come only after the step read the header on the same frame`() {
        val navigator = source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
        val body = navigator.substring(navigator.indexOf("private fun handleScenarioSelect(): TransitionResult {"), navigator.indexOf("// Utilities"))
        val header = body.indexOf("LabelScenarioSelectHeader.check(iu, sourceBitmap = bitmap)")
        val redetect = body.indexOf("if (step == ScenarioCarouselStep.REDETECT) {")
        val advance = body.indexOf("if (step == ScenarioCarouselStep.ADVANCE) {")
        val chevron = body.indexOf("\"scenario_carousel_next\")")
        assertTrue(header in 0 until redetect, "the header is read before the step is acted on")
        assertTrue(redetect < advance && advance < chevron, "a frame without the header returns before Next or the chevron")
        assertTrue(body.substring(redetect, advance).contains("return TransitionResult.Continue"))
        assertEquals(1, Regex("CoordinateTap\\.tap\\(gestureUtils, bitmap\\.width \\* 0\\.949").findAll(body).count(), "one chevron tap")
    }

    @Test
    fun `after Next the handler waits for the header to go before handing back`() {
        val navigator = source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
        val body = navigator.substring(navigator.indexOf("private fun handleScenarioSelect(): TransitionResult {"), navigator.indexOf("// Utilities"))
        assertEquals(2, Regex("awaitScenarioSelectLeft\\(\\)\\n\\s+return TransitionResult\\.Continue").findAll(body).count(), "both Next paths wait")
        val wait = body.substring(body.indexOf("private fun awaitScenarioSelectLeft() {"))
        assertTrue(wait.contains("SCENARIO_SELECT_LEAVE_WAIT_MS"))
        assertTrue(wait.contains("if (!LabelScenarioSelectHeader.check(iu, sourceBitmap = iu.getSourceBitmap())) return"))
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
