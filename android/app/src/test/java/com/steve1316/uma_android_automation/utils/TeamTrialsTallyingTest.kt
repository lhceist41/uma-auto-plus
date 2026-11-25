package com.steve1316.uma_android_automation.utils

import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The weekly Tallying period greys out Team Race while its label still matches (MuMu 1080x1920 frames masked to the button,
 * fixtures/tallying/PROVENANCE.md). Team Trials then ends as a finish, so the Home follow-ups still run.
 */
@DisplayName("Team Trials weekly Tallying period")
class TeamTrialsTallyingTest {
    /** The label's match centre on every fixture. */
    private val cx = 540
    private val cy = 1267

    private fun greyed(
        name: String,
        x: Int = cx,
        y: Int = cy,
    ): Boolean {
        val img = requireNotNull(javaClass.getResourceAsStream("/fixtures/tallying/$name.png")) { "missing fixture $name.png" }.use { FixturePng.read(it) }
        return teamRaceGreyed(SparkPixelSampler { px, py -> img.getRGB(px, py) }, img.width, img.height, x, y)
    }

    @Test
    fun `the greyed Team Race of the Tallying period reads greyed`() {
        assertTrue(greyed("team_race_tallying"))
    }

    @Test
    fun `an enabled Team Race does not`() {
        assertFalse(greyed("team_trials_home_rp0"))
        assertFalse(greyed("team_trials_home_later_week"))
    }

    @Test
    fun `a label matched off the button reads nothing, and one at the frame edge samples nothing outside it`() {
        assertFalse(greyed("team_race_tallying", y = cy - 120))
        assertFalse(greyed("team_race_tallying", x = 100))
        assertFalse(greyed("team_race_tallying", y = 1910))
    }

    @Test
    fun `one grey frame is not enough, two Team Trials home frames in a row are`() {
        assertFalse(TeamTrialsTask.tallyingConfirmed(true, null, 7))
        assertFalse(TeamTrialsTask.tallyingConfirmed(true, 5, 7), "a frame in between breaks the run")
        assertFalse(TeamTrialsTask.tallyingConfirmed(false, 6, 7))
        assertTrue(TeamTrialsTask.tallyingConfirmed(true, 6, 7))
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val f = File(dir, relative)
            if (f.isFile) return f.readText().replace("\r\n", "\n")
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    @Test
    fun `Tallying ends Team Trials as a finish, so the Home follow-ups still run`() {
        val src = source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/TeamTrialsTask.kt")
        val home = src.substringAfter("private fun handleTeamTrialsHome(bitmap: Bitmap) {").substringBefore("\n    }\n")
        val check = home.indexOf("if (tallyingConfirmed(greyed, teamRaceGreyedAt, iterationsCompleted)) {")
        assertTrue(check >= 0, "the Team Trials home checks for Tallying")
        assertTrue(home.indexOf("finishReason = TALLYING_REASON", check) > check, "Tallying sets the finish reason")
        assertTrue(check < home.indexOf("val rp = readRp(bitmap)"), "before the RP read and the Team Race tap")
        assertTrue(home.indexOf("goHome()", check) > check, "and walks back to Home")
        val homeScreen = src.substringAfter("private fun handleHomeScreen(): TaskResult? {").substringBefore("tapRaceTab()")
        assertTrue(homeScreen.contains("finishReason?.let") && homeScreen.contains("return TaskResult.Success("), "Home with a finish reason is a Success")
        val misc = source("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/MiscTask.kt")
        val start = misc.substringAfter("override fun start(maxRuntimeMinutes: Int): TaskResult {")
        assertTrue(start.indexOf("if (result !is TaskResult.Success) return result") in 0 until start.indexOf("runFollowUp(\"[LEGEND_RACE]\")"), "the follow-ups run after a Success")
    }
}
