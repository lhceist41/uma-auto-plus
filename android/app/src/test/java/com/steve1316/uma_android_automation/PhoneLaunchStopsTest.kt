package com.steve1316.uma_android_automation

import com.steve1316.uma_android_automation.bot.campaigns.GrandConcert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Launch stops on a phone screen")
class PhoneLaunchStopsTest {
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

    private val nav by lazy { source("CareerLaunchNavigator.kt") }

    private fun body(
        text: String,
        signature: String,
    ): String {
        val start = text.indexOf(signature)
        assertTrue(start >= 0, signature)
        return text.substring(start, text.indexOf("\n    }\n", start))
    }

    @Nested
    @DisplayName("Grand Concert outside the mapped surfaces")
    inner class GrandConcertScreen {
        @Test
        @DisplayName("the mapped 1080-wide surfaces support Grand Concert, other sizes do not")
        fun supportedScreen() {
            for ((w, h) in listOf(1080 to 1920, 1080 to 2316, 1080 to 2340)) assertTrue(GrandConcert.supportsScreen(w, h), "${w}x$h")
            for ((w, h) in listOf(1080 to 1800, 1440 to 3088, 720 to 1280)) assertFalse(GrandConcert.supportsScreen(w, h), "${w}x$h")
        }

        @Test
        @DisplayName("the finale is read on the mapped surfaces; elsewhere it stops before any tap")
        fun finaleStops() {
            val gc = source("bot/campaigns/GrandConcert.kt")
            val pending = body(gc, "    override fun checkCampaignSpecificConditions(): Boolean {")
            val stop = pending.indexOf("stopBeforeUnmeasuredFinale(bitmap.width, bitmap.height)")
            assertTrue(stop >= 0 && stop < pending.indexOf("runConcertEscort()"), "the finale check comes before the escort's first tap")
            val gate = body(gc, "    private fun stopBeforeUnmeasuredFinale(")
            assertTrue(gate.contains("if (finaleMeasuredOn(width, height)) return"))
            assertTrue(gate.contains("if (!finaleCannotBeRuledOut(date.dayObserved, date.day)) return"), "only a read turn of concerts 1-4 lets the escort run")
            assertTrue(gate.contains("throw CampaignBreakpointException(handoff.playerMessage())"))
            assertTrue(body(gc, "    private fun finaleMeasuredOn(").contains("): Boolean = isMappedSurface(width, height)"), "the finale screens are measured on the mapped surfaces")
            val escort = body(gc, "    private fun runConcertEscort(): Boolean {")
            assertTrue(escort.contains("DialogUtils.titleFromPixels(bitmap) == DialogBonusesUpdated.title ->"), "Bonuses Updated is named by its script before the Close tap")
            assertFalse(gc.contains("grandConcertBonusesUpdatedPresent("), "the bare probe also matches Confirm Playback and the skip confirm")
            for (probe in listOf("grandConcertPlaybackMenuSkipPresent", "grandConcertPlaybackMenuButtonPresent", "grandConcertOnStagePresent")) {
                assertTrue(escort.contains("finaleMeasured && $probe(sampler, bitmap.width, bitmap.height)"), probe)
            }
            val confirm = body(gc, "    private fun startConcertFromConfirm(): Boolean {")
            assertTrue(
                confirm.contains(
                    "when (if (finaleMeasuredOn(bitmap.width, bitmap.height)) grandConcertCutsceneCheckboxState(sampler, bitmap.width, bitmap.height) else GrandCutsceneCheckbox.ABSENT) {",
                ),
            )
        }

        @Test
        @DisplayName("a new career is refused on Final Confirmation before the mode gate and the Start Career press")
        fun launchRefused() {
            val handler = body(nav, "    private fun handlePreRunConfirmation(): TransitionResult {")
            val refusal = handler.indexOf("!GrandConcert.supportsScreen(SharedData.displayWidth, SharedData.displayHeight)")
            assertTrue(refusal >= 0, "the screen check")
            assertTrue(refusal < handler.indexOf("verifyNormalCareerMode()"), "before the mode gate and every later Start tap")
            assertTrue(handler.contains("reasonKey = \"GRAND_CONCERT_SCREEN_UNSUPPORTED\","))
            assertTrue(handler.contains("GrandConcert.isGrandConcert(SettingsHelper.getStringSetting(\"general\", \"scenario\"))"), "only for a Grand Concert run")
        }

        @Test
        @DisplayName("a started or resumed career stops at the first Grand Concert hook, before any of its probes or taps")
        fun careerStops() {
            val gc = source("bot/campaigns/GrandConcert.kt")
            for (hook in listOf(
                "override fun checkEndScreen(): Boolean {",
                "override fun checkCampaignSpecificConditions(): Boolean {",
                "override fun openCareerEndSkillScreen() {",
                "override fun onBeforeMainScreenUpdate() {",
            )) {
                assertTrue(gc.contains("$hook\n        stopOnUnsupportedScreen()\n"), hook)
            }
            val stop = body(gc, "    private fun stopOnUnsupportedScreen() {")
            assertTrue(stop.contains("if (supportsScreen(SharedData.displayWidth, SharedData.displayHeight)) return"))
            assertTrue(stop.contains("StartModule.queueStopKey = \"GRAND_CONCERT_SCREEN_UNSUPPORTED\""))
            assertTrue(stop.contains("StartModule.queueStopRequested = true"))
            assertTrue(stop.contains("throw InterruptedException(reason)"))
        }

        @Test
        @DisplayName("the pending screen's turn, one behind at most, lets concerts 1-4 run and stops the finale and an unread turn")
        fun finaleTurns() {
            // The turn is read on the career screen only: a MuMu career log shows 71 on the finale's pending screen.
            for (day in listOf(23, 24, 35, 36, 47, 48, 59, 60)) assertFalse(GrandConcert.finaleCannotBeRuledOut(true, day), "turn $day")
            for (day in listOf(1, 61, 71, 72, 73, 75)) assertTrue(GrandConcert.finaleCannotBeRuledOut(true, day), "turn $day")
            for (day in listOf(1, 24, 60, 71)) assertTrue(GrandConcert.finaleCannotBeRuledOut(false, day), "unread, default $day")
        }

        @Test
        @DisplayName("the navigator's Grand Concert screen probes read only on supported surfaces")
        fun navigatorProbesGated() {
            assertEquals(2, Regex("GrandConcert\\.supportsScreen\\(bitmap\\.width, bitmap\\.height\\)").findAll(nav).count())
        }
    }

