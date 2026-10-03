package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.outcomeConfigFingerprint
import com.steve1316.uma_android_automation.bot.rotationSlotScenarioMismatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Every rotation run starts on its own slot's settings, even when the trainee does not switch: a full settings save from the app can land
 * between the switch and the run start. A career that still runs another scenario's campaign stops at the career-start check. The decisions
 * are pure; the wiring is pinned by source guards.
 */
@DisplayName("Rotation slot at run start")
class RotationSlotReapplyTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val grandConcertSlot =
        listOf(
            Triple("general", "scenario", "Grand Concert"),
            Triple("racing", "enableRacingPlan", "false"),
            Triple("racing", "enableMandatoryRacingPlan", "false"),
            Triple("training", "moodFloor", "Good"),
            Triple("debug", "enableDebugMode", "false"),
        )

    private fun live(vararg overrides: Pair<String, String>): Map<String, String> =
        grandConcertSlot.associate { "${it.first}.${it.second}" to it.third } + overrides.toMap()

    @Nested
    @DisplayName("drift")
    inner class Drift {
        @Test
        fun `a base config saved over the slot is drift, scenario included`() {
            val drift =
                StartModule.rotationSlotDrift(
                    grandConcertSlot,
                    live("general.scenario" to "URA Finale", "racing.enableRacingPlan" to "true", "racing.enableMandatoryRacingPlan" to "true"),
                )
            assertEquals(listOf("general.scenario", "racing.enableMandatoryRacingPlan", "racing.enableRacingPlan"), drift)
        }

        @Test
        fun `a live config that matches the slot changes nothing`() {
            assertEquals(emptyList<String>(), StartModule.rotationSlotDrift(grandConcertSlot, live()))
        }

        @Test
        fun `debug rows never count, since a snapshot never applies them`() {
            assertEquals(emptyList<String>(), StartModule.rotationSlotDrift(grandConcertSlot, live("debug.enableDebugMode" to "true")))
        }

        @Test
        fun `a slot row missing from the live settings is drift`() {
            assertEquals(listOf("training.moodFloor"), StartModule.rotationSlotDrift(grandConcertSlot, live() - "training.moodFloor"))
        }
    }

    @Nested
    @DisplayName("run loop wiring")
    inner class Wiring {
        private val startModule by lazy {
            repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt").readText().replace("\r\n", "\n")
        }

        private val loop by lazy {
            val start = startModule.indexOf("for (i in startFromRun..totalRuns) {")
            assertTrue(start >= 0, "run loop not found")
            startModule.substring(start, startModule.indexOf("var result = runSingleGame()", start))
        }

        @Test
        fun `every rotation run re-checks its live slot before the run builds its Game, switching or not`() {
            assertTrue(loop.contains("if (enableRunQueue && rotation.enabled) reapplyLiveRotationSlot(i)\n"), "re-check before the run's first runSingleGame")
            val helper = startModule.substring(startModule.indexOf("private fun reapplyLiveRotationSlot(run: Int) {"))
            assertTrue(
                helper.substringBefore("\n    }").contains("val liveSlot = if (rotationResyncPrevIndex >= 0) rotationResyncPrevIndex else rotationPrevIndex"),
                "a mid-career resync's slot is the live one",
            )
        }

        @Test
        fun `a same-run retry re-checks the slot before it builds its new Game`() {
            assertTrue(
                startModule.contains(
                    "if (enableRunQueue && rotation.enabled) reapplyLiveRotationSlot(i)\n                        nextRunCareerInFlight = true\n                        result = runSingleGame()",
                ),
            )
        }

        @Test
        fun `a drifted slot is re-applied in full`() {
            val body = startModule.substring(startModule.indexOf("fun reapplyRotationSlotIfDrifted("), startModule.indexOf("fun setCurrentTrainee("))
            val drift = body.indexOf("val drift = rotationSlotDrift(slotRows, live)")
            val apply = body.indexOf("applyRotationSnapshot(context, index)")
            assertTrue(drift >= 0 && apply > drift, "re-apply follows the drift check")
            assertTrue(body.indexOf("if (drift.isEmpty()) {") in drift until apply, "a matching slot returns before any write")
        }
    }

    @Nested
    @DisplayName("career-start scenario check")
    inner class CareerStart {
        private val campaign by lazy {
            repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt").readText().replace("\r\n", "\n")
        }

        @Test
        fun `a slot scenario that differs from the running campaign is a mismatch`() {
            assertTrue(rotationSlotScenarioMismatch("Grand Concert", "URA Finale"))
        }

        @Test
        fun `a matching or unrecorded slot scenario is not`() {
            assertFalse(rotationSlotScenarioMismatch("Grand Concert", "Grand Concert"))
            assertFalse(rotationSlotScenarioMismatch("", "URA Finale"))
        }

        @Test
        fun `matching fingerprints still stop on a scenario mismatch, since the fingerprint does not cover the scenario`() {
            // A trainee whose URA Finale and Grand Concert presets share every fingerprinted value.
            val sharedPreset = mapOf("statPrioritization" to "Speed,Stamina,Wit,Power,Guts", "moodFloor" to "Good", "skillPointCheck" to "350")
            assertEquals(outcomeConfigFingerprint("1.6.0", sharedPreset), outcomeConfigFingerprint("1.6.0", sharedPreset))
            assertTrue(rotationSlotScenarioMismatch("Grand Concert", "URA Finale"))

            val check = campaign.substring(campaign.indexOf("private fun warnOnTraineeConfigDrift("), campaign.indexOf("private fun stopOnSlotScenarioMismatch("))
            val stop = check.indexOf("if (rotationSlotScenarioMismatch(slotScenario, game.scenario)) stopOnSlotScenarioMismatch(slotIndex, slotScenario)")
            assertTrue(stop >= 0, "the scenario check runs at the career start")
            assertTrue(stop < check.indexOf("val slotFp = rotationSlotFingerprint(slotIndex) ?: return"), "before a missing fingerprint can return")
            assertTrue(stop < check.indexOf("if (liveFp == slotFp) {"), "whatever the fingerprints say")
        }

        @Test
        fun `a scenario mismatch stops the queue instead of warning, before any tap`() {

            val body = campaign.substring(campaign.indexOf("private fun stopOnSlotScenarioMismatch("), campaign.indexOf("/** Required instance of the GameDate class. */"))
            assertTrue(body.contains("StartModule.queueStopKey = \"SCENARIO_MISMATCH\""), "truthful stop key")
            assertTrue(body.contains("StartModule.queueStopRequested = true"), "the queue stops")
            assertTrue(body.contains("throw InterruptedException(reason)"), "the run ends at once")
            assertFalse(Regex("tap|click", RegexOption.IGNORE_CASE).containsMatchIn(body), "no tap on the way out")
        }

        @Test
        fun `the stop has its own words for the player`() {
            assertTrue("SCENARIO_MISMATCH" in REPORT_REASON_KEYS)
        }
    }
}
