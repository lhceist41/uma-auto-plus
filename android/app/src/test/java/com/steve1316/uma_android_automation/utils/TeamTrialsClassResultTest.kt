package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The weekly Team Trials class result screen ("CLASS RETENTION" and "CLASS DEMOTION") on MuMu 1080x1920 frames, masked
 * to the probed rows (fixtures/classresult/PROVENANCE.md), against the Team Trials home behind it and every other Team
 * Trials frame, which must stay what they were.
 */
@DisplayName("Team Trials weekly class result screen")
class TeamTrialsClassResultTest {
    private var savedInset = 0

    @BeforeEach
    fun saveInset() {
        savedInset = screenTopInset
    }

    @AfterEach
    fun restoreInset() {
        screenTopInset = savedInset
    }

    private fun image(path: String): FixturePng = requireNotNull(javaClass.getResourceAsStream("/fixtures/$path.png")) { "missing fixture $path.png" }.use { FixturePng.read(it) }

    private fun classResult(path: String): Boolean {
        val img = image(path)
        return teamTrialsClassResult(SparkPixelSampler { x, y -> img.getRGB(x.coerceIn(0, img.width - 1), y.coerceIn(0, img.height - 1)) }, img.width, img.height)
    }

    @Test
    fun `the retention and the demotion screens are recognised`() {
        assertTrue(classResult("classresult/class_retention"))
        assertTrue(classResult("classresult/class_demotion"))
    }

    @Test
    fun `the Team Trials home it leads to and every other frame are not the class result`() {
        assertFalse(classResult("classresult/team_trials_home_after_next"))
        assertFalse(classResult("highscore/high_score_banner"))
        assertFalse(classResult("highscore/high_score_banner_tap_hidden"))
        assertFalse(classResult("highscore/session_error"))
        assertFalse(classResult("highscore/event_home_popup"))
        screenTopInset = 94
        val phone =
            listOf(
                "select_opponent",
                "select_opponent_race_again",
                "team_trials_home_rp5",
                "team_trials_home_rp0",
                "home_rp0",
                "race_tab_rp5",
                "team_preview",
                "items_selected",
                "standby",
                "result_splash",
                "race_finished",
                "winnings",
            )
        for (name in phone) assertFalse(classResult("teamtrials/$name"), name)
    }

    @Test
    fun `the fallback tap lands on the Next button of both screens`() {
        for (fixture in listOf("classresult/class_retention", "classresult/class_demotion")) {
            val img = image(fixture)
            val y = gameY(TeamTrialsGeometry.CLASS_RESULT_NEXT_Y.toDouble(), ScreenBand.BOTTOM, img.width, img.height).toInt()
            for (x in intArrayOf(TeamTrialsGeometry.CLASS_RESULT_NEXT_X - 150, TeamTrialsGeometry.CLASS_RESULT_NEXT_X + 150)) {
                val p = img.getRGB(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // The button's green, (175, 223, 92) at its centre row; the blurred field around it is darker.
                assertTrue(g >= 150 && g - b >= 60 && g - r >= 20, "$fixture ($r, $g, $b) at ($x, $y)")
            }
            assertTrue(y in 1562..1676, "y $y")
        }
    }

    @Test
    fun `Team Trials checks it only before a match, right after the high score banner, and closes it with Next`() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var file: File? = null
        repeat(8) {
            val f = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/TeamTrialsTask.kt")
            if (file == null && f.isFile) file = f
            dir = dir?.parentFile
        }
        val src = requireNotNull(file).readText().replace("\r\n", "\n")
        val detect = src.substringAfter("private fun detectScreenState(sourceBitmap: Bitmap): TeamTrialsScreenState {")
        val banner = detect.indexOf("return TeamTrialsScreenState.HIGH_SCORE_BANNER")
        val result = detect.indexOf("if (!matchInProgress && teamTrialsClassResult(sampler(sourceBitmap), sourceBitmap.width, sourceBitmap.height))")
        assertTrue(banner in 0 until result, "the class result check follows the banner check")
        assertTrue(result < detect.indexOf("return TeamTrialsScreenState.", banner + 40), "no other screen is named before it")
        assertTrue(src.contains("currentState == TeamTrialsScreenState.CLASS_RESULT\n        if (!ownDialog"), "the shared popup handler leaves it alone")
        val handler = src.substringAfter("private fun handleClassResult(bitmap: Bitmap) {").substringBefore("game.wait(")
        assertTrue(handler.contains("ButtonNext.click(game.imageUtils, sourceBitmap = bitmap, region = Region.bottomHalf)"))
        assertTrue(handler.contains("TeamTrialsGeometry.CLASS_RESULT_NEXT_Y"))
        assertTrue(src.contains("trackProgress(currentState.name, currentState == TeamTrialsScreenState.UNKNOWN)"))
    }
}
