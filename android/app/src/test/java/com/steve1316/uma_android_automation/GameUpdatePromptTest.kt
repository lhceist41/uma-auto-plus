package com.steve1316.uma_android_automation

import com.steve1316.automation_library.utils.TextUtils
import com.steve1316.uma_android_automation.bot.TaskResult
import com.steve1316.uma_android_automation.bot.TaskResultCode
import com.steve1316.uma_android_automation.bot.misc.MiscTask
import com.steve1316.uma_android_automation.bot.unknownScreenNudge
import com.steve1316.uma_android_automation.components.DialogApplicationUpdated
import com.steve1316.uma_android_automation.components.DialogBonusesUpdated
import com.steve1316.uma_android_automation.components.DialogObjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The game's forced "Application Updated" prompt: its only button, "To Store", opens the store app, so every
 * reader stops the run with its own reason and taps nothing (seen live 2026-11-05, where a blind nudge pressed it).
 */
@DisplayName("Forced game update prompt")
class GameUpdatePromptTest {
    private val titles = DialogObjects.items.map { it.title }

    @Test
    fun `its title reads as its own dialog, and it has no button the bot may press`() {
        for (read in listOf("Application Updated", "Application Updated.", "Applicaton Updated", "ApplicationUpdated")) {
            assertEquals(DialogApplicationUpdated.title, TextUtils.matchStringInList(read, titles), read)
        }
        for (title in titles - DialogApplicationUpdated.title) {
            assertTrue(TextUtils.matchStringInList(title, titles) != DialogApplicationUpdated.title, title)
        }
        assertEquals(DialogBonusesUpdated.title, TextUtils.matchStringInList(DialogBonusesUpdated.title, titles), "a neighbour keeps its own title")
        assertEquals(DialogApplicationUpdated, DialogObjects.map["application_updated"])
        assertNull(DialogApplicationUpdated.okButton)
        assertNull(DialogApplicationUpdated.closeButton)
        assertTrue(DialogApplicationUpdated.buttons.isEmpty())
    }

    @Test
    fun `between runs it stops the queue with its own reason and taps nothing`() {
        assertEquals(BetweenRunDialog.APPLICATION_UPDATED, readBetweenRunDialog(false, { true }, { DialogApplicationUpdated.title }))
        val step = planBetweenRunDialog(BetweenRunDialog.APPLICATION_UPDATED, { throw AssertionError("no outage budget for an update prompt") }, StartModule.NAV_DEADLINE_MS)
        assertEquals(BetweenRunDialogStep.Fail(reasonKey = "GAME_UPDATE_REQUIRED"), step)
        assertTrue(step.taps.isEmpty())
        assertTrue("GAME_UPDATE_REQUIRED" in REPORT_REASON_KEYS, "the stop has its own words")
    }

    @Test
    fun `the shared stop sets the bot's own stop and taps nothing`() {
        val dialog = source("components/Dialog.kt")
        val stop = block(dialog, "fun stopForGameUpdate(", "\n    }\n")
        val stopLines =
            listOf("StartModule.queueStopKey = \"GAME_UPDATE_REQUIRED\"", "StartModule.queueStopReason = reason", "StartModule.queueStopRequested = true", "throw InterruptedException(reason)")
        for (line in stopLines) assertTrue(stop.contains(line), line)
        for (never in listOf(".click(", "tap(")) assertFalse(stop.contains(never), never)
        assertTrue(block(dialog, "private fun handleDangerousDialogs(", "\n    }\n").contains("if (dialog == DialogApplicationUpdated) stopForGameUpdate()"), "every getDialog caller stops on it")
    }

    @Test
    fun `the unknown-screen ladders stop on it before any tap or reopen`() {
        val recover = block(source("bot/Campaign.kt"), "private fun recoverFromUnknownScreen(", "\n    }\n}\n")
        val check = recover.indexOf("if (DialogUtils.gameUpdatePromptShown(game.imageUtils)) DialogUtils.stopForGameUpdate()")
        assertTrue(check >= 0, "in-career unknown screen")
        for (later in listOf("dismissNotificationShade(", "isEventCutsceneSkipPillVisible()", "game.reopenGame(", "game.tap(")) {
            assertTrue(check < recover.indexOf(later), "before $later")
        }
        val frozen = block(source("bot/misc/MiscTask.kt"), "private fun recoverFrozenGame(", "\n    }\n")
        val miscCheck = frozen.indexOf("if (DialogUtils.gameUpdatePromptShown(game.imageUtils)) DialogUtils.stopForGameUpdate()")
        assertTrue(miscCheck in 0 until frozen.indexOf("game.reopenGame("), "Home modes stop before their restart")
        // The Daily Extras step is covered by behaviour in DailyExtrasTest.
        val legendRead = block(source("bot/misc/DailyLegendRace.kt"), "private fun spendingDialogShown(", "\n    }\n")
        assertTrue(legendRead.contains("if (title == DialogApplicationUpdated.title) DialogUtils.stopForGameUpdate()"), "Legend Race follow-up")
    }

    @Test
    fun `a Home follow-up the bot stopped itself ends as that stop, a user Stop does not`() {
        val reason = "The game asks for an update."
        assertEquals(TaskResult.Success(TaskResultCode.TASK_RESULT_MANUALLY_STOPPED, reason), MiscTask.followUpStopResult(InterruptedException(reason), botStopReason = reason))
        assertNull(MiscTask.followUpStopResult(InterruptedException("Stopped by the user"), botStopReason = null))
        assertTrue(source("bot/misc/MiscTask.kt").contains("return stop?.let { followUpStopResult(it, StartModule.queueStopReason) } ?: result"))
    }

    @Test
    fun `an unknown dialog with no Close never gets the tap that lands on its button row`() {
        for (count in 1..30) assertEquals(350.0 to 450.0, unknownScreenNudge(count, dialogBannerUp = true), "tick $count")
        assertEquals(540.0 to 1300.0, unknownScreenNudge(2, dialogBannerUp = false))
        assertEquals(350.0 to 450.0, unknownScreenNudge(3, dialogBannerUp = false))
        val recover = block(source("bot/Campaign.kt"), "private fun recoverFromUnknownScreen(", "\n    }\n}\n")
        assertTrue(recover.contains("val (nudgeX, nudgeY) = unknownScreenNudge(count, dialogBannerUp)\n        game.tap(nudgeX, nudgeY, taps = 1)"))
        assertFalse(recover.contains("game.tap(350.0, 450.0"), "the nudge spots live in one place")
    }

    private fun block(
        src: String,
        startMarker: String,
        endMarker: String,
    ): String {
        val start = src.indexOf(startMarker)
        assertTrue(start >= 0, "missing $startMarker")
        val end = src.indexOf(endMarker, start)
        assertTrue(end > start, "missing the end of $startMarker")
        return src.substring(start, end)
    }

    private fun source(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val path = "android/app/src/main/java/com/steve1316/uma_android_automation/$relative"
        repeat(8) {
            listOf(File(dir, path), File(dir, "src/main/java/com/steve1316/uma_android_automation/$relative")).firstOrNull { it.isFile }?.let { return it.readText().replace("\r\n", "\n") }
            dir = dir?.parentFile
        }
        throw AssertionError("$relative not found")
    }
}
