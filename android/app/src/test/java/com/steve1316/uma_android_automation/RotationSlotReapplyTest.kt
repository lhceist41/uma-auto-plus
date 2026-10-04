package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.StartModule.Companion.RotationConfig
import com.steve1316.uma_android_automation.bot.outcomeConfigFingerprint
import com.steve1316.uma_android_automation.bot.rotationSlotForCheck
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
            assertTrue(helper.substringBefore("\n    }").contains("val liveSlot = liveRotationSlot()"), "the shared live-slot read")
            assertTrue(
                startModule.contains("fun liveRotationSlot(): Int = if (rotationResyncPrevIndex >= 0) rotationResyncPrevIndex else rotationPrevIndex"),
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

    @Nested
    @DisplayName("slot the career-start check compares")
    inner class CheckedSlot {
        // One trainee in three scenarios, the shape that stopped runs 3 and 4 when the check took her first slot.
        private val names = listOf("Biwa Hayahide", "Rice Shower", "Rice Shower", "Rice Shower")
        private val scenarios = listOf("Grand Concert", "Grand Concert", "Unity Cup", "URA Finale")
        private val rotation = RotationConfig(true, 1, names)

        /** True when the check for [target] on a run of [running] would stop the queue. */
        private fun stops(target: String, liveSlot: Int, running: String): Boolean {
            val slot = rotationSlotForCheck(names, { scenarios[it] }, target, liveSlot, running)
            return rotationSlotScenarioMismatch(scenarios.getOrElse(slot) { "" }, running)
        }

        @Test
        fun `each run of a trainee held in several scenarios checks its own slot and never stops`() {
            for (run in 1..4) {
                val slot = rotation.indexForRun(run, 0)
                assertEquals(slot, rotationSlotForCheck(names, { scenarios[it] }, names[slot], slot, scenarios[slot]), "run $run")
                assertFalse(stops(names[slot], slot, scenarios[slot]), "run $run")
            }
        }

        @Test
        fun `her first slot would have stopped runs 3 and 4`() {
            assertTrue(rotationSlotScenarioMismatch(scenarios[names.indexOf("Rice Shower")], "Unity Cup"))
            assertTrue(rotationSlotScenarioMismatch(scenarios[names.indexOf("Rice Shower")], "URA Finale"))
        }

        @Test
        fun `a run whose settings drifted off its own slot still stops`() {
            // Run 3 loaded Rice's Unity Cup slot, but its Game was built on Grand Concert settings.
            assertTrue(stops("Rice Shower", 2, "Grand Concert"))
            assertTrue(stops("Biwa Hayahide", 0, "URA Finale"))
        }

        @Test
        fun `without a known slot, any of her slots in the running scenario passes`() {
            assertEquals(2, rotationSlotForCheck(names, { scenarios[it] }, "Rice Shower", -1, "Unity Cup"))
            assertEquals(3, rotationSlotForCheck(names, { scenarios[it] }, "Rice Shower", -1, "URA Finale"))
            assertFalse(stops("Rice Shower", -1, "Unity Cup"))
            assertFalse(stops("Rice Shower", -1, "URA Finale"))
        }

        @Test
        fun `without a known slot, a scenario none of her slots plays still stops`() {
            assertEquals(1, rotationSlotForCheck(names, { scenarios[it] }, "Rice Shower", -1, "Trackblazer"))
            assertTrue(stops("Rice Shower", -1, "Trackblazer"))
        }

        @Test
        fun `a live slot that holds another trainee falls back to her own slots`() {
            assertEquals(3, rotationSlotForCheck(names, { scenarios[it] }, "Rice Shower", 0, "URA Finale"))
        }

        @Test
        fun `after a resync the resynced slot is checked`() {
            val unique = listOf("Biwa Hayahide", "Rice Shower", "Mejiro McQueen")
            val uniqueScenarios = listOf("Grand Concert", "Unity Cup", "URA Finale")
            assertEquals(2, rotationSlotForCheck(unique, { uniqueScenarios[it] }, "Mejiro McQueen", 2, "URA Finale"))
            assertTrue(rotationSlotScenarioMismatch(uniqueScenarios[rotationSlotForCheck(unique, { uniqueScenarios[it] }, "Mejiro McQueen", 2, "Unity Cup")], "Unity Cup"))
        }

        @Test
        fun `unique names behave as before`() {
            val unique = listOf("Biwa Hayahide", "Rice Shower", "Mejiro McQueen")
            val uniqueScenarios = listOf("Grand Concert", "Unity Cup", "URA Finale")
            for (live in listOf(-1, 0, 1, 2)) {
                for (name in unique) {
                    assertEquals(unique.indexOf(name), rotationSlotForCheck(unique, { uniqueScenarios[it] }, name, live, "Unity Cup"), "$name live=$live")
                }
            }
            assertEquals(-1, rotationSlotForCheck(unique, { uniqueScenarios[it] }, "Haru Urara", 1, "Unity Cup"))
        }

        @Test
        fun `both checks read the run's slot, and the queue thread's slot is visible to the bot thread`() {
            val campaign = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/Campaign.kt").readText().replace("\r\n", "\n")
            val startModule = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/StartModule.kt").readText().replace("\r\n", "\n")
            assertTrue(campaign.contains("warnOnTraineeConfigDrift(rotationCheckSlot(target, StartModule.liveRotationSlot()), \"career-start check\")"))
            assertTrue(campaign.contains("warnOnTraineeConfigDrift(rotationCheckSlot(matched, bestIndex), \"post-resync verification\")"))
            assertFalse(campaign.contains("inGameNames.indexOf(target)"), "no first-slot lookup")
            assertTrue(startModule.contains("@Volatile\n        private var rotationPrevIndex: Int = -1"))
        }
    }
}
