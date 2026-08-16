package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source guards for the Grand Concert facts-based fan-requirement activation. They pin the
 * architectural boundaries that keep the change safe: the review-gated deferral seam stays null,
 * the deadline OCR standdown stays, the fan emergency is never fed from facts, activation is neither
 * a second mutable flag nor a second writer, the selector and scan planner are untouched, and the
 * facts value reaches `hasFanRequirement` through the single requirement-refresh writer.
 */
@DisplayName("Grand Concert fan-activation architecture guards")
class GrandConcertFanActivationGuardTest {
    private fun source(relative: String): String = File(kotlinRoot(), relative).readText().replace("\r\n", "\n")

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

    @Test
    @DisplayName("the review-gated deferral seam still returns null/null unconditionally")
    fun deferralSeamStaysNull() {
        assertTrue(
            source("bot/GrandConcertFanPressure.kt")
                .contains("fun reviewGatedPolicyInputs(snapshot: Snapshot): ReviewGatedPolicyInputs = ReviewGatedPolicyInputs(null, null)"),
            "reviewGatedPolicyInputs must still return ReviewGatedPolicyInputs(null, null) unconditionally",
        )
    }

    @Test
    @DisplayName("the Grand Concert deadline-turn OCR standdown (-1) is unchanged")
    fun gcDeadlineStanddownRemains() {
        assertTrue(
            source("utils/CustomImageUtils.kt").contains("if (GrandConcertScenario.matches(game.scenario)) return -1"),
            "the GC -1 deadline standdown must remain so bFanEmergencyActive stays false in Grand Concert",
        )
    }

    @Test
    @DisplayName("the activation helper is a pure derivation: no bFanEmergencyActive, no hasFanRequirement write")
    fun activationHelperIsPure() {
        val body = source("bot/GrandConcertFanRequirement.kt")
        assertFalse(body.contains("bFanEmergencyActive"), "facts activation must never touch bFanEmergencyActive")
        assertFalse(
            Regex("hasFanRequirement\\s*=[^=]").containsMatchIn(body),
            "the helper must not write hasFanRequirement; Racing is the writer",
        )
    }

    @Test
    @DisplayName("facts never feed the fan emergency: bFanEmergencyActive is assigned only from isFanEmergency")
    fun factsDoNotFeedEmergency() {
        val body = source("bot/Racing.kt")
        val assigns = Regex("bFanEmergencyActive\\s*=[^=]").findAll(body).map { it.value.trim() }.toList()
        // Exactly two assignments: the field default (= false) and the isFanEmergency computation.
        assertEquals(2, assigns.size, "unexpected bFanEmergencyActive assignment sites: $assigns")
        assertTrue(body.contains("bFanEmergencyActive = isFanEmergency("), "the only runtime assignment must remain the isFanEmergency one")
    }

    @Test
    @DisplayName("facts reach hasFanRequirement through the one requirement-refresh writer, ahead of routing and selection")
    fun oneWriterAndNoSelectionLeak() {
        val racing = source("bot/Racing.kt")
        // checkRacingRequirements applies the facts through the pure decision function.
        assertTrue(
            racing.contains("hasFanRequirement = GrandConcertFanRequirement.resolveHasFanRequirement(hasFanRequirement, scenarioFanRequirement)"),
            "the facts value must be applied via resolveHasFanRequirement",
        )
        // Racing does not load facts itself: the identity/asset lookup lives in the campaign hook.
        assertFalse(racing.contains("GrandConcertFanRequirement.evaluate("), "Racing must not load facts itself; the campaign hook does")
        // Every activation reference stays ahead of the routing/selection code (processStandardRacing
        // and the dedicated forced-fan branch), so no second facts-based force is introduced there.
        assertTrue(
            racing.indexOf("processStandardRacing") > racing.lastIndexOf("GrandConcertFanRequirement"),
            "activation-facts references must stay in the requirement-refresh path, not in routing/selection",
        )
        assertFalse(
            source("bot/GrandConcertFanRaceSelector.kt").contains("GrandConcertFanRequirement"),
            "the forced-fan selector must stay independent of activation facts",
        )
    }

    @Test
    @DisplayName("the facts arm sits after the Summer early-return, so a Summer turn never re-arms from facts")
    fun factsArmIsAfterSummerSkip() {
        val racing = source("bot/Racing.kt")
        val summerReturn = racing.indexOf("Skipping racing requirements checks and clearing flags")
        val factsArm = racing.indexOf("hasFanRequirement = GrandConcertFanRequirement.resolveHasFanRequirement")
        assertTrue(summerReturn in 0 until factsArm, "the facts arm must apply after the Summer early-return in checkRacingRequirements")
    }

    @Test
    @DisplayName("the scan planner still has no production caller")
    fun plannerHasNoProductionCaller() {
        val offenders =
            kotlinRoot().walkTopDown()
                .filter { it.isFile && it.extension == "kt" && it.name != "GrandConcertFanRaceScanPlanner.kt" }
                .filter { it.readText().contains("GrandConcertFanRaceScanPlanner") }
                .map { it.name }
                .toList()
        assertTrue(offenders.isEmpty(), "GrandConcertFanRaceScanPlanner must stay unwired; referenced by: $offenders")
    }
}
