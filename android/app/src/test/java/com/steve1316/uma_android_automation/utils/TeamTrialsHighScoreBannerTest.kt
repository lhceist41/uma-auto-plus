package com.steve1316.uma_android_automation.utils

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The Team Trials "NEW HIGH SCORE!" banner on MuMu 1080x1920 frames, masked to the probed rows (fixtures/highscore/PROVENANCE.md),
 * against unrelated frames that must stay unknown and so keep counting toward the bailout.
 */
@DisplayName("Team Trials high score banner")
class TeamTrialsHighScoreBannerTest {
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

    private fun banner(path: String): Boolean {
        val img = image(path)
        return teamTrialsHighScoreBanner(SparkPixelSampler { x, y -> img.getRGB(x.coerceIn(0, img.width - 1), y.coerceIn(0, img.height - 1)) }, img.width, img.height)
    }

    @Test
    fun `the banner is recognised, with and without its blinking TAP prompt`() {
        assertTrue(banner("highscore/high_score_banner"))
        assertTrue(banner("highscore/high_score_banner_tap_hidden"))
    }

    @Test
    fun `unrelated frames are not the banner`() {
        assertFalse(banner("highscore/session_error"))
        assertFalse(banner("highscore/event_home_popup"))
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
        for (name in phone) assertFalse(banner("teamtrials/$name"), name)
    }

    @Test
    fun `the tap lands on the TAP prompt`() {
        val img = image("highscore/high_score_banner")
        val y = gameY(TeamTrialsGeometry.HIGH_SCORE_TAP_Y.toDouble(), ScreenBand.BOTTOM, img.width, img.height).toInt()
        val p = img.getRGB(TeamTrialsGeometry.HIGH_SCORE_TAP_X, y)
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        // The prompt's bar is a dark grey-violet, (131, 119, 145) here; the blurred results around it are light.
        assertTrue(r + g + b < 480 && b > g, "($r, $g, $b) at y $y")
    }

    @Test
    fun `Team Trials checks it only during a match, before any other screen, and an unknown screen still counts`() {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        var file: File? = null
        repeat(8) {
            val f = File(dir, "android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/TeamTrialsTask.kt")
            if (file == null && f.isFile) file = f
            dir = dir?.parentFile
        }
        val src = requireNotNull(file).readText().replace("\r\n", "\n")
        val detect = src.substringAfter("private fun detectScreenState(sourceBitmap: Bitmap): TeamTrialsScreenState {")
        val first = detect.substringBefore("return TeamTrialsScreenState.")
        assertTrue(first.contains("if (matchInProgress && teamTrialsHighScoreBanner(sampler(sourceBitmap), sourceBitmap.width, sourceBitmap.height))"), first)
        assertTrue(src.contains("trackProgress(currentState.name, currentState == TeamTrialsScreenState.UNKNOWN)"))
    }
}
