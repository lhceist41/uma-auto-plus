package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Scenario Select pages a cyclic carousel one card per chevron tap, checking the target's logo before each tap. The bound must reach a
 * target on the far side of the cycle even when a game update added cards the bot has no logo for yet, with one tap that did not page.
 */
@DisplayName("Scenario Select carousel bound")
class ScenarioCarouselBoundTest {
    private val knownCards = 4

    /** Whether [checks] logo checks find a target [distance] taps away, when [stalledTaps] of the taps do not page. */
    private fun reaches(checks: Int, distance: Int, stalledTaps: Int): Boolean = distance + stalledTaps + 1 <= checks

    @Test
    fun `four logos give seven checks`() {
        assertEquals(7, scenarioCarouselPageChecks(knownCards))
    }

    @Test
    fun `a full cycle with up to two new cards is reached, even with one tap that did not page`() {
        val checks = scenarioCarouselPageChecks(knownCards)
        for (cards in knownCards..knownCards + 2) {
            for (distance in 0 until cards) {
                assertTrue(reaches(checks, distance, stalledTaps = 1), "$cards cards, $distance taps away")
            }
        }
        assertFalse(reaches(checks, knownCards + 2, stalledTaps = 1), "a third new card on the far side is past the bound")
    }

    @Test
    fun `the paging loop uses the bound and a miss stops with its own key`() {
        val navigator = source("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt")
        val body = navigator.substring(navigator.indexOf("private fun handleScenarioSelect(): TransitionResult {"), navigator.indexOf("// Utilities"))
        assertTrue(body.contains("val pageChecks = scenarioCarouselPageChecks(carouselLogos.size)\n        repeat(pageChecks) { attempt ->"))
        assertTrue(body.contains("(page check \${attempt + 1}/\$pageChecks)"))
        assertFalse(body.contains("repeat(6)"))
        assertTrue(body.contains("reasonKey = \"SCENARIO_NOT_FOUND\","))
        assertTrue("SCENARIO_NOT_FOUND" in REPORT_REASON_KEYS, "the stop has its own words for the player")
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