    @Nested
    @DisplayName("Spark reroll outside the mapped 1080-wide surfaces")
    inner class SparkReroll {
        @Test
        @DisplayName("the SPARKS screen is neither read nor priced, and the rolled set is kept with no spend")
        fun keepsWithoutSpend() {
            val handler = body(nav, "    private fun handleSparksScreen(): TransitionResult {")
            assertFalse(handler.contains("bitmap.width != 1080 || bitmap.height != 1920"), "a tall 1080-wide phone reads and prices the set")
            val gate = handler.indexOf("if (!isMappedSurface(bitmap.width, bitmap.height)) {")
            assertTrue(gate >= 0, "the screen gate")
            assertTrue(gate < handler.indexOf("readCompleteSparkSet(SPARKS_SCREEN_GEOMETRY, \"original\")"), "before the original-set read")
            assertTrue(gate < handler.indexOf("SparkRerollPolicy.decide("), "before the pricing")
            assertTrue(gate < handler.indexOf("ButtonRerollSparks.click("), "before the 30 TP spend")
            val branch = handler.substring(gate, handler.indexOf("\n        }\n", gate))
            assertTrue(branch.contains("transaction?.declineSpend("))
            assertTrue(branch.contains("return confirmSparks(bitmap)"))
        }
    }

    @Nested
    @DisplayName("Final Confirmation refusals")
    inner class FinalConfirmationRefusals {
        @Test
        @DisplayName("every mode refusal carries its key")
        fun modeRefusalsKeyed() {
            val gate = body(nav, "    private fun verifyNormalCareerMode(): TransitionResult? {")
            assertEquals(Regex("TransitionResult\\.Failed\\(").findAll(gate).count(), Regex("reasonKey = \"FINAL_CONFIRMATION_MODE_UNVERIFIED\",").findAll(gate).count())
            assertEquals(3, Regex("reasonKey = \"FINAL_CONFIRMATION_MODE_UNVERIFIED\",").findAll(gate).count())
        }

        @Test
        @DisplayName("a refusal before Start Career does not claim a career in the slot")
        fun noCareerClaimed() {
            assertEquals(setOf("FINAL_CONFIRMATION_MODE_UNVERIFIED", "GRAND_CONCERT_SCREEN_UNSUPPORTED"), FINAL_CONFIRMATION_REFUSAL_KEYS)
            assertTrue(
                source("StartModule.kt").contains(
                    "queueHaltCareerInFlight = (!careerFinished || navResult.careerResumed) && navResult.reasonKey !in FINAL_CONFIRMATION_REFUSAL_KEYS",
                ),
            )
        }
    }
}
