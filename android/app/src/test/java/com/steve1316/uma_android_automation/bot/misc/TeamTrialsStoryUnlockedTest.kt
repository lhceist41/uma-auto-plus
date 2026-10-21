package com.steve1316.uma_android_automation.bot.misc

import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask.Companion.ownDialog
import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask.Companion.splashTapAllowed
import com.steve1316.uma_android_automation.bot.misc.TeamTrialsTask.TeamTrialsScreenState
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** A Story Unlocked dialog over the results reads as UNKNOWN; the shared handler closes it and the next frame is handled as usual. */
class TeamTrialsStoryUnlockedTest {
    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            if (File(dir, relative).isFile) return File(dir, relative)
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found from the test working directory")
    }

    private val own =
        setOf(
            TeamTrialsScreenState.ITEMS_SELECTED_POPUP,
            TeamTrialsScreenState.RESTORE_RP_PROMPT,
            TeamTrialsScreenState.DAILY_SALE_POPUP,
            TeamTrialsScreenState.HIGH_SCORE_BANNER,
            TeamTrialsScreenState.CLASS_RESULT,
        )

    @Test
    fun `an unknown frame, a dialog included, goes to the shared handler`() {
        assertFalse(ownDialog(TeamTrialsScreenState.UNKNOWN))
        for (state in TeamTrialsScreenState.entries - own) assertFalse(ownDialog(state), state.name)
    }

    @Test
    fun `the task's own popups are kept from the shared handler`() {
        for (state in own) assertTrue(ownDialog(state), state.name)
    }

    @Test
    fun `a handled popup ends the pass before any state handler, so the dialog is never tapped blindly`() {
        val src = repoFile("android/app/src/main/java/com/steve1316/uma_android_automation/bot/misc/TeamTrialsTask.kt").readText().replace("\r\n", "\n")
        val handled = src.indexOf("if (!ownDialog(currentState) && handleIncidentalPopups()) {\n            return null\n        }")
        assertTrue(handled >= 0, "the shared handler runs on every frame that is not an own popup")
        val dispatch = src.indexOf("return when (currentState) {")
        assertTrue(dispatch > handled, "the state dispatch follows the handler")
        assertTrue(src.indexOf("trackProgress(currentState.name", 0) < handled, "the frame is counted before the handler")
    }

    @Test
    fun `the dialog follows the results screen, where an unknown frame is never splash-tapped`() {
        for (unknowns in 1..6) assertFalse(splashTapAllowed(true, unknowns, TeamTrialsScreenState.POST_MATCH_RESULTS.name), "$unknowns")
    }
}
