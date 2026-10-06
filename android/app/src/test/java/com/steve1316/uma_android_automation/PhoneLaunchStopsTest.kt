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
    @DisplayName("Grand Concert outside 1080x1920")
    inner class GrandConcertScreen {
        @Test
        @DisplayName("only the measured 1080x1920 surface supports Grand Concert")
        fun supportedScreen() {
            assertTrue(GrandConcert.supportsScreen(1080, 1920))
            for ((w, h) in listOf(1080 to 2316, 1080 to 2340, 1440 to 3088, 720 to 1280)) assertFalse(GrandConcert.supportsScreen(w, h), "${w}x$h")
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
        @DisplayName("the navigator's Grand Concert screen probes read only on 1080x1920")
        fun navigatorProbesGated() {
            assertEquals(2, Regex("GrandConcert\\.supportsScreen\\(bitmap\\.width, bitmap\\.height\\)").findAll(nav).count())
        }
    }

    @Nested
    @DisplayName("Spark reroll outside 1080x1920")
    inner class SparkReroll {
        @Test
        @DisplayName("the SPARKS screen is neither read nor priced, and the rolled set is kept with no spend")
        fun keepsWithoutSpend() {
            val handler = body(nav, "    private fun handleSparksScreen(): TransitionResult {")
            val gate = handler.indexOf("if (bitmap.width != 1080 || bitmap.height != 1920) {")
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
