package com.steve1316.uma_android_automation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

@DisplayName("Opening cinematic ending before the skip click")
class CinematicIntroRaceTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val nav by lazy {
        repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/CareerLaunchNavigator.kt").readText().replace("\r\n", "\n")
    }

    private val handler by lazy {
        val start = nav.indexOf("    private fun handleCinematicIntro(): TransitionResult {")
        nav.substring(start, nav.indexOf("\n    }\n", start))
    }

    @Test
    @DisplayName("a click that finds no skip button re-detects instead of failing the run")
    fun missedClickContinues() {
        assertFalse(handler.contains("TransitionResult.Failed("), "no terminal failure in the cinematic handler")
        assertTrue(handler.trimEnd().endsWith("return TransitionResult.Continue"), "the last path looks again")
    }

    @Test
    @DisplayName("the skip-scene confirmation is answered before any skip tap, and after each skip click")
    fun sceneSkipConfirmationAnswered() {
        assertTrue(handler.indexOf("if (pressSceneSkipConfirmation()) return TransitionResult.Continue") in 0 until handler.indexOf("ButtonSkipCinematic.click(iu)"))
        assertTrue(Regex("if \\(pressSceneSkipConfirmation\\(\\)\\) \\{\\s+return TransitionResult\\.Continue").findAll(handler).count() == 2, "after the >> and the text Skip clicks")
    }

    @Test
    @DisplayName("the confirmation's Skip is found from the dialog's own templates and read before the tap")
    fun sceneSkipDetectThenTap() {
        val start = nav.indexOf("    private fun pressSceneSkipConfirmation(): Boolean {")
        val helper = nav.substring(start, nav.indexOf("\n    }\n", start))
        val dialog = helper.indexOf("CheckboxDoNotShowAgain.check(iu, sourceBitmap = bitmap)")
        val cancel = helper.indexOf("ButtonCancel.findImageWithBitmap(iu, bitmap, null, null)")
        val read = helper.indexOf("if (!label.uppercase().contains(\"SKIP\")) return false")
        val tap = helper.indexOf("CoordinateTap.tap(gestureUtils, skipX, cancel.y, \"scene_skip_confirm\")")
        assertTrue(dialog in 0 until cancel && cancel < read && read < tap, "dialog, Cancel anchor, label read, then the tap")
        assertTrue(helper.contains("val skipX = bitmap.width - cancel.x"), "Skip mirrors Cancel across the centre")
        assertFalse(helper.contains("Checkbox.click") || helper.contains("CheckboxDoNotShowAgain.click"), "the player's game option is left alone")
    }

    @Test
    @DisplayName("the stuck limit still bounds a screen that keeps reading as the cinematic")
    fun stuckLimitStillApplies() {
        val start = nav.indexOf("if (detectedState == currentState &&")
        val exemptions = nav.substring(start, nav.indexOf("stuckInStateCount = stuckCountAfter(", start))
        assertTrue(start >= 0 && exemptions.contains("TAP_TO_CONTINUE"), "found the stuck-count exemptions")
        assertFalse(exemptions.contains("CINEMATIC_INTRO"), "the cinematic counts toward MAX_STUCK_ITERATIONS")
    }
}
