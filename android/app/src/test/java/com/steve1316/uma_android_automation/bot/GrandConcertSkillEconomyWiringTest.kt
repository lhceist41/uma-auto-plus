package com.steve1316.uma_android_automation.bot

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Source-level pins for the observation hooks: the hooks run beside the decision code, so the contracts that keep them
 * from changing it are asserted on the call sites themselves.
 */
@DisplayName("GrandConcertSkillEconomy wiring")
class GrandConcertSkillEconomyWiringTest {
    private val base = "android/app/src/main/java/com/steve1316/uma_android_automation/bot"

    @Test
    @DisplayName("an event is recorded only for an accepted match whose selected row was tapped")
    fun eventHookNeedsAnAcceptedMatch() {
        val trainingEvent = source("$base/TrainingEvent.kt")
        val hook = trainingEvent.indexOf("campaign.onTrainingEventResolved(")
        assertTrue(hook > 0)
        val guard = trainingEvent.lastIndexOf("if (", hook)
        assertTrue(trainingEvent.substring(guard, hook).contains("matchAccepted && tapPlan.action == OptionTapAction.USE_ROW"))
        assertTrue(trainingEvent.contains("val matchAccepted = shouldActOnEventMatch("))
    }

    @Test
    @DisplayName("the hooks add no capture or OCR and never swallow an interrupt")
    fun hooksAreCaptureFreeAndRethrowInterrupts() {
        val campaign = source("$base/campaigns/GrandConcert.kt")
        assertFalse(campaign.contains("runCatching"), "a runCatching in the Grand Concert hooks would swallow an InterruptedException")
        assertFalse(campaign.contains("determineSkillPoints("), "the record must not read Skill Points off the screen itself")
        assertTrue(campaign.contains("catch (e: InterruptedException)"))
        assertTrue(campaign.contains("trainee.skillPointsReads > skillPointsReadsAtConcert"))
    }

    @Test
    @DisplayName("only a successful turn-start read counts as a Skill Points read, never an assignment")
    fun skillPointsReadsCountRealReadsOnly() {
        val trainee = source("android/app/src/main/java/com/steve1316/uma_android_automation/types/Trainee.kt")
        assertFalse(trainee.contains("set(value)"), "a setter would count default or copied values as reads")
        val update = trainee.substring(trainee.indexOf("fun updateSkillPoints("))
        assertTrue(update.indexOf("skillPoints = res") in 0 until update.indexOf("skillPointsReads++"))
        assertTrue(Regex("skillPointsReads\\+\\+").findAll(trainee).count() == 1)
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val file = File(dir, relative)
            if (file.isFile) return file.readText().replace("\r\n", "\n")
            dir = dir.parentFile
        }
        error("source file not found: $relative")
    }
}
